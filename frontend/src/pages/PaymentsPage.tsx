import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { ExportMenu } from '@/components/ui/ExportMenu'
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
  const { t } = useTranslation()
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
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 space-y-5" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">{t('invoice.markPaid', 'Enregistrer le paiement')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        {/* Invoice summary */}
        <div className="bg-gray-50 rounded-xl p-4 flex items-center justify-between">
          <div>
            <p className="text-xs font-medium text-gray-500">{t('invoice.reference')}</p>
            <p className="font-mono font-bold text-gray-900">{invoice.referenceNumber}</p>
            <p className="text-xs text-gray-500 mt-0.5">{invoice.supplierName}</p>
          </div>
          <div className="text-right">
            <p className="text-xs font-medium text-gray-500">{t('invoice.amount')}</p>
            <p className="font-bold text-lg text-green-700">{Number(invoice.amount).toLocaleString()} {invoice.currency}</p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.paymentMethod', 'Mode de paiement')} *</label>
              <select value={form.paymentMethod} onChange={e => setForm(p => ({ ...p, paymentMethod: e.target.value }))}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30">
                {PAYMENT_METHODS.map(m => <option key={m} value={m}>{t(`invoice.paymentMethods.${m}`, m)}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.paymentDate', 'Date de paiement')} *</label>
              <input type="date" value={form.paymentDate} onChange={e => setForm(p => ({ ...p, paymentDate: e.target.value }))}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.paymentReference', 'Référence de paiement')} *</label>
            <input value={form.reference} onChange={e => setForm(p => ({ ...p, reference: e.target.value }))}
              placeholder="ex. VIR-2026-001"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('invoice.amount', 'Montant payé')} ({invoice.currency}) *</label>
            <input type="number" step="0.01" value={form.amountPaid}
              onChange={e => setForm(p => ({ ...p, amountPaid: Number(e.target.value) }))}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Notes (optionnel)</label>
            <textarea rows={2} value={form.notes} onChange={e => setForm(p => ({ ...p, notes: e.target.value }))}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
              placeholder="Informations complémentaires sur le paiement..." />
          </div>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" checked={form.scheduled}
              onChange={e => setForm(p => ({ ...p, scheduled: e.target.checked }))} />
            {t('payments.scheduleThis', 'Planifier ce paiement (à exécuter plus tard)')}
          </label>
        </div>

        {mutation.isError && (
          <p className="text-xs text-red-600 bg-red-50 p-2 rounded border border-red-200">
            {(mutation.error as any)?.response?.data?.message ?? t('app.error')}
          </p>
        )}

        <div className="flex justify-end gap-3 pt-2 border-t">
          <button onClick={onClose} className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">{t('app.cancel')}</button>
          <button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !form.reference || !form.paymentDate}
            className="flex items-center gap-2 px-5 py-2 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-60"
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

export default function PaymentsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [recordingFor, setRecordingFor] = useState<Invoice | null>(null)
  const [page, setPage] = useState(0)
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

  const { data: payments, isLoading: paymentsLoading } = useQuery({
    queryKey: ['payments', page],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Payment[]; totalPages: number; totalElements: number } }>(
        '/payments', { params: { page, size: 20 } }
      )
      return data.data
    },
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
      <div className="space-y-6">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{t('nav.payments', 'Paiements')}</h1>
            <p className="text-sm text-gray-500 mt-0.5">{t('payments.subtitle', 'Enregistrez les paiements et consultez l\'historique')}</p>
          </div>
          <Link
            to="/payments/alert-rules"
            className="flex items-center gap-2 border px-3 py-2 rounded-lg hover:bg-gray-50 text-sm font-medium text-gray-700"
          >
            <BellRing className="w-4 h-4" />
            {t('paymentAlerts.configureLink', 'Alert rules')}
          </Link>
        </div>

        {/* Invoices awaiting payment */}
        {(invoicesLoading || pendingInvoices.length > 0) && (
          <div className="bg-white rounded-xl border overflow-hidden">
            <div className="flex items-center gap-2 px-5 py-3 border-b bg-green-50">
              <DollarSign className="w-4 h-4 text-green-600" />
              <h2 className="font-semibold text-green-800 text-sm">
                {t('payments.awaitingPayment', 'Factures Bon à Payer — en attente de règlement')}
                {pendingInvoices.length > 0 && (
                  <span className="ml-2 bg-green-600 text-white text-xs px-2 py-0.5 rounded-full">{pendingInvoices.length}</span>
                )}
              </h2>
            </div>
            {/* B3 — batch payment toolbar (visible once invoices are selected) */}
            {selectedIds.size > 0 && (
              <div className="flex flex-wrap items-center gap-3 px-5 py-3 bg-blue-50 border-b border-blue-100">
                <span className="text-sm font-medium text-blue-800">
                  {t('payments.batchSelected', '{{count}} selected', { count: selectedIds.size })}
                </span>
                <select
                  value={batchMethod}
                  onChange={e => setBatchMethod(e.target.value)}
                  className="border rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                  {PAYMENT_METHODS.map(m => <option key={m} value={m}>{t(`invoice.paymentMethods.${m}`, m)}</option>)}
                </select>
                <input
                  type="date"
                  value={batchDate}
                  onChange={e => setBatchDate(e.target.value)}
                  className="border rounded-lg px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
                <button
                  onClick={() => batchMutation.mutate()}
                  disabled={batchMutation.isPending}
                  className="flex items-center gap-1.5 px-4 py-1.5 bg-green-600 text-white rounded-lg text-sm font-medium hover:bg-green-700 disabled:opacity-60"
                >
                  {batchMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <DollarSign className="w-4 h-4" />}
                  {t('payments.paySelected', 'Pay selected')}
                </button>
                <button onClick={() => setSelectedIds(new Set())} className="text-sm text-gray-500 hover:underline">
                  {t('app.cancel', 'Cancel')}
                </button>
              </div>
            )}

            {invoicesLoading ? (
              <div className="flex justify-center py-8"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
            ) : pendingInvoices.length === 0 ? (
              <div className="py-8 text-center text-sm text-gray-400">{t('payments.noPending', 'Aucune facture en attente de paiement')}</div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="px-4 py-3 w-10">
                      <input
                        type="checkbox"
                        aria-label={t('payments.selectAll', 'Select all')}
                        checked={pendingInvoices.length > 0 && selectedIds.size === pendingInvoices.length}
                        onChange={toggleSelectAll}
                      />
                    </th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.reference')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.supplier')}</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">{t('invoice.amount')}</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">{t('app.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {pendingInvoices.map(inv => (
                    <tr key={inv.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <input
                          type="checkbox"
                          aria-label={`select-${inv.referenceNumber}`}
                          checked={selectedIds.has(inv.id)}
                          onChange={() => toggleSelect(inv.id)}
                        />
                      </td>
                      <td className="px-4 py-3 font-mono text-xs font-semibold">{inv.referenceNumber}</td>
                      <td className="px-4 py-3 text-gray-700">{inv.supplierName ?? '—'}</td>
                      <td className="px-4 py-3 text-right font-medium text-green-700">
                        {Number(inv.amount).toLocaleString()} {inv.currency}
                      </td>
                      <td className="px-4 py-3 text-right flex items-center justify-end gap-2">
                        <Link to={`/invoices/${inv.id}`} className="text-xs text-primary hover:underline flex items-center gap-1">
                          <ExternalLink className="w-3 h-3" /> {t('app.view')}
                        </Link>
                        <button
                          onClick={() => setRecordingFor(inv)}
                          className="flex items-center gap-1.5 px-3 py-1.5 bg-green-600 text-white rounded-lg text-xs font-medium hover:bg-green-700 transition-colors"
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
          </div>
        )}

        {/* Payment history */}
        <div className="bg-white rounded-xl border overflow-hidden">
          <div className="flex items-center gap-2 px-5 py-3 border-b">
            <FileText className="w-4 h-4 text-gray-500" />
            <h2 className="font-semibold text-gray-800 text-sm">{t('payments.history', 'Historique des paiements')}</h2>
            {payments && <span className="text-xs text-gray-400">{payments.totalElements} {t('payments.total', 'paiements')}</span>}
            <div className="ml-auto">
              <ExportMenu endpoint="/payments/export" filename="payments" />
            </div>
          </div>

          {remittanceError && (
            <p className="px-5 py-2 text-xs text-amber-700 bg-amber-50 border-b border-amber-100">{remittanceError}</p>
          )}

          {paymentsLoading ? (
            <div className="flex justify-center py-10"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : paymentList.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 gap-2 text-gray-500">
              <CreditCard className="w-8 h-8" />
              <p className="text-sm">{t('payments.noHistory', 'Aucun paiement enregistré')}</p>
            </div>
          ) : (
            <>
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.reference')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.paymentMethod')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.paymentReference')}</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">{t('invoice.amount')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.paymentDate')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('payments.colStatus', 'Statut')}</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {paymentList.map(p => (
                    <tr key={p.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <Link to={`/invoices/${p.invoiceId}`} className="font-mono text-xs font-semibold text-primary hover:underline">
                          {p.invoiceReference ?? p.invoiceId.slice(0, 8)}
                        </Link>
                      </td>
                      <td className="px-4 py-3">
                        <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded border border-blue-100">
                          {t(`invoice.paymentMethods.${p.paymentMethod}`, p.paymentMethod)}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-700">{p.reference}</td>
                      <td className="px-4 py-3 text-right font-medium text-green-700">
                        {Number(p.amountPaid).toLocaleString()} {p.currency}
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">
                        {new Date(p.paymentDate).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3">
                        {p.status === 'SCHEDULED' ? (
                          <div className="flex flex-col items-start gap-1">
                            <span className="text-xs bg-amber-50 text-amber-700 px-2 py-0.5 rounded border border-amber-100">
                              {t('payments.status.scheduled', 'Planifié')}
                            </span>
                            <button onClick={() => processMutation.mutate(p.id)}
                              disabled={processMutation.isPending}
                              className="flex items-center gap-1 text-xs bg-amber-100 text-amber-800 border border-amber-200 px-2 py-1 rounded hover:bg-amber-200 disabled:opacity-50">
                              <CheckCircle className="w-3.5 h-3.5" />
                              {t('payments.markProcessed', 'Marquer exécuté')}
                            </button>
                            {processMutation.isError && processMutation.variables === p.id && (
                              <span className="text-xs text-red-600">
                                {(processMutation.error as any)?.response?.data?.message
                                  ? t((processMutation.error as any).response.data.message)
                                  : t('app.error', 'Une erreur est survenue')}
                              </span>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs bg-green-50 text-green-700 px-2 py-0.5 rounded border border-green-100">
                            {t('payments.status.processed', 'Exécuté')}
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {p.status === 'PROCESSED' && (
                          <button
                            onClick={() => downloadRemittance(p.id)}
                            disabled={remittanceId === p.id}
                            className="flex items-center gap-1 text-xs text-gray-500 hover:text-primary transition-colors ml-auto disabled:opacity-50">
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
                <div className="flex items-center justify-between px-4 py-3 border-t bg-gray-50">
                  <span className="text-sm text-gray-500">{t('pagination.page')} {page + 1} / {payments.totalPages}</span>
                  <div className="flex gap-2">
                    <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1.5 bg-white border rounded-lg text-sm disabled:opacity-40">{t('app.previous')}</button>
                    <button disabled={page >= payments.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1.5 bg-white border rounded-lg text-sm disabled:opacity-40">{t('app.next')}</button>
                  </div>
                </div>
              )}
            </>
          )}
        </div>
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
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg p-6 space-y-4" onClick={e => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-bold text-gray-900">{t('payments.batchResultTitle', 'Batch payment result')}</h2>
              <button onClick={() => setBatchResult(null)} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
            </div>
            <p className="text-sm text-gray-600">
              {t('payments.batchSummary', '{{ok}} succeeded, {{ko}} failed out of {{total}}', {
                ok: batchResult.succeeded, ko: batchResult.failed, total: batchResult.total,
              })}
            </p>
            <ul className="space-y-2 max-h-80 overflow-y-auto">
              {batchResult.results.map(r => (
                <li key={r.invoiceId} className={`flex items-start gap-2 text-sm rounded-lg p-2 border ${r.success ? 'bg-green-50 border-green-100' : 'bg-red-50 border-red-100'}`}>
                  {r.success
                    ? <CheckCircle className="w-4 h-4 text-green-600 mt-0.5 shrink-0" />
                    : <span className="w-4 h-4 text-red-600 mt-0.5 shrink-0 font-bold text-center">✕</span>}
                  <div className="min-w-0">
                    <span className="font-mono text-xs font-semibold">{refByInvoice(r.invoiceId)}</span>
                    {r.success
                      ? <span className="text-green-700 ml-2 text-xs">{r.reference}</span>
                      : <span className="text-red-700 ml-2 text-xs">{r.error}</span>}
                  </div>
                </li>
              ))}
            </ul>
            <div className="flex justify-end pt-2 border-t">
              <button onClick={() => setBatchResult(null)} className="px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90">
                {t('app.close', 'Close')}
              </button>
            </div>
          </div>
        </div>
      )}
    </PageRoleGuard>
  )
}
