import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { reportService } from '@/services/reportService'
import {
  PieChart, Pie, Cell, Tooltip, ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, LineChart, Line,
} from 'recharts'
import { Loader2, TrendingUp, AlertTriangle, Clock, XCircle } from 'lucide-react'
import { RoleGuard } from '@/components/auth/RoleGuard'

const STATUS_COLORS: Record<string, string> = {
  BROUILLON: '#94a3b8',
  SOUMIS: '#60a5fa',
  EN_VALIDATION_N1: '#fbbf24',
  EN_VALIDATION_N2: '#f97316',
  VALIDE: '#2dd4bf',
  BON_A_PAYER: '#34d399',
  PAYE: '#10b981',
  ARCHIVE: '#64748b',
  REJETE: '#f87171',
}

interface KpiCardProps {
  title: string
  value: string | number
  icon: React.ReactNode
  color: string
}

function KpiCard({ title, value, icon, color }: KpiCardProps) {
  return (
    <div className="bg-white rounded-xl border p-5 flex items-start gap-4">
      <div className={`p-3 rounded-xl ${color}`}>{icon}</div>
      <div>
        <p className="text-sm text-muted-foreground">{title}</p>
        <p className="text-2xl font-bold text-gray-900 mt-0.5">{value}</p>
      </div>
    </div>
  )
}

export default function DashboardPage() {
  const { t } = useTranslation()

  const { data: kpi, isLoading } = useQuery({
    queryKey: ['kpis'],
    queryFn: reportService.getKpis,
    retry: false,
  })

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
      </div>
    )
  }

  const pieData = kpi
    ? Object.entries(kpi.countByStatus).map(([name, value]) => ({ name, value }))
    : []

  const supplierData = kpi
    ? Object.entries(kpi.volumeBySupplier)
        .sort(([, a], [, b]) => b - a)
        .slice(0, 5)
        .map(([name, value]) => ({ name: name.length > 15 ? name.slice(0, 15) + '…' : name, value }))
    : []

  return (
    <RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']} fallback={
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('dashboard.title')}</h1>
        <p className="text-muted-foreground">{t('app.noData')}</p>
      </div>
    }>
      <div className="space-y-6">
        <h1 className="text-2xl font-bold text-gray-900">{t('dashboard.title')}</h1>

        {/* KPI Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <KpiCard
            title={t('dashboard.totalInvoices')}
            value={kpi?.totalInvoices ?? '—'}
            icon={<TrendingUp className="w-5 h-5 text-blue-600" />}
            color="bg-blue-50"
          />
          <KpiCard
            title={t('dashboard.overdueInvoices')}
            value={kpi?.overdueCount ?? '—'}
            icon={<AlertTriangle className="w-5 h-5 text-red-600" />}
            color="bg-red-50"
          />
          <KpiCard
            title={t('dashboard.avgProcessingTime')}
            value={kpi ? `${kpi.averageProcessingTimeDays.toFixed(1)} ${t('dashboard.days')}` : '—'}
            icon={<Clock className="w-5 h-5 text-amber-600" />}
            color="bg-amber-50"
          />
          <KpiCard
            title={t('dashboard.rejectionRate')}
            value={kpi ? `${(kpi.rejectionRate * 100).toFixed(1)}%` : '—'}
            icon={<XCircle className="w-5 h-5 text-orange-600" />}
            color="bg-orange-50"
          />
        </div>

        {/* Charts */}
        <div className="grid grid-cols-2 gap-6">
          {/* Status distribution donut */}
          <div className="bg-white rounded-xl border p-5">
            <h2 className="font-semibold text-gray-800 mb-4">{t('dashboard.invoicesByStatus')}</h2>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={60}
                    outerRadius={90}
                    paddingAngle={2}
                    dataKey="value"
                  >
                    {pieData.map((entry) => (
                      <Cell key={entry.name} fill={STATUS_COLORS[entry.name] ?? '#cbd5e1'} />
                    ))}
                  </Pie>
                  <Tooltip formatter={(v, n) => [v, t(`status.${n}`)]} />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-[220px] text-muted-foreground text-sm">{t('app.noData')}</div>
            )}
          </div>

          {/* Top suppliers bar */}
          <div className="bg-white rounded-xl border p-5">
            <h2 className="font-semibold text-gray-800 mb-4">{t('dashboard.topSuppliers')}</h2>
            {supplierData.length > 0 ? (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={supplierData} layout="vertical">
                  <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 11 }} />
                  <YAxis type="category" dataKey="name" width={80} tick={{ fontSize: 10 }} />
                  <Tooltip />
                  <Bar dataKey="value" fill="#6366f1" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-[220px] text-muted-foreground text-sm">{t('app.noData')}</div>
            )}
          </div>
        </div>
      </div>
    </RoleGuard>
  )
}
