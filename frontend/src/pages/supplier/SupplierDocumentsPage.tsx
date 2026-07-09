import { useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Upload, FileText, Loader2, AlertCircle } from 'lucide-react'
import { formatDate } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'

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
      setError(t('supplier.documents.uploadError'))
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
        <h1 className="text-2xl font-bold text-ink">{t('supplier.documents.title')}</h1>
        <p className="text-sm text-ink-soft mt-1">{t('supplier.documents.subtitle')}</p>
      </div>

      {/* Upload section */}
      <Panel className="p-5 space-y-4">
        <h2 className="font-semibold text-ink">{t('supplier.documents.upload')}</h2>

        <div className="flex flex-wrap gap-3 items-end">
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.documents.type')}</label>
            <select
              value={docType}
              onChange={e => setDocType(e.target.value as DocType)}
              className="border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface text-ink"
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
            <span className={`flex items-center gap-2 px-4 py-2 rounded-[4px] text-sm font-medium border border-hairline transition-colors ${uploadMutation.isPending ? 'opacity-60 cursor-not-allowed bg-ground' : 'bg-primary text-primary-foreground hover:bg-primary/90 cursor-pointer'}`}>
              {uploadMutation.isPending
                ? <><Loader2 className="w-4 h-4 animate-spin" /> {t('supplier.documents.uploading')}</>
                : <><Upload className="w-4 h-4" /> {t('supplier.documents.upload')}</>}
            </span>
          </label>
        </div>

        {error && (
          <div className="flex items-center gap-2 text-sm text-crit bg-crit-bg border border-crit/30 rounded-[4px] px-4 py-3">
            <AlertCircle className="w-4 h-4 shrink-0" />
            {error}
          </div>
        )}

        <p className="text-xs text-ink-faint">
          {t('supplier.documents.accepted')}
        </p>
      </Panel>

      {/* Documents list */}
      <Panel className="overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : !data?.length ? (
          <div className="py-16 text-center">
            <FileText className="w-8 h-8 text-ink-faint mx-auto mb-3" />
            <p className="text-sm text-ink-soft">{t('supplier.documents.noDocuments')}</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-ground">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('supplier.documents.filename')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('supplier.documents.type')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('supplier.documents.uploaded')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {data.map(doc => (
                <tr key={doc.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                  <td className="px-4 py-3 font-medium text-ink">
                    <div className="flex items-center gap-2">
                      <FileText className="w-4 h-4 text-ink-faint shrink-0" />
                      {doc.originalFilename ?? doc.filename ?? 'Document'}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className="text-xs bg-ground text-ink-soft px-2 py-0.5 rounded border border-hairline">
                      {t(`supplier.documents.types.${doc.documentType}`, doc.documentType)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-ink-soft text-xs num">
                    {doc.uploadedAt ? formatDate(doc.uploadedAt) : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </div>
  )
}
