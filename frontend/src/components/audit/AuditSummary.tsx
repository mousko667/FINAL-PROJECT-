import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, BarChart3 } from 'lucide-react'
import { ExportMenu } from '@/components/ui/ExportMenu'

interface CountEntry { label: string; count: number }
interface SummaryDTO {
  from: string; to: string; totalEvents: number
  byAction: CountEntry[]; byUser: CountEntry[]; byEntityType: CountEntry[]; byDay: CountEntry[]
}

function isoDaysAgo(n: number): string {
  const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().slice(0, 10)
}

function CountPanel({ title, entries }: { title: string; entries: CountEntry[] }) {
  return (
    <div className="bg-surface rounded-[4px] border border-hairline p-4">
      <h3 className="font-semibold text-ink mb-3">{title}</h3>
      {entries.length === 0 ? (
        <p className="text-sm text-ink-faint">—</p>
      ) : (
        <ul className="divide-y">
          {entries.map((e, i) => (
            <li key={i} className="flex items-center justify-between py-1.5 text-sm">
              <span className="text-ink-soft truncate">{e.label || '—'}</span>
              <span className="text-xs num bg-warn-bg text-warn px-2 py-0.5 rounded border border-warn/30">{e.count}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function DayBars({ title, entries }: { title: string; entries: CountEntry[] }) {
  const max = Math.max(1, ...entries.map((e) => e.count))
  return (
    <div className="bg-surface rounded-[4px] border border-hairline p-4">
      <h3 className="font-semibold text-ink mb-3">{title}</h3>
      {entries.length === 0 ? <p className="text-sm text-ink-faint">—</p> : (
        <ul className="space-y-1.5">
          {entries.map((e, i) => (
            <li key={i} className="flex items-center gap-2 text-xs">
              <span className="w-24 shrink-0 text-ink-soft">{e.label}</span>
              <span className="h-3 bg-primary/70 rounded" style={{ width: `${(e.count / max) * 100}%` }} />
              <span className="text-ink-soft">{e.count}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function AuditSummary({ scope }: { scope: 'system' | 'financial' }) {
  const { t } = useTranslation()
  const [from, setFrom] = useState(isoDaysAgo(30))
  const [to, setTo] = useState(isoDaysAgo(0))

  const { data, isLoading } = useQuery({
    queryKey: ['audit-summary', scope, from, to],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SummaryDTO }>(`/audit-logs/summary/${scope}`, { params: { from, to } })
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <div className="bg-surface rounded-[4px] border border-hairline p-4 flex flex-wrap items-end gap-3">
        <label className="text-sm text-ink-soft">{t('admin.audit.summary.from')}
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)}
            className="block border border-hairline rounded-[4px] px-3 py-1.5 mt-1 text-sm" />
        </label>
        <label className="text-sm text-ink-soft">{t('admin.audit.summary.to')}
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)}
            className="block border border-hairline rounded-[4px] px-3 py-1.5 mt-1 text-sm" />
        </label>
        <div className="ml-auto">
          <ExportMenu endpoint="/audit-logs/summary/export" filename="audit_summary"
            params={{ scope, from, to }} />
        </div>
      </div>

      {isLoading || !data ? (
        <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
      ) : (
        <>
          <div className="bg-surface rounded-[4px] border border-hairline p-4 flex items-center gap-3">
            <BarChart3 className="w-5 h-5 text-primary" />
            <span className="text-sm text-ink-soft">{t('admin.audit.summary.total')}</span>
            <span className="text-2xl font-bold text-ink">{data.totalEvents}</span>
          </div>
          <div className="grid gap-6 md:grid-cols-2">
            <CountPanel title={t('admin.audit.summary.byAction')} entries={data.byAction} />
            <CountPanel title={t('admin.audit.summary.byUser')} entries={data.byUser} />
            <CountPanel title={t('admin.audit.summary.byEntity')} entries={data.byEntityType} />
            <DayBars title={t('admin.audit.summary.byDay')} entries={data.byDay} />
          </div>
        </>
      )}
    </div>
  )
}
