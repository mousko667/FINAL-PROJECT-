import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { invoiceService } from '@/services/invoiceService'
import apiClient from '@/services/apiClient'
import { StatusBadge } from '@/components/ui/StatusBadge'
import { InvoiceTimeline } from '@/components/invoice/InvoiceTimeline'
import { InvoiceActionPanel } from '@/components/invoice/InvoiceActionPanel'
import { useAppSelector } from '@/store/hooks'
import { Loader2, ArrowLeft, Download, CheckCircle, XCircle, AlertTriangle, MinusCircle, Clock, User, FileDown, Lock } from 'lucide-react'

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
    MATCHED:   { icon: <CheckCircle className="w-4 h-4" />, cls: 'bg-green-100 text-green-800', label: t('matching.MATCHED', 'Matched') },
    PARTIAL:   { icon: <MinusCircle className="w-4 h-4" />, cls: 'bg-yellow-100 text-yellow-800', label: t('matching.PARTIAL', 'Partial Match') },
    MISMATCH:  { icon: <XCircle className="w-4 h-4" />, cls: 'bg-red-100 text-red-800', label: t('matching.MISMATCH', 'Mismatch') },
    OVERRIDDEN:{ icon: <AlertTriangle className="w-4 h-4" />, cls: 'bg-orange-100 text-orange-800', label: t('matching.OVERRIDDEN', 'Overridden') },
  }
  const config = map[status] ?? map.PARTIAL
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${config.cls}`}>
      {config.icon}{config.label}
    </span>
  )
}

const SENSITIVITY_STYLES: Record<string, string> = {
  PUBLIC: 'bg-gray-100 text-gray-700',
  INTERNAL: 'bg-blue-100 text-blue-800',
  CONFIDENTIAL: 'bg-red-100 text-red-800',
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

export default function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const roles = useAppSelector((s) => s.auth.user?.roles ?? [])
  const canOverride = roles.includes('ROLE_DAF') || roles.includes('ROLE_ADMIN') || roles.includes('ROLE_ASSISTANT_COMPTABLE')
  // P11-15: only DAF and Assistant Comptable may reclassify an invoice's data sensitivity.
  const canClassify = roles.includes('ROLE_DAF') || roles.includes('ROLE_ASSISTANT_COMPTABLE')

  const [overrideReason, setOverrideReason] = useState('')
  const [showOverrideForm, setShowOverrideForm] = useState(false)
  const [pdfLoading, setPdfLoading] = useState(false)

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
      <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
    </div>
  )

  if (isError || !invoice) return (
    <div className="text-center py-20">
      <p className="text-red-500 mb-4">{t('app.error')}</p>
      <button onClick={() => navigate('/invoices')} className="text-primary underline text-sm">{t('app.back')}</button>
    </div>
  )

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate('/invoices')} className="p-2 rounded-lg hover:bg-gray-100 text-gray-500">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{invoice.referenceNumber}</h1>
            <p className="text-sm text-muted-foreground">{invoice.supplierName}</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
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
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50"
            title={t('invoice.exportPdf', 'Exporter en PDF')}
          >
            {pdfLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileDown className="w-4 h-4" />}
            PDF
          </button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-6">
        {/* Main content */}
        <div className="col-span-2 space-y-6">

          {/* Invoice summary */}
          <div className="bg-white rounded-xl border p-6">
            <h2 className="font-semibold text-gray-800 mb-4">{t('invoice.details')}</h2>
            <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
              {[
                { label: t('invoice.amount'), value: `${Number(invoice.amount).toLocaleString()} ${invoice.currency}` },
                { label: t('invoice.issueDate'), value: invoice.issueDate },
                { label: t('invoice.dueDate'), value: invoice.dueDate },
                { label: t('invoice.department'), value: invoice.department?.nameEn ?? invoice.department?.name ?? '—' },
              ].map(({ label, value }) => (
                <div key={label}>
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd className="font-medium text-gray-800 mt-0.5">{value}</dd>
                </div>
              ))}
              {invoice.purchaseOrderId && (
                <div>
                  <dt className="text-muted-foreground">{t('invoice.purchaseOrder', 'Purchase Order')}</dt>
                  <dd className="font-mono text-xs text-gray-700 mt-0.5">{invoice.purchaseOrderId}</dd>
                </div>
              )}
              {invoice.description && (
                <div className="col-span-2">
                  <dt className="text-muted-foreground">{t('invoice.description')}</dt>
                  <dd className="mt-0.5 text-gray-700">{invoice.description}</dd>
                </div>
              )}
            </dl>
          </div>

          {/* Data Sensitivity Classification (P11-15) */}
          <div className="bg-white rounded-xl border p-6">
            <div className="flex items-center justify-between gap-4">
              <div>
                <h2 className="font-semibold text-gray-800">{t('sensitivity.title', 'Data Sensitivity')}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">{t('sensitivity.subtitle', 'Confidentiality level of this financial record.')}</p>
              </div>
              {canClassify ? (
                <select
                  value={invoice.dataSensitivity ?? 'INTERNAL'}
                  onChange={(e) => sensitivityMutation.mutate(e.target.value)}
                  disabled={sensitivityMutation.isPending}
                  className="border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 disabled:opacity-50"
                >
                  <option value="PUBLIC">{t('sensitivity.PUBLIC', 'Public')}</option>
                  <option value="INTERNAL">{t('sensitivity.INTERNAL', 'Internal')}</option>
                  <option value="CONFIDENTIAL">{t('sensitivity.CONFIDENTIAL', 'Confidential')}</option>
                </select>
              ) : (
                <SensitivityBadge level={invoice.dataSensitivity ?? 'INTERNAL'} />
              )}
            </div>
          </div>

          {/* Three-Way Matching Result */}
          {invoice.purchaseOrderId && (
            <div className="bg-white rounded-xl border p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="font-semibold text-gray-800">{t('matching.title', 'Three-Way Matching')}</h2>
                {matchingResult && <MatchingBadge status={matchingResult.status} />}
              </div>

              {!matchingResult ? (
                <p className="text-sm text-gray-400">{t('matching.pending', 'Matching will be performed on submission.')}</p>
              ) : (
                <div className="space-y-3">
                  {matchingResult.discrepancyNotes && (
                    <div className="text-sm bg-gray-50 rounded-lg p-3 border">
                      <p className="font-medium text-gray-700 mb-1">{t('matching.notes', 'Matching details:')}</p>
                      <p className="text-gray-600 text-xs whitespace-pre-wrap">{matchingResult.discrepancyNotes}</p>
                    </div>
                  )}

                  {matchingResult.status === 'OVERRIDDEN' && matchingResult.overrideReason && (
                    <div className="text-sm bg-orange-50 rounded-lg p-3 border border-orange-100">
                      <p className="font-medium text-orange-800 mb-1">{t('matching.overrideJustification', 'Override justification:')}</p>
                      <p className="text-orange-700 text-xs">{matchingResult.overrideReason}</p>
                    </div>
                  )}

                  {/* Override form — only for MISMATCH, only for authorised roles, only when invoice is in BROUILLON/REJETE */}
                  {matchingResult.status === 'MISMATCH' && canOverride && ['BROUILLON', 'REJETE'].includes(invoice.status) && (
                    <div>
                      {!showOverrideForm ? (
                        <button
                          onClick={() => setShowOverrideForm(true)}
                          className="text-sm text-orange-600 hover:text-orange-800 font-medium underline"
                        >
                          {t('matching.override', 'Override mismatch with justification')}
                        </button>
                      ) : (
                        <div className="space-y-2">
                          <label className="block text-sm font-medium text-gray-700">
                            {t('matching.overrideReason', 'Override justification')} * ({t('matching.minChars', 'min. 10 characters')})
                          </label>
                          <textarea
                            value={overrideReason}
                            onChange={(e) => setOverrideReason(e.target.value)}
                            rows={3}
                            className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-orange-300"
                            placeholder={t('matching.overridePlaceholder', 'Explain why this discrepancy is acceptable...')}
                          />
                          <div className="flex gap-2">
                            <button
                              onClick={() => overrideMutation.mutate()}
                              disabled={overrideReason.length < 10 || overrideMutation.isPending}
                              className="px-3 py-1.5 bg-orange-600 text-white rounded-lg text-sm font-medium disabled:opacity-50 hover:bg-orange-700"
                            >
                              {overrideMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin inline" /> : t('matching.confirmOverride', 'Confirm Override')}
                            </button>
                            <button onClick={() => setShowOverrideForm(false)} className="px-3 py-1.5 border rounded-lg text-sm hover:bg-gray-50">
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
          )}

          {/* Approval Steps — vertical timeline */}
          {approvalSteps && approvalSteps.length > 0 && (
            <div className="bg-white rounded-xl border p-6">
              <h2 className="font-semibold text-gray-800 mb-5">{t('invoice.approvalSteps', 'Historique d\'approbation')}</h2>
              <div className="relative">
                {/* Vertical connector line */}
                <div className="absolute left-4 top-5 bottom-5 w-px bg-gray-200" />
                <div className="space-y-0">
                  {approvalSteps.map((step, idx) => {
                    const isApproved = step.status === 'APPROVED'
                    const isRejected = step.status === 'REJECTED'
                    const isPending  = step.status === 'PENDING'
                    const isLast     = idx === approvalSteps.length - 1

                    const dotClass = isApproved
                      ? 'bg-green-500 border-green-500 text-white'
                      : isRejected
                      ? 'bg-red-500 border-red-500 text-white'
                      : 'bg-white border-gray-300 text-gray-400'

                    const lineClass = isApproved && !isLast ? 'bg-green-400' : 'bg-gray-200'

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
                        <div className={`flex-1 min-w-0 rounded-lg border px-4 py-3 mb-0 ${
                          isApproved ? 'border-green-200 bg-green-50/50' :
                          isRejected ? 'border-red-200 bg-red-50/50' :
                          'border-gray-200 bg-gray-50/50'
                        }`}>
                          <div className="flex items-center justify-between flex-wrap gap-2 mb-1">
                            <div className="flex items-center gap-2">
                              <span className="font-semibold text-sm text-gray-900">
                                {step.stepNameFr ?? step.stepName}
                              </span>
                              {step.departmentCode && (
                                <span className="text-[10px] font-mono bg-gray-200 text-gray-600 px-1.5 py-0.5 rounded">
                                  {step.departmentCode}
                                </span>
                              )}
                              <span className={`text-[10px] font-bold uppercase tracking-wide px-2 py-0.5 rounded-full ${
                                isApproved ? 'bg-green-100 text-green-700' :
                                isRejected ? 'bg-red-100 text-red-700' :
                                'bg-amber-100 text-amber-700'
                              }`}>
                                {isApproved ? t('invoice.stepApproved', 'Approuvé') : isRejected ? t('invoice.stepRejected', 'Rejeté') : t('invoice.stepPending', 'En attente')}
                              </span>
                            </div>
                            {step.actionAt && (
                              <span className="text-xs text-gray-400 shrink-0">
                                {new Date(step.actionAt).toLocaleString()}
                              </span>
                            )}
                          </div>

                          {step.approverName && (
                            <div className="flex items-center gap-1.5 text-xs text-gray-500 mb-1">
                              <User className="w-3 h-3" />
                              <span>{step.approverName}{step.approverUsername ? ` (${step.approverUsername})` : ''}</span>
                            </div>
                          )}

                          {isPending && step.deadline && (
                            <div className="flex items-center gap-1.5 text-xs text-amber-600 mt-1">
                              <Clock className="w-3 h-3" />
                              {t('invoice.deadline', 'Délai')}: {new Date(step.deadline).toLocaleDateString()}
                              {new Date(step.deadline) < new Date() && (
                                <span className="text-red-600 font-semibold ml-1">— {t('invoice.overdue', 'DÉPASSÉ')}</span>
                              )}
                            </div>
                          )}

                          {step.comments && (
                            <p className="text-xs text-gray-600 italic mt-1.5 border-l-2 border-gray-300 pl-2">
                              "{step.comments}"
                            </p>
                          )}

                          {step.rejectionReason && (
                            <div className="mt-1.5 text-xs text-red-700 bg-red-50 border border-red-200 rounded px-2 py-1.5">
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
            </div>
          )}

          {/* Empty approval steps — show placeholder when invoice is past SOUMIS but no steps yet */}
          {(!approvalSteps || approvalSteps.length === 0) && !['BROUILLON', 'SOUMIS'].includes(invoice.status) && (
            <div className="bg-white rounded-xl border p-6">
              <h2 className="font-semibold text-gray-800 mb-3">{t('invoice.approvalSteps', 'Historique d\'approbation')}</h2>
              <p className="text-sm text-gray-400">{t('invoice.noStepsYet', 'Aucune étape d\'approbation enregistrée.')}</p>
            </div>
          )}

          {/* Line Items */}
          {invoice.lineItems && invoice.lineItems.length > 0 && (
            <div className="bg-white rounded-xl border overflow-hidden">
              <div className="px-5 py-4 border-b font-semibold text-gray-800">{t('invoice.lineItems')}</div>
              <table className="w-full text-sm">
                <thead className="bg-gray-50">
                  <tr>
                    <th className="text-left px-5 py-2 text-gray-500 font-medium">{t('invoice.description')}</th>
                    <th className="text-right px-5 py-2 text-gray-500 font-medium">{t('invoice.quantity', 'Qty')}</th>
                    <th className="text-right px-5 py-2 text-gray-500 font-medium">{t('invoice.unitPrice', 'Unit Price')}</th>
                    <th className="text-right px-5 py-2 text-gray-500 font-medium">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {invoice.lineItems.map((li, i) => (
                    <tr key={i}>
                      <td className="px-5 py-2">{li.description}</td>
                      <td className="px-5 py-2 text-right">{li.quantity}</td>
                      <td className="px-5 py-2 text-right font-mono">{Number(li.unitPrice).toFixed(2)}</td>
                      <td className="px-5 py-2 text-right font-mono font-medium">{Number(li.totalPrice).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {/* Documents */}
          {invoice.documents && invoice.documents.length > 0 && (
            <div className="bg-white rounded-xl border p-5">
              <h2 className="font-semibold text-gray-800 mb-3">{t('invoice.documents')}</h2>
              <ul className="space-y-2">
                {invoice.documents.map((doc) => (
                  <li key={doc.id} className="flex items-center justify-between text-sm px-3 py-2.5 bg-gray-50 rounded-lg border">
                    <span className="font-medium text-gray-700 truncate">{doc.fileName}</span>
                    <div className="flex items-center gap-3 ml-2 shrink-0">
                      <span className="text-xs text-muted-foreground">{(doc.fileSize / 1024).toFixed(1)} KB</span>
                      {doc.downloadUrl && (
                        <a href={doc.downloadUrl} download className="text-primary hover:text-primary/80">
                          <Download className="w-4 h-4" />
                        </a>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          <InvoiceActionPanel invoice={invoice} />
          <InvoiceTimeline invoice={invoice} />
        </div>
      </div>
    </div>
  )
}
