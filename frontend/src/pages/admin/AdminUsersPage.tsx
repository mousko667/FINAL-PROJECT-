import { useState, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { Loader2, Plus, Pencil, LockOpen, UserCheck, UserX, Download, Upload, X, AlertCircle, CheckCircle, ShieldOff } from 'lucide-react'

interface ImportRowError { line: number; username: string; message: string }
interface ImportResult { totalRows: number; created: number; failed: number; errors: ImportRowError[] }

/** Export/import toolbar for bulk user CSV (P11-16). */
function CsvToolbar() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [report, setReport] = useState<ImportResult | null>(null)
  const [exporting, setExporting] = useState(false)

  const handleExport = async () => {
    setExporting(true)
    try {
      // Authenticated blob download (apiClient attaches the JWT), then trigger a browser save.
      const res = await apiClient.get('/users/export/csv', { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: 'text/csv' }))
      const a = document.createElement('a')
      a.href = url
      a.download = 'users_export.csv'
      document.body.appendChild(a)
      a.click()
      a.remove()
      window.URL.revokeObjectURL(url)
    } finally {
      setExporting(false)
    }
  }

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
      <button
        onClick={handleExport}
        disabled={exporting}
        className="flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
      >
        {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
        {t('admin.users.exportCsv')}
      </button>
      <button
        onClick={() => fileInputRef.current?.click()}
        disabled={importMutation.isPending}
        className="flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50"
      >
        {importMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
        {t('admin.users.importCsv')}
      </button>
      <input ref={fileInputRef} type="file" accept=".csv,text/csv" className="hidden" onChange={onFileChosen} />

      {report && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
          <div className="bg-white rounded-xl shadow-lg max-w-lg w-full max-h-[80vh] overflow-auto">
            <div className="flex items-center justify-between px-5 py-3 border-b">
              <h2 className="font-semibold text-gray-900">{t('admin.users.importReportTitle')}</h2>
              <button onClick={() => setReport(null)} className="text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="px-5 py-4 space-y-3">
              <div className="flex items-center gap-4 text-sm">
                <span className="inline-flex items-center gap-1.5 text-green-700">
                  <CheckCircle className="w-4 h-4" /> {t('admin.users.importCreated')}: {report.created}
                </span>
                <span className="inline-flex items-center gap-1.5 text-red-600">
                  <AlertCircle className="w-4 h-4" /> {t('admin.users.importFailed')}: {report.failed}
                </span>
              </div>
              {report.errors.length > 0 && (
                <table className="w-full text-xs border-collapse">
                  <thead>
                    <tr className="text-left text-gray-500 border-b">
                      <th className="py-1 pr-3">{t('admin.users.importLine')}</th>
                      <th className="py-1 pr-3">{t('admin.users.username')}</th>
                      <th className="py-1">{t('admin.users.importReason')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.errors.map((err, i) => (
                      <tr key={i} className="border-b last:border-0">
                        <td className="py-1 pr-3 text-gray-500">{err.line}</td>
                        <td className="py-1 pr-3 font-medium text-gray-900">{err.username || '—'}</td>
                        <td className="py-1 text-red-600">{err.message}</td>
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
      onClose()
    },
  })

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-5" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-gray-900">{t('admin.users.edit')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl leading-none">×</button>
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.users.firstName')}</label>
            <input value={firstName} onChange={e => setFirstName(e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.users.lastName')}</label>
            <input value={lastName} onChange={e => setLastName(e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.users.email')}</label>
            <input type="email" value={email} onChange={e => setEmail(e.target.value)}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('admin.users.username')}</label>
            <input value={user.username} disabled className="w-full border rounded-lg px-3 py-2 text-sm bg-gray-50 text-gray-400" />
          </div>
        </div>

        <div className="flex flex-wrap gap-2 pt-2 border-t">
          <button onClick={() => toggleActive.mutate()} disabled={toggleActive.isPending}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${(user.isActive ?? user.active) ? 'bg-red-50 text-red-700 hover:bg-red-100' : 'bg-green-50 text-green-700 hover:bg-green-100'}`}>
            {(user.isActive ?? user.active) ? <><UserX className="w-3.5 h-3.5" />{t('admin.users.deactivate')}</> : <><UserCheck className="w-3.5 h-3.5" />{t('admin.users.activate')}</>}
          </button>
          <button onClick={() => unlock.mutate()} disabled={unlock.isPending}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-amber-50 text-amber-700 hover:bg-amber-100 transition-colors">
            <LockOpen className="w-3.5 h-3.5" /> {t('admin.users.unlock')}
          </button>
          <button onClick={() => resetMfa.mutate()} disabled={resetMfa.isPending}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium bg-blue-50 text-blue-700 hover:bg-blue-100 transition-colors">
            <ShieldOff className="w-3.5 h-3.5" /> {t('admin.users.resetMfa', 'Réinitialiser MFA')}
          </button>
        </div>

        <div className="flex justify-end gap-3">
          <button onClick={onClose} className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">{t('app.cancel')}</button>
          <button onClick={() => updateMutation.mutate()} disabled={updateMutation.isPending}
            className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60">
            {updateMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('app.save')}
          </button>
        </div>

        {updateMutation.isError && (
          <p className="text-xs text-red-600 bg-red-50 p-2 rounded border border-red-200">
            {t('admin.users.createError')}
          </p>
        )}
      </div>
    </div>
  )
}

export default function AdminUsersPage() {
  const { t } = useTranslation()
  const [editingUser, setEditingUser] = useState<User | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<User>>>('/users', { params: { size: 100 } })
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('admin.users.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{data?.totalElements ?? 0} users registered</p>
        </div>
        <div className="flex items-center gap-2">
          <CsvToolbar />
          <Link
            to="/admin/users/new"
            className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-lg hover:bg-primary/90 text-sm font-medium transition-colors"
          >
            <Plus className="w-4 h-4" />
            {t('admin.users.create')}
          </Link>
        </div>
      </div>

      <div className="bg-white rounded-xl border overflow-hidden">
        {isLoading ? (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.fullName')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.username')}</th>
                <th className="text-left px-4 py-3 font-medium text-gray-600">{t('admin.users.role')}</th>
                <th className="text-center px-4 py-3 font-medium text-gray-600">{t('admin.users.active')}</th>
                <th className="text-right px-4 py-3 font-medium text-gray-600">{t('app.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {(!data?.content || data.content.length === 0) ? (
                <tr><td colSpan={5} className="text-center py-16 text-muted-foreground">{t('app.noData')}</td></tr>
              ) : data.content.map((user) => {
                const fullName = user.fullName ?? (`${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.username)
                const isActive = user.isActive ?? user.active ?? false
                return (
                  <tr key={user.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-gray-900">{fullName}</div>
                      <div className="text-xs text-gray-400">{user.email}</div>
                    </td>
                    <td className="px-4 py-3 text-gray-500 font-mono text-xs">{user.username}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {user.roles.map(r => <RoleBadge key={r} role={r} />)}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`inline-block w-2 h-2 rounded-full ${isActive ? 'bg-green-500' : 'bg-red-400'}`} title={isActive ? 'Active' : 'Inactive'} />
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={() => setEditingUser(user)}
                        className="flex items-center gap-1.5 ml-auto px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-50 text-gray-700 transition-colors"
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
