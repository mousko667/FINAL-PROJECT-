import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AuditSummary from '@/components/audit/AuditSummary'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}))

const mockSummary = {
  from: '2026-05-21', to: '2026-06-20', totalEvents: 7,
  byAction: [{ label: 'LOGIN', count: 5 }, { label: 'USER_CREATE', count: 2 }],
  byUser: [{ label: 'alice', count: 4 }],
  byEntityType: [{ label: 'User', count: 7 }],
  byDay: [{ label: '2026-06-20', count: 7 }],
}

function renderWithProviders() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <AuditSummary scope="system" />
      </I18nextProvider>
    </QueryClientProvider>
  )
}

describe('AuditSummary', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders aggregated panels from the API', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ data: { data: mockSummary } } as never)
    renderWithProviders()
    await waitFor(() => expect(screen.getByText('LOGIN')).toBeInTheDocument())
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('USER_CREATE')).toBeInTheDocument()
    // total events (7) appears at least once; several buckets also count 7
    expect(screen.getAllByText('7').length).toBeGreaterThan(0)
  })
})
