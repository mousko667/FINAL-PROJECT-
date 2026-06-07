import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Shield, CheckCircle, Clock, Lock, Key, Users, Trash2 } from 'lucide-react'
import apiClient from '@/services/apiClient'

interface ActiveSession {
  id: string
  userId: string
  username: string
  ipAddress: string | null
  createdAt: string
  expiresAt: string
}

export default function SecuritySettingsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [mfaRequired, setMfaRequired]     = useState(true)
  const [sessionTimeout, setSessionTimeout] = useState(60)
  const [maxAttempts, setMaxAttempts]      = useState(5)
  const [minPassword, setMinPassword]      = useState(8)
  const [saved, setSaved] = useState(false)

  const { data: sessions = [], isLoading: sessionsLoading } = useQuery<ActiveSession[]>({
    queryKey: ['admin', 'sessions'],
    queryFn: () => apiClient.get('/api/v1/admin/sessions').then(r => r.data.data ?? []),
  })

  const revokeSession = useMutation({
    mutationFn: (userId: string) => apiClient.delete(`/api/v1/admin/sessions/user/${userId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'sessions'] }),
  })

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault()
    setSaved(true)
    setTimeout(() => setSaved(false), 3000)
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">{t('admin.security.title')}</h1>
        <p className="text-sm text-gray-500 mt-1">Configure system-wide security policies.</p>
      </div>

      {saved && (
        <div className="flex items-center gap-2 text-sm text-green-700 bg-green-50 border border-green-200 rounded-lg px-4 py-3">
          <CheckCircle className="w-4 h-4" />
          {t('admin.security.save')} — settings saved (simulation only in this version).
        </div>
      )}

      <form onSubmit={handleSave} className="space-y-4">
        {/* MFA */}
        <div className="bg-white rounded-xl border p-5">
          <div className="flex items-start gap-3">
            <Shield className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-gray-900">{t('admin.security.mfaPolicy')}</h2>
              <p className="text-sm text-gray-500 mt-0.5">TOTP-based two-factor authentication for all staff accounts.</p>
              <label className="flex items-center gap-3 mt-3 cursor-pointer">
                <input type="checkbox" checked={mfaRequired} onChange={e => setMfaRequired(e.target.checked)} className="w-4 h-4 accent-primary" />
                <span className="text-sm font-medium text-gray-700">{t('admin.security.mfaRequired')}</span>
              </label>
            </div>
          </div>
        </div>

        {/* Session Timeout */}
        <div className="bg-white rounded-xl border p-5">
          <div className="flex items-start gap-3">
            <Clock className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-gray-900">{t('admin.security.sessionTimeout')}</h2>
              <p className="text-sm text-gray-500 mt-0.5">Idle sessions are terminated after this many minutes.</p>
              <input
                type="number"
                value={sessionTimeout}
                onChange={e => setSessionTimeout(Number(e.target.value))}
                min={5} max={480}
                className="mt-2 w-32 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="ml-2 text-sm text-gray-500">minutes</span>
            </div>
          </div>
        </div>

        {/* Login Attempts */}
        <div className="bg-white rounded-xl border p-5">
          <div className="flex items-start gap-3">
            <Lock className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-gray-900">{t('admin.security.maxLoginAttempts')}</h2>
              <p className="text-sm text-gray-500 mt-0.5">Account is locked after this many consecutive failures.</p>
              <input
                type="number"
                value={maxAttempts}
                onChange={e => setMaxAttempts(Number(e.target.value))}
                min={3} max={10}
                className="mt-2 w-24 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="ml-2 text-sm text-gray-500">attempts</span>
            </div>
          </div>
        </div>

        {/* Password Policy */}
        <div className="bg-white rounded-xl border p-5">
          <div className="flex items-start gap-3">
            <Key className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-gray-900">{t('admin.security.passwordMinLength')}</h2>
              <p className="text-sm text-gray-500 mt-0.5">Minimum number of characters required for all passwords.</p>
              <input
                type="number"
                value={minPassword}
                onChange={e => setMinPassword(Number(e.target.value))}
                min={8} max={32}
                className="mt-2 w-24 border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="ml-2 text-sm text-gray-500">characters</span>
            </div>
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <button type="submit" className="px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90">
            {t('admin.security.save')}
          </button>
        </div>
      </form>

      {/* Active Sessions */}
      <div className="bg-white rounded-xl border p-5">
        <div className="flex items-center gap-3 mb-4">
          <Users className="w-5 h-5 text-primary" />
          <h2 className="font-semibold text-gray-900">Sessions actives</h2>
        </div>
        {sessionsLoading ? (
          <p className="text-sm text-gray-500">Chargement...</p>
        ) : sessions.length === 0 ? (
          <p className="text-sm text-gray-500">Aucune session active.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-500 border-b">
                  <th className="pb-2 pr-4">Utilisateur</th>
                  <th className="pb-2 pr-4">IP</th>
                  <th className="pb-2 pr-4">Créée le</th>
                  <th className="pb-2 pr-4">Expire le</th>
                  <th className="pb-2">Action</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map(s => (
                  <tr key={s.id} className="border-b last:border-0">
                    <td className="py-2 pr-4 font-medium text-gray-900">{s.username}</td>
                    <td className="py-2 pr-4 text-gray-500">{s.ipAddress ?? '—'}</td>
                    <td className="py-2 pr-4 text-gray-500">{new Date(s.createdAt).toLocaleString('fr-FR')}</td>
                    <td className="py-2 pr-4 text-gray-500">{new Date(s.expiresAt).toLocaleString('fr-FR')}</td>
                    <td className="py-2">
                      <button
                        onClick={() => revokeSession.mutate(s.userId)}
                        disabled={revokeSession.isPending}
                        className="flex items-center gap-1 text-xs text-red-600 hover:text-red-800 disabled:opacity-50"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        Révoquer
                      </button>
                    </td>
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
