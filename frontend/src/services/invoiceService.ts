import apiClient from '@/services/apiClient'
import type { Invoice, InvoiceStatus, PagedResponse, ApiResponse } from '@/types/invoice'

export interface ImportLineResult {
  line: number
  success: boolean
  invoiceId: string | null
  reference: string | null
  error: string | null
}

export interface ImportResult {
  total: number
  created: number
  failed: number
  results: ImportLineResult[]
}

export interface DuplicateCheckResult {
  duplicate: boolean
  count: number
}

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
        from: filters.fromDate,
        to: filters.toDate,
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

  checkDuplicate: async (
    supplierId: string,
    description: string
  ): Promise<DuplicateCheckResult> => {
    const { data } = await apiClient.get<ApiResponse<DuplicateCheckResult>>(
      '/invoices/duplicate-check',
      { params: { supplierId, description } }
    )
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

  importInvoices: async (file: File, departmentCode?: string): Promise<ImportResult> => {
    const formData = new FormData()
    formData.append('file', file)
    if (departmentCode) formData.append('departmentCode', departmentCode)
    const { data } = await apiClient.post<ApiResponse<ImportResult>>('/invoices/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data.data
  },
}
