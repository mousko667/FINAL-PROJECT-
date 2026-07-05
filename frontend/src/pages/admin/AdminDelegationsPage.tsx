import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Loader2, UserCheck, Trash2, Plus, ArrowRight, AlertCircle } from 'lucide-react'
import { formatDate } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'

interface Department {
  id: string
  code: string
  nameEn: string
  nameFr: string
}

interface User {
  id: string
  username: string
  firstName?: string
  lastName?: string
}

interface Delegation {
  id: string
  delegatorUsername: string
  delegateeUsername: string
  departmentCode: string
  fromDate: string
  toDate: string
  reason: string
  createdAt: string
}

function userLabel(u: User): string {
  const name = `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim()
  return name ? `${name} (${u.username})` : u.username
}

export default function AdminDelegationsPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()

  const [dept, setDept] = useState('')
  const [delegatorId, setDelegatorId] = useState('')
  const [delegateeId, setDelegateeId] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState('')
  const [revokeTargetId, setRevokeTargetId] = useState<string | null>(null)

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: Department[] } }>('/departments')
      return data.data?.content ?? []
    },
  })

  const { data: users } = useQuery({
    queryKey: ['users-all'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: { content: User[] } }>('/users', { params: { size: 100 } })
      return data.data?.content ?? []
    },
  })

  const { data: delegations, isLoading: delegationsLoading } = useQuery({
    queryKey: ['delegations', dept],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Delegation[] }>('/approvals/delegations', {
        params: { departmentCode: dept },
      })
      return data.data ?? []
    },
    enabled: !!dept,
  })

  const createMutation = useMutation({
    mutationFn: () =>
      apiClient.post('/approvals/delegations', {
        delegatorId,
        delegateeId,
        departmentCode: dept,
        fromDate,
        toDate,
        reason: reason || null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['delegations', dept] })
      setDelegatorId('')
      setDelegateeId('')
      setFromDate('')
      setToDate('')
      setReason('')
      setFormError('')
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      setFormError(err?.response?.data?.message ?? t('admin.delegations.createError', 'Could not create the delegation.'))
    },
  })

  const revokeMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/approvals/delegations/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['delegations', dept] })
      setRevokeTargetId(null)
    },
  })

  const submit = () => {
    setFormError('')
    if (!dept || !delegatorId || !delegateeId || !fromDate || !toDate) {
      setFormError(t('admin.delegations.missingFields', 'Please fill in department, both users, and the date range.'))
      return
    }
    if (delegatorId === delegateeId) {
      setFormError(t('admin.delegations.sameUser', 'The delegator and delegatee must be different users.'))
      return
    }
    if (toDate < fromDate) {
      setFormError(t('admin.delegations.badRange', 'The end date must be on or after the start date.'))
      return
    }
    createMutation.mutate()
  }

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="space-y-6 page-enter">
        <div>
          <h1 className="text-2xl font-bold text-ink flex items-center gap-2">
            <UserCheck className="w-6 h-6 text-primary" />
            {t('admin.delegations.title', 'Approval Delegations')}
          </h1>
          <p className="text-sm text-ink-soft mt-0.5">
            {t('admin.delegations.subtitle', 'Reassign a validator’s approval authority during an absence.')}
          </p>
        </div>

        {/* Department selector */}
        <Panel className="p-5">
          <label className="block text-sm font-medium text-ink-soft mb-1">
            {t('admin.delegations.department', 'Department')}
          </label>
          <select
            value={dept}
            onChange={(e) => setDept(e.target.value)}
            className="w-full sm:w-80 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface"
          >
            <option value="">{t('admin.delegations.selectDepartment', '— Select a department —')}</option>
            {(departments ?? []).map((d) => (
              <option key={d.id} value={d.code}>{d.code} — {i18n.language === 'fr' ? d.nameFr : d.nameEn}</option>
            ))}
          </select>
        </Panel>

        {dept && (
          <>
            {/* Active delegations list */}
            <Panel title={t('admin.delegations.activeTitle', 'Active delegations') + ' — ' + dept}>
              <div className="-m-5">
              {delegationsLoading ? (
                <div className="p-8 flex justify-center"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
              ) : (delegations ?? []).length === 0 ? (
                <div className="p-8 text-center text-sm text-ink-faint">
                  {t('admin.delegations.empty', 'No active delegations for this department.')}
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-ink-faint text-xs font-medium uppercase tracking-wide bg-ground hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                      <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.delegations.delegator', 'Delegator')}</th>
                      <th className="px-2 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint" />
                      <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.delegations.delegatee', 'Delegatee')}</th>
                      <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.delegations.period', 'Period')}</th>
                      <th className="text-left px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint">{t('admin.delegations.reason', 'Reason')}</th>
                      <th className="px-4 py-3 bg-ground text-xs font-medium uppercase tracking-wide text-ink-faint" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-hairline">
                    {(delegations ?? []).map((d) => (
                      <tr key={d.id} className="hover:bg-[color-mix(in_srgb,hsl(var(--gold-deep))_5%,transparent)]">
                        <td className="px-4 py-3 font-medium text-ink">{d.delegatorUsername}</td>
                        <td className="px-2 py-3 text-ink-faint"><ArrowRight className="w-4 h-4" /></td>
                        <td className="px-4 py-3 text-ink">{d.delegateeUsername}</td>
                        <td className="px-4 py-3 text-ink-faint text-xs num">
                          {formatDate(d.fromDate)} – {formatDate(d.toDate)}
                        </td>
                        <td className="px-4 py-3 text-ink-soft truncate max-w-[200px]">{d.reason || '—'}</td>
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => setRevokeTargetId(d.id)}
                            disabled={revokeMutation.isPending}
                            className="inline-flex items-center gap-1 text-xs text-crit hover:text-crit disabled:opacity-50"
                          >
                            <Trash2 className="w-3.5 h-3.5" /> {t('admin.delegations.revoke', 'Revoke')}
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              </div>
            </Panel>

            {/* Create form */}
            <Panel className="p-5 space-y-4">
              <h2 className="font-semibold text-ink">{t('admin.delegations.createTitle', 'Create a delegation')}</h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.delegations.delegator', 'Delegator')} *</label>
                  <select value={delegatorId} onChange={(e) => setDelegatorId(e.target.value)}
                    className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface">
                    <option value="">{t('admin.delegations.selectUser', '— Select a user —')}</option>
                    {(users ?? []).map((u) => <option key={u.id} value={u.id}>{userLabel(u)}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.delegations.delegatee', 'Delegatee')} *</label>
                  <select value={delegateeId} onChange={(e) => setDelegateeId(e.target.value)}
                    className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface">
                    <option value="">{t('admin.delegations.selectUser', '— Select a user —')}</option>
                    {(users ?? []).map((u) => <option key={u.id} value={u.id}>{userLabel(u)}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.delegations.fromDate', 'From')} *</label>
                  <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)}
                    className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.delegations.toDate', 'To')} *</label>
                  <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)}
                    className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface" />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.delegations.reason', 'Reason')}</label>
                  <input type="text" value={reason} onChange={(e) => setReason(e.target.value)}
                    placeholder={t('admin.delegations.reasonPlaceholder', 'e.g. annual leave')}
                    className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 bg-surface" />
                </div>
              </div>

              {formError && (
                <p className="flex items-center gap-1.5 text-sm text-crit">
                  <AlertCircle className="w-4 h-4 shrink-0" /> {formError}
                </p>
              )}

              <button
                onClick={submit}
                disabled={createMutation.isPending}
                className="inline-flex items-center gap-1.5 bg-primary text-primary-foreground rounded-[4px] px-4 py-2 text-sm font-medium disabled:opacity-50"
              >
                {createMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                {t('admin.delegations.create', 'Create delegation')}
              </button>
            </Panel>
          </>
        )}

        <ConfirmDialog
          open={revokeTargetId !== null}
          title={t('admin.delegations.revokeConfirmTitle', 'Revoke this delegation?')}
          message={t('admin.delegations.revokeConfirmBody', "The delegator will regain sole approval authority for this department.")}
          variant="danger"
          onConfirm={() => { if (revokeTargetId) revokeMutation.mutate(revokeTargetId) }}
          onCancel={() => setRevokeTargetId(null)}
        />
      </div>
    </PageRoleGuard>
  )
}
