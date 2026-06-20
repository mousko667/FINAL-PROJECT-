import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import i18n from '@/i18n'
import { InvoiceTimeline } from '@/components/invoice/InvoiceTimeline'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import apiClient from '@/services/apiClient'
import type { Invoice } from '@/types/invoice'

// InvoiceTimeline fetches history via apiClient.get(`/invoices/{id}/history`)
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}))

const store = configureStore({
  reducer: { auth: authReducer, notifications: notificationReducer },
})

function renderTimeline(invoice: Invoice) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={store}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <InvoiceTimeline invoice={invoice} />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

const baseInvoice: Invoice = {
  id: 'inv-1',
  referenceNumber: 'REF-001',
  supplierName: 'ACME SA',
  amount: 5000,
  currency: 'EUR',
  issueDate: '2024-01-01',
  dueDate: '2024-02-01',
  status: 'EN_VALIDATION_N1',
}

describe('InvoiceTimeline', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows no data message when no history', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: [] } })
    renderTimeline(baseInvoice)
    // "Aucune donnée disponible" in French
    expect(await screen.findByText(/aucune donnée/i)).toBeDefined()
  })

  it('renders history entries when present', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: [
          {
            id: 'h1',
            fromStatus: 'BROUILLON',
            toStatus: 'SOUMIS',
            changedByUsername: 'alice',
            changedAt: '2024-01-15T10:00:00Z',
          },
          {
            id: 'h2',
            fromStatus: 'SOUMIS',
            toStatus: 'EN_VALIDATION_N1',
            changedByUsername: 'bob',
            changedAt: '2024-01-16T09:00:00Z',
          },
        ],
      },
    })
    renderTimeline(baseInvoice)
    // Username is rendered inline with '-' and date — use regex
    expect(await screen.findByText(/alice/)).toBeDefined()
    expect(await screen.findByText(/bob/)).toBeDefined()
  })

  it('shows rejection reason when present', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: [
          {
            id: 'h1',
            fromStatus: 'EN_VALIDATION_N1',
            toStatus: 'REJETE',
            changedByUsername: 'validateur',
            changedAt: '2024-01-20T14:00:00Z',
            changeReason: 'Justificatif manquant',
          },
        ],
      },
    })
    renderTimeline(baseInvoice)
    expect(await screen.findByText(/justificatif manquant/i)).toBeDefined()
  })
})
