import apiClient from '@/services/apiClient'
import type { Invoice, InvoiceStatus, PagedResponse, ApiResponse } from '@/types/invoice'

export interface InvoiceFilters {
  status?: InvoiceStatus
  departmentId?: string
  fromDate?: string
  toDate?: string
  reference?: string
  page?: number
  size?: number
}

export const invoiceService = {
  list: async (filters: InvoiceFilters = {}): Promise<PagedResponse<Invoice>> => {
    const { data } = await apiClient.get<ApiResponse<PagedResponse<Invoice>>>('/invoices', {
      params: {
        ...filters,
        page: filters.page ?? 0,
        size: filters.size ?? 20,
      },
    })
    return data.data
  },

  getById: async (id: string): Promise<Invoice> => {
    const { data } = await apiClient.get<ApiResponse<Invoice>>(`/invoices/${id}`)
    return data.data
  },

  create: async (payload: Partial<Invoice>): Promise<Invoice> => {
    const { data } = await apiClient.post<ApiResponse<Invoice>>('/invoices', payload)
    return data.data
  },

  updateStatus: async (
    id: string,
    action: string,
    reason?: string
  ): Promise<Invoice> => {
    const { data } = await apiClient.post<ApiResponse<Invoice>>(
      `/invoices/${id}/transition`,
      { action, reason }
    )
    return data.data
  },

  uploadDocument: async (id: string, file: File): Promise<void> => {
    const formData = new FormData()
    formData.append('file', file)
    await apiClient.post(`/invoices/${id}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  deleteDocument: async (invoiceId: string, docId: string): Promise<void> => {
    await apiClient.delete(`/invoices/${invoiceId}/documents/${docId}`)
  },
}
