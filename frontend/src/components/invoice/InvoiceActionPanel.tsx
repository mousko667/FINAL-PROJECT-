import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppSelector } from '@/store/hooks'
import apiClient from '@/services/apiClient'
import type { Invoice, InvoiceStatus } from '@/types/invoice'
import { Loader2 } from 'lucide-react'

interface InvoiceActionPanelProps {
  invoice: Invoice
}

const variantClasses: Record<string, string> = {
  primary:   'bg-primary text-primary-foreground hover:bg-primary/90',
  success:   'bg-green-600 text-white hover:bg-green-700',
  danger:    'bg-red-600 text-white hover:bg-red-700',
  secondary: 'border border-gray-300 text-gray-700 hover:bg-gray-50',
}

export function InvoiceActionPanel({ invoice }: InvoiceActionPanelProps) {
  const { t } = useTranslation()
  const { user } = useAppSelector((s) => s.auth)
  const queryClient = useQueryClient()
  const [rejectReason, setRejectReason] = useState('')
  const [pendingAction, setPendingAction] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: async ({ action, reason }: { action: string; reason?: string }) => {
      const invoiceId = invoice.id
      const base = `/invoices/${invoiceId}/workflow`
      switch (action) {
        case 'SUBMIT':
          return apiClient.post(`/invoices/${invoiceId}/submit`)
        case 'VALIDATE_N1':
          return apiClient.post(`${base}/validate-n1`, reason ? { comment: reason } : {})
        case 'VALIDATE_N2':
          return apiClient.post(`${base}/validate-n2`, reason ? { comment: reason } : {})
        case 'REJECT':
          return apiClient.post(`${base}/reject`, { rejectionReason: reason })
        case 'BON_A_PAYER':
          return apiClient.post(`${base}/bon-a-payer`, {})
        case 'MARK_PAID':
          return apiClient.post(`/payments/invoice/${invoiceId}`, {
            amountPaid: invoice.amount,
            paymentDate: new Date().toISOString(),
            paymentMethod: 'BANK_TRANSFER',
            reference: `PAY-${invoice.referenceNumber}`,
          })
        default:
          throw new Error('Unknown action: ' + action)
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoice', invoice.id] })
      queryClient.invalidateQueries({ queryKey: ['approval-queue'] })
      setPendingAction(null)
      setRejectReason('')
    },
  })

  if (!user) return null

  const roles = user.roles ?? []
  const deptId = user.departmentId
  const invDeptId = invoice.department?.id
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

  // AA: record payment once BON_A_PAYER issued
  if (isAA && status === 'BON_A_PAYER') {
    buttons.push({ action: 'MARK_PAID', label: t('invoice.markPaid', 'Record Payment'), variant: 'primary' })
  }

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
    <div className="bg-white rounded-xl border p-5">
      <h2 className="font-semibold text-gray-800 mb-4">{t('invoice.actions')}</h2>

      {/* Department mismatch warning */}
      {(isN1 || isN2) && invDeptId && deptId && deptId !== invDeptId && !isAdmin && (
        <div className="mb-3 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
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
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 ${variantClasses[btn.variant]}`}
          >
            {mutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
            {btn.label}
          </button>
        ))}
      </div>

      {mutation.isError && (
        <p className="mt-2 text-xs text-red-600">
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
          <div className="bg-white rounded-xl p-6 shadow-2xl w-full max-w-md space-y-4">
            <h3 className="font-semibold text-gray-900">{t('invoice.confirmReject', 'Confirm rejection')}</h3>
            <div>
              <label className="block text-sm text-gray-600 mb-1">
                {t('invoice.rejectReason', 'Rejection reason')} *
              </label>
              <textarea
                id="reject-reason-input"
                rows={3}
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-300"
                placeholder={t('invoice.rejectReasonPlaceholder', 'Explain why this invoice is being rejected...')}
              />
            </div>
            <div className="flex justify-end gap-3">
              <button
                onClick={() => { setPendingAction(null); setRejectReason('') }}
                className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50"
              >
                {t('app.cancel')}
              </button>
              <button
                id="btn-confirm-reject"
                disabled={!rejectReason.trim() || mutation.isPending}
                onClick={() => mutation.mutate({ action: pendingAction, reason: rejectReason })}
                className="px-4 py-2 bg-red-600 text-white rounded-lg text-sm font-medium hover:bg-red-700 disabled:opacity-50"
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
