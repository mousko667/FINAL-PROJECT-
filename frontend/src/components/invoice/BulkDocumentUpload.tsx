import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { DocumentUploader } from '@/components/invoice/DocumentUploader'
import { Loader2, UploadCloud, CheckCircle2, XCircle } from 'lucide-react'

interface BulkUploadResult {
  totalFiles: number
  uploaded: number
  failed: number
  documents: { id: string; originalFilename: string }[]
  errors: { filename: string; message: string }[]
}

/**
 * P11-48 (REQ-05) — bulk/multi-file upload for an existing invoice. Collects files with the shared
 * DocumentUploader, then POSTs them in one multipart request to /invoices/{id}/documents/bulk and
 * shows the per-file outcome report. ASSISTANT_COMPTABLE only (gated by the caller + the backend).
 */
export function BulkDocumentUpload({ invoiceId }: { invoiceId: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [files, setFiles] = useState<File[]>([])
  const [result, setResult] = useState<BulkUploadResult | null>(null)

  const upload = useMutation({
    mutationFn: async () => {
      const form = new FormData()
      files.forEach((f) => form.append('files', f))
      const { data } = await apiClient.post<{ data: BulkUploadResult }>(
        `/invoices/${invoiceId}/documents/bulk`,
        form,
        { headers: { 'Content-Type': 'multipart/form-data' } },
      )
      return data.data
    },
    onSuccess: (res) => {
      setResult(res)
      setFiles([])
      queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
    },
  })

  return (
    <div className="bg-white rounded-xl border p-5">
      <div className="flex items-center gap-2 mb-3">
        <UploadCloud className="w-5 h-5 text-primary" />
        <h2 className="font-semibold text-gray-800">{t('invoice.bulkUpload.title')}</h2>
      </div>
      <p className="text-sm text-gray-500 mb-3">{t('invoice.bulkUpload.subtitle')}</p>

      <DocumentUploader onFilesChange={setFiles} disabled={upload.isPending} />

      <button
        type="button"
        onClick={() => upload.mutate()}
        disabled={files.length === 0 || upload.isPending}
        className="mt-3 inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
      >
        {upload.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
        {t('invoice.bulkUpload.submit', { count: files.length })}
      </button>

      {upload.isError && (
        <p className="mt-3 text-sm text-red-600">{t('invoice.bulkUpload.error')}</p>
      )}

      {result && (
        <div className="mt-4 space-y-2">
          <p className="text-sm font-medium text-gray-700">
            {t('invoice.bulkUpload.report', {
              uploaded: result.uploaded,
              failed: result.failed,
              total: result.totalFiles,
            })}
          </p>
          {result.documents.map((d) => (
            <div key={d.id} className="flex items-center gap-2 text-xs text-green-700">
              <CheckCircle2 className="w-3.5 h-3.5" /> {d.originalFilename}
            </div>
          ))}
          {result.errors.map((e, i) => (
            <div key={i} className="flex items-center gap-2 text-xs text-red-600">
              <XCircle className="w-3.5 h-3.5" /> {e.filename} — {e.message}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
