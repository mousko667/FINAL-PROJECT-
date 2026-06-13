import { useEffect, useState } from 'react'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { store } from '@/store'
import { setCredentials, logout } from '@/store/slices/authSlice'
import AppRoutes from './AppRoutes'
import '@/i18n'
import apiClient from '@/services/apiClient'
import { useSessionTimeout } from '@/hooks/useSessionTimeout'

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

  useEffect(() => {
    const token = localStorage.getItem('accessToken')
    if (!token) {
      setReady(true)
      return
    }
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
      .catch(() => {
        // Token invalid or expired — clear and send to login
        store.dispatch(logout())
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
      })
      .finally(() => setReady(true))
  }, [])

  if (!ready) {
    return (
      <div className="flex items-center justify-center h-screen bg-gray-50">
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  return <>{children}</>
}

// Mounts the inactivity-timeout watcher inside the Router (needs useNavigate + the store).
function SessionTimeoutManager() {
  useSessionTimeout()
  return null
}

function App() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthRehydrator>
            <SessionTimeoutManager />
            <AppRoutes />
          </AuthRehydrator>
        </BrowserRouter>
      </QueryClientProvider>
    </Provider>
  )
}

export default App
