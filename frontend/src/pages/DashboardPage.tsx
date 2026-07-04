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
  TrendingUp, AlertTriangle, Clock, XCircle,
  Users, Building2, ScrollText, FileText, CheckCircle,
  DollarSign, ArrowRight, Plus, GitBranch, Shield, Zap,
  Package, BarChart3, ChevronRight,
} from 'lucide-react'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { SkeletonCard, SkeletonDashboard, Skeleton } from '@/components/ui/Skeleton'
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

interface KpiCardProps {
  title: string
  value: string | number
  icon: React.ReactNode
  iconBg: string
  trend?: string
  trendUp?: boolean
}

function KpiCard({ title, value, icon, iconBg, trend, trendUp }: KpiCardProps) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 p-5 flex items-start gap-4 hover:shadow-sm transition-shadow">
      <div className={`p-3 rounded-xl shrink-0 ${iconBg}`}>{icon}</div>
      <div className="min-w-0 flex-1">
        <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{title}</p>
        <p className="text-2xl font-bold text-gray-900 mt-1 leading-none">{value}</p>
        {trend && (
          <p className={`text-xs mt-1.5 ${trendUp ? 'text-green-600' : 'text-red-500'}`}>
            {trendUp ? '↑' : '↓'} {trend}
          </p>
        )}
      </div>
    </div>
  )
}

function QuickLink({ to, icon: Icon, label, sub, color }: { to: string; icon: React.ElementType; label: string; sub?: string; color: string }) {
  return (
    <Link
      to={to}
      className={`flex items-center gap-3 p-4 rounded-xl border transition-all hover:shadow-sm hover:-translate-y-0.5 ${color}`}
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
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('dashboard.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('dashboard.adminNote')}</p>
        </div>

        {/* Primary admin actions */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <Link to="/admin/users" className="group flex flex-col items-center p-5 rounded-xl bg-blue-50 hover:bg-blue-100 border border-blue-100 transition-all hover:shadow-sm text-center">
            <div className="w-12 h-12 rounded-xl bg-blue-100 group-hover:bg-blue-200 flex items-center justify-center mb-3 transition-colors">
              <Users className="w-6 h-6 text-blue-600" />
            </div>
            <span className="text-sm font-semibold text-blue-800">{t('nav.users')}</span>
            <span className="text-xs text-blue-500 mt-0.5">{t('dashboard.manageAccounts', 'Gérer les comptes')}</span>
          </Link>

          <Link to="/admin/departments" className="group flex flex-col items-center p-5 rounded-xl bg-indigo-50 hover:bg-indigo-100 border border-indigo-100 transition-all hover:shadow-sm text-center">
            <div className="w-12 h-12 rounded-xl bg-indigo-100 group-hover:bg-indigo-200 flex items-center justify-center mb-3 transition-colors">
              <Building2 className="w-6 h-6 text-indigo-600" />
            </div>
            <span className="text-sm font-semibold text-indigo-800">{t('nav.departments')}</span>
            <span className="text-xs text-indigo-500 mt-0.5">{t('dashboard.deptMatrix', 'Départements & matrice')}</span>
          </Link>

          <Link to="/admin/audit" className="group flex flex-col items-center p-5 rounded-xl bg-amber-50 hover:bg-amber-100 border border-amber-100 transition-all hover:shadow-sm text-center">
            <div className="w-12 h-12 rounded-xl bg-amber-100 group-hover:bg-amber-200 flex items-center justify-center mb-3 transition-colors">
              <ScrollText className="w-6 h-6 text-amber-600" />
            </div>
            <span className="text-sm font-semibold text-amber-800">{t('nav.auditLog')}</span>
            <span className="text-xs text-amber-500 mt-0.5">{t('dashboard.securityLogs', 'Journaux système')}</span>
          </Link>

          <Link to="/admin/suppliers" className="group flex flex-col items-center p-5 rounded-xl bg-teal-50 hover:bg-teal-100 border border-teal-100 transition-all hover:shadow-sm text-center">
            <div className="w-12 h-12 rounded-xl bg-teal-100 group-hover:bg-teal-200 flex items-center justify-center mb-3 transition-colors">
              <Users className="w-6 h-6 text-teal-600" />
            </div>
            <span className="text-sm font-semibold text-teal-800">{t('nav.suppliers')}</span>
            <span className="text-xs text-teal-500 mt-0.5">{t('dashboard.supplierRegistry', 'Registre fournisseurs')}</span>
          </Link>
        </div>

        {/* Secondary admin actions */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Link to="/admin/approval-matrix" className="flex items-center gap-3 p-4 rounded-xl bg-white border hover:bg-purple-50 hover:border-purple-200 transition-all group">
            <div className="w-9 h-9 rounded-lg bg-purple-100 flex items-center justify-center group-hover:bg-purple-200 transition-colors">
              <GitBranch className="w-4 h-4 text-purple-600" />
            </div>
            <div>
              <p className="text-sm font-semibold text-gray-800">{t('admin.approvalMatrix.title', 'Matrice d\'approbation')}</p>
              <p className="text-xs text-gray-400">Workflows de validation</p>
            </div>
          </Link>
          <Link to="/admin/security" className="flex items-center gap-3 p-4 rounded-xl bg-white border hover:bg-red-50 hover:border-red-200 transition-all group">
            <div className="w-9 h-9 rounded-lg bg-red-100 flex items-center justify-center group-hover:bg-red-200 transition-colors">
              <Shield className="w-4 h-4 text-red-600" />
            </div>
            <div>
              <p className="text-sm font-semibold text-gray-800">{t('admin.security.title', 'Paramètres de sécurité')}</p>
              <p className="text-xs text-gray-400">MFA, sessions, accès</p>
            </div>
          </Link>
          <Link to="/admin/integrations" className="flex items-center gap-3 p-4 rounded-xl bg-white border hover:bg-green-50 hover:border-green-200 transition-all group">
            <div className="w-9 h-9 rounded-lg bg-green-100 flex items-center justify-center group-hover:bg-green-200 transition-colors">
              <Zap className="w-4 h-4 text-green-600" />
            </div>
            <div>
              <p className="text-sm font-semibold text-gray-800">{t('admin.integrations.title', 'Intégrations')}</p>
              <p className="text-xs text-gray-400">Webhooks & API keys</p>
            </div>
          </Link>
        </div>
      </div>
    )
  }

  // ── SUPPLIER ───────────────────────────────────────────────────────────────
  if (isSupplier) {
    const sd = supplierDash as Record<string, number> | undefined
    const kpiItems = [
      { icon: FileText,    label: t('supplier.portal.submitted'), value: sd?.submittedCount ?? 0,  iconBg: 'bg-blue-100',   iconColor: 'text-blue-600' },
      { icon: Clock,       label: t('supplier.portal.pending'),   value: sd?.pendingCount   ?? 0,  iconBg: 'bg-amber-100',  iconColor: 'text-amber-600' },
      { icon: CheckCircle, label: t('supplier.portal.approved'),  value: sd?.approvedCount  ?? 0,  iconBg: 'bg-teal-100',   iconColor: 'text-teal-600' },
      { icon: DollarSign,  label: t('supplier.portal.paid'),      value: sd?.paidCount      ?? 0,  iconBg: 'bg-green-100',  iconColor: 'text-green-600' },
      { icon: XCircle,     label: t('supplier.portal.rejected'),  value: sd?.rejectedCount  ?? 0,  iconBg: 'bg-red-100',    iconColor: 'text-red-600' },
    ]
    return (
      <div className="space-y-6 page-enter">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('dashboard.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">Suivi de vos factures en temps réel</p>
        </div>
        <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
          {kpiItems.map(({ icon: Icon, label, value, iconBg, iconColor }) => (
            <div key={label} className="bg-white rounded-xl border p-4 flex flex-col items-center text-center hover:shadow-sm transition-shadow">
              <div className={`p-3 rounded-full mb-3 ${iconBg}`}><Icon className={`w-5 h-5 ${iconColor}`} /></div>
              <p className="text-2xl font-bold text-gray-900">{value}</p>
              <p className="text-xs text-gray-500 mt-1 leading-tight">{label}</p>
            </div>
          ))}
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Link to="/supplier/invoices/new" className="flex items-center gap-4 p-5 bg-oct-navy text-white rounded-xl hover:bg-oct-navy-light transition-colors shadow-sm">
            <div className="w-10 h-10 rounded-lg bg-oct-gold flex items-center justify-center shrink-0">
              <Plus className="w-5 h-5 text-oct-navy" />
            </div>
            <div>
              <p className="font-semibold">{t('supplier.portal.submitInvoice')}</p>
              <p className="text-xs opacity-70 mt-0.5">PDF, JPEG ou PNG</p>
            </div>
            <ArrowRight className="w-5 h-5 ml-auto opacity-60" />
          </Link>
          <Link to="/supplier/invoices" className="flex items-center gap-4 p-5 bg-white border rounded-xl hover:bg-gray-50 transition-colors">
            <div className="w-10 h-10 rounded-lg bg-gray-100 flex items-center justify-center shrink-0">
              <FileText className="w-5 h-5 text-gray-600" />
            </div>
            <div>
              <p className="font-semibold text-gray-900">{t('supplier.portal.viewInvoices')}</p>
              <p className="text-xs text-gray-500 mt-0.5">Suivi du statut</p>
            </div>
            <ArrowRight className="w-5 h-5 ml-auto text-gray-300" />
          </Link>
        </div>
      </div>
    )
  }

  // ── VALIDATOR (N1/N2) ──────────────────────────────────────────────────────
  if (isValidator) {
    const queue = pendingQueue?.content ?? []
    return (
      <div className="space-y-6 page-enter">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('dashboard.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('dashboard.validatorQueueSub')}</p>
        </div>

        <div className="grid grid-cols-3 gap-4">
          <div className="bg-white rounded-xl border p-5 text-center">
            <p className="text-3xl font-bold text-amber-600">{queue.length}</p>
            <p className="text-xs text-gray-500 mt-1">En attente de ma validation</p>
          </div>
          <div className="bg-white rounded-xl border p-5 text-center">
            <p className="text-3xl font-bold text-gray-900">{validatorStatsLoading ? '…' : (validatorStats?.processedThisMonth ?? '—')}</p>
            <p className="text-xs text-gray-500 mt-1">{t('dashboard.processedThisMonth', 'Traitées ce mois')}</p>
          </div>
          <div className="bg-white rounded-xl border p-5 text-center">
            <p className="text-3xl font-bold text-green-600">{validatorStatsLoading ? '…' : (validatorStats?.approvedTotal ?? '—')}</p>
            <p className="text-xs text-gray-500 mt-1">{t('dashboard.approved', 'Approuvées')}</p>
          </div>
        </div>

        <div className="bg-white rounded-xl border overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b bg-gray-50">
            <div>
              <h2 className="font-semibold text-gray-900">{t('dashboard.validatorQueue')}</h2>
              <p className="text-xs text-gray-500 mt-0.5">Cliquer pour valider ou rejeter</p>
            </div>
            <Link to="/approvals" className="flex items-center gap-1 text-sm text-primary font-medium hover:underline">
              {t('app.view')} <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="divide-y">
            {queue.slice(0, 6).map((inv) => (
              <Link key={inv.id} to={`/invoices/${inv.id}`}
                className="flex items-center gap-4 px-5 py-3.5 hover:bg-gray-50 transition-colors">
                <span className="font-mono text-xs font-semibold text-primary w-32 shrink-0">{inv.referenceNumber}</span>
                <span className="text-sm text-gray-700 truncate flex-1">{inv.supplierName}</span>
                <StatusBadge status={inv.status as InvoiceStatus} />
                <span className="text-sm font-mono text-gray-700 text-right w-28 shrink-0">
                  {formatAmount(inv.amount)} XOF
                </span>
                <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
              </Link>
            ))}
            {queue.length === 0 && (
              <div className="py-10 text-sm text-center text-gray-400">
                <CheckCircle className="w-8 h-8 mx-auto mb-2 text-green-300" />
                {t('approvals.empty')}
              </div>
            )}
          </div>
        </div>
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

  return (
    <div className="space-y-6 page-enter">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('dashboard.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">Vue d'ensemble du traitement des factures</p>
        </div>
        {isAA && (
          <Link to="/invoices/new"
            className="flex items-center gap-2 px-4 py-2 bg-oct-navy text-white text-sm font-medium rounded-lg hover:bg-oct-navy-light transition-colors">
            <Plus className="w-4 h-4" />
            Nouvelle facture
          </Link>
        )}
      </div>

      {/* M2: system announcements (everyone) + budget alerts (DAF/AA) ; M14: privacy acceptance */}
      <PrivacyPolicyBanner />
      <DashboardAnnouncements />
      {(isDaf || isAA) && <BudgetAlerts />}

      {(isDaf || isAA) && <AgingBucketsWidget />}

      {/* KPI Cards */}
      {canViewKpis && (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard
            title={t('dashboard.totalInvoices')}
            value={kpi?.totalInvoices ?? '—'}
            icon={<TrendingUp className="w-5 h-5 text-blue-600" />}
            iconBg="bg-blue-50"
          />
          <KpiCard
            title={t('dashboard.overdueInvoices')}
            value={kpi?.overdueCount ?? '—'}
            icon={<AlertTriangle className="w-5 h-5 text-red-500" />}
            iconBg="bg-red-50"
          />
          <KpiCard
            title={t('dashboard.avgProcessingTime')}
            value={kpi ? `${kpi.averageProcessingTimeDays.toFixed(1)} j.` : '—'}
            icon={<Clock className="w-5 h-5 text-amber-500" />}
            iconBg="bg-amber-50"
          />
          <KpiCard
            title={t('dashboard.rejectionRate')}
            value={kpi ? `${(kpi.rejectionRate * 100).toFixed(1)} %` : '—'}
            icon={<XCircle className="w-5 h-5 text-orange-500" />}
            iconBg="bg-orange-50"
          />
        </div>
      )}

      {/* Quick Actions for AA */}
      {isAA && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <QuickLink to="/invoices/new" icon={Plus} label="Nouvelle facture" sub="Saisir une facture fournisseur" color="bg-oct-navy text-white border-transparent hover:bg-oct-navy-light" />
          <QuickLink to="/admin/suppliers" icon={Users} label={t('nav.suppliers')} sub="Gérer le registre" color="bg-white text-gray-800 border-gray-200 hover:bg-gray-50" />
          <QuickLink to="/purchase-orders" icon={FileText} label={t('nav.purchaseOrders', 'Bons de commande')} sub="Consulter les BDC" color="bg-white text-gray-800 border-gray-200 hover:bg-gray-50" />
        </div>
      )}

      {/* Processing queue */}
      {(isAA || isDaf) && (
        <div className="bg-white rounded-xl border overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b bg-gray-50">
            <div>
              <h2 className="font-semibold text-gray-900">{t('dashboard.processingQueue')}</h2>
              <p className="text-xs text-gray-500 mt-0.5">{t('dashboard.processingQueueSub')}</p>
            </div>
            <Link to="/invoices" className="flex items-center gap-1 text-sm text-primary font-medium hover:underline">
              {t('app.view')} <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="divide-y">
            {(pendingQueue?.content ?? []).map((invoice) => (
              <Link key={invoice.id} to={`/invoices/${invoice.id}`}
                className="flex items-center gap-4 px-5 py-3.5 hover:bg-gray-50 transition-colors">
                <span className="font-mono text-xs font-semibold text-primary w-32 shrink-0">{invoice.referenceNumber}</span>
                <span className="text-sm text-gray-700 truncate flex-1">{invoice.supplierName}</span>
                <StatusBadge status={invoice.status as InvoiceStatus} />
                <span className="text-sm font-mono text-gray-700 text-right w-28 shrink-0">
                  {formatAmount(invoice.amount)} XOF
                </span>
                <ChevronRight className="w-4 h-4 text-gray-300 shrink-0" />
              </Link>
            ))}
            {(!pendingQueue?.content || pendingQueue.content.length === 0) && (
              <div className="py-10 text-sm text-center text-gray-400">
                <CheckCircle className="w-8 h-8 mx-auto mb-2 text-green-300" />
                {t('dashboard.noPendingItems')}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Charts */}
      {canViewKpis && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="bg-white rounded-xl border p-5">
            <h2 className="font-semibold text-gray-800 mb-1">{t('dashboard.invoicesByStatus')}</h2>
            <p className="text-xs text-gray-400 mb-4">Distribution des statuts actuels</p>
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
              <div className="flex items-center justify-center h-[220px] text-gray-400 text-sm">{t('app.noData')}</div>
            )}
          </div>

          <div className="bg-white rounded-xl border p-5">
            <h2 className="font-semibold text-gray-800 mb-1">{t('dashboard.topSuppliers')}</h2>
            <p className="text-xs text-gray-400 mb-4">Volume traité par fournisseur (XOF)</p>
            {supplierData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={supplierData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="#f3f4f6" />
                  <XAxis type="number" tick={{ fontSize: 11, fill: '#9ca3af' }} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="name" width={90} tick={{ fontSize: 10, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ borderRadius: 8, border: '1px solid #e5e7eb', fontSize: 12 }}
                    formatter={(v) => [`${formatAmount(v)} XOF`]}
                  />
                  <Bar dataKey="value" fill="#0F2540" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-[220px] text-gray-400 text-sm">{t('app.noData')}</div>
            )}
          </div>
        </div>
      )}

      {/* DAF shortcuts */}
      {isDaf && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <QuickLink to="/payments" icon={DollarSign} label="Paiements" sub="Factures en attente de paiement" color="bg-white text-gray-800 border-gray-200 hover:bg-gray-50" />
          <QuickLink to="/reports" icon={BarChart3} label={t('nav.reports')} sub="Rapports & statistiques" color="bg-white text-gray-800 border-gray-200 hover:bg-gray-50" />
          <QuickLink to="/financial-audit" icon={ScrollText} label="Audit financier" sub="Journal des opérations" color="bg-white text-gray-800 border-gray-200 hover:bg-gray-50" />
        </div>
      )}
    </div>
  )
}
