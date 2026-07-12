import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, CheckCircle, Building2, Lock } from 'lucide-react'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'

interface SupplierProfile {
  companyName: string
  taxId: string
  contactEmail: string
  contactPhone?: string
  address?: string
  bankDetails?: string
}

export default function SupplierProfilePage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<SupplierProfile>()

  const { data, isLoading } = useQuery({
    queryKey: ['supplier-profile'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierProfile }>('/supplier/profile')
      reset(data.data)
      return data.data
    },
  })

  const mutation = useMutation({
    mutationFn: async (payload: SupplierProfile) => apiClient.put('/supplier/profile', payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['supplier-profile'] }),
  })

  if (isLoading) {
    return <div className="flex justify-center py-20"><Loader2 className="w-8 h-8 animate-spin text-muted-foreground" /></div>
  }

  return (
    <form onSubmit={handleSubmit(values => mutation.mutate(values))} className="space-y-6 max-w-3xl">
      <PageHeader title={t('supplier.profile.title')} subtitle={t('supplier.profile.subtitle')} />

      {mutation.isSuccess && (
        <div className="flex items-center gap-2 text-sm text-pos bg-pos-bg border border-pos/30 rounded-[4px] px-4 py-3">
          <CheckCircle className="w-4 h-4" /> {t('supplier.profile.updated')}
        </div>
      )}

      {/* Company information */}
      <Panel className="p-5 space-y-4">
        <div className="flex items-center gap-2 mb-2">
          <Building2 className="w-4 h-4 text-primary" />
          <h2 className="font-semibold text-ink">{t('supplier.profile.companyInfo')}</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <label className="block text-sm font-medium text-ink-soft">
            {t('supplier.profile.companyName')} *
            <input {...register('companyName', { required: true })}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30" />
            {errors.companyName && <p className="text-xs text-crit mt-1">{t('validation.required')}</p>}
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('supplier.profile.taxId')}
            <input value={data?.taxId ?? ''} disabled
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-ground text-ink-faint num" />
            <p className="text-xs text-ink-faint mt-0.5">{t('supplier.profile.taxIdLocked')}</p>
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('supplier.profile.contactEmail')} *
            <input {...register('contactEmail', { required: true })} type="email"
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </label>
          <label className="block text-sm font-medium text-ink-soft">
            {t('supplier.profile.contactPhone')}
            <input {...register('contactPhone')}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </label>
          <label className="block text-sm font-medium text-ink-soft md:col-span-2">
            {t('supplier.profile.address')}
            <input {...register('address')}
              className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30" />
          </label>
        </div>
      </Panel>

      {/* Bank details — sensitive, write-only */}
      <Panel className="p-5 space-y-3">
        <div className="flex items-center gap-2">
          <Lock className="w-4 h-4 text-warn" />
          <h2 className="font-semibold text-ink">{t('supplier.profile.bankDetails')}</h2>
        </div>
        <p className="text-sm text-ink-soft">{t('supplier.profile.bankDetailsHint')}</p>
        <label className="block text-sm font-medium text-ink-soft">
          <textarea
            {...register('bankDetails')}
            rows={3}
            placeholder={t('supplier.profile.bankPlaceholder')}
            className="mt-1 w-full border border-hairline rounded-[4px] px-3 py-2 text-sm bg-surface text-ink focus:outline-none focus:ring-2 focus:ring-primary/30 resize-none"
          />
        </label>
        <p className="text-xs text-warn">{t('supplier.profile.bankKeepHint')}</p>
      </Panel>

      <div className="flex justify-end">
        <button type="submit" disabled={mutation.isPending}
          className="flex items-center gap-2 bg-primary text-primary-foreground rounded-[4px] px-5 py-2 text-sm font-medium hover:bg-primary/90 disabled:opacity-60 transition-colors">
          {mutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
          {t('supplier.profile.save')}
        </button>
      </div>
    </form>
  )
}
