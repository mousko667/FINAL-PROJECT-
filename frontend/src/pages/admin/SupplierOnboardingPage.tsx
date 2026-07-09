import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, ChevronLeft, ChevronRight, Loader2 } from 'lucide-react'
import { useCreateSupplier } from '@/api/suppliers'

const SUPPLIER_CATEGORIES = ['GOODS', 'SERVICES', 'WORKS', 'CONSULTING'] as const

const onboardingSchema = z.object({
  companyName: z.string().min(1),
  taxId: z.string().min(1),
  category: z.enum(SUPPLIER_CATEGORIES).optional().or(z.literal('')),
  contactEmail: z.string().email(),
  contactPhone: z.string().optional(),
  address: z.string().optional(),
  bankDetails: z.string().min(1),
})

type SupplierOnboardingForm = z.infer<typeof onboardingSchema>

const DEFAULT_VALUES: SupplierOnboardingForm = {
  companyName: '',
  taxId: '',
  category: '',
  contactEmail: '',
  contactPhone: '',
  address: '',
  bankDetails: '',
}

export default function SupplierOnboardingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [step, setStep] = useState(1)

  const { mutateAsync: createSupplier, isPending } = useCreateSupplier()

  const {
    register,
    handleSubmit,
    trigger,
    watch,
    formState: { errors },
  } = useForm<SupplierOnboardingForm>({
    resolver: zodResolver(onboardingSchema),
    defaultValues: DEFAULT_VALUES,
    mode: 'onTouched',
  })

  const nextStep = async () => {
    const fields = step === 1
      ? ['companyName', 'taxId', 'category']
      : ['contactEmail', 'contactPhone', 'address']
    const isValid = await trigger(fields as never)
    if (isValid) {
      setStep((current) => Math.min(current + 1, 3))
    }
  }

  const previousStep = () => setStep((current) => Math.max(current - 1, 1))

  const onSubmit = handleSubmit(async (data) => {
    const created = await createSupplier({
      companyName: data.companyName,
      taxId: data.taxId,
      contactEmail: data.contactEmail,
      contactPhone: data.contactPhone || undefined,
      address: data.address || undefined,
      bankDetails: data.bankDetails,
      category: data.category ? data.category : null,
    })

    navigate(`/admin/suppliers/${created.id}`)
  })

  const summary = watch()

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div className="space-y-2">
        <h1 className="text-2xl font-bold text-ink">{t('supplier.onboarding.title', 'Supplier onboarding')}</h1>
        <p className="text-sm text-ink-soft">
          {t('supplier.onboarding.subtitle', 'Guide a supplier through creation, verification, and activation readiness.')}
        </p>
      </div>

      <div className="bg-surface rounded-[4px] border border-hairline p-5">
        <div className="flex items-center gap-3">
          {[1, 2, 3].map((item) => (
            <div
              key={item}
              className={`flex items-center gap-2 px-3 py-2 rounded-[4px] border text-sm ${
                step === item ? 'border-primary bg-primary/5 text-primary' : 'border-hairline text-ink-soft'
              }`}
            >
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-surface border border-hairline text-xs font-semibold">
                {item}
              </span>
              {t(`supplier.onboarding.step${item}`, `Step ${item}`)}
            </div>
          ))}
        </div>
      </div>

      <form onSubmit={onSubmit} className="bg-surface rounded-[4px] border border-hairline p-6 space-y-6">
        {step === 1 && (
          <section className="space-y-4" data-testid="supplier-onboarding-step-1">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div className="md:col-span-2">
                <label htmlFor="supplier-companyName" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.companyName', 'Company Name')} *
                </label>
                <input id="supplier-companyName" {...register('companyName')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm" />
                {errors.companyName && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
              </div>
              <div>
                <label htmlFor="supplier-taxId" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.taxId', 'Tax ID')} *
                </label>
                <input id="supplier-taxId" {...register('taxId')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm" />
                {errors.taxId && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
              </div>
              <div>
                <label htmlFor="supplier-category" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.category', 'Category')}
                </label>
                <select id="supplier-category" {...register('category')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface">
                  <option value="">{t('supplier.category.none', 'Uncategorized')}</option>
                  {SUPPLIER_CATEGORIES.map((category) => (
                    <option key={category} value={category}>
                      {t(`supplier.category.${category}`, category)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          </section>
        )}

        {step === 2 && (
          <section className="space-y-4" data-testid="supplier-onboarding-step-2">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div>
                <label htmlFor="supplier-contactEmail" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.contactEmail', 'Contact Email')} *
                </label>
                <input id="supplier-contactEmail" type="email" {...register('contactEmail')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm" />
                {errors.contactEmail && <p className="text-xs text-crit mt-1">{t('validation.invalidEmail')}</p>}
              </div>
              <div>
                <label htmlFor="supplier-contactPhone" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.contactPhone', 'Phone')}
                </label>
                <input id="supplier-contactPhone" {...register('contactPhone')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm" />
              </div>
              <div className="md:col-span-2">
                <label htmlFor="supplier-address" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.address', 'Address')}
                </label>
                <input id="supplier-address" {...register('address')} className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm" />
              </div>
            </div>
          </section>
        )}

        {step === 3 && (
          <section className="grid grid-cols-1 lg:grid-cols-[1.3fr_0.7fr] gap-5" data-testid="supplier-onboarding-step-3">
            <div className="space-y-4">
              <div>
                <label htmlFor="supplier-bankDetails" className="block text-sm font-medium text-ink-soft mb-1">
                  {t('supplier.fields.bankDetails', 'Bank Details')} *
                </label>
                <input
                  id="supplier-bankDetails"
                  type="password"
                  autoComplete="new-password"
                  {...register('bankDetails')}
                  className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm"
                />
                {errors.bankDetails && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
              </div>
              <p className="text-sm text-ink-soft">
                {t('supplier.onboarding.summaryHelp', 'Review the collected information before creating the supplier record.')}
              </p>
            </div>

            <aside className="rounded-[4px] border border-hairline bg-ground p-4 space-y-3">
              <h2 className="text-sm font-semibold text-ink">
                {t('supplier.onboarding.summaryTitle', 'Onboarding summary')}
              </h2>
              <dl className="space-y-2 text-sm">
                <div className="flex items-start justify-between gap-4">
                  <dt className="text-ink-soft">{t('supplier.fields.companyName', 'Company Name')}</dt>
                  <dd className="text-right font-medium text-ink">{summary.companyName || '—'}</dd>
                </div>
                <div className="flex items-start justify-between gap-4">
                  <dt className="text-ink-soft">{t('supplier.fields.taxId', 'Tax ID')}</dt>
                  <dd className="text-right font-medium text-ink">{summary.taxId || '—'}</dd>
                </div>
                <div className="flex items-start justify-between gap-4">
                  <dt className="text-ink-soft">{t('supplier.fields.contactEmail', 'Contact Email')}</dt>
                  <dd className="text-right font-medium text-ink">{summary.contactEmail || '—'}</dd>
                </div>
                <div className="flex items-start justify-between gap-4">
                  <dt className="text-ink-soft">{t('supplier.fields.category', 'Category')}</dt>
                  <dd className="text-right font-medium text-ink">{summary.category || t('supplier.category.none', 'Uncategorized')}</dd>
                </div>
              </dl>
              <div className="rounded-[4px] border border-pos/30 bg-pos-bg p-3 text-sm text-pos flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 mt-0.5 shrink-0" />
                <span>{t('supplier.onboarding.ready', 'The supplier can be created once the banking details are confirmed.')}</span>
              </div>
            </aside>
          </section>
        )}

        <div className="flex items-center justify-between pt-4 border-t">
          <button
            type="button"
            onClick={() => navigate('/admin/suppliers')}
            className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground"
          >
            {t('app.cancel', 'Cancel')}
          </button>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={previousStep}
              disabled={step === 1}
              className="flex items-center gap-2 px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground disabled:opacity-50"
            >
              <ChevronLeft className="w-4 h-4" />
              {t('supplier.onboarding.previous', 'Previous')}
            </button>

            {step < 3 ? (
              <button
                type="button"
                onClick={nextStep}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90"
              >
                {t('supplier.onboarding.next', 'Next')}
                <ChevronRight className="w-4 h-4" />
              </button>
            ) : (
              <button
                type="submit"
                disabled={isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60"
              >
                {isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                {t('supplier.onboarding.finish', 'Create supplier')}
              </button>
            )}
          </div>
        </div>
      </form>
    </div>
  )
}
