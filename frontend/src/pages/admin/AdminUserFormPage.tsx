import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2, ArrowLeft } from 'lucide-react'
import type { ApiResponse, PagedResponse } from '@/types/invoice'
import { ROLE_OPTIONS, DEPT_REQUIRED_ROLES } from '@/constants/roles'

interface Department { id: string; code: string; nameEn: string; nameFr: string }

const userSchema = z.object({
  username:   z.string().min(1),
  email:      z.string().email(),
  firstName:  z.string().min(1),
  lastName:   z.string().min(1),
  password:   z.string().min(8, 'Minimum 8 characters'),
  role:       z.string().min(1, 'Select a role'),
  departmentId: z.string().optional(),
  employeeId: z.string().optional(),
  approvalLimit: z.preprocess(
    (v) => (v === '' || v === null || v === undefined ? undefined : Number(v)),
    z.number({ invalid_type_error: 'Enter a valid number' }).nonnegative('Must be ≥ 0').optional(),
  ),
  preferredLang: z.enum(['fr', 'en']),
})

type UserFormData = z.infer<typeof userSchema>

export default function AdminUserFormPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const { data: deptData } = useQuery({
    queryKey: ['departments'],
    queryFn: async () => {
      const { data } = await apiClient.get<ApiResponse<PagedResponse<Department>>>('/departments')
      return data.data?.content ?? []
    },
  })

  const {
    register,
    handleSubmit,
    watch,
    control,
    formState: { errors, isSubmitting },
  } = useForm<UserFormData>({
    resolver: zodResolver(userSchema),
    defaultValues: { preferredLang: 'fr' },
  })

  const selectedRole = watch('role')
  const needsDepartment = DEPT_REQUIRED_ROLES.has(selectedRole)

  const createUserMutation = useMutation({
    mutationFn: (data: UserFormData) => {
      const payload = {
        username:     data.username,
        email:        data.email,
        firstName:    data.firstName,
        lastName:     data.lastName,
        password:     data.password,
        roles:        [data.role],
        departmentId: data.departmentId || null,
        employeeId:   data.employeeId?.trim() || null,
        approvalLimit: data.approvalLimit ?? null,
        preferredLang: data.preferredLang,
      }
      return apiClient.post('/users', payload)
    },
    onSuccess: () => navigate('/admin/users'),
  })

  const onSubmit = (data: UserFormData) => createUserMutation.mutateAsync(data)

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/admin/users')}
          className="p-2 hover:bg-gray-100 rounded-full transition-colors"
        >
          <ArrowLeft className="w-5 h-5 text-gray-600" />
        </button>
        <h1 className="text-2xl font-bold text-gray-900">
          {t('admin.users.create', 'Add User')}
        </h1>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="bg-white rounded-xl border p-6 space-y-5">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">

          {/* First Name */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.firstName', 'First Name')} *
            </label>
            <input
              {...register('firstName')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.firstName && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>

          {/* Last Name */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.lastName', 'Last Name')} *
            </label>
            <input
              {...register('lastName')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.lastName && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>

          {/* Username */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.username', 'Username')} *
            </label>
            <input
              {...register('username')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.username && <p className="text-xs text-red-500 mt-1">{t('validation.required')}</p>}
          </div>

          {/* Email */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.email', 'Email')} *
            </label>
            <input
              type="email"
              {...register('email')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.email && <p className="text-xs text-red-500 mt-1">{t('validation.invalidEmail')}</p>}
          </div>

          {/* Password */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.password', 'Password')} *
            </label>
            <input
              type="password"
              {...register('password')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.password && <p className="text-xs text-red-500 mt-1">{errors.password.message}</p>}
          </div>

          {/* Preferred Language */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.preferredLang', 'Preferred Language')}
            </label>
            <select
              {...register('preferredLang')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            >
              <option value="fr">Français</option>
              <option value="en">English</option>
            </select>
          </div>

          {/* Role — dropdown, not free text */}
          <div className="md:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.role', 'Role')} *
            </label>
            <Controller
              name="role"
              control={control}
              render={({ field }) => (
                <select
                  {...field}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                  <option value="">{t('admin.users.selectRole', 'Select a role...')}</option>
                  {ROLE_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
              )}
            />
            {errors.role && <p className="text-xs text-red-500 mt-1">{errors.role.message}</p>}
          </div>

          {/* Department — only shown for department-specific roles */}
          {needsDepartment && (
            <div className="md:col-span-2">
              <label className="block text-sm font-medium text-gray-700 mb-1">
                {t('admin.users.department', 'Department')} *
              </label>
              <Controller
                name="departmentId"
                control={control}
                render={({ field }) => (
                  <select
                    {...field}
                    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
                  >
                    <option value="">{t('admin.users.selectDepartment', 'Select a department...')}</option>
                    {(deptData ?? []).map((dept) => (
                      <option key={dept.id} value={dept.id}>
                        {dept.nameEn} ({dept.code})
                      </option>
                    ))}
                  </select>
                )}
              />
              <p className="text-xs text-gray-400 mt-1">
                {t('admin.users.deptHint', 'Required for department-specific approver roles.')}
              </p>
            </div>
          )}

          {/* Employee ID */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.employeeId', 'Employee ID')}
            </label>
            <input
              {...register('employeeId')}
              placeholder={t('admin.users.employeeIdPlaceholder', 'e.g. EMP-0042')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            {errors.employeeId && <p className="text-xs text-red-500 mt-1">{errors.employeeId.message}</p>}
          </div>

          {/* Approval Limit */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              {t('admin.users.approvalLimit', 'Approval Limit')}
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              {...register('approvalLimit')}
              placeholder={t('admin.users.approvalLimitPlaceholder', 'e.g. 50000')}
              className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30"
            />
            <p className="text-xs text-gray-400 mt-1">
              {t('admin.users.approvalLimitHint', 'Maximum invoice amount this user may approve (optional).')}
            </p>
            {errors.approvalLimit && <p className="text-xs text-red-500 mt-1">{errors.approvalLimit.message}</p>}
          </div>
        </div>

        {createUserMutation.isError && (
          <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
            {t('admin.users.createError', 'Failed to create user. Please check the inputs and try again.')}
          </p>
        )}

        <div className="flex items-center justify-end gap-3 pt-2 border-t">
          <button
            type="button"
            onClick={() => navigate('/admin/users')}
            className="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 transition-colors"
          >
            {t('app.cancel', 'Cancel')}
          </button>
          <button
            type="submit"
            disabled={isSubmitting || createUserMutation.isPending}
            className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60 transition-colors"
          >
            {(isSubmitting || createUserMutation.isPending) && <Loader2 className="w-4 h-4 animate-spin" />}
            {t('admin.users.create', 'Create User')}
          </button>
        </div>
      </form>
    </div>
  )
}
