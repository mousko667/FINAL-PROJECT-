import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AdminArchiveCompliancePage from '@/pages/admin/AdminArchiveCompliancePage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

const adminUser: AuthUser = { id: '1', username: 'admin', email: 'admin@oct.fr', roles: ['ROLE_ADMIN'] }

const makeStore = (user: AuthUser | null) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user, accessToken: 'test-token', refreshToken: null, isAuthenticated: !!user },
    },
  })

function renderPage(user: AuthUser | null = adminUser) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={makeStore(user)}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <AdminArchiveCompliancePage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

const sampleReport = {
  generatedAt: '2026-06-21T10:00:00Z',
  coverage: { archivedInvoices: 8, archivedWithDocument: 6, archivedWithoutDocument: 2, coverageRate: 0.75 },
  integrity: { totalDocuments: 10, withChecksum: 10, missingChecksum: 0, integrityRate: 1.0 },
  retention: { status: 'CONFORME', retentionYears: 10, active: true, sweepOverdue: false },
  lifecycle: { pending: 3, retained: 2, purged: 1, versionedDocuments: 4 },
}

describe('AdminArchiveCompliancePage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads and renders the report sections', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleReport } })
    renderPage()
    expect(await screen.findByText('75%')).toBeInTheDocument()
    expect(screen.getByText('CONFORME')).toBeInTheDocument()
  })

  it('denies access to non-admin users', () => {
    renderPage({ id: '2', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'] })
    expect(screen.queryByText('75%')).toBeNull()
  })
})
