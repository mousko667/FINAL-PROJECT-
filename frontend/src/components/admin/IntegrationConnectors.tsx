import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Plug, Plus, Trash2, Loader2, Wifi, RefreshCw, Clock } from 'lucide-react'
import { formatDateTime } from '@/lib/format'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { notifyApiError } from '@/components/ErrorToaster'

interface Connector {
  id: string; name: string; type: string; endpoint: string | null; enabled: boolean
  lastStatus: string | null; lastMessage: string | null; lastCheckedAt: string | null
  syncIntervalMinutes: number | null; lastSyncAt: string | null
  lastSyncStatus: string | null; lastSyncMessage: string | null
}

const TYPES = ['MOCK', 'ERP', 'ACCOUNTING', 'BANKING', 'DMS']

/** M12 — configurable integration connectors (ADMIN). Create / test / enable / delete. */
export function IntegrationConnectors() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [type, setType] = useState('MOCK')
  const [endpoint, setEndpoint] = useState('')
  const [testing, setTesting] = useState<string | null>(null)
  const [syncing, setSyncing] = useState<string | null>(null)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  const { data: connectors = [], isLoading } = useQuery<Connector[]>({
    queryKey: ['integration-connectors'],
    queryFn: async () => (await apiClient.get<{ data: Connector[] }>('/integrations/connectors')).data.data ?? [],
  })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['integration-connectors'] })

  const create = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => apiClient.post('/integrations/connectors', { name, type, endpoint: endpoint || null }),
    onSuccess: () => { setName(''); setEndpoint(''); setType('MOCK'); invalidate() },
  })
  const test = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: (id: string) => apiClient.post(`/integrations/connectors/${id}/test`),
    onSettled: () => { setTesting(null); invalidate() },
  })
  const del = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: (id: string) => apiClient.delete(`/integrations/connectors/${id}`),
    onSuccess: () => { invalidate(); setDeleteTargetId(null) },
  })
  const syncNow = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: (id: string) => apiClient.post(`/integrations/connectors/${id}/sync`),
    onSettled: () => { setSyncing(null); invalidate() },
  })
  const updateSchedule = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: ({ id, minutes }: { id: string; minutes: number | null }) =>
      apiClient.put(`/integrations/connectors/${id}/sync-schedule`, { syncIntervalMinutes: minutes }),
    onSuccess: invalidate,
  })

  const inputCls = 'border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'
  const statusCls = (s: string | null) =>
    s === 'UP' ? 'bg-pos-bg text-pos' : s === 'DOWN' ? 'bg-crit-bg text-crit' : 'bg-ground text-ink-soft'

  return (
    <div className="bg-surface rounded-[4px] border border-hairline p-5">
      <div className="flex items-center gap-2 mb-3">
        <Plug className="w-5 h-5 text-primary" />
        <h2 className="font-semibold text-ink">{t('admin.connectors.title', 'Connecteurs d\'intégration')}</h2>
      </div>
      <p className="text-sm text-ink-soft mb-4">{t('admin.connectors.subtitle', 'Connectez des systèmes externes (ERP, comptabilité, banque, GED). Le type MOCK simule une connexion.')}</p>

      <div className="flex flex-wrap items-end gap-2 mb-4">
        <input value={name} onChange={e => setName(e.target.value)} placeholder={t('admin.connectors.name', 'Nom')} className={`${inputCls} flex-1 min-w-[140px]`} />
        <select value={type} onChange={e => setType(e.target.value)} aria-label={t('admin.connectors.type', 'Connector type')} className={inputCls}>
          {TYPES.map(ty => <option key={ty} value={ty}>{ty}</option>)}
        </select>
        <input value={endpoint} onChange={e => setEndpoint(e.target.value)} placeholder={t('admin.connectors.endpoint', 'Endpoint (URL, optionnel pour MOCK)')} className={`${inputCls} flex-1 min-w-[180px]`} />
        <button onClick={() => create.mutate()} disabled={!name.trim() || create.isPending}
          className="inline-flex items-center gap-1.5 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
          {create.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}{t('app.add', 'Ajouter')}
        </button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-6"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
      ) : connectors.length === 0 ? (
        <p className="text-sm text-ink-faint py-2">{t('admin.connectors.none', 'Aucun connecteur.')}</p>
      ) : (
        <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead><tr className="border-b text-left text-ink-soft">
            <th className="px-3 py-2 font-medium">{t('admin.connectors.name', 'Nom')}</th>
            <th className="px-3 py-2 font-medium">Type</th>
            <th className="px-3 py-2 font-medium">{t('admin.connectors.status', 'Statut')}</th>
            <th className="px-3 py-2 font-medium">{t('admin.connectors.sync', 'Synchronisation')}</th>
            <th className="px-3 py-2 font-medium text-right">Actions</th>
          </tr></thead>
          <tbody>
            {connectors.map(c => (
              <tr key={c.id} className="border-b last:border-0">
                <td className="px-3 py-2"><div className="font-medium text-ink">{c.name}</div><div className="text-xs text-ink-faint truncate max-w-xs">{c.endpoint}</div></td>
                <td className="px-3 py-2">{c.type}</td>
                <td className="px-3 py-2">
                  <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${statusCls(c.lastStatus)}`}>{c.lastStatus ?? 'UNKNOWN'}</span>
                  {c.lastMessage && <div className="text-xs text-ink-faint mt-0.5">{c.lastMessage}</div>}
                </td>
                <td className="px-3 py-2">
                  <div className="flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 text-ink-faint" />
                    <input type="number" min={1} defaultValue={c.syncIntervalMinutes ?? ''}
                      placeholder={t('admin.connectors.off', 'Off')}
                      onBlur={e => {
                        const v = e.target.value.trim()
                        const minutes = v === '' ? null : Number(v)
                        if (minutes !== c.syncIntervalMinutes) updateSchedule.mutate({ id: c.id, minutes })
                      }}
                      className={`${inputCls} w-20 py-1`}
                      title={t('admin.connectors.intervalHint', 'Intervalle en minutes (vide = désactivé)')} />
                    <span className="text-xs text-ink-faint">min</span>
                  </div>
                  {c.lastSyncStatus && (
                    <div className={`text-xs mt-0.5 ${c.lastSyncStatus === 'SUCCESS' ? 'text-pos' : 'text-crit'}`}>
                      {c.lastSyncStatus === 'SUCCESS'
                        ? t('admin.connectors.syncOk', 'Dernière synchro OK')
                        : t('admin.connectors.syncFail', 'Échec synchro')}
                      {c.lastSyncAt && ` · ${formatDateTime(c.lastSyncAt)}`}
                    </div>
                  )}
                </td>
                <td className="px-3 py-2 text-right">
                  <div className="flex items-center gap-3 justify-end">
                    <button onClick={() => { setSyncing(c.id); syncNow.mutate(c.id) }} disabled={syncing === c.id}
                      className="text-primary hover:text-primary/80" title={t('admin.connectors.syncNow', 'Synchroniser maintenant')}>
                      {syncing === c.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                    </button>
                    <button onClick={() => { setTesting(c.id); test.mutate(c.id) }} disabled={testing === c.id}
                      className="text-primary hover:text-primary/80" title={t('admin.connectors.test', 'Tester')}>
                      {testing === c.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Wifi className="w-4 h-4" />}
                    </button>
                    <button onClick={() => setDeleteTargetId(c.id)} className="text-ink-faint hover:text-crit" title={t('app.delete', 'Supprimer')}>
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        </div>
      )}

      <ConfirmDialog
        open={deleteTargetId !== null}
        title={t('admin.connectors.deleteConfirmTitle', 'Delete this connector?')}
        message={t('admin.connectors.deleteConfirmBody', 'Scheduled synchronisation for this connector will stop immediately.')}
        variant="danger"
        onConfirm={() => { if (deleteTargetId) del.mutate(deleteTargetId) }}
        onCancel={() => setDeleteTargetId(null)}
      />
    </div>
  )
}
