import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, Search, ChevronLeft, ChevronRight, Activity } from 'lucide-react'
import { ExportMenu } from '@/components/ui/ExportMenu'
import AuditSummary from '@/components/audit/AuditSummary'
import RetentionComplianceCard from '@/components/audit/RetentionComplianceCard'
import { formatDateTime } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'

interface AuditLog {
  id: string
  action: string
  entityType: string
  entityId?: string
  performedBy?: { username: string }
  performedAt?: string
  createdAt?: string
  ipAddress?: string
  userAgent?: string
  newValue?: string
  details?: string
}

interface AuditFilters {
  username?: string
  entityType?: string
  action?: string
  page: number
  size: number
}

function relativeTime(raw: string | undefined, t: (k: string, o?: object) => string): string {
  if (!raw) return '—'
  const d = new Date(raw)
  if (isNaN(d.getTime())) return raw
  const secs = Math.max(0, Math.floor((Date.now() - d.getTime()) / 1000))
  if (secs < 60) return t('admin.audit.recent.justNow')
  if (secs < 3600) return t('admin.audit.recent.minutesAgo', { count: Math.floor(secs / 60) })
  if (secs < 86400) return t('admin.audit.recent.hoursAgo', { count: Math.floor(secs / 3600) })
  return formatDateTime(d)
}

/**
 * P11-51 (REQ-19, partial) — live "recent activity" feed. Re-fetches the latest audit entries on
 * an interval (react-query refetchInterval) so an admin sees activity as it happens, without a
 * manual reload. Reuses the existing /audit-logs endpoint (newest page, small size).
 */
function RecentActivityPanel() {
  const { t } = useTranslation()
  const REFRESH_MS = 15000

  const { data, isFetching } = useQuery({
    queryKey: ['audit-logs-recent'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<AuditLog>>>(
        '/audit-logs', { params: { page: 0, size: 8 } })
      return data.data
    },
    refetchInterval: REFRESH_MS,
    refetchIntervalInBackground: false,
  })

  const recent = data?.content ?? []

  return (
    <Panel className="p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Activity className="w-5 h-5 text-primary" />
          <h2 className="font-semibold text-ink">{t('admin.audit.recent.title')}</h2>
        </div>
        <span className="flex items-center gap-1.5 text-xs text-ink-faint">
          <span className={`w-2 h-2 rounded-full ${isFetching ? 'bg-pos animate-pulse' : 'bg-pos'}`} />
          {t('admin.audit.recent.live')}
        </span>
      </div>
      {recent.length === 0 ? (
        <p className="text-sm text-ink-faint py-2">{t('app.noData')}</p>
      ) : (
        <ul className="divide-y divide-hairline">
          {recent.map((log) => (
            <li key={log.id} className="flex items-center justify-between gap-3 py-2 text-sm">
              <div className="flex items-center gap-2 min-w-0">
                <span className="num text-xs bg-warn-bg text-warn px-2 py-0.5 rounded-[4px] border border-warn/30 shrink-0">
                  {log.action}
                </span>
                <span className="text-ink-soft truncate">
                  {log.performedBy?.username ?? '—'} · {log.entityType}
                </span>
              </div>
              <span className="text-xs text-ink-faint whitespace-nowrap shrink-0">
                {relativeTime(log.createdAt ?? log.performedAt, t)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

interface Anomaly {
  userId: string; username: string; type: string; observed: number; baseline: number; detail: string
}

/**
 * M10 — statistical audit anomaly detection. Lists users flagged for unusual activity volume or
 * excessive access-denied events over the recent window. Empty (hidden) when nothing is flagged.
 */
function AnomalyPanel() {
  const { t } = useTranslation()
  const { data: anomalies = [] } = useQuery<Anomaly[]>({
    queryKey: ['audit-anomalies'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Anomaly[] }>('/audit-logs/anomalies')
      return data.data ?? []
    },
    retry: false,
  })

  if (anomalies.length === 0) return null

  return (
    <Panel className="p-4 border-crit/30">
      <div className="flex items-center gap-2 mb-3">
        <Activity className="w-5 h-5 text-crit" />
        <h2 className="font-semibold text-ink">{t('admin.audit.anomalies.title', 'Anomalies détectées')}</h2>
      </div>
      <ul className="divide-y divide-hairline">
        {anomalies.map((a, i) => (
          <li key={i} className="flex items-center justify-between gap-3 py-2 text-sm">
            <div className="flex items-center gap-2 min-w-0">
              <span className="num text-xs bg-crit-bg text-crit px-2 py-0.5 rounded-[4px] border border-crit/30 shrink-0">
                {t(`admin.audit.anomalies.${a.type}`, a.type)}
              </span>
              <span className="text-ink-soft truncate">{a.username}</span>
            </div>
            <span className="text-xs text-ink-faint text-right">{a.detail}</span>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

export default function AdminAuditPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'journal' | 'summary'>('journal')
  const [filters, setFilters] = useState<AuditFilters>({ page: 0, size: 20 })

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs-system', filters],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<AuditLog>>>(
        '/audit-logs',
        { params: filters }
      )
      return data.data
    },
  })

  const logs = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const currentPage = filters.page

  const handleFilter = (key: keyof AuditFilters, value: string) =>
    setFilters((prev) => ({ ...prev, [key]: value || undefined, page: 0 }))

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-ink">{t('admin.audit.title')}</h1>
        {tab === 'journal' && (
          <ExportMenu endpoint="/audit-logs/export" filename="audit"
            params={{ entityType: filters.entityType, action: filters.action }} />
        )}
      </div>

      {/* M10 #12: Journal / Synthèse tabs */}
      <div className="flex gap-2 border-b border-hairline">
        <button onClick={() => setTab('journal')}
          className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === 'journal' ? 'border-primary text-primary' : 'border-transparent text-ink-soft'}`}>
          {t('admin.audit.summary.tabJournal')}
        </button>
        <button onClick={() => setTab('summary')}
          className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === 'summary' ? 'border-primary text-primary' : 'border-transparent text-ink-soft'}`}>
          {t('admin.audit.summary.tabSummary')}
        </button>
      </div>

      {tab === 'summary' && <AuditSummary scope="system" />}

      {tab === 'journal' && (
      <>
      {/* M10 #10: retention compliance indicator */}
      <RetentionComplianceCard />

      {/* M10: statistical anomaly detection */}
      <AnomalyPanel />

      {/* P11-51: live recent-activity feed */}
      <RecentActivityPanel />

      {/* Filters */}
      <Panel className="p-4 flex flex-wrap gap-3">
        <div className="flex items-center gap-2 border border-hairline rounded-[4px] px-3 py-2 text-sm flex-1 min-w-[180px]">
          <Search className="w-4 h-4 text-ink-faint shrink-0" />
          <input
            id="audit-filter-user"
            className="outline-none w-full bg-transparent"
            placeholder={t('admin.audit.user')}
            onChange={(e) => handleFilter('username', e.target.value)}
          />
        </div>
        <input
          id="audit-filter-entity"
          className="border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface"
          placeholder={t('admin.audit.entity')}
          onChange={(e) => handleFilter('entityType', e.target.value)}
        />
        <input
          id="audit-filter-action"
          className="border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface"
          placeholder={t('admin.audit.action')}
          onChange={(e) => handleFilter('action', e.target.value)}
        />
      </Panel>

      {/* Table */}
      <Panel>
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
          </div>
        ) : (
          <>
            <table className="w-full text-sm">
              <thead className="bg-ground">
                <tr>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.audit.date')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.audit.user')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.audit.action')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.audit.entity')}</th>
                  <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.audit.details')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {logs.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="text-center py-16 text-ink-faint">
                      {t('app.noData')}
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                      <td className="px-4 py-3 text-xs text-ink-faint whitespace-nowrap num">
                        {(() => {
                          const raw = log.createdAt ?? log.performedAt
                          if (!raw) return '—'
                          const d = new Date(raw)
                          return isNaN(d.getTime()) ? raw : formatDateTime(d)
                        })()}
                      </td>
                      <td className="px-4 py-3 font-medium text-ink-soft text-xs">
                        <div>{log.performedBy?.username ?? '—'}</div>
                        {log.ipAddress && <div className="text-ink-faint num">{log.ipAddress}</div>}
                      </td>
                      <td className="px-4 py-3">
                        <span className="num text-xs bg-warn-bg text-warn px-2 py-0.5 rounded-[4px] border border-warn/30 break-all">
                          {log.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-ink-soft text-xs">
                        <div>{log.entityType}</div>
                        {log.entityId && (
                          <span className="text-ink-faint num">{log.entityId.slice(0, 40)}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-ink-faint max-w-xs truncate">
                        {log.details ?? log.newValue ?? '—'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-hairline bg-ground">
                <span className="text-sm text-ink-faint">
                  {t('pagination.page')} {currentPage + 1} {t('pagination.of')} {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    id="audit-btn-prev"
                    disabled={currentPage === 0}
                    onClick={() => setFilters((p) => ({ ...p, page: p.page - 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface transition-colors"
                  >
                    <ChevronLeft className="w-4 h-4" /> {t('pagination.previous')}
                  </button>
                  <button
                    id="audit-btn-next"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => setFilters((p) => ({ ...p, page: p.page + 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface transition-colors"
                  >
                    {t('pagination.next')} <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </Panel>
      </>
      )}
    </div>
  )
}
