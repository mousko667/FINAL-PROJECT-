import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import RetentionComplianceCard from '@/components/audit/RetentionComplianceCard'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

function renderCard() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <RetentionComplianceCard />
      </I18nextProvider>
    </QueryClientProvider>
  )
}

const base = {
  retentionYears: 10, active: true, lastSweepAt: '2026-06-20T02:30:00Z',
  lastFlaggedCount: 0, sweepOverdue: false, updatedAt: '2026-06-19T10:00:00Z',
}

describe('RetentionComplianceCard', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders CONFORME status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'CONFORME' } } } as never)
    renderCard()
    await waitFor(() => expect(screen.getByText('Conforme')).toBeInTheDocument())
  })

  it('renders ATTENTION status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'ATTENTION', lastFlaggedCount: 3 } } } as never)
    renderCard()
    await waitFor(() => expect(screen.getByText('Attention')).toBeInTheDocument())
  })

  it('renders NON_CONFORME status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'NON_CONFORME', active: false } } } as never)
    renderCard()
    await waitFor(() => expect(screen.getByText('Non conforme')).toBeInTheDocument())
  })

  it('renders nothing when the request fails', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('403'))
    const { container } = renderCard()
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled())
    expect(container.textContent).toBe('')
  })

  it('renders nothing when the API returns an unknown status', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: { ...base, status: 'UNKNOWN' } } } as never)
    const { container } = renderCard()
    await waitFor(() => expect(apiClient.get).toHaveBeenCalled())
    expect(container.textContent).toBe('')
  })
})
