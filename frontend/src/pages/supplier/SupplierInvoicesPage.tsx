import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import type { InvoiceStatus } from '@/types/invoice'
import {
  Loader2, Plus, Filter, ArrowLeft, Calendar, DollarSign,
  FileText, CheckCircle, Clock, XCircle, AlertCircle,
  RefreshCw, ChevronRight, Building2,
} from 'lucide-react'
import { formatAmount, formatDate } from '@/lib/format'

interface SupplierInvoice {
  id: string
  referenceNumber: string
  amount: number
  currency: string
  status: InvoiceStatus
  issueDate: string
  dueDate: string
  description?: string
  departmentCode?: string
  rejectionReason?: string
  createdAt: string
  updatedAt: string
}

// Maps invoice status to timeline step index (0=Submitted, 1=Validation, 2=Approved, 3=Paid)
function statusToStep(status: InvoiceStatus): number {
  switch (status) {
    case 'BROUILLON': return -1
    case 'SOUMIS': return 0
    case 'EN_VALIDATION_N1': return 1
    case 'EN_VALIDATION_N2': return 1
    case 'VALIDE': return 2
    case 'BON_A_PAYER': return 2
    case 'PAYE': return 3
    case 'ARCHIVE': return 3
    case 'REJETE': return -2
    default: return 0
  }
}

const STATUS_CONFIG: Record<string, { bg: string; text: string; border: string; icon: typeof CheckCircle }> = {
  BROUILLON:        { bg: 'bg-gray-50',   text: 'text-gray-700',  border: 'border-gray-300',  icon: FileText },
  SOUMIS:           { bg: 'bg-blue-50',   text: 'text-blue-700',  border: 'border-blue-300',  icon: Clock },
  EN_VALIDATION_N1: { bg: 'bg-amber-50',  text: 'text-amber-700', border: 'border-amber-400', icon: Clock },
  EN_VALIDATION_N2: { bg: 'bg-orange-50', text: 'text-orange-700',border: 'border-orange-400',icon: Clock },
  VALIDE:           { bg: 'bg-teal-50',   text: 'text-teal-700',  border: 'border-teal-400',  icon: CheckCircle },
  BON_A_PAYER:      { bg: 'bg-green-50',  text: 'text-green-700', border: 'border-green-400', icon: CheckCircle },
  PAYE:             { bg: 'bg-green-50',  text: 'text-green-800', border: 'border-green-500', icon: CheckCircle },
  ARCHIVE:          { bg: 'bg-slate-50',  text: 'text-slate-600', border: 'border-slate-400', icon: FileText },
  REJETE:           { bg: 'bg-red-50',    text: 'text-red-700',   border: 'border-red-400',   icon: XCircle },
}

function StatusPill({ status }: { status: InvoiceStatus }) {
  const { t } = useTranslation()
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG['BROUILLON']
  const Icon = cfg.icon
  return (
    <span className={`inline-flex items-center gap-1.5 pl-2 pr-3 py-1 text-xs font-semibold border-l-4 ${cfg.bg} ${cfg.text} ${cfg.border}`}>
      <Icon className="w-3.5 h-3.5" />
      {t(`status.${status}`, status)}
    </span>
  )
}

function ApprovalTimeline({ status }: { status: InvoiceStatus }) {
  const { t } = useTranslation()
  const step = statusToStep(status)
  const isRejected = status === 'REJETE'
  const isDraft = status === 'BROUILLON'

  const steps = [
    { key: 'submitted',   label: t('supplier.tracking.step.submitted',   'Soumis') },
    { key: 'validation',  label: t('supplier.tracking.step.validation',  'Validation') },
    { key: 'approved',    label: t('supplier.tracking.step.approved',    'Approuvé') },
    { key: 'paid',        label: t('supplier.tracking.step.paid',        'Payé') },
  ]

  if (isDraft) {
    return (
      <div className="flex items-center gap-2 text-xs text-gray-400">
        <FileText className="w-3.5 h-3.5" />
        {t('supplier.tracking.draft', 'Brouillon — non encore soumis')}
      </div>
    )
  }

  return (
    <div className="flex items-center gap-0 w-full">
      {steps.map((s, i) => {
        const done = !isRejected && step >= i
        const isCurrent = !isRejected && step === i
        const rejectedHere = isRejected && i === 1

        const dotColor = isRejected && i <= 1
          ? 'bg-red-500 border-red-500'
          : done
          ? 'bg-green-500 border-green-500'
          : 'bg-white border-gray-300'

        const textColor = isRejected && i <= 1
          ? 'text-red-600'
          : done
          ? 'text-green-700'
          : 'text-gray-400'

        const lineColor = !isRejected && step > i ? 'bg-green-500' : 'bg-gray-200'

        return (
          <div key={s.key} className="flex items-center flex-1 last:flex-none">
            <div className="flex flex-col items-center gap-1 min-w-0">
              <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center text-white text-xs font-bold shrink-0 ${dotColor}`}>
                {rejectedHere ? '✕' : done ? '✓' : i + 1}
              </div>
              <span className={`text-[9px] font-semibold uppercase tracking-wide whitespace-nowrap ${textColor}`}>
                {rejectedHere ? t('status.REJETE', 'Rejeté') : s.label}
                {isCurrent && !isRejected && <span className="ml-0.5 text-amber-500">●</span>}
              </span>
            </div>
            {i < steps.length - 1 && (
              <div className={`flex-1 h-0.5 mx-1 mb-4 ${lineColor}`} />
            )}
          </div>
        )
      })}
    </div>
  )
}

function InvoiceDetailView({ invoice, onBack }: { invoice: SupplierInvoice; onBack: () => void }) {
  const { t } = useTranslation()
  const isRejected = invoice.status === 'REJETE'

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center gap-3">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-9 h-9 border rounded-lg hover:bg-gray-50 text-gray-600 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <p className="text-xs font-semibold text-primary uppercase tracking-wider">{t('supplier.tracking.title', 'Suivi de facture')}</p>
          <h1 className="text-xl font-bold text-gray-900">{invoice.referenceNumber}</h1>
          {invoice.description && <p className="text-sm text-gray-500 mt-0.5">{invoice.description}</p>}
        </div>
        <div className="ml-auto"><StatusPill status={invoice.status} /></div>
      </div>

      {/* Rejection banner */}
      {isRejected && invoice.rejectionReason && (
        <div className="flex items-start gap-3 bg-red-50 border border-red-200 border-l-4 border-l-red-500 rounded-lg p-4">
          <AlertCircle className="w-5 h-5 text-red-600 shrink-0 mt-0.5" />
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-red-700 mb-1">
              {t('supplier.tracking.rejectionReason', 'Motif du rejet')}
            </p>
            <p className="text-sm text-red-800 font-medium">{invoice.rejectionReason}</p>
          </div>
        </div>
      )}

      {/* Timeline */}
      <div className="bg-white rounded-xl border p-5">
        <h2 className="text-sm font-semibold text-gray-800 mb-5">{t('supplier.tracking.progress', 'Progression de la validation')}</h2>
        <ApprovalTimeline status={invoice.status} />
      </div>

      {/* Meta grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { icon: DollarSign, label: t('invoice.amount', 'Montant'), value: `${formatAmount(invoice.amount)} ${invoice.currency}` },
          { icon: Building2,  label: t('invoice.department', 'Département'), value: invoice.departmentCode ?? '—' },
          { icon: Calendar,   label: t('invoice.issueDate', 'Date d\'émission'), value: formatDate(invoice.issueDate) },
          { icon: Calendar,   label: t('invoice.dueDate', 'Date d\'échéance'), value: formatDate(invoice.dueDate) },
        ].map(({ icon: Icon, label, value }) => (
          <div key={label} className="bg-white rounded-xl border p-4">
            <div className="flex items-center gap-1.5 text-gray-400 mb-1">
              <Icon className="w-3.5 h-3.5" />
              <p className="text-[10px] font-bold uppercase tracking-wide">{label}</p>
            </div>
            <p className="text-sm font-bold text-gray-900">{value}</p>
          </div>
        ))}
      </div>

      {/* Action row */}
      <div className="flex flex-wrap gap-3">
        <Link
          to="/supplier/invoices/new"
          className="flex items-center gap-2 px-4 py-2.5 bg-oct-navy text-white text-sm font-medium rounded-lg hover:bg-oct-navy-light transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('supplier.invoice.submit.title', 'Soumettre une nouvelle facture')}
        </Link>
      </div>

      {/* Resubmit hint for rejected */}
      {isRejected && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start gap-3">
          <RefreshCw className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-amber-800">
              {t('invoice.resubmitHint', 'Cette facture a été rejetée. Soumettez une nouvelle facture corrigée via le bouton ci-dessus.')}
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

export default function SupplierInvoicesPage() {
  const { t } = useTranslation()
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [selectedInvoice, setSelectedInvoice] = useState<SupplierInvoice | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['supplier-invoices', statusFilter, page],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: SupplierInvoice[]; totalPages: number; totalElements: number } }>(
        '/supplier/invoices',
        { params: { page, size: 10, status: statusFilter || undefined } }
      )
      return data.data
    },
  })

  if (selectedInvoice) {
    return <InvoiceDetailView invoice={selectedInvoice} onBack={() => setSelectedInvoice(null)} />
  }

  const invoices = data?.content ?? []
  const stats = {
    total: data?.totalElements ?? 0,
    pending: invoices.filter(i => ['SOUMIS','EN_VALIDATION_N1','EN_VALIDATION_N2'].includes(i.status)).length,
    approved: invoices.filter(i => ['VALIDE','BON_A_PAYER'].includes(i.status)).length,
    paid: invoices.filter(i => ['PAYE','ARCHIVE'].includes(i.status)).length,
    rejected: invoices.filter(i => i.status === 'REJETE').length,
  }

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('supplier.portal.invoices', 'Mes Factures')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('supplier.tracking.subtitle', 'Suivez le statut de vos factures en temps réel')}</p>
        </div>
        <Link
          to="/supplier/invoices/new"
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('supplier.invoice.submit.title', 'Soumettre une facture')}
        </Link>
      </div>

      {/* KPI cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { label: t('supplier.portal.submitted', 'Soumises'),  value: stats.total,    color: 'border-t-primary text-primary' },
          { label: t('supplier.portal.pending',   'En attente'), value: stats.pending,  color: 'border-t-amber-500 text-amber-600' },
          { label: t('supplier.portal.approved',  'Approuvées'), value: stats.approved, color: 'border-t-teal-500 text-teal-600' },
          { label: t('supplier.portal.paid',      'Payées'),     value: stats.paid,     color: 'border-t-green-600 text-green-700' },
        ].map(({ label, value, color }) => (
          <div key={label} className={`bg-white rounded-xl border border-t-4 ${color.split(' ')[0]} p-4`}>
            <p className={`text-2xl font-black ${color.split(' ')[1]}`}>{value}</p>
            <p className="text-xs font-medium text-gray-500 mt-0.5 uppercase tracking-wide">{label}</p>
          </div>
        ))}
      </div>

      {/* Filter */}
      <div className="bg-white rounded-xl border p-4 flex items-center gap-3">
        <Filter className="w-4 h-4 text-gray-400 shrink-0" />
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
          className="text-sm border rounded-lg px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/20 bg-white"
        >
          <option value="">{t('app.allStatus', 'Tous les statuts')}</option>
          {(['BROUILLON','SOUMIS','EN_VALIDATION_N1','EN_VALIDATION_N2','VALIDE','BON_A_PAYER','PAYE','REJETE','ARCHIVE'] as InvoiceStatus[]).map(s => (
            <option key={s} value={s}>{t(`status.${s}`, s)}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
        ) : invoices.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-gray-400">
            <FileText className="w-10 h-10" />
            <p className="text-sm font-medium">{t('app.noData')}</p>
            <Link to="/supplier/invoices/new" className="text-sm text-primary hover:underline font-medium">
              {t('supplier.invoice.submit.title', 'Soumettre votre première facture →')}
            </Link>
          </div>
        ) : (
          <>
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.reference')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.amount')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('invoice.status')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600 hidden md:table-cell">{t('supplier.tracking.progress', 'Progression')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600 hidden md:table-cell">{t('invoice.dueDate')}</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y">
                {invoices.map((inv) => (
                  <tr
                    key={inv.id}
                    className={`hover:bg-gray-50 cursor-pointer transition-colors ${inv.status === 'REJETE' ? 'bg-red-50/50' : ''}`}
                    onClick={() => setSelectedInvoice(inv)}
                  >
                    <td className="px-4 py-3 font-mono text-xs font-semibold text-gray-900">{inv.referenceNumber}</td>
                    <td className="px-4 py-3 font-medium text-gray-700">{formatAmount(inv.amount)} {inv.currency}</td>
                    <td className="px-4 py-3"><StatusPill status={inv.status} /></td>
                    <td className="px-4 py-3 hidden md:table-cell w-48">
                      <ApprovalTimeline status={inv.status} />
                    </td>
                    <td className="px-4 py-3 text-gray-500 text-xs hidden md:table-cell">
                      {formatDate(inv.dueDate)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <ChevronRight className="w-4 h-4 text-gray-400 ml-auto" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {data && data.totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t bg-gray-50">
                <span className="text-sm text-gray-500">{t('pagination.page')} {page + 1} / {data.totalPages}</span>
                <div className="flex gap-2">
                  <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1.5 bg-white border rounded-lg text-sm disabled:opacity-40 hover:bg-gray-50">{t('app.previous')}</button>
                  <button disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1.5 bg-white border rounded-lg text-sm disabled:opacity-40 hover:bg-gray-50">{t('app.next')}</button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
