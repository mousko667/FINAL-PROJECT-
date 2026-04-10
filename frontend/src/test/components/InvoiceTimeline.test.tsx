import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import { InvoiceTimeline } from '@/components/invoice/InvoiceTimeline'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { Invoice } from '@/types/invoice'

const store = configureStore({
  reducer: { auth: authReducer, notifications: notificationReducer },
})

function renderTimeline(invoice: Invoice) {
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <I18nextProvider i18n={i18n}>
          <InvoiceTimeline invoice={invoice} />
        </I18nextProvider>
      </MemoryRouter>
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
  it('shows no data message when no history', () => {
    renderTimeline(baseInvoice)
    // "Aucune donnée disponible" in French
    expect(screen.getByText(/aucune donnée/i)).toBeDefined()
  })

  it('renders history entries when present', () => {
    const invoiceWithHistory = {
      ...baseInvoice,
      statusHistory: [
        {
          id: 'h1',
          fromStatus: 'BROUILLON',
          toStatus: 'SOUMIS',
          changedBy: { username: 'alice' },
          changedAt: '2024-01-15T10:00:00Z',
          changeReason: undefined,
        },
        {
          id: 'h2',
          fromStatus: 'SOUMIS',
          toStatus: 'EN_VALIDATION_N1',
          changedBy: { username: 'bob' },
          changedAt: '2024-01-16T09:00:00Z',
          changeReason: undefined,
        },
      ],
    }
    renderTimeline(invoiceWithHistory as Invoice)
    // Username is rendered inline with '·' and date — use regex
    expect(screen.getByText(/alice/)).toBeDefined()
    expect(screen.getByText(/bob/)).toBeDefined()
  })

  it('shows rejection reason when present', () => {
    const invoiceWithReject = {
      ...baseInvoice,
      statusHistory: [
        {
          id: 'h1',
          fromStatus: 'EN_VALIDATION_N1',
          toStatus: 'REJETE',
          changedBy: { username: 'validateur' },
          changedAt: '2024-01-20T14:00:00Z',
          changeReason: 'Justificatif manquant',
        },
      ],
    }
    renderTimeline(invoiceWithReject as Invoice)
    expect(screen.getByText(/justificatif manquant/i)).toBeDefined()
  })
})
