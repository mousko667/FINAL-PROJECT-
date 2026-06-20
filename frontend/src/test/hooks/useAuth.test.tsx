import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import { type ReactNode } from 'react'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import { useAuth } from '@/hooks/useAuth'

// Mock apiClient
vi.mock('@/services/apiClient', () => ({
  default: {
    post: vi.fn(),
    get: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}))

// Mock react-router navigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

import apiClient from '@/services/apiClient'

function makeWrapper() {
  const store = configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
  })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  return ({ children }: { children: ReactNode }) => (
    <Provider store={store}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('useAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
  })

  it('navigates to /dashboard on successful login', async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({
      data: {
        data: {
          accessToken: 'access-123',
          refreshToken: 'refresh-456',
          userId: '1',
          username: 'admin',
          roles: ['ROLE_ADMIN'],
        },
      },
    })

    const { result } = renderHook(() => useAuth(), {
      wrapper: makeWrapper(),
    })

    act(() => {
      result.current.login({ username: 'admin', password: 'secret' })
    })

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/dashboard'))
  })

  it('exposes login function and initial state', () => {
    const { result } = renderHook(() => useAuth(), {
      wrapper: makeWrapper(),
    })

    expect(typeof result.current.login).toBe('function')
    expect(result.current.isLoggingIn).toBe(false)
    expect(result.current.loginError).toBe(false)
  })

  it('sets loginError to true on failed login', async () => {
    vi.mocked(apiClient.post).mockRejectedValueOnce(new Error('Unauthorized'))

    const { result } = renderHook(() => useAuth(), {
      wrapper: makeWrapper(),
    })

    act(() => {
      result.current.login({ username: 'admin', password: 'wrong' })
    })

    await waitFor(() => expect(result.current.loginError).toBe(true))
  })

  it('navigates to /login on logout', () => {
    const { result } = renderHook(() => useAuth(), {
      wrapper: makeWrapper(),
    })

    act(() => {
      result.current.logout()
    })

    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })
})
