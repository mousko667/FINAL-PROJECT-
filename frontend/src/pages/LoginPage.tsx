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
import { isNetworkError, hasStatus } from '@/lib/apiError'
import { AlertCircle, Loader2, Globe, ShieldCheck, ArrowLeft, KeyRound } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'

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

  // When the backend returns mfa_setup_required, the enforcement filter only lets
  // /auth/mfa/setup and /auth/mfa/confirm through, so the whole first-time setup
  // (QR + confirmation OTP) has to happen here before entering the app.
  const [setupToken, setSetupToken] = useState<string | null>(null)
  const [setupData, setSetupData] = useState<{ qrCodeUrl: string; secret: string } | null>(null)
  const [setupOtp, setSetupOtp] = useState('')

  const {
    register,
    handleSubmit,
    getValues,
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
      // First login of a staff account without MFA: run the TOTP enrollment here —
      // the enforcement filter blocks every other endpoint until it is confirmed.
      if (data.mfa_setup_required && data.accessToken) {
        setSetupToken(data.accessToken)
        setupMutation.mutate(data.accessToken)
        return
      }
      completeLogin(data)
    },
  })

  const setupMutation = useMutation({
    mutationFn: (token: string) =>
      apiClient.post<{ data: { qrCodeUrl: string; secret: string } }>(
        '/auth/mfa/setup',
        {},
        { headers: { Authorization: `Bearer ${token}` } }
      ),
    onSuccess: (response) => setSetupData(response.data.data),
  })

  const setupConfirmMutation = useMutation({
    mutationFn: () =>
      apiClient.post(
        '/auth/mfa/confirm',
        { otp: setupOtp },
        { headers: { Authorization: `Bearer ${setupToken}` } }
      ),
    onSuccess: () => {
      // MFA is now active: replay the login so the regular OTP challenge takes over.
      setSetupToken(null)
      setSetupData(null)
      setSetupOtp('')
      loginMutation.mutate(getValues())
    },
  })

  const cancelSetup = () => {
    setSetupToken(null)
    setSetupData(null)
    setSetupOtp('')
    setupMutation.reset()
    setupConfirmMutation.reset()
  }

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
  const isSetupStep = setupToken !== null

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
        <div className="bg-surface rounded-2xl shadow-2xl p-8">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary/10 mb-4">
              {isMfaStep || isSetupStep ? (
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
            <h1 className="text-2xl font-bold text-ink">{t('app.name')}</h1>
            <p className="text-sm text-muted-foreground mt-1">
              {isSetupStep
                ? t('mfa.setupTitle', 'Activation de la double authentification')
                : isMfaStep
                  ? t('mfa.challengeTitle')
                  : t('auth.login')}
            </p>
          </div>

          {isSetupStep ? (
            /* ── First login: mandatory MFA enrollment (QR + OTP) ── */
            <>
              {setupMutation.isPending && (
                <div className="flex items-center justify-center py-10">
                  <Loader2 className="w-6 h-6 animate-spin text-primary" />
                </div>
              )}

              {setupData && (
                <div className="space-y-5">
                  <p className="text-sm text-ink-soft text-center">
                    {t('mfa.setupDesc', 'Scannez le QR code avec Google Authenticator, Authy ou toute application TOTP compatible.')}
                  </p>

                  <div className="flex justify-center">
                    {/* Rendered locally — the otpauth URI embeds the TOTP secret and must
                        never be sent to a third-party QR service. */}
                    <div
                      className="border border-hairline rounded-[4px] p-2 bg-surface"
                      role="img"
                      aria-label={t('mfa.qrCodeAlt', 'QR code for MFA setup — scan with an authenticator app')}
                    >
                      <QRCodeSVG value={setupData.qrCodeUrl} size={168} marginSize={1} />
                    </div>
                  </div>

                  <div className="text-center">
                    <p className="text-xs font-semibold text-ink-soft uppercase tracking-wide mb-1">
                      {t('mfa.manualEntry', 'Ou saisissez manuellement la clé :')}
                    </p>
                    <code className="text-xs bg-ground border border-hairline rounded px-2 py-1 num break-all select-all">
                      {setupData.secret}
                    </code>
                  </div>

                  {setupConfirmMutation.isError && (
                    <div className="flex items-center gap-2 bg-crit-bg border border-crit/30 text-crit rounded-[4px] px-4 py-3 text-sm">
                      <AlertCircle className="w-4 h-4 shrink-0" />
                      {isNetworkError(setupConfirmMutation.error)
                        ? t('error.network')
                        : t('mfa.invalidOtp', 'Code invalide. Vérifiez votre application et réessayez.')}
                    </div>
                  )}

                  <form
                    onSubmit={(e) => {
                      e.preventDefault()
                      if (setupOtp.length === 6) setupConfirmMutation.mutate()
                    }}
                    className="space-y-4"
                  >
                    <div>
                      <label htmlFor="setup-otp" className="block text-sm font-medium text-ink-soft mb-1.5">
                        {t('mfa.enterCode', '2. Entrez le code à 6 chiffres')}
                      </label>
                      <input
                        id="setup-otp"
                        type="text"
                        inputMode="numeric"
                        autoComplete="one-time-code"
                        autoFocus
                        maxLength={6}
                        value={setupOtp}
                        onChange={(e) => setSetupOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                        className="w-full px-3 py-2.5 border border-hairline-strong rounded-[4px] text-center text-lg tracking-[0.5em] font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                        placeholder="000000"
                      />
                    </div>

                    <button
                      type="submit"
                      disabled={setupConfirmMutation.isPending || setupOtp.length < 6}
                      className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
                    >
                      {setupConfirmMutation.isPending ? (
                        <>
                          <Loader2 className="w-4 h-4 animate-spin" />
                          {t('app.loading')}
                        </>
                      ) : (
                        <>
                          <KeyRound className="w-4 h-4" />
                          {t('mfa.confirm', 'Confirmer')}
                        </>
                      )}
                    </button>
                  </form>
                </div>
              )}

              <button
                type="button"
                onClick={cancelSetup}
                className="mt-6 w-full flex items-center justify-center gap-1.5 text-sm text-ink-soft hover:text-ink-soft transition-colors"
              >
                <ArrowLeft className="w-4 h-4" />
                {t('auth.backToLogin', 'Retour à la connexion')}
              </button>
            </>
          ) : isMfaStep ? (
            /* ── Step 2: MFA OTP entry ───────────────────────────── */
            <>
              {mfaMutation.isError && (
                <div className="flex items-center gap-2 bg-crit-bg border border-crit/30 text-crit rounded-[4px] px-4 py-3 mb-6 text-sm">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  {isNetworkError(mfaMutation.error)
                    ? t('error.network')
                    : t('mfa.invalidOtp')}
                </div>
              )}

              <p className="text-sm text-ink-soft mb-5 text-center">
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
                  <label htmlFor="otp" className="block text-sm font-medium text-ink-soft mb-1.5">
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
                    className="w-full px-3 py-2.5 border border-hairline-strong rounded-[4px] text-center text-lg tracking-[0.5em] font-mono focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                    placeholder="000000"
                  />
                </div>

                <button
                  type="submit"
                  disabled={mfaMutation.isPending || otp.length < 6}
                  className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
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
                className="mt-6 w-full flex items-center justify-center gap-1.5 text-sm text-ink-soft hover:text-ink-soft transition-colors"
              >
                <ArrowLeft className="w-4 h-4" />
                {t('auth.backToLogin', 'Retour à la connexion')}
              </button>
            </>
          ) : (
            /* ── Step 1: username + password ─────────────────────── */
            <>
              {loginMutation.isError && (
                <div className="flex items-center gap-2 bg-crit-bg border border-crit/30 text-crit rounded-[4px] px-4 py-3 mb-6 text-sm">
                  <AlertCircle className="w-4 h-4 shrink-0" />
                  {/* AUDIT-035: an unreachable backend rejects with NO response —
                      claiming "wrong credentials" made users retry passwords and
                      risk locking their own account. Only a real 401 says that. */}
                  {isNetworkError(loginMutation.error)
                    ? t('error.network')
                    : hasStatus(loginMutation.error, 423)
                      ? t('auth.accountLocked', 'Your account is locked after too many failed attempts. Please contact an administrator.')
                      : hasStatus(loginMutation.error, 401)
                        ? t('auth.loginError')
                        : t('error.server')}
                </div>
              )}

              <form onSubmit={handleSubmit((d) => loginMutation.mutate(d))} className="space-y-5">
                <div>
                  <label
                    htmlFor="username"
                    className="block text-sm font-medium text-ink-soft mb-1.5"
                  >
                    {t('auth.username')}
                  </label>
                  <input
                    id="username"
                    type="text"
                    autoComplete="username"
                    {...register('username')}
                    className="w-full px-3 py-2.5 border border-hairline-strong rounded-[4px] text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                    placeholder="admin"
                  />
                  {errors.username && (
                    <p className="mt-1 text-xs text-crit">{t(errors.username.message as string)}</p>
                  )}
                </div>

                <div>
                  <label
                    htmlFor="password"
                    className="block text-sm font-medium text-ink-soft mb-1.5"
                  >
                    {t('auth.password')}
                  </label>
                  <input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    {...register('password')}
                    className="w-full px-3 py-2.5 border border-hairline-strong rounded-[4px] text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 focus:border-primary transition-colors"
                    placeholder="••••••••"
                  />
                  {errors.password && (
                    <p className="mt-1 text-xs text-crit">{t(errors.password.message as string)}</p>
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
                  className="w-full flex items-center justify-center gap-2 py-2.5 px-4 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
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
                <p className="text-sm text-ink-soft">
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
