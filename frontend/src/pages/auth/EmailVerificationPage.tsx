import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { CheckCircle, XCircle, Loader2 } from 'lucide-react'

type State = 'loading' | 'success' | 'error'

export default function EmailVerificationPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const [state, setState] = useState<State>('loading')
  const token = searchParams.get('token')

  useEffect(() => {
    if (!token) { setState('error'); return }
    apiClient
      .get(`/auth/verify-email?token=${encodeURIComponent(token)}`)
      .then(() => setState('success'))
      .catch(() => setState('error'))
  }, [token])

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex items-center justify-center p-4">
      <div className="bg-surface rounded-2xl shadow-xl p-10 max-w-md w-full text-center">
        {state === 'loading' && (
          <>
            <Loader2 className="w-14 h-14 text-primary animate-spin mx-auto mb-4" />
            <h2 className="text-xl font-bold text-ink">{t('supplier.verify.verifying', 'Verifying your email…')}</h2>
          </>
        )}
        {state === 'success' && (
          <>
            <CheckCircle className="w-16 h-16 text-pos mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-ink mb-2">{t('supplier.verify.successTitle', 'Email Verified!')}</h2>
            <p className="text-ink-soft mb-6">{t('supplier.verify.successMessage', 'Your email has been verified. You can now log in.')}</p>
            <Link to="/login" className="inline-block px-6 py-2.5 bg-primary text-primary-foreground rounded-[4px] font-medium hover:bg-primary/90 transition-colors">
              {t('auth.login', 'Login')}
            </Link>
          </>
        )}
        {state === 'error' && (
          <>
            <XCircle className="w-16 h-16 text-crit mx-auto mb-4" />
            <h2 className="text-2xl font-bold text-ink mb-2">{t('supplier.verify.errorTitle', 'Verification Failed')}</h2>
            <p className="text-ink-soft mb-6">{t('supplier.verify.errorMessage', 'Invalid or expired token. Please request a new verification email.')}</p>
            <Link to="/register/supplier" className="text-primary hover:underline text-sm">
              {t('supplier.verify.retryRegister', 'Register again')}
            </Link>
          </>
        )}
      </div>
    </div>
  )
}
