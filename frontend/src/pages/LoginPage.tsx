import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate, Link } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useAppDispatch } from '@/store/hooks'
import { setCredentials } from '@/store/slices/authSlice'
import apiClient from '@/services/apiClient'
import { AlertCircle, Loader2, Globe, ShieldCheck, ArrowLeft } from 'lucide-react'

const loginSchema = z.object({
  username: z.string().min(1, 'validation.required'),
  password: z.string().min(1, 'validation.required'),
})

type LoginFormData = z.infer<typeof loginSchema>

interface LoginData {
  accessToken?: string
  refreshToken?: string
  userId?: string
  username?: string
  roles?: string[]
  session_timeout_minutes?: number
  // Two-step MFA challenge
  mfa_required?: boolean
  mfa_setup_required?: boolean
  pre_auth_token?: string
}

interface LoginResponse {
  data: LoginData
}

export default function LoginPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()

  // When the backend returns mfa_required, we hold the short-lived pre-auth token
  // and switch the card to the OTP-entry step.
  const [preAuthToken, setPreAuthToken] = useState<string | null>(null)
  const [otp, setOtp] = useState('')

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  // Establishes the authenticated session from a full login payload (post-password or post-OTP).
  const completeLogin = (data: LoginData) => {
    dispatch(
      setCredentials({
        user: {
          id: data.userId ?? '',
          username: data.username ?? '',
          email: '',
          roles: data.roles ?? [],
        },
        accessToken: data.accessToken ?? '',
        refreshToken: data.refreshToken ?? '',
        sessionTimeoutMinutes: data.session_timeout_minutes ?? null,
      })
    )
    if ((data.roles ?? []).includes('ROLE_SUPPLIER')) {
      navigate('/supplier/dashboard')
    } else {
      navigate('/dashboard')
    }
  }

  const loginMutation = useMutation({
    mutationFn: (data: LoginFormData) =>
      apiClient.post<LoginResponse>('/auth/login', data),
    onSuccess: (response) => {
      const data = response.data.data
      // Step 1 outcome: MFA challenge required → move to OTP step instead of failing.
      if (data.mfa_required && data.pre_auth_token) {
        setPreAuthToken(data.pre_auth_token)
        return
      }
      // mfa_setup_required carries a normal access token; the enforcement filter
      // routes the user to MFA setup after they land in the app.
      completeLogin(data)
    },
  })

  const mfaMutation = useMutation({
    mutationFn: () =>
      apiClient.post<LoginResponse>('/auth/mfa/validate', {
        preAuthToken,
        otp,
      }),
    onSuccess: (response) => completeLogin(response.data.data),
  })

  const cancelMfa = () => {
    setPreAuthToken(null)
    setOtp('')
    mfaMutation.reset()
  }

  const isMfaStep = preAuthToken !== null

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex items-center justify-center p-4">
      {/* Language switcher — top right corner */}
      <div className="absolute top-4 right-4 flex items-center gap-1">
        <Globe className="w-4 h-4 text-white/60" />
        <button
          onClick={() => i18n.changeLanguage('fr')}
          className={`text-sm px-2 py-1 rounded transition-colors ${i18n.language === 'fr' ? 'text-white font-semibold' : 'text-white/60 hover:text-white'}`}
        >
          FR
        </button>
        <span className="text-white/40">|</span>
        <button
          onClick={() => i18n.changeLanguage('en')}
          className={`text-sm px-2 py-1 rounded transition-colors ${i18n.language === 'en' ? 'text-white font-semibold' : 'text-white/60 hover:text-white'}`}
        >
          EN
        </button>
      </div>

      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary/10 mb-4">
              {isMfaStep ? (
                <ShieldCheck className="w-9 h-9 text-primary" />
              ) : (
                <svg
                  className="w-9 h-9 text-primary"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={1.5}
                    d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                  />
                </svg>
              )}
            </div>
            <h1 className="text-2xl font-bold text-gray-900">{t('app.name')}</h1>
            <p className="text-sm text-muted-foreground mt-1">
              {isMfaStep ? t('mfa.challengeTitle') : t('auth.login')}
            </p>
          </div>

          {isMfaStep ? (
            /* ── Step 2: MFA OTP entry ───────────────────────────── */
            <>
              {mfaMutation.isError && (
                <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 mb-6 text-sm">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  {t('mfa.invalidOtp')}
                </div>
              )}

              <p className="text-sm text-gray-500 mb-5 text-center">
                {t('mfa.challengeSubtitle')}
              </p>

              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (otp.length >= 6) mfaMutation.mutate()
                }}
                className="space-y-5"
              >
                <div>
                  <label htmlFor="otp" className="block text-sm font-medium text-gray-700 mb-1.5">
                    {t('mfa.codeLabel')}
                  </label>
                  <input
                    id="otp"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    autoFocus
                    maxLength={6}
                    value={otp}
                    onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-center text-lg tracking-[0.5em] font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                    placeholder="000000"
                  />
                </div>

                <button
                  type="submit"
                  disabled={mfaMutation.isPending || otp.length < 6}
                  className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                >
                  {mfaMutation.isPending ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      {t('app.loading')}
                    </>
                  ) : (
                    t('mfa.verify')
                  )}
                </button>
              </form>

              <button
                type="button"
                onClick={cancelMfa}
                className="mt-6 w-full flex items-center justify-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors"
              >
                <ArrowLeft className="w-4 h-4" />
                {t('auth.backToLogin', 'Retour à la connexion')}
              </button>
            </>
          ) : (
            /* ── Step 1: username + password ─────────────────────── */
            <>
              {loginMutation.isError && (
                <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 mb-6 text-sm">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  {(loginMutation.error as { response?: { status?: number } })?.response?.status === 423
                    ? t('auth.accountLocked', 'Your account is locked after too many failed attempts. Please contact an administrator.')
                    : t('auth.loginError')}
                </div>
              )}

              <form onSubmit={handleSubmit((d) => loginMutation.mutate(d))} className="space-y-5">
                <div>
                  <label
                    htmlFor="username"
                    className="block text-sm font-medium text-gray-700 mb-1.5"
                  >
                    {t('auth.username')}
                  </label>
                  <input
                    id="username"
                    type="text"
                    autoComplete="username"
                    {...register('username')}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                    placeholder="admin"
                  />
                  {errors.username && (
                    <p className="mt-1 text-xs text-red-600">{t(errors.username.message as string)}</p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="password"
                    className="block text-sm font-medium text-gray-700 mb-1.5"
                  >
                    {t('auth.password')}
                  </label>
                  <input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    {...register('password')}
                    className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                    placeholder="••••••••"
                  />
                  {errors.password && (
                    <p className="mt-1 text-xs text-red-600">{t(errors.password.message as string)}</p>
                  )}
                  <div className="mt-1.5 text-right">
                    <Link to="/forgot-password" className="text-xs text-primary hover:underline">
                      {t('auth.forgotPasswordLink', 'Mot de passe oublié ?')}
                    </Link>
                  </div>
                </div>

                <button
                  type="submit"
                  disabled={loginMutation.isPending}
                  className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                >
                  {loginMutation.isPending ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      {t('app.loading')}
                    </>
                  ) : (
                    t('auth.loginButton')
                  )}
                </button>
              </form>

              <div className="mt-6 pt-6 border-t text-center">
                <p className="text-sm text-gray-500">
                  {t('supplier.register.isSupplier', 'Are you a supplier?')}{' '}
                  <Link to="/register" className="text-primary font-medium hover:underline">
                    {t('supplier.register.linkText', 'Register')}
                  </Link>
                </p>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
