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

  // AUDIT-026: this used to enumerate the FORBIDDEN prefixes (/admin, /dashboard, /invoices,
  // /reports), so every staff route outside that list stayed open to suppliers — /profile,
  // /notifications, /my-delegations and /access-requests were all reachable, the last two showing
  // an external third party the company's internal role structure ("Administrateur", "DAF") and an
  // approval-delegation form. A blocklist can only ever be as complete as the day it was written;
  // the rule is now stated positively — anything outside /supplier is staff.
  const isSupplierPath = location.pathname.startsWith('/supplier')

  if (isSupplier && !isSupplierPath) {
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

// AUDIT-016: StaffRoute was removed here. It had been written to block ROLE_SUPPLIER but was never
// imported anywhere (dead code), while the real guard next door used a blocklist that let suppliers
// through (AUDIT-026). Now that ProtectedRoute states the rule positively, a second guard doing the
// same job would just be another place for the two to drift apart.
