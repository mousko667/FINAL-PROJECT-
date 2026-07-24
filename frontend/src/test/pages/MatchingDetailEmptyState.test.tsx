import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import { AxiosError, AxiosHeaders } from 'axios'
import i18n from '@/i18n'
import MatchingDetailPage from '@/pages/matching/MatchingDetailPage'
import { getMatchingLines } from '@/services/matchingService'

vi.mock('@/services/matchingService', () => ({
  getMatchingLines: vi.fn(),
  resolveMatchingLine: vi.fn(),
}))
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

function httpError(status: number) {
  const err = new AxiosError('failed')
  err.response = {
    status, statusText: '', data: {},
    headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() },
  }
  return err
}

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/matching/i1']}>
        <I18nextProvider i18n={i18n}>
          <Routes><Route path="/matching/:invoiceId" element={<MatchingDetailPage />} /></Routes>
        </I18nextProvider>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('AUDIT-025 — a 404 is an empty state, not a breakdown', () => {
  beforeEach(() => {
    vi.mocked(getMatchingLines).mockReset()
  })
  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('shows the dedicated empty state when the invoice has no matching (404)', async () => {
    vi.mocked(getMatchingLines).mockRejectedValue(httpError(404))
    renderPage()

    expect(await screen.findByTestId('matching-empty')).toBeInTheDocument()
    expect(screen.getByText(i18n.t('matching.emptyTitle'))).toBeInTheDocument()
    // The error screen must NOT appear: 404 is the backend's normal answer here.
    expect(screen.queryByText(i18n.t('matching.error'))).toBeNull()
  })

  it('still shows the error screen on a real server failure (500)', async () => {
    vi.mocked(getMatchingLines).mockRejectedValue(httpError(500))
    renderPage()

    expect(await screen.findByText(i18n.t('matching.error'))).toBeInTheDocument()
    expect(screen.queryByTestId('matching-empty')).toBeNull()
  })
})
