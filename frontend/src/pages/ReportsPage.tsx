import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { reportService } from '@/services/reportService'
import apiClient from '@/services/apiClient'
import VolumeTrendSection from '@/components/reports/VolumeTrendSection'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import {
  FileSpreadsheet, FileCheck, TrendingUp, AlertTriangle, Clock,
  XCircle, Loader2, Download, BarChart3, ChevronDown, ChevronUp,
  Target, ThumbsDown, CalendarClock, FileStack,
} from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { formatAmount } from '@/lib/format'

function KpiCard({ title, value, sub, icon, color }: { title: string; value: string | number; sub?: string; icon: React.ReactNode; color: string }) {
  return (
    <div className="bg-white rounded-xl border p-5 flex items-start gap-4">
      <div className={`p-3 rounded-xl ${color}`}>{icon}</div>
      <div>
        <p className="text-xs text-muted-foreground uppercase tracking-wide">{title}</p>
        <p className="text-2xl font-bold text-gray-900 mt-0.5">{value}</p>
        {sub && <p className="text-xs text-gray-500 mt-0.5">{sub}</p>}
      </div>
    </div>
  )
}

function Section({ title, defaultOpen = true, children }: { title: string; defaultOpen?: boolean; children: React.ReactNode }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      <button
        onClick={() => setOpen(o => !o)}
        className="w-full flex items-center justify-between px-5 py-4 text-left font-semibold text-gray-900 hover:bg-gray-50 transition-colors"
      >
        {title}
        {open ? <ChevronUp className="w-4 h-4 text-gray-400" /> : <ChevronDown className="w-4 h-4 text-gray-400" />}
      </button>
      {open && <div className="border-t px-5 py-4">{children}</div>}
    </div>
  )
}

export default function ReportsPage() {
  const { t } = useTranslation()
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [perfSupplierId, setPerfSupplierId] = useState('')

  const { data: kpi, isLoading: kpiLoading } = useQuery({
    queryKey: ['kpis'],
    queryFn: reportService.getKpis,
    retry: false,
  })

  const { data: aging, isLoading: agingLoading } = useQuery({
    queryKey: ['aging'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { buckets: Array<{ label: string; count: number; totalAmount: number }> } }>('/reports/aging')
      return data.data
    },
    retry: false,
  })

  const { data: bottlenecks, isLoading: bnLoading } = useQuery({
    queryKey: ['bottlenecks'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Array<{ approverUsername: string; invoiceCount: number; averageDays: number }> }>('/reports/bottlenecks')
      return data.data ?? []
    },
    retry: false,
  })

  const { data: cashFlow, isLoading: cfLoading } = useQuery({
    queryKey: ['cash-flow'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { weeks: Array<{ weekLabel: string; totalAmount: number; invoiceCount: number }> } }>('/reports/cash-flow')
      return data.data
    },
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
    retry: false,
  })

  const { data: supplierPerf, isLoading: perfLoading } = useQuery({
    queryKey: ['supplier-performance', perfSupplierId],
    queryFn: () => reportService.getSupplierPerformance(perfSupplierId),
    enabled: !!perfSupplierId,
    retry: false,
  })

  const excelMutation = useMutation({
    mutationFn: () => reportService.exportExcel({ fromDate: fromDate || undefined, toDate: toDate || undefined }),
  })

  const complianceMutation = useMutation({
    mutationFn: () => reportService.exportCompliancePdf(fromDate, toDate),
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE']}>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('reports.title')}</h1>
          <p className="text-sm text-gray-500 mt-1">{t('reports.subtitle')}</p>
        </div>

        {/* Date range selector */}
        <div className="bg-white rounded-xl border p-5 flex flex-wrap items-end gap-4">
          <div>
            <label htmlFor="reports-from-date" className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{t('reports.startDate')}</label>
            <input id="reports-from-date" type="date" value={fromDate} onChange={e => setFromDate(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label htmlFor="reports-to-date" className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">{t('reports.endDate')}</label>
            <input id="reports-to-date" type="date" value={toDate} onChange={e => setToDate(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <p className="text-xs text-gray-500 self-center">{t('reports.dateRangeHint')}</p>
        </div>

        {/* KPIs */}
        <Section title={t('reports.kpiTitle')}>
          {kpiLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <KpiCard title={t('dashboard.totalInvoices')} value={kpi?.totalInvoices ?? '—'} icon={<TrendingUp className="w-5 h-5 text-blue-600" />} color="bg-blue-50" />
              <KpiCard title={t('dashboard.overdueInvoices')} value={kpi?.overdueCount ?? '—'} icon={<AlertTriangle className="w-5 h-5 text-red-600" />} color="bg-red-50" />
              <KpiCard title={t('dashboard.avgProcessingTime')} value={kpi ? `${kpi.averageProcessingTimeDays.toFixed(1)} ${t('dashboard.days')}` : '—'} icon={<Clock className="w-5 h-5 text-amber-600" />} color="bg-amber-50" />
              <KpiCard title={t('dashboard.rejectionRate')} value={kpi ? `${(kpi.rejectionRate * 100).toFixed(1)}%` : '—'} icon={<XCircle className="w-5 h-5 text-orange-600" />} color="bg-orange-50" />
            </div>
          )}
        </Section>

        {/* P11-52: Budget vs Actual per department */}
        <Section title={t('reports.budgetTitle')}>
          <p className="text-sm text-gray-500 mb-4">{t('reports.budgetDesc')}</p>
          {budgetLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !budget?.lines?.length ? (
            <p className="text-sm text-center text-gray-500 py-4">{t('reports.noData')}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-gray-500">
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
                      <tr key={l.departmentCode} className="border-b last:border-0">
                        <td className="px-3 py-2 font-medium text-gray-900">{l.departmentCode}</td>
                        <td className="px-3 py-2 text-right text-gray-600">
                          {l.budget != null ? `${formatAmount(l.budget)} XOF` : <span className="text-gray-300">—</span>}
                        </td>
                        <td className="px-3 py-2 text-right text-gray-600">{formatAmount(l.actual)} XOF</td>
                        <td className={`px-3 py-2 text-right font-medium ${l.variance == null ? 'text-gray-300' : over ? 'text-red-600' : 'text-green-700'}`}>
                          {l.variance != null ? `${formatAmount(l.variance)} XOF` : '—'}
                        </td>
                        <td className="px-3 py-2 text-right">
                          {l.utilizationPercent != null ? (
                            <span className={over ? 'text-red-600 font-medium' : 'text-gray-600'}>{Number(l.utilizationPercent).toFixed(1)}%</span>
                          ) : <span className="text-gray-300">—</span>}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
                <tfoot>
                  <tr className="border-t-2 font-semibold text-gray-800">
                    <td className="px-3 py-2">{t('reports.budgetTotal')}</td>
                    <td className="px-3 py-2 text-right">{formatAmount(budget.totalBudget)} XOF</td>
                    <td className="px-3 py-2 text-right">{formatAmount(budget.totalActual)} XOF</td>
                    <td className="px-3 py-2" colSpan={2}></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          )}
        </Section>

        {/* Aging analysis */}
        <Section title={t('reports.agingTitle')}>
          <p className="text-sm text-gray-500 mb-4">{t('reports.agingDesc')}</p>
          {agingLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !aging?.buckets?.length ? (
            <p className="text-sm text-center text-gray-500 py-4">{t('reports.noData')}</p>
          ) : (
            <div className="space-y-3">
              {aging.buckets.map(b => (
                <div key={b.label} className="flex items-center gap-4">
                  <div className="w-28 text-xs font-medium text-gray-600 shrink-0">{b.label}</div>
                  <div className="flex-1 h-6 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-red-400 rounded-full transition-all"
                      style={{ width: `${Math.min(100, (b.count / Math.max(1, (aging.buckets[0]?.count ?? 1))) * 100)}%` }}
                    />
                  </div>
                  <div className="text-xs text-gray-500 w-20 text-right shrink-0">
                    {b.count} inv — {formatAmount(b.totalAmount)} XOF
                  </div>
                </div>
              ))}
            </div>
          )}
        </Section>

        {/* Bottlenecks */}
        <Section title={t('reports.bottleneckTitle')} defaultOpen={false}>
          <p className="text-sm text-gray-500 mb-4">{t('reports.bottleneckDesc')}</p>
          {bnLoading ? (
            <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !bottlenecks?.length ? (
            <p className="text-sm text-center text-gray-500 py-4">No SLA breaches detected — all approvals are within the 3-day SLA.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 border-b">
                  <th className="pb-2">{t('reports.approver')}</th>
                  <th className="pb-2 text-right">{t('reports.invoiceCount')}</th>
                  <th className="pb-2 text-right">{t('reports.avgDays')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {bottlenecks.map(b => (
                  <tr key={b.approverUsername} className="hover:bg-gray-50">
                    <td className="py-2.5 font-medium text-gray-700">{b.approverUsername}</td>
                    <td className="py-2.5 text-right text-gray-600">{b.invoiceCount}</td>
                    <td className="py-2.5 text-right">
                      <span className={`font-medium ${b.averageDays > 5 ? 'text-red-600' : b.averageDays > 3 ? 'text-amber-600' : 'text-green-600'}`}>
                        {b.averageDays.toFixed(1)}d
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Section>

        {/* Supplier performance */}
        <Section title={t('reports.supplierPerformance.title')} defaultOpen={false}>
          <p className="text-sm text-gray-500 mb-4">{t('reports.supplierPerformance.desc')}</p>
          <div className="mb-4 max-w-md">
            <label htmlFor="perf-supplier" className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1">
              {t('reports.supplierPerformance.selectLabel')}
            </label>
            <select
              id="perf-supplier"
              value={perfSupplierId}
              onChange={e => setPerfSupplierId(e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">{t('reports.supplierPerformance.selectPlaceholder')}</option>
              {(perfSuppliers ?? []).map(s => (
                <option key={s.id} value={s.id}>{s.companyName}</option>
              ))}
            </select>
          </div>

          {!perfSupplierId ? (
            <p className="text-sm text-center text-gray-500 py-4">{t('reports.supplierPerformance.prompt')}</p>
          ) : perfLoading ? (
            <div className="flex justify-center py-6"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !supplierPerf ? (
            <p className="text-sm text-center text-gray-500 py-4">{t('reports.noData')}</p>
          ) : (
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <KpiCard
                title={t('reports.supplierPerformance.accuracy')}
                value={supplierPerf.invoiceAccuracyRate != null ? `${(supplierPerf.invoiceAccuracyRate * 100).toFixed(1)}%` : '—'}
                icon={<Target className="w-5 h-5 text-green-600" />} color="bg-green-50"
              />
              <KpiCard
                title={t('reports.supplierPerformance.rejection')}
                value={supplierPerf.rejectionRate != null ? `${(supplierPerf.rejectionRate * 100).toFixed(1)}%` : '—'}
                icon={<ThumbsDown className="w-5 h-5 text-orange-600" />} color="bg-orange-50"
              />
              <KpiCard
                title={t('reports.supplierPerformance.paymentDays')}
                value={supplierPerf.averagePaymentDays != null ? `${supplierPerf.averagePaymentDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
                icon={<CalendarClock className="w-5 h-5 text-blue-600" />} color="bg-blue-50"
              />
              <KpiCard
                title={t('reports.supplierPerformance.submitted')}
                value={supplierPerf.totalInvoicesSubmitted}
                sub={t('reports.supplierPerformance.matchedSub', { matched: supplierPerf.matchedInvoices, mismatched: supplierPerf.mismatchedInvoices })}
                icon={<FileStack className="w-5 h-5 text-indigo-600" />} color="bg-indigo-50"
              />
            </div>
          )}
        </Section>

        {/* Cash flow */}
        <Section title={t('reports.cashFlowTitle')} defaultOpen={false}>
          <p className="text-sm text-gray-500 mb-4">{t('reports.cashFlowDesc')}</p>
          {cfLoading ? (
            <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : !cashFlow?.weeks?.length ? (
            <p className="text-sm text-center text-gray-500 py-4">{t('reports.noData')}</p>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={cashFlow.weeks}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="weekLabel" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} tickFormatter={v => `${(v/1000).toFixed(0)}k`} />
                <Tooltip formatter={(v) => [`${formatAmount(v)} XOF`]} />
                <Bar dataKey="totalAmount" fill="#6366f1" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Section>

        {/* Volume / value trends */}
        <VolumeTrendSection />

        {/* Exports */}
        <Section title="Data Exports">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Excel export */}
            <div className="flex items-start gap-4 p-4 rounded-xl border bg-gray-50">
              <div className="p-2.5 bg-green-50 rounded-xl shrink-0">
                <FileSpreadsheet className="w-5 h-5 text-green-600" />
              </div>
              <div className="flex-1">
                <h3 className="font-semibold text-gray-800 text-sm">{t('reports.exportExcel')}</h3>
                <p className="text-xs text-gray-500 mt-0.5">{t('reports.exportExcelDesc')}</p>
              </div>
              <button
                disabled={excelMutation.isPending}
                onClick={() => excelMutation.mutate()}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white rounded-lg text-xs font-medium hover:bg-green-700 disabled:opacity-50 shrink-0"
              >
                {excelMutation.isPending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />}
                {t('app.export')}
              </button>
            </div>

            {/* Compliance PDF */}
            <div className="flex items-start gap-4 p-4 rounded-xl border bg-gray-50">
              <div className="p-2.5 bg-purple-50 rounded-xl shrink-0">
                <FileCheck className="w-5 h-5 text-purple-600" />
              </div>
              <div className="flex-1">
                <h3 className="font-semibold text-gray-800 text-sm">{t('reports.exportPdfCompliance')}</h3>
                <p className="text-xs text-gray-500 mt-0.5">{t('reports.exportPdfComplianceDesc')}</p>
                {(!fromDate || !toDate) && (
                  <p className="text-xs text-amber-600 mt-1">{t('reports.dateRangeRequired')}</p>
                )}
              </div>
              <button
                disabled={complianceMutation.isPending || !fromDate || !toDate}
                onClick={() => complianceMutation.mutate()}
                className="flex items-center gap-1.5 px-3 py-1.5 bg-purple-600 text-white rounded-lg text-xs font-medium hover:bg-purple-700 disabled:opacity-50 shrink-0"
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
