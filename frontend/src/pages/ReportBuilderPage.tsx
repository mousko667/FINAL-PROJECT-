import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { FileBarChart, Loader2, Plus, Trash2, Play, FileText, Eye, X, Download } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'

interface ReportDef {
  id: string; name: string; dataset: string; format: string
  frequency: string; recipients: string | null; active: boolean; lastRunAt: string | null
}

interface ReportPreview { columns: string[]; rows: string[][]; totalRows: number; dataset: string; format: string }

const DATASETS = ['INVOICES', 'SUPPLIERS', 'AUDIT', 'BUDGET']
const FORMATS = ['CSV', 'EXCEL', 'PDF']
const FREQUENCIES = ['MANUAL', 'DAILY', 'WEEKLY', 'MONTHLY']
const MIME: Record<string, string> = { csv: 'text/csv', xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', pdf: 'application/pdf' }

export default function ReportBuilderPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [dataset, setDataset] = useState('INVOICES')
  const [format, setFormat] = useState('CSV')
  const [frequency, setFrequency] = useState('MANUAL')
  const [recipients, setRecipients] = useState('')
  const [deleteTargetId, setDeleteTargetId] = useState<string | null>(null)

  const { data: defs = [], isLoading } = useQuery<ReportDef[]>({
    queryKey: ['report-definitions'],
    queryFn: async () => (await apiClient.get<{ data: ReportDef[] }>('/reports/definitions')).data.data ?? [],
  })

  const create = useMutation({
    mutationFn: () => apiClient.post('/reports/definitions', { name, dataset, format, frequency, recipients }),
    onSuccess: () => { setName(''); setRecipients(''); queryClient.invalidateQueries({ queryKey: ['report-definitions'] }) },
  })
  const del = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/reports/definitions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['report-definitions'] })
      setDeleteTargetId(null)
    },
  })

  const [running, setRunning] = useState<string | null>(null)
  const runReport = async (def: ReportDef) => {
    setRunning(def.id)
    try {
      const res = await apiClient.get(`/reports/definitions/${def.id}/run`, { responseType: 'blob' })
      const ext = def.format.toLowerCase() === 'excel' ? 'xlsx' : def.format.toLowerCase()
      const url = window.URL.createObjectURL(new Blob([res.data], { type: MIME[ext] }))
      const a = document.createElement('a'); a.href = url; a.download = `${def.name}.${ext}`
      document.body.appendChild(a); a.click(); a.remove(); window.URL.revokeObjectURL(url)
    } finally { setRunning(null) }
  }

  const [preview, setPreview] = useState<{ def: ReportDef; data: ReportPreview } | null>(null)
  const [previewing, setPreviewing] = useState<string | null>(null)
  const openPreview = async (def: ReportDef) => {
    setPreviewing(def.id)
    try {
      const res = await apiClient.get<{ data: ReportPreview }>(`/reports/definitions/${def.id}/preview`, { params: { limit: 20 } })
      setPreview({ def, data: res.data.data })
    } finally { setPreviewing(null) }
  }

  const downloadExecSummary = async () => {
    const res = await apiClient.get('/reports/executive-summary', { responseType: 'blob' })
    const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
    const a = document.createElement('a'); a.href = url; a.download = 'executive_summary.pdf'
    document.body.appendChild(a); a.click(); a.remove(); window.URL.revokeObjectURL(url)
  }

  const inputCls = 'w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30'

  return (
    <PageRoleGuard allowedRoles={['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE']}>
      <div className="space-y-6 max-w-4xl">
        <PageHeader
          title={
            <span className="flex items-center gap-2">
              <FileBarChart className="w-5 h-5" aria-hidden />
              {t('reportBuilder.title', 'Constructeur de rapports')}
            </span>
          }
          subtitle={t('reportBuilder.subtitle', 'Définissez des rapports personnalisés, exécutez-les à la demande ou planifiez leur envoi par e-mail.')}
          actions={
            <button onClick={downloadExecSummary}
              className="inline-flex items-center gap-2 border border-white/30 px-3 py-2 rounded-[4px] text-sm font-medium text-white hover:bg-white/10">
              <FileText className="w-4 h-4" /> {t('reportBuilder.execSummary', 'Résumé exécutif (PDF)')}
            </button>
          }
        />

        <form onSubmit={e => { e.preventDefault(); if (name.trim()) create.mutate() }} className="bg-surface rounded-[4px] border p-5 grid grid-cols-1 md:grid-cols-2 gap-3">
          <input value={name} onChange={e => setName(e.target.value)} placeholder={t('reportBuilder.name', 'Nom du rapport')} className={`${inputCls} md:col-span-2`} />
          <select value={dataset} onChange={e => setDataset(e.target.value)} className={inputCls}>
            {DATASETS.map(d => <option key={d} value={d}>{t(`reportBuilder.dataset.${d}`, d)}</option>)}
          </select>
          <select value={format} onChange={e => setFormat(e.target.value)} className={inputCls}>
            {FORMATS.map(f => <option key={f} value={f}>{f}</option>)}
          </select>
          <select value={frequency} onChange={e => setFrequency(e.target.value)} className={inputCls}>
            {FREQUENCIES.map(f => <option key={f} value={f}>{t(`reportBuilder.frequency.${f}`, f)}</option>)}
          </select>
          <input value={recipients} onChange={e => setRecipients(e.target.value)} placeholder={t('reportBuilder.recipients', 'Destinataires (e-mails séparés par des virgules)')} className={inputCls} />
          <div className="md:col-span-2">
            <button type="submit" disabled={!name.trim() || create.isPending}
              className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
              {create.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
              {t('reportBuilder.create', 'Créer le rapport')}
            </button>
          </div>
        </form>

        <div className="bg-surface rounded-[4px] border overflow-x-auto">
          {isLoading ? (
            <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
          ) : defs.length === 0 ? (
            <p className="text-sm text-ink-faint py-8 text-center">{t('reportBuilder.none', 'Aucun rapport défini.')}</p>
          ) : (
            <table className="w-full text-sm">
              <thead><tr className="border-b border-hairline text-left text-ink-faint text-xs uppercase tracking-wide">
                <th className="px-4 py-2.5 font-medium">{t('reportBuilder.name', 'Nom')}</th>
                <th className="px-4 py-2.5 font-medium">{t('reportBuilder.datasetCol', 'Données')}</th>
                <th className="px-4 py-2.5 font-medium">{t('reportBuilder.format', 'Format')}</th>
                <th className="px-4 py-2.5 font-medium">{t('reportBuilder.frequencyCol', 'Fréquence')}</th>
                <th className="px-4 py-2.5 font-medium text-right">{t('app.actions')}</th>
              </tr></thead>
              <tbody>
                {defs.map(d => (
                  <tr key={d.id} className="border-b border-hairline last:border-0">
                    <td className="px-4 py-2.5 font-medium text-ink">{d.name}</td>
                    <td className="px-4 py-2.5">{t(`reportBuilder.dataset.${d.dataset}`, d.dataset)}</td>
                    <td className="px-4 py-2.5">{d.format}</td>
                    <td className="px-4 py-2.5">{t(`reportBuilder.frequency.${d.frequency}`, d.frequency)}</td>
                    <td className="px-4 py-2.5 text-right">
                      <div className="flex items-center gap-3 justify-end">
                        <button onClick={() => openPreview(d)} disabled={previewing === d.id} className="text-ink-soft hover:text-primary" title={t('reportBuilder.preview', 'Aperçu')}>
                          {previewing === d.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Eye className="w-4 h-4" />}
                        </button>
                        <button onClick={() => runReport(d)} disabled={running === d.id} className="text-primary hover:text-primary/80" title={t('reportBuilder.run', 'Exécuter')}>
                          {running === d.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                        </button>
                        <button onClick={() => setDeleteTargetId(d.id)} className="text-ink-faint hover:text-crit" title={t('app.delete', 'Supprimer')}>
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        {preview && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
               onClick={() => setPreview(null)}>
            <div className="bg-surface rounded-[4px] shadow-lg border border-hairline max-w-3xl w-full max-h-[80vh] flex flex-col"
                 onClick={e => e.stopPropagation()}>
              <div className="flex items-center justify-between px-5 py-4 border-b border-hairline">
                <div>
                  <h2 className="font-semibold text-ink">{preview.def.name}</h2>
                  <span className="text-xs text-ink-soft">{preview.data.dataset} · {preview.data.format}</span>
                </div>
                <button onClick={() => setPreview(null)} className="text-ink-faint hover:text-ink-soft" title={t('app.close', 'Fermer')}>
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="overflow-auto p-5 flex-1">
                {preview.data.rows.length === 0 ? (
                  <p className="text-sm text-ink-faint text-center py-8">{t('reportBuilder.previewEmpty', 'Aucune donnée à afficher.')}</p>
                ) : (
                  <table className="w-full text-sm border-collapse">
                    <thead><tr className="border-b border-hairline text-left text-ink-faint text-xs uppercase tracking-wide">
                      {preview.data.columns.map((c, i) => <th key={i} className="px-3 py-2 font-medium whitespace-nowrap">{c}</th>)}
                    </tr></thead>
                    <tbody>
                      {preview.data.rows.map((row, ri) => (
                        <tr key={ri} className="border-b border-hairline last:border-0">
                          {row.map((cell, ci) => <td key={ci} className="px-3 py-2 text-ink-soft whitespace-nowrap">{cell}</td>)}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
              <div className="flex items-center justify-between px-5 py-4 border-t border-hairline">
                <span className="text-xs text-ink-soft">
                  {t('reportBuilder.previewShowing', { shown: preview.data.rows.length, total: preview.data.totalRows })}
                </span>
                <div className="flex items-center gap-2">
                  <button onClick={() => setPreview(null)} className="px-3 py-2 text-sm border border-hairline rounded-[4px] hover:bg-ground">{t('app.close', 'Fermer')}</button>
                  <button onClick={() => runReport(preview.def)} disabled={running === preview.def.id}
                    className="inline-flex items-center gap-2 px-3 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
                    {running === preview.def.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
                    {t('reportBuilder.download', 'Télécharger')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={deleteTargetId !== null}
        title={t('reportBuilder.deleteConfirmTitle', 'Delete this report definition?')}
        message={t('reportBuilder.deleteConfirmBody', 'Scheduled runs for this report will stop.')}
        variant="danger"
        onConfirm={() => { if (deleteTargetId) del.mutate(deleteTargetId) }}
        onCancel={() => setDeleteTargetId(null)}
      />
    </PageRoleGuard>
  )
}
