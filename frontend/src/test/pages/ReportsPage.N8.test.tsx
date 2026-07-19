import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import authReducer from '@/store/slices/authSlice'
import ReportsPage from '../../pages/ReportsPage'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k: string) => k, i18n: { language: 'fr' } }),
}))

vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn((url) => {
      if (url === '/reports/payment-cycle') {
        return Promise.resolve({ data: { data: { invoicesPaidCount: 10, avgSubmissionToBapDays: 2.5, avgBapToPaymentDays: 1.5, avgScheduledToProcessedDays: null, avgTotalCycleDays: 4.0 } } })
      }
      if (url.includes('/reports/supplier/')) {
        return Promise.resolve({ data: { data: [{ paymentId: 'p1', invoiceReference: 'INV-01', amountPaid: 1000, paymentMethod: 'VIREMENT', paymentDate: '2026-07-01T10:00:00Z', paymentReference: 'REF-1' }] } })
      }
      if (url === '/reports/activity?limit=20') {
        return Promise.resolve({ data: { data: [{ id: 'a1', invoiceId: 'i1', referenceNumber: 'INV-02', fromStatus: 'BROUILLON', toStatus: 'SOUMIS', changedBy: '1', changedByUsername: 'test', changeReason: null, changedAt: '2026-07-01T10:00:00Z' }] } })
      }
      return Promise.resolve({ data: { data: {} } })
    }),
  }
}))

vi.mock('@/services/reportService', () => ({
  reportService: {
    getKpis: vi.fn().mockResolvedValue({ totalInvoices: 0, overdueCount: 0, averageProcessingTimeDays: 0, rejectionRate: 0 }),
  }
}))

// Mock recharts to avoid rendering errors in JSDOM
vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: any) => <div>{children}</div>,
  BarChart: () => <div>BarChart</div>,
  Bar: () => <div>Bar</div>,
  XAxis: () => <div>XAxis</div>,
  YAxis: () => <div>YAxis</div>,
  CartesianGrid: () => <div>CartesianGrid</div>,
  Tooltip: () => <div>Tooltip</div>,
  LineChart: () => <div>LineChart</div>,
  Line: () => <div>Line</div>,
  Legend: () => <div>Legend</div>,
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
          <ReportsPage />
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('ReportsPage N8 sections', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders new report sections for DAF', async () => {
    renderPage(['ROLE_DAF'])
    
    // Check if sections are rendered
    expect(await screen.findByText('reports.paymentCycle.title')).toBeInTheDocument()
    expect(screen.getByText('reports.supplierHistory.title')).toBeInTheDocument()
    expect(screen.getByText('reports.recentActivity.title')).toBeInTheDocument()
  })
})
