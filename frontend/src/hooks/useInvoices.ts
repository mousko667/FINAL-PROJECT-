import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { invoiceService, type InvoiceFilters } from '@/services/invoiceService'
import type { Invoice } from '@/types/invoice'

/**
 * useInvoices — fetches paginated invoice list.
 * Exported as a standalone hook so it can be reused and tested.
 */
export function useInvoices(filters: InvoiceFilters = {}) {
  return useQuery({
    queryKey: ['invoices', filters],
    queryFn: () => invoiceService.list(filters),
  })
}

/**
 * useInvoice — fetches a single invoice by id.
 */
export function useInvoice(id: string | undefined) {
  return useQuery({
    queryKey: ['invoice', id],
    queryFn: () => invoiceService.getById(id!),
    enabled: !!id,
  })
}

/**
 * useUpdateInvoiceStatus — triggers a workflow transition.
 */
export function useUpdateInvoiceStatus(invoiceId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ action, reason }: { action: string; reason?: string }) =>
      invoiceService.updateStatus(invoiceId, action, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoice', invoiceId] })
      qc.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}

/**
 * useCreateInvoice — creates a new invoice draft.
 */
export function useCreateInvoice() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: Partial<Invoice>) => invoiceService.create(payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}
