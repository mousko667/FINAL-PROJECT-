import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Megaphone, AlertTriangle, Info, AlertOctagon } from 'lucide-react'

interface Announcement {
  id: string
  title: string
  body: string
  severity: 'INFO' | 'WARNING' | 'CRITICAL'
}

interface BudgetAlert {
  departmentCode: string
  nameFr: string
  nameEn: string
  budget: number | null
  actual: number
  utilizationPercent: number | null
}

const SEVERITY_STYLE: Record<string, { cls: string; icon: React.ReactNode }> = {
  INFO: { cls: 'bg-blue-50 border-blue-200 text-blue-800', icon: <Info className="w-4 h-4" /> },
  WARNING: { cls: 'bg-amber-50 border-amber-200 text-amber-800', icon: <AlertTriangle className="w-4 h-4" /> },
  CRITICAL: { cls: 'bg-red-50 border-red-200 text-red-800', icon: <AlertOctagon className="w-4 h-4" /> },
}

/** Active system announcements — shown to every authenticated user (M2). */
export function DashboardAnnouncements() {
  const { t } = useTranslation()
  const { data: announcements = [] } = useQuery<Announcement[]>({
    queryKey: ['announcements', 'active'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Announcement[] }>('/announcements')
      return data.data ?? []
    },
  })

  if (announcements.length === 0) return null

  return (
    <div className="space-y-2">
      {announcements.map(a => {
        const s = SEVERITY_STYLE[a.severity] ?? SEVERITY_STYLE.INFO
        return (
          <div key={a.id} className={`flex items-start gap-2 border rounded-lg px-4 py-3 ${s.cls}`}>
            <span className="mt-0.5 shrink-0">{s.icon}</span>
            <div>
              <p className="text-sm font-semibold flex items-center gap-1.5">
                <Megaphone className="w-3.5 h-3.5" /> {a.title}
              </p>
              <p className="text-sm mt-0.5">{a.body}</p>
            </div>
          </div>
        )
      })}
    </div>
  )
}

/** Budget alerts — departments at/above 80% utilisation. DAF/Assistant only (M2). */
export function BudgetAlerts() {
  const { t, i18n } = useTranslation()
  const { data: alerts = [] } = useQuery<BudgetAlert[]>({
    queryKey: ['budget-alerts'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: BudgetAlert[] }>('/reports/budget-alerts', { params: { threshold: 80 } })
      return data.data ?? []
    },
    retry: false,
  })

  if (alerts.length === 0) return null

  return (
    <div className="bg-white rounded-xl border p-5">
      <div className="flex items-center gap-2 mb-3">
        <AlertTriangle className="w-5 h-5 text-amber-600" />
        <h2 className="font-semibold text-gray-800">{t('dashboard.budgetAlerts.title')}</h2>
      </div>
      <ul className="space-y-2">
        {alerts.map(a => {
          const over = (a.utilizationPercent ?? 0) >= 100
          return (
            <li key={a.departmentCode} className="flex items-center justify-between text-sm">
              <span className="font-medium text-gray-700">
                {a.departmentCode} — {i18n.language === 'en' ? a.nameEn : a.nameFr}
              </span>
              <span className={over ? 'text-red-600 font-semibold' : 'text-amber-600 font-medium'}>
                {(a.utilizationPercent ?? 0).toFixed(0)}%
                {over ? ` · ${t('dashboard.budgetAlerts.overBudget')}` : ''}
              </span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
