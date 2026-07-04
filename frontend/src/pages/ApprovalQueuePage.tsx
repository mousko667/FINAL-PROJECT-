import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'
import apiClient from '@/services/apiClient'
import { Loader2, CheckCircle, Clock, AlertTriangle } from 'lucide-react'
import { formatAmount, formatDate } from '@/lib/format'

interface PendingInvoice {
  id: string
  referenceNumber: string
  supplierName: string
  amount: number
  currency: string
  status: string
  departmentCode?: string
  departmentNameFr?: string
  departmentNameEn?: string
  dueDate?: string
  createdAt?: string
}

function statusColor(status: string) {
  if (status === 'SOUMIS') return 'bg-blue-100 text-blue-800'
  if (status === 'EN_VALIDATION_N1') return 'bg-yellow-100 text-yellow-800'
  if (status === 'EN_VALIDATION_N2') return 'bg-orange-100 text-orange-800'
  if (status === 'VALIDE') return 'bg-teal-100 text-teal-800'
  return 'bg-gray-100 text-gray-700'
}

/** Returns days elapsed since the invoice entered its current status (approx. from createdAt as fallback) */
function daysWaiting(inv: PendingInvoice): number {
  const ref = inv.createdAt ? new Date(inv.createdAt) : null
  if (!ref) return 0
  return Math.floor((Date.now() - ref.getTime()) / 86_400_000)
}

/** SLA is 3 business days. Returns true if the invoice has been waiting > 3 days. */
function isSlaBreached(inv: PendingInvoice): boolean {
  return daysWaiting(inv) > 3
}

function isSlaNearBreach(inv: PendingInvoice): boolean {
  const d = daysWaiting(inv)
  return d === 3
}

export default function ApprovalQueuePage() {
  const { t, i18n } = useTranslation()
  const roles = useAppSelector((s) => s.auth.user?.roles ?? [])
  const departmentId = useAppSelector((s) => s.auth.user?.departmentId)

  const isN1 = roles.some((r) => r.startsWith('ROLE_VALIDATEUR_N1_'))
  const isN2 = roles.some((r) => r.startsWith('ROLE_VALIDATEUR_N2_'))
  const isDaf = roles.includes('ROLE_DAF')
  const isAA = roles.includes('ROLE_ASSISTANT_COMPTABLE')

  // N1/AA take charge of SOUMIS, then validate EN_VALIDATION_N1; N2 sees N2; DAF sees VALIDE.
  const statuses = isDaf ? ['VALIDE'] : isN2 ? ['EN_VALIDATION_N2'] : ['SOUMIS', 'EN_VALIDATION_N1']

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['approval-queue', statuses.join(','), departmentId],
    queryFn: async () => {
      const results = await Promise.all(statuses.map((status) => {
        const params: Record<string, string> = { status, size: '50', sort: 'createdAt,asc' }
        if (departmentId) params.department = departmentId
        return apiClient.get('/invoices', { params }).then((r) => r.data.data.content as PendingInvoice[])
      }))
      return { content: results.flat() }
    },
  })

  const invoices = data?.content ?? []
  const breachedCount = invoices.filter(isSlaBreached).length

  const roleLabel = isDaf
    ? t('approvals.roleLabel.daf', 'Invoices awaiting your BON À PAYER authorisation')
    : isN2
    ? t('approvals.roleLabel.n2', 'Invoices awaiting your Level 2 approval')
    : (isN1 || isAA)
    ? t('approvals.roleLabel.soumis', 'Invoices to take charge of')
    : t('approvals.roleLabel.n1', 'Invoices awaiting your Level 1 approval')

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('nav.approvals', 'Approval Queue')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{roleLabel}</p>
        </div>
        <button onClick={() => refetch()} className="text-sm text-primary hover:underline">{t('app.retry', 'Refresh')}</button>
      </div>

      {/* SLA breach banner */}
      {breachedCount > 0 && (
        <div className="flex items-center gap-3 bg-red-50 border border-red-200 rounded-xl px-4 py-3">
          <AlertTriangle className="w-5 h-5 text-red-600 shrink-0" />
          <p className="text-sm text-red-800 font-medium">
            {t('approvals.slaBreached', '{{count}} invoice(s) have exceeded the 3-day SLA — immediate action required.', { count: breachedCount })}
          </p>
        </div>
      )}

      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : invoices.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-muted-foreground gap-2">
            <CheckCircle className="w-10 h-10 text-green-400" />
            <p className="text-sm font-medium">{t('approvals.empty', 'No invoices pending your approval')}</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.reference')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.supplier')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.department')}</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">{t('invoice.amount')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('approvals.waiting', 'Waiting')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.dueDate')}</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {invoices.map((inv) => {
                const breached = isSlaBreached(inv)
                const nearBreach = isSlaNearBreach(inv)
                const days = daysWaiting(inv)

                return (
                  <tr key={inv.id} className={`hover:bg-gray-50 ${breached ? 'bg-red-50' : nearBreach ? 'bg-amber-50' : ''}`}>
                    <td className="px-4 py-3 font-mono text-xs font-medium text-gray-900">{inv.referenceNumber}</td>
                    <td className="px-4 py-3 text-gray-700 truncate max-w-[150px]">{inv.supplierName}</td>
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      {inv.departmentCode ? `${(i18n.language === 'en' ? inv.departmentNameEn : inv.departmentNameFr) ?? inv.departmentCode} (${inv.departmentCode})` : '—'}
                    </td>
                    <td className="px-4 py-3 text-right font-medium text-gray-900">
                      {formatAmount(inv.amount)} {inv.currency}
                    </td>
                    {/* SLA indicator */}
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full
                        ${breached ? 'bg-red-100 text-red-700' : nearBreach ? 'bg-amber-100 text-amber-700' : 'bg-gray-100 text-gray-600'}`}>
                        {breached && <AlertTriangle className="w-3 h-3" />}
                        {!breached && <Clock className="w-3 h-3" />}
                        {days === 0
                          ? t('approvals.today', 'Today')
                          : t('approvals.daysAgo', '{{n}}d ago', { n: days })}
                        {breached && <span className="ml-0.5">{t('approvals.overdue', '⚠ Overdue')}</span>}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      {inv.dueDate ? formatDate(inv.dueDate) : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <Link to={`/invoices/${inv.id}`} className="text-xs font-medium text-primary hover:underline whitespace-nowrap">
                        {t('app.view', 'Review')} →
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      <p className="text-xs text-gray-400">
        {t('approvals.hint', 'Click "Review" to open the invoice and record your decision.')}
        {' '}{t('approvals.slaNote', 'SLA: 3 business days per approval level. Red = SLA breached. Amber = last day.')}
      </p>
    </div>
  )
}
