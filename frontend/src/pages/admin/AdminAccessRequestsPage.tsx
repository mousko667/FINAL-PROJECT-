import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { type AccessRequest, StatusBadge } from '@/pages/MyAccessRequestsPage'
import { ShieldCheck, Loader2, Check, X } from 'lucide-react'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { notifyApiError } from '@/components/ErrorToaster'

const ADMIN_ROLES = ['ROLE_ADMIN']

type StatusFilter = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL'

/**
 * P11-17 — admin review queue. Lists access requests (filterable by status) and lets an admin
 * approve (adds the requested role to the requester) or reject (with an optional comment). ADMIN only.
 */
function AdminAccessRequestsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const [filter, setFilter] = useState<StatusFilter>('PENDING')
  const [comment, setComment] = useState<Record<string, string>>({})
  const [actingId, setActingId] = useState<string | null>(null)

  const { data: requests = [], isLoading } = useQuery<AccessRequest[]>({
    queryKey: ['access-requests', 'queue', filter],
    queryFn: async () => {
      const params: Record<string, string | number> = { size: 100 }
      if (filter !== 'ALL') params.status = filter
      const { data } = await apiClient.get<ApiResponse<PagedResponse<AccessRequest>>>(
        '/access-requests', { params })
      return data.data.content ?? []
    },
  })

  const review = useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: ({ id, approve }: { id: string; approve: boolean }) =>
      apiClient.patch(`/access-requests/${id}`, { approve, comment: comment[id] || null }),
    onSettled: () => {
      setActingId(null)
      queryClient.invalidateQueries({ queryKey: ['access-requests', 'queue'] })
    },
  })

  const filters: StatusFilter[] = ['PENDING', 'APPROVED', 'REJECTED', 'ALL']

  return (
    <div className="space-y-6">
      <PageHeader
        title={<span className="flex items-center gap-2"><ShieldCheck className="w-6 h-6" aria-hidden /> {t('accessRequests.adminTitle')}</span>}
        subtitle={t('accessRequests.adminSubtitle')}
      />

      <div className="flex gap-2">
        {filters.map((f) => (
          <button
            key={f}
            onClick={() => setFilter(f)}
            className={`px-3 py-1.5 rounded-[4px] text-sm font-medium ${
              filter === f ? 'bg-primary text-primary-foreground' : 'bg-ground text-ink-soft hover:bg-hairline'
            }`}
          >
            {f === 'ALL' ? t('accessRequests.filterAll') : t(`accessRequests.status.${f}`)}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex justify-center py-10"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
      ) : requests.length === 0 ? (
        <p className="text-sm text-ink-faint py-8">{t('accessRequests.noneQueue')}</p>
      ) : (
        <Panel className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-ground text-left text-ink-faint text-xs font-medium uppercase tracking-wide">
                <th className="px-4 py-2.5 font-medium">{t('accessRequests.requester')}</th>
                <th className="px-4 py-2.5 font-medium">{t('accessRequests.role')}</th>
                <th className="px-4 py-2.5 font-medium">{t('accessRequests.reason')}</th>
                <th className="px-4 py-2.5 font-medium">{t('accessRequests.statusCol')}</th>
                <th className="px-4 py-2.5 font-medium text-right">{t('accessRequests.action')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-hairline">
              {requests.map((r) => {
                const pending = r.status === 'PENDING'
                const busy = review.isPending && actingId === r.id
                return (
                  <tr key={r.id} className="align-top">
                    <td className="px-4 py-3">
                      <div className="font-medium text-ink">{r.requesterName}</div>
                      <div className="text-xs text-ink-faint">{r.requesterUsername}</div>
                    </td>
                    <td className="px-4 py-3 font-medium text-ink">{t(`roles.${r.requestedRole}`, r.requestedRole)}</td>
                    <td className="px-4 py-3 text-ink-soft max-w-xs">{r.reason}</td>
                    <td className="px-4 py-3"><StatusBadge status={r.status} /></td>
                    <td className="px-4 py-3 text-right">
                      {pending ? (
                        <div className="flex flex-col items-end gap-2">
                          <input
                            type="text"
                            value={comment[r.id] ?? ''}
                            onChange={(e) => setComment((c) => ({ ...c, [r.id]: e.target.value }))}
                            placeholder={t('accessRequests.commentPlaceholder')}
                            className="w-48 border border-hairline rounded-[4px] px-2 py-1 text-xs focus:ring-2 focus:ring-primary/40 focus:outline-none"
                          />
                          <div className="flex gap-2">
                            <button
                              onClick={() => { setActingId(r.id); review.mutate({ id: r.id, approve: true }) }}
                              disabled={busy}
                              className="inline-flex items-center gap-1 px-3 py-1.5 bg-pos text-pos-bg rounded-[4px] text-xs font-medium hover:opacity-90 disabled:opacity-50"
                            >
                              {busy ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Check className="w-3.5 h-3.5" />}
                              {t('accessRequests.approve')}
                            </button>
                            <button
                              onClick={() => { setActingId(r.id); review.mutate({ id: r.id, approve: false }) }}
                              disabled={busy}
                              className="inline-flex items-center gap-1 px-3 py-1.5 bg-crit text-crit-bg rounded-[4px] text-xs font-medium hover:opacity-90 disabled:opacity-50"
                            >
                              <X className="w-3.5 h-3.5" />
                              {t('accessRequests.reject')}
                            </button>
                          </div>
                        </div>
                      ) : (
                        <div className="text-xs text-ink-soft">
                          <div>{r.reviewedByName ? t('accessRequests.reviewedBy', { name: r.reviewedByName }) : '—'}</div>
                          {r.reviewComment && <div className="text-ink-faint mt-0.5">{r.reviewComment}</div>}
                        </div>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </Panel>
      )}
    </div>
  )
}


export default function AdminAccessRequestsPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ADMIN_ROLES}>
      <AdminAccessRequestsPage />
    </PageRoleGuard>
  )
}
