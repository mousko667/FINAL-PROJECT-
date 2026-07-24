import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { reportService } from '@/services/reportService'
import apiClient from '@/services/apiClient'
import VolumeTrendSection from '@/components/reports/VolumeTrendSection'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { useHasRole } from '@/hooks/useHasRole'
import { PageHeader } from '@/components/ui/PageHeader'
import {
  FileSpreadsheet, FileCheck, TrendingUp, AlertTriangle, Clock,
  XCircle, Loader2, Download, BarChart3, ChevronDown, ChevronUp,
  Target, ThumbsDown, CalendarClock, FileStack,
} from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { formatAmount, formatDate, formatDateTime } from '@/lib/format'
import { notifyApiError } from '@/components/ErrorToaster'

function KpiCard({ title, value, sub, icon }: { title: string; value: string | number; sub?: string; icon: React.ReactNode; color?: string }) {
  return (
    <div className="bg-surface rounded-[4px] border border-hairline shadow-sm p-5 flex items-start gap-4">
      <div className="p-3 rounded-[4px] bg-ground text-ink-soft">{icon}</div>
      <div>
        <p className="text-xs text-ink-faint uppercase tracking-wide">{title}</p>
        <p className="num text-2xl font-bold text-ink mt-0.5">{value}</p>
        {sub && <p className="text-xs text-ink-soft mt-0.5">{sub}</p>}
      </div>
    </div>
  )
}

function Section({ title, defaultOpen = true, children }: { title: string; defaultOpen?: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="bg-surface rounded-[4px] border border-hairline shadow-sm overflow-x-auto">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between px-5 py-4 text-left font-semibold text-ink hover:bg-ground transition-colors"
      >
        {title}
        {open ? <ChevronUp className="w-4 h-4 text-ink-faint" /> : <ChevronDown className="w-4 h-4 text-ink-faint" />}
      </button>
      {open && <div className="border-t border-hairline px-5 py-4">{children}</div>}
    </div>
  )
}

export default function ReportsPage() {
  const { t } = useTranslation()
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [perfSupplierId, setPerfSupplierId] = useState('')
  
  const [fromDateCycle, setFromDateCycle] = useState('')
  const [toDateCycle, setToDateCycle] = useState('')
  const [supplierHistoryId, setSupplierHistoryId] = useState('')

  // Reports are DAF / Assistant Comptable only. Gate every fetch on the role so
  // the page doesn't fire calls the backend will 403 before PageRoleGuard hides it.
  const canView = useHasRole('ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE')

  const { data: kpi, isLoading: kpiLoading } = useQuery({
    queryKey: ['kpis'],
    queryFn: reportService.getKpis,
    enabled: canView,
    retry: false,
  })

  const { data: aging, isLoading: agingLoading } = useQuery({
    queryKey: ['aging'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { buckets: Array<{ label: string; count: number; totalAmount: number }> } }>('/reports/aging')
      return data.data
    },
    enabled: canView,
    retry: false,
  })

  const { data: bottlenecks, isLoading: bnLoading } = useQuery({
    queryKey: ['bottlenecks'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Array<{ approverUsername: string; invoiceCount: number; averageDays: number }> }>('/reports/bottlenecks')
      return data.data ?? []
    },
    enabled: canView,
    retry: false,
  })

  const { data: cashFlow, isLoading: cfLoading } = useQuery({
    queryKey: ['cash-flow'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { weeks: Array<{ weekLabel: string; totalAmount: number; invoiceCount: number }> } }>('/reports/cash-flow')
      return data.data
    },
    enabled: canView,
    retry: false,
  })

  const { data: budget, isLoading: budgetLoading } = useQuery({
    queryKey: ['budget-vs-actual'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: {
        lines: Array<{ departmentCode: string; nameFr: string; nameEn: string; budget: number | null; actual: number; variance: number | null; utilizationPercent: number | null }>
        totalBudget: number; totalActual: number
      } }>('/reports/budget-vs-actual')
      return data.data
    },
    enabled: canView,
    retry: false,
  })

  const { data: perfSuppliers } = useQuery({
    queryKey: ['report-active-suppliers'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Array<{ id: string; companyName: string }> } }>(
        '/suppliers', { params: { status: 'ACTIVE', size: 200 } }
      )
      return data.data?.content ?? []
    },
    enabled: canView,
    retry: false,
  })

  const { data: supplierPerf, isLoading: perfLoading } = useQuery({
    queryKey: ['supplier-performance', perfSupplierId],
    queryFn: () => reportService.getSupplierPerformance(perfSupplierId),
    enabled: canView && !!perfSupplierId,
    retry: false,
  })

  const { data: paymentCycle, isLoading: cycleLoading } = useQuery({
    queryKey: ['payment-cycle', fromDateCycle, toDateCycle],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { invoicesPaidCount: number; avgSubmissionToBapDays: number|null; avgBapToPaymentDays: number|null; avgScheduledToProcessedDays: number|null; avgTotalCycleDays: number|null } }>(
        '/reports/payment-cycle',
        { params: { from: fromDateCycle ? new Date(fromDateCycle).toISOString() : undefined, to: toDateCycle ? new Date(toDateCycle).toISOString() : undefined } }
      )
      return data.data
    },
    // AUDIT-022: `from` and `to` are mandatory @RequestParam on the backend. Firing
    // this on `canView` alone sent both as undefined and produced a systematic 400
    // on every single load of /reports. Same guard as the other date-driven sections.
    enabled: canView && !!fromDateCycle && !!toDateCycle,
  })

  const { data: supplierHistory, isLoading: supplierHistoryLoading } = useQuery({
    queryKey: ['supplier-payments', supplierHistoryId],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Array<{ paymentId: string; invoiceReference: string; amountPaid: number; paymentMethod: string; paymentDate: string; paymentReference: string }> }>(
        `/reports/supplier/${supplierHistoryId}/payments`
      )
      return data.data
    },
    enabled: canView && !!supplierHistoryId,
  })

  const { data: recentActivity, isLoading: recentActivityLoading } = useQuery({
    queryKey: ['recent-activity'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Array<{ id: string; invoiceId: string; referenceNumber: string; fromStatus: string; toStatus: string; changedBy: string; changedByUsername: string; changeReason: string; changedAt: string }> }>('/reports/activity?limit=20')
      return data.data
    },
    enabled: canView,
  })

  const excelMutation = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => reportService.exportExcel({ fromDate: fromDate || undefined, toDate: toDate || undefined }),
  })

  const complianceMutation = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => reportService.exportCompliancePdf(fromDate, toDate),
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE']}>
      <div className="space-y-6">
        <PageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />

        {/* Date range selector */}
        <div className="bg-surface rounded-[4px] border border-hairline shadow-sm p-5 flex flex-wrap items-end gap-4">
          <div>
            <label htmlFor="reports-from-date" className="block text-xs font-medium text-ink-faint uppercase tracking-wide mb-1">{t('reports.startDate')}</label>
            <input id="reports-from-date" type="date" value={fromDate} onChange={e => setFromDate(e.target.value)}
              className="border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink num focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label htmlFor="reports-to-date" className="block text-xs font-medium text-ink-faint uppercase tracking-wide mb-1">{t('reports.endDate')}</label>
            <input id="reports-to-date" type="date" value={toDate} onChange={e => setToDate(e.target.value)}
              className="border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink num focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <p className="text-xs text-ink-soft self-center">{t('reports.dateRangeHint')}</p>
        </div>

        {/* KPIs */}
        <Section title={t('reports.kpiTitle')}>
          {kpiLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <KpiCard title={t('dashboard.totalInvoices')} value={kpi?.totalInvoices ?? '—'} icon={<TrendingUp className="w-5 h-5 text-ink-soft" />} />
              <KpiCard title={t('dashboard.overdueInvoices')} value={kpi?.overdueCount ?? '—'} icon={<AlertTriangle className="w-5 h-5 text-ink-soft" />} />
              <KpiCard title={t('dashboard.avgProcessingTime')} value={kpi ? `${kpi.averageProcessingTimeDays.toFixed(1)} ${t('dashboard.days')}` : '—'} icon={<Clock className="w-5 h-5 text-ink-soft" />} />
              <KpiCard title={t('dashboard.rejectionRate')} value={kpi ? `${(kpi.rejectionRate * 100).toFixed(1)}%` : '—'} icon={<XCircle className="w-5 h-5 text-ink-soft" />} />
            </div>
          )}
        </Section>

        {/* P11-52: Budget vs Actual per department */}
        <Section title={t('reports.budgetTitle')}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.budgetDesc')}</p>
          {budgetLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !budget?.lines?.length ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-hairline text-left text-ink-soft">
                    <th className="px-3 py-2 font-medium">{t('reports.budgetDept')}</th>
                    <th className="px-3 py-2 font-medium text-right">{t('reports.budgetColBudget')}</th>
                    <th className="px-3 py-2 font-medium text-right">{t('reports.budgetColActual')}</th>
                    <th className="px-3 py-2 font-medium text-right">{t('reports.budgetColVariance')}</th>
                    <th className="px-3 py-2 font-medium text-right">{t('reports.budgetColUtil')}</th>
                  </tr>
                </thead>
                <tbody>
                  {budget.lines.map(l => {
                    const over = l.variance != null && l.variance < 0
                    return (
                      <tr key={l.departmentCode} className="border-b border-hairline last:border-0">
                        <td className="px-3 py-2 font-medium text-ink">{l.departmentCode}</td>
                        <td className="px-3 py-2 text-right text-ink-soft">
                          {l.budget != null ? `${formatAmount(l.budget)} XAF` : <span className="text-ink-faint">—</span>}
                        </td>
                        <td className="px-3 py-2 text-right text-ink-soft">{formatAmount(l.actual)} XAF</td>
                        <td className={`px-3 py-2 text-right font-medium ${l.variance == null ? 'text-ink-faint' : over ? 'text-crit' : 'text-pos'}`}>
                          {l.variance != null ? `${formatAmount(l.variance)} XAF` : '—'}
                        </td>
                        <td className="px-3 py-2 text-right">
                          {l.utilizationPercent != null ? (
                            <span className={over ? 'text-crit font-medium' : 'text-ink-soft'}>{Number(l.utilizationPercent).toFixed(1)}%</span>
                          ) : <span className="text-ink-faint">—</span>}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
                <tfoot>
                  <tr className="border-t-2 border-hairline-strong font-semibold text-ink">
                    <td className="px-3 py-2">{t('reports.budgetTotal')}</td>
                    <td className="px-3 py-2 text-right">{formatAmount(budget.totalBudget)} XAF</td>
                    <td className="px-3 py-2 text-right">{formatAmount(budget.totalActual)} XAF</td>
                    <td className="px-3 py-2" colSpan={2}></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </Section>

        {/* Aging analysis */}
        <Section title={t('reports.agingTitle')}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.agingDesc')}</p>
          {agingLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !aging?.buckets?.length ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <div className="space-y-3">
              {aging.buckets.map(b => (
                <div key={b.label} className="flex items-center gap-4">
                  <div className="w-28 text-xs font-medium text-ink-soft shrink-0">{b.label}</div>
                  <div className="flex-1 h-6 bg-ground rounded-full overflow-hidden">
                    <div
                      className="h-full bg-crit rounded-full transition-all"
                      style={{ width: `${Math.min(100, (b.count / Math.max(1, (aging.buckets[0]?.count ?? 1))) * 100)}%` }}
                    />
                  </div>
                  <div className="text-xs text-ink-soft w-20 text-right shrink-0">
                    {b.count} inv — {formatAmount(b.totalAmount)} XAF
                  </div>
                </div>
              ))}
            </div>
          )}
        </Section>

        {/* Bottlenecks */}
        <Section title={t('reports.bottleneckTitle')} defaultOpen={false}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.bottleneckDesc')}</p>
          {bnLoading ? (
            <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !bottlenecks?.length ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noBottlenecks')}</p>
          ) : (
            <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-ink-soft border-b border-hairline">
                  <th className="pb-2">{t('reports.approver')}</th>
                  <th className="pb-2 text-right">{t('reports.invoiceCount')}</th>
                  <th className="pb-2 text-right">{t('reports.avgDays')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {bottlenecks.map(b => (
                  <tr key={b.approverUsername} className="hover:bg-ground">
                    <td className="py-2.5 font-medium text-ink-soft">{b.approverUsername}</td>
                    <td className="py-2.5 text-right text-ink-soft">{b.invoiceCount}</td>
                    <td className="py-2.5 text-right">
                      <span className={`font-medium ${b.averageDays > 5 ? 'text-crit' : b.averageDays > 3 ? 'text-warn' : 'text-pos'}`}>
                        {b.averageDays.toFixed(1)}d
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          )}
        </Section>

        {/* Supplier performance */}
        <Section title={t('reports.supplierPerformance.title')} defaultOpen={false}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.supplierPerformance.desc')}</p>
          <div className="mb-4 max-w-md">
            <label htmlFor="perf-supplier" className="block text-xs font-medium text-ink-soft uppercase tracking-wide mb-1">
              {t('reports.supplierPerformance.selectLabel')}
            </label>
            <select
              id="perf-supplier"
              value={perfSupplierId}
              onChange={e => setPerfSupplierId(e.target.value)}
              className="w-full border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">{t('reports.supplierPerformance.selectPlaceholder')}</option>
              {(perfSuppliers ?? []).map(s => (
                <option key={s.id} value={s.id}>{s.companyName}</option>
              ))}
            </select>
          </div>

          {!perfSupplierId ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.supplierPerformance.prompt')}</p>
          ) : perfLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !supplierPerf ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <KpiCard
                title={t('reports.supplierPerformance.accuracy')}
                value={supplierPerf.invoiceAccuracyRate != null ? `${(supplierPerf.invoiceAccuracyRate * 100).toFixed(1)}%` : '—'}
                icon={<Target className="w-5 h-5 text-ink-soft" />}
              />
              <KpiCard
                title={t('reports.supplierPerformance.rejection')}
                value={supplierPerf.rejectionRate != null ? `${(supplierPerf.rejectionRate * 100).toFixed(1)}%` : '—'}
                icon={<ThumbsDown className="w-5 h-5 text-ink-soft" />}
              />
              <KpiCard
                title={t('reports.supplierPerformance.paymentDays')}
                value={supplierPerf.averagePaymentDays != null ? `${supplierPerf.averagePaymentDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
                icon={<CalendarClock className="w-5 h-5 text-ink-soft" />}
              />
              <KpiCard
                title={t('reports.supplierPerformance.submitted')}
                value={supplierPerf.totalInvoicesSubmitted}
                sub={t('reports.supplierPerformance.matchedSub', { matched: supplierPerf.matchedInvoices, mismatched: supplierPerf.mismatchedInvoices })}
                icon={<FileStack className="w-5 h-5 text-ink-soft" />}
              />
            </div>
          )}
        </Section>

        {/* Cash flow */}
        <Section title={t('reports.cashFlowTitle')} defaultOpen={false}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.cashFlowDesc')}</p>
          {cfLoading ? (
            <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !cashFlow?.weeks?.length ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={cashFlow.weeks}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="weekLabel" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} tickFormatter={v => `${(v/1000).toFixed(0)}k`} />
                <Tooltip formatter={(v) => [`${formatAmount(v)} XAF`]} />
                <Bar dataKey="totalAmount" fill="hsl(var(--primary))" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Section>

        {/* Payment Cycle */}
        <Section title={t('reports.paymentCycle.title')} defaultOpen={false}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.paymentCycle.desc')}</p>
          <div className="flex flex-wrap items-end gap-4 mb-6">
            <div>
              <label htmlFor="cycleFromDate" className="block text-xs font-medium text-ink-soft uppercase tracking-wide mb-1">{t('reports.startDate')}</label>
              <input id="cycleFromDate" type="date" value={fromDateCycle} onChange={e => setFromDateCycle(e.target.value)}
                className="border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div>
              <label htmlFor="cycleToDate" className="block text-xs font-medium text-ink-soft uppercase tracking-wide mb-1">{t('reports.endDate')}</label>
              <input id="cycleToDate" type="date" value={toDateCycle} onChange={e => setToDateCycle(e.target.value)}
                className="border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
          </div>
          {cycleLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !paymentCycle ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <KpiCard
                title={t('reports.paymentCycle.avgSubToBap')}
                value={paymentCycle.avgSubmissionToBapDays != null ? `${paymentCycle.avgSubmissionToBapDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
                icon={<Clock className="w-5 h-5 text-ink-soft" />}
              />
              <KpiCard
                title={t('reports.paymentCycle.avgBapToPay')}
                value={paymentCycle.avgBapToPaymentDays != null ? `${paymentCycle.avgBapToPaymentDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
                icon={<Clock className="w-5 h-5 text-ink-soft" />}
              />
              <KpiCard
                title={t('reports.paymentCycle.avgSchedToProc')}
                value={paymentCycle.avgScheduledToProcessedDays != null ? `${paymentCycle.avgScheduledToProcessedDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
                icon={<Clock className="w-5 h-5 text-ink-soft" />}
              />
              <KpiCard
                title={t('reports.paymentCycle.avgTotal')}
                value={paymentCycle.avgTotalCycleDays != null ? `${paymentCycle.avgTotalCycleDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
                sub={t('reports.paymentCycle.invoicesCount', { count: paymentCycle.invoicesPaidCount })}
                icon={<Clock className="w-5 h-5 text-ink-soft" />}
              />
            </div>
          )}
        </Section>

        {/* Supplier Payment History */}
        <Section title={t('reports.supplierHistory.title')} defaultOpen={false}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.supplierHistory.desc')}</p>
          <div className="mb-4 max-w-md">
            <label htmlFor="supplierHistoryId" className="block text-xs font-medium text-ink-soft uppercase tracking-wide mb-1">
              {t('reports.supplierHistory.selectLabel')}
            </label>
            <select
              id="supplierHistoryId"
              value={supplierHistoryId}
              onChange={e => setSupplierHistoryId(e.target.value)}
              className="w-full border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">{t('reports.supplierPerformance.selectPlaceholder')}</option>
              {(perfSuppliers ?? []).map(s => (
                <option key={s.id} value={s.id}>{s.companyName}</option>
              ))}
            </select>
          </div>
          {!supplierHistoryId ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.supplierPerformance.prompt')}</p>
          ) : supplierHistoryLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !supplierHistory?.length ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-hairline text-left text-ink-soft">
                    <th className="px-3 py-2 font-medium">{t('invoice.reference')}</th>
                    <th className="px-3 py-2 font-medium">{t('reports.supplierHistory.date')}</th>
                    <th className="px-3 py-2 font-medium">{t('reports.supplierHistory.method')}</th>
                    <th className="px-3 py-2 font-medium">{t('reports.supplierHistory.reference')}</th>
                    <th className="px-3 py-2 font-medium text-right">{t('reports.supplierHistory.amount')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {supplierHistory.map(p => (
                    <tr key={p.paymentId} className="hover:bg-ground">
                      <td className="px-3 py-2 font-medium text-ink">{p.invoiceReference}</td>
                      <td className="px-3 py-2 text-ink-soft">{formatDate(p.paymentDate)}</td>
                      <td className="px-3 py-2 text-ink-soft">{t(`invoice.paymentMethods.${p.paymentMethod}`, p.paymentMethod)}</td>
                      <td className="px-3 py-2 text-ink-soft">{p.paymentReference}</td>
                      <td className="px-3 py-2 text-right num text-ink">{formatAmount(p.amountPaid)} XAF</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Section>

        {/* Recent Activity */}
        <Section title={t('reports.recentActivity.title')} defaultOpen={false}>
          <p className="text-sm text-ink-soft mb-4">{t('reports.recentActivity.desc')}</p>
          {recentActivityLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !recentActivity?.length ? (
            <p className="text-sm text-center text-ink-soft py-4">{t('reports.noData')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-hairline text-left text-ink-soft">
                    <th className="px-3 py-2 font-medium">{t('reports.recentActivity.date', 'Date')}</th>
                    <th className="px-3 py-2 font-medium">{t('invoice.reference')}</th>
                    <th className="px-3 py-2 font-medium">{t('reports.recentActivity.transition', 'De → À')}</th>
                    <th className="px-3 py-2 font-medium">{t('reports.recentActivity.changedBy')}</th>
                    <th className="px-3 py-2 font-medium">{t('reports.recentActivity.reason')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {recentActivity.map(a => (
                    <tr key={a.id} className="hover:bg-ground">
                      <td className="px-3 py-2 text-ink-soft">{formatDateTime(a.changedAt)}</td>
                      <td className="px-3 py-2 font-medium text-ink">{a.referenceNumber}</td>
                      <td className="px-3 py-2 text-ink-soft">
                        {t(`status.${a.fromStatus}`, a.fromStatus)} → <span className="font-medium text-ink">{t(`status.${a.toStatus}`, a.toStatus)}</span>
                      </td>
                      <td className="px-3 py-2 text-ink-soft">{a.changedByUsername}</td>
                      <td className="px-3 py-2 text-ink-faint truncate max-w-[200px]">{a.changeReason ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Section>

        {/* Volume / value trends */}
        <VolumeTrendSection />

        {/* Exports */}
        <Section title={t('reports.dataExports')}>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Excel export */}
            <div className="flex items-start gap-4 p-4 rounded-[4px] border border-hairline bg-ground">
              <div className="p-2.5 bg-pos-bg rounded-[4px] shrink-0">
                <FileSpreadsheet className="w-5 h-5 text-pos" />
              </div>
              <div className="flex-1">
                <h3 className="font-semibold text-ink text-sm">{t('reports.exportExcel')}</h3>
                <p className="text-xs text-ink-soft mt-0.5">{t('reports.exportExcelDesc')}</p>
              </div>
              <button
                disabled={excelMutation.isPending}
                onClick={() => excelMutation.mutate()}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-primary text-primary-foreground rounded-[4px] text-xs font-medium hover:bg-primary/90 disabled:opacity-50 shrink-0"
              >
                {excelMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />}
                {t('app.export')}
              </button>
            </div>

            {/* Compliance PDF */}
            <div className="flex items-start gap-4 p-4 rounded-[4px] border border-hairline bg-ground">
              <div className="p-2.5 bg-info-bg rounded-[4px] shrink-0">
                <FileCheck className="w-5 h-5 text-info" />
              </div>
              <div className="flex-1">
                <h3 className="font-semibold text-ink text-sm">{t('reports.exportPdfCompliance')}</h3>
                <p className="text-xs text-ink-soft mt-0.5">{t('reports.exportPdfComplianceDesc')}</p>
                {(!fromDate || !toDate) && (
                  <p className="text-xs text-warn mt-1">{t('reports.dateRangeRequired')}</p>
                )}
              </div>
              <button
                disabled={complianceMutation.isPending || !fromDate || !toDate}
                onClick={() => complianceMutation.mutate()}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-primary text-primary-foreground rounded-[4px] text-xs font-medium hover:bg-primary/90 disabled:opacity-50 shrink-0"
              >
                {complianceMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />}
                {t('app.export')}
              </button>
            </div>
          </div>
        </Section>
      </div>
    </PageRoleGuard>
  )
}
