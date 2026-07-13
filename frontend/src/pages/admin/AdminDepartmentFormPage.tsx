import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { Loader2, ArrowLeft } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { ROLE_OPTIONS } from '@/constants/roles'
import { Panel } from '@/components/ui/Panel'
import { PageHeader } from '@/components/ui/PageHeader'

// Matches the backend DepartmentCreateRequest: code, nameFr, nameEn, requiresN2, n1Role, n2Role (+ optional budget via update).
const departmentSchema = z.object({
  code: z.string().min(1),
  nameFr: z.string().min(1),
  nameEn: z.string().min(1),
  n1Role: z.string().min(1),
  requiresN2: z.boolean().default(false),
  n2Role: z.string().optional(),
  budget: z.coerce.number().min(0).optional(),
})

type DepartmentFormData = z.infer<typeof departmentSchema>

export default function AdminDepartmentFormPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<DepartmentFormData>({ resolver: zodResolver(departmentSchema), defaultValues: { requiresN2: false } })

  const requiresN2 = watch('requiresN2')

  const createDeptMutation = useMutation({
    mutationFn: async (data: DepartmentFormData) => {
      // 1) create with the backend's expected shape
      const { data: resp } = await apiClient.post('/departments', {
        code: data.code,
        nameFr: data.nameFr,
        nameEn: data.nameEn,
        requiresN2: data.requiresN2,
        n1Role: data.n1Role,
        n2Role: data.requiresN2 ? data.n2Role : null,
      })
      // 2) if a budget was provided, set it via the update endpoint (budget is update-only)
      const id = resp?.data?.id
      if (id && data.budget != null && !Number.isNaN(data.budget)) {
        await apiClient.put(`/departments/${id}`, { budget: data.budget })
      }
      return resp
    },
    onSuccess: () => navigate('/admin/departments'),
  })

  const onSubmit = async (data: DepartmentFormData) => {
    await createDeptMutation.mutateAsync(data)
  }

  const inputCls =
    'w-full border border-hairline rounded-[4px] px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30'

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <PageHeader
        title={
          <span className="flex items-center gap-4">
            <button
              onClick={() => navigate('/admin/departments')}
              className="p-2 hover:bg-white/10 rounded-full transition-colors"
            >
              <ArrowLeft className="w-5 h-5 text-white" aria-hidden />
            </button>
            {t('admin.departments.create', 'Créer un département')}
          </span>
        }
      />

      <Panel className="p-6">
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('admin.departments.code', 'Code')} *
            </label>
            <input {...register('code')} placeholder="ex. FIN" className={inputCls} />
            {errors.code && <p className="text-xs text-crit mt-1">{t('validation.required', 'Requis')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('admin.departments.budget', 'Budget annuel')}
            </label>
            <input type="number" step="0.01" {...register('budget')} placeholder="0" className={inputCls} />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('admin.departments.nameFr', 'Nom (FR)')} *
            </label>
            <input {...register('nameFr')} className={inputCls} />
            {errors.nameFr && <p className="text-xs text-crit mt-1">{t('validation.required', 'Requis')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('admin.departments.nameEn', 'Nom (EN)')} *
            </label>
            <input {...register('nameEn')} className={inputCls} />
            {errors.nameEn && <p className="text-xs text-crit mt-1">{t('validation.required', 'Requis')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-ink-soft mb-1">
              {t('admin.departments.n1Role', 'Rôle Validateur N1')} *
            </label>
            <select {...register('n1Role')} className={inputCls}>
              <option value="">{t('admin.departments.selectRole', '— Sélectionner —')}</option>
              {ROLE_OPTIONS.filter(r => r.value.startsWith('ROLE_VALIDATEUR_N1_')).map(r => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
            {errors.n1Role && <p className="text-xs text-crit mt-1">{t('validation.required', 'Requis')}</p>}
          </div>

          <div className="flex items-center gap-2 pt-7">
            <input id="requiresN2" type="checkbox" {...register('requiresN2')} className="w-4 h-4 accent-primary" />
            <label htmlFor="requiresN2" className="text-sm font-medium text-ink-soft">
              {t('admin.departments.requiresN2', 'Nécessite un Niveau 2')}
            </label>
          </div>

          {requiresN2 && (
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-ink-soft mb-1">
                {t('admin.departments.n2Role', 'Rôle Validateur N2')}
              </label>
              <select {...register('n2Role')} className={inputCls}>
                <option value="">{t('admin.departments.selectRole', '— Sélectionner —')}</option>
                {ROLE_OPTIONS.filter(r => r.value.startsWith('ROLE_VALIDATEUR_N2_')).map(r => (
                  <option key={r.value} value={r.value}>{r.label}</option>
                ))}
              </select>
            </div>
          )}
        </div>

        {createDeptMutation.isError && (
          <p className="text-sm text-crit bg-crit-bg p-3 rounded-[4px] border border-crit/30">
            {t('admin.departments.createError', 'Échec de la création. Vérifiez les champs (le code doit être unique).')}
          </p>
        )}

        <div className="flex items-center justify-end gap-3 pt-2 border-t border-hairline">
          <button
            type="button"
            onClick={() => navigate('/admin/departments')}
            className="px-4 py-2 border border-hairline rounded-[4px] text-sm hover:bg-ground transition-colors"
          >
            {t('app.cancel', 'Annuler')}
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-[4px] text-sm font-medium hover:bg-primary/90 disabled:opacity-60 transition-colors"
          >
            {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('admin.departments.create', 'Créer le département')}
          </button>
        </div>
        </form>
      </Panel>
    </div>
  )
}
