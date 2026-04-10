import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useAppSelector } from '@/store/hooks'
import { invoiceService } from '@/services/invoiceService'
import type { Invoice, InvoiceStatus } from '@/types/invoice'
import type { UserRole } from '@/store/slices/authSlice'
import { Loader2 } from 'lucide-react'

interface InvoiceActionPanelProps {
  invoice: Invoice
}

type ActionConfig = {
  action: string
  label: string
  variant: 'primary' | 'success' | 'danger' | 'secondary'
  requiresReason?: boolean
  roles: UserRole[]
  requiredStatuses: InvoiceStatus[]
}

const ACTIONS: ActionConfig[] = [
  {
    action: 'SUBMIT',
    label: 'invoice.submit',
    variant: 'primary',
    roles: ['ROLE_ASSISTANT_COMPTABLE', 'ROLE_ADMIN'],
    requiredStatuses: ['BROUILLON', 'REJETE'],
  },
  {
    action: 'VALIDATE_N1',
    label: 'invoice.validate',
    variant: 'success',
    roles: ['ROLE_VALIDATEUR_N1', 'ROLE_ADMIN'],
    requiredStatuses: ['EN_VALIDATION_N1'],
  },
  {
    action: 'REJECT_N1',
    label: 'invoice.reject',
    variant: 'danger',
    requiresReason: true,
    roles: ['ROLE_VALIDATEUR_N1', 'ROLE_ADMIN'],
    requiredStatuses: ['EN_VALIDATION_N1'],
  },
  {
    action: 'VALIDATE_N2',
    label: 'invoice.validate',
    variant: 'success',
    roles: ['ROLE_VALIDATEUR_N2', 'ROLE_ADMIN'],
    requiredStatuses: ['EN_VALIDATION_N2'],
  },
  {
    action: 'REJECT_N2',
    label: 'invoice.reject',
    variant: 'danger',
    requiresReason: true,
    roles: ['ROLE_VALIDATEUR_N2', 'ROLE_ADMIN'],
    requiredStatuses: ['EN_VALIDATION_N2'],
  },
  {
    action: 'APPROVE_FOR_PAYMENT',
    label: 'invoice.approve',
    variant: 'success',
    roles: ['ROLE_DAF', 'ROLE_ADMIN'],
    requiredStatuses: ['VALIDE'],
  },
  {
    action: 'MARK_PAID',
    label: 'invoice.markPaid',
    variant: 'primary',
    roles: ['ROLE_DAF', 'ROLE_ADMIN'],
    requiredStatuses: ['BON_A_PAYER'],
  },
  {
    action: 'ARCHIVE',
    label: 'invoice.archive',
    variant: 'secondary',
    roles: ['ROLE_ADMIN'],
    requiredStatuses: ['PAYE'],
  },
]

const variantClasses: Record<ActionConfig['variant'], string> = {
  primary: 'bg-primary text-primary-foreground hover:bg-primary/90',
  success: 'bg-green-600 text-white hover:bg-green-700',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  secondary: 'border border-gray-300 text-gray-700 hover:bg-gray-50',
}

export function InvoiceActionPanel({ invoice }: InvoiceActionPanelProps) {
  const { t } = useTranslation()
  const { user } = useAppSelector((s) => s.auth)
  const queryClient = useQueryClient()
  const [rejectReason, setRejectReason] = useState('')
  const [pendingAction, setPendingAction] = useState<ActionConfig | null>(null)

  const updateMutation = useMutation({
    mutationFn: ({ action, reason }: { action: string; reason?: string }) =>
      invoiceService.updateStatus(invoice.id, action, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoice', invoice.id] })
      setPendingAction(null)
      setRejectReason('')
    },
  })

  if (!user) return null

  const availableActions = ACTIONS.filter(
    (a) =>
      a.requiredStatuses.includes(invoice.status) &&
      user.roles.some((r) => a.roles.includes(r))
  )

  if (availableActions.length === 0) return null

  const execute = (action: ActionConfig) => {
    if (action.requiresReason) {
      setPendingAction(action)
    } else {
      updateMutation.mutate({ action: action.action })
    }
  }

  return (
    <div className="bg-white rounded-xl border p-5">
      <h2 className="font-semibold text-gray-800 mb-4">{t('invoice.actions')}</h2>

      <div className="flex flex-wrap gap-3">
        {availableActions.map((action) => (
          <button
            key={action.action}
            id={`btn-action-${action.action.toLowerCase()}`}
            disabled={updateMutation.isPending}
            onClick={() => execute(action)}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors disabled:opacity-50 ${variantClasses[action.variant]}`}
          >
            {updateMutation.isPending && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
            {t(action.label)}
          </button>
        ))}
      </div>

      {/* Reject reason modal */}
      {pendingAction && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-xl p-6 shadow-2xl w-full max-w-md space-y-4">
            <h3 className="font-semibold text-gray-900">{t('invoice.confirmReject')}</h3>
            <div>
              <label className="block text-sm text-gray-600 mb-1">{t('invoice.rejectReason')}</label>
              <textarea
                id="reject-reason-input"
                rows={3}
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-300"
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
                disabled={!rejectReason.trim() || updateMutation.isPending}
                onClick={() => updateMutation.mutate({ action: pendingAction.action, reason: rejectReason })}
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
