import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, Trash2, Save, X, AlarmClock } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'

interface EscalationRule {
  id: string
  hoursAfterDeadline: number
  label?: string | null
  active: boolean
}

interface EditorState {
  id?: string
  hoursAfterDeadline: number
  label: string
  active: boolean
}

const emptyEditor = (): EditorState => ({ hoursAfterDeadline: 24, label: '', active: true })

export default function EscalationRulesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [editor, setEditor] = useState<EditorState | null>(null)

  const { data: rules, isLoading } = useQuery({
    queryKey: ['escalation-rules'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: EscalationRule[] }>('/escalation-rules')
      return data.data ?? []
    },
  })

  const saveMutation = useMutation({
    mutationFn: (state: EditorState) => {
      const body = { hoursAfterDeadline: state.hoursAfterDeadline, label: state.label || null, active: state.active }
      return state.id
        ? apiClient.put(`/escalation-rules/${state.id}`, body)
        : apiClient.post('/escalation-rules', body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['escalation-rules'] })
      setEditor(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/escalation-rules/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['escalation-rules'] }),
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']}>
      <div className="space-y-6">
        <PageHeader
          title={t('escalationRules.title', 'Escalation Rules')}
          subtitle={t('escalationRules.subtitle', 'Configure how long after a missed deadline an approval is escalated.')}
          actions={!editor && (
            <button onClick={() => setEditor(emptyEditor())}
              className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 text-sm font-medium">
              <Plus className="w-4 h-4" />{t('escalationRules.new', 'New rule')}
            </button>
          )}
        />

        <p className="text-xs text-ink-soft bg-ground border border-hairline rounded-[4px] p-3">
          {t('escalationRules.recipientNote', 'The recipient is determined automatically: the next approval tier in the same department, otherwise the DAF.')}
        </p>

        {editor ? (
          <div className="bg-surface rounded-[4px] border border-hairline p-6 space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div>
                <label htmlFor="hoursAfterDeadline" className="block text-sm font-medium text-ink-soft mb-1">{t('escalationRules.hoursAfter', 'Hours after deadline')} *</label>
                <input id="hoursAfterDeadline" type="number" min={0} max={720} value={editor.hoursAfterDeadline}
                  onChange={e => setEditor({ ...editor, hoursAfterDeadline: Number(e.target.value) })}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                <p className="text-xs text-ink-faint mt-1">{t('escalationRules.hoursHint', 'Escalation is sent once an approval is overdue by this many hours (0 = immediately).')}</p>
              </div>
              <div>
                <label htmlFor="escalationRuleLabel" className="block text-sm font-medium text-ink-soft mb-1">{t('escalationRules.label', 'Label')}</label>
                <input id="escalationRuleLabel" value={editor.label} onChange={e => setEditor({ ...editor, label: e.target.value })}
                  placeholder={t('escalationRules.labelPlaceholder', 'e.g. Escalate after 1 day')}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm text-ink-soft">
              <input type="checkbox" checked={editor.active} onChange={e => setEditor({ ...editor, active: e.target.checked })} />
              {t('escalationRules.active', 'Active')}
            </label>

            {saveMutation.isError && (
              <p className="text-sm text-crit bg-crit-bg p-3 rounded-[4px] border border-crit/30">
                {t('escalationRules.saveError', 'Failed to save the rule.')}
              </p>
            )}

            <div className="flex items-center justify-end gap-3 pt-2 border-t">
              <button onClick={() => setEditor(null)} className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">
                <X className="w-4 h-4" /> {t('app.cancel', 'Cancel')}
              </button>
              <button onClick={() => saveMutation.mutate(editor)} disabled={saveMutation.isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60">
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
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('escalationRules.hoursAfter', 'Hours after deadline')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('escalationRules.label', 'Label')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('escalationRules.active', 'Active')}</th>
                  <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('app.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {(!rules || rules.length === 0) ? (
                  <tr><td colSpan={4} className="text-center py-16 text-muted-foreground">
                    <AlarmClock className="w-8 h-8 mx-auto mb-2 text-ink-faint" />
                    {t('escalationRules.empty', 'No rules — escalation is immediate to the DAF by default.')}
                  </td></tr>
                ) : rules.map(rule => (
                  <tr key={rule.id} className="hover:bg-ground group">
                    <td className="px-4 py-3 font-medium">+{rule.hoursAfterDeadline}h</td>
                    <td className="px-4 py-3 text-ink-soft">{rule.label || '—'}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-0.5 rounded ${rule.active ? 'bg-pos-bg text-pos' : 'bg-ground text-ink-soft'}`}>
                        {rule.active ? t('escalationRules.active', 'Active') : t('escalationRules.inactive', 'Inactive')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button onClick={() => setEditor({ id: rule.id, hoursAfterDeadline: rule.hoursAfterDeadline, label: rule.label ?? '', active: rule.active })}
                          className="text-sm text-primary hover:underline">{t('app.edit', 'Edit')}</button>
                        <button onClick={() => { if (confirm(t('escalationRules.deleteConfirm', 'Delete this rule?'))) deleteMutation.mutate(rule.id) }}
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
