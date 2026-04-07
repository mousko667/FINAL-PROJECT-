import { Navigate, Outlet } from 'react-router-dom'
import { useAppSelector } from '@/store/hooks'

/**
 * ProtectedRoute: Redirects to /login when no valid JWT is present.
 */
export function ProtectedRoute() {
  const { isAuthenticated } = useAppSelector((state) => state.auth)
  return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />
}
