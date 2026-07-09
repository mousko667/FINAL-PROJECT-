import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { useSupplier, useCreateSupplier, useUpdateSupplier } from '@/api/suppliers'
import { Loader2, ArrowLeft } from 'lucide-react'

const SUPPLIER_CATEGORIES = ['GOODS', 'SERVICES', 'WORKS', 'CONSULTING'] as const

const supplierSchema = z.object({
  companyName: z.string().min(1),
  taxId: z.string().min(1),
  contactEmail: z.string().email(),
  contactPhone: z.string().optional(),
  address: z.string().optional(),
  bankDetails: z.string().optional(),
  category: z.enum(SUPPLIER_CATEGORIES).optional().or(z.literal('')),
})

type SupplierFormData = z.infer<typeof supplierSchema>

export default function SupplierFormPage() {
  const { id } = useParams<{ id: string }>()
  const isEdit = !!id
  const navigate = useNavigate()
  const { t } = useTranslation()

  const { data: existing, isLoading: loadingExisting } = useSupplier(id)

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SupplierFormData>({ resolver: zodResolver(supplierSchema) })

  useEffect(() => {
    if (existing) {
      reset({
        companyName: existing.companyName,
        taxId: existing.taxId,
        contactEmail: existing.contactEmail,
        contactPhone: existing.contactPhone ?? '',
        address: existing.address ?? '',
        bankDetails: '',
        category: existing.category ?? '',
      })
    }
  }, [existing, reset])

  const { mutateAsync: create } = useCreateSupplier()
  const { mutateAsync: update } = useUpdateSupplier(id)

  const onSubmit = async (data: SupplierFormData) => {
    const payload = { ...data, category: data.category ? data.category : null }
    if (isEdit) {
      await update(payload)
      navigate(`/admin/suppliers/${id}`)
    } else {
      const created = await create(payload)
      navigate(`/admin/suppliers/${created.id}`)
    }
  }

  if (isEdit && loadingExisting) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate(isEdit ? `/admin/suppliers/${id}` : '/admin/suppliers')}
          className="p-2 hover:bg-ground rounded-full transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-ink-soft" />
        </button>
        <h1 className="text-2xl font-bold text-ink">
          {isEdit ? t('supplier.edit', 'Edit Supplier') : t('supplier.create', 'Add Supplier')}
        </h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="bg-surface rounded-[4px] border border-hairline p-6 space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.companyName', 'Company Name')} *
            </label>
            <input
              {...register('companyName')}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.companyName && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.taxId', 'Tax ID')} *
            </label>
            <input
              {...register('taxId')}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.taxId && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.contactEmail', 'Contact Email')} *
            </label>
            <input
              type="email"
              {...register('contactEmail')}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.contactEmail && <p className="text-xs text-crit mt-1">{t('validation.invalidEmail')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.contactPhone', 'Phone')}
            </label>
            <input
              {...register('contactPhone')}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.address', 'Address')}
            </label>
            <input
              {...register('address')}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.category', 'Category')}
            </label>
            <select
              {...register('category')}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="">{t('supplier.category.none', 'Uncategorized')}</option>
              {SUPPLIER_CATEGORIES.map(c => (
                <option key={c} value={c}>{t(`supplier.category.${c}`, c)}</option>
              ))}
            </select>
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('supplier.fields.bankDetails', 'Bank Details')}
              <span className="ml-2 text-xs text-ink-faint font-normal">({t('supplier.fields.bankDetailsHint', 'write-only, stored encrypted')})</span>
            </label>
            <input
              type="password"
              autoComplete="new-password"
              {...register('bankDetails')}
              placeholder={isEdit ? '••••••••' : ''}
              className="w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
          </div>
        </div>

        <div className="flex items-center justify-end gap-3 pt-2 border-t">
          <button
            type="button"
            onClick={() => navigate(isEdit ? `/admin/suppliers/${id}` : '/admin/suppliers')}
            className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground transition-colors"
          >
            {t('app.cancel', 'Cancel')}
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60 transition-colors"
          >
            {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {isEdit ? t('app.save', 'Save') : t('supplier.create', 'Create Supplier')}
          </button>
        </div>
      </form>
    </div>
  )
}
