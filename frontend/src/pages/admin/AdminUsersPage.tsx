import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, Plus, Pencil, LockOpen, UserCheck, UserX, Upload, X, AlertCircle, CheckCircle, ShieldOff } from 'lucide-react'
import { ExportMenu } from '@/components/ui/ExportMenu'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageHeader } from '@/components/ui/PageHeader'
import { PageRoleGuard } from '@/components/auth/RoleGuard'

const ADMIN_ROLES = ['ROLE_ADMIN']

interface ImportRowError { line: number; username: string; message: string }
interface ImportResult { totalRows: number; created: number; failed: number; errors: ImportRowError[] }

/** Export/import toolbar for bulk user CSV (P11-16). */
function CsvToolbar({ from, to }: { from?: string, to?: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [report, setReport] = useState<ImportResult | null>(null)

  const importMutation = useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData()
      form.append('file', file)
      const { data } = await apiClient.post<ApiResponse<ImportResult>>('/users/import/csv', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return data.data
    },
    onSuccess: (result) => {
      setReport(result)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const onFileChosen = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) importMutation.mutate(file)
    e.target.value = '' // allow re-importing the same file
  }

  return (
    <div className="flex items-center gap-2">
      <ExportMenu 
        endpoint="/users/export" 
        filename="users_export" 
        params={{
          from: from ? new Date(from).toISOString() : undefined,
          to: to ? (() => {
            const d = new Date(to)
            d.setHours(23, 59, 59, 999)
            return d.toISOString()
          })() : undefined
        }} 
      />
      <button
        onClick={() => fileInputRef.current?.click()}
        disabled={importMutation.isPending}
        className="flex items-center gap-2 px-3 py-2 rounded-[4px] border border-white/30 text-sm font-medium text-white hover:bg-white/10 disabled:opacity-50"
      >
        {importMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
        {t('admin.users.importCsv')}
      </button>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" className="hidden" onChange={onFileChosen} />

      {report && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
          <div className="bg-surface rounded-[4px] shadow-lg max-w-lg w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between px-5 py-3 border-b">
              <h2 className="font-semibold text-ink">{t('admin.users.importReportTitle')}</h2>
              <button onClick={() => setReport(null)} className="text-ink-faint hover:text-ink-soft">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="px-5 py-4 space-y-3">
              <div className="flex items-center gap-4 text-sm">
                <span className="inline-flex items-center gap-1.5 text-pos">
                  <CheckCircle className="w-4 h-4" /> {t('admin.users.importCreated')}: {report.created}
                </span>
                <span className="inline-flex items-center gap-1.5 text-crit">
                  <AlertCircle className="w-4 h-4" /> {t('admin.users.importFailed')}: {report.failed}
                </span>
              </div>
              {report.errors.length > 0 && (
                <table className="w-full text-xs border-collapse">
                  <thead>
                    <tr className="text-left text-ink-soft border-b">
                      <th className="py-1 pr-3">{t('admin.users.importLine')}</th>
                      <th className="py-1 pr-3">{t('admin.users.username')}</th>
                      <th className="py-1">{t('admin.users.importReason')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.errors.map((err, i) => (
                      <tr key={i} className="border-b last:border-0">
                        <td className="py-1 pr-3 text-ink-soft">{err.line}</td>
                        <td className="py-1 pr-3 font-medium text-ink">{err.username || '—'}</td>
                        <td className="py-1 text-crit">{err.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

interface User {
  id: string
  username: string
  email: string
  firstName?: string
  lastName?: string
  fullName?: string
  roles: string[]
  departmentId?: string
  isActive?: boolean
  active?: boolean
}

function RoleBadge({ role }: { role: string }) {
  const { t } = useTranslation()
  return (
    <span className="text-xs bg-primary/10 text-primary px-2 py-0.5 rounded-full whitespace-nowrap">
      {t(`roles.${role}`, role.replace('ROLE_', '').replace(/_/g, ' '))}
    </span>
  )
}

interface EditUserModalProps {
  user: User
  onClose: () => void
}

function EditUserModal({ user, onClose }: EditUserModalProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [firstName, setFirstName] = useState(user.firstName ?? '')
  const [lastName, setLastName]  = useState(user.lastName ?? '')
  const [email, setEmail]        = useState(user.email ?? '')
  const [confirmAction, setConfirmAction] = useState<'toggleActive' | 'resetMfa' | null>(null)

  const updateMutation = useMutation({
    mutationFn: () => apiClient.put(`/users/${user.id}`, { firstName, lastName, email }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      onClose()
    },
  })

  const toggleActive = useMutation({
    mutationFn: () => apiClient.patch(`/users/${user.id}/activate?active=${!(user.isActive ?? user.active)}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setConfirmAction(null)
      onClose()
    },
  })

  const unlock = useMutation({
    mutationFn: () => apiClient.post(`/users/${user.id}/unlock`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      onClose()
    },
  })

  const resetMfa = useMutation({
    mutationFn: () => apiClient.post(`/users/${user.id}/mfa/reset`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
      setConfirmAction(null)
      onClose()
    },
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-surface rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-5" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-ink">{t('admin.users.edit')}</h2>
          <button onClick={onClose} className="text-ink-faint hover:text-ink-soft text-xl leading-none">×</button>
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.users.firstName')}</label>
            <input value={firstName} onChange={e => setFirstName(e.target.value)}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.users.lastName')}</label>
            <input value={lastName} onChange={e => setLastName(e.target.value)}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.users.email')}</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">{t('admin.users.username')}</label>
            <input value={user.username} disabled className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-faint" />
          </div>
        </div>

        <div className="flex flex-wrap gap-2 pt-2 border-t">
          <button onClick={() => setConfirmAction('toggleActive')} disabled={toggleActive.isPending}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-[4px] text-sm font-medium transition-colors ${(user.isActive ?? user.active) ? 'bg-crit-bg text-crit hover:bg-crit-bg' : 'bg-pos-bg text-pos hover:bg-pos-bg'}`}>
            {(user.isActive ?? user.active) ? <><UserX className="w-3.5 h-3.5" />{t('admin.users.deactivate')}</> : <><UserCheck className="w-3.5 h-3.5" />{t('admin.users.activate')}</>}
          </button>
          <button onClick={() => unlock.mutate()} disabled={unlock.isPending}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-[4px] text-sm font-medium bg-warn-bg text-warn hover:bg-warn-bg transition-colors">
            <LockOpen className="w-3.5 h-3.5" /> {t('admin.users.unlock')}
          </button>
          <button onClick={() => setConfirmAction('resetMfa')} disabled={resetMfa.isPending}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-[4px] text-sm font-medium bg-info-bg text-info hover:bg-info-bg transition-colors">
            <ShieldOff className="w-3.5 h-3.5" /> {t('admin.users.resetMfa', 'Réinitialiser MFA')}
          </button>
        </div>

        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground">{t('app.cancel')}</button>
          <button onClick={() => updateMutation.mutate()} disabled={updateMutation.isPending}
            className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60">
            {updateMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('app.save')}
          </button>
        </div>

        {updateMutation.isError && (
          <p className="text-xs text-crit bg-crit-bg p-2 rounded border border-crit/30">
            {t('admin.users.createError')}
          </p>
        )}
      </div>

      <ConfirmDialog
        open={confirmAction === 'toggleActive'}
        title={(user.isActive ?? user.active)
          ? t('admin.users.disableConfirmTitle', 'Disable this account?')
          : t('admin.users.activateConfirmTitle', 'Activate this account?')}
        message={(user.isActive ?? user.active)
          ? t('admin.users.disableConfirmBody', 'The user will no longer be able to sign in until the account is reactivated.')
          : t('admin.users.activateConfirmBody', 'The user will be able to sign in again.')}
        variant={(user.isActive ?? user.active) ? 'danger' : 'default'}
        onConfirm={() => toggleActive.mutate()}
        onCancel={() => setConfirmAction(null)}
      />
      <ConfirmDialog
        open={confirmAction === 'resetMfa'}
        title={t('admin.users.resetMfaConfirmTitle', 'Reset MFA for this account?')}
        message={t('admin.users.resetMfaConfirmBody', 'The user will need to set up their authenticator app again at their next login.')}
        variant="danger"
        onConfirm={() => resetMfa.mutate()}
        onCancel={() => setConfirmAction(null)}
      />
    </div>
  )
}

function AdminUsersPage() {
  const { t } = useTranslation()
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', from, to],
    queryFn: async () => {
      const params: any = { size: 100 }
      if (from) params.from = new Date(from).toISOString()
      if (to) {
        const d = new Date(to)
        d.setHours(23, 59, 59, 999)
        params.to = d.toISOString()
      }
      const { data } = await apiClient.get<ApiResponse<PagedResponse<User>>>('/users', { params })
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader
        title={t('admin.users.title')}
        subtitle={`${data?.totalElements ?? 0} users registered`}
        actions={
          <>
            <CsvToolbar from={from} to={to} />
            <Link
              to="/admin/users/new"
              className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-[4px] hover:bg-primary/90 text-sm font-medium transition-colors"
            >
              <Plus className="w-4 h-4" />
              {t('admin.users.create')}
            </Link>
          </>
        }
      />

      <div className="flex items-center gap-3 bg-surface p-4 rounded-[4px] border border-hairline flex-wrap">
        <input
          type="date"
          title={t('common.fromDate', 'From date')}
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          className="border border-hairline rounded-[4px] px-3 py-2 text-sm outline-none focus:border-primary"
        />
        <input
          type="date"
          title={t('common.toDate', 'To date')}
          value={to}
          onChange={(e) => setTo(e.target.value)}
          className="border border-hairline rounded-[4px] px-3 py-2 text-sm outline-none focus:border-primary"
        />
      </div>

      <div className="bg-surface rounded-[4px] border border-hairline overflow-x-auto">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-ground border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.users.fullName')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.users.username')}</th>
                <th className="text-left px-4 py-3 font-medium text-ink-soft">{t('admin.users.role')}</th>
                <th className="text-center px-4 py-3 font-medium text-ink-soft">{t('admin.users.active')}</th>
                <th className="text-right px-4 py-3 font-medium text-ink-soft">{t('app.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {(!data?.content || data.content.length === 0) ? (
                <tr><td colSpan={5} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td></tr>
              ) : data.content.map((user) => {
                const fullName = user.fullName ?? (`${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.username)
                const isActive = user.isActive ?? user.active ?? false
                return (
                  <tr key={user.id} className="hover:bg-ground">
                    <td className="px-4 py-3">
                      <div className="font-medium text-ink">{fullName}</div>
                      <div className="text-xs text-ink-faint">{user.email}</div>
                    </td>
                    <td className="px-4 py-3 text-ink-soft num text-xs">{user.username}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.map(r => <RoleBadge key={r} role={r} />)}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-block w-2 h-2 rounded-full ${isActive ? 'bg-pos' : 'bg-crit'}`} title={isActive ? 'Active' : 'Inactive'} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => setEditingUser(user)}
                        className="flex items-center gap-1.5 ml-auto px-3 py-1.5 text-sm border border-hairline rounded-[4px] hover:bg-ground text-ink-soft transition-colors"
                      >
                        <Pencil className="w-3.5 h-3.5" />
                        {t('app.edit')}
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {editingUser && <EditUserModal user={editingUser} onClose={() => setEditingUser(null)} />}
    </div>
  )
}


export default function AdminUsersPageWrapper() {
  return (
    <PageRoleGuard allowedRoles={ADMIN_ROLES}>
      <AdminUsersPage />
    </PageRoleGuard>
  )
}
