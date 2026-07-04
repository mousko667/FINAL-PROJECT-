import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { InvoiceActionPanel } from '@/components/invoice/InvoiceActionPanel'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'
import type { Invoice } from '@/types/invoice'

// Mock apiClient so no real HTTP calls happen
vi.mock('@/services/apiClient', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}))

const makeStore = (user: AuthUser) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: {
        user,
        accessToken: 'test-token',
        refreshToken: null,
        isAuthenticated: true,
      },
    },
  })

const makeInvoice = (status: Invoice['status']): Invoice => ({
  id: 'inv-1',
  referenceNumber: 'REF-001',
  supplierName: 'ACME SA',
  amount: 5000,
  currency: 'EUR',
  issueDate: '2024-01-01',
  dueDate: '2024-02-01',
  status,
})

const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

function renderPanel(invoice: Invoice, user: AuthUser) {
  return render(
    <Provider store={makeStore(user)}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <InvoiceActionPanel invoice={invoice} />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

const assistantUser: AuthUser = {
  id: '1', username: 'ac', email: 'ac@oct.fr', roles: ['ROLE_ASSISTANT_COMPTABLE'],
}
const validateur1: AuthUser = {
  id: '2', username: 'v1', email: 'v1@oct.fr', roles: ['ROLE_VALIDATEUR_N1_DRH'],
}
const dafUser: AuthUser = {
  id: '4', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'],
}

describe('InvoiceActionPanel — Start review (SOUMIS -> EN_VALIDATION_N1)', () => {
  it('shows Start review on a SOUMIS invoice for an AA and posts /workflow/assign', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { data: null } } as any)
    renderPanel(makeInvoice('SOUMIS'), assistantUser)

    const btn = screen.getByRole('button', { name: /Démarrer la revue|Start review/ })
    await userEvent.click(btn)

    expect(post).toHaveBeenCalledWith('/invoices/inv-1/workflow/assign')
  })

  it('shows Start review on a SOUMIS invoice for an N1 validator', () => {
    renderPanel(makeInvoice('SOUMIS'), validateur1)
    expect(screen.getByRole('button', { name: /Démarrer la revue|Start review/ })).toBeDefined()
  })

  it('does NOT show Start review for DAF on a SOUMIS invoice', () => {
    renderPanel(makeInvoice('SOUMIS'), dafUser)
    expect(screen.queryByRole('button', { name: /Démarrer la revue|Start review/ })).toBeNull()
  })
})
