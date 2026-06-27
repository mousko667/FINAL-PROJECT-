import { useState, useEffect } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { invoiceService } from '@/services/invoiceService'
import { Loader2, ArrowLeft, Upload, CheckCircle, AlertCircle, AlertTriangle, FileText } from 'lucide-react'

interface Department { id: string; code: string; nameEn: string; nameFr: string }

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
  invoiceNumber: z.string().optional(),
  amount: z.coerce.number().positive(),
  currency: z.string().min(1),
  issueDate: z.string().min(1),
  dueDate: z.string().min(1),
  description: z.string().optional(),
})

type ConfirmData = z.infer<typeof confirmSchema>

type Stage = 'upload' | 'confirm' | 'manual'

export default function SupplierInvoiceSubmitPage() {
  const { t } = useTranslation()
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
    defaultValues: { currency: 'XOF' },
  })

  // The duplicate pre-check is keyed by the supplier's own id (resolved from their profile).
  const { data: supplierProfile } = useQuery({
    queryKey: ['supplier-profile-id'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { id: string } }>('/supplier/profile')
      return data.data
    },
  })

  const watchedDescription = watch('description')
  const watchedInvoiceNumber = watch('invoiceNumber')

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
      const { data: createResp } = await apiClient.post('/supplier/invoices', {
        departmentId: formData.departmentId,
        amount: formData.amount,
        currency: formData.currency,
        issueDate: formData.issueDate,
        dueDate: formData.dueDate,
        description: formData.description || (formData.invoiceNumber ? `Invoice #${formData.invoiceNumber}` : undefined),
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
        <div className="flex items-start gap-3 bg-green-50 border border-green-200 rounded-lg p-4">
          <CheckCircle className="w-5 h-5 text-green-600 shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-medium text-green-800">{t('ocr.success', 'Fields extracted from your document')}</p>
            <p className="text-xs text-green-700 mt-0.5">
              {ocrResult.digitalPdf
                ? t('ocr.digitalPdf', 'Digital PDF — extracted via text layer (high accuracy)')
                : t('ocr.scanned', 'Scanned document — extracted via OCR (please verify all fields)')}
            </p>
          </div>
        </div>
      )}

      {ocrError && (
        <div className="flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-lg p-4">
          <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
          <p className="text-sm text-amber-800">{ocrError}</p>
        </div>
      )}

      {duplicateCheck?.duplicate && (
        <div role="status" className="flex items-start gap-3 bg-amber-50 border border-amber-200 rounded-lg p-4">
          <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
          <p className="text-sm text-amber-800">{t('invoice.duplicateWarning', { count: duplicateCheck.count })}</p>
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">

        {/* Department */}
        <div className="md:col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.department')} *</label>
          <Controller
            name="departmentId"
            control={control}
            render={({ field }) => (
              <select {...field} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                <option value="">{t('invoice.selectDept', '— Select department —')}</option>
                {(departments ?? []).map((d) => (
                  <option key={d.id} value={d.id}>{d.nameEn} ({d.code})</option>
                ))}
              </select>
            )}
          />
          {errors.departmentId && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
        </div>

        {/* Invoice Number (OCR-extracted, editable) */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('invoice.invoiceNumber', 'Invoice Number (your ref.)')}
            {ocrResult?.invoiceNumber && <span className="ml-2 text-xs text-green-600">✓ {t('ocr.extracted', 'auto-filled')}</span>}
          </label>
          <input
            {...register('invoiceNumber')}
            defaultValue={ocrResult?.invoiceNumber ?? ''}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            placeholder={t('invoice.invoiceNumberPlaceholder', 'e.g. FAC-2026-001')}
          />
        </div>

        {/* Amount */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('invoice.amount')} *
            {ocrResult?.totalAmount && <span className="ml-2 text-xs text-green-600">✓ {t('ocr.extracted', 'auto-filled')}</span>}
          </label>
          <input
            type="number" step="0.01"
            {...register('amount')}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.amount && <p className="text-xs text-red-500 mt-1">{t('validation.positiveNumber')}</p>}
        </div>

        {/* Currency */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.currency')} *</label>
          <select {...register('currency')} className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
            <option value="XOF">XOF (FCFA)</option>
            <option value="EUR">EUR</option>
            <option value="USD">USD</option>
          </select>
        </div>

        {/* Issue Date */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            {t('invoice.issueDate')} *
            {ocrResult?.invoiceDate && <span className="ml-2 text-xs text-green-600">✓ {t('ocr.extracted', 'auto-filled')}</span>}
          </label>
          <input
            type="date"
            {...register('issueDate')}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.issueDate && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
        </div>

        {/* Due Date */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.dueDate')} *</label>
          <input
            type="date"
            {...register('dueDate')}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
          {errors.dueDate && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
        </div>

        {/* Description */}
        <div className="md:col-span-2">
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.description')}</label>
          <textarea
            {...register('description')}
            rows={2}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          />
        </div>
      </div>

      {submitMutation.isError && (
        <p className="text-sm text-red-500 bg-red-50 px-4 py-2 rounded-lg">
          {(submitMutation.error as any)?.response?.data?.message ?? t('app.error')}
        </p>
      )}

      <div className="flex items-center justify-between pt-2 border-t">
        <button
          type="button"
          onClick={() => { setStage('upload'); setOcrResult(null); setUploadedFile(null) }}
          className="text-sm text-gray-500 hover:text-gray-700 underline"
        >
          {t('ocr.uploadDifferent', '← Upload a different file')}
        </button>
        <button
          type="submit"
          disabled={submitMutation.isPending}
          className="flex items-center gap-2 px-6 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
        >
          {submitMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
          {t('supplier.invoice.submit.title', 'Submit Invoice')}
        </button>
      </div>
    </form>
  )

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/supplier/invoices')} className="p-2 hover:bg-gray-100 rounded-full">
          <ArrowLeft className="w-5 h-5 text-gray-600" />
        </button>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('supplier.invoice.submit.title', 'Submit Invoice')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('supplier.invoice.submit.subtitle', 'Upload your invoice — fields will be extracted automatically.')}</p>
        </div>
      </div>

      {/* ── STAGE: Upload ── */}
      {stage === 'upload' && (
        <div
          onDrop={handleDrop}
          onDragOver={(e) => e.preventDefault()}
          className="bg-white rounded-xl border-2 border-dashed border-gray-300 p-10 flex flex-col items-center gap-4 hover:bg-gray-50 transition-colors"
        >
          {ocrMutation.isPending ? (
            <div className="flex flex-col items-center gap-3">
              <Loader2 className="w-10 h-10 animate-spin text-primary" />
              <p className="text-sm font-medium text-gray-700">{t('ocr.extracting', 'Extracting invoice data…')}</p>
              <p className="text-xs text-gray-400">{t('ocr.wait', 'This takes a few seconds')}</p>
            </div>
          ) : (
            <>
              <Upload className="w-12 h-12 text-gray-300" />
              <div className="text-center">
                <p className="text-base font-medium text-gray-700">{t('ocr.dropHere', 'Drop your invoice here')}</p>
                <p className="text-sm text-gray-400 mt-1">{t('ocr.formats', 'PDF, JPEG, PNG, TIFF — max 10 MB')}</p>
              </div>
              <label className="cursor-pointer">
                <span className="px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90">
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
                className="text-xs text-gray-400 hover:text-gray-600 underline"
              >
                {t('ocr.skipOcr', 'Skip — fill in manually instead')}
              </button>
            </>
          )}
        </div>
      )}

      {/* ── STAGE: Confirm OCR fields ── */}
      {stage === 'confirm' && uploadedFile && (
        <div className="bg-white rounded-xl border p-6 space-y-5">
          <div className="flex items-center gap-3 pb-3 border-b">
            <FileText className="w-5 h-5 text-primary" />
            <div>
              <p className="text-sm font-semibold text-gray-800">{uploadedFile.name}</p>
              <p className="text-xs text-gray-400">{(uploadedFile.size / 1024).toFixed(1)} KB</p>
            </div>
          </div>
          <p className="text-sm text-gray-600">{t('ocr.reviewPrompt', 'Review and correct the extracted fields below, then submit.')}</p>
          {formContent}
        </div>
      )}

      {/* ── STAGE: Manual entry (OCR skipped or failed) ── */}
      {stage === 'manual' && (
        <div className="bg-white rounded-xl border p-6 space-y-5">
          {formContent}
        </div>
      )}
    </div>
  )
}
