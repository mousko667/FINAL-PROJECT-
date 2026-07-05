import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { useAppSelector } from '@/store/hooks'
import { ROLE_OPTIONS } from '@/constants/roles'
import { Panel } from "@/components/ui/Panel"
import {  KeyRound, Loader2, AlertCircle, Send  } from 'lucide-react'

export interface AccessRequest {
  id: string
  requesterId: string
  requesterUsername: string
  requesterName: string
  requestedRole: string
  reason: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  reviewedById: string | null
  reviewedByName: string | null
  reviewComment: string | null
  createdAt: string | null
  reviewedAt: string | null
}

export function StatusBadge({ status }: { status: AccessRequest['status'] }) {
  const { t } = useTranslation()
  const styles: Record<AccessRequest['status'], string> = {
    PENDING: 'bg-amber-100 text-amber-800',
    APPROVED: 'bg-pos/10 text-pos',
    REJECTED: 'bg-crit/10 text-red-800',
  }
  return (
    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${styles[status]}`}>
      {t(`accessRequests.status.${status}`)}
    </span>
  )
}

/**
 * P11-17 — staff self-service: request one additional role (with a reason) and track the
 * status of your own requests. Any authenticated staff user (not suppliers) can use this.
 */
export default function MyAccessRequestsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const currentUser = useAppSelector((s) => s.auth.user)

  const [requestedRole, setRequestedRole] = useState('')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const { data: requests = [], isLoading } = useQuery<AccessRequest[]>({
    queryKey: ['access-requests', 'mine'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<AccessRequest>>>(
        '/access-requests/mine', { params: { size: 100 } })
      return data.data.content ?? []
    },
  })

  // Don't offer roles the user already holds.
  const heldRoles = new Set(currentUser?.roles ?? [])
  const availableRoles = ROLE_OPTIONS.filter((r) => !heldRoles.has(r.value))

  const submit = useMutation({
    mutationFn: () =>
      apiClient.post('/access-requests', { requestedRole, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['access-requests', 'mine'] })
      setRequestedRole('')
      setReason('')
      setFormError(null)
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      setFormError(err.response?.data?.message ?? t('accessRequests.submitError'))
    },
  })

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!requestedRole || !reason.trim()) {
      setFormError(t('accessRequests.formIncomplete'))
      return
    }
    submit.mutate()
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3">
        <KeyRound className="w-6 h-6 text-primary mt-0.5" />
        <div>
          <h1 className="text-2xl font-bold text-ink">{t('accessRequests.myTitle')}</h1>
          <p className="text-sm text-ink-faint mt-1">{t('accessRequests.mySubtitle')}</p>
        </div>
      </div>

      {/* Request form */}
      <form onSubmit={onSubmit} className="bg-surface rounded-xl border border-hairline p-5 space-y-4 max-w-2xl">
        <div>
          <label htmlFor="ar-role" className="block text-sm font-medium text-ink-soft mb-1">
            {t('accessRequests.role')}
          </label>
          <select
            id="ar-role"
            value={requestedRole}
            onChange={(e) => { setRequestedRole(e.target.value); setFormError(null) }}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary/40 focus:outline-none"
          >
            <option value="">{t('accessRequests.selectRole')}</option>
            {availableRoles.map((r) => (
              <option key={r.value} value={r.value}>{t(`roles.${r.value}`, r.label)}</option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="ar-reason" className="block text-sm font-medium text-ink-soft mb-1">
            {t('accessRequests.reason')}
          </label>
          <textarea
            id="ar-reason"
            value={reason}
            onChange={(e) => { setReason(e.target.value); setFormError(null) }}
            rows={3}
            maxLength={1000}
            placeholder={t('accessRequests.reasonPlaceholder')}
            className="w-full border rounded-lg px-3 py-2 text-sm focus:ring-2 focus:ring-primary/40 focus:outline-none"
          />
        </div>

        {formError && (
          <p className="flex items-center gap-1.5 text-sm text-crit">
            <AlertCircle className="w-4 h-4" /> {formError}
          </p>
        )}

        <button
          type="submit"
          disabled={submit.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
        >
          {submit.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          {t('accessRequests.submit')}
        </button>
      </form>

      {/* My requests */}
      <div>
        <h2 className="text-sm font-semibold text-ink-soft mb-2">{t('accessRequests.myRequests')}</h2>
        {isLoading ? (
          <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
        ) : requests.length === 0 ? (
          <p className="text-sm text-ink-faint py-6">{t('accessRequests.noneMine')}</p>
        ) : (
          <div className="bg-surface rounded-xl border border-hairline overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-ink-faint hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                  <th className="px-4 py-2.5 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('accessRequests.role')}</th>
                  <th className="px-4 py-2.5 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('accessRequests.reason')}</th>
                  <th className="px-4 py-2.5 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('accessRequests.statusCol')}</th>
                  <th className="px-4 py-2.5 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('accessRequests.reviewComment')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-hairline">
                {requests.map((r) => (
                  <tr key={r.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                    <td className="px-4 py-2.5 font-medium text-ink">{t(`roles.${r.requestedRole}`, r.requestedRole)}</td>
                    <td className="px-4 py-2.5 text-ink-soft max-w-xs truncate" title={r.reason}>{r.reason}</td>
                    <td className="px-4 py-2.5"><StatusBadge status={r.status} /></td>
                    <td className="px-4 py-2.5 text-ink-faint">{r.reviewComment || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
