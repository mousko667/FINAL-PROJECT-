import React, { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { CheckCircle, Loader2, Building, ShieldCheck, ShieldAlert, ShieldOff } from 'lucide-react'

const schema = z
  .object({
    companyName: z.string().min(1),
    taxId: z.string().min(1),
    email: z.string().email(),
    password: z.string().min(8),
    confirmPassword: z.string().min(8),
    firstName: z.string().min(1),
    lastName: z.string().min(1),
    contactPhone: z.string().optional(),
    bankDetails: z.string().optional(),
    address: z.string().optional(),
  })
  .refine((d) => d.password === d.confirmPassword, {
    // i18n key, resolved through t() at display time (project pattern PROB-006):
    // zod runs outside the component so it cannot call t() directly.
    message: 'validation.passwordMismatch',
    path: ['confirmPassword'],
  })

type FormData = z.infer<typeof schema>

export default function SupplierRegisterPage() {
  const { t } = useTranslation()
  const [success, setSuccess] = useState(false)
  const [serverError, setServerError] = useState('')

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<FormData>({ resolver: zodResolver(schema) })
  const password = watch('password') ?? ''
  const passwordStrength = [
    password.length >= 8,
    /[A-Z]/.test(password),
    /[0-9]/.test(password),
    /[^A-Za-z0-9]/.test(password),
  ].filter(Boolean).length

  const strengthMeta: { label: string; color: string; Icon: React.ElementType } =
    passwordStrength <= 1
      ? { label: t('auth.passwordStrength.weak', 'Weak'), color: 'text-crit', Icon: ShieldOff }
      : passwordStrength === 2
      ? { label: t('auth.passwordStrength.fair', 'Fair'), color: 'text-warn', Icon: ShieldAlert }
      : passwordStrength === 3
      ? { label: t('auth.passwordStrength.good', 'Good'), color: 'text-info', Icon: ShieldAlert }
      : { label: t('auth.passwordStrength.strong', 'Strong'), color: 'text-pos', Icon: ShieldCheck }

  const barColors = ['bg-crit', 'bg-warn', 'bg-info', 'bg-pos']
  const activeBarColor = barColors[Math.max(passwordStrength - 1, 0)]

  const onSubmit = async (data: FormData) => {
    setServerError('')
    try {
      await apiClient.post('/auth/register/supplier', { ...data, confirmPassword: undefined })
      setSuccess(true)
    } catch {
      setServerError(t('supplier.register.error', 'Registration failed. Please try again.'))
    }
  }

  if (success) {
    return (
      <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex items-center justify-center p-4">
        <div className="bg-surface rounded-2xl shadow-xl p-10 max-w-md w-full text-center">
          <CheckCircle className="w-16 h-16 text-pos mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-ink mb-2">{t('supplier.register.successTitle', 'Registration Submitted!')}</h2>
          <p className="text-ink-soft mb-6">{t('supplier.register.successMessage', 'Check your email to verify your account before logging in.')}</p>
          <Link to="/login" className="inline-block px-6 py-2.5 bg-primary text-primary-foreground rounded-[4px] font-medium hover:bg-primary/90 transition-colors">
            {t('auth.login', 'Login')}
          </Link>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex items-center justify-center p-4">
      <div className="bg-surface rounded-2xl shadow-xl p-8 w-full max-w-2xl">
        <div className="flex items-center gap-3 mb-8">
          <div className="p-2 bg-primary/10 rounded-[4px]">
            <Building className="w-6 h-6 text-primary" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-ink">{t('supplier.register.title', 'Register as Supplier')}</h1>
            <p className="text-sm text-ink-soft">{t('supplier.register.subtitle', 'Create your supplier account to submit invoices')}</p>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {/* Company Info */}
            <div className="md:col-span-2">
              <p className="text-xs font-semibold text-ink-faint uppercase tracking-wider mb-3">{t('supplier.register.companySection', 'Company Information')}</p>
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.fields.companyName')} *</label>
              <input {...register('companyName')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.companyName && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.fields.taxId')} *</label>
              <input {...register('taxId')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.taxId && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.fields.contactPhone')}</label>
              <input {...register('contactPhone')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.fields.address')}</label>
              <input {...register('address')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.fields.bankDetails')}</label>
              <input type="password" autoComplete="new-password" {...register('bankDetails')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
            </div>

            {/* Account Info */}
            <div className="md:col-span-2 pt-2">
              <p className="text-xs font-semibold text-ink-faint uppercase tracking-wider mb-3">{t('supplier.register.accountSection', 'Account Credentials')}</p>
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.register.firstName', 'First Name')} *</label>
              <input {...register('firstName')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.firstName && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.register.lastName', 'Last Name')} *</label>
              <input {...register('lastName')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.lastName && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('auth.email', 'Email')} *</label>
              <input type="email" {...register('email')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.email && <p className="text-xs text-crit mt-1">{t('validation.invalidEmail')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('auth.password')} *</label>
              <input type="password" autoComplete="new-password" {...register('password')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {password.length > 0 && (
                <>
                  <div className="mt-2 grid grid-cols-4 gap-1" aria-label="Password strength">
                    {[0, 1, 2, 3].map((index) => (
                      <div
                        key={index}
                        className={`h-1.5 rounded-full transition-colors ${index < passwordStrength ? activeBarColor : 'bg-ground'}`}
                      />
                    ))}
                  </div>
                  <p className={`flex items-center gap-1 text-xs mt-1 font-medium ${strengthMeta.color}`}>
                    <strengthMeta.Icon className="w-3 h-3" />
                    {strengthMeta.label}
                    {passwordStrength < 4 && (
                      <span className="font-normal text-ink-faint">
                        {' — '}{t('auth.passwordStrength.hint', 'Use uppercase, numbers and symbols to strengthen')}
                      </span>
                    )}
                  </p>
                </>
              )}
              {errors.password && <p className="text-xs text-crit mt-1">{t('supplier.register.passwordMin', 'Minimum 8 characters')}</p>}
            </div>
            <div>
              <label className="block text-sm font-medium text-ink-soft mb-1">{t('supplier.register.confirmPassword', 'Confirm Password')} *</label>
              <input type="password" autoComplete="new-password" {...register('confirmPassword')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              {errors.confirmPassword && <p className="text-xs text-crit mt-1">{t(errors.confirmPassword.message ?? 'validation.passwordMismatch')}</p>}
            </div>
          </div>

          {serverError && <p className="text-sm text-crit bg-crit-bg px-4 py-2 rounded-[4px]">{serverError}</p>}

          <div className="flex items-center justify-between pt-2">
            <Link to="/login" className="text-sm text-primary hover:underline">{t('supplier.register.alreadyHaveAccount', 'Already have an account?')} {t('auth.login')}</Link>
            <button type="submit" disabled={isSubmitting} className="flex items-center gap-2 px-6 py-2.5 bg-primary text-primary-foreground rounded-[4px] font-medium hover:bg-primary/90 disabled:opacity-60 transition-colors">
              {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
              {t('supplier.register.submit', 'Create Account')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
