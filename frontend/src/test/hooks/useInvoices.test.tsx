import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { type ReactNode } from 'react'
import { useInvoices, useInvoice } from '@/hooks/useInvoices'
import * as invoiceService from '@/services/invoiceService'
import type { PagedResponse } from '@/types/invoice'
import type { Invoice } from '@/types/invoice'

// Mock the entire invoiceService module
vi.mock('@/services/invoiceService', () => ({
  invoiceService: {
    list: vi.fn(),
    getById: vi.fn(),
    create: vi.fn(),
    updateStatus: vi.fn(),
    uploadDocument: vi.fn(),
    deleteDocument: vi.fn(),
  },
}))

const mockInvoice: Invoice = {
  id: 'inv-1',
  referenceNumber: 'REF-2024-001',
  supplierName: 'ACME SA',
  amount: 15000,
  currency: 'EUR',
  issueDate: '2024-01-01',
  dueDate: '2024-02-01',
  status: 'SOUMIS',
}

const mockPage: PagedResponse<Invoice> = {
  content: [mockInvoice],
  totalElements: 1,
  totalPages: 1,
  pageNumber: 0,
  pageSize: 20,
}

function makeWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  )
}

describe('useInvoices', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns paginated invoice data on success', async () => {
    vi.mocked(invoiceService.invoiceService.list).mockResolvedValue(mockPage)

    const { result } = renderHook(() => useInvoices(), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.content).toHaveLength(1)
    expect(result.current.data?.content[0].referenceNumber).toBe('REF-2024-001')
    expect(result.current.data?.totalElements).toBe(1)
  })

  it('passes filters to the service', async () => {
    vi.mocked(invoiceService.invoiceService.list).mockResolvedValue({
      ...mockPage,
      content: [],
      totalElements: 0,
    })

    const { result } = renderHook(
      () => useInvoices({ status: 'REJETE', page: 1 }),
      { wrapper: makeWrapper() }
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invoiceService.invoiceService.list).toHaveBeenCalledWith({
      status: 'REJETE',
      page: 1,
    })
  })

  it('is in error state when the service throws', async () => {
    vi.mocked(invoiceService.invoiceService.list).mockRejectedValue(
      new Error('Network error')
    )

    const { result } = renderHook(() => useInvoices(), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useInvoice', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('fetches a single invoice by id', async () => {
    vi.mocked(invoiceService.invoiceService.getById).mockResolvedValue(mockInvoice)

    const { result } = renderHook(() => useInvoice('inv-1'), {
      wrapper: makeWrapper(),
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(result.current.data?.id).toBe('inv-1')
    expect(result.current.data?.status).toBe('SOUMIS')
    expect(invoiceService.invoiceService.getById).toHaveBeenCalledWith('inv-1')
  })

  it('is disabled when id is undefined', () => {
    const { result } = renderHook(() => useInvoice(undefined), {
      wrapper: makeWrapper(),
    })

    // Should not fire since enabled: false
    expect(result.current.fetchStatus).toBe('idle')
    expect(invoiceService.invoiceService.getById).not.toHaveBeenCalled()
  })
})
