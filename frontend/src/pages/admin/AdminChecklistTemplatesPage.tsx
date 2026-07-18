import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2, Plus, Trash2, Save, X, GripVertical } from 'lucide-react'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'

const ADMIN_ROLES = ['ROLE_ADMIN']

interface Department { id: string; code: string; nameEn: string; nameFr: string }

interface TemplateItem { id?: string; label: string; required: boolean; displayOrder: number }
interface ChecklistTemplate {
  id: string
  name: string
  departmentId: string | null
  active: boolean
  items: TemplateItem[]
}

interface EditorState {
  id?: string
  name: string
  departmentId: string
  active: boolean
  items: TemplateItem[]
}

const emptyEditor = (): EditorState => ({ name: '', departmentId: '', active: true, items: [] })

function AdminChecklistTemplatesPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [editor, setEditor] = useState<EditorState | null>(null)

  const { data: templates, isLoading } = useQuery({
    queryKey: ['checklist-templates'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ChecklistTemplate[] }>('/checklist-templates')
      return data.data ?? []
    },
  })

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Department[] } }>('/departments')
      return data.data?.content ?? []
    },
  })

  const saveMutation = useMutation({
    mutationFn: (state: EditorState) => {
      const body = {
        name: state.name,
        departmentId: state.departmentId || null,
        active: state.active,
        items: state.items.map((it, idx) => ({ label: it.label, required: it.required, displayOrder: idx })),
      }
      return state.id
        ? apiClient.put(`/checklist-templates/${state.id}`, body)
        : apiClient.post('/checklist-templates', body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['checklist-templates'] })
      setEditor(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/checklist-templates/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['checklist-templates'] }),
  })

  const deptName = (d: Department) => (i18n.language === 'fr' ? d.nameFr : d.nameEn)
  const deptLabel = (id: string | null) => {
    if (!id) return t('checklist.global', 'Global')
    const d = departments?.find(x => x.id === id)
    return d ? `${deptName(d)} (${d.code})` : id
  }

  const startEdit = (tpl: ChecklistTemplate) => setEditor({
    id: tpl.id, name: tpl.name, departmentId: tpl.departmentId ?? '', active: tpl.active,
    items: tpl.items.map(i => ({ ...i })),
  })

  const updateItem = (idx: number, patch: Partial<TemplateItem>) => {
    if (!editor) return
    const items = editor.items.map((it, i) => (i === idx ? { ...it, ...patch } : it))
    setEditor({ ...editor, items })
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <PageHeader
        title={t('checklist.title', 'Validation Checklist Templates')}
        subtitle={t('checklist.subtitle', 'Reusable checklists shown to validators during invoice review.')}
        actions={!editor && (
          <button
            onClick={() => setEditor(emptyEditor())}
            className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 text-sm font-medium"
          >
            <Plus className="w-4 h-4" />
            {t('checklist.new', 'New template')}
          </button>
        )}
      />

      {editor ? (
        <Panel className="p-6 space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('checklist.name', 'Name')} *</label>
              <input
                value={editor.name}
                onChange={e => setEditor({ ...editor, name: e.target.value })}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('checklist.department', 'Department')}</label>
              <select
                value={editor.departmentId}
                onChange={e => setEditor({ ...editor, departmentId: e.target.value })}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              >
                <option value="">{t('checklist.global', 'Global (all departments)')}</option>
                {(departments ?? []).map(d => (
                  <option key={d.id} value={d.id}>{deptName(d)} ({d.code})</option>
                ))}
              </select>
            </div>
          </div>

          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={editor.active} onChange={e => setEditor({ ...editor, active: e.target.checked })} />
            {t('checklist.active', 'Active')}
          </label>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm font-medium text-ink-soft">{t('checklist.items', 'Checklist items')}</label>
              <button
                onClick={() => setEditor({ ...editor, items: [...editor.items, { label: '', required: false, displayOrder: editor.items.length }] })}
                className="text-sm text-primary hover:underline flex items-center gap-1"
              >
                <Plus className="w-3.5 h-3.5" /> {t('checklist.addItem', 'Add item')}
              </button>
            </div>
            <div className="space-y-2">
              {editor.items.length === 0 && (
                <p className="text-sm text-ink-faint">{t('checklist.noItems', 'No items yet. Add at least one.')}</p>
              )}
              {editor.items.map((it, idx) => (
                <div key={idx} className="flex items-center gap-2 bg-ground rounded-[4px] p-2 border border-hairline">
                  <GripVertical className="w-4 h-4 text-ink-faint" />
                  <input
                    value={it.label}
                    onChange={e => updateItem(idx, { label: e.target.value })}
                    placeholder={t('checklist.itemPlaceholder', 'e.g. Amount matches the purchase order')}
                    className="flex-1 border border-hairline rounded-[4px] px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                  />
                  <label className="flex items-center gap-1 text-xs text-ink-soft whitespace-nowrap">
                    <input type="checkbox" checked={it.required} onChange={e => updateItem(idx, { required: e.target.checked })} />
                    {t('checklist.required', 'Required')}
                  </label>
                  <button onClick={() => setEditor({ ...editor, items: editor.items.filter((_, i) => i !== idx) })}
                    className="p-1 text-ink-faint hover:text-crit">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>

          {saveMutation.isError && (
            <p className="text-sm text-crit bg-crit-bg p-3 rounded-[4px] border border-crit/30">
              {t('checklist.saveError', 'Failed to save the template. Check the inputs and try again.')}
            </p>
          )}

          <div className="flex items-center justify-end gap-3 pt-2 border-t border-hairline">
            <button onClick={() => setEditor(null)} className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">
              <X className="w-4 h-4" /> {t('app.cancel', 'Cancel')}
            </button>
            <button
              onClick={() => saveMutation.mutate(editor)}
              disabled={!editor.name.trim() || editor.items.length === 0 || editor.items.some(i => !i.label.trim()) || saveMutation.isPending}
              className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
            >
              {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
              {t('app.save', 'Save')}
            </button>
          </div>
        </Panel>
      ) : isLoading ? (
        <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
      ) : (
        <Panel className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-ground">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('checklist.name', 'Name')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('checklist.department', 'Department')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('checklist.items', 'Items')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('checklist.active', 'Active')}</th>
                <th className="text-right px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('app.actions', 'Actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {(!templates || templates.length === 0) ? (
                <tr><td colSpan={5} className="text-center py-16 text-ink-faint">{t('checklist.empty', 'No templates yet.')}</td></tr>
              ) : templates.map(tpl => (
                <tr key={tpl.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] group">
                  <td className="px-4 py-3 font-medium text-ink">{tpl.name}</td>
                  <td className="px-4 py-3 text-ink-soft">{deptLabel(tpl.departmentId)}</td>
                  <td className="px-4 py-3 text-ink-soft num">{tpl.items.length}</td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-0.5 rounded-[4px] ${tpl.active ? 'bg-pos-bg text-pos' : 'bg-ground text-ink-soft'}`}>
                      {tpl.active ? t('checklist.active', 'Active') : t('checklist.inactive', 'Inactive')}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => startEdit(tpl)} className="text-sm text-primary hover:underline">{t('app.edit', 'Edit')}</button>
                      <button
                        onClick={() => { if (confirm(t('checklist.deleteConfirm', 'Delete this template?'))) deleteMutation.mutate(tpl.id) }}
                        className="p-1 text-ink-faint hover:text-crit" title={t('app.delete', 'Delete')}>
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Panel>
      )}
    </div>
  )
}


export default function AdminChecklistTemplatesPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ADMIN_ROLES}>
      <AdminChecklistTemplatesPage />
    </PageRoleGuard>
  )
}
