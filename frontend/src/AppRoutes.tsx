import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute } from '@/components/auth/ProtectedRoute'
import AppShell from '@/components/layout/AppShell'
import { useWebSocket } from '@/hooks/useWebSocket'

// Lazy-loaded pages
const LoginPage = lazy(() => import('@/pages/LoginPage'))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const InvoiceListPage = lazy(() => import('@/pages/InvoiceListPage'))
const InvoiceCreatePage = lazy(() => import('@/pages/InvoiceCreatePage'))
const InvoiceDetailPage = lazy(() => import('@/pages/InvoiceDetailPage'))
const ReportsPage = lazy(() => import('@/pages/ReportsPage'))
const AdminUsersPage = lazy(() => import('@/pages/admin/AdminUsersPage'))
const AdminDepartmentsPage = lazy(() => import('@/pages/admin/AdminDepartmentsPage'))
const AdminAuditPage = lazy(() => import('@/pages/admin/AdminAuditPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))

function AppRoutes() {
  // Initialize WebSocket connection for authenticated users
  useWebSocket()

  return (
    <Suspense fallback={<div className="flex items-center justify-center h-screen text-gray-500">Chargement...</div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />

        {/* Protected routes wrapped in AppShell */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/invoices" element={<InvoiceListPage />} />
            <Route path="/invoices/new" element={<InvoiceCreatePage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/departments" element={<AdminDepartmentsPage />} />
            <Route path="/admin/audit" element={<AdminAuditPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  )
}

export default AppRoutes
