import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import PaymentsPage from '@/pages/PaymentsPage'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn(), post: vi.fn() },
}))

vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

const samplePayments = {
  content: [
    {
      id: 'pay-scheduled', invoiceId: 'inv-1', invoiceReference: 'INV-001',
      amountPaid: 1000, currency: 'EUR', paymentDate: '2026-06-01T00:00:00Z',
      paymentMethod: 'VIREMENT', reference: 'PAY-1', createdAt: '2026-06-01T00:00:00Z',
      status: 'SCHEDULED',
    },
    {
      id: 'pay-processed', invoiceId: 'inv-2', invoiceReference: 'INV-002',
      amountPaid: 2000, currency: 'EUR', paymentDate: '2026-06-02T00:00:00Z',
      paymentMethod: 'CHEQUE', reference: 'PAY-2', createdAt: '2026-06-02T00:00:00Z',
      status: 'PROCESSED', processedDate: '2026-06-03T00:00:00Z',
    },
  ],
  totalPages: 1,
  totalElements: 2,
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <MemoryRouter><PaymentsPage /></MemoryRouter>
      </I18nextProvider>
    </QueryClientProvider>
  )
}

describe('PaymentsPage — statut paiement (planifié / exécuté)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.get).mockImplementation((url: string) => {
      if (url === '/payments') {
        return Promise.resolve({ data: { data: samplePayments } })
      }
      if (url === '/invoices') {
        return Promise.resolve({ data: { data: { content: [] } } })
      }
      return Promise.resolve({ data: { data: {} } })
    })
  })

  it('affiche le bouton Marquer exécuté seulement pour les paiements SCHEDULED', async () => {
    renderPage()
    expect(await screen.findByText('Marquer exécuté')).toBeInTheDocument()
    // "Exécuté" libellé du badge de statut (SPAN) ; ignorer l'OPTION homonyme du filtre.
    const executedBadge = screen
      .getAllByText('Exécuté')
      .find((el) => el.tagName === 'SPAN')
    expect(executedBadge).toBeInTheDocument()
    // Only one scheduled payment => only one "Marquer exécuté" button.
    expect(screen.getAllByText('Marquer exécuté')).toHaveLength(1)
  })

  it("n'affiche le lien d'avis (remittance) que pour les paiements PROCESSED", async () => {
    renderPage()
    await screen.findByText('Marquer exécuté')
    expect(screen.getAllByText('Avis')).toHaveLength(1)
  })
})
