import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import ReportsPage from '@/pages/ReportsPage'

// Render i18n keys verbatim (with interpolation) so assertions stay stable.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: any) =>
      typeof opts === 'object' && opts && !('defaultValue' in opts)
        ? `${key}:${JSON.stringify(opts)}`
        : key,
  }),
}))

// The role guard is exercised elsewhere; render children directly here.
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

// The page gates its data fetches on the current user's role via useHasRole
// (backed by the Redux store); in tests we grant the role directly.
vi.mock('@/hooks/useHasRole', () => ({
  useHasRole: () => true,
}))

// VolumeTrendSection makes its own queries we don't care about.
vi.mock('@/components/reports/VolumeTrendSection', () => ({ default: () => <div /> }))

const getSupplierPerformance = vi.fn()
vi.mock('@/services/reportService', () => ({
  reportService: {
    getKpis: vi.fn().mockResolvedValue({
      totalInvoices: 0, countByStatus: {}, averageProcessingTimeDays: 0,
      rejectionRate: 0, overdueCount: 0, volumeBySupplier: {},
    }),
    getSupplierPerformance: (...a: any[]) => getSupplierPerformance(...a),
  },
}))

// Suppliers list + all other report endpoints flow through apiClient.
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/suppliers') {
        return Promise.resolve({ data: { data: { content: [{ id: 'sup-1', companyName: 'ACME Corp' }] } } })
      }
      return Promise.resolve({ data: { data: {} } })
    }),
  },
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter><ReportsPage /></MemoryRouter>
    </QueryClientProvider>
  )
}

describe('ReportsPage supplier performance section', () => {
  beforeEach(() => getSupplierPerformance.mockReset())

  it('loads and displays metrics after a supplier is selected', async () => {
    getSupplierPerformance.mockResolvedValue({
      supplierId: 'sup-1', supplierName: 'ACME Corp',
      invoiceAccuracyRate: 0.95, rejectionRate: 0.1, averagePaymentDays: 12.3,
      totalInvoicesSubmitted: 20, matchedInvoices: 18, mismatchedInvoices: 2,
    })
    const user = userEvent.setup()
    renderPage()

    // The section is collapsed by default — expand it first.
    await user.click(await screen.findByRole('button', { name: /supplierPerformance.title/ }))
    const supplierSelect = await screen.findByLabelText('reports.supplierPerformance.selectLabel')
    await user.selectOptions(supplierSelect, 'sup-1')

    expect(await screen.findByText('95.0%')).toBeInTheDocument()
    expect(screen.getByText('10.0%')).toBeInTheDocument()
    expect(getSupplierPerformance).toHaveBeenCalledWith('sup-1')
  })

  it('does not query performance until a supplier is chosen', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByRole('button', { name: /supplierPerformance.title/ }))
    await screen.findByLabelText('reports.supplierPerformance.selectLabel')
    expect(getSupplierPerformance).not.toHaveBeenCalled()
  })
})
