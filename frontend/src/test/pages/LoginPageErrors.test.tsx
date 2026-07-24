import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import { AxiosError, AxiosHeaders } from 'axios'
import i18n from '@/i18n'
import LoginPage from '@/pages/LoginPage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'

vi.mock('@/services/apiClient', () => ({
  default: { post: vi.fn(), get: vi.fn() },
  BASE_URL: 'http://localhost:8080/api/v1',
}))

const makeStore = () =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user: null, accessToken: null, refreshToken: null, isAuthenticated: false },
    },
  })

function renderLogin() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <Provider store={makeStore()}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <LoginPage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

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

async function submitLogin() {
  fireEvent.change(screen.getByLabelText(/utilisateur/i), { target: { value: 'aa' } })
  fireEvent.change(screen.getByLabelText(/mot de passe/i), { target: { value: 'Test1234!' } })
  fireEvent.click(screen.getByRole('button', { name: /se connecter/i }))
}

describe('AUDIT-035 — login error messages', () => {
  beforeEach(() => {
    vi.mocked(apiClient.post).mockReset()
  })

  afterEach(async () => {
    cleanup()
    await i18n.changeLanguage('fr')
  })

  it('shows "service unavailable", NOT "wrong credentials", when the backend is down', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(networkError())
    renderLogin()
    await submitLogin()

    await waitFor(() => {
      expect(screen.getByText(i18n.t('error.network'))).toBeInTheDocument()
    })
    // The whole point of the finding: the misleading message must be gone.
    expect(screen.queryByText(i18n.t('auth.loginError'))).toBeNull()
  })

  it('still shows "wrong credentials" on a real 401', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(httpError(401))
    renderLogin()
    await submitLogin()

    await waitFor(() => {
      expect(screen.getByText(i18n.t('auth.loginError'))).toBeInTheDocument()
    })
    expect(screen.queryByText(i18n.t('error.network'))).toBeNull()
  })

  it('keeps the locked-account message on a 423', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(httpError(423))
    renderLogin()
    await submitLogin()

    await waitFor(() => {
      expect(screen.getByText(i18n.t('auth.accountLocked'))).toBeInTheDocument()
    })
  })

  it('shows a generic server error on a 500 rather than blaming the credentials', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(httpError(500))
    renderLogin()
    await submitLogin()

    await waitFor(() => {
      expect(screen.getByText(i18n.t('error.server'))).toBeInTheDocument()
    })
    expect(screen.queryByText(i18n.t('auth.loginError'))).toBeNull()
  })
})
