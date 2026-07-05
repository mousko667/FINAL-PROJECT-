import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { ShieldAlert, Database, ListChecks, CalendarClock, Plus, Trash2, Loader2, ClipboardCheck } from 'lucide-react'
import { formatDate, formatDateTime } from '@/lib/format'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Panel } from '@/components/ui/Panel'

interface Incident { id: string; title: string; severity: string; status: string; reportedAt: string }
interface ChecklistItem { id: string; framework: string; label: string; completed: boolean }
interface CalendarEntry { id: string; title: string; dueDate: string; completed: boolean }
interface BackupStatus { lastBackupAt: string | null; status: string; detail: string | null }

/** M14 — security & compliance console (ADMIN): backup, incidents, SOX/IFRS checklist, calendar. */
export default function AdminCompliancePage() {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const inputCls = 'border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'

  const backup = useQuery<BackupStatus>({ queryKey: ['backup-status'], queryFn: async () => (await apiClient.get('/compliance/backup-status')).data.data })
  const incidents = useQuery<Incident[]>({ queryKey: ['incidents'], queryFn: async () => (await apiClient.get('/compliance/incidents')).data.data ?? [] })
  const checklist = useQuery<ChecklistItem[]>({ queryKey: ['checklist'], queryFn: async () => (await apiClient.get('/compliance/checklist')).data.data ?? [] })
  const calendar = useQuery<CalendarEntry[]>({ queryKey: ['calendar'], queryFn: async () => (await apiClient.get('/compliance/calendar')).data.data ?? [] })

  const recordBackup = useMutation({ mutationFn: () => apiClient.post('/compliance/backup-status', { status: 'OK', detail: 'Manual backup recorded' }), onSuccess: () => qc.invalidateQueries({ queryKey: ['backup-status'] }) })

  const [incTitle, setIncTitle] = useState(''); const [incSev, setIncSev] = useState('MEDIUM')
  const addIncident = useMutation({ mutationFn: () => apiClient.post('/compliance/incidents', { title: incTitle, severity: incSev }), onSuccess: () => { setIncTitle(''); qc.invalidateQueries({ queryKey: ['incidents'] }) } })
  const setIncStatus = useMutation({ mutationFn: ({ id, status }: { id: string; status: string }) => apiClient.patch(`/compliance/incidents/${id}/status?status=${status}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['incidents'] }) })

  const [clFw, setClFw] = useState('SOX'); const [clLabel, setClLabel] = useState('')
  const addChecklist = useMutation({ mutationFn: () => apiClient.post('/compliance/checklist', { framework: clFw, label: clLabel }), onSuccess: () => { setClLabel(''); qc.invalidateQueries({ queryKey: ['checklist'] }) } })
  const toggleChecklist = useMutation({ mutationFn: ({ id, completed }: { id: string; completed: boolean }) => apiClient.patch(`/compliance/checklist/${id}?completed=${completed}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['checklist'] }) })
  const [deleteChecklistTargetId, setDeleteChecklistTargetId] = useState<string | null>(null)
  const delChecklist = useMutation({ mutationFn: (id: string) => apiClient.delete(`/compliance/checklist/${id}`), onSuccess: () => { qc.invalidateQueries({ queryKey: ['checklist'] }); setDeleteChecklistTargetId(null) } })

  const [calTitle, setCalTitle] = useState(''); const [calDue, setCalDue] = useState('')
  const addCalendar = useMutation({ mutationFn: () => apiClient.post('/compliance/calendar', { title: calTitle, dueDate: calDue }), onSuccess: () => { setCalTitle(''); setCalDue(''); qc.invalidateQueries({ queryKey: ['calendar'] }) } })
  const toggleCalendar = useMutation({ mutationFn: ({ id, completed }: { id: string; completed: boolean }) => apiClient.patch(`/compliance/calendar/${id}?completed=${completed}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['calendar'] }) })
  const [deleteCalendarTargetId, setDeleteCalendarTargetId] = useState<string | null>(null)
  const delCalendar = useMutation({ mutationFn: (id: string) => apiClient.delete(`/compliance/calendar/${id}`), onSuccess: () => { qc.invalidateQueries({ queryKey: ['calendar'] }); setDeleteCalendarTargetId(null) } })

  const badge = (ok: boolean) => ok ? 'bg-pos-bg text-pos' : 'bg-ground text-ink-soft border border-hairline'

  // ── Audit-prep synthesis: read-only roll-up of the data already loaded above ──
  const openIncidentCount = (incidents.data ?? []).filter(i => i.status === 'OPEN' || i.status === 'INVESTIGATING').length
  const checklistTotal = (checklist.data ?? []).length
  const checklistDone = (checklist.data ?? []).filter(c => c.completed).length
  const upcomingDeadlines = (calendar.data ?? [])
    .filter(e => !e.completed)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  const stat = (label: string, value: string, danger = false) => (
    <div className="bg-surface rounded-[4px] border border-hairline shadow-sm p-4">
      <p className="text-xs text-ink-faint uppercase tracking-wide font-medium">{label}</p>
      <p className={`num text-2xl font-bold mt-0.5 ${danger ? 'text-crit' : 'text-ink'}`}>{value}</p>
    </div>
  )

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex items-center gap-3">
        <ShieldAlert className="w-6 h-6 text-primary" />
        <div>
          <h1 className="text-2xl font-bold text-ink">{t('admin.compliance.title', 'Sécurité & Conformité')}</h1>
          <p className="text-sm text-ink-soft mt-1">{t('admin.compliance.subtitle', 'Sauvegardes, incidents, checklist SOX/IFRS, calendrier de conformité.')}</p>
        </div>
      </div>

      {/* Audit preparation — read-only synthesis of the existing compliance data */}
      <Panel className="p-5">
        <div className="flex items-center gap-2 mb-1"><ClipboardCheck className="w-5 h-5 text-primary" /><h2 className="font-semibold text-ink">{t('admin.compliance.auditPrep.title', 'Préparation d\'audit')}</h2></div>
        <p className="text-sm text-ink-soft mb-4">{t('admin.compliance.auditPrep.subtitle')}</p>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {stat(t('admin.compliance.auditPrep.openIncidents'), String(openIncidentCount), openIncidentCount > 0)}
          {stat(t('admin.compliance.auditPrep.checklistProgress'), checklistTotal ? `${checklistDone}/${checklistTotal}` : '—')}
          {stat(t('admin.compliance.auditPrep.upcomingDeadlines'), String(upcomingDeadlines.length))}
          {stat(t('admin.compliance.auditPrep.backupStatus'), backup.data?.lastBackupAt ? formatDate(backup.data.lastBackupAt) : '—', backup.data?.status === 'FAILED')}
        </div>
        <div className="mt-4">
          <h3 className="text-sm font-medium text-ink-soft mb-2">{t('admin.compliance.auditPrep.nextDeadlinesTitle')}</h3>
          {upcomingDeadlines.length === 0 ? (
            <p className="text-sm text-ink-faint">{t('admin.compliance.auditPrep.noUpcoming')}</p>
          ) : (
            <ul className="divide-y divide-hairline">
              {upcomingDeadlines.slice(0, 5).map(e => (
                <li key={e.id} className="flex items-center justify-between py-2 text-sm">
                  <span className="text-ink-soft">{e.title}</span>
                  <span className="text-xs text-ink-faint">{e.dueDate}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </Panel>

      {/* Backup status */}
      <Panel className="p-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2"><Database className="w-5 h-5 text-primary" /><h2 className="font-semibold text-ink">{t('admin.compliance.backup', 'Statut de sauvegarde')}</h2></div>
          <button onClick={() => recordBackup.mutate()} disabled={recordBackup.isPending} className="text-sm border border-hairline px-3 py-1.5 rounded-[4px] hover:bg-ground">{t('admin.compliance.recordBackup', 'Enregistrer une sauvegarde')}</button>
        </div>
        <div className="mt-2 text-sm">
          <span className={`px-2 py-0.5 rounded-[4px] text-xs font-medium ${backup.data?.status === 'OK' ? 'bg-pos-bg text-pos' : backup.data?.status === 'FAILED' ? 'bg-crit-bg text-crit' : 'bg-ground text-ink-soft'}`}>{backup.data?.status ?? 'UNKNOWN'}</span>
          <span className="text-ink-soft ml-2">{backup.data?.lastBackupAt ? formatDateTime(backup.data.lastBackupAt) : t('admin.compliance.noBackup', 'Aucune sauvegarde enregistrée')}</span>
        </div>
      </Panel>

      {/* Incidents */}
      <Panel className="p-5">
        <div className="flex items-center gap-2 mb-3"><ShieldAlert className="w-5 h-5 text-primary" /><h2 className="font-semibold text-ink">{t('admin.compliance.incidents', 'Incidents de sécurité')}</h2></div>
        <div className="flex flex-wrap gap-2 mb-3">
          <input value={incTitle} onChange={e => setIncTitle(e.target.value)} placeholder={t('admin.compliance.incidentTitle', 'Titre de l\'incident')} className={`${inputCls} flex-1 min-w-[180px]`} />
          <select value={incSev} onChange={e => setIncSev(e.target.value)} className={inputCls}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map(s => <option key={s}>{s}</option>)}</select>
          <button onClick={() => addIncident.mutate()} disabled={!incTitle.trim() || addIncident.isPending} className="inline-flex items-center gap-1.5 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50"><Plus className="w-4 h-4" />{t('app.add', 'Ajouter')}</button>
        </div>
        {(incidents.data ?? []).length === 0 ? <p className="text-sm text-ink-faint">{t('admin.compliance.noIncidents', 'Aucun incident.')}</p> : (
          <ul className="divide-y divide-hairline">
            {incidents.data!.map(i => (
              <li key={i.id} className="flex items-center justify-between py-2 text-sm">
                <span><span className="font-medium text-ink">{i.title}</span> <span className="text-xs text-ink-faint">({i.severity})</span></span>
                <select value={i.status} onChange={e => setIncStatus.mutate({ id: i.id, status: e.target.value })} className="text-xs border border-hairline rounded-[4px] px-2 py-1 bg-surface">
                  {['OPEN', 'INVESTIGATING', 'RESOLVED', 'CLOSED'].map(s => <option key={s}>{s}</option>)}
                </select>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {/* SOX/IFRS checklist */}
      <Panel className="p-5">
        <div className="flex items-center gap-2 mb-3"><ListChecks className="w-5 h-5 text-primary" /><h2 className="font-semibold text-ink">{t('admin.compliance.checklist', 'Checklist de conformité (SOX/IFRS)')}</h2></div>
        <div className="flex flex-wrap gap-2 mb-3">
          <select value={clFw} onChange={e => setClFw(e.target.value)} className={inputCls}>{['SOX', 'IFRS', 'LOCAL'].map(f => <option key={f}>{f}</option>)}</select>
          <input value={clLabel} onChange={e => setClLabel(e.target.value)} placeholder={t('admin.compliance.checklistLabel', 'Élément de contrôle')} className={`${inputCls} flex-1 min-w-[180px]`} />
          <button onClick={() => addChecklist.mutate()} disabled={!clLabel.trim() || addChecklist.isPending} className="inline-flex items-center gap-1.5 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50"><Plus className="w-4 h-4" />{t('app.add', 'Ajouter')}</button>
        </div>
        {(checklist.data ?? []).length === 0 ? <p className="text-sm text-ink-faint">{t('admin.compliance.noChecklist', 'Aucun élément.')}</p> : (
          <ul className="divide-y divide-hairline">
            {checklist.data!.map(c => (
              <li key={c.id} className="flex items-center justify-between py-2 text-sm">
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={c.completed} onChange={e => toggleChecklist.mutate({ id: c.id, completed: e.target.checked })} className="w-4 h-4 accent-primary" />
                  <span className="num text-xs bg-ground border border-hairline px-1.5 py-0.5 rounded-[4px]">{c.framework}</span>
                  <span className={c.completed ? 'line-through text-ink-faint' : 'text-ink-soft'}>{c.label}</span>
                </label>
                <button onClick={() => setDeleteChecklistTargetId(c.id)} className="text-ink-faint hover:text-crit"><Trash2 className="w-4 h-4" /></button>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      {/* Compliance calendar */}
      <Panel className="p-5">
        <div className="flex items-center gap-2 mb-3"><CalendarClock className="w-5 h-5 text-primary" /><h2 className="font-semibold text-ink">{t('admin.compliance.calendar', 'Calendrier de conformité')}</h2></div>
        <div className="flex flex-wrap gap-2 mb-3">
          <input value={calTitle} onChange={e => setCalTitle(e.target.value)} placeholder={t('admin.compliance.calendarTitle', 'Échéance')} className={`${inputCls} flex-1 min-w-[160px]`} />
          <input type="date" value={calDue} onChange={e => setCalDue(e.target.value)} className={inputCls} />
          <button onClick={() => addCalendar.mutate()} disabled={!calTitle.trim() || !calDue || addCalendar.isPending} className="inline-flex items-center gap-1.5 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50"><Plus className="w-4 h-4" />{t('app.add', 'Ajouter')}</button>
        </div>
        {(calendar.data ?? []).length === 0 ? <p className="text-sm text-ink-faint">{t('admin.compliance.noCalendar', 'Aucune échéance.')}</p> : (
          <ul className="divide-y divide-hairline">
            {calendar.data!.map(e => (
              <li key={e.id} className="flex items-center justify-between py-2 text-sm">
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={e.completed} onChange={ev => toggleCalendar.mutate({ id: e.id, completed: ev.target.checked })} className="w-4 h-4 accent-primary" />
                  <span className={e.completed ? 'line-through text-ink-faint' : 'text-ink-soft'}>{e.title}</span>
                  <span className="text-xs text-ink-faint">{e.dueDate}</span>
                </label>
                <button onClick={() => setDeleteCalendarTargetId(e.id)} className="text-ink-faint hover:text-crit"><Trash2 className="w-4 h-4" /></button>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <ConfirmDialog
        open={deleteChecklistTargetId !== null}
        title={t('admin.compliance.deleteChecklistConfirmTitle', 'Delete this checklist item?')}
        message={t('admin.compliance.deleteChecklistConfirmBody', 'This action is permanent.')}
        variant="danger"
        onConfirm={() => { if (deleteChecklistTargetId) delChecklist.mutate(deleteChecklistTargetId) }}
        onCancel={() => setDeleteChecklistTargetId(null)}
      />
      <ConfirmDialog
        open={deleteCalendarTargetId !== null}
        title={t('admin.compliance.deleteCalendarConfirmTitle', 'Delete this deadline?')}
        message={t('admin.compliance.deleteCalendarConfirmBody', 'This action is permanent.')}
        variant="danger"
        onConfirm={() => { if (deleteCalendarTargetId) delCalendar.mutate(deleteCalendarTargetId) }}
        onCancel={() => setDeleteCalendarTargetId(null)}
      />
    </div>
  )
}
