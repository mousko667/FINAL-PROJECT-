import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import MatchingListPage from '@/pages/matching/MatchingListPage'

vi.mock('@/services/matchingService', () => ({
  listMatching: vi.fn().mockResolvedValue({
    content: [{
      invoiceId: 'i1', invoiceNumber: 'INV-1', supplierName: 'ACME',
      purchaseOrderId: 'p1', purchaseOrderNumber: 'PO-1', grnPresent: true,
      status: 'MATCHED', lineCount: 2, discrepancyLineCount: 0, matchedAt: '2026-06-21T10:00:00Z',
    }],
    totalPages: 1, page: 0,
  }),
}))
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter><MatchingListPage /></MemoryRouter>
    </QueryClientProvider>
  )
}

describe('MatchingListPage', () => {
  it('affiche une ligne de rapprochement', async () => {
    renderPage()
    expect(await screen.findByText('INV-1')).toBeInTheDocument()
    expect(screen.getByText('ACME')).toBeInTheDocument()
  })
})
