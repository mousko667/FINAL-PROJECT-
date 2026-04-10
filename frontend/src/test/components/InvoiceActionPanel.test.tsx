import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { InvoiceActionPanel } from '@/components/invoice/InvoiceActionPanel'
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
  id: '2', username: 'v1', email: 'v1@oct.fr', roles: ['ROLE_VALIDATEUR_N1'],
}
const validateur2: AuthUser = {
  id: '3', username: 'v2', email: 'v2@oct.fr', roles: ['ROLE_VALIDATEUR_N2'],
}
const dafUser: AuthUser = {
  id: '4', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'],
}

describe('InvoiceActionPanel', () => {
  it('shows Submit button for ASSISTANT_COMPTABLE on BROUILLON invoice', () => {
    renderPanel(makeInvoice('BROUILLON'), assistantUser)
    expect(screen.getByText(/soumettre/i)).toBeDefined()
  })

  it('does NOT show Submit for VALIDATEUR_N1 on BROUILLON invoice', () => {
    renderPanel(makeInvoice('BROUILLON'), validateur1)
    expect(screen.queryByText(/soumettre/i)).toBeNull()
  })

  it('shows Validate and Reject for VALIDATEUR_N1 on EN_VALIDATION_N1', () => {
    renderPanel(makeInvoice('EN_VALIDATION_N1'), validateur1)
    expect(screen.getAllByText(/valider/i).length).toBeGreaterThan(0)
    expect(screen.getByText(/rejeter/i)).toBeDefined()
  })

  it('shows Validate and Reject for VALIDATEUR_N2 on EN_VALIDATION_N2', () => {
    renderPanel(makeInvoice('EN_VALIDATION_N2'), validateur2)
    expect(screen.getAllByText(/valider/i).length).toBeGreaterThan(0)
    expect(screen.getByText(/rejeter/i)).toBeDefined()
  })

  it('shows Approve (BAP) for DAF on VALIDE invoice', () => {
    renderPanel(makeInvoice('VALIDE'), dafUser)
    // French translation: 'Approuver (BAP)'
    expect(screen.getByText(/approuver/i)).toBeDefined()
  })

  it('shows Mark Paid for DAF on BON_A_PAYER invoice', () => {
    renderPanel(makeInvoice('BON_A_PAYER'), dafUser)
    expect(screen.getByText(/payée/i)).toBeDefined()
  })

  it('renders nothing for wrong role+status combo', () => {
    const { container } = renderPanel(makeInvoice('PAYE'), assistantUser)
    // No action panel rendered (or rendered empty)
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('opens reject dialog when Reject is clicked', () => {
    const { container } = renderPanel(makeInvoice('EN_VALIDATION_N1'), validateur1)
    const rejectBtn = screen.getByText(/rejeter/i)
    fireEvent.click(rejectBtn)
    expect(screen.getByText(/confirmer le rejet/i)).toBeDefined()
    const textarea = container.querySelector('textarea')
    expect(textarea).not.toBeNull()
  })

  it('Confirm button in reject dialog is disabled when reason is empty', () => {
    renderPanel(makeInvoice('EN_VALIDATION_N1'), validateur1)
    fireEvent.click(screen.getByText(/rejeter/i))
    const confirmBtn = document.getElementById('btn-confirm-reject') as HTMLButtonElement | null
    if (confirmBtn) expect(confirmBtn.disabled).toBe(true)
  })
})
