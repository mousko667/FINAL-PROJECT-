import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { listBackups, createBackup, restoreBackup, getBackupStatus, getAuditLogs } from '@/api/backups'
import { Loader2, HardDrive, Download, RotateCcw, Plus, AlertTriangle, Activity, CheckCircle2, XCircle } from 'lucide-react'

export default function AdminBackupsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [restoringFile, setRestoringFile] = useState<string | null>(null)

  const { data: backups, isLoading: backupsLoading } = useQuery({
    queryKey: ['backups'],
    queryFn: listBackups,
  })

  const { data: status } = useQuery({
    queryKey: ['backup-status'],
    queryFn: getBackupStatus,
  })

  const { data: auditLogs, isLoading: auditLogsLoading } = useQuery({
    queryKey: ['backup-audit-logs'],
    queryFn: getAuditLogs,
  })

  const createMutation = useMutation({
    mutationFn: createBackup,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['backups'] })
      queryClient.invalidateQueries({ queryKey: ['backup-status'] })
      queryClient.invalidateQueries({ queryKey: ['backup-audit-logs'] })
    },
  })

  const restoreMutation = useMutation({
    mutationFn: restoreBackup,
    onSuccess: () => {
      setRestoringFile(null)
      queryClient.invalidateQueries({ queryKey: ['backup-status'] })
      queryClient.invalidateQueries({ queryKey: ['backup-audit-logs'] })
    },
  })

  const handleRestore = () => {
    if (restoringFile) {
      restoreMutation.mutate(restoringFile)
    }
  }

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="max-w-4xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
              <HardDrive className="w-6 h-6 text-blue-600" />
              {t('admin.backups.title')}
            </h1>
            <p className="text-sm text-gray-500 mt-1">
              {t('admin.backups.description')}
            </p>
          </div>
          <button
            onClick={() => createMutation.mutate()}
            disabled={createMutation.isPending}
            className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
            {t('admin.backups.createBtn')}
          </button>
        </div>

        {/* Status Card */}
        {status && (
          <div className={`p-4 rounded-lg border flex items-center justify-between ${
            status.status === 'OK' ? 'bg-green-50 border-green-200' : 
            status.status === 'FAILED' ? 'bg-red-50 border-red-200' : 'bg-gray-50 border-gray-200'
          }`}>
            <div>
              <p className="font-semibold text-sm">
                {t('admin.backups.lastStatus')} :{' '}
                <span className={status.status === 'OK' ? 'text-green-700' : status.status === 'FAILED' ? 'text-red-700' : 'text-gray-700'}>
                  {status.status}
                </span>
              </p>
              <p className="text-xs text-gray-600 mt-1">{status.detail}</p>
            </div>
            {status.lastBackupAt && (
              <div className="text-right text-sm text-gray-500">
                {new Date(status.lastBackupAt).toLocaleString('fr-FR', {
                  day: '2-digit', month: 'short', year: 'numeric',
                  hour: '2-digit', minute: '2-digit'
                })}
              </div>
            )}
          </div>
        )}

        <div className="bg-white rounded-lg shadow border overflow-hidden">
          {backupsLoading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            </div>
          ) : !backups || backups.length === 0 ? (
            <div className="p-8 text-center text-gray-500">
              {t('admin.backups.empty')}
            </div>
          ) : (
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="px-6 py-3 font-medium text-gray-500">{t('admin.backups.filename')}</th>
                  <th className="px-6 py-3 font-medium text-gray-500 text-right">{t('common.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {backups.map((filename) => (
                  <tr key={filename} className="hover:bg-gray-50">
                    <td className="px-6 py-4 font-medium text-gray-900 flex items-center gap-2">
                      <Download className="w-4 h-4 text-gray-400" />
                      {filename}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => setRestoringFile(filename)}
                        className="text-red-600 hover:text-red-800 font-medium flex items-center gap-1 justify-end w-full"
                      >
                        <RotateCcw className="w-4 h-4" />
                        {t('admin.backups.restoreBtn')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {/* Audit History Card */}
        <div className="bg-white rounded-lg shadow border overflow-hidden mt-8">
          <div className="px-6 py-4 border-b border-gray-200 flex items-center gap-2">
            <Activity className="w-5 h-5 text-gray-500" />
            <h2 className="text-lg font-semibold text-gray-900">
              Historique des opérations
            </h2>
          </div>
          {auditLogsLoading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            </div>
          ) : !auditLogs || auditLogs.length === 0 ? (
            <div className="p-8 text-center text-gray-500">
              Aucun historique d'opération.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm whitespace-nowrap">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="px-6 py-3 font-medium text-gray-500">Date</th>
                    <th className="px-6 py-3 font-medium text-gray-500">Opération</th>
                    <th className="px-6 py-3 font-medium text-gray-500">Statut</th>
                    <th className="px-6 py-3 font-medium text-gray-500">Fichier</th>
                    <th className="px-6 py-3 font-medium text-gray-500">Déclenché par</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {auditLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50">
                      <td className="px-6 py-3 text-gray-500">
                        {new Date(log.createdAt).toLocaleString('fr-FR', {
                          day: '2-digit', month: '2-digit', year: 'numeric',
                          hour: '2-digit', minute: '2-digit', second: '2-digit'
                        })}
                      </td>
                      <td className="px-6 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                          log.operation === 'CREATE' ? 'bg-blue-100 text-blue-800' :
                          log.operation === 'RESTORE' ? 'bg-purple-100 text-purple-800' :
                          'bg-gray-100 text-gray-800'
                        }`}>
                          {log.operation}
                        </span>
                      </td>
                      <td className="px-6 py-3 flex items-center gap-1">
                        {log.status === 'OK' ? (
                          <><CheckCircle2 className="w-4 h-4 text-green-500" /> <span className="text-green-700">OK</span></>
                        ) : (
                          <><XCircle className="w-4 h-4 text-red-500" /> <span className="text-red-700">FAILED</span></>
                        )}
                        {log.errorMessage && (
                          <span className="text-xs text-red-500 ml-2 truncate max-w-xs" title={log.errorMessage}>
                            ({log.errorMessage})
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-3 text-gray-900 truncate max-w-xs" title={log.filename || ''}>
                        {log.filename || '-'}
                      </td>
                      <td className="px-6 py-3 text-gray-500">
                        {log.triggeredBy}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {/* Restore Modal */}
      {restoringFile && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
          <div className="bg-white rounded-lg shadow-lg w-full max-w-md p-6">
            <div className="flex items-center gap-3 text-red-600 mb-4">
              <AlertTriangle className="w-6 h-6" />
              <h2 className="text-lg font-semibold">{t('admin.backups.restoreConfirmTitle')}</h2>
            </div>
            <p className="text-sm text-gray-600 mb-6">
              {t('admin.backups.restoreConfirmDesc', { file: restoringFile })}
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setRestoringFile(null)}
                disabled={restoreMutation.isPending}
                className="px-4 py-2 text-sm border rounded font-medium hover:bg-gray-50"
              >
                {t('common.cancel')}
              </button>
              <button
                onClick={handleRestore}
                disabled={restoreMutation.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded font-medium hover:bg-red-700 flex items-center gap-2"
              >
                {restoreMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('admin.backups.restoreBtn')}
              </button>
            </div>
          </div>
        </div>
      )}
    </PageRoleGuard>
  )
}
