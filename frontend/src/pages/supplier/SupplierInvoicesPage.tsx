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
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import { KpiBand, type KpiBandItem } from '@/components/ui/KpiBand'

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

// Registre semantic mapping — mirrors StatusBadge (5 semantics + 2 neutrals).
// N1=warn / N2=hot kept distinct; only the icon + left-stamp are page-specific.
const STATUS_CONFIG: Record<string, { bg: string; text: string; border: string; icon: typeof CheckCircle }> = {
  BROUILLON:        { bg: 'bg-ground',   text: 'text-ink-soft', border: 'border-l-hairline-strong', icon: FileText },
  SOUMIS:           { bg: 'bg-info-bg',  text: 'text-info',     border: 'border-l-info',            icon: Clock },
  EN_VALIDATION_N1: { bg: 'bg-warn-bg',  text: 'text-warn',     border: 'border-l-warn',            icon: Clock },
  EN_VALIDATION_N2: { bg: 'bg-hot-bg',   text: 'text-hot',      border: 'border-l-hot',             icon: Clock },
  VALIDE:           { bg: 'bg-pos-bg',   text: 'text-pos',      border: 'border-l-pos',             icon: CheckCircle },
  BON_A_PAYER:      { bg: 'bg-pos-bg',   text: 'text-pos',      border: 'border-l-pos',             icon: CheckCircle },
  PAYE:             { bg: 'bg-pos-bg',   text: 'text-pos',      border: 'border-l-pos',             icon: CheckCircle },
  ARCHIVE:          { bg: 'bg-ground',   text: 'text-ink-faint',border: 'border-l-hairline-strong', icon: FileText },
  REJETE:           { bg: 'bg-crit-bg',  text: 'text-crit',     border: 'border-l-crit',            icon: XCircle },
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
      <div className="flex items-center gap-2 text-xs text-ink-faint">
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
          ? 'bg-crit border-crit'
          : done
          ? 'bg-pos border-pos'
          : 'bg-surface border-hairline-strong'

        const textColor = isRejected && i <= 1
          ? 'text-crit'
          : done
          ? 'text-pos'
          : 'text-ink-faint'

        const lineColor = !isRejected && step > i ? 'bg-pos' : 'bg-hairline'

        return (
          <div key={s.key} className="flex items-center flex-1 last:flex-none">
            <div className="flex flex-col items-center gap-1 min-w-0">
              <div className={`w-6 h-6 rounded-full border-2 flex items-center justify-center text-pos-bg text-xs font-bold shrink-0 ${dotColor}`}>
                {rejectedHere ? '✕' : done ? '✓' : i + 1}
              </div>
              <span className={`text-[9px] font-semibold uppercase tracking-wide whitespace-nowrap ${textColor}`}>
                {rejectedHere ? t('status.REJETE', 'Rejeté') : s.label}
                {isCurrent && !isRejected && <span className="ml-0.5 text-warn">●</span>}
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
          className="flex items-center justify-center w-9 h-9 border border-hairline rounded-[4px] hover:bg-ground text-ink-soft transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>
        <div>
          <p className="text-xs font-semibold text-primary uppercase tracking-wider">{t('supplier.tracking.title', 'Suivi de facture')}</p>
          <h1 className="text-xl font-bold text-ink num">{invoice.referenceNumber}</h1>
          {invoice.description && <p className="text-sm text-ink-soft mt-0.5">{invoice.description}</p>}
        </div>
        <div className="ml-auto"><StatusPill status={invoice.status} /></div>
      </div>

      {/* Rejection banner */}
      {isRejected && invoice.rejectionReason && (
        <div className="flex items-start gap-3 bg-crit-bg border border-crit/30 border-l-4 border-l-crit rounded-[4px] p-4">
          <AlertCircle className="w-5 h-5 text-crit shrink-0 mt-0.5" />
          <div>
            <p className="text-xs font-bold uppercase tracking-wide text-crit mb-1">
              {t('supplier.tracking.rejectionReason', 'Motif du rejet')}
            </p>
            <p className="text-sm text-crit font-medium">{invoice.rejectionReason}</p>
          </div>
        </div>
      )}

      {/* Timeline */}
      <Panel className="p-5">
        <h2 className="text-sm font-semibold text-ink mb-5">{t('supplier.tracking.progress', 'Progression de la validation')}</h2>
        <ApprovalTimeline status={invoice.status} />
      </Panel>

      {/* Meta grid */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        {[
          { icon: DollarSign, label: t('invoice.amount', 'Montant'), value: `${formatAmount(invoice.amount)} ${invoice.currency}`, mono: true },
          { icon: Building2,  label: t('invoice.department', 'Département'), value: invoice.departmentCode ?? '—', mono: false },
          { icon: Calendar,   label: t('invoice.issueDate', 'Date d\'émission'), value: formatDate(invoice.issueDate), mono: true },
          { icon: Calendar,   label: t('invoice.dueDate', 'Date d\'échéance'), value: formatDate(invoice.dueDate), mono: true },
        ].map(({ icon: Icon, label, value, mono }) => (
          <Panel key={label} className="p-4">
            <div className="flex items-center gap-1.5 text-ink-faint mb-1">
              <Icon className="w-3.5 h-3.5" />
              <p className="text-[10px] font-bold uppercase tracking-wide">{label}</p>
            </div>
            <p className={`text-sm font-bold text-ink ${mono ? 'num' : ''}`}>{value}</p>
          </Panel>
        ))}
      </div>

      {/* Action row */}
      <div className="flex flex-wrap gap-3">
        <Link
          to="/supplier/invoices/new"
          className="flex items-center gap-2 px-4 py-2.5 bg-primary text-primary-foreground text-sm font-medium rounded-[4px] hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          {t('supplier.invoice.submit.title', 'Soumettre une nouvelle facture')}
        </Link>
      </div>

      {/* Resubmit hint for rejected */}
      {isRejected && (
        <div className="bg-warn-bg border border-warn/30 rounded-[4px] p-4 flex items-start gap-3">
          <RefreshCw className="w-5 h-5 text-warn shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-warn">
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

  const kpiItems: KpiBandItem[] = [
    { label: t('supplier.portal.submitted', 'Soumises'),  value: stats.total,    tone: 'info' },
    { label: t('supplier.portal.pending',   'En attente'), value: stats.pending,  tone: 'warn' },
    { label: t('supplier.portal.approved',  'Approuvées'), value: stats.approved, tone: 'pos' },
    { label: t('supplier.portal.paid',      'Payées'),     value: stats.paid,     tone: 'pos' },
  ]

  return (
    <div className="space-y-5">
      {/* Header */}
      <PageHeader
        title={t('supplier.portal.invoices', 'Mes Factures')}
        subtitle={t('supplier.tracking.subtitle', 'Suivez le statut de vos factures en temps réel')}
        actions={
          <Link
            to="/supplier/invoices/new"
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('supplier.invoice.submit.title', 'Soumettre une facture')}
          </Link>
        }
      />

      {/* KPI band */}
      <KpiBand items={kpiItems} className="grid grid-cols-2 md:grid-cols-4" />

      {/* Filter */}
      <Panel className="p-4 flex items-center gap-3">
        <Filter className="w-4 h-4 text-ink-faint shrink-0" />
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0) }}
          className="text-sm border border-hairline rounded-[4px] px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-primary/20 bg-surface text-ink"
        >
          <option value="">{t('app.allStatus', 'Tous les statuts')}</option>
          {(['BROUILLON','SOUMIS','EN_VALIDATION_N1','EN_VALIDATION_N2','VALIDE','BON_A_PAYER','PAYE','REJETE','ARCHIVE'] as InvoiceStatus[]).map(s => (
            <option key={s} value={s}>{t(`status.${s}`, s)}</option>
          ))}
        </select>
      </Panel>

      {/* Table */}
      <Panel className="overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-primary" /></div>
        ) : invoices.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3 text-ink-faint">
            <FileText className="w-10 h-10" />
            <p className="text-sm font-medium">{t('app.noData')}</p>
            <Link to="/supplier/invoices/new" className="text-sm text-primary hover:underline font-medium">
              {t('supplier.invoice.submit.title', 'Soumettre votre première facture →')}
            </Link>
          </div>
        ) : (
          <>
            <table className="w-full text-sm">
              <thead className="bg-ground">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('invoice.reference')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('invoice.amount')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide">{t('invoice.status')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide hidden md:table-cell">{t('supplier.tracking.progress', 'Progression')}</th>
                  <th className="text-left px-4 py-3 font-medium text-ink-faint text-xs uppercase tracking-wide hidden md:table-cell">{t('invoice.dueDate')}</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {invoices.map((inv) => (
                  <tr
                    key={inv.id}
                    className={`cursor-pointer transition-colors ${inv.status === 'REJETE' ? 'bg-crit-bg/40' : ''} hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]`}
                    onClick={() => setSelectedInvoice(inv)}
                  >
                    <td className="px-4 py-3 num text-xs font-semibold text-ink">{inv.referenceNumber}</td>
                    <td className="px-4 py-3 num font-medium text-ink-soft text-right">{formatAmount(inv.amount)} <span className="text-ink-faint">{inv.currency}</span></td>
                    <td className="px-4 py-3"><StatusPill status={inv.status} /></td>
                    <td className="px-4 py-3 hidden md:table-cell w-48">
                      <ApprovalTimeline status={inv.status} />
                    </td>
                    <td className="px-4 py-3 text-ink-soft text-xs hidden md:table-cell num">
                      {formatDate(inv.dueDate)}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <ChevronRight className="w-4 h-4 text-ink-faint ml-auto" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {data && data.totalPages > 1 && (
              <div className="flex items-center justify-between px-4 py-3 border-t border-hairline bg-ground">
                <span className="text-sm text-ink-soft">{t('pagination.page')} <span className="num">{page + 1} / {data.totalPages}</span></span>
                <div className="flex gap-2">
                  <button disabled={page === 0} onClick={() => setPage(p => p - 1)} className="px-3 py-1.5 bg-surface border border-hairline rounded-[4px] text-sm disabled:opacity-40 hover:bg-ground">{t('app.previous')}</button>
                  <button disabled={page >= data.totalPages - 1} onClick={() => setPage(p => p + 1)} className="px-3 py-1.5 bg-surface border border-hairline rounded-[4px] text-sm disabled:opacity-40 hover:bg-ground">{t('app.next')}</button>
                </div>
              </div>
            )}
          </>
        )}
      </Panel>
    </div>
  )
}
