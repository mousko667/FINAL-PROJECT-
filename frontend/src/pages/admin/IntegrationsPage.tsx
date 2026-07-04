import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, Plus, Trash2, Zap, CheckCircle, XCircle, Activity, ScrollText } from 'lucide-react'
import { IntegrationConnectors } from '@/components/admin/IntegrationConnectors'
import { formatDateTime } from '@/lib/format'

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

export default function IntegrationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [url, setUrl] = useState('')
  const [event, setEvent] = useState('INVOICE_SUBMITTED')
  const [logsWebhookId, setLogsWebhookId] = useState<string | null>(null)

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
    },
  })

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('admin.integrations.title')}</h1>
          <p className="text-sm text-gray-500 mt-1">{t('admin.integrations.subtitle')}</p>
        </div>
        <button
          onClick={() => setShowForm(true)}
          className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" /> {t('admin.integrations.addWebhook')}
        </button>
      </div>

      {/* M12: configurable integration connectors */}
      <IntegrationConnectors />

      {/* Integration health */}
      {(status?.length ?? 0) > 0 && (
        <div className="bg-white rounded-xl border p-5">
          <h2 className="font-semibold text-gray-900 flex items-center gap-2 mb-3">
            <Activity className="w-4 h-4 text-primary" /> {t('admin.integrations.healthTitle', 'Integration Health')}
          </h2>
          <div className="space-y-2">
            {(status ?? []).map(s => (
              <div key={s.id} className="flex items-center gap-3 text-sm">
                {s.lastDeliverySuccess === undefined || s.lastDeliveredAt == null
                  ? <span className="w-2.5 h-2.5 rounded-full bg-gray-300 shrink-0" title={t('admin.integrations.neverDelivered', 'No delivery yet')} />
                  : s.lastDeliverySuccess
                    ? <CheckCircle className="w-4 h-4 text-green-500 shrink-0" />
                    : <XCircle className="w-4 h-4 text-red-500 shrink-0" />}
                <span className="font-mono text-xs text-gray-700 truncate flex-1">{s.url}</span>
                {s.lastDeliveredAt
                  ? <span className="text-xs text-gray-400">
                      {t('admin.integrations.lastDelivery', 'Last')}: {formatDateTime(s.lastDeliveredAt)}
                      {s.lastResponseStatus != null && ` · HTTP ${s.lastResponseStatus}`}
                    </span>
                  : <span className="text-xs text-gray-400">{t('admin.integrations.neverDelivered', 'No delivery yet')}</span>}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Add webhook form */}
      {showForm && (
        <div className="bg-white rounded-xl border p-5 space-y-4">
          <h2 className="font-semibold text-gray-900">{t('admin.integrations.addWebhook')}</h2>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.integrations.webhookUrl')}</label>
            <input
              type="url"
              value={url}
              onChange={e => setUrl(e.target.value)}
              placeholder="https://your-server.com/webhooks/oct"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.integrations.webhookEvent')}</label>
            <select
              value={event}
              onChange={e => setEvent(e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              {EVENT_TYPES.map(ev => <option key={ev} value={ev}>{ev}</option>)}
            </select>
          </div>
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowForm(false)} className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">{t('app.cancel')}</button>
            <button
              onClick={() => createMutation.mutate()}
              disabled={!url || createMutation.isPending}
              className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
            >
              {createMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
              {t('app.save')}
            </button>
          </div>
        </div>
      )}

      {/* Webhooks list */}
      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : !webhooks?.length ? (
          <div className="py-16 text-center">
            <Zap className="w-8 h-8 text-gray-300 mx-auto mb-3" />
            <p className="text-sm text-gray-500">{t('admin.integrations.noWebhooks')}</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.integrations.webhookUrl')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.integrations.webhookEvent')}</th>
                <th className="text-center px-4 py-3 font-medium text-gray-600">{t('admin.integrations.webhookActive')}</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">{t('app.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {webhooks.map(wh => (
                <tr key={wh.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 font-mono text-xs text-gray-700 max-w-[240px] truncate">{wh.url}</td>
                  <td className="px-4 py-3">
                    <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded border border-blue-100 font-mono">{wh.eventType}</span>
                  </td>
                  <td className="px-4 py-3 text-center">
                    {wh.isActive
                      ? <CheckCircle className="w-4 h-4 text-green-500 mx-auto" />
                      : <span className="w-4 h-4 rounded-full bg-gray-200 inline-block" />}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2 justify-end">
                      <button
                        onClick={() => setLogsWebhookId(logsWebhookId === wh.id ? null : wh.id)}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 border rounded-lg hover:bg-gray-50 transition-colors"
                      >
                        <ScrollText className="w-3.5 h-3.5" /> {t('admin.integrations.viewLogs', 'Logs')}
                      </button>
                      <button
                        onClick={() => deleteMutation.mutate(wh.id)}
                        disabled={deleteMutation.isPending}
                        className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
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
        <div className="bg-white rounded-xl border overflow-hidden">
          <div className="flex items-center justify-between px-5 py-3 border-b bg-gray-50">
            <h2 className="font-semibold text-gray-900 flex items-center gap-2 text-sm">
              <ScrollText className="w-4 h-4 text-gray-500" /> {t('admin.integrations.deliveryLog', 'Delivery log')}
            </h2>
            <button onClick={() => setLogsWebhookId(null)} className="text-xs text-gray-400 hover:text-gray-600">{t('app.close')}</button>
          </div>
          {deliveriesLoading ? (
            <div className="flex justify-center py-8"><Loader2 className="w-5 h-5 animate-spin text-gray-400" /></div>
          ) : !deliveries?.length ? (
            <p className="py-8 text-center text-sm text-gray-400">{t('admin.integrations.noDeliveries', 'No deliveries recorded for this webhook.')}</p>
          ) : (
            <table className="w-full text-sm">
              <thead className="bg-gray-50/50">
                <tr className="text-gray-600">
                  <th className="text-left px-4 py-2.5 font-medium">{t('admin.integrations.deliveryEvent', 'Event')}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t('admin.integrations.deliveryStatus', 'HTTP')}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t('admin.integrations.deliveryAttempts', 'Attempts')}</th>
                  <th className="text-center px-4 py-2.5 font-medium">{t('admin.integrations.deliveryResult', 'Result')}</th>
                  <th className="text-left px-4 py-2.5 font-medium">{t('admin.integrations.deliveryTime', 'Last attempt')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {deliveries.map(d => (
                  <tr key={d.id} className="hover:bg-gray-50">
                    <td className="px-4 py-2.5 font-mono text-xs text-gray-700">{d.eventType}</td>
                    <td className="px-4 py-2.5 text-center text-xs text-gray-600">{d.responseStatus ?? '—'}</td>
                    <td className="px-4 py-2.5 text-center text-xs text-gray-600">{d.attemptCount}</td>
                    <td className="px-4 py-2.5 text-center">
                      {d.success
                        ? <CheckCircle className="w-4 h-4 text-green-500 mx-auto" />
                        : <XCircle className="w-4 h-4 text-red-500 mx-auto" />}
                    </td>
                    <td className="px-4 py-2.5 text-xs text-gray-400">
                      {formatDateTime(d.lastAttemptedAt ?? d.createdAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  )
}
