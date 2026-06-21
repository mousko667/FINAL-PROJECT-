import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Link } from 'react-router-dom'
import { Loader2, CheckCircle, Trash2, ShieldCheck } from 'lucide-react'

interface PendingDocument {
  id: string
  invoiceId: string | null
  originalFilename: string
  uploadedAt: string
  retentionDisposition: 'PENDING' | 'RETAINED' | 'PURGED'
}

export default function AdminRetentionDispositionPage() {
  const { t, i18n } = useTranslation()

  const { data: docs, isLoading, isError } = useQuery({
    queryKey: ['retention-pending-documents'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PendingDocument[] }>('/retention/pending-documents')
      return data.data
    },
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="max-w-4xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('retentionDisposition.title', 'Contrôles de purge')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {t('retentionDisposition.subtitle', 'Documents de facture ayant dépassé la durée de rétention et en attente de décision.')}
          </p>
        </div>

        <p className="text-xs text-gray-500 bg-gray-50 border rounded-lg p-3">
          {t('retentionDisposition.note', "« Purger » est un marquage de conformité : le fichier n’est pas supprimé physiquement du stockage. La décision est tracée dans l’audit.")}
        </p>

        {isLoading ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : isError ? (
          <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
            {t('retentionDisposition.loadError', 'Échec du chargement des documents à traiter.')}
          </p>
        ) : !docs || docs.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 py-16 text-center">
            <CheckCircle className="w-10 h-10 text-green-500" />
            <p className="text-sm font-medium text-gray-800">{t('retentionDisposition.empty', 'Aucun document à traiter.')}</p>
            <p className="text-xs text-gray-500">{t('retentionDisposition.emptyHint', 'Tous les documents archivés sont dans leur durée de rétention.')}</p>
          </div>
        ) : (
          <div className="bg-white rounded-xl border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 text-left text-xs text-gray-500">
                <tr>
                  <th className="px-4 py-2 font-medium">{t('retentionDisposition.colFile', 'Fichier')}</th>
                  <th className="px-4 py-2 font-medium">{t('retentionDisposition.colInvoice', 'Facture')}</th>
                  <th className="px-4 py-2 font-medium">{t('retentionDisposition.colUploaded', 'Déposé le')}</th>
                  <th className="px-4 py-2 font-medium text-right">{t('retentionDisposition.colActions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody>
                {docs.map(doc => (
                  <tr key={doc.id} className="border-t">
                    <td className="px-4 py-2 text-gray-800">{doc.originalFilename}</td>
                    <td className="px-4 py-2">
                      {doc.invoiceId
                        ? <Link to={`/invoices/${doc.invoiceId}`} className="text-primary hover:underline">{doc.invoiceId.slice(0, 8)}</Link>
                        : <span className="text-gray-400">—</span>}
                    </td>
                    <td className="px-4 py-2 text-gray-600">{new Date(doc.uploadedAt).toLocaleString(i18n.language)}</td>
                    <td className="px-4 py-2">
                      <div className="flex items-center justify-end gap-2">
                        <button className="px-3 py-1 text-xs rounded-md border hover:bg-gray-50">
                          {t('retentionDisposition.retain', 'Conserver')}
                        </button>
                        <button className="flex items-center gap-1 px-3 py-1 text-xs rounded-md bg-red-600 text-white hover:bg-red-700">
                          <Trash2 className="w-3.5 h-3.5" />{t('retentionDisposition.purge', 'Purger')}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        <p className="flex items-center gap-1.5 text-xs text-gray-400">
          <ShieldCheck className="w-3.5 h-3.5" />
          {t('retentionDisposition.note', '« Purger » est un marquage de conformité ; aucune suppression physique.')}
        </p>
      </div>
    </PageRoleGuard>
  )
}
