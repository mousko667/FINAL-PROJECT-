import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { invoiceService } from '@/services/invoiceService'
import apiClient from '@/services/apiClient'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { InvoiceTimeline } from '@/components/invoice/InvoiceTimeline'
import { InvoiceActionPanel } from '@/components/invoice/InvoiceActionPanel'
import { BulkDocumentUpload } from '@/components/invoice/BulkDocumentUpload'
import { DocumentViewerModal } from '@/components/invoice/DocumentViewerModal'
import { ExportMenu } from '@/components/ui/ExportMenu'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { INVOICE_VIEW_ROLES } from '@/constants/invoiceRoles'
import { ValidationChecklist } from '@/components/invoice/ValidationChecklist'
import { useAppSelector } from '@/store/hooks'
import { Loader2, ArrowLeft, Download, CheckCircle, XCircle, AlertTriangle, MinusCircle, Clock, User, FileDown, Lock, Eye } from 'lucide-react'
import { formatAmount, formatDate, formatDateTime } from '@/lib/format'

interface ApprovalStep {
  id: string
  stepOrder: number
  stepName: string
  stepNameFr: string
  departmentCode: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  approverUsername?: string
  approverName?: string
  comments?: string
  rejectionReason?: string
  deadline?: string
  actionAt?: string
}

interface MatchingResult {
  id: string
  status: 'MATCHED' | 'PARTIAL' | 'MISMATCH' | 'OVERRIDDEN'
  discrepancyNotes?: string
  overrideReason?: string
  overriddenBy?: string
  createdAt?: string
}

function MatchingBadge({ status }: { status: string }) {
  const { t } = useTranslation()
  const map: Record<string, { icon: React.ReactNode; cls: string; label: string }> = {
    MATCHED:   { icon: <CheckCircle className="w-4 h-4" />, cls: 'bg-pos-bg text-pos', label: t('matching.MATCHED', 'Matched') },
    PARTIAL:   { icon: <MinusCircle className="w-4 h-4" />, cls: 'bg-warn-bg text-warn', label: t('matching.PARTIAL', 'Partial Match') },
    MISMATCH:  { icon: <XCircle className="w-4 h-4" />, cls: 'bg-crit-bg text-crit', label: t('matching.MISMATCH', 'Mismatch') },
    OVERRIDDEN:{ icon: <AlertTriangle className="w-4 h-4" />, cls: 'bg-hot-bg text-hot', label: t('matching.OVERRIDDEN', 'Overridden') },
  }
  const config = map[status] ?? map.PARTIAL
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${config.cls}`}>
      {config.icon}{config.label}
    </span>
  )
}

const SENSITIVITY_STYLES: Record<string, string> = {
  PUBLIC: 'bg-ground text-ink-soft',
  INTERNAL: 'bg-info-bg text-info',
  CONFIDENTIAL: 'bg-crit-bg text-crit',
}

function SensitivityBadge({ level }: { level: string }) {
  const { t } = useTranslation()
  const cls = SENSITIVITY_STYLES[level] ?? SENSITIVITY_STYLES.INTERNAL
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${cls}`}>
      <Lock className="w-3.5 h-3.5" />{t(`sensitivity.${level}`, level)}
    </span>
  )
}

function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const roles = useAppSelector((s) => s.auth.user?.roles ?? [])
  // Matching override is restricted to DAF on the backend (InvoiceController /matching/override
  // is @PreAuthorize hasRole('DAF')). ASSISTANT_COMPTABLE must NOT see this button.
  const canOverride = roles.includes('ROLE_DAF')
  // P11-15: only DAF and Assistant Comptable may reclassify an invoice's data sensitivity.
  const canClassify = roles.includes('ROLE_DAF') || roles.includes('ROLE_ASSISTANT_COMPTABLE')

  const [overrideReason, setOverrideReason] = useState('')
  const [showOverrideForm, setShowOverrideForm] = useState(false)
  const [pdfLoading, setPdfLoading] = useState(false)
  const [viewerDoc, setViewerDoc] = useState<{ fileName: string; fileType: string; downloadUrl?: string } | null>(null)

  const { data: invoice, isLoading, isError } = useQuery({
    queryKey: ['invoice', id],
    queryFn: () => invoiceService.getById(id!),
    enabled: !!id,
  })

  const { data: approvalSteps } = useQuery({
    queryKey: ['approval-steps', id],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ApprovalStep[] }>(`/invoices/${id}/workflow/steps`)
      return data.data ?? []
    },
    enabled: !!id,
    retry: false,
  })

  const { data: matchingResult } = useQuery({
    queryKey: ['matching', id],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: MatchingResult }>(`/invoices/${id}/matching`)
      return data.data
    },
    enabled: !!id && !!invoice?.purchaseOrderId,
    retry: false,
  })

  const sensitivityMutation = useMutation({
    mutationFn: async (level: string) => {
      await apiClient.patch(`/invoices/${id}/sensitivity`, { dataSensitivity: level })
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invoice', id] }),
  })

  const overrideMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post(`/invoices/${id}/matching/override`, { overrideReason })
    },
    onSuccess: () => {
      setShowOverrideForm(false)
      setOverrideReason('')
      queryClient.invalidateQueries({ queryKey: ['matching', id] })
      queryClient.invalidateQueries({ queryKey: ['invoice', id] })
    },
  })

  if (isLoading) return (
    <div className="flex items-center justify-center h-64">
      <Loader2 className="w-8 h-8 animate-spin text-ink-faint" />
    </div>
  )

  if (isError || !invoice) return (
    <div className="text-center py-20">
      <p className="text-crit mb-4">{t('app.error')}</p>
      <button onClick={() => navigate('/invoices')} className="text-gold-deep underline text-sm">{t('app.back')}</button>
    </div>
  )

  return (
    <div className="max-w-5xl mx-auto space-y-6 page-enter">
      {/* Header */}
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            <button onClick={() => navigate('/invoices')} className="p-2 -ml-2 rounded-[4px] hover:bg-white/10 text-white/80 shrink-0">
              <ArrowLeft className="w-5 h-5" />
            </button>
            <span className="num">{invoice.referenceNumber}</span>
          </span>
        }
        subtitle={invoice.supplierName}
        actions={
          <>
            {invoice.dataSensitivity && <SensitivityBadge level={invoice.dataSensitivity} />}
            {invoice.matchingStatus && <MatchingBadge status={invoice.matchingStatus} />}
            <StatusBadge status={invoice.status} className="text-sm px-3 py-1" />
            <button
              onClick={async () => {
                setPdfLoading(true)
                try {
                  const res = await apiClient.get(`/invoices/${id}/export/pdf`, { responseType: 'blob' })
                  const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
                  const a = document.createElement('a')
                  a.href = url
                  a.download = `${invoice.referenceNumber}.pdf`
                  a.click()
                  window.URL.revokeObjectURL(url)
                } finally {
                  setPdfLoading(false)
                }
              }}
              disabled={pdfLoading}
              className="flex items-center gap-1.5 px-3 py-1.5 text-sm border border-white/30 rounded-[4px] hover:bg-white/10 transition-colors disabled:opacity-50 text-white"
              title={t('invoice.exportPdf', 'Exporter en PDF')}
            >
              {pdfLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileDown className="w-4 h-4" />}
              PDF
            </button>
          </>
        }
      />

      <div className="grid grid-cols-3 gap-6">
        {/* Main content */}
        <div className="col-span-2 space-y-6">

          {/* Invoice summary */}
          <Panel title={t('invoice.details')}>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
              {[
                { label: t('invoice.amount'), value: `${formatAmount(invoice.amount)} ${invoice.currency}`, num: true },
                { label: t('invoice.issueDate'), value: invoice.issueDate },
                { label: t('invoice.dueDate'), value: invoice.dueDate },
                { label: t('invoice.department'), value: (i18n.language === 'en' ? invoice.departmentNameEn : invoice.departmentNameFr) ?? invoice.departmentCode ?? '—' },
              ].map(({ label, value, num }) => (
                <div key={label}>
                  <dt className="text-ink-faint">{label}</dt>
                  <dd className={`font-medium text-ink mt-0.5 ${num ? 'num' : ''}`}>{value}</dd>
                </div>
              ))}
              {invoice.purchaseOrderId && (
                <div>
                  <dt className="text-ink-faint">{t('invoice.purchaseOrder', 'Purchase Order')}</dt>
                  <dd className="num text-xs text-ink-soft mt-0.5">{invoice.purchaseOrderId}</dd>
                </div>
              )}
              {invoice.description && (
                <div className="col-span-2">
                  <dt className="text-ink-faint">{t('invoice.description')}</dt>
                  <dd className="mt-0.5 text-ink-soft">{invoice.description}</dd>
                </div>
              )}
            </dl>
          </Panel>

          {/* Data Sensitivity Classification (P11-15) */}
          <Panel>
            <div className="flex items-center justify-between gap-4 p-5">
              <div>
                <h2 className="font-semibold text-ink">{t('sensitivity.title', 'Data Sensitivity')}</h2>
                <p className="text-sm text-ink-soft mt-0.5">{t('sensitivity.subtitle', 'Confidentiality level of this financial record.')}</p>
              </div>
              {canClassify ? (
                <select
                  value={invoice.dataSensitivity ?? 'INTERNAL'}
                  onChange={(e) => sensitivityMutation.mutate(e.target.value)}
                  disabled={sensitivityMutation.isPending}
                  className="border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-gold-deep/30 disabled:opacity-50"
                >
                  <option value="PUBLIC">{t('sensitivity.PUBLIC', 'Public')}</option>
                  <option value="INTERNAL">{t('sensitivity.INTERNAL', 'Internal')}</option>
                  <option value="CONFIDENTIAL">{t('sensitivity.CONFIDENTIAL', 'Confidential')}</option>
                </select>
              ) : (
                <SensitivityBadge level={invoice.dataSensitivity ?? 'INTERNAL'} />
              )}
            </div>
          </Panel>

          {/* Three-Way Matching Result */}
          {invoice.purchaseOrderId && (
            <Panel>
              <div className="p-5">
              <div className="flex items-center justify-between mb-4">
                <h2 className="font-semibold text-ink">{t('matching.title', 'Three-Way Matching')}</h2>
                <div className="flex items-center gap-3">
                  {matchingResult && <MatchingBadge status={matchingResult.status} />}
                  {matchingResult && (
                    <ExportMenu
                      endpoint={`/invoices/${id}/matching/export`}
                      filename={`matching_report_${invoice.referenceNumber ?? id}`}
                      label={t('matching.exportReport', 'Export report')}
                    />
                  )}
                </div>
              </div>

              {!matchingResult ? (
                <p className="text-sm text-ink-faint">{t('matching.pending', 'Matching will be performed on submission.')}</p>
              ) : (
                <div className="space-y-3">
                  {matchingResult.discrepancyNotes && (
                    <div className="text-sm bg-ground rounded-[4px] p-3 border border-hairline">
                      <p className="font-medium text-ink-soft mb-1">{t('matching.notes', 'Matching details:')}</p>
                      <p className="text-ink-soft text-xs whitespace-pre-wrap">{matchingResult.discrepancyNotes}</p>
                    </div>
                  )}

                  {matchingResult.status === 'OVERRIDDEN' && matchingResult.overrideReason && (
                    <div className="text-sm bg-hot-bg rounded-[4px] p-3 border border-hot/30">
                      <p className="font-medium text-hot mb-1">{t('matching.overrideJustification', 'Override justification:')}</p>
                      <p className="text-hot text-xs">{matchingResult.overrideReason}</p>
                    </div>
                  )}

                  {/* Override form — only for MISMATCH, only for authorised roles, only when invoice is in BROUILLON/REJETE */}
                  {matchingResult.status === 'MISMATCH' && canOverride && ['BROUILLON', 'REJETE'].includes(invoice.status) && (
                    <div>
                      {!showOverrideForm ? (
                        <button
                          onClick={() => setShowOverrideForm(true)}
                          className="text-sm text-hot hover:opacity-80 font-medium underline"
                        >
                          {t('matching.override', 'Override mismatch with justification')}
                        </button>
                      ) : (
                        <div className="space-y-2">
                          <label htmlFor="overrideReason" className="block text-sm font-medium text-ink-soft">
                            {t('matching.overrideReason', 'Override justification')} * ({t('matching.minChars', 'min. 10 characters')})
                          </label>
                          <textarea
                            id="overrideReason"
                            value={overrideReason}
                            onChange={(e) => setOverrideReason(e.target.value)}
                            rows={3}
                            className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-hot/30"
                            placeholder={t('matching.overridePlaceholder', 'Explain why this discrepancy is acceptable...')}
                          />
                          <div className="flex gap-2">
                            <button
                              onClick={() => overrideMutation.mutate()}
                              disabled={overrideReason.length < 10 || overrideMutation.isPending}
                              className="px-3 py-1.5 bg-hot text-white rounded-[4px] text-sm font-medium disabled:opacity-50 hover:opacity-90"
                            >
                              {overrideMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin inline" /> : t('matching.confirmOverride', 'Confirm Override')}
                            </button>
                            <button onClick={() => setShowOverrideForm(false)} className="px-3 py-1.5 border border-hairline rounded-[4px] text-sm hover:bg-ground text-ink-soft">
                              {t('app.cancel')}
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
              </div>
            </Panel>
          )}

          {/* Validation checklist (B1) — renders only when a template applies to this invoice */}
          {id && <ValidationChecklist invoiceId={id} />}

          {/* Approval Steps — vertical timeline */}
          {approvalSteps && approvalSteps.length > 0 && (
            <Panel title={t('invoice.approvalSteps', 'Historique d\'approbation')}>
              <div className="relative">
                {/* Vertical connector line */}
                <div className="absolute left-4 top-5 bottom-5 w-px bg-hairline" />
                <div className="space-y-0">
                  {approvalSteps.map((step, idx) => {
                    const isApproved = step.status === 'APPROVED'
                    const isRejected = step.status === 'REJECTED'
                    const isPending  = step.status === 'PENDING'
                    const isLast     = idx === approvalSteps.length - 1

                    const dotClass = isApproved
                      ? 'bg-pos border-pos text-white'
                      : isRejected
                      ? 'bg-crit border-crit text-white'
                      : 'bg-surface border-hairline-strong text-ink-faint'

                    const lineClass = isApproved && !isLast ? 'bg-pos/50' : 'bg-hairline'

                    return (
                      <div key={step.id} className="flex gap-4 relative pb-6 last:pb-0">
                        {/* Dot */}
                        <div className="relative z-10 flex-shrink-0">
                          <div className={`w-8 h-8 rounded-full border-2 flex items-center justify-center text-xs font-bold ${dotClass}`}>
                            {isApproved ? <CheckCircle className="w-4 h-4" /> : isRejected ? <XCircle className="w-4 h-4" /> : <Clock className="w-4 h-4" />}
                          </div>
                          {!isLast && (
                            <div className={`absolute left-1/2 top-8 w-px flex-1 h-full -translate-x-1/2 ${lineClass}`} />
                          )}
                        </div>

                        {/* Content */}
                        <div className={`flex-1 min-w-0 rounded-[4px] border px-4 py-3 mb-0 ${
                          isApproved ? 'border-pos/30 bg-pos-bg/50' :
                          isRejected ? 'border-crit/30 bg-crit-bg/50' :
                          'border-hairline bg-ground/50'
                        }`}>
                          <div className="flex items-center justify-between flex-wrap gap-2 mb-1">
                            <div className="flex items-center gap-2">
                              <span className="font-semibold text-sm text-ink">
                                {step.stepNameFr ?? step.stepName}
                              </span>
                              {step.departmentCode && (
                                <span className="num text-[10px] bg-ground text-ink-soft px-1.5 py-0.5 rounded border border-hairline">
                                  {step.departmentCode}
                                </span>
                              )}
                              <span className={`text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-full ${
                                isApproved ? 'bg-pos-bg text-pos' :
                                isRejected ? 'bg-crit-bg text-crit' :
                                'bg-warn-bg text-warn'
                              }`}>
                                {isApproved ? t('invoice.stepApproved', 'Approuvé') : isRejected ? t('invoice.stepRejected', 'Rejeté') : t('invoice.stepPending', 'En attente')}
                              </span>
                            </div>
                            {step.actionAt && (
                              <span className="text-xs text-ink-faint shrink-0">
                                {formatDateTime(step.actionAt)}
                              </span>
                            )}
                          </div>

                          {step.approverName && (
                            <div className="flex items-center gap-1.5 text-xs text-ink-faint mb-1">
                              <User className="w-3 h-3" />
                              <span>{step.approverName}{step.approverUsername ? ` (${step.approverUsername})` : ''}</span>
                            </div>
                          )}

                          {isPending && step.deadline && (
                            <div className="flex items-center gap-1.5 text-xs text-warn mt-1">
                              <Clock className="w-3 h-3" />
                              {t('invoice.deadline', 'Délai')}: {formatDate(step.deadline)}
                              {new Date(step.deadline) < new Date() && (
                                <span className="text-crit font-semibold ml-1">— {t('invoice.overdue', 'DÉPASSÉ')}</span>
                              )}
                            </div>
                          )}

                          {step.comments && (
                            <p className="text-xs text-ink-soft italic mt-1.5 border-l-2 border-hairline-strong pl-2">
                              "{step.comments}"
                            </p>
                          )}

                          {step.rejectionReason && (
                            <div className="mt-1.5 text-xs text-crit bg-crit-bg border border-crit/30 rounded px-2 py-1.5">
                              <span className="font-semibold">{t('invoice.rejectionReason', 'Motif du rejet')} : </span>
                              {step.rejectionReason}
                            </div>
                          )}
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            </Panel>
          )}

          {/* Empty approval steps — show placeholder when invoice is past SOUMIS but no steps yet */}
          {(!approvalSteps || approvalSteps.length === 0) && !['BROUILLON', 'SOUMIS'].includes(invoice.status) && (
            <Panel title={t('invoice.approvalSteps', 'Historique d\'approbation')}>
              <p className="text-sm text-ink-faint">{t('invoice.noStepsYet', 'Aucune étape d\'approbation enregistrée.')}</p>
            </Panel>
          )}

          {/* Line Items */}
          {invoice.lineItems && invoice.lineItems.length > 0 && (
            <Panel className="overflow-x-auto" title={t('invoice.lineItems')}>
              <div className="-m-5">
              <table className="w-full text-sm">
                <thead className="bg-ground">
                  <tr>
                    <th className="text-left px-5 py-2 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.description')}</th>
                    <th className="text-right px-5 py-2 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.quantity', 'Qty')}</th>
                    <th className="text-right px-5 py-2 text-xs font-medium uppercase tracking-wide text-ink-faint">{t('invoice.unitPrice', 'Unit Price')}</th>
                    <th className="text-right px-5 py-2 text-xs font-medium uppercase tracking-wide text-ink-faint">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-hairline">
                  {invoice.lineItems.map((li, i) => (
                    <tr key={i}>
                      <td className="px-5 py-2 text-ink">{li.description}</td>
                      <td className="num px-5 py-2 text-right text-ink-soft">{li.quantity}</td>
                      <td className="num px-5 py-2 text-right text-ink-soft">{Number(li.unitPrice).toFixed(2)}</td>
                      <td className="num px-5 py-2 text-right font-medium text-ink">{Number(li.totalPrice).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              </div>
            </Panel>
          )}

          {/* Documents */}
          {invoice.documents && invoice.documents.length > 0 && (
            <Panel title={t('invoice.documents')}>
              <ul className="space-y-2">
                {invoice.documents.map((doc) => (
                  <li key={doc.id} className="flex items-center justify-between text-sm px-3 py-2.5 bg-ground rounded-[4px] border border-hairline">
                    <span className="font-medium text-ink-soft truncate">
                      {doc.fileName}
                      {doc.version && doc.version > 1 && (
                        <span className="num ml-2 text-xs bg-info-bg text-info px-1.5 py-0.5 rounded">v{doc.version}</span>
                      )}
                    </span>
                    <div className="flex items-center gap-3 ml-2 shrink-0">
                      <span className="text-xs text-ink-faint">{(doc.fileSize / 1024).toFixed(1)} KB</span>
                      {doc.downloadUrl && (
                        <button onClick={() => setViewerDoc(doc)} className="text-ink-faint hover:text-gold-deep" title={t('invoice.viewer.view', 'Aperçu')}>
                          <Eye className="w-4 h-4" />
                        </button>
                      )}
                      {doc.downloadUrl && (
                        <a href={doc.downloadUrl} download aria-label={t('invoice.viewer.download', 'Download')} className="text-gold-deep hover:opacity-80">
                          <Download className="w-4 h-4" />
                        </a>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </Panel>
          )}

          {/* P11-48: bulk multi-file document upload (Assistant Comptable only) */}
          {roles.includes('ROLE_ASSISTANT_COMPTABLE') && id && (
            <BulkDocumentUpload invoiceId={id} />
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          <InvoiceActionPanel invoice={invoice} />
          <InvoiceTimeline invoice={invoice} />
        </div>
      </div>

      {/* M9: in-app document viewer */}
      {viewerDoc?.downloadUrl && (
        <DocumentViewerModal
          url={viewerDoc.downloadUrl}
          filename={viewerDoc.fileName}
          fileType={viewerDoc.fileType}
          onClose={() => setViewerDoc(null)}
        />
      )}
    </div>
  )
}

/**
 * AUDIT-003: same guard as InvoiceListPage. Without it, a refused role (ADMIN) got
 * the generic "Une erreur est survenue" screen off the backend 403 instead of the
 * explicit "Acces non autorise" the other 18 financial pages show.
 */
export default function InvoiceDetailPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={INVOICE_VIEW_ROLES}>
      <InvoiceDetailPage />
    </PageRoleGuard>
  )
}
