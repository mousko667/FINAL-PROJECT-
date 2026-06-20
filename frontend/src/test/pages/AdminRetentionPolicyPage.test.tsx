import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AdminRetentionPolicyPage from '@/pages/admin/AdminRetentionPolicyPage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

const adminUser: AuthUser = {
  id: '1', username: 'admin', email: 'admin@oct.fr', roles: ['ROLE_ADMIN'],
}

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
            <AdminRetentionPolicyPage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('AdminRetentionPolicyPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads and renders the current policy value', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { retentionYears: 7, active: true, lastSweepAt: null, lastFlaggedCount: null } },
    })
    renderPage()
    const input = (await screen.findByRole('spinbutton')) as HTMLInputElement
    expect(input.value).toBe('7')
  })

  it('submits the PUT with edited values', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { retentionYears: 10, active: true, lastSweepAt: null, lastFlaggedCount: null } },
    })
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: {} } })
    renderPage()

    const input = (await screen.findByRole('spinbutton')) as HTMLInputElement
    fireEvent.change(input, { target: { value: '5' } })
    fireEvent.click(screen.getByText(/enregistrer|save/i))

    await waitFor(() =>
      expect(apiClient.put).toHaveBeenCalledWith('/retention-policy', { retentionYears: 5, active: true })
    )
  })

  it('denies access to non-admin users', () => {
    renderPage({ id: '2', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'] })
    expect(screen.queryByRole('spinbutton')).toBeNull()
  })
})
