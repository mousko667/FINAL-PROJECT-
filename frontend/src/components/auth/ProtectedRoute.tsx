import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'
import { selectIsSupplier } from '@/store/slices/authSlice'

/**
 * ProtectedRoute: Redirects to /login when not authenticated.
 * Also enforces role-based routing separation:
 *  - ROLE_SUPPLIER accessing staff routes → /supplier/dashboard
 *  - Staff roles accessing /supplier/* → /dashboard
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth)
  const isSupplier = useAppSelector(selectIsSupplier)
  const location = useLocation()

  if (!isAuthenticated) return <Navigate to="/login" replace />

  const isSupplierPath = location.pathname.startsWith('/supplier')
  const isAdminPath =
    location.pathname.startsWith('/admin') ||
    location.pathname.startsWith('/dashboard') ||
    location.pathname.startsWith('/invoices') ||
    location.pathname.startsWith('/reports')

  if (isSupplier && isAdminPath) {
    return <Navigate to="/supplier/dashboard" replace />
  }
  if (!isSupplier && isSupplierPath) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}

/**
 * SupplierRoute: ProtectedRoute restricted to ROLE_SUPPLIER only.
 */
export function SupplierRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth)
  const isSupplier = useAppSelector(selectIsSupplier)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!isSupplier) return <Navigate to="/dashboard" replace />
  return <Outlet />
}

/**
 * StaffRoute: ProtectedRoute that blocks ROLE_SUPPLIER.
 */
export function StaffRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth)
  const isSupplier = useAppSelector(selectIsSupplier)
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (isSupplier) return <Navigate to="/supplier/dashboard" replace />
  return <Outlet />
}
