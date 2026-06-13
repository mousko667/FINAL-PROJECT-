import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import {
  Loader2, DollarSign, ExternalLink, Download, CheckCircle,
  FileText, CreditCard, Calendar, Hash,
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
}

interface Invoice {
  id: string
  referenceNumber: string
  amount: number
  currency: string
  supplierName: string
  status: string
}

const PAYMENT_METHODS = [
  { value: 'BANK_TRANSFER', label: 'Virement bancaire' },
  { value: 'CHECK', label: 'Chèque' },
  { value: 'MOBILE_MONEY', label: 'Mobile Money' },
  { value: 'CASH', label: 'Espèces' },
]

function RecordPaymentModal({ invoice, onClose, onSuccess }: {
  invoice: Invoice
  onClose: () => void
  onSuccess: () => void
}) {
  const { t } = useTranslation()
  const [form, setForm] = useState({
    amountPaid: invoice.amount,
    paymentDate: new Date().toISOString().slice(0, 10),
    paymentMethod: 'BANK_TRANSFER',
    reference: `PAY-${invoice.referenceNumber}-${Date.now().toString().slice(-6)}`,
    notes: '',
  })

  const mutation = useMutation({
    mutationFn: () => apiClient.post(`/payments/invoice/${invoice.id}`, {
      amountPaid: form.amountPaid,
      paymentDate: new Date(form.paymentDate).toISOString(),
      paymentMethod: form.paymentMethod,
      reference: form.reference,
      notes: form.notes || undefined,
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
                {PAYMENT_METHODS.map(m => <option key={m.value} value={m.value}>{m.label}</option>)}
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
            {t('invoice.markPaid', 'Enregistrer le paiement')}
          </button>
        </div>
      </div>
    </div>
  )
}

export default function PaymentsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [recordingFor, setRecordingFor] = useState<Invoice | null>(null)
  const [page, setPage] = useState(0)
  const [remittanceId, setRemittanceId] = useState<string | null>(null)
  const [remittanceError, setRemittanceError] = useState('')

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

  return (
    <PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE', 'ROLE_DAF']}>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('nav.payments', 'Paiements')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('payments.subtitle', 'Enregistrez les paiements et consultez l\'historique')}</p>
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
            {invoicesLoading ? (
              <div className="flex justify-center py-8"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
            ) : pendingInvoices.length === 0 ? (
              <div className="py-8 text-center text-sm text-gray-400">{t('payments.noPending', 'Aucune facture en attente de paiement')}</div>
            ) : (
              <table className="w-full text-sm">
                <thead className="bg-gray-50 border-b">
                  <tr>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.reference')}</th>
                    <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.supplier')}</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">{t('invoice.amount')}</th>
                    <th className="text-right px-4 py-3 font-medium text-gray-600">{t('app.actions')}</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {pendingInvoices.map(inv => (
                    <tr key={inv.id} className="hover:bg-gray-50">
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
            {payments && <span className="ml-auto text-xs text-gray-400">{payments.totalElements} {t('payments.total', 'paiements')}</span>}
          </div>

          {remittanceError && (
            <p className="px-5 py-2 text-xs text-amber-700 bg-amber-50 border-b border-amber-100">{remittanceError}</p>
          )}

          {paymentsLoading ? (
            <div className="flex justify-center py-10"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
          ) : paymentList.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 gap-2 text-gray-400">
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
                          {PAYMENT_METHODS.find(m => m.value === p.paymentMethod)?.label ?? p.paymentMethod}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-700">{p.reference}</td>
                      <td className="px-4 py-3 text-right font-medium text-green-700">
                        {Number(p.amountPaid).toLocaleString()} {p.currency}
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">
                        {new Date(p.paymentDate).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          onClick={() => downloadRemittance(p.id)}
                          disabled={remittanceId === p.id}
                          className="flex items-center gap-1 text-xs text-gray-500 hover:text-primary transition-colors ml-auto disabled:opacity-50">
                          {remittanceId === p.id
                            ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
                            : <Download className="w-3.5 h-3.5" />}
                          {t('payments.remittance', 'Avis')}
                        </button>
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
    </PageRoleGuard>
  )
}
