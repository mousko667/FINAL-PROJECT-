import { useAppSelector } from '@/store/hooks'
import type { UserRole } from '@/store/slices/authSlice'

/**
 * Returns true if the current user holds at least one of the given roles.
 *
 * Use it to gate data-fetching hooks (`enabled: canView`) so pages don't fire
 * API calls the backend will reject with 403 before a PageRoleGuard hides the
 * UI — the guard only blocks rendering, not the hooks above the return.
 */
export function useHasRole(...roles: UserRole[]): boolean {
  const user = useAppSelector((state) => state.auth.user)
  if (!user) return false
  return user.roles.some((r) => roles.includes(r))
}
