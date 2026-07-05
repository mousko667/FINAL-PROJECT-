import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { listBackups, createBackup, restoreBackup, getBackupStatus, getAuditLogs } from '@/api/backups'
import { Loader2, HardDrive, Download, RotateCcw, Plus, AlertTriangle, Activity, CheckCircle2, XCircle } from 'lucide-react'
import { Panel } from '@/components/ui/Panel'

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
            <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
              <HardDrive className="w-6 h-6 text-info" />
              {t('admin.backups.title')}
            </h1>
            <p className="text-sm text-ink-soft mt-1">
              {t('admin.backups.description')}
            </p>
          </div>
          <button
            onClick={() => createMutation.mutate()}
            disabled={createMutation.isPending}
            className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 disabled:opacity-50"
          >
            {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
            {t('admin.backups.createBtn')}
          </button>
        </div>

        {/* Status Card */}
        {status && (
          <div className={`p-4 rounded-[4px] border flex items-center justify-between ${
            status.status === 'OK' ? 'bg-pos-bg border-pos/30' :
            status.status === 'FAILED' ? 'bg-crit-bg border-crit/30' : 'bg-ground border-hairline'
          }`}>
            <div>
              <p className="font-semibold text-sm text-ink">
                {t('admin.backups.lastStatus')} :{' '}
                <span className={status.status === 'OK' ? 'text-pos' : status.status === 'FAILED' ? 'text-crit' : 'text-ink-soft'}>
                  {status.status}
                </span>
              </p>
              <p className="text-xs text-ink-soft mt-1">{status.detail}</p>
            </div>
            {status.lastBackupAt && (
              <div className="text-right text-sm text-ink-soft num">
                {new Date(status.lastBackupAt).toLocaleString('fr-FR', {
                  day: '2-digit', month: 'short', year: 'numeric',
                  hour: '2-digit', minute: '2-digit'
                })}
              </div>
            )}
          </div>
        )}

        <Panel>
          {backupsLoading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
            </div>
          ) : !backups || backups.length === 0 ? (
            <div className="p-8 text-center text-ink-faint">
              {t('admin.backups.empty')}
            </div>
          ) : (
            <table className="w-full text-left text-sm">
              <thead className="bg-ground">
                <tr>
                  <th className="px-6 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.backups.filename')}</th>
                  <th className="px-6 py-3 text-right bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('app.actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {backups.map((filename) => (
                  <tr key={filename} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                    <td className="px-6 py-4 font-medium text-ink flex items-center gap-2">
                      <Download className="w-4 h-4 text-ink-faint" />
                      {filename}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => setRestoringFile(filename)}
                        className="text-crit hover:text-red-800 font-medium flex items-center gap-1 justify-end w-full"
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
        </Panel>

        {/* Audit History Card */}
        <Panel className="mt-8" title={
          <span className="flex items-center gap-2 text-base">
            <Activity className="w-5 h-5 text-ink-faint" />
            {t('admin.backups.historyTitle')}
          </span>
        }>
          <div className="-m-5">
          {auditLogsLoading ? (
            <div className="flex justify-center p-8">
              <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
            </div>
          ) : !auditLogs || auditLogs.length === 0 ? (
            <div className="p-8 text-center text-ink-faint">
              {t('admin.backups.historyEmpty')}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm whitespace-nowrap">
                <thead className="bg-ground">
                  <tr>
                    <th className="px-6 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.backups.colDate')}</th>
                    <th className="px-6 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.backups.colOperation')}</th>
                    <th className="px-6 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.backups.colStatus')}</th>
                    <th className="px-6 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.backups.colFile')}</th>
                    <th className="px-6 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.backups.colTriggeredBy')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {auditLogs.map((log) => (
                    <tr key={log.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                      <td className="px-6 py-3 text-ink-soft num">
                        {new Date(log.createdAt).toLocaleString('fr-FR', {
                          day: '2-digit', month: '2-digit', year: 'numeric',
                          hour: '2-digit', minute: '2-digit', second: '2-digit'
                        })}
                      </td>
                      <td className="px-6 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-[4px] text-xs font-medium ${
                          log.operation === 'CREATE' ? 'bg-info-bg text-info' :
                          log.operation === 'RESTORE' ? 'bg-warn-bg text-warn' :
                          'bg-ground text-ink-soft border border-hairline'
                        }`}>
                          {log.operation}
                        </span>
                      </td>
                      <td className="px-6 py-3 flex items-center gap-1">
                        {log.status === 'OK' ? (
                          <><CheckCircle2 className="w-4 h-4 text-pos" /> <span className="text-pos">OK</span></>
                        ) : (
                          <><XCircle className="w-4 h-4 text-crit" /> <span className="text-crit">FAILED</span></>
                        )}
                        {log.errorMessage && (
                          <span className="text-xs text-crit ml-2 truncate max-w-xs" title={log.errorMessage}>
                            ({log.errorMessage})
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-3 text-ink truncate max-w-xs" title={log.filename || ''}>
                        {log.filename || '-'}
                      </td>
                      <td className="px-6 py-3 text-ink-soft">
                        {log.triggeredBy}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          </div>
        </Panel>
      </div>

      {/* Restore Modal */}
      {restoringFile && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
          <div className="bg-surface rounded-[4px] border border-hairline shadow-lg w-full max-w-md p-6">
            <div className="flex items-center gap-3 text-crit mb-4">
              <AlertTriangle className="w-6 h-6" />
              <h2 className="text-lg font-semibold">{t('admin.backups.restoreConfirmTitle')}</h2>
            </div>
            <p className="text-sm text-ink-soft mb-6">
              {t('admin.backups.restoreConfirmDesc', { file: restoringFile })}
            </p>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => setRestoringFile(null)}
                disabled={restoreMutation.isPending}
                className="px-4 py-2 text-sm border border-hairline rounded-[4px] font-medium hover:bg-ground"
              >
                {t('app.cancel')}
              </button>
              <button
                onClick={handleRestore}
                disabled={restoreMutation.isPending}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-[4px] font-medium hover:bg-red-700 flex items-center gap-2"
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
