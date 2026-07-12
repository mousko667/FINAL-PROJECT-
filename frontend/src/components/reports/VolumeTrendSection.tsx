import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2 } from 'lucide-react'
import {
  BarChart, Bar, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { formatAmount } from '@/lib/format'
import { getSeriesColor, chartGridProps, chartAxisProps } from '@/lib/chart-theme'
import { ChartTooltip } from '@/components/ui/ChartTooltip'

interface TrendPoint {
  monthLabel: string
  year: number
  month: number
  invoiceCount: number
  totalAmount: number
}
interface VolumeTrend {
  fromDate: string
  toDate: string
  points: TrendPoint[]
}

export default function VolumeTrendSection() {
  const { t } = useTranslation()
  const dark = typeof document !== 'undefined'
    && document.documentElement.classList.contains('dark')

  const { data: trend, isLoading } = useQuery({
    queryKey: ['volume-trend'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: VolumeTrend }>('/reports/volume-trend')
      return data.data
    },
    retry: false,
  })

  const isEmpty = !trend?.points?.length
    || trend.points.every(p => p.invoiceCount === 0 && p.totalAmount === 0)

  if (isLoading) {
    return (
      <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
        <div className="px-5 py-4">
          <div className="flex justify-center py-4">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        </div>
      </div>
    )
  }

  const amountColor = getSeriesColor(0, dark)
  const countColor = getSeriesColor(1, dark)

  return (
    <div className="bg-surface rounded-[4px] border border-hairline overflow-hidden">
      <div className="px-5 py-4 font-semibold text-ink">{t('reports.trends.title', 'Tendances volume / valeur')}</div>
      <div className="border-t px-5 py-4">
        <p className="text-sm text-ink-soft mb-4">{t('reports.trends.desc', 'Nombre de factures et montant total par mois sur les 12 derniers mois (par date de facture).')}</p>
        {isEmpty ? (
          <p data-testid="volume-trend-empty" className="text-sm text-center text-ink-faint py-4">{t('reports.noData', 'Aucune donnée')}</p>
        ) : (
          <div className="space-y-6">
            <div data-testid="volume-trend-amount">
              <div className="text-xs font-medium text-ink-soft mb-2">{t('reports.trends.amount', 'Montant (XAF)')}</div>
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={trend!.points}>
                  <CartesianGrid {...chartGridProps} />
                  <XAxis dataKey="monthLabel" {...chartAxisProps} />
                  <YAxis {...chartAxisProps} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
                  <Tooltip
                    cursor={{ fill: 'hsl(var(--hairline) / 0.4)' }}
                    content={<ChartTooltip valueFormatter={v => `${formatAmount(v)} XAF`} />}
                  />
                  <Bar dataKey="totalAmount" name={t('reports.trends.amount', 'Montant (XAF)')} fill={amountColor} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
            <div data-testid="volume-trend-count">
              <div className="text-xs font-medium text-ink-soft mb-2">{t('reports.trends.count', 'Nb de factures')}</div>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={trend!.points}>
                  <CartesianGrid {...chartGridProps} />
                  <XAxis dataKey="monthLabel" {...chartAxisProps} />
                  <YAxis {...chartAxisProps} allowDecimals={false} />
                  <Tooltip
                    cursor={{ stroke: 'hsl(var(--hairline))' }}
                    content={<ChartTooltip />}
                  />
                  <Line dataKey="invoiceCount" name={t('reports.trends.count', 'Nb de factures')} stroke={countColor} strokeWidth={2} dot={{ r: 3 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
