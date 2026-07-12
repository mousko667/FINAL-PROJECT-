import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'
import apiClient from '@/services/apiClient'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
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
  if (status === 'SOUMIS') return 'bg-info-bg text-info border border-info/30'
  if (status === 'EN_VALIDATION_N1') return 'bg-warn-bg text-warn border border-warn/30'
  if (status === 'EN_VALIDATION_N2') return 'bg-hot-bg text-hot border border-hot/30'
  if (status === 'VALIDE') return 'bg-pos-bg text-pos border border-pos/30'
  return 'bg-ground text-ink-soft border border-hairline'
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

const rowHoverTint = 'hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] transition-colors'

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
    <div className="space-y-6 page-enter">
      <PageHeader
        title={t('nav.approvals', 'Approval Queue')}
        subtitle={roleLabel}
        actions={<button onClick={() => refetch()} className="text-sm text-gold-deep hover:underline">{t('app.retry', 'Refresh')}</button>}
      />

      {/* SLA breach banner */}
      {breachedCount > 0 && (
        <div className="flex items-center gap-3 bg-crit-bg border border-crit/30 rounded-[4px] px-4 py-3">
          <AlertTriangle className="w-5 h-5 text-crit shrink-0" />
          <p className="text-sm text-crit font-medium">
            {t('approvals.slaBreached', '{{count}} invoice(s) have exceeded the 3-day SLA — immediate action required.', { count: breachedCount })}
          </p>
        </div>
      )}

      <Panel className="overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-ink-faint" />
          </div>
        ) : invoices.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 text-ink-faint gap-2">
            <CheckCircle className="w-10 h-10 text-pos" />
            <p className="text-sm font-medium">{t('approvals.empty', 'No invoices pending your approval')}</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-ground">
              <tr>
                <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.reference')}</th>
                <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.supplier')}</th>
                <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.department')}</th>
                <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.amount')}</th>
                <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('approvals.waiting', 'Waiting')}</th>
                <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.dueDate')}</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {invoices.map((inv) => {
                const breached = isSlaBreached(inv)
                const nearBreach = isSlaNearBreach(inv)
                const days = daysWaiting(inv)

                return (
                  <tr key={inv.id} className={`${rowHoverTint} ${breached ? 'bg-crit-bg' : nearBreach ? 'bg-warn-bg' : ''}`}>
                    <td className="num px-4 py-3 text-xs font-medium text-ink">{inv.referenceNumber}</td>
                    <td className="px-4 py-3 text-ink-soft truncate max-w-[150px]">{inv.supplierName}</td>
                    <td className="px-4 py-3 text-ink-faint text-xs">
                      {inv.departmentCode ? `${(i18n.language === 'en' ? inv.departmentNameEn : inv.departmentNameFr) ?? inv.departmentCode} (${inv.departmentCode})` : '—'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <span className="num font-medium text-ink">{formatAmount(inv.amount)}</span>{' '}
                      <span className="text-ink-faint text-xs">{inv.currency}</span>
                    </td>
                    {/* SLA indicator */}
                    <td className="px-4 py-3">
                      <span className={`inline-flex items-center gap-1 text-xs font-medium px-2 py-0.5 rounded-full
                        ${breached ? 'bg-crit-bg text-crit' : nearBreach ? 'bg-warn-bg text-warn' : 'bg-ground text-ink-soft'}`}>
                        {breached && <AlertTriangle className="w-3 h-3" />}
                        {!breached && <Clock className="w-3 h-3" />}
                        {days === 0
                          ? t('approvals.today', 'Today')
                          : t('approvals.daysAgo', '{{n}}d ago', { n: days })}
                        {breached && <span className="ml-0.5">{t('approvals.overdue', '⚠ Overdue')}</span>}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-ink-faint text-xs">
                      {inv.dueDate ? formatDate(inv.dueDate) : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <Link to={`/invoices/${inv.id}`} className="text-xs font-medium text-gold-deep hover:underline whitespace-nowrap">
                        {t('app.view', 'Review')} →
                      </Link>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </Panel>

      <p className="text-xs text-ink-faint">
        {t('approvals.hint', 'Click "Review" to open the invoice and record your decision.')}
        {' '}{t('approvals.slaNote', 'SLA: 3 business days per approval level. Red = SLA breached. Amber = last day.')}
      </p>
    </div>
  )
}
