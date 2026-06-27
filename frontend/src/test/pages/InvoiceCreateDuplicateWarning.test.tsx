import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import InvoiceCreatePage from '@/pages/InvoiceCreatePage'

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => vi.fn() }
})

// Render i18n keys verbatim with the interpolated count so assertions are stable.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: any) =>
      typeof opts === 'object' && opts?.count !== undefined ? `${key}:${opts.count}` : key,
  }),
}))

const checkDuplicate = vi.fn()
vi.mock('@/services/invoiceService', () => ({
  invoiceService: {
    checkDuplicate: (...args: any[]) => checkDuplicate(...args),
    create: vi.fn(),
    uploadDocument: vi.fn(),
  },
}))

// Suppliers / departments / POs come through the raw apiClient.
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn((url: string) => {
      if (url === '/suppliers') {
        return Promise.resolve({
          data: { data: { content: [{ id: 'sup-1', companyName: 'ACME', taxId: 'TX1', status: 'ACTIVE' }] } },
        })
      }
      if (url === '/departments') {
        return Promise.resolve({ data: { data: { content: [{ id: 'd1', code: 'IT', nameEn: 'IT', nameFr: 'IT' }] } } })
      }
      return Promise.resolve({ data: { data: { content: [] } } })
    }),
  },
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter><InvoiceCreatePage /></MemoryRouter>
    </QueryClientProvider>
  )
}

async function fillSupplierAndDescription(user: ReturnType<typeof userEvent.setup>) {
  // Wait for the supplier option to load, then select it (supplier is the first combobox).
  await screen.findByRole('option', { name: /ACME/ }, { timeout: 5000 })
  const supplierSelect = screen.getAllByRole('combobox')[0]
  await user.selectOptions(supplierSelect, 'sup-1')
  const description = screen.getByRole('textbox')
  await user.type(description, 'Maintenance Q1')
}

describe('InvoiceCreatePage advisory duplicate warning', () => {
  beforeEach(() => checkDuplicate.mockReset())

  it('shows the warning banner when a duplicate is detected', async () => {
    checkDuplicate.mockResolvedValue({ duplicate: true, count: 2 })
    const user = userEvent.setup()
    renderPage()
    await fillSupplierAndDescription(user)

    await waitFor(
      () => expect(checkDuplicate).toHaveBeenCalledWith('sup-1', 'Maintenance Q1'),
      { timeout: 2000 }
    )
    expect(await screen.findByText('invoice.duplicateWarning:2')).toBeInTheDocument()
  })

  it('shows no banner when there is no duplicate', async () => {
    checkDuplicate.mockResolvedValue({ duplicate: false, count: 0 })
    const user = userEvent.setup()
    renderPage()
    await fillSupplierAndDescription(user)

    await waitFor(() => expect(checkDuplicate).toHaveBeenCalled(), { timeout: 2000 })
    expect(screen.queryByText(/invoice.duplicateWarning/)).not.toBeInTheDocument()
  })

  it('does not call the API while the description is empty', async () => {
    checkDuplicate.mockResolvedValue({ duplicate: false, count: 0 })
    const user = userEvent.setup()
    renderPage()
    await screen.findByRole('option', { name: /ACME/ }, { timeout: 5000 })
    const supplierSelect = screen.getAllByRole('combobox')[0]
    await user.selectOptions(supplierSelect, 'sup-1')

    // Give the debounce window time to elapse; with no description it must stay silent.
    await new Promise((r) => setTimeout(r, 700))
    expect(checkDuplicate).not.toHaveBeenCalled()
  })
})
