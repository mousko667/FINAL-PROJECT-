import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, Search, ChevronLeft, ChevronRight, Activity } from 'lucide-react'
import { ExportMenu } from '@/components/ui/ExportMenu'
import AuditSummary from '@/components/audit/AuditSummary'
import RetentionComplianceCard from '@/components/audit/RetentionComplianceCard'

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
  return d.toLocaleString()
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
    <div className="bg-white rounded-xl border p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Activity className="w-5 h-5 text-primary" />
          <h2 className="font-semibold text-gray-800">{t('admin.audit.recent.title')}</h2>
        </div>
        <span className="flex items-center gap-1.5 text-xs text-gray-400">
          <span className={`w-2 h-2 rounded-full ${isFetching ? 'bg-green-500 animate-pulse' : 'bg-green-400'}`} />
          {t('admin.audit.recent.live')}
        </span>
      </div>
      {recent.length === 0 ? (
        <p className="text-sm text-gray-400 py-2">{t('app.noData')}</p>
      ) : (
        <ul className="divide-y">
          {recent.map((log) => (
            <li key={log.id} className="flex items-center justify-between gap-3 py-2 text-sm">
              <div className="flex items-center gap-2 min-w-0">
                <span className="text-xs font-mono bg-amber-50 text-amber-700 px-2 py-0.5 rounded border border-amber-100 shrink-0">
                  {log.action}
                </span>
                <span className="text-gray-600 truncate">
                  {log.performedBy?.username ?? '—'} · {log.entityType}
                </span>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap shrink-0">
                {relativeTime(log.createdAt ?? log.performedAt, t)}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
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
    <div className="bg-white rounded-xl border border-red-200 p-4">
      <div className="flex items-center gap-2 mb-3">
        <Activity className="w-5 h-5 text-red-600" />
        <h2 className="font-semibold text-gray-800">{t('admin.audit.anomalies.title', 'Anomalies détectées')}</h2>
      </div>
      <ul className="divide-y">
        {anomalies.map((a, i) => (
          <li key={i} className="flex items-center justify-between gap-3 py-2 text-sm">
            <div className="flex items-center gap-2 min-w-0">
              <span className="text-xs font-mono bg-red-50 text-red-700 px-2 py-0.5 rounded border border-red-100 shrink-0">
                {t(`admin.audit.anomalies.${a.type}`, a.type)}
              </span>
              <span className="text-gray-700 truncate">{a.username}</span>
            </div>
            <span className="text-xs text-gray-500 text-right">{a.detail}</span>
          </li>
        ))}
      </ul>
    </div>
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
        <h1 className="text-2xl font-bold text-gray-900">{t('admin.audit.title')}</h1>
        {tab === 'journal' && (
          <ExportMenu endpoint="/audit-logs/export" filename="audit"
            params={{ entityType: filters.entityType, action: filters.action }} />
        )}
      </div>

      {/* M10 #12: Journal / Synthèse tabs */}
      <div className="flex gap-2 border-b">
        <button onClick={() => setTab('journal')}
          className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === 'journal' ? 'border-primary text-primary' : 'border-transparent text-gray-500'}`}>
          {t('admin.audit.summary.tabJournal')}
        </button>
        <button onClick={() => setTab('summary')}
          className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === 'summary' ? 'border-primary text-primary' : 'border-transparent text-gray-500'}`}>
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
      <div className="bg-white rounded-xl border p-4 flex flex-wrap gap-3">
        <div className="flex items-center gap-2 border rounded-lg px-3 py-2 text-sm flex-1 min-w-[180px]">
          <Search className="w-4 h-4 text-muted-foreground shrink-0" />
          <input
            id="audit-filter-user"
            className="outline-none w-full bg-transparent"
            placeholder={t('admin.audit.user')}
            onChange={(e) => handleFilter('username', e.target.value)}
          />
        </div>
        <input
          id="audit-filter-entity"
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          placeholder={t('admin.audit.entity')}
          onChange={(e) => handleFilter('entityType', e.target.value)}
        />
        <input
          id="audit-filter-action"
          className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
          placeholder={t('admin.audit.action')}
          onChange={(e) => handleFilter('action', e.target.value)}
        />
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.audit.date')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.audit.user')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.audit.action')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.audit.entity')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.audit.details')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {logs.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="text-center py-16 text-muted-foreground">
                      {t('app.noData')}
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3 text-xs text-gray-500 whitespace-nowrap">
                        {(() => {
                          const raw = log.createdAt ?? log.performedAt
                          if (!raw) return '—'
                          const d = new Date(raw)
                          return isNaN(d.getTime()) ? raw : d.toLocaleString()
                        })()}
                      </td>
                      <td className="px-4 py-3 font-medium text-gray-700 text-xs">
                        <div>{log.performedBy?.username ?? '—'}</div>
                        {log.ipAddress && <div className="text-gray-400 font-mono">{log.ipAddress}</div>}
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-xs font-mono bg-amber-50 text-amber-700 px-2 py-0.5 rounded border border-amber-100 break-all">
                          {log.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-gray-600 text-xs">
                        <div>{log.entityType}</div>
                        {log.entityId && (
                          <span className="text-muted-foreground font-mono">{log.entityId.slice(0, 40)}</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500 max-w-xs truncate">
                        {log.details ?? log.newValue ?? '—'}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t bg-gray-50">
                <span className="text-sm text-muted-foreground">
                  {t('pagination.page')} {currentPage + 1} {t('pagination.of')} {totalPages}
                </span>
                <div className="flex gap-2">
                  <button
                    id="audit-btn-prev"
                    disabled={currentPage === 0}
                    onClick={() => setFilters((p) => ({ ...p, page: p.page - 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40 hover:bg-white transition-colors"
                  >
                    <ChevronLeft className="w-4 h-4" /> {t('pagination.previous')}
                  </button>
                  <button
                    id="audit-btn-next"
                    disabled={currentPage >= totalPages - 1}
                    onClick={() => setFilters((p) => ({ ...p, page: p.page + 1 }))}
                    className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg disabled:opacity-40 hover:bg-white transition-colors"
                  >
                    {t('pagination.next')} <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
      </>
      )}
    </div>
  )
}
