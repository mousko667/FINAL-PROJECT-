import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import MyDelegationsPage from '@/pages/MyDelegationsPage'
import InvoiceDetailPage from '@/pages/InvoiceDetailPage'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: { data: [] } }), post: vi.fn(), delete: vi.fn() },
}))
vi.mock('@/services/invoiceService', () => ({
  invoiceService: { getById: vi.fn().mockResolvedValue({}), getHistory: vi.fn().mockResolvedValue([]) },
}))

const user = (username: string, roles: string[]): AuthUser => ({
  id: '1', username, email: `${username}@oct.fr`, roles,
})

const makeStore = (u: AuthUser) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user: u, accessToken: 'token', refreshToken: null, isAuthenticated: true },
    },
  })

function renderPage(ui: React.ReactElement, u: AuthUser, path = '/') {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={makeStore(u)}>
      <QueryClientProvider client={qc}>
        <MemoryRouter initialEntries={[path]}>
          <I18nextProvider i18n={i18n}>
            <Routes><Route path={path} element={ui} /></Routes>
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

/** The explicit refusal screen rendered by PageRoleGuard. */
const refusalShown = () => !!screen.queryByText(i18n.t('roleGuard.unauthorized'))

describe('AUDIT-004 — MyDelegationsPage must carry the sidebar guard', () => {
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('refuses an ADMIN typing the URL', () => {
    renderPage(<MyDelegationsPage />, user('admin', ['ROLE_ADMIN']), '/my-delegations')
    expect(refusalShown()).toBe(true)
  })

  it('refuses an ASSISTANT_COMPTABLE — approvers only', () => {
    renderPage(<MyDelegationsPage />, user('aa', ['ROLE_ASSISTANT_COMPTABLE']), '/my-delegations')
    expect(refusalShown()).toBe(true)
  })

  it('still lets the DAF and the validators through', () => {
    renderPage(<MyDelegationsPage />, user('daf', ['ROLE_DAF']), '/my-delegations')
    expect(refusalShown()).toBe(false)

    cleanup()
    renderPage(<MyDelegationsPage />, user('val', ['ROLE_VALIDATEUR_N1_DRH']), '/my-delegations')
    expect(refusalShown()).toBe(false)
  })
})

describe('AUDIT-003 — /invoices/:id must refuse explicitly, not look broken', () => {
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('shows the explicit refusal to an ADMIN instead of a generic error screen', () => {
    renderPage(<InvoiceDetailPage />, user('admin', ['ROLE_ADMIN']), '/invoices/i1')
    expect(refusalShown()).toBe(true)
    // The screen the finding is about: "Une erreur est survenue" looked like an outage.
    expect(screen.queryByText(/Une erreur est survenue/i)).toBeNull()
  })

  it('lets the roles that may read invoices through', () => {
    renderPage(<InvoiceDetailPage />, user('daf', ['ROLE_DAF']), '/invoices/i1')
    expect(refusalShown()).toBe(false)

    cleanup()
    renderPage(<InvoiceDetailPage />, user('aa', ['ROLE_ASSISTANT_COMPTABLE']), '/invoices/i1')
    expect(refusalShown()).toBe(false)
  })
})
