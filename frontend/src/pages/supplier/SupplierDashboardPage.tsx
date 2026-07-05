import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { Panel } from "@/components/ui/Panel"
import {  Loader2, FileText, Clock, CheckCircle, DollarSign, XCircle, ArrowRight, Calendar  } from 'lucide-react'
import { StatusBadge } from '@/components/ui/StatusBadge'
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

  const cards = [
    { icon: FileText, label: t('supplier.portal.submitted', 'Submitted'), value: data?.submittedCount ?? 0, color: 'text-primary bg-primary/10' },
    { icon: Clock, label: t('supplier.portal.pending', 'Pending'), value: data?.pendingCount ?? 0, color: 'text-warn bg-warn/10' },
    { icon: CheckCircle, label: t('supplier.portal.approved', 'Approved'), value: data?.approvedCount ?? 0, color: 'text-teal-600 bg-teal-50' },
    { icon: DollarSign, label: t('supplier.portal.paid', 'Paid'), value: data?.paidCount ?? 0, color: 'text-pos bg-pos/10' },
    { icon: XCircle, label: t('supplier.portal.rejected', 'Rejected'), value: data?.rejectedCount ?? 0, color: 'text-crit bg-crit/10' },
  ]

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-ink">{t('supplier.portal.dashboard', 'Dashboard')}</h1>

      {isLoading ? (
        <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-primary" /></div>
      ) : (
        <>
          {/* Summary cards */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            {cards.map(({ icon: Icon, label, value, color }) => (
              <div key={label} className="bg-surface rounded-xl border border-hairline p-5 flex flex-col items-center text-center">
                <div className={`p-3 rounded-full mb-3 ${color}`}>
                  <Icon className="w-5 h-5" />
                </div>
                <p className="text-2xl font-bold text-ink">{value}</p>
                <p className="text-xs text-ink-faint mt-1">{label}</p>
              </div>
            ))}
          </div>

          {/* Payment info */}
          {(data?.lastPaymentDate || data?.nextExpectedPaymentDate) && (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {data?.lastPaymentDate && (
                <div className="bg-surface rounded-xl border border-hairline p-5 flex items-center gap-4">
                  <div className="p-3 bg-emerald-50 rounded-full">
                    <Calendar className="w-5 h-5 text-emerald-600" />
                  </div>
                  <div>
                    <p className="text-sm text-ink-faint">{t('supplier.portal.lastPayment', 'Last Payment')}</p>
                    <p className="font-semibold text-ink">{formatDate(data.lastPaymentDate)}</p>
                  </div>
                </div>
              )}
              {data?.nextExpectedPaymentDate && (
                <div className="bg-surface rounded-xl border border-hairline p-5 flex items-center gap-4">
                  <div className="p-3 bg-primary/10 rounded-full">
                    <Calendar className="w-5 h-5 text-primary" />
                  </div>
                  <div>
                    <p className="text-sm text-ink-faint">{t('supplier.portal.nextPayment', 'Next Expected Payment')}</p>
                    <p className="font-semibold text-ink">{formatDate(data.nextExpectedPaymentDate)}</p>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* Pending actions */}
          {data?.pendingActions && data.pendingActions.length > 0 && (
            <div className="bg-surface rounded-xl border border-hairline">
              <div className="flex items-center justify-between px-5 py-4 border-b">
                <h2 className="font-semibold text-ink">{t('supplier.portal.pendingActions', 'Pending Actions')}</h2>
                <Link to="/supplier/invoices" className="text-xs text-primary flex items-center gap-1 hover:underline">
                  {t('app.view', 'View all')} <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
              <ul className="divide-y">
                {data.pendingActions.map((invoice) => (
                  <li key={invoice.id} className="flex items-center justify-between px-5 py-3">
                    <div>
                      <p className="font-medium text-sm text-ink">{invoice.referenceNumber}</p>
                      {invoice.dueDate && (
                        <p className="text-xs text-ink-faint">{t('invoice.dueDate')}: {formatDate(invoice.dueDate)}</p>
                      )}
                    </div>
                    <StatusBadge status={invoice.status as InvoiceStatus} />
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </div>
  )
}
