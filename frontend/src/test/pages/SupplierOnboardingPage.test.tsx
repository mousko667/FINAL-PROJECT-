import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import SupplierOnboardingPage from '@/pages/admin/SupplierOnboardingPage'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { post: vi.fn() },
}))

// The page is now wrapped in a PageRoleGuard (audit findings N15/N7); render children directly here.
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <I18nextProvider i18n={i18n}>
          <SupplierOnboardingPage />
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('SupplierOnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(apiClient.post).mockResolvedValue({
      data: { data: { id: 'sup-1' } },
    } as never)
  })

  it('advances through the onboarding steps and submits the supplier payload', async () => {
    renderPage()

    expect(await screen.findByTestId('supplier-onboarding-step-1')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Raison sociale|Company Name/), { target: { value: 'Acme' } })
    fireEvent.change(screen.getByLabelText(/NIF \/ Identifiant fiscal/), { target: { value: 'TAX-001' } })
    fireEvent.click(screen.getByRole('button', { name: /Suivant|Next/ }))

    expect(await screen.findByTestId('supplier-onboarding-step-2')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Email de contact|Contact Email/), { target: { value: 'acme@example.com' } })
    fireEvent.change(screen.getByLabelText(/Téléphone|Phone/), { target: { value: '+24100000000' } })
    fireEvent.change(screen.getByLabelText(/Adresse|Address/), { target: { value: '1 Supplier St' } })
    fireEvent.click(screen.getByRole('button', { name: /Suivant|Next/ }))

    expect(await screen.findByTestId('supplier-onboarding-step-3')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText(/Coordonnées bancaires|Bank Details/), { target: { value: 'BANK-123' } })
    fireEvent.click(screen.getByRole('button', { name: /Créer le fournisseur|Create supplier/ }))

    await waitFor(() =>
      expect(apiClient.post).toHaveBeenCalledWith('/suppliers', {
        companyName: 'Acme',
        taxId: 'TAX-001',
        contactEmail: 'acme@example.com',
        contactPhone: '+24100000000',
        address: '1 Supplier St',
        bankDetails: 'BANK-123',
        category: null,
      })
    )
  })

  it('allows returning to the previous onboarding step', async () => {
    renderPage()

    await screen.findByTestId('supplier-onboarding-step-1')
    fireEvent.change(screen.getByLabelText(/Raison sociale|Company Name/), { target: { value: 'Acme' } })
    fireEvent.change(screen.getByLabelText(/NIF \/ Identifiant fiscal/), { target: { value: 'TAX-001' } })
    fireEvent.click(screen.getByRole('button', { name: /Suivant|Next/ }))

    expect(await screen.findByTestId('supplier-onboarding-step-2')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Précédent|Previous/ }))
    expect(await screen.findByTestId('supplier-onboarding-step-1')).toBeInTheDocument()
  })
})
