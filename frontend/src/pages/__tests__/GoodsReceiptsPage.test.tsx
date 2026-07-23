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
        // AUDIT-028: the unfiltered branch returns a PAGINATED payload. This mock used to serve a
        // bare array — a shape the backend never produces — so the test passed identically before
        // and after the fix and proved nothing about the finding.
        return Promise.resolve({
          data: {
            data: {
              content: [{
                id: 'grn1',
                grnNumber: 'GRN-123',
                purchaseOrderNumber: 'PO-2026-001',
                receivedByUsername: 'aa',
                receiptDate: '2026-06-01',
                items: [{ id: 'item1', itemDescription: 'Laptop', receivedQuantity: 5 }],
                createdAt: '2026-06-01T10:00:00Z',
              }],
              page: 0,
              size: 200,
              totalElements: 1,
              totalPages: 1,
              last: true,
            },
          },
        })
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

  // AUDIT-028: the row used to read `supplierName`, `receivedDate`, `status` and `notes` — four
  // fields GoodsReceiptDTO never returns. Nobody saw it because the list was always empty. These
  // assertions pin the row to what the backend actually sends.
  it('renders the row from the paginated payload, with the fields the API really returns', async () => {
    renderPage(['ROLE_DAF'])
    expect(await screen.findByText('GRN-123')).toBeInTheDocument()
    expect(screen.getByText('PO-2026-001')).toBeInTheDocument()
    expect(screen.getByText('aa')).toBeInTheDocument()
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
