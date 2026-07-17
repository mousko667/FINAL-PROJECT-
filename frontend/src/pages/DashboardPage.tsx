import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { reportService } from '@/services/reportService'
import apiClient from '@/services/apiClient'
import { useAppSelector } from '@/store/hooks'
import { DashboardAnnouncements, BudgetAlerts, PrivacyPolicyBanner } from '@/components/dashboard/DashboardPanels'
import AgingBucketsWidget from '@/components/dashboard/AgingBucketsWidget'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
} from 'recharts'
import {
  Users, Building2, ScrollText, FileText, CheckCircle,
  DollarSign, ArrowRight, Plus, GitBranch, Shield, Zap,
  Package, BarChart3, ChevronRight,
} from 'lucide-react'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { SkeletonCard, SkeletonDashboard, Skeleton } from '@/components/ui/Skeleton'
import { KpiBand, type KpiBandItem } from '@/components/ui/KpiBand'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import type { InvoiceStatus } from '@/types/invoice'
import { formatAmount } from '@/lib/format'

const STATUS_COLORS: Record<string, string> = {
  BROUILLON:        '#94a3b8',
  SOUMIS:           '#3b82f6',
  EN_VALIDATION_N1: '#f59e0b',
  EN_VALIDATION_N2: '#f97316',
  VALIDE:           '#14b8a6',
  BON_A_PAYER:      '#22c55e',
  PAYE:             '#10b981',
  ARCHIVE:          '#64748b',
  REJETE:           '#ef4444',
}

function QuickLink({ to, icon: Icon, label, sub, color }: { to: string; icon: React.ElementType; label: string; sub?: string; color: string }) {
  return (
    <Link
      to={to}
      className={`flex items-center gap-3 p-4 rounded-[4px] border transition-all hover:shadow-sm hover:-translate-y-0.5 ${color}`}
    >
      <Icon className="w-5 h-5 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">{label}</p>
        {sub && <p className="text-xs opacity-70 mt-0.5">{sub}</p>}
      </div>
      <ChevronRight className="w-4 h-4 opacity-40 shrink-0" />
    </Link>
  )
}

export default function DashboardPage() {
  const { t } = useTranslation()
  const roles = useAppSelector((state) => state.auth.user?.roles ?? [])

  const isAdmin    = roles.includes('ROLE_ADMIN')
  const isSupplier = roles.includes('ROLE_SUPPLIER')
  const isAA       = roles.includes('ROLE_ASSISTANT_COMPTABLE')
  const isDaf      = roles.includes('ROLE_DAF')
  const isValidator = roles.some(r => r.startsWith('ROLE_VALIDATEUR_N1_') || r.startsWith('ROLE_VALIDATEUR_N2_'))
  const canViewKpis = isAA || isDaf

  const { data: kpi, isLoading: kpiLoading } = useQuery({
    queryKey: ['kpis'],
    queryFn: reportService.getKpis,
    enabled: canViewKpis,
    retry: false,
  })

  // REQ-04: validator's own dashboard stats (approved total, processed this month).
  const { data: validatorStats, isLoading: validatorStatsLoading } = useQuery({
    queryKey: ['my-validator-stats'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { approvedTotal: number; processedThisMonth: number } }>('/workflow/my-stats')
      return data.data
    },
    enabled: isValidator,
  })

  const { data: pendingQueue } = useQuery({
    queryKey: ['pending-validation-queue'],
    queryFn: async () => {
      const response = await apiClient.get('/invoices/pending-validation', { params: { size: 5 } })
      return response.data.data as { content: Array<{ id: string; referenceNumber: string; supplierName: string; amount: number; status: string; departmentCode?: string }> }
    },
    enabled: isAA || isDaf || isValidator,
    retry: false,
  })

  const { data: supplierDash } = useQuery({
    queryKey: ['supplier-dashboard'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Record<string, number> }>('/supplier/dashboard')
      return data.data
    },
    enabled: isSupplier,
    retry: false,
  })

  // ── ADMIN ──────────────────────────────────────────────────────────────────
  if (isAdmin) {
    return (
      <div className="space-y-6 page-enter">
        <PageHeader title={t('dashboard.title')} subtitle={t('dashboard.adminNote')} />

        {/* Primary admin actions */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Link to="/admin/users" className="group flex flex-col items-center p-5 rounded-[4px] bg-surface hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] border border-hairline hover:border-gold/40 transition-all text-center">
            <div className="w-12 h-12 rounded-[4px] bg-ground flex items-center justify-center mb-3">
              <Users className="w-6 h-6 text-ink-soft" />
            </div>
            <span className="text-sm font-semibold text-ink">{t('nav.users')}</span>
            <span className="text-xs text-ink-faint mt-0.5">{t('dashboard.manageAccounts', 'Gérer les comptes')}</span>
          </Link>

          <Link to="/admin/departments" className="group flex flex-col items-center p-5 rounded-[4px] bg-surface hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] border border-hairline hover:border-gold/40 transition-all text-center">
            <div className="w-12 h-12 rounded-[4px] bg-ground flex items-center justify-center mb-3">
              <Building2 className="w-6 h-6 text-ink-soft" />
            </div>
            <span className="text-sm font-semibold text-ink">{t('nav.departments')}</span>
            <span className="text-xs text-ink-faint mt-0.5">{t('dashboard.deptMatrix', 'Départements & matrice')}</span>
          </Link>

          <Link to="/admin/audit" className="group flex flex-col items-center p-5 rounded-[4px] bg-surface hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] border border-hairline hover:border-gold/40 transition-all text-center">
            <div className="w-12 h-12 rounded-[4px] bg-ground flex items-center justify-center mb-3">
              <ScrollText className="w-6 h-6 text-ink-soft" />
            </div>
            <span className="text-sm font-semibold text-ink">{t('nav.auditLog')}</span>
            <span className="text-xs text-ink-faint mt-0.5">{t('dashboard.securityLogs', 'Journaux système')}</span>
          </Link>

          {/* N16: the admin has ZERO supplier surface — no "Registre fournisseurs" quick-action. */}
        </div>

        {/* Secondary admin actions */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Link to="/admin/approval-matrix" className="flex items-center gap-3 p-4 rounded-[4px] bg-surface border border-hairline hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] hover:border-gold/40 transition-all group">
            <div className="w-9 h-9 rounded-[4px] bg-ground flex items-center justify-center">
              <GitBranch className="w-4 h-4 text-ink-soft" />
            </div>
            <div>
              <p className="text-sm font-semibold text-ink">{t('admin.approvalMatrix.title', 'Matrice d\'approbation')}</p>
              <p className="text-xs text-ink-faint">{t('dashboard.validationWorkflows')}</p>
            </div>
          </Link>
          <Link to="/admin/security" className="flex items-center gap-3 p-4 rounded-[4px] bg-surface border border-hairline hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] hover:border-gold/40 transition-all group">
            <div className="w-9 h-9 rounded-[4px] bg-ground flex items-center justify-center">
              <Shield className="w-4 h-4 text-ink-soft" />
            </div>
            <div>
              <p className="text-sm font-semibold text-ink">{t('admin.security.title', 'Paramètres de sécurité')}</p>
              <p className="text-xs text-ink-faint">{t('dashboard.mfaSessionsAccess')}</p>
            </div>
          </Link>
          <Link to="/admin/integrations" className="flex items-center gap-3 p-4 rounded-[4px] bg-surface border border-hairline hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] hover:border-gold/40 transition-all group">
            <div className="w-9 h-9 rounded-[4px] bg-ground flex items-center justify-center">
              <Zap className="w-4 h-4 text-ink-soft" />
            </div>
            <div>
              <p className="text-sm font-semibold text-ink">{t('admin.integrations.title', 'Intégrations')}</p>
              <p className="text-xs text-ink-faint">{t('dashboard.webhooksApiKeys')}</p>
            </div>
          </Link>
        </div>
      </div>
    )
  }

  // ── SUPPLIER ───────────────────────────────────────────────────────────────
  if (isSupplier) {
    const sd = supplierDash as Record<string, number> | undefined
    const kpiItems: KpiBandItem[] = [
      { label: t('supplier.portal.submitted'), value: sd?.submittedCount ?? 0 },
      { label: t('supplier.portal.pending'),   value: sd?.pendingCount   ?? 0, tone: 'warn' },
      { label: t('supplier.portal.approved'),  value: sd?.approvedCount  ?? 0, tone: 'pos' },
      { label: t('supplier.portal.paid'),      value: sd?.paidCount      ?? 0, tone: 'pos' },
      { label: t('supplier.portal.rejected'),  value: sd?.rejectedCount  ?? 0, tone: 'crit' },
    ]
    return (
      <div className="space-y-6 page-enter">
        <PageHeader title={t('dashboard.title')} subtitle={t('dashboard.supplierRealtimeTracking')} />
        <KpiBand items={kpiItems} className="grid grid-cols-2 md:grid-cols-5 [&>div]:text-center" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Link to="/supplier/invoices/new" className="flex items-center gap-4 p-5 bg-oct-navy text-white rounded-[4px] hover:bg-oct-navy-light transition-colors shadow-sm">
            <div className="w-10 h-10 rounded-lg bg-oct-gold flex items-center justify-center shrink-0">
              <Plus className="w-5 h-5 text-oct-navy" />
            </div>
            <div>
              <p className="font-semibold">{t('supplier.portal.submitInvoice')}</p>
              <p className="text-xs opacity-70 mt-0.5">{t('dashboard.pdfJpegPng')}</p>
            </div>
            <ArrowRight className="w-5 h-5 ml-auto opacity-60" />
          </Link>
          <Link to="/supplier/invoices" className="flex items-center gap-4 p-5 bg-surface border border-hairline rounded-[4px] hover:bg-ground transition-colors">
            <div className="w-10 h-10 rounded-lg bg-ground flex items-center justify-center shrink-0">
              <FileText className="w-5 h-5 text-ink-soft" />
            </div>
            <div>
              <p className="font-semibold text-ink">{t('supplier.portal.viewInvoices')}</p>
              <p className="text-xs text-ink-soft mt-0.5">{t('dashboard.statusTracking')}</p>
            </div>
            <ArrowRight className="w-5 h-5 ml-auto text-ink-faint" />
          </Link>
        </div>
      </div>
    )
  }

  // ── VALIDATOR (N1/N2) ──────────────────────────────────────────────────────
  if (isValidator) {
    const queue = pendingQueue?.content ?? []
    const validatorKpis: KpiBandItem[] = [
      { label: t('dashboard.awaitingMyValidation'), value: queue.length, tone: 'warn' },
      { label: t('dashboard.processedThisMonth', 'Traitées ce mois'), value: validatorStatsLoading ? '…' : (validatorStats?.processedThisMonth ?? '—') },
      { label: t('dashboard.approved', 'Approuvées'), value: validatorStatsLoading ? '…' : (validatorStats?.approvedTotal ?? '—'), tone: 'pos' },
    ]
    return (
      <div className="space-y-6 page-enter">
        <PageHeader title={t('dashboard.title')} subtitle={t('dashboard.validatorQueueSub')} />

        <KpiBand items={validatorKpis} className="grid grid-cols-3 [&>div]:text-center" />

        <Panel
          className="overflow-hidden"
          title={
            <div className="flex items-center justify-between">
              <div>
                <h2 className="font-semibold text-ink">{t('dashboard.validatorQueue')}</h2>
                <p className="text-xs text-ink-faint mt-0.5">{t('dashboard.clickToValidateOrReject')}</p>
              </div>
              <Link to="/approvals" className="flex items-center gap-1 text-sm text-primary font-medium hover:underline">
                {t('app.view')} <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          }
        >
          <div className="divide-y divide-hairline -m-5">
            {queue.slice(0, 6).map((inv) => (
              <Link key={inv.id} to={`/invoices/${inv.id}`}
                className="flex items-center gap-4 px-5 py-3.5 hover:bg-ground transition-colors">
                <span className="num text-xs font-semibold text-primary w-32 shrink-0">{inv.referenceNumber}</span>
                <span className="text-sm text-ink-soft truncate flex-1">{inv.supplierName}</span>
                <StatusBadge status={inv.status as InvoiceStatus} />
                <span className="num text-sm text-ink-soft text-right w-28 shrink-0">
                  {formatAmount(inv.amount)} <span className="text-ink-faint">XAF</span>
                </span>
                <ChevronRight className="w-4 h-4 text-ink-faint shrink-0" />
              </Link>
            ))}
            {queue.length === 0 && (
              <div className="py-10 text-sm text-center text-ink-faint">
                <CheckCircle className="w-8 h-8 mx-auto mb-2 text-pos" />
                {t('approvals.empty')}
              </div>
            )}
          </div>
        </Panel>
      </div>
    )
  }

  // ── AA / DAF ───────────────────────────────────────────────────────────────
  if (canViewKpis && kpiLoading) {
    return <SkeletonDashboard />
  }

  const pieData = kpi ? Object.entries(kpi.countByStatus).map(([name, value]) => ({ name, value })) : []
  const supplierData = kpi
    ? Object.entries(kpi.volumeBySupplier).sort(([, a], [, b]) => b - a).slice(0, 5)
        .map(([name, value]) => ({ name: name.length > 15 ? name.slice(0, 15) + '…' : name, value }))
    : []

  const aaKpis: KpiBandItem[] = [
    { label: t('dashboard.totalInvoices'), value: kpi?.totalInvoices ?? '—' },
    { label: t('dashboard.overdueInvoices'), value: kpi?.overdueCount ?? '—', tone: 'crit' },
    { label: t('dashboard.avgProcessingTime'), value: kpi ? `${kpi.averageProcessingTimeDays.toFixed(1)} j.` : '—', tone: 'warn' },
    { label: t('dashboard.rejectionRate'), value: kpi ? `${(kpi.rejectionRate * 100).toFixed(1)} %` : '—', tone: 'hot' },
  ]

  return (
    <div className="space-y-6 page-enter">
      <PageHeader
        title={t('dashboard.title')}
        subtitle={t('dashboard.invoiceProcessingOverview')}
        actions={isAA && (
          <Link to="/invoices/new"
            className="flex items-center gap-2 px-4 py-2 bg-oct-navy text-white text-sm font-medium rounded-[4px] hover:bg-oct-navy-light transition-colors">
            <Plus className="w-4 h-4" />
            {t('breadcrumb.newInvoice')}
          </Link>
        )}
      />

      {/* M2: system announcements (everyone) + budget alerts (DAF/AA) ; M14: privacy acceptance */}
      <PrivacyPolicyBanner />
      <DashboardAnnouncements />
      {(isDaf || isAA) && <BudgetAlerts />}

      {(isDaf || isAA) && <AgingBucketsWidget />}

      {/* KPI Cards */}
      {canViewKpis && (
        <KpiBand items={aaKpis} className="grid grid-cols-2 lg:grid-cols-4" />
      )}

      {/* Quick Actions for AA */}
      {isAA && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <QuickLink to="/invoices/new" icon={Plus} label={t('breadcrumb.newInvoice')} sub={t('dashboard.enterSupplierInvoice')} color="bg-oct-navy text-white border-transparent hover:bg-oct-navy-light" />
          <QuickLink to="/admin/suppliers" icon={Users} label={t('nav.suppliers')} sub={t('dashboard.manageRegistry')} color="bg-surface text-ink border-hairline hover:bg-ground" />
          <QuickLink to="/purchase-orders" icon={FileText} label={t('nav.purchaseOrders', 'Bons de commande')} sub={t('dashboard.viewPurchaseOrders')} color="bg-surface text-ink border-hairline hover:bg-ground" />
        </div>
      )}

      {/* Processing queue */}
      {(isAA || isDaf) && (
        <Panel
          className="overflow-hidden"
          title={
            <div className="flex items-center justify-between">
              <div>
                <h2 className="font-semibold text-ink">{t('dashboard.processingQueue')}</h2>
                <p className="text-xs text-ink-faint mt-0.5">{t('dashboard.processingQueueSub')}</p>
              </div>
              <Link to="/invoices" className="flex items-center gap-1 text-sm text-primary font-medium hover:underline">
                {t('app.view')} <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          }
        >
          <div className="divide-y divide-hairline -m-5">
            {(pendingQueue?.content ?? []).map((invoice) => (
              <Link key={invoice.id} to={`/invoices/${invoice.id}`}
                className="flex items-center gap-4 px-5 py-3.5 hover:bg-ground transition-colors">
                <span className="num text-xs font-semibold text-primary w-32 shrink-0">{invoice.referenceNumber}</span>
                <span className="text-sm text-ink-soft truncate flex-1">{invoice.supplierName}</span>
                <StatusBadge status={invoice.status as InvoiceStatus} />
                <span className="num text-sm text-ink-soft text-right w-28 shrink-0">
                  {formatAmount(invoice.amount)} <span className="text-ink-faint">XAF</span>
                </span>
                <ChevronRight className="w-4 h-4 text-ink-faint shrink-0" />
              </Link>
            ))}
            {(!pendingQueue?.content || pendingQueue.content.length === 0) && (
              <div className="py-10 text-sm text-center text-ink-faint">
                <CheckCircle className="w-8 h-8 mx-auto mb-2 text-pos" />
                {t('dashboard.noPendingItems')}
              </div>
            )}
          </div>
        </Panel>
      )}

      {/* Charts */}
      {canViewKpis && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <Panel className="p-5">
            <h2 className="font-semibold text-ink mb-1">{t('dashboard.invoicesByStatus')}</h2>
            <p className="text-xs text-ink-faint mb-4">{t('dashboard.statusDistribution')}</p>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie data={pieData} cx="50%" cy="50%" innerRadius={65} outerRadius={90} paddingAngle={2} dataKey="value">
                    {pieData.map((entry) => <Cell key={entry.name} fill={STATUS_COLORS[entry.name] ?? '#cbd5e1'} />)}
                  </Pie>
                  <Tooltip
                    contentStyle={{ borderRadius: 8, border: '1px solid #e5e7eb', fontSize: 12 }}
                    formatter={(v, n) => [v, t(`status.${n}`)]}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-[220px] text-ink-faint text-sm">{t('app.noData')}</div>
            )}
          </Panel>

          <Panel className="p-5">
            <h2 className="font-semibold text-ink mb-1">{t('dashboard.topSuppliers')}</h2>
            <p className="text-xs text-ink-faint mb-4">{t('dashboard.topSuppliersSubtitle')}</p>
            {supplierData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={supplierData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#f3f4f6" />
                  <XAxis type="number" tick={{ fontSize: 11, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="name" width={90} tick={{ fontSize: 10, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, border: '1px solid #e5e7eb', fontSize: 12 }}
                    formatter={(v) => [`${formatAmount(v)} XAF`]}
                  />
                  <Bar dataKey="value" fill="#0F2540" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-[220px] text-ink-faint text-sm">{t('app.noData')}</div>
            )}
          </Panel>
        </div>
      )}

      {/* DAF shortcuts */}
      {isDaf && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <QuickLink to="/payments" icon={DollarSign} label={t('nav.payments')} sub={t('dashboard.paymentsPendingSub')} color="bg-surface text-ink border-hairline hover:bg-ground" />
          <QuickLink to="/reports" icon={BarChart3} label={t('nav.reports')} sub={t('dashboard.reportsStatsSub')} color="bg-surface text-ink border-hairline hover:bg-ground" />
          <QuickLink to="/financial-audit" icon={ScrollText} label={t('nav.financialAudit')} sub={t('dashboard.operationsLogSub')} color="bg-surface text-ink border-hairline hover:bg-ground" />
        </div>
      )}
    </div>
  )
}
