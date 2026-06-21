import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import VolumeTrendSection from '@/components/reports/VolumeTrendSection'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

function renderSection() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <VolumeTrendSection />
      </I18nextProvider>
    </QueryClientProvider>
  )
}

const sampleTrend = {
  fromDate: '2025-07-01',
  toDate: '2026-06-21',
  points: [
    { monthLabel: '2026-05', year: 2026, month: 5, invoiceCount: 4, totalAmount: 4000 },
    { monthLabel: '2026-06', year: 2026, month: 6, invoiceCount: 2, totalAmount: 1500 },
  ],
}

describe('VolumeTrendSection', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the trend section title once loaded', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleTrend } })
    renderSection()
    expect(await screen.findByText('Tendances volume / valeur')).toBeInTheDocument()
  })

  it('shows the empty state when all months are zero', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { fromDate: '2025-07-01', toDate: '2026-06-21', points: [
        { monthLabel: '2026-06', year: 2026, month: 6, invoiceCount: 0, totalAmount: 0 },
      ] } },
    })
    renderSection()
    // titre toujours rendu ; le graphe est remplacé par l'état vide
    expect(await screen.findByText('Tendances volume / valeur')).toBeInTheDocument()
    expect(screen.getByTestId('volume-trend-empty')).toBeInTheDocument()
  })
})
