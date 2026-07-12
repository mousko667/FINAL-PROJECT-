import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { CheckCircle, Shield, ShieldCheck, ShieldOff, Loader2, QrCode, KeyRound } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import apiClient from '@/services/apiClient'
import { useAppSelector } from '@/store/hooks'
import { formatAmount } from '@/lib/format'
import { PageHeader } from '@/components/ui/PageHeader'

interface StaffProfile {
  id: string
  username: string
  email: string
  firstName: string
  lastName: string
  preferredLang?: string
  employeeId?: string
  departmentId?: string
  approvalLimit?: number
  roles: string[]
  mfaEnabled?: boolean
  mfaVerified?: boolean
}

interface Department {
  id: string
  code: string
  nameEn: string
  nameFr: string
}

interface MfaSetupData {
  qrCodeUrl: string
  secret: string
}

// Roles that require MFA (per CLAUDE.md security constraints)
const MFA_REQUIRED_ROLES = [
  'ROLE_DAF', 'ROLE_ADMIN',
  'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG', 'ROLE_VALIDATEUR_N1_FIN',
  'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO',
  'ROLE_VALIDATEUR_N1_TERM', 'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE', 'ROLE_VALIDATEUR_N1_INFRA',
  'ROLE_VALIDATEUR_N2_INFRA', 'ROLE_VALIDATEUR_N1_TECH',
  'ROLE_VALIDATEUR_N2_TECH',
]

function MfaSection({ profile }: { profile: StaffProfile }) {
  const { t } = useTranslation()
  const [setupData, setSetupData] = useState<MfaSetupData | null>(null)
  const [otpCode, setOtpCode] = useState('')
  const [confirmSuccess, setConfirmSuccess] = useState(false)
  const [confirmError, setConfirmError] = useState('')

  const isMfaRequired = profile.roles.some(r => MFA_REQUIRED_ROLES.includes(r))
  const isMfaActive = profile.mfaEnabled && profile.mfaVerified

  const setupMutation = useMutation({
    mutationFn: async () => {
      const { data } = await apiClient.post<{ data: MfaSetupData }>('/auth/mfa/setup')
      return data.data
    },
    onSuccess: (data) => {
      setSetupData(data)
      setOtpCode('')
      setConfirmError('')
    },
  })

  const confirmMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/auth/mfa/confirm', { otp: otpCode })
    },
    onSuccess: () => {
      setConfirmSuccess(true)
      setSetupData(null)
      setOtpCode('')
    },
    onError: () => {
      setConfirmError(t('mfa.invalidOtp', 'Code invalide. Vérifiez votre application et réessayez.'))
    },
  })

  return (
    <div className="bg-surface border border-hairline rounded-[4px] p-5 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Shield className="w-5 h-5 text-ink-soft" />
          <h2 className="font-semibold text-ink">{t('mfa.title', 'Authentification à deux facteurs (MFA)')}</h2>
        </div>
        {isMfaActive ? (
          <span className="flex items-center gap-1.5 text-xs font-semibold text-pos bg-pos-bg px-2.5 py-1 rounded-full">
            <ShieldCheck className="w-3.5 h-3.5" />
            {t('mfa.active', 'Activé')}
          </span>
        ) : (
          <span className="flex items-center gap-1.5 text-xs font-semibold text-crit bg-crit-bg px-2.5 py-1 rounded-full">
            <ShieldOff className="w-3.5 h-3.5" />
            {isMfaRequired
              ? t('mfa.requiredNotConfigured', 'Requis — non configuré')
              : t('mfa.inactive', 'Désactivé')}
          </span>
        )}
      </div>

      {isMfaRequired && !isMfaActive && (
        <div className="flex items-start gap-2 text-sm text-warn bg-warn-bg border border-warn/30 rounded-[4px] px-3 py-2.5">
          <ShieldOff className="w-4 h-4 shrink-0 mt-0.5 text-warn" />
          {t('mfa.mandatory', 'La MFA est obligatoire pour votre rôle. Vous devez la configurer avant votre prochaine connexion.')}
        </div>
      )}

      {isMfaActive && !setupData && (
        <p className="text-sm text-ink-soft">
          {t('mfa.configuredDesc', 'Votre compte est protégé par un code TOTP généré par votre application d\'authentification (Google Authenticator, Authy…).')}
        </p>
      )}

      {confirmSuccess && (
        <div className="flex items-center gap-2 text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] px-3 py-2.5">
          <CheckCircle className="w-4 h-4 shrink-0" />
          {t('mfa.confirmed', 'MFA activée avec succès ! Elle sera requise à votre prochaine connexion.')}
        </div>
      )}

      {/* Step 1 — Generate QR code */}
      {!isMfaActive && !setupData && !confirmSuccess && (
        <div className="space-y-3">
          <p className="text-sm text-ink-soft">
            {t('mfa.setupDesc', 'Scannez le QR code avec Google Authenticator, Authy ou toute application TOTP compatible.')}
          </p>
          <button
            type="button"
            onClick={() => setupMutation.mutate()}
            disabled={setupMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-oct-navy text-white text-sm font-medium rounded-[4px] hover:bg-oct-navy-light transition-colors disabled:opacity-60"
          >
            {setupMutation.isPending
              ? <Loader2 className="w-4 h-4 animate-spin" />
              : <QrCode className="w-4 h-4" />}
            {t('mfa.setup', 'Configurer la MFA')}
          </button>
        </div>
      )}

      {/* Step 2 — QR code displayed, enter OTP */}
      {setupData && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row gap-6 items-start">
            {/* Rendered locally — the otpauth URI embeds the TOTP secret and must
                never be sent to a third-party QR service. */}
            <div className="flex-shrink-0">
              <div
                className="border border-hairline rounded-[4px] p-2 bg-surface inline-block"
                role="img"
                aria-label={t('mfa.qrCodeAlt', 'QR code for MFA setup — scan with an authenticator app')}
              >
                <QRCodeSVG value={setupData.qrCodeUrl} size={144} marginSize={1} />
              </div>
            </div>
            <div className="space-y-3 flex-1 min-w-0">
              <div>
                <p className="text-xs font-semibold text-ink-soft uppercase tracking-wide mb-1">
                  {t('mfa.scanQr', '1. Scannez le QR code')}
                </p>
                <p className="text-sm text-ink-soft">
                  {t('mfa.scanQrDesc', 'Ouvrez votre application d\'authentification et scannez ce code.')}
                </p>
              </div>
              <div>
                <p className="text-xs font-semibold text-ink-soft uppercase tracking-wide mb-1">
                  {t('mfa.manualEntry', 'Ou saisissez manuellement la clé :')}
                </p>
                <code className="text-xs bg-ground border border-hairline rounded px-2 py-1 num break-all select-all">
                  {setupData.secret}
                </code>
              </div>
              <div>
                <p className="text-xs font-semibold text-ink-soft uppercase tracking-wide mb-1">
                  {t('mfa.enterCode', '2. Entrez le code à 6 chiffres')}
                </p>
                <div className="flex gap-2 items-center">
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    value={otpCode}
                    onChange={e => {
                      setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))
                      setConfirmError('')
                    }}
                    placeholder="000000"
                    className="w-32 border border-hairline rounded-[4px] px-3 py-2 text-sm num text-center tracking-widest focus:outline-none focus:ring-2 focus:ring-oct-navy/30"
                    onKeyDown={e => e.key === 'Enter' && otpCode.length === 6 && confirmMutation.mutate()}
                  />
                  <button
                    type="button"
                    onClick={() => confirmMutation.mutate()}
                    disabled={otpCode.length !== 6 || confirmMutation.isPending}
                    className="flex items-center gap-1.5 px-4 py-2 bg-primary text-primary-foreground text-sm font-medium rounded-[4px] hover:bg-primary/90 transition-colors disabled:opacity-50"
                  >
                    {confirmMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <KeyRound className="w-4 h-4" />}
                    {t('mfa.confirm', 'Confirmer')}
                  </button>
                </div>
                {confirmError && <p className="text-xs text-crit mt-1">{confirmError}</p>}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => { setSetupData(null); setOtpCode(''); setConfirmError('') }}
            className="text-xs text-ink-faint hover:text-ink-soft"
          >
            {t('app.cancel')}
          </button>
        </div>
      )}

      {/* Re-configure when already active */}
      {isMfaActive && !setupData && (
        <button
          type="button"
          onClick={() => setupMutation.mutate()}
          disabled={setupMutation.isPending}
          className="flex items-center gap-2 text-sm text-ink-soft hover:text-ink-soft transition-colors"
        >
          <QrCode className="w-4 h-4" />
          {t('mfa.reconfigure', 'Reconfigurer l\'application d\'authentification')}
        </button>
      )}
    </div>
  )
}

export default function ProfilePage() {
  const queryClient = useQueryClient()
  const { t, i18n } = useTranslation()
  const { register, handleSubmit, reset } = useForm<StaffProfile>()
  const roles = useAppSelector(s => s.auth.user?.roles ?? [])
  const isSupplier = roles.includes('ROLE_SUPPLIER')

  const { data } = useQuery({
    queryKey: ['staff-profile'],
    queryFn: async () => {
      const response = await apiClient.get<{ data: StaffProfile }>('/profile')
      reset(response.data.data)
      return response.data.data
    },
  })

  const { data: departments } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const res = await apiClient.get<{ data: { content: Department[] } }>('/departments')
      return res.data.data?.content ?? []
    },
    enabled: !isSupplier,
  })

  const departmentName = departments?.find(d => d.id === data?.departmentId)
    ? (i18n.language === 'fr'
        ? departments.find(d => d.id === data?.departmentId)!.nameFr
        : departments.find(d => d.id === data?.departmentId)!.nameEn)
    : data?.departmentId
      ? t('app.loading')
      : '—'

  const mutation = useMutation({
    mutationFn: async (payload: StaffProfile) => apiClient.put('/profile', {
      email: payload.email,
      firstName: payload.firstName,
      lastName: payload.lastName,
      preferredLang: payload.preferredLang,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff-profile'] })
    },
  })

  return (
    <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="max-w-4xl space-y-5 page-enter">
      <PageHeader title={t('profile.title')} subtitle={t('profile.subtitle')} />

      {mutation.isSuccess && (
        <div className="flex items-center gap-2 text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] px-4 py-3">
          <CheckCircle className="w-4 h-4 shrink-0" />
          {t('profile.saved')}
        </div>
      )}

      {/* Editable fields */}
      <div className="bg-surface border border-hairline rounded-[4px] p-5 space-y-1">
        <h2 className="font-semibold text-ink mb-4">{t('profile.title')}</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <label className="block text-sm font-medium text-ink-soft">
            {t('profile.username')}
            <input
              value={data?.username ?? ''}
              disabled
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-soft"
            />
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('profile.email')}
            <input
              {...register('email')}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('profile.firstName')}
            <input
              {...register('firstName')}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('profile.lastName')}
            <input
              {...register('lastName')}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('profile.preferredLanguage')}
            <select
              {...register('preferredLang')}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="fr">{t('profile.french')}</option>
              <option value="en">{t('profile.english')}</option>
            </select>
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('profile.employeeId')}
            <input
              value={data?.employeeId ?? '—'}
              disabled
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-soft"
            />
          </label>
        </div>
      </div>

      {/* Staff assignment — hidden for suppliers */}
      {!isSupplier && (
        <div className="bg-surface border border-hairline rounded-[4px] p-5">
          <h2 className="font-semibold text-ink mb-4">{t('profile.staffAssignment', 'Affectation')}</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <label className="block text-sm font-medium text-ink-soft">
              {t('profile.department')}
              <input
                value={departmentName}
                disabled
                className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-soft"
              />
            </label>
            <label className="block text-sm font-medium text-ink-soft">
              {t('profile.approvalLimit')}
              <input
                value={data?.approvalLimit != null
                  ? `${formatAmount(data.approvalLimit)} XAF`
                  : t('profile.approvalLimitNone')}
                disabled
                className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-soft"
              />
            </label>
          </div>
        </div>
      )}

      {/* Roles */}
      <div className="bg-surface border border-hairline rounded-[4px] p-5">
        <h2 className="font-semibold text-ink mb-3">{t('profile.roles')}</h2>
        <div className="flex flex-wrap gap-2">
          {(data?.roles ?? []).map((role) => (
            <span
              key={role}
              className="px-3 py-1 rounded-full bg-oct-navy/10 text-oct-navy text-xs font-medium"
            >
              {t(`roles.${role}`, role.replace('ROLE_', '').replace(/_/g, ' '))}
            </span>
          ))}
        </div>
      </div>

      {/* MFA section — hidden for suppliers (not required) */}
      {!isSupplier && data && <MfaSection profile={data} />}

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={mutation.isPending}
          className="bg-oct-navy text-white rounded-[4px] px-5 py-2 text-sm font-medium hover:bg-oct-navy-light disabled:opacity-60 transition-colors"
        >
          {mutation.isPending ? t('app.loading') : t('profile.save')}
        </button>
      </div>
    </form>
  )
}
