import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, Plus, Trash2, Zap, CheckCircle, XCircle, Activity, ScrollText } from 'lucide-react'
import { IntegrationConnectors } from '@/components/admin/IntegrationConnectors'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageHeader } from '@/components/ui/PageHeader'
import { formatDateTime } from '@/lib/format'
import { PageRoleGuard } from '@/components/auth/RoleGuard'

const ADMIN_ROLES = ['ROLE_ADMIN']

interface Webhook {
  id: string
  url: string
  eventType: string
  isActive: boolean
  createdAt: string
}

interface IntegrationStatus {
  id: string
  name?: string
  url: string
  events?: string[]
  isActive: boolean
  lastResponseStatus?: number
  lastDeliverySuccess?: boolean
  lastDeliveredAt?: string
}

interface Delivery {
  id: string
  eventType: string
  responseStatus?: number
  attemptCount: number
  success: boolean
  lastAttemptedAt?: string
  createdAt: string
}

const EVENT_TYPES = [
  'INVOICE_SUBMITTED', 'INVOICE_VALIDATED', 'INVOICE_REJECTED',
  'INVOICE_APPROVED', 'INVOICE_PAID', 'INVOICE_ARCHIVED',
]

function IntegrationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [url, setUrl] = useState('')
  const [event, setEvent] = useState('INVOICE_SUBMITTED')
  const [logsWebhookId, setLogsWebhookId] = useState<string | null>(null)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  const { data: webhooks, isLoading } = useQuery({
    queryKey: ['webhooks'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Webhook[] }>('/integrations/webhooks')
      return data.data ?? []
    },
  })

  const { data: status } = useQuery({
    queryKey: ['integration-status'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: IntegrationStatus[] }>('/integrations/status')
      return data.data ?? []
    },
  })

  const { data: deliveries, isLoading: deliveriesLoading } = useQuery({
    queryKey: ['webhook-deliveries', logsWebhookId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Delivery[] } }>(
        `/integrations/webhooks/${logsWebhookId}/deliveries`, { params: { size: 20 } })
      return data.data?.content ?? []
    },
    enabled: !!logsWebhookId,
  })

  const createMutation = useMutation({
    mutationFn: () => apiClient.post('/integrations/webhooks', { url, eventType: event }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks'] })
      queryClient.invalidateQueries({ queryKey: ['integration-status'] })
      setShowForm(false)
      setUrl('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/integrations/webhooks/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks'] })
      queryClient.invalidateQueries({ queryKey: ['integration-status'] })
      setDeleteTargetId(null)
    },
  })

  return (
    <div className="space-y-6 max-w-3xl">
      <PageHeader
        title={t('admin.integrations.title')}
        subtitle={t('admin.integrations.subtitle')}
        actions={
          <button
            onClick={() => setShowForm(true)}
            className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] text-sm font-medium hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" /> {t('admin.integrations.addWebhook')}
          </button>
        }
      />

      {/* M12: configurable integration connectors */}
      <IntegrationConnectors />

      {/* Integration health */}
      {(status?.length ?? 0) > 0 && (
        <div className="bg-surface rounded-[4px] border border-hairline p-5">
          <h2 className="font-semibold text-ink flex items-center gap-2 mb-3">
            <Activity className="w-4 h-4 text-primary" /> {t('admin.integrations.healthTitle', 'Integration Health')}
          </h2>
          <div className="space-y-2">
            {(status ?? []).map(s => (
              <div key={s.id} className="flex items-center gap-3 text-sm">
                {s.lastDeliverySuccess === undefined || s.lastDeliveredAt == null
                  ? <span className="w-2.5 h-2.5 rounded-full bg-hairline-strong shrink-0" title={t('admin.integrations.neverDelivered', 'No delivery yet')} />
                  : s.lastDeliverySuccess
                    ? <CheckCircle className="w-4 h-4 text-pos shrink-0" />
                    : <XCircle className="w-4 h-4 text-crit shrink-0" />}
                <span className="num text-xs text-ink-soft truncate flex-1">{s.url}</span>
                {s.lastDeliveredAt
                  ? <span className="text-xs text-ink-faint">
                      {t('admin.integrations.lastDelivery', 'Last')}: {formatDateTime(s.lastDeliveredAt)}
                      {s.lastResponseStatus != null && ` · HTTP ${s.lastResponseStatus}`}
                    </span>
                  : <span className="text-xs text-ink-faint">{t('admin.integrations.neverDelivered', 'No delivery yet')}</span>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add webhook form */}
      {showForm && (
        <div className="bg-surface rounded-[4px] border border-hairline p-5 space-y-4">
          <h2 className="font-semibold text-ink">{t('admin.integrations.addWebhook')}</h2>
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.integrations.webhookUrl')}</label>
            <input
              type="url"
              value={url}
              onChange={e => setUrl(e.target.value)}
              placeholder="https://your-server.com/webhooks/oct"
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.integrations.webhookEvent')}</label>
            <select
              value={event}
              onChange={e => setEvent(e.target.value)}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              {EVENT_TYPES.map(ev => <option key={ev} value={ev}>{ev}</option>)}
            </select>
          </div>
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowForm(false)} className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">{t('app.cancel')}</button>
            <button
              onClick={() => createMutation.mutate()}
              disabled={!url || createMutation.isPending}
              className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
            >
              {createMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
              {t('app.save')}
            </button>
          </div>
        </div>
      )}

      {/* Webhooks list */}
      <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : !webhooks?.length ? (
          <div className="py-16 text-center">
            <Zap className="w-8 h-8 text-ink-faint mx-auto mb-3" />
            <p className="text-sm text-ink-soft">{t('admin.integrations.noWebhooks')}</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-ground border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.integrations.webhookUrl')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.integrations.webhookEvent')}</th>
                <th className="text-center px-4 py-3 font-medium text-ink-soft">{t('admin.integrations.webhookActive')}</th>
                <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('app.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {webhooks.map(wh => (
                <tr key={wh.id} className="hover:bg-ground">
                  <td className="px-4 py-3 num text-xs text-ink-soft max-w-[240px] truncate">{wh.url}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs bg-info-bg text-info px-2 py-0.5 rounded border border-info/30 num">{wh.eventType}</span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    {wh.isActive
                      ? <CheckCircle className="w-4 h-4 text-pos mx-auto" />
                      : <span className="w-4 h-4 rounded-full bg-ground inline-block" />}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2 justify-end">
                      <button
                        onClick={() => setLogsWebhookId(logsWebhookId === wh.id ? null : wh.id)}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-ink-soft border border-hairline rounded-[4px] hover:bg-ground transition-colors"
                      >
                        <ScrollText className="w-3.5 h-3.5" /> {t('admin.integrations.viewLogs', 'Logs')}
                      </button>
                      <button
                        onClick={() => setDeleteTargetId(wh.id)}
                        disabled={deleteMutation.isPending}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-crit border border-crit/30 rounded-[4px] hover:bg-crit-bg transition-colors"
                      >
                        <Trash2 className="w-3.5 h-3.5" /> {t('admin.integrations.delete')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Delivery log for the selected webhook */}
      {logsWebhookId && (
        <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
          <div className="flex items-center justify-between px-5 py-3 border-b bg-ground">
            <h2 className="font-semibold text-ink flex items-center gap-2 text-sm">
              <ScrollText className="w-4 h-4 text-ink-soft" /> {t('admin.integrations.deliveryLog', 'Delivery log')}
            </h2>
            <button onClick={() => setLogsWebhookId(null)} className="text-xs text-ink-faint hover:text-ink-soft">{t('app.close')}</button>
          </div>
          {deliveriesLoading ? (
            <div className="flex justify-center py-8"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
          ) : !deliveries?.length ? (
            <p className="py-8 text-center text-sm text-ink-faint">{t('admin.integrations.noDeliveries', 'No deliveries recorded for this webhook.')}</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-ground/50">
                <tr className="text-ink-soft">
                  <th className="text-left px-4 py-2.5 font-medium">{t('admin.integrations.deliveryEvent', 'Event')}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t('admin.integrations.deliveryStatus', 'HTTP')}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t('admin.integrations.deliveryAttempts', 'Attempts')}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t('admin.integrations.deliveryResult', 'Result')}</th>
                  <th className="text-left px-4 py-2.5 font-medium">{t('admin.integrations.deliveryTime', 'Last attempt')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {deliveries.map(d => (
                  <tr key={d.id} className="hover:bg-ground">
                    <td className="px-4 py-2.5 num text-xs text-ink-soft">{d.eventType}</td>
                    <td className="px-4 py-2.5 text-center text-xs text-ink-soft">{d.responseStatus ?? '—'}</td>
                    <td className="px-4 py-2.5 text-center text-xs text-ink-soft">{d.attemptCount}</td>
                    <td className="px-4 py-2.5 text-center">
                      {d.success
                        ? <CheckCircle className="w-4 h-4 text-pos mx-auto" />
                        : <XCircle className="w-4 h-4 text-crit mx-auto" />}
                    </td>
                    <td className="px-4 py-2.5 text-xs text-ink-faint">
                      {formatDateTime(d.lastAttemptedAt ?? d.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <ConfirmDialog
        open={deleteTargetId !== null}
        title={t('admin.integrations.deleteWebhookConfirmTitle', 'Delete this webhook?')}
        message={t('admin.integrations.deleteWebhookConfirmBody', 'Deliveries to this endpoint will stop immediately.')}
        variant="danger"
        onConfirm={() => { if (deleteTargetId) deleteMutation.mutate(deleteTargetId) }}
        onCancel={() => setDeleteTargetId(null)}
      />
    </div>
  )
}


export default function IntegrationsPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ADMIN_ROLES}>
      <IntegrationsPage />
    </PageRoleGuard>
  )
}
