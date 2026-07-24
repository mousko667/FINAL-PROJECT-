import { useEffect, useState } from 'react'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { store } from '@/store'
import { setCredentials, logout } from '@/store/slices/authSlice'
import AppRoutes from './AppRoutes'
import '@/i18n'
import apiClient from '@/services/apiClient'
import { isNetworkError } from '@/lib/apiError'
import { OfflineBanner } from '@/components/OfflineBanner'
import { ErrorToaster } from '@/components/ErrorToaster'
import { useSessionTimeout } from '@/hooks/useSessionTimeout'
import { useTheme } from '@/hooks/useTheme'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
})

// Rehydrates user profile from backend on every page load/refresh.
// Without this, user is null after refresh even with a valid token,
// breaking role-based routing, sidebar, and action buttons.
function AuthRehydrator({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false)
  const [offline, setOffline] = useState(false)
  // Bumped by the retry button to re-run the effect after an outage.
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      setReady(true)
      return
    }
    setOffline(false)
    apiClient
      .get('/profile')
      .then((res) => {
        const d = res.data?.data ?? res.data
        store.dispatch(
          setCredentials({
            user: {
              id: d.id,
              username: d.username,
              email: d.email ?? '',
              roles: Array.isArray(d.roles) ? d.roles : [],
              departmentId: d.departmentId ?? undefined,
              supplierId: d.supplierId ?? undefined,
            },
            accessToken: token,
            refreshToken: localStorage.getItem('refreshToken') ?? '',
          })
        )
      })
      .catch((err) => {
        // AUDIT-014: a backend outage rejects with NO response at all. Wiping the
        // session here turned every server outage into a silent logout. Only an
        // actual authentication rejection may clear the token.
        if (isNetworkError(err)) {
          setOffline(true)
          return
        }
        // Token invalid or expired — clear and send to login
        store.dispatch(logout())
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
      })
      .finally(() => setReady(true))
  }, [attempt])

  if (!ready) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  // AUDIT-014: the session is kept intact — the user is not signed out by an outage.
  if (offline) {
    return <OfflineBanner onRetry={() => setAttempt((n) => n + 1)} />
  }

  return <>{children}</>
}

// Mounts the inactivity-timeout watcher inside the Router (needs useNavigate + the store).
function SessionTimeoutManager() {
  useSessionTimeout()
  return null
}

// Activates the light/dark theme (OS preference or the user's saved choice) on the
// document root for every layout — staff and supplier alike.
function ThemeManager() {
  useTheme()
  return null
}

function App() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthRehydrator>
            <ThemeManager />
            <SessionTimeoutManager />
            <AppRoutes />
            {/* AUDIT-014: single mount point for mutation failure notices. */}
            <ErrorToaster />
          </AuthRehydrator>
        </BrowserRouter>
      </QueryClientProvider>
    </Provider>
  )
}

export default App
