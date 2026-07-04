import type { ReactNode } from 'react'
import { ShieldOff } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAppSelector } from '@/store/hooks'
import type { UserRole } from '@/store/slices/authSlice'

interface RoleGuardProps {
  allowedRoles: UserRole[]
  children: ReactNode
  fallback?: ReactNode
}

const DefaultFallback = () => {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col items-center justify-center h-64 gap-3 text-gray-400">
      <ShieldOff className="w-10 h-10" />
      <p className="text-sm font-medium">{t('roleGuard.unauthorized')}</p>
      <p className="text-xs text-gray-400">{t('roleGuard.unauthorizedDetail')}</p>
    </div>
  )
}

/**
 * RoleGuard: Conditionally renders children based on user role.
 * - Without fallback prop: silently hides content (null) — use in nav/sidebar
 * - With fallback={<AccessDenied />}: shows access-denied UI — use on full pages
 */
export function RoleGuard({ allowedRoles, children, fallback = null }: RoleGuardProps) {
  const { user } = useAppSelector((state) => state.auth)

  if (!user) return <>{fallback}</>

  const hasRole = user.roles.some((role) => allowedRoles.includes(role))
  return hasRole ? <>{children}</> : <>{fallback}</>
}

/**
 * PageRoleGuard: For full pages — shows access-denied when user lacks role.
 */
export function PageRoleGuard({ allowedRoles, children }: Omit<RoleGuardProps, 'fallback'>) {
  const { user } = useAppSelector((state) => state.auth)

  if (!user) return <DefaultFallback />

  const hasRole = user.roles.some((role) => allowedRoles.includes(role))
  return hasRole ? <>{children}</> : <DefaultFallback />
}
