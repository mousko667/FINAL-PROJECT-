import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { Loader2, ArrowRight, Calendar } from 'lucide-react'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { KpiBand, type KpiBandItem } from '@/components/ui/KpiBand'
import { Panel } from '@/components/ui/Panel'
import type { InvoiceStatus } from '@/types/invoice'
import { formatDate } from '@/lib/format'

interface SupplierDashboard {
  submittedCount: number
  pendingCount: number
  approvedCount: number
  paidCount: number
  rejectedCount: number
  lastPaymentDate?: string
  nextExpectedPaymentDate?: string
  pendingActions?: Array<{ id: string; referenceNumber: string; status: string; dueDate?: string }>
  matchingStatusBreakdown?: Record<string, number>
}

export default function SupplierDashboardPage() {
  const { t } = useTranslation()

  const { data, isLoading } = useQuery({
    queryKey: ['supplier-dashboard'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierDashboard }>('/supplier/dashboard')
      return data.data
    },
  })

  const kpiItems: KpiBandItem[] = [
    { label: t('supplier.portal.submitted', 'Submitted'), value: data?.submittedCount ?? 0, tone: 'info' },
    { label: t('supplier.portal.pending', 'Pending'), value: data?.pendingCount ?? 0, tone: 'warn' },
    { label: t('supplier.portal.approved', 'Approved'), value: data?.approvedCount ?? 0, tone: 'pos' },
    { label: t('supplier.portal.paid', 'Paid'), value: data?.paidCount ?? 0, tone: 'pos' },
    { label: t('supplier.portal.rejected', 'Rejected'), value: data?.rejectedCount ?? 0, tone: 'crit' },
  ]

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">{t('supplier.portal.dashboard', 'Dashboard')}</h1>

      {isLoading ? (
        <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>
      ) : (
        <>
          {/* Summary band */}
          <KpiBand items={kpiItems} className="grid grid-cols-2 md:grid-cols-5" />

          {/* Payment info */}
          {(data?.lastPaymentDate || data?.nextExpectedPaymentDate) && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {data?.lastPaymentDate && (
                <Panel className="p-5 flex items-center gap-4">
                  <Calendar className="w-5 h-5 text-ink-faint shrink-0" />
                  <div>
                    <p className="text-sm text-ink-soft">{t('supplier.portal.lastPayment', 'Last Payment')}</p>
                    <p className="font-semibold text-ink num">{formatDate(data.lastPaymentDate)}</p>
                  </div>
                </Panel>
              )}
              {data?.nextExpectedPaymentDate && (
                <Panel className="p-5 flex items-center gap-4">
                  <Calendar className="w-5 h-5 text-ink-faint shrink-0" />
                  <div>
                    <p className="text-sm text-ink-soft">{t('supplier.portal.nextPayment', 'Next Expected Payment')}</p>
                    <p className="font-semibold text-ink num">{formatDate(data.nextExpectedPaymentDate)}</p>
                  </div>
                </Panel>
              )}
            </div>
          )}

          {/* Pending actions */}
          {data?.pendingActions && data.pendingActions.length > 0 && (
            <Panel>
              <div className="flex items-center justify-between px-5 py-4 border-b border-hairline">
                <h2 className="font-semibold text-ink">{t('supplier.portal.pendingActions', 'Pending Actions')}</h2>
                <Link to="/supplier/invoices" className="text-xs text-primary flex items-center gap-1 hover:underline">
                  {t('app.view', 'View all')} <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
              <ul className="divide-y divide-hairline">
                {data.pendingActions.map((invoice) => (
                  <li key={invoice.id} className="flex items-center justify-between px-5 py-3">
                    <div>
                      <p className="font-medium text-sm text-ink num">{invoice.referenceNumber}</p>
                      {invoice.dueDate && (
                        <p className="text-xs text-ink-faint">{t('invoice.dueDate')}: {formatDate(invoice.dueDate)}</p>
                      )}
                    </div>
                    <StatusBadge status={invoice.status as InvoiceStatus} />
                  </li>
                ))}
              </ul>
            </Panel>
          )}
        </>
      )}
    </div>
  )
}
