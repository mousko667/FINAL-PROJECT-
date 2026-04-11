import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useMutation } from '@tanstack/react-query'
import { useAppDispatch } from '@/store/hooks'
import { setCredentials } from '@/store/slices/authSlice'
import apiClient from '@/services/apiClient'
import { AlertCircle, Loader2 } from 'lucide-react'

const loginSchema = z.object({
  username: z.string().min(1, 'validation.required'),
  password: z.string().min(1, 'validation.required'),
})

type LoginFormData = z.infer<typeof loginSchema>

interface LoginResponse {
  data: {
    accessToken: string
    refreshToken: string
    userId: string
    username: string
    roles: string[]
  }
}

export default function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  const loginMutation = useMutation({
    mutationFn: (data: LoginFormData) =>
      apiClient.post<LoginResponse>('/auth/login', data),
    onSuccess: (response) => {
      const { accessToken, refreshToken, userId, username, roles } = response.data.data
      dispatch(
        setCredentials({
          user: {
            id: userId,
            username,
            email: '',
            roles,
          },
          accessToken,
          refreshToken,
        })
      )
      navigate('/dashboard')
    },
  })

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-blue-950 to-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Card */}
        <div className="bg-white rounded-2xl shadow-2xl p-8">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-2xl bg-primary/10 mb-4">
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
            </div>
            <h1 className="text-2xl font-bold text-gray-900">{t('app.name')}</h1>
            <p className="text-sm text-muted-foreground mt-1">{t('auth.login')}</p>
          </div>

          {/* Error */}
          {loginMutation.isError && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 rounded-lg px-4 py-3 mb-6 text-sm">
              <AlertCircle className="w-4 h-4 shrink-0" />
              {t('auth.loginError')}
            </div>
          )}

          {/* Form */}
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
        </div>
      </div>
    </div>
  )
}
