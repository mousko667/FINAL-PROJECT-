import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, Trash2, Save, X, ArrowLeft, BellRing } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'

interface AlertRule {
  id: string
  daysBeforeDue: number
  label?: string | null
  active: boolean
}

interface EditorState {
  id?: string
  daysBeforeDue: number
  label: string
  active: boolean
}

const emptyEditor = (): EditorState => ({ daysBeforeDue: 3, label: '', active: true })

export default function PaymentAlertRulesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [editor, setEditor] = useState<EditorState | null>(null)

  const { data: rules, isLoading } = useQuery({
    queryKey: ['payment-alert-rules'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: AlertRule[] }>('/payment-alert-rules')
      return data.data ?? []
    },
  })

  const saveMutation = useMutation({
    mutationFn: (state: EditorState) => {
      const body = { daysBeforeDue: state.daysBeforeDue, label: state.label || null, active: state.active }
      return state.id
        ? apiClient.put(`/payment-alert-rules/${state.id}`, body)
        : apiClient.post('/payment-alert-rules', body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payment-alert-rules'] })
      setEditor(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/payment-alert-rules/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payment-alert-rules'] }),
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE']}>
      <div className="space-y-6">
        <PageHeader
          title={
            <span className="flex items-center gap-3">
              <Link to="/payments" className="p-2 -ml-2 hover:bg-white/10 rounded-full transition-colors shrink-0">
                <ArrowLeft className="w-5 h-5 text-white/80" />
              </Link>
              {t('paymentAlerts.title', 'Payment Alert Rules')}
            </span>
          }
          subtitle={t('paymentAlerts.subtitle', 'Configure when payment due-date reminders are sent (J-N).')}
          actions={!editor && (
            <button
              onClick={() => setEditor(emptyEditor())}
              className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 text-sm font-medium"
            >
              <Plus className="w-4 h-4" />
              {t('paymentAlerts.new', 'New rule')}
            </button>
          )}
        />

        {editor ? (
          <div className="bg-surface rounded-[4px] border border-hairline p-6 space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">{t('paymentAlerts.daysBeforeDue', 'Days before due date')} *</label>
                <input
                  type="number"
                  min={0}
                  max={365}
                  value={editor.daysBeforeDue}
                  onChange={e => setEditor({ ...editor, daysBeforeDue: Number(e.target.value) })}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
                <p className="text-xs text-ink-faint mt-1">{t('paymentAlerts.daysHint', 'Alert is sent when an invoice is this many days from its due date.')}</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-ink-soft mb-1">{t('paymentAlerts.label', 'Label')}</label>
                <input
                  value={editor.label}
                  onChange={e => setEditor({ ...editor, label: e.target.value })}
                  placeholder={t('paymentAlerts.labelPlaceholder', 'e.g. Final reminder')}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm text-ink-soft">
              <input type="checkbox" checked={editor.active} onChange={e => setEditor({ ...editor, active: e.target.checked })} />
              {t('paymentAlerts.active', 'Active')}
            </label>

            {saveMutation.isError && (
              <p className="text-sm text-crit bg-crit-bg p-3 rounded-[4px] border border-crit/30">
                {t('paymentAlerts.saveError', 'Failed to save the rule (a rule for this number of days may already exist).')}
              </p>
            )}

            <div className="flex items-center justify-end gap-3 pt-2 border-t">
              <button onClick={() => setEditor(null)} className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">
                <X className="w-4 h-4" /> {t('app.cancel', 'Cancel')}
              </button>
              <button
                onClick={() => saveMutation.mutate(editor)}
                disabled={saveMutation.isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
              >
                {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                {t('app.save', 'Save')}
              </button>
            </div>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-ground border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('paymentAlerts.daysBeforeDue', 'Days before due')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('paymentAlerts.label', 'Label')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('paymentAlerts.active', 'Active')}</th>
                  <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('app.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {(!rules || rules.length === 0) ? (
                  <tr><td colSpan={4} className="text-center py-16 text-muted-foreground">
                    <BellRing className="w-8 h-8 mx-auto mb-2 text-ink-faint" />
                    {t('paymentAlerts.empty', 'No rules — the default 7-day reminder applies.')}
                  </td></tr>
                ) : rules.map(rule => (
                  <tr key={rule.id} className="hover:bg-ground group">
                    <td className="px-4 py-3 font-medium">J-{rule.daysBeforeDue}</td>
                    <td className="px-4 py-3 text-ink-soft">{rule.label || '—'}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-0.5 rounded ${rule.active ? 'bg-pos-bg text-pos' : 'bg-ground text-ink-soft'}`}>
                        {rule.active ? t('paymentAlerts.active', 'Active') : t('paymentAlerts.inactive', 'Inactive')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button onClick={() => setEditor({ id: rule.id, daysBeforeDue: rule.daysBeforeDue, label: rule.label ?? '', active: rule.active })}
                          className="text-sm text-primary hover:underline">{t('app.edit', 'Edit')}</button>
                        <button
                          onClick={() => { if (confirm(t('paymentAlerts.deleteConfirm', 'Delete this rule?'))) deleteMutation.mutate(rule.id) }}
                          className="p-1 text-ink-faint hover:text-crit" title={t('app.delete', 'Delete')}>
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
      </div>
    </PageRoleGuard>
  )
}
