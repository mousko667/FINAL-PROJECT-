
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import authReducer from '@/store/slices/authSlice'
import AdminCompliancePage from '../AdminCompliancePage'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'fr' } }),
}))

// Global mocks to prevent rendering crashes
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ data: { data: { content: [], totalElements: 0 } } }),
    post: vi.fn().mockResolvedValue({ data: {} }),
    put: vi.fn().mockResolvedValue({ data: {} }),
    delete: vi.fn().mockResolvedValue({ data: {} }),
    patch: vi.fn().mockResolvedValue({ data: {} }),
  }
}))

const makeStore = (roles: string[]) =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: '1', username: 'test', email: 'test@oct.fr', roles, departmentId: '1' },
        accessToken: 'token',
        refreshToken: null,
        isAuthenticated: true,
      },
    },
  })

const renderPage = (roles: string[]) => {
  const store = makeStore(roles)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={store}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <AdminCompliancePage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('AdminCompliancePage RoleGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('affiche roleGuard.unauthorized si le rôle est invalide', async () => {
    renderPage(['ROLE_INVALID'])
    expect(await screen.findByText('roleGuard.unauthorized')).toBeInTheDocument()
  })

  it('affiche le contenu avec le bon rôle', async () => {
    renderPage(['ROLE_ADMIN'])
    expect(screen.queryByText('roleGuard.unauthorized')).toBeNull()
  })
})
