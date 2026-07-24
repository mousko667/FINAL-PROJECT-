import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Megaphone, AlertTriangle, Info, AlertOctagon, ShieldCheck } from 'lucide-react'
import { notifyApiError } from '@/components/ErrorToaster'

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
  INFO: { cls: 'bg-info-bg border-info/30 text-info', icon: <Info className="w-4 h-4" /> },
  WARNING: { cls: 'bg-warn-bg border-warn/30 text-warn', icon: <AlertTriangle className="w-4 h-4" /> },
  CRITICAL: { cls: 'bg-crit-bg border-crit/30 text-crit', icon: <AlertOctagon className="w-4 h-4" /> },
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
          <div key={a.id} className={`flex items-start gap-2 border rounded-[4px] px-4 py-3 ${s.cls}`}>
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

/** Privacy-policy acceptance prompt (M14) — shown to any user who hasn't accepted the current version. */
export function PrivacyPolicyBanner() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data } = useQuery<{ accepted: boolean; policyVersion: string }>({
    queryKey: ['privacy-acceptance'],
    queryFn: async () => (await apiClient.get('/compliance/privacy-acceptance')).data.data,
    retry: false,
  })
  const accept = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: () => apiClient.post('/compliance/privacy-acceptance'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['privacy-acceptance'] }),
  })

  if (!data || data.accepted) return null

  return (
    <div className="flex items-center justify-between gap-3 border border-info/30 bg-info-bg text-info rounded-[4px] px-4 py-3">
      <span className="flex items-center gap-2 text-sm">
        <ShieldCheck className="w-4 h-4 shrink-0" />
        {t('privacy.prompt', 'Veuillez accepter la politique de confidentialité (v{{version}}).', { version: data.policyVersion })}
      </span>
      <button onClick={() => accept.mutate()} disabled={accept.isPending}
        className="text-sm bg-primary text-primary-foreground px-3 py-1.5 rounded-[4px] hover:bg-primary/90 disabled:opacity-50 shrink-0">
        {t('privacy.accept', 'Accepter')}
      </button>
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
    <div className="bg-surface rounded-[4px] border border-hairline p-5">
      <div className="flex items-center gap-2 mb-3">
        <AlertTriangle className="w-5 h-5 text-warn" />
        <h2 className="font-semibold text-ink">{t('dashboard.budgetAlerts.title')}</h2>
      </div>
      <ul className="space-y-2">
        {alerts.map(a => {
          const over = (a.utilizationPercent ?? 0) >= 100
          return (
            <li key={a.departmentCode} className="flex items-center justify-between text-sm">
              <span className="font-medium text-ink-soft">
                {a.departmentCode} — {i18n.language === 'en' ? a.nameEn : a.nameFr}
              </span>
              <span className={over ? 'text-crit font-semibold' : 'text-warn font-medium'}>
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
