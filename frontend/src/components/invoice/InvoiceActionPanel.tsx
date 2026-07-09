import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAppSelector } from '@/store/hooks'
import apiClient from '@/services/apiClient'
import type { Invoice, InvoiceStatus } from '@/types/invoice'
import { Loader2 } from 'lucide-react'

interface InvoiceActionPanelProps {
  invoice: Invoice
}

const variantClasses: Record<string, string> = {
  primary:   'bg-primary text-primary-foreground hover:bg-primary/90',
  success:   'bg-pos text-white hover:bg-pos/90',
  danger:    'bg-crit text-white hover:bg-crit/90',
  secondary: 'border border-hairline-strong text-ink-soft hover:bg-ground',
}

export function InvoiceActionPanel({ invoice }: InvoiceActionPanelProps) {
  const { t } = useTranslation()
  const { user } = useAppSelector((s) => s.auth)
  const queryClient = useQueryClient()
  const [rejectReason, setRejectReason] = useState('')
  const [reasonCode, setReasonCode] = useState('')
  const [pendingAction, setPendingAction] = useState<string | null>(null)

  // Predefined rejection reasons (M4 #8) — loaded only while the reject dialog is open.
  const { data: rejectionReasons } = useQuery<{ code: string; label: string }[]>({
    queryKey: ['rejection-reasons'],
    queryFn: () =>
      apiClient
        .get(`/invoices/${invoice.id}/workflow/rejection-reasons`)
        .then((r) => r.data.data),
    enabled: pendingAction === 'REJECT',
  })

  const mutation = useMutation({
    mutationFn: async ({ action, reason, code }: { action: string; reason?: string; code?: string }) => {
      const invoiceId = invoice.id
      const base = `/invoices/${invoiceId}/workflow`
      switch (action) {
        case 'SUBMIT':
          return apiClient.post(`/invoices/${invoiceId}/submit`)
        case 'ASSIGN_REVIEWER':
          return apiClient.post(`/invoices/${invoiceId}/workflow/assign`)
        case 'VALIDATE_N1':
          return apiClient.post(`${base}/validate-n1`, reason ? { comment: reason } : {})
        case 'VALIDATE_N2':
          return apiClient.post(`${base}/validate-n2`, reason ? { comment: reason } : {})
        case 'REJECT':
          return apiClient.post(`${base}/reject`, { reasonCode: code, rejectionReason: reason })
        case 'BON_A_PAYER':
          return apiClient.post(`${base}/bon-a-payer`, {})
        default:
          throw new Error('Unknown action: ' + action)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoice', invoice.id] })
      queryClient.invalidateQueries({ queryKey: ['approval-queue'] })
      setPendingAction(null)
      setRejectReason('')
      setReasonCode('')
    },
  })

  if (!user) return null

  const roles = user.roles ?? []
  const deptId = user.departmentId
  const invDeptId = invoice.departmentId
  const status = invoice.status

  const isAdmin  = roles.includes('ROLE_ADMIN')
  const isAA     = roles.includes('ROLE_ASSISTANT_COMPTABLE')
  const isDaf    = roles.includes('ROLE_DAF')
  const isN1     = roles.some(r => r.startsWith('ROLE_VALIDATEUR_N1_'))
  const isN2     = roles.some(r => r.startsWith('ROLE_VALIDATEUR_N2_'))

  // Department check: a validator should only act on invoices from their own dept.
  // We match by departmentId stored in their JWT claim.
  const deptMatches = !deptId || !invDeptId || deptId === invDeptId || isAdmin

  type Btn = { action: string; label: string; variant: string; requiresReason?: boolean }
  const buttons: Btn[] = []

  // AA: submit draft or rejected invoice
  if (isAA && (status === 'BROUILLON' || status === 'REJETE')) {
    buttons.push({ action: 'SUBMIT', label: t('invoice.submit', 'Submit for Validation'), variant: 'primary' })
  }

  // Take charge of a submitted invoice (SOUMIS → EN_VALIDATION_N1)
  if ((isAA || isN1) && status === 'SOUMIS') {
    buttons.push({ action: 'ASSIGN_REVIEWER', label: t('invoice.startReview', 'Start review'), variant: 'primary' })
  }

  // N1 validator: approve or reject — only for their department
  if ((isN1 || isDaf) && status === 'EN_VALIDATION_N1' && deptMatches) {
    buttons.push({ action: 'VALIDATE_N1', label: t('invoice.validate', 'Approve (N1)'), variant: 'success' })
    buttons.push({ action: 'REJECT', label: t('invoice.reject', 'Reject'), variant: 'danger', requiresReason: true })
  }

  // N2 validator: approve or reject — only for their department
  if ((isN2 || isAdmin) && status === 'EN_VALIDATION_N2' && deptMatches) {
    buttons.push({ action: 'VALIDATE_N2', label: t('invoice.validate', 'Approve (N2)'), variant: 'success' })
    buttons.push({ action: 'REJECT', label: t('invoice.reject', 'Reject'), variant: 'danger', requiresReason: true })
  }

  // DAF: BON_A_PAYER on any validated invoice
  if ((isDaf || isAdmin) && status === 'VALIDE') {
    buttons.push({ action: 'BON_A_PAYER', label: t('invoice.approve', 'Issue BON À PAYER'), variant: 'success' })
    buttons.push({ action: 'REJECT', label: t('invoice.reject', 'Reject'), variant: 'danger', requiresReason: true })
  }

  // Payment recording is handled via PaymentsPage modal — see PaymentsPage.tsx

  // Admin-only actions
  if (isAdmin && status === 'EN_VALIDATION_N1') {
    buttons.push({ action: 'REJECT', label: t('invoice.reject', 'Reject (Admin)'), variant: 'danger', requiresReason: true })
  }

  if (buttons.length === 0) return null

  const execute = (btn: Btn) => {
    if (btn.requiresReason) {
      setPendingAction(btn.action)
    } else {
      mutation.mutate({ action: btn.action })
    }
  }

  return (
    <div className="bg-surface rounded-[4px] border border-hairline p-5">
      <h2 className="font-semibold text-ink mb-4">{t('invoice.actions')}</h2>

      {/* Department mismatch warning */}
      {(isN1 || isN2) && invDeptId && deptId && deptId !== invDeptId && !isAdmin && (
        <div className="mb-3 text-xs text-warn bg-warn-bg border border-warn/30 rounded-[4px] px-3 py-2">
          {t('invoice.wrongDept', 'This invoice belongs to a different department. You cannot approve it.')}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        {buttons.map((btn) => (
          <button
            key={btn.action + btn.label}
            id={`btn-action-${btn.action.toLowerCase()}`}
            disabled={mutation.isPending}
            onClick={() => execute(btn)}
            className={`flex items-center gap-2 px-4 py-2 rounded-[4px] text-sm font-medium transition-colors disabled:opacity-50 ${variantClasses[btn.variant]}`}
          >
            {mutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
            {btn.label}
          </button>
        ))}
      </div>

      {mutation.isError && (
        <p className="mt-2 text-xs text-crit">
          {(() => {
            const key = (mutation.error as any)?.response?.data?.message
            if (!key) return t('app.error', 'Action failed. Please try again.')
            // Try to translate backend i18n key, fallback to raw message
            const translated = t(key)
            return translated !== key ? translated : key
          })()}
        </p>
      )}

      {/* Reject reason modal */}
      {pendingAction && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-surface rounded-[4px] p-6 shadow-2xl w-full max-w-md space-y-4">
            <h3 className="font-semibold text-ink">{t('invoice.confirmReject', 'Confirm rejection')}</h3>
            <div>
              <label className="block text-sm text-ink-soft mb-1">
                {t('invoice.rejectReasonCode', 'Rejection reason')} *
              </label>
              <select
                id="reject-reason-code"
                value={reasonCode}
                onChange={(e) => setReasonCode(e.target.value)}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-300"
              >
                <option value="">{t('invoice.selectReason', 'Select a reason...')}</option>
                {(rejectionReasons ?? []).map((r) => (
                  <option key={r.code} value={r.code}>{r.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-ink-soft mb-1">
                {t('invoice.rejectReasonDetail', 'Detail (optional)')}{reasonCode === 'AUTRE' ? ' *' : ''}
              </label>
              <textarea
                id="reject-reason-input"
                rows={3}
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-300"
                placeholder={t('invoice.rejectReasonPlaceholder', 'Explain why this invoice is being rejected...')}
              />
            </div>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => { setPendingAction(null); setRejectReason(''); setReasonCode('') }}
                className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground"
              >
                {t('app.cancel')}
              </button>
              <button
                id="btn-confirm-reject"
                disabled={
                  !reasonCode ||
                  (reasonCode === 'AUTRE' && rejectReason.trim().length < 10) ||
                  mutation.isPending
                }
                onClick={() => mutation.mutate({ action: pendingAction, reason: rejectReason, code: reasonCode })}
                className="px-4 py-2 bg-crit text-white rounded-[4px] text-sm font-medium hover:bg-crit/90 disabled:opacity-50"
              >
                {t('app.confirm')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
