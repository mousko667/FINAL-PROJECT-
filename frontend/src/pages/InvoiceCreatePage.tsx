import { useState, useEffect } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { invoiceService } from '@/services/invoiceService'
import apiClient from '@/services/apiClient'
import { ChevronRight, ChevronLeft, Loader2, Plus, Trash2, AlertTriangle } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { formatAmount } from '@/lib/format'

interface Supplier { id: string; companyName: string; taxId: string; status: string }
interface Department { id: string; code: string; nameEn: string; nameFr: string }
interface PurchaseOrder { id: string; poNumber: string; status: string; totalAmount: number }

const detailsSchema = z.object({
  supplierId: z.string().min(1, 'Select a supplier'),
  purchaseOrderId: z.string().optional(),
  departmentId: z.string().min(1, 'Select a department'),
  amount: z.coerce.number().positive(),
  currency: z.string().min(1),
  issueDate: z.string().min(1),
  dueDate: z.string().min(1),
  description: z.string().optional(),
})

type DetailsFormData = z.infer<typeof detailsSchema>

const lineItemSchema = z.object({
  description: z.string().min(1),
  quantity: z.coerce.number().positive(),
  unitPrice: z.coerce.number().positive(),
})

type LineItemData = z.infer<typeof lineItemSchema> & { totalPrice: number }

const STEPS = ['details', 'lineItems', 'documents'] as const
type Step = typeof STEPS[number]

export default function InvoiceCreatePage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('details')
  const [lineItems, setLineItems] = useState<LineItemData[]>([])
  const [files, setFiles] = useState<File[]>([])
  const [detailsData, setDetailsData] = useState<DetailsFormData | null>(null)

  const { data: suppliers } = useQuery({
    queryKey: ['active-suppliers'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<Supplier>>>('/suppliers', {
        params: { status: 'ACTIVE', size: 200 },
      })
      return data.data?.content ?? []
    },
  })

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<Department>>>('/departments')
      return data.data?.content ?? []
    },
  })

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors },
  } = useForm<DetailsFormData>({ resolver: zodResolver(detailsSchema) })

  const watchedSupplierId = watch('supplierId')
  const watchedDescription = watch('description')

  // Advisory duplicate pre-check: debounce supplier + description, then query the non-blocking endpoint.
  const [dupCheckInput, setDupCheckInput] = useState<{ supplierId: string; description: string } | null>(null)
  useEffect(() => {
    const supplierId = watchedSupplierId
    const description = (watchedDescription ?? '').trim()
    if (!supplierId || !description) {
      setDupCheckInput(null)
      return
    }
    const handle = setTimeout(() => setDupCheckInput({ supplierId, description }), 500)
    return () => clearTimeout(handle)
  }, [watchedSupplierId, watchedDescription])

  const { data: duplicateCheck } = useQuery({
    queryKey: ['invoice-duplicate-check', dupCheckInput],
    queryFn: () => invoiceService.checkDuplicate(dupCheckInput!.supplierId, dupCheckInput!.description),
    enabled: !!dupCheckInput,
    staleTime: 30_000,
  })

  const { data: supplierPOs } = useQuery({
    queryKey: ['supplier-pos', watchedSupplierId],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<PurchaseOrder>>>('/purchase-orders', {
        params: { supplierId: watchedSupplierId, status: 'OPEN', size: 100 },
      })
      return data.data?.content ?? []
    },
    enabled: !!watchedSupplierId,
  })

  const liForm = useForm<z.infer<typeof lineItemSchema>>({
    resolver: zodResolver(lineItemSchema),
  })

  const createMutation = useMutation({
    mutationFn: async () => {
      if (!detailsData) return
      const invoice = await invoiceService.create({
        supplierId: detailsData.supplierId,
        purchaseOrderId: detailsData.purchaseOrderId || undefined,
        departmentId: detailsData.departmentId,
        amount: detailsData.amount,
        currency: detailsData.currency,
        issueDate: detailsData.issueDate,
        dueDate: detailsData.dueDate,
        description: detailsData.description,
        lineItems,
      })
      for (const file of files) {
        await invoiceService.uploadDocument(invoice.id, file)
      }
      return invoice
    },
    onSuccess: (invoice) => {
      if (invoice) navigate(`/invoices/${invoice.id}`)
    },
  })

  const onDetailsSubmit = (data: DetailsFormData) => {
    setDetailsData(data)
    setStep('lineItems')
  }

  const addLineItem = (data: z.infer<typeof lineItemSchema>) => {
    setLineItems((prev) => [...prev, { ...data, totalPrice: data.quantity * data.unitPrice }])
    liForm.reset()
  }

  const removeLineItem = (idx: number) => setLineItems((prev) => prev.filter((_, i) => i !== idx))
  const total = lineItems.reduce((s, li) => s + li.totalPrice, 0)

  const stepLabels = {
    details: t('invoice.details', 'Details'),
    lineItems: t('invoice.lineItems', 'Line Items'),
    documents: t('invoice.documents', 'Documents'),
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <PageHeader title={t('invoice.new', 'New Invoice')} />

      {/* Step indicators */}
      <div className="flex gap-2">
        {STEPS.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold
              ${step === s ? 'bg-primary text-white' : STEPS.indexOf(step) > i ? 'bg-pos text-white' : 'bg-ground text-ink-soft'}`}>
              {i + 1}
            </div>
            <span className={`text-sm ${step === s ? 'font-medium text-primary' : 'text-muted-foreground'}`}>
              {stepLabels[s]}
            </span>
            {i < STEPS.length - 1 && <ChevronRight className="w-4 h-4 text-ink-faint mx-1" />}
          </div>
        ))}
      </div>

      {/* ── STEP 1: Details ── */}
      {step === 'details' && (
        <form onSubmit={handleSubmit(onDetailsSubmit)} className="bg-surface rounded-[4px] border border-hairline p-6 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

            {/* Supplier dropdown */}
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">
                {t('invoice.supplier', 'Supplier')} *
              </label>
              <Controller
                name="supplierId"
                control={control}
                render={({ field }) => (
                  <select {...field} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                    <option value="">{t('invoice.selectSupplier', '— Select supplier —')}</option>
                    {(suppliers ?? []).map((s) => (
                      <option key={s.id} value={s.id}>{s.companyName} ({s.taxId})</option>
                    ))}
                  </select>
                )}
              />
              {errors.supplierId && <p className="text-xs text-crit mt-1">{errors.supplierId.message}</p>}
            </div>

            {/* PO Reference — only shown when supplier selected */}
            {watchedSupplierId && (
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-ink-soft mb-1">
                  {t('invoice.purchaseOrder', 'Purchase Order (optional)')}
                </label>
                <Controller
                  name="purchaseOrderId"
                  control={control}
                  render={({ field }) => (
                    <select {...field} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                      <option value="">{t('invoice.noPO', '— No linked PO —')}</option>
                      {(supplierPOs ?? []).map((po) => (
                        <option key={po.id} value={po.id}>
                          {po.poNumber} — {formatAmount(po.totalAmount)} XAF
                        </option>
                      ))}
                    </select>
                  )}
                />
                <p className="text-xs text-ink-faint mt-1">
                  {t('invoice.poHint', 'Linking a PO enables automatic three-way matching on submission.')}
                </p>
              </div>
            )}

            {/* Department */}
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">
                {t('invoice.department', 'Department')} *
              </label>
              <Controller
                name="departmentId"
                control={control}
                render={({ field }) => (
                  <select {...field} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                    <option value="">{t('invoice.selectDept', '— Select department —')}</option>
                    {(departments ?? []).map((d) => (
                      <option key={d.id} value={d.id}>{i18n.language === 'fr' ? d.nameFr : d.nameEn} ({d.code})</option>
                    ))}
                  </select>
                )}
              />
              {errors.departmentId && <p className="text-xs text-crit mt-1">{errors.departmentId.message}</p>}
            </div>

            {/* Amount */}
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.amount', 'Amount')} *</label>
              <input type="number" step="0.01" {...register('amount')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.amount && <p className="text-xs text-crit mt-1">{t('validation.positiveNumber')}</p>}
            </div>

            {/* Currency */}
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.currency', 'Currency')} *</label>
              <select {...register('currency')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                <option value="XAF">XAF (Franc CFA)</option>
              </select>
            </div>

            {/* Issue Date */}
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.issueDate', 'Issue Date')} *</label>
              <input type="date" {...register('issueDate')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>

            {/* Due Date */}
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.dueDate', 'Due Date')} *</label>
              <input type="date" {...register('dueDate')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.description', 'Description')}</label>
            <textarea {...register('description')} rows={3} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>

          {duplicateCheck?.duplicate && (
            <div role="status" className="flex items-start gap-2 text-sm text-warn bg-warn-bg border border-warn/30 rounded-[4px] p-3">
              <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0 text-warn" />
              <span>{t('invoice.duplicateWarning', { count: duplicateCheck.count })}</span>
            </div>
          )}

          <div className="flex justify-end">
            <button type="submit" className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90">
              {t('pagination.next', 'Next')} <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </form>
      )}

      {/* ── STEP 2: Line Items ── */}
      {step === 'lineItems' && (
        <div className="space-y-4">
          <div className="bg-surface rounded-[4px] border border-hairline p-6 space-y-4">
            <h2 className="font-semibold text-ink">{t('invoice.lineItems', 'Line Items')}</h2>
            <p className="text-xs text-ink-faint">{t('invoice.lineItemsHint', 'Add invoice line items. These are compared against the PO during matching.')}</p>
            <form onSubmit={liForm.handleSubmit(addLineItem)} className="grid grid-cols-4 gap-3 items-end">
              <div className="col-span-2">
                <label className="block text-xs font-medium text-ink-soft mb-1">{t('invoice.description', 'Description')}</label>
                <input {...liForm.register('description')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft mb-1">{t('invoice.quantity', 'Qty')}</label>
                <input type="number" step="0.001" {...liForm.register('quantity')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-medium text-ink-soft mb-1">{t('invoice.unitPrice', 'Unit Price')}</label>
                <input type="number" step="0.01" {...liForm.register('unitPrice')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none" />
              </div>
              <div className="col-span-4 flex justify-end">
                <button type="submit" className="flex items-center gap-2 px-3 py-1.5 bg-primary text-primary-foreground rounded-[4px] text-sm hover:bg-primary/90">
                  <Plus className="w-4 h-4" /> {t('invoice.addLine', 'Add Line')}
                </button>
              </div>
            </form>

            {lineItems.length > 0 && (
              <table className="w-full text-sm mt-2">
                <thead><tr className="border-b text-left text-ink-soft text-xs">
                  <th className="py-2">{t('invoice.description', 'Description')}</th>
                  <th className="text-right">{t('invoice.quantity', 'Qty')}</th>
                  <th className="text-right">{t('invoice.unitPrice', 'Unit Price')}</th>
                  <th className="text-right">Total</th>
                  <th></th>
                </tr></thead>
                <tbody className="divide-y">
                  {lineItems.map((li, i) => (
                    <tr key={i}>
                      <td className="py-2">{li.description}</td>
                      <td className="text-right">{li.quantity}</td>
                      <td className="text-right num">{li.unitPrice.toFixed(2)}</td>
                      <td className="text-right num font-medium">{li.totalPrice.toFixed(2)}</td>
                      <td className="pl-2">
                        <button onClick={() => removeLineItem(i)} className="text-crit hover:text-crit"><Trash2 className="w-4 h-4" /></button>
                      </td>
                    </tr>
                  ))}
                  <tr className="font-semibold border-t">
                    <td colSpan={3} className="text-right py-2">Total</td>
                    <td className="text-right num">{total.toFixed(2)}</td>
                    <td></td>
                  </tr>
                </tbody>
              </table>
            )}
          </div>
          <div className="flex justify-between">
            <button onClick={() => setStep('details')} className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">
              <ChevronLeft className="w-4 h-4" /> {t('pagination.previous', 'Previous')}
            </button>
            <button onClick={() => setStep('documents')} className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90">
              {t('pagination.next', 'Next')} <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* ── STEP 3: Documents ── */}
      {step === 'documents' && (
        <div className="space-y-4">
          <div className="bg-surface rounded-[4px] border border-hairline p-6 space-y-4">
            <h2 className="font-semibold text-ink">{t('invoice.documents', 'Documents')}</h2>
            <label htmlFor="document-upload" className="flex flex-col items-center justify-center w-full h-40 border-2 border-dashed border-hairline-strong rounded-[4px] cursor-pointer hover:bg-ground transition-colors">
              <div className="text-center">
                <svg className="w-10 h-10 text-ink-faint mx-auto mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6h.1a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                </svg>
                <p className="text-sm text-muted-foreground">{t('invoice.dropFiles', 'Drop files or click to select')}</p>
                <p className="text-xs text-ink-faint mt-1">PDF, PNG, JPG, XLSX (max 10 MB)</p>
              </div>
              <input id="document-upload" type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.xlsx,.xls" className="hidden"
                onChange={(e) => { if (e.target.files) setFiles((prev) => [...prev, ...Array.from(e.target.files!)]) }} />
            </label>
            {files.length > 0 && (
              <ul className="space-y-1.5">
                {files.map((f, i) => (
                  <li key={i} className="flex items-center justify-between text-sm px-3 py-2 bg-ground rounded-[4px] border border-hairline">
                    <span className="font-medium text-ink-soft truncate">{f.name}</span>
                    <div className="flex items-center gap-2 ml-2 shrink-0">
                      <span className="text-xs text-muted-foreground">{(f.size / 1024).toFixed(1)} KB</span>
                      <button onClick={() => setFiles((p) => p.filter((_, j) => j !== i))} className="text-crit hover:text-crit"><Trash2 className="w-3.5 h-3.5" /></button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {createMutation.isError && (
            <div className="text-sm text-crit bg-crit-bg p-3 rounded-[4px] border border-crit/30">
              {(createMutation.error as any)?.response?.data?.message ?? t('app.error', 'An error occurred. Check that all required fields are filled.')}
            </div>
          )}

          <div className="flex justify-between">
            <button onClick={() => setStep('lineItems')} className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">
              <ChevronLeft className="w-4 h-4" /> {t('pagination.previous', 'Previous')}
            </button>
            <button onClick={() => createMutation.mutate()} disabled={createMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60">
              {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              {t('invoice.createDraft', 'Create Draft')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
