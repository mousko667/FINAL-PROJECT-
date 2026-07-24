import { useState, useEffect } from 'react'
import { apiErrorMessage } from '@/types/apiError'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { invoiceService } from '@/services/invoiceService'
import { Loader2, ArrowLeft, Upload, CheckCircle, AlertCircle, AlertTriangle, FileText } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'

interface Department { id: string; code: string; nameEn: string; nameFr: string }

/** A purchase order the supplier can invoice against (AUDIT-001). Scoped server-side. */
interface SupplierPurchaseOrder {
  id: string
  poNumber: string
  totalAmount: number
  items: Array<{
    id: string
    itemDescription: string
    quantity: number
    unitPrice: number
    lineTotal: number
  }>
}

interface OcrResult {
  invoiceNumber?: string
  invoiceDate?: string
  totalAmount?: number
  supplierId?: string
  poReference?: string
  rawText?: string
  digitalPdf?: boolean
  lineItems?: Array<{ description: string; quantity: string; unitPrice: string }>
}

const confirmSchema = z.object({
  departmentId: z.string().min(1, 'Required'),
  purchaseOrderId: z.string().optional(),
  invoiceNumber: z.string().optional(),
  amount: z.coerce.number().positive(),
  // AUDIT-033 (D4): single-currency system — XAF only.
  currency: z.literal('XAF'),
  issueDate: z.string().min(1),
  dueDate: z.string().min(1),
  description: z.string().optional(),
})
  // AUDIT-032: the due date cannot precede the issue date. Mirrors the backend constraint so the
  // supplier gets the refusal in the form rather than as a server error after submitting.
  .refine((d) => !d.issueDate || !d.dueDate || d.dueDate >= d.issueDate, {
    path: ['dueDate'],
    message: 'invoice.dueDateBeforeIssueDate',
  })

type ConfirmData = z.infer<typeof confirmSchema>

type Stage = 'upload' | 'confirm' | 'manual'

export default function SupplierInvoiceSubmitPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()

  const [stage, setStage] = useState<Stage>('upload')
  const [ocrResult, setOcrResult] = useState<OcrResult | null>(null)
  const [uploadedFile, setUploadedFile] = useState<File | null>(null)
  const [ocrError, setOcrError] = useState<string | null>(null)

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Department[] } }>('/departments')
      return data.data?.content ?? []
    },
  })

  const ocrMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      const { data } = await apiClient.post<{ data: OcrResult }>('/ocr/extract', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return data.data
    },
    onSuccess: (result) => {
      setOcrResult(result)
      setOcrError(null)
      setStage('confirm')
      // Pre-fill form with extracted values
      if (result.invoiceDate) setValue('issueDate', result.invoiceDate)
      if (result.totalAmount) setValue('amount', result.totalAmount)
    },
    onError: () => {
      setOcrError(t('ocr.error', 'Could not extract data from the file. Please fill in the fields manually.'))
      setStage('manual')
    },
  })

  const {
    register,
    handleSubmit,
    control,
    setValue,
    watch,
    formState: { errors },
  } = useForm<ConfirmData>({
    resolver: zodResolver(confirmSchema),
    defaultValues: { currency: 'XAF' },
  })

  // The duplicate pre-check is keyed by the supplier's own id (resolved from their profile).
  const { data: supplierProfile } = useQuery({
    queryKey: ['supplier-profile-id'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { id: string } }>('/supplier/profile')
      return data.data
    },
  })

  // AUDIT-001: purchase orders this supplier may invoice against. Without this selector the
  // purchaseOrderId field was unreachable from the UI, so no portal invoice could ever trigger
  // the three-way matching. The endpoint is scoped to the authenticated supplier server-side.
  const { data: purchaseOrders } = useQuery({
    queryKey: ['supplier-purchase-orders'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierPurchaseOrder[] }>('/supplier/purchase-orders')
      return data.data ?? []
    },
  })

  const watchedDescription = watch('description')
  const watchedInvoiceNumber = watch('invoiceNumber')
  const watchedPurchaseOrderId = watch('purchaseOrderId')
  const selectedPo = (purchaseOrders ?? []).find((po) => po.id === watchedPurchaseOrderId)

  // Selecting a PO prefills the amount so the invoice lines sent for matching stay consistent
  // with the order. The supplier can still adjust the amount before submitting.
  useEffect(() => {
    if (selectedPo) {
      setValue('amount', selectedPo.totalAmount)
    }
  }, [selectedPo, setValue])

  // Advisory duplicate pre-check: debounce description (falls back to the invoice number) then query.
  const [dupDescription, setDupDescription] = useState<string | null>(null)
  useEffect(() => {
    const description = ((watchedDescription || watchedInvoiceNumber) ?? '').trim()
    if (!description) {
      setDupDescription(null)
      return
    }
    const handle = setTimeout(() => setDupDescription(description), 500)
    return () => clearTimeout(handle)
  }, [watchedDescription, watchedInvoiceNumber])

  const { data: duplicateCheck } = useQuery({
    queryKey: ['supplier-duplicate-check', supplierProfile?.id, dupDescription],
    queryFn: () => invoiceService.checkDuplicate(supplierProfile!.id, dupDescription!),
    enabled: !!supplierProfile?.id && !!dupDescription,
    staleTime: 30_000,
  })

  const submitMutation = useMutation({
    mutationFn: async (formData: ConfirmData) => {
      // 1. Create the invoice draft
      // When a PO is selected, its lines are sent as invoice lines: the three-way matching needs
      // line-level data on both sides, otherwise submission is rejected (AUDIT-001 + AUDIT-031).
      const po = (purchaseOrders ?? []).find((p) => p.id === formData.purchaseOrderId)
      const { data: createResp } = await apiClient.post('/supplier/invoices', {
        departmentId: formData.departmentId,
        purchaseOrderId: formData.purchaseOrderId || undefined,
        amount: formData.amount,
        currency: formData.currency,
        issueDate: formData.issueDate,
        dueDate: formData.dueDate,
        description: formData.description || (formData.invoiceNumber ? `Invoice #${formData.invoiceNumber}` : undefined),
        lineItems: po
          ? po.items.map((item) => ({
              description: item.itemDescription,
              quantity: item.quantity,
              unitPrice: item.unitPrice,
              totalPrice: item.lineTotal,
            }))
          : undefined,
      })
      const invoiceId = createResp.data?.id

      // 2. Upload the original file as an invoice document
      if (uploadedFile && invoiceId) {
        const fd = new FormData()
        fd.append('file', uploadedFile)
        await apiClient.post(`/supplier/invoices/${invoiceId}/documents`, fd, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
      }

      return invoiceId
    },
    onSuccess: () => navigate('/supplier/invoices'),
  })

  const handleFileSelected = (file: File) => {
    setUploadedFile(file)
    setOcrError(null)
    ocrMutation.mutate(file)
  }

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault()
    const file = e.dataTransfer.files[0]
    if (file) handleFileSelected(file)
  }

  const formContent = (
    <form onSubmit={handleSubmit((d) => submitMutation.mutate(d))} className="space-y-5">

      {ocrResult && stage === 'confirm' && (
        <div className="flex items-start gap-3 bg-pos-bg border border-pos/30 rounded-[4px] p-4">
          <CheckCircle className="w-5 h-5 text-pos shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-pos">{t('ocr.success', 'Fields extracted from your document')}</p>
            <p className="text-xs text-pos mt-0.5">
              {ocrResult.digitalPdf
                ? t('ocr.digitalPdf', 'Digital PDF — extracted via text layer (high accuracy)')
                : t('ocr.scanned', 'Scanned document — extracted via OCR (please verify all fields)')}
            </p>
          </div>
        </div>
      )}

      {ocrError && (
        <div className="flex items-start gap-3 bg-warn-bg border border-warn/30 rounded-[4px] p-4">
          <AlertCircle className="w-5 h-5 text-warn shrink-0 mt-0.5" />
          <p className="text-sm text-warn">{ocrError}</p>
        </div>
      )}

      {duplicateCheck?.duplicate && (
        <div role="status" className="flex items-start gap-3 bg-warn-bg border border-warn/30 rounded-[4px] p-4">
          <AlertTriangle className="w-5 h-5 text-warn shrink-0 mt-0.5" />
          <p className="text-sm text-warn">{t('invoice.duplicateWarning', { count: duplicateCheck.count })}</p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">

        {/* Department */}
        <div className="md:col-span-2">
          <label htmlFor="departmentId" className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.department')} *</label>
          <Controller
            name="departmentId"
            control={control}
            render={({ field }) => (
              <select id="departmentId" {...field} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30">
                <option value="">{t('invoice.selectDept', '— Select department —')}</option>
                {(departments ?? []).map((d) => (
                  <option key={d.id} value={d.id}>{i18n.language === 'fr' ? d.nameFr : d.nameEn} ({d.code})</option>
                ))}
              </select>
            )}
          />
          {errors.departmentId && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
        </div>

        {/* Purchase Order (AUDIT-001) — optional, but required for three-way matching */}
        <div className="md:col-span-2">
          <label htmlFor="purchaseOrderId" className="block text-sm font-medium text-ink-soft mb-1">
            {t('invoice.purchaseOrder')}
          </label>
          <select
            id="purchaseOrderId"
            {...register('purchaseOrderId')}
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
          >
            <option value="">{t('invoice.noPO')}</option>
            {(purchaseOrders ?? []).map((po) => (
              <option key={po.id} value={po.id}>
                {po.poNumber} — {po.totalAmount}
              </option>
            ))}
          </select>
          <p className="text-xs text-ink-soft mt-1">
            {t('invoice.poHint')}
          </p>
          {selectedPo && (
            <div className="mt-2 border border-hairline rounded-[4px] p-3 bg-ground">
              <p className="text-xs font-medium text-ink-soft mb-1">
                {t('invoice.poLines', 'Lignes reprises de la commande')} ({selectedPo.items.length})
              </p>
              <ul className="text-xs text-ink-soft space-y-0.5">
                {selectedPo.items.map((item) => (
                  <li key={item.id}>
                    {item.itemDescription} — {item.quantity} × {item.unitPrice} = {item.lineTotal}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Invoice Number (OCR-extracted, editable) */}
        <div>
          <label htmlFor="invoiceNumber" className="block text-sm font-medium text-ink-soft mb-1">
            {t('invoice.invoiceNumber', 'Invoice Number (your ref.)')}
            {ocrResult?.invoiceNumber && <span className="ml-2 text-xs text-pos">✓ {t('ocr.extracted', 'auto-filled')}</span>}
          </label>
          <input
            id="invoiceNumber"
            {...register('invoiceNumber')}
            defaultValue={ocrResult?.invoiceNumber ?? ''}
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
            placeholder={t('invoice.invoiceNumberPlaceholder', 'e.g. FAC-2026-001')}
          />
        </div>

        {/* Amount */}
        <div>
          <label htmlFor="amount" className="block text-sm font-medium text-ink-soft mb-1">
            {t('invoice.amount')} *
            {ocrResult?.totalAmount && <span className="ml-2 text-xs text-pos">✓ {t('ocr.extracted', 'auto-filled')}</span>}
          </label>
          <input
            id="amount"
            type="number" step="0.01"
            {...register('amount')}
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.amount && <p className="text-xs text-crit mt-1">{t('validation.positiveNumber')}</p>}
        </div>

        {/* Currency — AUDIT-033 (D4): the system is single-currency XAF (Central African CFA
            franc, BEAC). EUR and USD were removed: nothing validated them server-side and no
            screen could convert them. A one-option dropdown would be a misleading affordance, so
            the value is shown read-only. It is submitted from the form's `defaultValues`
            (currency: 'XAF'), not from this input — a hidden `register()` field would suggest a
            binding that react-hook-form does not actually perform for hidden inputs. */}
        <div>
          <label htmlFor="invoice-currency" className="block text-sm font-medium text-ink-soft mb-1">
            {t('invoice.currency')} *
          </label>
          <input id="invoice-currency" type="text" value="XAF (FCFA)" readOnly
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-soft focus:outline-none" />
        </div>

        {/* Issue Date */}
        <div>
          <label htmlFor="issueDate" className="block text-sm font-medium text-ink-soft mb-1">
            {t('invoice.issueDate')} *
            {ocrResult?.invoiceDate && <span className="ml-2 text-xs text-pos">✓ {t('ocr.extracted', 'auto-filled')}</span>}
          </label>
          <input
            id="issueDate"
            type="date"
            {...register('issueDate')}
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.issueDate && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
        </div>

        {/* Due Date */}
        <div>
          <label htmlFor="dueDate" className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.dueDate')} *</label>
          <input
            id="dueDate"
            type="date"
            {...register('dueDate')}
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {/* AUDIT-032: distinguish "missing" from "due date before issue date" — the schema puts
              the i18n key in the message, so a blanket "required" would hide the real reason. */}
          {errors.dueDate && (
            <p className="text-xs text-crit mt-1">
              {errors.dueDate.message
                ? t(errors.dueDate.message)
                : t('validation.required')}
            </p>
          )}
        </div>

        {/* Description */}
        <div className="md:col-span-2">
          <label htmlFor="description" className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.description')}</label>
          <textarea
            id="description"
            {...register('description')}
            rows={2}
            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>
      </div>

      {submitMutation.isError && (
        <p className="text-sm text-crit bg-crit-bg px-4 py-2 rounded-[4px]">
          {apiErrorMessage(submitMutation.error) ?? t('app.error')}
        </p>
      )}

      <div className="flex items-center justify-between pt-2 border-t border-hairline">
        <button
          type="button"
          onClick={() => { setStage('upload'); setOcrResult(null); setUploadedFile(null) }}
          className="text-sm text-ink-soft hover:text-ink underline"
        >
          {t('ocr.uploadDifferent', '← Upload a different file')}
        </button>
        <button
          type="submit"
          disabled={submitMutation.isPending}
          className="flex items-center gap-2 px-6 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
        >
          {submitMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
          {t('supplier.invoice.submit.title', 'Submit Invoice')}
        </button>
      </div>
    </form>
  )

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <button onClick={() => navigate('/supplier/invoices')} className="p-2 -ml-2 hover:bg-white/10 rounded-full shrink-0">
              <ArrowLeft className="w-5 h-5 text-white/80" />
            </button>
            {t('supplier.invoice.submit.title', 'Submit Invoice')}
          </span>
        }
        subtitle={t('supplier.invoice.submit.subtitle', 'Upload your invoice — fields will be extracted automatically.')}
      />

      {/* ── STAGE: Upload ── */}
      {stage === 'upload' && (
        <div
          onDrop={handleDrop}
          onDragOver={(e) => e.preventDefault()}
          className="bg-surface rounded-[4px] border-2 border-dashed border-hairline-strong p-10 flex flex-col items-center gap-4 hover:bg-ground transition-colors"
        >
          {ocrMutation.isPending ? (
            <div className="flex flex-col items-center gap-3">
              <Loader2 className="w-10 h-10 animate-spin text-primary" />
              <p className="text-sm font-medium text-ink-soft">{t('ocr.extracting', 'Extracting invoice data…')}</p>
              <p className="text-xs text-ink-faint">{t('ocr.wait', 'This takes a few seconds')}</p>
            </div>
          ) : (
            <>
              <Upload className="w-12 h-12 text-ink-faint" />
              <div className="text-center">
                <p className="text-base font-medium text-ink-soft">{t('ocr.dropHere', 'Drop your invoice here')}</p>
                <p className="text-sm text-ink-faint mt-1">{t('ocr.formats', 'PDF, JPEG, PNG, TIFF — max 10 MB')}</p>
              </div>
              <label className="cursor-pointer">
                <span className="px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90">
                  {t('ocr.chooseFile', 'Choose File')}
                </span>
                <input
                  type="file"
                  accept=".pdf,.jpg,.jpeg,.png,.tiff,.tif,.xml"
                  className="hidden"
                  onChange={(e) => { if (e.target.files?.[0]) handleFileSelected(e.target.files[0]) }}
                />
              </label>
              <button
                onClick={() => setStage('manual')}
                className="text-xs text-ink-faint hover:text-ink-soft underline"
              >
                {t('ocr.skipOcr', 'Skip — fill in manually instead')}
              </button>
            </>
          )}
        </div>
      )}

      {/* ── STAGE: Confirm OCR fields ── */}
      {stage === 'confirm' && uploadedFile && (
        <div className="bg-surface rounded-[4px] border border-hairline shadow-sm p-6 space-y-5">
          <div className="flex items-center gap-3 pb-3 border-b border-hairline">
            <FileText className="w-5 h-5 text-primary" />
            <div>
              <p className="text-sm font-semibold text-ink">{uploadedFile.name}</p>
              <p className="text-xs text-ink-faint num">{(uploadedFile.size / 1024).toFixed(1)} KB</p>
            </div>
          </div>
          <p className="text-sm text-ink-soft">{t('ocr.reviewPrompt', 'Review and correct the extracted fields below, then submit.')}</p>
          {formContent}
        </div>
      )}

      {/* ── STAGE: Manual entry (OCR skipped or failed) ── */}
      {stage === 'manual' && (
        <div className="bg-surface rounded-[4px] border border-hairline shadow-sm p-6 space-y-5">
          {formContent}
        </div>
      )}
    </div>
  )
}
