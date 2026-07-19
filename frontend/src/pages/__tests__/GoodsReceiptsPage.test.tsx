import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import authReducer from '@/store/slices/authSlice'
import GoodsReceiptsPage from '../GoodsReceiptsPage'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'fr' } }),
}))

vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn((url) => {
      if (url === '/goods-receipts') {
        return Promise.resolve({ data: { data: [{ id: 'grn1', grnNumber: 'GRN-123', status: 'RECEIVED' }] } })
      }
      if (url.startsWith('/goods-receipts/')) {
        return Promise.resolve({ data: { data: { id: 'grn1', grnNumber: 'GRN-123', items: [{ id: 'item1', itemDescription: 'Laptop', receivedQuantity: 5 }] } } })
      }
      return Promise.resolve({ data: { data: [] } })
    }),
    post: vi.fn().mockResolvedValue({ data: {} }),
  }
}))

const makeStore = (roles: string[]) =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: '1', username: 'test', email: 'test@oct.fr', roles, departmentId: '1' },
        accessToken: 'token',
        refreshToken: null,
        isAuthenticated: true,
      },
    },
  })

const renderPage = (roles: string[]) => {
  const store = makeStore(roles)
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={store}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <GoodsReceiptsPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('GoodsReceiptsPage N8 Test', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders list and opens detail modal', async () => {
    renderPage(['ROLE_DAF'])
    expect(await screen.findByText('GRN-123')).toBeInTheDocument()
    
    // Click View
    const viewBtn = await screen.findByText('app.view')
    fireEvent.click(viewBtn)

    // Wait for modal to load item details
    expect(await screen.findByText('Laptop')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
  })
})
