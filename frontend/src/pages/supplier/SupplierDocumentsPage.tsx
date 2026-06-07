import { useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Upload, FileText, Loader2, AlertCircle } from 'lucide-react'

interface SupplierDocument {
  id: string
  filename?: string
  originalFilename?: string
  documentType: string
  uploadedAt?: string
  fileSizeBytes?: number
}

const DOC_TYPES = ['TAX_CERTIFICATE', 'CONTRACT', 'OTHER'] as const
type DocType = typeof DOC_TYPES[number]

export default function SupplierDocumentsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const fileRef = useRef<HTMLInputElement>(null)
  const [docType, setDocType] = useState<DocType>('TAX_CERTIFICATE')
  const [error, setError] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['supplier-documents'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierDocument[] }>('/supplier/documents')
      return data.data ?? []
    },
  })

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const fd = new FormData()
      fd.append('file', file)
      fd.append('documentType', docType)
      await apiClient.post('/supplier/documents', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['supplier-documents'] })
      setError(null)
      if (fileRef.current) fileRef.current.value = ''
    },
    onError: () => {
      setError('Failed to upload document. Please try again.')
    },
  })

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    uploadMutation.mutate(file)
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">{t('supplier.documents.title')}</h1>
        <p className="text-sm text-gray-500 mt-1">{t('supplier.documents.subtitle')}</p>
      </div>

      {/* Upload section */}
      <div className="bg-white rounded-xl border p-5 space-y-4">
        <h2 className="font-semibold text-gray-900">{t('supplier.documents.upload')}</h2>

        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('supplier.documents.type')}</label>
            <select
              value={docType}
              onChange={e => setDocType(e.target.value as DocType)}
              className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-white"
            >
              {DOC_TYPES.map(dt => (
                <option key={dt} value={dt}>{t(`supplier.documents.types.${dt}`)}</option>
              ))}
            </select>
          </div>

          <label className="cursor-pointer">
            <input
              ref={fileRef}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png,.tiff,.tif,.doc,.docx,.xlsx"
              className="hidden"
              onChange={handleFileChange}
              disabled={uploadMutation.isPending}
            />
            <span className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium border transition-colors ${uploadMutation.isPending ? 'opacity-60 cursor-not-allowed bg-gray-50' : 'bg-primary text-primary-foreground hover:bg-primary/90 cursor-pointer'}`}>
              {uploadMutation.isPending
                ? <><Loader2 className="w-4 h-4 animate-spin" /> Uploading…</>
                : <><Upload className="w-4 h-4" /> {t('supplier.documents.upload')}</>}
            </span>
          </label>
        </div>

        {error && (
          <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-4 py-3">
            <AlertCircle className="w-4 h-4 shrink-0" />
            {error}
          </div>
        )}

        <p className="text-xs text-gray-400">
          Accepted: PDF, Word, Excel, JPEG, PNG, TIFF. Max 10 MB.
        </p>
      </div>

      {/* Documents list */}
      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : !data?.length ? (
          <div className="py-16 text-center">
            <FileText className="w-8 h-8 text-gray-300 mx-auto mb-3" />
            <p className="text-sm text-gray-500">{t('supplier.documents.noDocuments')}</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('supplier.documents.filename')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('supplier.documents.type')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('supplier.documents.uploaded')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.map(doc => (
                <tr key={doc.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-medium text-gray-800">
                    <div className="flex items-center gap-2">
                      <FileText className="w-4 h-4 text-gray-400 shrink-0" />
                      {doc.originalFilename ?? doc.filename ?? 'Document'}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded">
                      {t(`supplier.documents.types.${doc.documentType}`, doc.documentType)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500 text-xs">
                    {doc.uploadedAt ? new Date(doc.uploadedAt).toLocaleDateString() : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
