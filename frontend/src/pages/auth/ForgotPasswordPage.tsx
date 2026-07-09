import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'

export default function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError('')
    try {
      await apiClient.post('/auth/forgot-password', { email })
      setSubmitted(true)
    } catch {
      setError(t('auth.forgotPassword.error', 'Unable to request password reset. Please try again.'))
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-ground px-4">
      <form onSubmit={submit} className="w-full max-w-md bg-surface border border-hairline rounded-[4px] p-6 space-y-4">
        <div>
          <h1 className="text-2xl font-bold text-ink">{t('auth.forgotPassword.title', 'Reset password')}</h1>
          <p className="text-sm text-ink-soft mt-1">{t('auth.forgotPassword.subtitle', 'Enter your account email to receive a reset link.')}</p>
        </div>
        {submitted ? (
          <div className="text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] p-3">
            {t('auth.forgotPassword.success', 'If an account exists for this email, a reset link has been sent.')}
          </div>
        ) : (
          <>
            <label className="block text-sm font-medium text-ink-soft">
              {t('auth.email', 'Email')}
              <input
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
            </label>
            {error && <p className="text-sm text-crit">{error}</p>}
            <button className="w-full bg-primary text-primary-foreground rounded-[4px] px-4 py-2 text-sm font-medium">
              {t('auth.forgotPassword.submit', 'Send reset link')}
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
