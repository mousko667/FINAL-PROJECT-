import { describe, it, expect, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import AdminCompliancePage from '@/pages/admin/AdminCompliancePage'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string, fallback?: string) => (typeof fallback === 'string' ? fallback : key) }),
}))

// Back the four compliance queries with deterministic fixtures.
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn((url: string) => {
      const wrap = (data: unknown) => Promise.resolve({ data: { data } })
      if (url === '/compliance/backup-status') return wrap({ lastBackupAt: '2026-06-20T10:00:00Z', status: 'OK', detail: null })
      if (url === '/compliance/incidents') return wrap([
        { id: '1', title: 'Phishing', severity: 'HIGH', status: 'OPEN', reportedAt: '2026-06-01' },
        { id: '2', title: 'Old', severity: 'LOW', status: 'RESOLVED', reportedAt: '2026-05-01' },
        { id: '3', title: 'Probe', severity: 'MEDIUM', status: 'INVESTIGATING', reportedAt: '2026-06-10' },
      ])
      if (url === '/compliance/checklist') return wrap([
        { id: 'c1', framework: 'SOX', label: 'A', completed: true },
        { id: 'c2', framework: 'IFRS', label: 'B', completed: false },
      ])
      if (url === '/compliance/calendar') return wrap([
        { id: 'd1', title: 'Later deadline', dueDate: '2026-12-01', completed: false },
        { id: 'd2', title: 'Sooner deadline', dueDate: '2026-07-01', completed: false },
        { id: 'd3', title: 'Done deadline', dueDate: '2026-06-15', completed: true },
      ])
      return Promise.resolve({ data: { data: [] } })
    }),
    post: vi.fn(), patch: vi.fn(), delete: vi.fn(),
  },
}))

import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '@/store/slices/authSlice'

const makeStore = () =>
  configureStore({
    reducer: { auth: authReducer },
    preloadedState: {
      auth: {
        user: { id: '1', username: 'admin', email: 'admin@oct.fr', roles: ['ROLE_ADMIN'] },
        accessToken: 'token',
        refreshToken: null,
        isAuthenticated: true,
      },
    },
  })

function renderPage() {
  const store = makeStore()
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={store}>
      <QueryClientProvider client={qc}>
        <MemoryRouter><AdminCompliancePage /></MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

describe('AdminCompliancePage audit-prep synthesis', () => {
  it('rolls up open incidents, checklist progress and upcoming deadlines', async () => {
    renderPage()

    // 2 open (OPEN + INVESTIGATING), not the RESOLVED one.
    const openCard = (await screen.findByText('admin.compliance.auditPrep.openIncidents')).closest('div')!
    await waitFor(() => expect(openCard).toHaveTextContent('2'))

    // 1 of 2 checklist items completed.
    const checklistCard = screen.getByText('admin.compliance.auditPrep.checklistProgress').closest('div')!
    await waitFor(() => expect(checklistCard).toHaveTextContent('1/2'))

    // Scope to the audit-prep "next deadlines" list (the same titles also appear
    // in the full calendar section lower down the page).
    const deadlinesList = screen.getByText('admin.compliance.auditPrep.nextDeadlinesTitle').closest('div')!
    const rows = within(deadlinesList).getAllByRole('listitem')
    // 2 incomplete deadlines, soonest (2026-07-01) first; the completed one is excluded.
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Sooner deadline')
    expect(rows[1]).toHaveTextContent('Later deadline')
    expect(within(deadlinesList).queryByText('Done deadline')).not.toBeInTheDocument()
  })
})
