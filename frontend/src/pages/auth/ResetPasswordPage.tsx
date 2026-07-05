import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'

export default function ResetPasswordPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const [newPassword, setNewPassword] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')
  const token = searchParams.get('token') ?? ''

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await apiClient.post('/auth/reset-password', { token, newPassword })
      setSubmitted(true)
    } catch {
      setError(t('auth.resetPassword.error', 'Invalid or expired reset link.'))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-ground px-4">
      <form onSubmit={submit} className="w-full max-w-md bg-surface border-hairline rounded-xl p-6 space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">{t('auth.resetPassword.title', 'Choose a new password')}</h1>
          <p className="text-sm text-ink-faint mt-1">{t('auth.resetPassword.subtitle', 'Use at least 8 characters.')}</p>
        </div>
        {submitted ? (
          <div className="text-sm text-emerald-700 bg-emerald-50 border border-emerald-100 rounded-lg p-3">
            {t('auth.resetPassword.success', 'Password reset successful. You can now sign in.')}
          </div>
        ) : (
          <>
            <label className="block text-sm font-medium text-ink-soft">
              {t('auth.resetPassword.newPassword', 'New password')}
              <input
                type="password"
                required
                minLength={8}
                value={newPassword}
                onChange={(event) => setNewPassword(event.target.value)}
                className="mt-1 w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </label>
            {error && <p className="text-sm text-crit">{error}</p>}
            <button disabled={!token} className="w-full bg-primary text-primary-foreground rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50">
              {t('auth.resetPassword.submit', 'Reset password')}
            </button>
          </>
        )}
        <Link to="/login" className="block text-center text-sm text-primary hover:underline">
          {t('auth.backToLogin', 'Back to login')}
        </Link>
      </form>
    </div>
  )
}
