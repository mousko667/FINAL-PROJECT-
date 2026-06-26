import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2 } from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'

interface AgingBucket {
  bucketKey: string
  displayName: string
  invoiceCount: number
  totalAmount: number
}

interface SupplierRollup {
  supplierId: string | null
  supplierName: string
  invoiceCount: number
  totalOverdueAmount: number
  amountByBucket: Record<string, number>
}

interface BucketedAging {
  buckets: Record<string, AgingBucket>
  totalOverdueAmount: number
  totalOverdueInvoiceCount: number
  supplierRollup: SupplierRollup[]
}

const BUCKET_ORDER = ['0_30', '31_60', '61_90', '90_plus'] as const
const BUCKET_COLORS: Record<string, string> = {
  '0_30': '#22c55e',
  '31_60': '#f59e0b',
  '61_90': '#f97316',
  '90_plus': '#ef4444',
}

export default function AgingBucketsWidget() {
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['aging-buckets'],
    queryFn: async () => {
      const { data: res } = await apiClient.get<{ data: BucketedAging }>('/reports/aging/buckets')
      return res.data
    },
    retry: false,
  })

  const chartData = BUCKET_ORDER.map((key) => {
    const bucket = data?.buckets?.[key]
    return {
      key,
      label: bucket?.displayName ?? t(`dashboard.agingBuckets.bucket.${key}`),
      amount: Number(bucket?.totalAmount ?? 0),
      count: Number(bucket?.invoiceCount ?? 0),
      fill: BUCKET_COLORS[key],
    }
  })

  const isEmpty = !data || data.totalOverdueInvoiceCount === 0

  if (isLoading) {
    return (
      <div className="bg-white rounded-xl border p-5 flex justify-center py-8">
        <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
      </div>
    )
  }

  return (
    <div className="bg-white rounded-xl border overflow-hidden" data-testid="aging-buckets-widget">
      <div className="px-5 py-4 border-b bg-gray-50">
        <h2 className="font-semibold text-gray-900">{t('dashboard.agingBuckets.title')}</h2>
        <p className="text-xs text-gray-500 mt-0.5">{t('dashboard.agingBuckets.desc')}</p>
      </div>
      <div className="p-5">
        {isEmpty ? (
          <p data-testid="aging-buckets-empty" className="text-sm text-center text-gray-500 py-6">
            {t('dashboard.agingBuckets.empty')}
          </p>
        ) : (
          <>
            <div className="flex flex-wrap gap-4 mb-4 text-sm">
              <span className="text-gray-600">
                {t('dashboard.agingBuckets.totalOverdue')}:{' '}
                <strong className="text-gray-900">{data.totalOverdueInvoiceCount}</strong>
              </span>
              <span className="text-gray-600">
                {t('dashboard.agingBuckets.totalAmount')}:{' '}
                <strong className="text-gray-900 font-mono">
                  {Number(data.totalOverdueAmount).toLocaleString()} XAF
                </strong>
              </span>
            </div>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} />
                <Tooltip
                  formatter={(value: number, name: string) =>
                    name === 'amount'
                      ? [`${value.toLocaleString()} XAF`, t('dashboard.agingBuckets.amount')]
                      : [value, t('dashboard.agingBuckets.invoices')]
                  }
                />
                <Bar dataKey="amount" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
            {data.supplierRollup.length > 0 && (
              <div className="mt-5 border-t pt-4">
                <h3 className="text-sm font-semibold text-gray-800 mb-2">
                  {t('dashboard.agingBuckets.supplierRollup')}
                </h3>
                <div className="divide-y max-h-40 overflow-y-auto">
                  {data.supplierRollup.slice(0, 5).map((row) => (
                    <div key={row.supplierId ?? row.supplierName} className="flex justify-between py-2 text-sm">
                      <span className="text-gray-700 truncate pr-4">{row.supplierName}</span>
                      <span className="font-mono text-gray-900 shrink-0">
                        {Number(row.totalOverdueAmount).toLocaleString()} XAF
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
