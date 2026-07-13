import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { useHasRole } from '@/hooks/useHasRole'
import { ExportMenu } from '@/components/ui/ExportMenu'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import {
  Loader2, DollarSign, ExternalLink, Download, CheckCircle,
  FileText, CreditCard, Calendar, Hash, BellRing,
} from 'lucide-react'

interface Payment {
  id: string
  invoiceId: string
  invoiceReference?: string
  amountPaid: number
  currency: string
  paymentDate: string
  paymentMethod: string
  reference: string
  notes?: string
  createdAt: string
  status: 'SCHEDULED' | 'PROCESSED'
  processedDate?: string
}

interface Invoice {
  id: string
  referenceNumber: string
  amount: number
  currency: string
  supplierName: string
  status: string
}

// Values MUST match the backend PaymentMethod enum exactly (Jackson maps the JSON string to the
// enum by name). Labels are resolved at render time via i18n (invoice.paymentMethods.<value>).
const PAYMENT_METHODS = ['VIREMENT', 'CHEQUE', 'ESPECES', 'MOBILE_MONEY'] as const

function RecordPaymentModal({ invoice, onClose, onSuccess }: {
  invoice: Invoice
  onClose: () => void
  onSuccess: () => void
}) {
  const { t, i18n } = useTranslation()
  const [form, setForm] = useState({
    amountPaid: invoice.amount,
    paymentDate: new Date().toISOString().slice(0, 10),
    paymentMethod: 'VIREMENT',
    reference: `PAY-${invoice.referenceNumber}-${Date.now().toString().slice(-6)}`,
    notes: '',
    scheduled: false,
  })

  const mutation = useMutation({
    mutationFn: () => apiClient.post(`/payments/invoice/${invoice.id}`, {
      amountPaid: form.amountPaid,
      paymentDate: new Date(form.paymentDate).toISOString(),
      paymentMethod: form.paymentMethod,
      reference: form.reference,
      notes: form.notes || undefined,
      scheduled: form.scheduled || undefined,
    }),
    onSuccess: () => { onSuccess(); onClose() },
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-surface rounded-[4px] shadow-2xl w-full max-w-lg p-6 space-y-5 border border-hairline" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-ink">{t('invoice.markPaid', 'Enregistrer le paiement')}</h2>
          <button onClick={onClose} className="text-ink-faint hover:text-ink-soft text-xl leading-none">×</button>
        </div>

        {/* Invoice summary */}
        <div className="bg-ground rounded-[4px] p-4 flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-ink-faint">{t('invoice.reference')}</p>
            <p className="num font-bold text-ink">{invoice.referenceNumber}</p>
            <p className="text-xs text-ink-faint mt-0.5">{invoice.supplierName}</p>
          </div>
          <div className="text-right">
            <p className="text-xs font-medium text-ink-faint">{t('invoice.amount')}</p>
            <p className="num font-bold text-lg text-pos">{Number(invoice.amount).toLocaleString(i18n.language === 'en' ? 'en-US' : 'fr-FR')} {invoice.currency}</p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.paymentMethod', 'Mode de paiement')} *</label>
              <select value={form.paymentMethod} onChange={e => setForm(p => ({ ...p, paymentMethod: e.target.value }))}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30">
                {PAYMENT_METHODS.map(m => <option key={m} value={m}>{t(`invoice.paymentMethods.${m}`, m)}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.paymentDate', 'Date de paiement')} *</label>
              <input type="date" value={form.paymentDate} onChange={e => setForm(p => ({ ...p, paymentDate: e.target.value }))}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30" />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.paymentReference', 'Référence de paiement')} *</label>
            <input value={form.reference} onChange={e => setForm(p => ({ ...p, reference: e.target.value }))}
              placeholder="ex. VIR-2026-001"
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30" />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('invoice.amount', 'Montant payé')} ({invoice.currency}) *</label>
            <input type="number" step="0.01" value={form.amountPaid}
              onChange={e => setForm(p => ({ ...p, amountPaid: Number(e.target.value) }))}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30" />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">Notes (optionnel)</label>
            <textarea rows={2} value={form.notes} onChange={e => setForm(p => ({ ...p, notes: e.target.value }))}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30 resize-none"
              placeholder={t('payments.notesPlaceholder')} />
          </div>

          <label className="flex items-center gap-2 text-sm text-ink-soft">
            <input type="checkbox" checked={form.scheduled}
              onChange={e => setForm(p => ({ ...p, scheduled: e.target.checked }))} />
            {t('payments.scheduleThis', 'Planifier ce paiement (à exécuter plus tard)')}
          </label>
        </div>

        {mutation.isError && (
          <p className="text-xs text-crit bg-crit-bg p-2 rounded border border-crit/30">
            {(mutation.error as any)?.response?.data?.message ?? t('app.error')}
          </p>
        )}

        <div className="flex justify-end gap-3 pt-2 border-t border-hairline">
          <button onClick={onClose} className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground text-ink-soft">{t('app.cancel')}</button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !form.reference || !form.paymentDate}
            className="flex items-center gap-2 px-5 py-2 bg-pos text-white rounded-[4px] text-sm font-medium hover:opacity-90 disabled:opacity-60"
          >
            {mutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
            {form.scheduled ? t('payments.scheduleBtn', 'Planifier le paiement') : t('invoice.markPaid', 'Enregistrer le paiement')}
          </button>
        </div>
      </div>
    </div>
  )
}

interface BatchLineResult {
  invoiceId: string
  success: boolean
  paymentId?: string | null
  reference?: string | null
  error?: string | null
}
interface BatchResult {
  total: number
  succeeded: number
  failed: number
  results: BatchLineResult[]
}

const rowHoverTint = 'hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)] transition-colors'

export default function PaymentsPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [recordingFor, setRecordingFor] = useState<Invoice | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [remittanceId, setRemittanceId] = useState<string | null>(null)
  const [remittanceError, setRemittanceError] = useState('')
  // B3 — batch payment of selected BON_A_PAYER invoices.
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [batchMethod, setBatchMethod] = useState('VIREMENT')
  const [batchDate, setBatchDate] = useState(new Date().toISOString().slice(0, 10))
  const [batchResult, setBatchResult] = useState<BatchResult | null>(null)

  // REQ-11: fetch the pre-signed remittance-advice URL and open it.
  const downloadRemittance = async (paymentId: string) => {
    setRemittanceId(paymentId)
    setRemittanceError('')
    try {
      const { data } = await apiClient.get<{ data: string }>(`/payments/${paymentId}/remittance`)
      if (data.data) window.open(data.data, '_blank', 'noopener,noreferrer')
    } catch {
      setRemittanceError(t('payments.remittanceError', 'No remittance advice is available for this payment yet.'))
    } finally {
      setRemittanceId(null)
    }
  }

  // Payments are DAF / Assistant Comptable only. Gate the fetches on role so the
  // page doesn't fire calls the backend 403s before PageRoleGuard hides the UI.
  const canView = useHasRole('ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE')

  const { data: payments, isLoading: paymentsLoading } = useQuery({
    queryKey: ['payments', page, statusFilter],
    queryFn: async () => {
      const params: any = { page, size: 20 }
      if (statusFilter !== 'ALL') params.status = statusFilter
      const { data } = await apiClient.get<{ data: { content: Payment[]; totalPages: number; totalElements: number } }>(
        '/payments', { params }
      )
      return data.data
    },
    enabled: canView,
  })

  // Invoices awaiting payment (BON_A_PAYER status)
  const { data: awaitingPayment, isLoading: invoicesLoading } = useQuery({
    queryKey: ['invoices-bon-a-payer'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Invoice[] } }>(
        '/invoices', { params: { status: 'BON_A_PAYER', size: 50 } }
      )
      return data.data?.content ?? []
    },
    enabled: canView,
  })

  const paymentList = payments?.content ?? []
  const pendingInvoices = awaitingPayment ?? []

  const toggleSelect = (id: string) => setSelectedIds(prev => {
    const next = new Set(prev)
    next.has(id) ? next.delete(id) : next.add(id)
    return next
  })
  const toggleSelectAll = () => setSelectedIds(prev =>
    prev.size === pendingInvoices.length ? new Set() : new Set(pendingInvoices.map(i => i.id)))

  const batchMutation = useMutation({
    mutationFn: async () => {
      const { data } = await apiClient.post<{ data: BatchResult }>('/payments/batch', {
        invoiceIds: Array.from(selectedIds),
        paymentMethod: batchMethod,
        paymentDate: new Date(batchDate).toISOString(),
      })
      return data.data
    },
    onSuccess: (result) => {
      setBatchResult(result)
      setSelectedIds(new Set())
      queryClient.invalidateQueries({ queryKey: ['invoices-bon-a-payer'] })
      queryClient.invalidateQueries({ queryKey: ['payments'] })
    },
  })

  const refByInvoice = (invoiceId: string) => pendingInvoices.find(i => i.id === invoiceId)?.referenceNumber ?? invoiceId

  const processMutation = useMutation({
    mutationFn: (paymentId: string) => apiClient.post(`/payments/${paymentId}/process`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payments'] })
      queryClient.invalidateQueries({ queryKey: ['invoices-bon-a-payer'] })
    },
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']}>
      <div className="space-y-6 page-enter">
        <PageHeader
          title={t('nav.payments', 'Paiements')}
          subtitle={t('payments.subtitle', 'Enregistrez les paiements et consultez l\'historique')}
          actions={
            <Link
              to="/payments/alert-rules"
              className="flex items-center gap-2 border border-white/30 px-3 py-2 rounded-[4px] hover:bg-white/10 text-sm font-medium text-white"
            >
              <BellRing className="w-4 h-4" />
              {t('paymentAlerts.configureLink', 'Alert rules')}
            </Link>
          }
        />

        {/* Invoices awaiting payment */}
        {(invoicesLoading || pendingInvoices.length > 0) && (
          <Panel className="overflow-hidden">
            <div className="flex items-center gap-2 px-5 py-3 border-b border-hairline bg-pos-bg">
              <DollarSign className="w-4 h-4 text-pos" />
              <h2 className="font-semibold text-pos text-sm">
                {t('payments.awaitingPayment', 'Factures Bon à Payer — en attente de règlement')}
                {pendingInvoices.length > 0 && (
                  <span className="num ml-2 bg-pos text-white text-xs px-2 py-0.5 rounded-full">{pendingInvoices.length}</span>
                )}
              </h2>
            </div>
            {/* B3 — batch payment toolbar (visible once invoices are selected) */}
            {selectedIds.size > 0 && (
              <div className="flex flex-wrap items-center gap-3 px-5 py-3 bg-info-bg border-b border-info/30">
                <span className="text-sm font-medium text-info">
                  {t('payments.batchSelected', '{{count}} selected', { count: selectedIds.size })}
                </span>
                <select
                  value={batchMethod}
                  onChange={e => setBatchMethod(e.target.value)}
                  className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30"
                >
                  {PAYMENT_METHODS.map(m => <option key={m} value={m}>{t(`invoice.paymentMethods.${m}`, m)}</option>)}
                </select>
                <input
                  type="date"
                  value={batchDate}
                  onChange={e => setBatchDate(e.target.value)}
                  className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30"
                />
                <button
                  onClick={() => batchMutation.mutate()}
                  disabled={batchMutation.isPending}
                  className="flex items-center gap-1.5 px-4 py-1.5 bg-pos text-white rounded-[4px] text-sm font-medium hover:opacity-90 disabled:opacity-60"
                >
                  {batchMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <DollarSign className="w-4 h-4" />}
                  {t('payments.paySelected', 'Pay selected')}
                </button>
                <button onClick={() => setSelectedIds(new Set())} className="text-sm text-ink-faint hover:underline">
                  {t('app.cancel', 'Cancel')}
                </button>
              </div>
            )}

            {invoicesLoading ? (
              <div className="flex justify-center py-8"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
            ) : pendingInvoices.length === 0 ? (
              <div className="py-8 text-center text-sm text-ink-faint">{t('payments.noPending', 'Aucune facture en attente de paiement')}</div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-ground">
                  <tr>
                    <th className="px-4 py-3 w-10">
                      <input
                        type="checkbox"
                        aria-label={t('payments.selectAll', 'Select all')}
                        checked={pendingInvoices.length > 0 && selectedIds.size === pendingInvoices.length}
                        onChange={toggleSelectAll}
                      />
                    </th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.reference')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.supplier')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.amount')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('app.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {pendingInvoices.map(inv => (
                    <tr key={inv.id} className={rowHoverTint}>
                      <td className="px-4 py-3">
                        <input
                          type="checkbox"
                          aria-label={`select-${inv.referenceNumber}`}
                          checked={selectedIds.has(inv.id)}
                          onChange={() => toggleSelect(inv.id)}
                        />
                      </td>
                      <td className="num px-4 py-3 text-xs font-semibold text-ink">{inv.referenceNumber}</td>
                      <td className="px-4 py-3 text-ink-soft">{inv.supplierName ?? '—'}</td>
                      <td className="px-4 py-3 text-right">
                        <span className="num font-medium text-pos">{Number(inv.amount).toLocaleString(i18n.language === 'en' ? 'en-US' : 'fr-FR')}</span>{' '}
                        <span className="text-ink-faint text-xs">{inv.currency}</span>
                      </td>
                      <td className="px-4 py-3 text-right flex items-center justify-end gap-2">
                        <Link to={`/invoices/${inv.id}`} className="text-xs text-gold-deep hover:underline flex items-center gap-1">
                          <ExternalLink className="w-3 h-3" /> {t('app.view')}
                        </Link>
                        <button
                          onClick={() => setRecordingFor(inv)}
                          className="flex items-center gap-1.5 px-3 py-1.5 bg-pos text-white rounded-[4px] text-xs font-medium hover:opacity-90 transition-colors"
                        >
                          <DollarSign className="w-3.5 h-3.5" />
                          {t('invoice.markPaid', 'Payer')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </Panel>
        )}

        {/* Payment history */}
        <Panel className="overflow-hidden">
          <div className="flex items-center gap-2 px-5 py-3 border-b border-hairline flex-wrap">
            <FileText className="w-4 h-4 text-ink-faint" />
            <h2 className="font-semibold text-ink text-sm">{t('payments.history', 'Historique des paiements')}</h2>
            {payments && <span className="text-xs text-ink-faint">{payments.totalElements} {t('payments.total', 'paiements')}</span>}
            <div className="ml-auto flex items-center gap-3">
              <select
                value={statusFilter}
                onChange={e => { setStatusFilter(e.target.value); setPage(0) }}
                className="border border-hairline rounded-[4px] px-2 py-1.5 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30"
              >
                <option value="ALL">{t('matching.all', 'Tous')}</option>
                <option value="SCHEDULED">{t('payments.status.scheduled', 'Planifié')}</option>
                <option value="PROCESSED">{t('payments.status.processed', 'Exécuté')}</option>
              </select>
              <ExportMenu endpoint="/payments/export" filename="payments" />
            </div>
          </div>

          {remittanceError && (
            <p className="px-5 py-2 text-xs text-warn bg-warn-bg border-b border-warn/30">{remittanceError}</p>
          )}

          {paymentsLoading ? (
            <div className="flex justify-center py-10"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
          ) : paymentList.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 gap-2 text-ink-faint">
              <CreditCard className="w-8 h-8" />
              <p className="text-sm">{t('payments.noHistory', 'Aucun paiement enregistré')}</p>
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="bg-ground">
                  <tr>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.reference')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.paymentMethod')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.paymentReference')}</th>
                    <th className="text-right px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.amount')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.paymentDate')}</th>
                    <th className="text-left px-4 py-3 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('payments.colStatus', 'Statut')}</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {paymentList.map(p => (
                    <tr key={p.id} className={rowHoverTint}>
                      <td className="px-4 py-3">
                        <Link to={`/invoices/${p.invoiceId}`} className="num text-xs font-semibold text-gold-deep hover:underline">
                          {p.invoiceReference ?? p.invoiceId.slice(0, 8)}
                        </Link>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-xs bg-info-bg text-info px-2 py-0.5 rounded border border-info/30">
                          {t(`invoice.paymentMethods.${p.paymentMethod}`, p.paymentMethod)}
                        </span>
                      </td>
                      <td className="num px-4 py-3 text-xs text-ink-soft">{p.reference}</td>
                      <td className="px-4 py-3 text-right">
                        <span className="num font-medium text-pos">{Number(p.amountPaid).toLocaleString(i18n.language === 'en' ? 'en-US' : 'fr-FR')}</span>{' '}
                        <span className="text-ink-faint text-xs">{p.currency}</span>
                      </td>
                      <td className="px-4 py-3 text-ink-faint text-xs">
                        {new Date(p.paymentDate).toLocaleDateString(i18n.language === 'en' ? 'en-US' : 'fr-FR')}
                      </td>
                      <td className="px-4 py-3">
                        {p.status === 'SCHEDULED' ? (
                          <div className="flex flex-col items-start gap-1">
                            <span className="text-xs bg-warn-bg text-warn px-2 py-0.5 rounded border border-warn/30">
                              {t('payments.status.scheduled', 'Planifié')}
                            </span>
                            <button onClick={() => processMutation.mutate(p.id)}
                              disabled={processMutation.isPending}
                              className="flex items-center gap-1 text-xs bg-warn-bg text-warn border border-warn/30 px-2 py-1 rounded hover:opacity-90 disabled:opacity-50">
                              <CheckCircle className="w-3.5 h-3.5" />
                              {t('payments.markProcessed', 'Marquer exécuté')}
                            </button>
                            {processMutation.isError && processMutation.variables === p.id && (
                              <span className="text-xs text-crit">
                                {(processMutation.error as any)?.response?.data?.message
                                  ? t((processMutation.error as any).response.data.message)
                                  : t('app.error', 'Une erreur est survenue')}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs bg-pos-bg text-pos px-2 py-0.5 rounded border border-pos/30">
                            {t('payments.status.processed', 'Exécuté')}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {p.status === 'PROCESSED' && (
                          <button
                            onClick={() => downloadRemittance(p.id)}
                            disabled={remittanceId === p.id}
                            className="flex items-center gap-1 text-xs text-ink-faint hover:text-gold-deep transition-colors ml-auto disabled:opacity-50">
                            {remittanceId === p.id
                              ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                              : <Download className="w-3.5 h-3.5" />}
                            {t('payments.remittance', 'Avis')}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              {payments && payments.totalPages > 1 && (
                <div className="flex items-center justify-between px-4 py-3 border-t border-hairline bg-ground">
                  <span className="text-sm text-ink-faint">{t('pagination.page')} {page + 1} / {payments.totalPages}</span>
                  <div className="flex gap-2">
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1.5 bg-surface border border-hairline rounded-[4px] text-sm disabled:opacity-40 text-ink-soft">{t('app.previous')}</button>
                    <button disabled={page >= payments.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1.5 bg-surface border border-hairline rounded-[4px] text-sm disabled:opacity-40 text-ink-soft">{t('app.next')}</button>
                  </div>
                </div>
              )}
            </>
          )}
        </Panel>
      </div>

      {recordingFor && (
        <RecordPaymentModal
          invoice={recordingFor}
          onClose={() => setRecordingFor(null)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['payments'] })
            queryClient.invalidateQueries({ queryKey: ['invoices-bon-a-payer'] })
            setRecordingFor(null)
          }}
        />
      )}

      {/* B3 — batch payment result (per-line) */}
      {batchResult && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={() => setBatchResult(null)}>
          <div className="bg-surface rounded-[4px] shadow-2xl w-full max-w-lg p-6 space-y-4 border border-hairline" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-ink">{t('payments.batchResultTitle', 'Batch payment result')}</h2>
              <button onClick={() => setBatchResult(null)} className="text-ink-faint hover:text-ink-soft text-xl leading-none">×</button>
            </div>
            <p className="text-sm text-ink-soft">
              {t('payments.batchSummary', '{{ok}} succeeded, {{ko}} failed out of {{total}}', {
                ok: batchResult.succeeded, ko: batchResult.failed, total: batchResult.total,
              })}
            </p>
            <ul className="space-y-2 max-h-80 overflow-y-auto">
              {batchResult.results.map(r => (
                <li key={r.invoiceId} className={`flex items-start gap-2 text-sm rounded-[4px] p-2 border ${r.success ? 'bg-pos-bg border-pos/30' : 'bg-crit-bg border-crit/30'}`}>
                  {r.success
                    ? <CheckCircle className="w-4 h-4 text-pos mt-0.5 shrink-0" />
                    : <span className="w-4 h-4 text-crit mt-0.5 shrink-0 font-bold text-center">✕</span>}
                  <div className="min-w-0">
                    <span className="num text-xs font-semibold text-ink">{refByInvoice(r.invoiceId)}</span>
                    {r.success
                      ? <span className="text-pos ml-2 text-xs">{r.reference}</span>
                      : <span className="text-crit ml-2 text-xs">{r.error}</span>}
                  </div>
                </li>
              ))}
            </ul>
            <div className="flex justify-end pt-2 border-t border-hairline">
              <button onClick={() => setBatchResult(null)} className="px-4 py-2 bg-oct-navy text-white rounded-[4px] text-sm font-medium hover:bg-oct-navy-light">
                {t('app.close', 'Close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </PageRoleGuard>
  )
}
