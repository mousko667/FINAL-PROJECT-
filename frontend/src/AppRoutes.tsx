import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { ProtectedRoute, SupplierRoute } from '@/components/auth/ProtectedRoute'
import AppShell from '@/components/layout/AppShell'
import SupplierLayout from '@/layouts/SupplierLayout'
import { ErrorBoundary } from '@/components/ErrorBoundary'
import { useWebSocket } from '@/hooks/useWebSocket'

// Lazy-loaded pages
const LoginPage = lazy(() => import('@/pages/LoginPage'))
const RegisterPage = lazy(() => import('@/pages/auth/RegisterPage'))
const ForgotPasswordPage = lazy(() => import('@/pages/auth/ForgotPasswordPage'))
const ResetPasswordPage = lazy(() => import('@/pages/auth/ResetPasswordPage'))
const SupplierRegisterPage = lazy(() => import('@/pages/auth/SupplierRegisterPage'))
const EmailVerificationPage = lazy(() => import('@/pages/auth/EmailVerificationPage'))
const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const ProfilePage = lazy(() => import('@/pages/ProfilePage'))
const InvoiceListPage = lazy(() => import('@/pages/InvoiceListPage'))
const InvoiceCreatePage = lazy(() => import('@/pages/InvoiceCreatePage'))
const InvoiceDetailPage = lazy(() => import('@/pages/InvoiceDetailPage'))
const ReportsPage = lazy(() => import('@/pages/ReportsPage'))
const AdminUsersPage = lazy(() => import('@/pages/admin/AdminUsersPage'))
const AdminUserFormPage = lazy(() => import('@/pages/admin/AdminUserFormPage'))
const AdminPermissionMatrixPage = lazy(() => import('@/pages/admin/AdminPermissionMatrixPage'))
const AdminDepartmentsPage = lazy(() => import('@/pages/admin/AdminDepartmentsPage'))
const AdminDepartmentFormPage = lazy(() => import('@/pages/admin/AdminDepartmentFormPage'))
const AdminAuditPage = lazy(() => import('@/pages/admin/AdminAuditPage'))
const ApprovalMatrixPage = lazy(() => import('@/pages/admin/ApprovalMatrixPage'))
const AdminDelegationsPage = lazy(() => import('@/pages/admin/AdminDelegationsPage'))
const AdminMatchingConfigPage = lazy(() => import('@/pages/admin/AdminMatchingConfigPage'))
const AdminChecklistTemplatesPage = lazy(() => import('@/pages/admin/AdminChecklistTemplatesPage'))
const SecuritySettingsPage = lazy(() => import('@/pages/admin/SecuritySettingsPage'))
const IntegrationsPage = lazy(() => import('@/pages/admin/IntegrationsPage'))
const MyAccessRequestsPage = lazy(() => import('@/pages/MyAccessRequestsPage'))
const MyDelegationsPage = lazy(() => import('@/pages/MyDelegationsPage'))
const ReportBuilderPage = lazy(() => import('@/pages/ReportBuilderPage'))
const AdminAccessRequestsPage = lazy(() => import('@/pages/admin/AdminAccessRequestsPage'))
const AdminAnnouncementsPage = lazy(() => import('@/pages/admin/AdminAnnouncementsPage'))
const AdminCompliancePage = lazy(() => import('@/pages/admin/AdminCompliancePage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))
const SuppliersPage = lazy(() => import('@/pages/admin/SuppliersPage'))
const SupplierDetailPage = lazy(() => import('@/pages/admin/SupplierDetailPage'))
const SupplierFormPage = lazy(() => import('@/pages/admin/SupplierFormPage'))
const SupplierDashboardPage = lazy(() => import('@/pages/supplier/SupplierDashboardPage'))
const SupplierInvoicesPage = lazy(() => import('@/pages/supplier/SupplierInvoicesPage'))
const SupplierInvoiceSubmitPage = lazy(() => import('@/pages/supplier/SupplierInvoiceSubmitPage'))
const SupplierProfilePage = lazy(() => import('@/pages/supplier/SupplierProfilePage'))
const SupplierDocumentsPage = lazy(() => import('@/pages/supplier/SupplierDocumentsPage'))
const ApprovalQueuePage = lazy(() => import('@/pages/ApprovalQueuePage'))
const FinancialAuditPage = lazy(() => import('@/pages/FinancialAuditPage'))
const PurchaseOrdersPage = lazy(() => import('@/pages/PurchaseOrdersPage'))
const PaymentsPage = lazy(() => import('@/pages/PaymentsPage'))
const NotificationsPage = lazy(() => import('@/pages/NotificationsPage'))
const GoodsReceiptsPage = lazy(() => import('@/pages/GoodsReceiptsPage'))
const ArchivePage = lazy(() => import('@/pages/ArchivePage'))

function AppRoutes() {
  // Initialize WebSocket connection for authenticated users
  useWebSocket()

  return (
    <Suspense fallback={<div className="flex items-center justify-center h-screen text-gray-500">Chargement...</div>}>
    <ErrorBoundary>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/register/supplier" element={<SupplierRegisterPage />} />
        <Route path="/verify-email" element={<EmailVerificationPage />} />

        {/* Protected routes wrapped in AppShell */}
        <Route element={<ProtectedRoute />}>
          <Route element={<AppShell />}>
            <Route index element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/profile" element={<ProfilePage />} />
            <Route path="/access-requests" element={<MyAccessRequestsPage />} />
            <Route path="/my-delegations" element={<MyDelegationsPage />} />
            <Route path="/invoices" element={<InvoiceListPage />} />
            <Route path="/invoices/new" element={<InvoiceCreatePage />} />
            <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
            <Route path="/approvals" element={<ApprovalQueuePage />} />
            <Route path="/financial-audit" element={<FinancialAuditPage />} />
            <Route path="/purchase-orders" element={<PurchaseOrdersPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/reports/builder" element={<ReportBuilderPage />} />
            <Route path="/payments" element={<PaymentsPage />} />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/goods-receipts" element={<GoodsReceiptsPage />} />
            <Route path="/archive" element={<ArchivePage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/users/new" element={<AdminUserFormPage />} />
            <Route path="/admin/permissions" element={<AdminPermissionMatrixPage />} />
            <Route path="/admin/access-requests" element={<AdminAccessRequestsPage />} />
            <Route path="/admin/announcements" element={<AdminAnnouncementsPage />} />
            <Route path="/admin/compliance" element={<AdminCompliancePage />} />
            <Route path="/admin/departments" element={<AdminDepartmentsPage />} />
            <Route path="/admin/departments/new" element={<AdminDepartmentFormPage />} />
            <Route path="/admin/audit" element={<AdminAuditPage />} />
            <Route path="/admin/approval-matrix" element={<ApprovalMatrixPage />} />
            <Route path="/admin/delegations" element={<AdminDelegationsPage />} />
            <Route path="/admin/matching-config" element={<AdminMatchingConfigPage />} />
            <Route path="/admin/checklist-templates" element={<AdminChecklistTemplatesPage />} />
            <Route path="/admin/security" element={<SecuritySettingsPage />} />
            <Route path="/admin/integrations" element={<IntegrationsPage />} />
            <Route path="/admin/suppliers" element={<SuppliersPage />} />
            <Route path="/admin/suppliers/new" element={<SupplierFormPage />} />
            <Route path="/admin/suppliers/:id" element={<SupplierDetailPage />} />
            <Route path="/admin/suppliers/:id/edit" element={<SupplierFormPage />} />
          </Route>
        </Route>

        <Route element={<SupplierRoute />}>
          <Route path="/supplier" element={<SupplierLayout />}>
            <Route index element={<Navigate to="/supplier/dashboard" replace />} />
            <Route path="dashboard" element={<SupplierDashboardPage />} />
            <Route path="invoices" element={<SupplierInvoicesPage />} />
            <Route path="invoices/new" element={<SupplierInvoiceSubmitPage />} />
            <Route path="profile" element={<SupplierProfilePage />} />
            <Route path="documents" element={<SupplierDocumentsPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </ErrorBoundary>
    </Suspense>
  )
}

export default AppRoutes
