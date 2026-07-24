import { useState } from 'react'
import { translateApiMessage } from '@/types/apiError'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { UserCheck, Loader2, Trash2, Plus, AlertCircle } from 'lucide-react'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'

interface Delegation {
  id: string
  delegatorUsername: string
  delegateeUsername: string
  departmentCode: string
  fromDate: string
  toDate: string
  reason: string
}

interface Delegatee { id: string; username: string; fullName: string }

/**
 * M6 — self-service: an approver delegates their own approvals while absent.
 *
 * AUDIT-004: the page had no guard while the sidebar reserved its entry to approvers,
 * so an ADMIN or ASSISTANT_COMPTABLE typing the URL reached it. This list mirrors
 * Sidebar.tsx and the backend's APPROVER_ROLES (DelegationController:64-70) exactly —
 * DAF plus the 11 validators, ADMIN excluded (SoD).
 */
const DELEGATION_ROLES = [
  'ROLE_DAF',
  'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG',
  'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO',
  'ROLE_VALIDATEUR_N1_TERM', 'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE', 'ROLE_VALIDATEUR_N1_INFRA',
  'ROLE_VALIDATEUR_N2_INFRA', 'ROLE_VALIDATEUR_N1_TECH',
  'ROLE_VALIDATEUR_N2_TECH',
]

export default function MyDelegationsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [delegateeId, setDelegateeId] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [reason, setReason] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [revokeTargetId, setRevokeTargetId] = useState<string | null>(null)

  const { data: delegations = [], isLoading } = useQuery<Delegation[]>({
    queryKey: ['my-delegations'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Delegation[] }>('/approvals/delegations/mine')
      return data.data ?? []
    },
  })

  const { data: delegatees = [] } = useQuery<Delegatee[]>({
    queryKey: ['eligible-delegatees'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Delegatee[] }>('/approvals/delegations/eligible-delegatees')
      return data.data ?? []
    },
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['my-delegations'] })

  const create = useMutation({
    mutationFn: () => apiClient.post('/approvals/delegations/mine', { delegateeId, fromDate, toDate, reason }),
    onSuccess: () => { setDelegateeId(''); setFromDate(''); setToDate(''); setReason(''); setFormError(null); invalidate() },
    onError: (err: { response?: { data?: { message?: string } } }) =>
      setFormError(translateApiMessage(err, t) ?? t('delegations.createError', 'Échec de la création.')),
  })

  const revoke = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/approvals/delegations/mine/${id}`),
    onSuccess: () => { invalidate(); setRevokeTargetId(null) },
  })

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!delegateeId || !fromDate || !toDate) { setFormError(t('delegations.incomplete', 'Délégataire et dates requis.')); return }
    create.mutate()
  }

  const inputCls = 'w-full border rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'
  const today = new Date().toISOString().slice(0, 10)
  const isActive = (d: Delegation) => d.fromDate <= today && d.toDate >= today

  return (
    <PageRoleGuard allowedRoles={DELEGATION_ROLES}>
      <div className="space-y-6">
      <PageHeader
        title={
          <span className="flex items-center gap-2">
            <UserCheck className="w-5 h-5" aria-hidden />
            {t('delegations.myTitle', 'Mes délégations d\'approbation')}
          </span>
        }
        subtitle={t('delegations.mySubtitle', 'Déléguez vos approbations à un collègue pendant votre absence.')}
      />

      <form onSubmit={onSubmit} className="bg-surface rounded-[4px] border border-hairline p-5 space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="md:col-span-2">
            <label htmlFor="delegateeId" className="block text-sm font-medium text-ink-soft mb-1">{t('delegations.delegatee', 'Délégataire')}</label>
            <select id="delegateeId" value={delegateeId} onChange={e => { setDelegateeId(e.target.value); setFormError(null) }} className={inputCls}>
              <option value="">{t('delegations.selectDelegatee', '— Sélectionner —')}</option>
              {delegatees.map(d => <option key={d.id} value={d.id}>{d.fullName || d.username} ({d.username})</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="delegationFromDate" className="block text-sm font-medium text-ink-soft mb-1">{t('delegations.fromDate', 'Du')}</label>
            <input id="delegationFromDate" type="date" value={fromDate} onChange={e => { setFromDate(e.target.value); setFormError(null) }} className={inputCls} />
          </div>
          <div>
            <label htmlFor="delegationToDate" className="block text-sm font-medium text-ink-soft mb-1">{t('delegations.toDate', 'Au')}</label>
            <input id="delegationToDate" type="date" value={toDate} onChange={e => { setToDate(e.target.value); setFormError(null) }} className={inputCls} />
          </div>
          <div className="md:col-span-2">
            <label htmlFor="delegationReason" className="block text-sm font-medium text-ink-soft mb-1">{t('delegations.reason', 'Motif (facultatif)')}</label>
            <input id="delegationReason" value={reason} onChange={e => setReason(e.target.value)} className={inputCls} maxLength={255} />
          </div>
        </div>
        {formError && <p className="flex items-center gap-1.5 text-sm text-crit"><AlertCircle className="w-4 h-4" />{formError}</p>}
        <button type="submit" disabled={create.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50">
          {create.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
          {t('delegations.create', 'Créer la délégation')}
        </button>
      </form>

      <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
        {isLoading ? (
          <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-ink-faint" /></div>
        ) : delegations.length === 0 ? (
          <p className="text-sm text-ink-faint py-8 text-center">{t('delegations.none', 'Aucune délégation.')}</p>
        ) : (
          <table className="w-full text-sm">
            <thead><tr className="border-b text-left text-ink-soft">
              <th className="px-4 py-2.5 font-medium">{t('delegations.delegatee', 'Délégataire')}</th>
              <th className="px-4 py-2.5 font-medium">{t('delegations.department', 'Département')}</th>
              <th className="px-4 py-2.5 font-medium">{t('delegations.period', 'Période')}</th>
              <th className="px-4 py-2.5 font-medium">{t('delegations.statusCol', 'Statut')}</th>
              <th className="px-4 py-2.5 font-medium text-right">Actions</th>
            </tr></thead>
            <tbody>
              {delegations.map(d => (
                <tr key={d.id} className="border-b last:border-0">
                  <td className="px-4 py-2.5 font-medium text-ink">{d.delegateeUsername}</td>
                  <td className="px-4 py-2.5">{d.departmentCode}</td>
                  <td className="px-4 py-2.5 text-ink-soft">{d.fromDate} → {d.toDate}</td>
                  <td className="px-4 py-2.5">
                    <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${isActive(d) ? 'bg-pos-bg text-pos' : 'bg-ground text-ink-soft'}`}>
                      {isActive(d) ? t('delegations.active', 'Active') : t('delegations.inactive', 'Inactive')}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <button onClick={() => setRevokeTargetId(d.id)} className="text-ink-faint hover:text-crit" title={t('delegations.revoke', 'Révoquer')}>
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <ConfirmDialog
        open={revokeTargetId !== null}
        title={t('delegations.revokeConfirmTitle', 'Revoke this delegation?')}
        message={t('delegations.revokeConfirmBody', "You will no longer be able to approve on this delegate's behalf once revoked.")}
        variant="danger"
        onConfirm={() => { if (revokeTargetId) revoke.mutate(revokeTargetId) }}
        onCancel={() => setRevokeTargetId(null)}
      />
    </div>
    </PageRoleGuard>
  )
}
