import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import ReportsPage from '@/pages/ReportsPage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))
vi.mock('@/services/reportService', () => ({
  reportService: {
    getKpis: vi.fn().mockResolvedValue({}),
    getSupplierPerformance: vi.fn().mockResolvedValue({}),
    exportExcel: vi.fn(),
    exportCompliancePdf: vi.fn(),
  },
}))
vi.mock('@/components/reports/VolumeTrendSection', () => ({ default: () => null }))

const daf: AuthUser = { id: '1', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'] }

const makeStore = () =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user: daf, accessToken: 'token', refreshToken: null, isAuthenticated: true },
    },
  })

function renderReports() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={makeStore()}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <ReportsPage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('AUDIT-022 — /reports must not fire payment-cycle without both dates', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: {} } } as never)
  })
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('never calls /reports/payment-cycle on load, when both date bounds are empty', async () => {
    renderReports()

    // Let the other sections' queries settle first, otherwise the assertion could
    // pass simply because nothing had been requested yet.
    await waitFor(() => {
      expect(vi.mocked(apiClient.get).mock.calls.length).toBeGreaterThan(0)
    })

    const cycleCalls = vi
      .mocked(apiClient.get)
      .mock.calls.filter(([url]) => String(url).includes('/reports/payment-cycle'))
    expect(cycleCalls).toHaveLength(0)
  })
})
