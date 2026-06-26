import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AgingBucketsWidget from '@/components/dashboard/AgingBucketsWidget'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

function renderWidget() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <AgingBucketsWidget />
      </I18nextProvider>
    </QueryClientProvider>
  )
}

const sampleAging = {
  buckets: {
    '0_30': { bucketKey: '0_30', displayName: '0-30 jours', invoiceCount: 2, totalAmount: 1000 },
    '31_60': { bucketKey: '31_60', displayName: '31-60 jours', invoiceCount: 1, totalAmount: 500 },
    '61_90': { bucketKey: '61_90', displayName: '61-90 jours', invoiceCount: 0, totalAmount: 0 },
    '90_plus': { bucketKey: '90_plus', displayName: '90+ jours', invoiceCount: 1, totalAmount: 300 },
  },
  totalOverdueAmount: 1800,
  totalOverdueInvoiceCount: 4,
  supplierRollup: [
    { supplierId: 'a', supplierName: 'Alpha SA', invoiceCount: 3, totalOverdueAmount: 1500, amountByBucket: {} },
  ],
}

describe('AgingBucketsWidget', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the widget title when data is loaded', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleAging } })
    renderWidget()
    expect(await screen.findByTestId('aging-buckets-widget')).toBeInTheDocument()
    expect(screen.getByText("Ancienneté des factures en retard")).toBeInTheDocument()
  })

  it('shows empty state when there are no overdue invoices', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: {
        data: {
          buckets: {},
          totalOverdueAmount: 0,
          totalOverdueInvoiceCount: 0,
          supplierRollup: [],
        },
      },
    })
    renderWidget()
    expect(await screen.findByTestId('aging-buckets-empty')).toBeInTheDocument()
  })
})
