import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { invoiceService } from '@/services/invoiceService'
import { ChevronRight, ChevronLeft, Loader2, Plus, Trash2 } from 'lucide-react'

// Schemas
const detailsSchema = z.object({
  supplierName: z.string().min(1),
  referenceNumber: z.string().min(1),
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
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [step, setStep] = useState<Step>('details')
  const [lineItems, setLineItems] = useState<LineItemData[]>([])
  const [files, setFiles] = useState<File[]>([])
  const [detailsData, setDetailsData] = useState<DetailsFormData | null>(null)

  const { register, handleSubmit, formState: { errors } } = useForm<DetailsFormData>({
    resolver: zodResolver(detailsSchema),
  })
  const liForm = useForm<z.infer<typeof lineItemSchema>>({
    resolver: zodResolver(lineItemSchema),
  })

  const createMutation = useMutation({
    mutationFn: async () => {
      if (!detailsData) return
      const invoice = await invoiceService.create({
        ...detailsData,
        lineItems,
      })
      // Upload documents in sequence
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
    setLineItems((prev) => [
      ...prev,
      { ...data, totalPrice: data.quantity * data.unitPrice },
    ])
    liForm.reset()
  }

  const removeLineItem = (idx: number) =>
    setLineItems((prev) => prev.filter((_, i) => i !== idx))

  const total = lineItems.reduce((s, li) => s + li.totalPrice, 0)

  const stepLabels = {
    details: t('invoice.details'),
    lineItems: t('invoice.lineItems'),
    documents: t('invoice.documents'),
  }

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">{t('invoice.new')}</h1>

      {/* Step indicators */}
      <div className="flex gap-2">
        {STEPS.map((s, i) => (
          <div key={s} className="flex items-center gap-2">
            <div
              className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold
                ${step === s ? 'bg-primary text-white' : STEPS.indexOf(step) > i ? 'bg-green-500 text-white' : 'bg-gray-200 text-gray-500'}`}
            >
              {i + 1}
            </div>
            <span className={`text-sm ${step === s ? 'font-medium text-primary' : 'text-muted-foreground'}`}>
              {stepLabels[s]}
            </span>
            {i < STEPS.length - 1 && <ChevronRight className="w-4 h-4 text-gray-300 mx-1" />}
          </div>
        ))}
      </div>

      {/* Step: Details */}
      {step === 'details' && (
        <form onSubmit={handleSubmit(onDetailsSubmit)} className="bg-white rounded-xl border p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            {[
              { id: 'referenceNumber', label: t('invoice.reference') },
              { id: 'supplierName', label: t('invoice.supplier') },
            ].map(({ id, label }) => (
              <div key={id}>
                <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
                <input {...register(id as keyof DetailsFormData)} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                {errors[id as keyof DetailsFormData] && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
              </div>
            ))}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.amount')}</label>
              <input type="number" step="0.01" {...register('amount')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.amount && <p className="text-xs text-red-500 mt-1">{t('validation.positiveNumber')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.currency')}</label>
              <select {...register('currency')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                <option value="EUR">EUR</option>
                <option value="XOF">XOF</option>
                <option value="USD">USD</option>
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.issueDate')}</label>
              <input type="date" {...register('issueDate')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.dueDate')}</label>
              <input type="date" {...register('dueDate')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.description')}</label>
            <textarea {...register('description')} rows={3} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div className="flex justify-end">
            <button type="submit" id="btn-next-step-1" className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90">
              {t('pagination.next')} <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </form>
      )}

      {/* Step: Line Items */}
      {step === 'lineItems' && (
        <div className="space-y-4">
          <div className="bg-white rounded-xl border p-6 space-y-4">
            <h2 className="font-semibold text-gray-800">{t('invoice.lineItems')}</h2>
            <form onSubmit={liForm.handleSubmit(addLineItem)} className="grid grid-cols-4 gap-3 items-end">
              <div className="col-span-2">
                <label className="block text-xs font-medium text-gray-600 mb-1">{t('app.search')}</label>
                <input {...liForm.register('description')} placeholder="Description" className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Qté</label>
                <input type="number" {...liForm.register('quantity')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none" />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">P.U.</label>
                <input type="number" {...liForm.register('unitPrice')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none" />
              </div>
              <div className="col-span-4 flex justify-end">
                <button type="submit" className="flex items-center gap-2 px-3 py-1.5 bg-gray-800 text-white rounded-lg text-sm hover:bg-gray-700">
                  <Plus className="w-4 h-4" /> Ajouter
                </button>
              </div>
            </form>

            {lineItems.length > 0 && (
              <table className="w-full text-sm mt-2">
                <thead><tr className="border-b text-left text-gray-500">
                  <th className="py-2">Description</th><th>Qté</th><th>P.U.</th><th>Total</th><th></th>
                </tr></thead>
                <tbody className="divide-y">
                  {lineItems.map((li, i) => (
                    <tr key={i}>
                      <td className="py-2">{li.description}</td>
                      <td>{li.quantity}</td>
                      <td>{li.unitPrice}</td>
                      <td className="font-mono">{li.totalPrice.toFixed(2)}</td>
                      <td>
                        <button onClick={() => removeLineItem(i)} className="text-red-400 hover:text-red-600">
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  ))}
                  <tr className="font-semibold">
                    <td colSpan={3} className="text-right py-2">Total</td>
                    <td className="font-mono">{total.toFixed(2)}</td>
                    <td></td>
                  </tr>
                </tbody>
              </table>
            )}
          </div>
          <div className="flex justify-between">
            <button onClick={() => setStep('details')} id="btn-prev-step-2" className="flex items-center gap-2 px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">
              <ChevronLeft className="w-4 h-4" /> {t('pagination.previous')}
            </button>
            <button onClick={() => setStep('documents')} id="btn-next-step-2" className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90">
              {t('pagination.next')} <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Step: Documents */}
      {step === 'documents' && (
        <div className="space-y-4">
          <div className="bg-white rounded-xl border p-6 space-y-4">
            <h2 className="font-semibold text-gray-800">{t('invoice.documents')}</h2>
            <label
              htmlFor="document-upload"
              className="flex flex-col items-center justify-center w-full h-40 border-2 border-dashed border-gray-300 rounded-xl cursor-pointer hover:bg-gray-50 transition-colors"
            >
              <div className="text-center">
                <svg className="w-10 h-10 text-gray-300 mx-auto mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6h.1a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                </svg>
                <p className="text-sm text-muted-foreground">Glissez-déposez ou cliquez pour sélectionner</p>
                <p className="text-xs text-gray-400 mt-1">PDF, PNG, JPG, XLSX (max 10 Mo)</p>
              </div>
              <input
                id="document-upload"
                type="file"
                multiple
                accept=".pdf,.png,.jpg,.jpeg,.xlsx,.xls"
                className="hidden"
                onChange={(e) => {
                  if (e.target.files) setFiles((prev) => [...prev, ...Array.from(e.target.files!)])
                }}
              />
            </label>

            {files.length > 0 && (
              <ul className="space-y-1.5">
                {files.map((f, i) => (
                  <li key={i} className="flex items-center justify-between text-sm px-3 py-2 bg-gray-50 rounded-lg border">
                    <span className="font-medium text-gray-700 truncate">{f.name}</span>
                    <div className="flex items-center gap-2 ml-2 shrink-0">
                      <span className="text-xs text-muted-foreground">{(f.size / 1024).toFixed(1)} KB</span>
                      <button onClick={() => setFiles((p) => p.filter((_, j) => j !== i))} className="text-red-400 hover:text-red-600">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="flex justify-between">
            <button onClick={() => setStep('lineItems')} id="btn-prev-step-3" className="flex items-center gap-2 px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">
              <ChevronLeft className="w-4 h-4" /> {t('pagination.previous')}
            </button>
            <button
              id="btn-submit-invoice"
              onClick={() => createMutation.mutate()}
              disabled={createMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-60"
            >
              {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              {t('invoice.submit')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
