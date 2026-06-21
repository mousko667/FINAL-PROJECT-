import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2 } from 'lucide-react'
import {
  ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'

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
      <div className="bg-white rounded-xl border overflow-hidden">
        <div className="px-5 py-4">
          <div className="flex justify-center py-4">
            <Loader2 className="w-5 h-5 animate-spin text-muted-foreground" />
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      <div className="px-5 py-4 font-semibold text-gray-900">{t('reports.trends.title', 'Tendances volume / valeur')}</div>
      <div className="border-t px-5 py-4">
        <p className="text-sm text-gray-500 mb-4">{t('reports.trends.desc', 'Nombre de factures et montant total par mois sur les 12 derniers mois (par date de facture).')}</p>
        {isEmpty ? (
          <p data-testid="volume-trend-empty" className="text-sm text-center text-gray-400 py-4">{t('reports.noData', 'Aucune donnée')}</p>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <ComposedChart data={trend!.points}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="monthLabel" tick={{ fontSize: 11 }} />
              <YAxis yAxisId="left" tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
              <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11 }} allowDecimals={false} />
              <Tooltip formatter={(value, name) => name === t('reports.trends.amount', 'Montant (XAF)')
                ? [`${Number(value).toLocaleString()} XAF`, name]
                : [value, name]} />
              <Legend />
              <Bar yAxisId="left" dataKey="totalAmount" name={t('reports.trends.amount', 'Montant (XAF)')} fill="#6366f1" radius={[4, 4, 0, 0]} />
              <Line yAxisId="right" dataKey="invoiceCount" name={t('reports.trends.count', 'Nb de factures')} stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} />
            </ComposedChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
