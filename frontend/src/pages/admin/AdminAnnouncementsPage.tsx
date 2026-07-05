import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Megaphone, Loader2, Trash2, Plus } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'

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
export default function AdminAnnouncementsPage() {
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

  const inputCls = 'w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center gap-3">
        <Megaphone className="w-6 h-6 text-primary" />
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('admin.announcements.title', 'Annonces système')}</h1>
          <p className="text-sm text-gray-500 mt-1">{t('admin.announcements.subtitle', 'Messages affichés sur les tableaux de bord de tous les utilisateurs.')}</p>
        </div>
      </div>

      <form onSubmit={onSubmit} className="bg-white rounded-xl border p-5 space-y-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.announcements.titleField', 'Titre')}</label>
          <input value={title} onChange={e => { setTitle(e.target.value); setFormError(null) }} className={inputCls} maxLength={200} />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.announcements.body', 'Message')}</label>
          <textarea value={body} onChange={e => { setBody(e.target.value); setFormError(null) }} rows={3} className={inputCls} maxLength={2000} />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.announcements.severity', 'Niveau')}</label>
          <select value={severity} onChange={e => setSeverity(e.target.value)} className={inputCls}>
            <option value="INFO">INFO</option>
            <option value="WARNING">WARNING</option>
            <option value="CRITICAL">CRITICAL</option>
          </select>
        </div>
        {formError && <p className="text-sm text-red-600">{formError}</p>}
        <button type="submit" disabled={create.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
          {create.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
          {t('admin.announcements.create', 'Publier')}
        </button>
      </form>

      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-gray-400" /></div>
        ) : announcements.length === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">{t('admin.announcements.none', 'Aucune annonce.')}</p>
        ) : (
          <table className="w-full text-sm">
            <thead><tr className="border-b text-left text-gray-500">
              <th className="px-4 py-2.5 font-medium">{t('admin.announcements.titleField', 'Titre')}</th>
              <th className="px-4 py-2.5 font-medium">{t('admin.announcements.severity', 'Niveau')}</th>
              <th className="px-4 py-2.5 font-medium">{t('admin.announcements.statusCol', 'Actif')}</th>
              <th className="px-4 py-2.5 font-medium text-right">Actions</th>
            </tr></thead>
            <tbody>
              {announcements.map(a => (
                <tr key={a.id} className="border-b last:border-0">
                  <td className="px-4 py-2.5"><div className="font-medium text-gray-900">{a.title}</div><div className="text-xs text-gray-400 truncate max-w-md">{a.body}</div></td>
                  <td className="px-4 py-2.5">{a.severity}</td>
                  <td className="px-4 py-2.5">
                    <button onClick={() => toggle.mutate({ id: a.id, active: !a.active })}
                      className={`px-2 py-0.5 rounded-full text-xs font-medium ${a.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-500'}`}>
                      {a.active ? t('admin.announcements.active', 'Actif') : t('admin.announcements.inactive', 'Inactif')}
                    </button>
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <button onClick={() => setDeleteTargetId(a.id)} className="text-gray-400 hover:text-red-500"><Trash2 className="w-4 h-4" /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

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
