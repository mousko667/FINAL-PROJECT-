import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import apiClient from '@/services/apiClient'
import { Loader2, ArrowLeft } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'

const departmentSchema = z.object({
  name: z.string().min(1),
  code: z.string().min(1),
  budget: z.coerce.number().min(0),
  currency: z.string().min(1),
})

type DepartmentFormData = z.infer<typeof departmentSchema>

export default function AdminDepartmentFormPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<DepartmentFormData>({ resolver: zodResolver(departmentSchema) })

  const createDeptMutation = useMutation({
    mutationFn: (data: DepartmentFormData) => {
      return apiClient.post('/departments', data)
    },
    onSuccess: () => {
      navigate('/admin/departments')
    }
  })

  const onSubmit = async (data: DepartmentFormData) => {
    await createDeptMutation.mutateAsync(data)
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/admin/departments')}
          className="p-2 hover:bg-gray-100 rounded-full transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-600" />
        </button>
        <h1 className="text-2xl font-bold text-gray-900">
          {t('admin.departments.create', 'Add Department')}
        </h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="bg-white rounded-xl border p-6 space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.departments.name', 'Department Name')} *
            </label>
            <input
              {...register('name')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.name && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.departments.code', 'Code')} *
            </label>
            <input
              {...register('code')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.code && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.departments.budget', 'Budget')} *
            </label>
            <input
              type="number"
              step="0.01"
              {...register('budget')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.budget && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.departments.currency', 'Currency')} *
            </label>
            <input
              {...register('currency')}
              defaultValue="EUR"
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.currency && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>
        </div>

        {createDeptMutation.isError && (
            <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
                Failed to create department. Please check the inputs and try again.
            </p>
        )}

        <div className="flex items-center justify-end gap-3 pt-2 border-t">
          <button
            type="button"
            onClick={() => navigate('/admin/departments')}
            className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 transition-colors"
          >
            {t('app.cancel', 'Cancel')}
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60 transition-colors"
          >
            {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('admin.departments.create', 'Create Department')}
          </button>
        </div>
      </form>
    </div>
  )
}
