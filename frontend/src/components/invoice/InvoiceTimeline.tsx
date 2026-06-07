import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { StatusBadge } from '@/components/ui/StatusBadge'
import type { Invoice } from '@/types/invoice'
import apiClient from '@/services/apiClient'

interface InvoiceTimelineProps {
  invoice: Invoice
}

interface StatusHistory {
  id: string
  fromStatus: string
  toStatus: string
  changedByUsername?: string
  changedAt: string
  changeReason?: string
}

export function InvoiceTimeline({ invoice }: InvoiceTimelineProps) {
  const { t } = useTranslation()
  const { data: history = [] } = useQuery({
    queryKey: ['invoice-history', invoice.id],
    queryFn: async () => {
      const response = await apiClient.get<{ data: StatusHistory[] }>(`/invoices/${invoice.id}/history`)
      return response.data.data
    },
    enabled: !!invoice.id,
  })

  return (
    <div className="bg-white rounded-xl border p-5">
      <h2 className="font-semibold text-gray-800 mb-4">{t('invoice.timeline')}</h2>
      {history.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('app.noData')}</p>
      ) : (
        <ol className="relative border-l border-gray-200 ml-3 space-y-6">
          {history.map((h) => (
            <li key={h.id} className="ml-6">
              <span className="absolute -left-3 flex items-center justify-center w-6 h-6 bg-primary/10 rounded-full ring-4 ring-white">
                <span className="w-2 h-2 bg-primary rounded-full" />
              </span>
              <div className="flex items-center gap-2 flex-wrap">
                <StatusBadge status={h.fromStatus as never} />
                <span className="text-gray-400 text-xs">to</span>
                <StatusBadge status={h.toStatus as never} />
              </div>
              <div className="mt-1 text-xs text-muted-foreground">
                {h.changedByUsername ?? 'System'} - {new Date(h.changedAt).toLocaleString()}
              </div>
              {h.changeReason && (
                <p className="mt-1 text-xs text-gray-600 italic">{h.changeReason}</p>
              )}
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
