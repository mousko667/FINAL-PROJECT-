import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Shield, CheckCircle, Clock, Lock, Key, Users, Trash2, Loader2, AlertCircle } from 'lucide-react'
import apiClient from '@/services/apiClient'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PageHeader } from '@/components/ui/PageHeader'

interface ActiveSession {
  id: string
  userId: string
  username: string
  ipAddress: string | null
  createdAt: string
  expiresAt: string
}

interface SecurityPolicy {
  mfaRequired: boolean
  sessionTimeoutMinutes: number
  maxLoginAttempts: number
  minPasswordLength: number
  updatedAt: string
}

interface SecurityHealth {
  atRestEncryptionEnabled: boolean
  encryptedBankDetailRecords: number
  totalActiveUsers: number
  mfaEnabledUsers: number
  mfaAdoptionPercent: number
  lockedAccounts: number
  totalFailedLoginAttempts: number
  webhookDeliverySuccessRate: number
}

export default function SecuritySettingsPage() {
  const { t, i18n } = useTranslation()
  const dateLocale = i18n.language === 'en' ? 'en-GB' : 'fr-FR'
  const queryClient = useQueryClient()
  const [mfaRequired, setMfaRequired]     = useState(true)
  const [sessionTimeout, setSessionTimeout] = useState(60)
  const [maxAttempts, setMaxAttempts]      = useState(5)
  const [minPassword, setMinPassword]      = useState(8)
  const [saved, setSaved] = useState(false)
  const [formError, setFormError] = useState('')
  const [revokeTargetUserId, setRevokeTargetUserId] = useState<string | null>(null)

  const { data: policy, isLoading: policyLoading } = useQuery<SecurityPolicy>({
    queryKey: ['security-policy'],
    queryFn: () => apiClient.get('/admin/security-policy').then(r => r.data.data),
  })

  useEffect(() => {
    if (policy) {
      setMfaRequired(policy.mfaRequired)
      setSessionTimeout(policy.sessionTimeoutMinutes)
      setMaxAttempts(policy.maxLoginAttempts)
      setMinPassword(policy.minPasswordLength)
    }
  }, [policy])

  const savePolicy = useMutation({
    mutationFn: () => apiClient.put('/admin/security-policy', {
      mfaRequired,
      sessionTimeoutMinutes: sessionTimeout,
      maxLoginAttempts: maxAttempts,
      minPasswordLength: minPassword,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['security-policy'] })
      setSaved(true)
      setFormError('')
      setTimeout(() => setSaved(false), 3000)
    },
    onError: (err: { response?: { data?: { message?: string } } }) => {
      setFormError(err?.response?.data?.message ?? t('admin.security.saveError', 'Could not save the security policy.'))
    },
  })

  const { data: sessions = [], isLoading: sessionsLoading } = useQuery<ActiveSession[]>({
    queryKey: ['admin', 'sessions'],
    queryFn: () => apiClient.get('/admin/sessions').then(r => r.data.data ?? []),
  })

  const revokeSession = useMutation({
    mutationFn: (userId: string) => apiClient.delete(`/admin/sessions/user/${userId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'sessions'] })
      setRevokeTargetUserId(null)
    },
  })

  // P11-53: security-health snapshot (REQ-24, partial — 2 of 8 items)
  const { data: health } = useQuery<SecurityHealth>({
    queryKey: ['admin', 'security-health'],
    queryFn: () => apiClient.get('/admin/security-health').then(r => r.data.data),
  })

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault()
    setFormError('')
    savePolicy.mutate()
  }

  return (
    <div className="space-y-6 max-w-2xl">
      <PageHeader
        title={t('admin.security.title')}
        subtitle={t('admin.security.subtitle', 'Configure system-wide security policies.')}
      />

      {/* P11-53: security-health dashboard */}
      {health && (
        <div className="bg-surface rounded-[4px] border border-hairline p-5">
          <div className="flex items-center gap-2 mb-4">
            <Shield className="w-5 h-5 text-primary" />
            <h2 className="font-semibold text-ink">{t('admin.security.health.title')}</h2>
          </div>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="rounded-[4px] border border-hairline p-4">
              <div className="flex items-center gap-2 text-xs text-ink-soft"><Lock className="w-4 h-4" />{t('admin.security.health.encryption')}</div>
              <div className="mt-1 text-lg font-bold text-ink">
                {health.atRestEncryptionEnabled ? t('admin.security.health.enabled') : t('admin.security.health.disabled')}
              </div>
              <div className="text-xs text-ink-faint">{t('admin.security.health.encryptedRecords', { count: health.encryptedBankDetailRecords })}</div>
            </div>
            <div className="rounded-[4px] border border-hairline p-4">
              <div className="flex items-center gap-2 text-xs text-ink-soft"><Key className="w-4 h-4" />{t('admin.security.health.mfaAdoption')}</div>
              <div className="mt-1 text-lg font-bold text-ink">{health.mfaAdoptionPercent.toFixed(0)}%</div>
              <div className="text-xs text-ink-faint">{health.mfaEnabledUsers} / {health.totalActiveUsers}</div>
            </div>
            <div className="rounded-[4px] border border-hairline p-4">
              <div className="flex items-center gap-2 text-xs text-ink-soft"><Users className="w-4 h-4" />{t('admin.security.health.loginFailures')}</div>
              <div className={`mt-1 text-lg font-bold ${health.lockedAccounts > 0 ? 'text-crit' : 'text-ink'}`}>{health.lockedAccounts}</div>
              <div className="text-xs text-ink-faint">{t('admin.security.health.failedAttempts', { count: health.totalFailedLoginAttempts })}</div>
            </div>
            <div className="rounded-[4px] border border-hairline p-4">
              <div className="flex items-center gap-2 text-xs text-ink-soft"><CheckCircle className="w-4 h-4" />{t('admin.security.health.webhookSuccess')}</div>
              <div className="mt-1 text-lg font-bold text-ink">{(health.webhookDeliverySuccessRate * 100).toFixed(0)}%</div>
              <div className="text-xs text-ink-faint">{t('admin.security.health.last7Days')}</div>
            </div>
          </div>
        </div>
      )}

      {saved && (
        <div className="flex items-center gap-2 text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] px-4 py-3">
          <CheckCircle className="w-4 h-4" />
          {t('admin.security.saved', 'Security policy saved.')}
        </div>
      )}
      {formError && (
        <div className="flex items-center gap-2 text-sm text-crit bg-crit-bg border border-crit/30 rounded-[4px] px-4 py-3">
          <AlertCircle className="w-4 h-4" /> {formError}
        </div>
      )}
      {policyLoading && (
        <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-ink-faint" /></div>
      )}

      <form onSubmit={handleSave} className="space-y-4">
        {/* MFA */}
        <div className="bg-surface rounded-[4px] border border-hairline p-5">
          <div className="flex items-start gap-3">
            <Shield className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-ink">{t('admin.security.mfaPolicy')}</h2>
              <p className="text-sm text-ink-soft mt-0.5">{t('admin.security.mfaDesc', 'TOTP-based two-factor authentication for all staff accounts.')}</p>
              <label className="flex items-center gap-3 mt-3 cursor-pointer">
                <input type="checkbox" checked={mfaRequired} onChange={e => setMfaRequired(e.target.checked)} className="w-4 h-4 accent-primary" />
                <span className="text-sm font-medium text-ink-soft">{t('admin.security.mfaRequired')}</span>
              </label>
            </div>
          </div>
        </div>

        {/* Session Timeout */}
        <div className="bg-surface rounded-[4px] border border-hairline p-5">
          <div className="flex items-start gap-3">
            <Clock className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-ink">{t('admin.security.sessionTimeout')}</h2>
              <p className="text-sm text-ink-soft mt-0.5">{t('admin.security.sessionTimeoutDesc', 'Idle sessions are terminated after this many minutes.')}</p>
              <input
                type="number"
                value={sessionTimeout}
                onChange={e => setSessionTimeout(Number(e.target.value))}
                min={5} max={480}
                className="mt-2 w-32 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="ml-2 text-sm text-ink-soft">{t('admin.security.minutes', 'minutes')}</span>
              <p className="text-xs text-ink-faint mt-1">
                {t('admin.security.sessionTimeoutNote', 'Applies to new sign-ins (access-token lifetime). Tokens already issued keep their current expiry.')}
              </p>
            </div>
          </div>
        </div>

        {/* Login Attempts */}
        <div className="bg-surface rounded-[4px] border border-hairline p-5">
          <div className="flex items-start gap-3">
            <Lock className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-ink">{t('admin.security.maxLoginAttempts')}</h2>
              <p className="text-sm text-ink-soft mt-0.5">{t('admin.security.maxLoginAttemptsDesc', 'Account is locked after this many consecutive failures.')}</p>
              <input
                type="number"
                value={maxAttempts}
                onChange={e => setMaxAttempts(Number(e.target.value))}
                min={3} max={10}
                className="mt-2 w-24 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="ml-2 text-sm text-ink-soft">{t('admin.security.attempts', 'attempts')}</span>
            </div>
          </div>
        </div>

        {/* Password Policy */}
        <div className="bg-surface rounded-[4px] border border-hairline p-5">
          <div className="flex items-start gap-3">
            <Key className="w-5 h-5 text-primary mt-0.5" />
            <div className="flex-1">
              <h2 className="font-semibold text-ink">{t('admin.security.passwordMinLength')}</h2>
              <p className="text-sm text-ink-soft mt-0.5">{t('admin.security.passwordMinLengthDesc', 'Minimum number of characters required for all passwords.')}</p>
              <input
                type="number"
                value={minPassword}
                onChange={e => setMinPassword(Number(e.target.value))}
                min={8} max={32}
                className="mt-2 w-24 border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <span className="ml-2 text-sm text-ink-soft">{t('admin.security.characters', 'characters')}</span>
            </div>
          </div>
        </div>

        <div className="flex justify-end pt-2">
          <button
            type="submit"
            disabled={savePolicy.isPending}
            className="inline-flex items-center gap-1.5 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-50"
          >
            {savePolicy.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('admin.security.save')}
          </button>
        </div>
      </form>

      {/* Active Sessions */}
      <div className="bg-surface rounded-[4px] border border-hairline p-5">
        <div className="flex items-center gap-3 mb-4">
          <Users className="w-5 h-5 text-primary" />
          <h2 className="font-semibold text-ink">{t('admin.security.activeSessions', 'Active sessions')}</h2>
        </div>
        {sessionsLoading ? (
          <p className="text-sm text-ink-soft">{t('app.loading')}</p>
        ) : sessions.length === 0 ? (
          <p className="text-sm text-ink-soft">{t('admin.security.noActiveSessions', 'No active sessions.')}</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-ink-soft border-b">
                  <th className="pb-2 pr-4">{t('admin.security.sessionUser', 'User')}</th>
                  <th className="pb-2 pr-4">{t('admin.security.sessionIp', 'IP')}</th>
                  <th className="pb-2 pr-4">{t('admin.security.sessionCreated', 'Created')}</th>
                  <th className="pb-2 pr-4">{t('admin.security.sessionExpires', 'Expires')}</th>
                  <th className="pb-2">{t('admin.security.sessionAction', 'Action')}</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map(s => (
                  <tr key={s.id} className="border-b last:border-0">
                    <td className="py-2 pr-4 font-medium text-ink">{s.username}</td>
                    <td className="py-2 pr-4 text-ink-soft">{s.ipAddress ?? '—'}</td>
                    <td className="py-2 pr-4 text-ink-soft">{new Date(s.createdAt).toLocaleString(dateLocale)}</td>
                    <td className="py-2 pr-4 text-ink-soft">{new Date(s.expiresAt).toLocaleString(dateLocale)}</td>
                    <td className="py-2">
                      <button
                        onClick={() => setRevokeTargetUserId(s.userId)}
                        disabled={revokeSession.isPending}
                        className="flex items-center gap-1 text-xs text-crit hover:text-crit disabled:opacity-50"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        {t('admin.security.revoke', 'Log out all sessions')}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={revokeTargetUserId !== null}
        title={t('admin.security.revokeSessionConfirmTitle', 'Revoke this session?')}
        message={t('admin.security.revokeSessionConfirmBody', 'The user will be signed out immediately and will need to log in again.')}
        variant="danger"
        onConfirm={() => { if (revokeTargetUserId) revokeSession.mutate(revokeTargetUserId) }}
        onCancel={() => setRevokeTargetUserId(null)}
      />
    </div>
  )
}
