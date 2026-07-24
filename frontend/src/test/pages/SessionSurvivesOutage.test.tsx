import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup, waitFor } from '@testing-library/react'
import { AxiosError, AxiosHeaders } from 'axios'
import App from '@/App'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn(), post: vi.fn() },
  BASE_URL: 'http://localhost:8080/api/v1',
}))
// AppRoutes pulls in the whole page tree; the session behaviour under test lives
// in AuthRehydrator, so a stub keeps this test about that and nothing else.
vi.mock('@/AppRoutes', () => ({ default: () => <div>routes</div> }))

function httpError(status: number) {
  const err = new AxiosError('failed')
  err.response = {
    status, statusText: '', data: {},
    headers: new AxiosHeaders(), config: { headers: new AxiosHeaders() },
  }
  return err
}

function networkError() {
  const err = new AxiosError('Network Error', 'ERR_NETWORK')
  err.request = {}
  return err
}

describe('AUDIT-014 — a backend outage must not sign the user out', () => {
  beforeEach(() => {
    localStorage.setItem('accessToken', 'token')
    localStorage.setItem('refreshToken', 'refresh')
    vi.mocked(apiClient.get).mockReset()
  })
  afterEach(() => {
    cleanup()
    localStorage.clear()
  })

  it('keeps the session and shows the service-unavailable screen when /profile is unreachable', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(networkError())
    render(<App />)

    expect(await screen.findByTestId('offline-banner')).toBeInTheDocument()
    // The regression this finding is about: the token used to be wiped here.
    expect(localStorage.getItem('accessToken')).toBe('token')
    expect(localStorage.getItem('refreshToken')).toBe('refresh')
  })

  it('still clears the session when the token is genuinely rejected (401)', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(httpError(401))
    render(<App />)

    await waitFor(() => {
      expect(localStorage.getItem('accessToken')).toBeNull()
    })
    expect(screen.queryByTestId('offline-banner')).toBeNull()
  })
})
