import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Panel } from "@/components/ui/Panel"
import {  Loader2  } from 'lucide-react'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'
import { formatAmount } from '@/lib/format'

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
/**
 * Recharts renders literal CSS colors (SVG fill), it cannot read Tailwind
 * utility classes — so these are the resolved hex values of the Registre
 * semantic tokens (Track B / Lot B1, index.css :root), aligned bucket by
 * bucket with escalating severity: 0-30 = pos, 31-60 = warn, 61-90 = hot,
 * 90+ = crit.
 */
const BUCKET_COLORS: Record<string, string> = {
  '0_30': '#3E7C5A',   // --pos
  '31_60': '#B5852A',  // --warn
  '61_90': '#C4622E',  // --hot
  '90_plus': '#A6432E', // --crit
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
      <div className="bg-surface rounded-[4px] border border-hairline shadow-sm p-5 flex justify-center py-8">
        <Loader2 className="w-5 h-5 animate-spin text-ink-faint" />
      </div>
    )
  }

  return (
    <div className="bg-surface rounded-[4px] border border-hairline shadow-sm overflow-hidden" data-testid="aging-buckets-widget">
      <div className="px-5 py-4 border-b border-hairline bg-ground">
        <h2 className="font-semibold text-ink">{t('dashboard.agingBuckets.title')}</h2>
        <p className="text-xs text-ink-soft mt-0.5">{t('dashboard.agingBuckets.desc')}</p>
      </div>
      <div className="p-5">
        {isEmpty ? (
          <p data-testid="aging-buckets-empty" className="text-sm text-center text-ink-soft py-6">
            {t('dashboard.agingBuckets.empty')}
          </p>
        ) : (
          <>
            <div className="flex flex-wrap gap-4 mb-4 text-sm">
              <span className="text-ink-soft">
                {t('dashboard.agingBuckets.totalOverdue')}:{' '}
                <strong className="text-ink">{data.totalOverdueInvoiceCount}</strong>
              </span>
              <span className="text-ink-soft">
                {t('dashboard.agingBuckets.totalAmount')}:{' '}
                <strong className="num text-ink">
                  {formatAmount(data.totalOverdueAmount)} XOF
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
                      ? [`${formatAmount(value)} XOF`, t('dashboard.agingBuckets.amount')]
                      : [value, t('dashboard.agingBuckets.invoices')]
                  }
                />
                <Bar dataKey="amount" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
            {data.supplierRollup.length > 0 && (
              <div className="mt-5 border-t border-hairline pt-4">
                <h3 className="text-sm font-semibold text-ink mb-2">
                  {t('dashboard.agingBuckets.supplierRollup')}
                </h3>
                <div className="divide-y divide-hairline max-h-40 overflow-y-auto">
                  {data.supplierRollup.slice(0, 5).map((row) => (
                    <div key={row.supplierId ?? row.supplierName} className="flex justify-between py-2 text-sm">
                      <span className="text-ink-soft truncate pr-4">{row.supplierName}</span>
                      <span className="num text-ink shrink-0">
                        {formatAmount(row.totalOverdueAmount)} XOF
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
