import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, Plus, Trash2, Zap, CheckCircle } from 'lucide-react'

interface Webhook {
  id: string
  url: string
  eventType: string
  isActive: boolean
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

  const { data: webhooks, isLoading } = useQuery({
    queryKey: ['webhooks'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Webhook[] }>('/integrations/webhooks')
      return data.data ?? []
    },
  })

  const createMutation = useMutation({
    mutationFn: () => apiClient.post('/integrations/webhooks', { url, eventType: event }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks'] })
      setShowForm(false)
      setUrl('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/integrations/webhooks/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['webhooks'] }),
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
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => deleteMutation.mutate(wh.id)}
                      disabled={deleteMutation.isPending}
                      className="flex items-center gap-1.5 ml-auto px-3 py-1.5 text-sm text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      <Trash2 className="w-3.5 h-3.5" /> {t('admin.integrations.delete')}
                    </button>
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
