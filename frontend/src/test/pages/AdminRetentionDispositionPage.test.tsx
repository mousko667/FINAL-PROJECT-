import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AdminRetentionDispositionPage from '@/pages/admin/AdminRetentionDispositionPage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn(), put: vi.fn() },
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
            <AdminRetentionDispositionPage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

const sampleDocs = [
  { id: 'd1', invoiceId: 'inv1', originalFilename: 'facture-2014.pdf', uploadedAt: '2014-03-01T10:00:00Z', retentionDisposition: 'PENDING' },
]

describe('AdminRetentionDispositionPage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders rows when there are expired pending documents', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleDocs } })
    renderPage()
    expect(await screen.findByText('facture-2014.pdf')).toBeInTheDocument()
  })

  it('renders the reassuring empty state when there is nothing to process', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [] } })
    renderPage()
    expect(await screen.findByText('Aucun document à traiter.')).toBeInTheDocument()
    expect(screen.queryByText('facture-2014.pdf')).toBeNull()
  })

  it('denies access to non-admin users', () => {
    renderPage({ id: '2', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'] })
    expect(screen.queryByText('facture-2014.pdf')).toBeNull()
  })
})
