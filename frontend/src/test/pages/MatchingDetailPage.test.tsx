import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import MatchingDetailPage from '@/pages/matching/MatchingDetailPage'

vi.mock('@/services/matchingService', () => ({
  getMatchingLines: vi.fn().mockResolvedValue({
    summary: { invoiceId: 'i1', invoiceNumber: 'INV-1', supplierName: 'ACME',
      purchaseOrderId: 'p1', purchaseOrderNumber: 'PO-1', grnPresent: true,
      status: 'MISMATCH', lineCount: 1, discrepancyLineCount: 1, matchedAt: null },
    discrepancyNotes: null, overriddenBy: null, overrideReason: null,
    lines: [{ description: 'Widget', poQuantity: 10, poUnitPrice: 5, receivedQuantity: 10,
      invoiceQuantity: 20, invoiceUnitPrice: 5, qtyVariancePct: 100, priceVariancePct: 0, verdict: 'MISMATCH' }],
  }),
}))
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/matching/i1']}>
        <Routes><Route path="/matching/:invoiceId" element={<MatchingDetailPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('MatchingDetailPage', () => {
  it('affiche la comparaison ligne-à-ligne', async () => {
    renderPage()
    expect(await screen.findByText('Widget')).toBeInTheDocument()
    expect(screen.getByText('100%')).toBeInTheDocument()
  })
})
