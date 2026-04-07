import type { ReactNode } from 'react'
import { useAppSelector } from '@/store/hooks'
import type { UserRole } from '@/store/slices/authSlice'

interface RoleGuardProps {
  allowedRoles: UserRole[]
  children: ReactNode
  fallback?: ReactNode
}

/**
 * RoleGuard: Hides (does NOT redirect) UI elements when user lacks the required role.
 */
export function RoleGuard({ allowedRoles, children, fallback = null }: RoleGuardProps) {
  const { user } = useAppSelector((state) => state.auth)

  if (!user) return <>{fallback}</>

  const hasRole = user.roles.some((role) => allowedRoles.includes(role))
  return hasRole ? <>{children}</> : <>{fallback}</>
}
