import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Megaphone, Loader2, Trash2, Plus } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'

const ADMIN_ROLES = ['ROLE_ADMIN']

interface Announcement {
  id: string
  title: string
  body: string
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
  active: boolean
  createdAt: string
  expiresAt: string | null
}

/** M2 — admin management of system announcements (create / toggle / delete). */
function AdminAnnouncementsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [severity, setSeverity] = useState('INFO')
  const [formError, setFormError] = useState<string | null>(null)
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  const { data: announcements = [], isLoading } = useQuery<Announcement[]>({
    queryKey: ['announcements', 'all'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Announcement[] }>('/announcements/all')
      return data.data ?? []
    },
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['announcements'] })

  const create = useMutation({
    mutationFn: () => apiClient.post('/announcements', { title, body, severity }),
    onSuccess: () => { setTitle(''); setBody(''); setSeverity('INFO'); setFormError(null); invalidate() },
    onError: () => setFormError(t('admin.announcements.createError', 'Échec de la création.')),
  })

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      apiClient.patch(`/announcements/${id}/active?active=${active}`),
    onSuccess: invalidate,
  })

  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/announcements/${id}`),
    onSuccess: () => { invalidate(); setDeleteTargetId(null) },
  })

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!title.trim() || !body.trim()) { setFormError(t('admin.announcements.incomplete', 'Titre et message requis.')); return }
    create.mutate()
  }

  const inputCls = 'w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'

  return (
    <div className="space-y-6 max-w-3xl">
      <PageHeader
        title={<span className="flex items-center gap-2"><Megaphone className="w-6 h-6" aria-hidden /> {t('admin.announcements.title', 'Annonces système')}</span>}
        subtitle={t('admin.announcements.subtitle', 'Messages affichés sur les tableaux de bord de tous les utilisateurs.')}
      />

      <Panel className="p-5">
        <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.announcements.titleField', 'Titre')}</label>
          <input value={title} onChange={e => { setTitle(e.target.value); setFormError(null) }} className={inputCls} maxLength={200} />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.announcements.body', 'Message')}</label>
          <textarea value={body} onChange={e => { setBody(e.target.value); setFormError(null) }} rows={3} className={inputCls} maxLength={2000} />
        </div>
        <div>
          <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.announcements.severity', 'Niveau')}</label>
          <select value={severity} onChange={e => setSeverity(e.target.value)} className={inputCls}>
            <option value="INFO">INFO</option>
            <option value="WARNING">WARNING</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
        </div>
        {formError && <p className="text-sm text-crit">{formError}</p>}
        <button type="submit" disabled={create.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
          {create.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
          {t('admin.announcements.create', 'Publier')}
        </button>
        </form>
      </Panel>

      <Panel className="overflow-x-auto">
        {isLoading ? (
          <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
        ) : announcements.length === 0 ? (
          <p className="text-sm text-ink-faint py-8 text-center">{t('admin.announcements.none', 'Aucune annonce.')}</p>
        ) : (
          <table className="w-full text-sm">
            <thead><tr className="bg-ground text-left text-ink-faint text-xs font-medium uppercase tracking-wide">
              <th className="px-4 py-2.5 font-medium">{t('admin.announcements.titleField', 'Titre')}</th>
              <th className="px-4 py-2.5 font-medium">{t('admin.announcements.severity', 'Niveau')}</th>
              <th className="px-4 py-2.5 font-medium">{t('admin.announcements.statusCol', 'Actif')}</th>
              <th className="px-4 py-2.5 font-medium text-right">Actions</th>
            </tr></thead>
            <tbody className="divide-y divide-hairline">
              {announcements.map(a => (
                <tr key={a.id}>
                  <td className="px-4 py-2.5"><div className="font-medium text-ink">{a.title}</div><div className="text-xs text-ink-faint truncate max-w-md">{a.body}</div></td>
                  <td className="px-4 py-2.5">{a.severity}</td>
                  <td className="px-4 py-2.5">
                    <button onClick={() => toggle.mutate({ id: a.id, active: !a.active })}
                      className={`px-2 py-0.5 rounded-[4px] text-xs font-medium ${a.active ? 'bg-pos-bg text-pos' : 'bg-ground text-ink-soft'}`}>
                      {a.active ? t('admin.announcements.active', 'Actif') : t('admin.announcements.inactive', 'Inactif')}
                    </button>
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <button onClick={() => setDeleteTargetId(a.id)} className="text-ink-faint hover:text-crit"><Trash2 className="w-4 h-4" /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>

      <ConfirmDialog
        open={deleteTargetId !== null}
        title={t('admin.announcements.deleteConfirmTitle', 'Delete this announcement?')}
        message={t('admin.announcements.deleteConfirmBody', 'This announcement will no longer be visible on any dashboard.')}
        variant="danger"
        onConfirm={() => { if (deleteTargetId) remove.mutate(deleteTargetId) }}
        onCancel={() => setDeleteTargetId(null)}
      />
    </div>
  )
}


export default function AdminAnnouncementsPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ADMIN_ROLES}>
      <AdminAnnouncementsPage />
    </PageRoleGuard>
  )
}
