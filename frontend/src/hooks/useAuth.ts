import { useMutation } from '@tanstack/react-query'
import { useAppDispatch } from '@/store/hooks'
import { setCredentials, logout } from '@/store/slices/authSlice'
import apiClient from '@/services/apiClient'
import { useNavigate } from 'react-router-dom'
import type { AuthUser } from '@/store/slices/authSlice'

interface LoginRequest {
  username: string
  password: string
}

interface LoginResponse {
  data: {
    accessToken: string
    refreshToken: string
    userId: string
    username: string
    roles: string[]
    supplierId?: string
  }
}

/**
 * useAuth — provides login and logout mutations with Redux side effects.
 * Suppliers are redirected to /supplier/dashboard after login.
 */
export function useAuth() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()

  const loginMutation = useMutation({
    mutationFn: (credentials: LoginRequest) =>
      apiClient.post<LoginResponse>('/auth/login', credentials),
    onSuccess: (response) => {
      const { accessToken, refreshToken, userId, username, roles, supplierId } =
        response.data.data
      dispatch(
        setCredentials({
          user: {
            id: userId,
            username,
            email: '',
            roles: roles as AuthUser['roles'],
            supplierId,
          },
          accessToken,
          refreshToken,
        })
      )
      // Suppliers have a separate portal layout
      if (roles.includes('ROLE_SUPPLIER')) {
        navigate('/supplier/dashboard')
      } else {
        navigate('/dashboard')
      }
    },
  })

  const logoutAction = () => {
    dispatch(logout())
    navigate('/login')
  }

  return {
    login: loginMutation.mutate,
    isLoggingIn: loginMutation.isPending,
    loginError: loginMutation.isError,
    logout: logoutAction,
  }
}
