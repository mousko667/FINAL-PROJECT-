import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, Search, ChevronLeft, ChevronRight, FileDown } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { reportService } from '@/services/reportService'
import AuditSummary from '@/components/audit/AuditSummary'
import { PageHeader } from '@/components/ui/PageHeader'
import { formatDateTime } from '@/lib/format'

interface AuditLog {
  id: string
  action: string
  entityType: string
  entityId?: string
  performedBy?: { username: string }
  performedAt?: string
  createdAt?: string
  ipAddress?: string
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

export default function FinancialAuditPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'journal' | 'summary'>('journal')
  const [filters, setFilters] = useState<AuditFilters>({ page: 0, size: 20 })

  const exportMutation = useMutation({
    mutationFn: () => {
      const now = new Date()
      const start = `${now.getFullYear()}-01-01`
      const end = now.toISOString().slice(0, 10)
      return reportService.exportCompliancePdf(start, end)
    },
  })

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['audit-logs-financial', filters],
    queryFn: async () => {
      // DAF sees financial logs only (invoices, approvals, rejections, payments)
      // financial endpoint returns 0 entries because AuditLoggingFilter records all
      // as HTTP_REQUEST type. Use main endpoint filtered by invoice/payment actions.
      const { data } = await apiClient.get<ApiResponse<PagedResponse<AuditLog>>>(
        '/audit-logs',
        { params: { ...filters, entityType: 'HTTP_REQUEST' } }
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
    <PageRoleGuard allowedRoles={['ROLE_DAF']}>
      <div className="space-y-6">
        <PageHeader
          title={t('audit.financial.title', 'Financial Audit Trail')}
          subtitle={t('audit.financial.subtitle', 'Immutable record of all financial events — invoice submissions, approvals, rejections, payments.')}
          actions={tab === 'journal' && (
            <button
              onClick={() => exportMutation.mutate()}
              disabled={exportMutation.isPending}
              className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground disabled:opacity-60"
            >
              {exportMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileDown className="w-4 h-4" />}
              {t('audit.financial.exportPdf', 'Export PDF')}
            </button>
          )}
        />

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

        {tab === 'summary' && <AuditSummary scope="financial" />}

        {tab === 'journal' && (
        <>
        {/* Filters */}
        <div className="bg-surface rounded-[4px] border p-4 flex flex-wrap gap-3">
          <div className="flex items-center gap-2 border border-hairline rounded-[4px] px-3 py-2 text-sm flex-1 min-w-[180px]">
            <Search className="w-4 h-4 text-muted-foreground shrink-0" />
            <input
              className="outline-none w-full bg-transparent"
              placeholder={t('admin.audit.user', 'User')}
              onChange={(e) => handleFilter('username', e.target.value)}
            />
          </div>
          <input
            className="border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
            placeholder={t('admin.audit.entity', 'Entity type (e.g. Invoice)')}
            onChange={(e) => handleFilter('entityType', e.target.value)}
          />
          <input
            className="border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30"
            placeholder={t('admin.audit.action', 'Action (e.g. APPROVE)')}
            onChange={(e) => handleFilter('action', e.target.value)}
          />
        </div>

        {/* Table */}
        <div className="bg-surface rounded-[4px] border overflow-hidden">
          {isLoading ? (
            <div className="flex items-center justify-center py-20">
              <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
            </div>
          ) : isError ? (
            <div className="text-center py-20 space-y-3">
              <p className="text-crit text-sm">{t('app.error')}</p>
              <button onClick={() => refetch()} className="px-4 py-2 text-sm border border-hairline rounded-[4px] hover:bg-ground">
                {t('app.retry')}
              </button>
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="bg-ground">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.audit.date')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.audit.user')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.audit.action')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.audit.entity')}</th>
                    <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('admin.audit.details')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {logs.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td>
                    </tr>
                  ) : logs.map((log) => (
                    <tr key={log.id} className="hover:bg-ground">
                      <td className="px-4 py-3 text-xs text-ink-soft whitespace-nowrap">
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
                        <span className="text-xs num bg-ground text-ink-soft px-2 py-0.5 rounded border border-hairline">
                          {log.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-ink-soft">
                        {log.entityType}
                        {log.entityId && <span className="ml-1 text-xs text-muted-foreground">#{log.entityId.slice(0, 8)}</span>}
                      </td>
                      <td className="px-4 py-3 text-xs text-ink-soft max-w-xs truncate">{log.details ?? log.newValue ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {totalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 border-t border-hairline bg-ground">
                  <span className="text-sm text-muted-foreground">
                    {t('pagination.page')} {currentPage + 1} {t('pagination.of')} {totalPages}
                  </span>
                  <div className="flex gap-2">
                    <button
                      disabled={currentPage === 0}
                      onClick={() => setFilters((p) => ({ ...p, page: p.page - 1 }))}
                      className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface"
                    >
                      <ChevronLeft className="w-4 h-4" /> {t('pagination.previous')}
                    </button>
                    <button
                      disabled={currentPage >= totalPages - 1}
                      onClick={() => setFilters((p) => ({ ...p, page: p.page + 1 }))}
                      className="flex items-center gap-1 px-3 py-1.5 text-sm border border-hairline rounded-[4px] disabled:opacity-40 hover:bg-surface"
                    >
                      {t('pagination.next')} <ChevronRight className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>

        <p className="text-xs text-ink-faint">
          {t('audit.financial.immutableNote', 'This audit trail is immutable — records cannot be modified or deleted.')}
        </p>
        </>
        )}
      </div>
    </PageRoleGuard>
  )
}
