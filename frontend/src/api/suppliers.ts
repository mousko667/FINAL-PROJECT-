import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { notifyApiError } from '@/components/ErrorToaster'

export interface Supplier {
  id: string
  companyName: string
  taxId: string
  contactEmail: string
  contactPhone?: string
  address?: string
  status: 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED'
  category?: SupplierCategory | null
  onboardingDate?: string
  createdAt?: string
  updatedAt?: string
}

export type SupplierCategory = 'GOODS' | 'SERVICES' | 'WORKS' | 'CONSULTING'

export interface SupplierPerformance {
  accuracyRate: number
  rejectionRate: number
  averagePaymentTimeDays: number
}

export interface SupplierDocument {
  id: string
  documentType: 'TAX_CERTIFICATE' | 'CONTRACT' | 'OTHER'
  originalFilename: string
  uploadedAt: string
}

export interface PagedResponse<T> {
  content: T[]
  pageable: {
    pageNumber: number
    pageSize: number
  }
  totalElements: number
  totalPages: number
}

// Hooks

/** Filters accepted by GET /suppliers (AUDIT-016: was `Record<string, any>` in a public signature). */
export interface SupplierFilters {
  page?: number
  size?: number
  name?: string
  taxId?: string
  status?: Supplier['status'] | ''
  category?: SupplierCategory | ''
  /** ISO-8601 instants, as the backend binds them with @DateTimeFormat(ISO.DATE_TIME). */
  from?: string
  to?: string
}

export function useSuppliers(filters: SupplierFilters = {}) {
  return useQuery({
    queryKey: ['suppliers', filters],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: PagedResponse<Supplier> }>('/suppliers', { params: filters })
      return data.data
    },
  })
}

export function useSupplier(id: string | undefined) {
  return useQuery({
    queryKey: ['supplier', id],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: Supplier }>(`/suppliers/${id}`)
      return data.data
    },
    enabled: !!id,
  })
}

export function useCreateSupplier() {
  const qc = useQueryClient()
  return useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: async (payload: Partial<Supplier> & { bankDetails?: string }) => {
      const { data } = await apiClient.post<{ data: Supplier }>('/suppliers', payload)
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['suppliers'] })
    },
  })
}

export function useUpdateSupplier(id: string | undefined) {
  const qc = useQueryClient()
  return useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: async (payload: Partial<Supplier> & { bankDetails?: string }) => {
      const { data } = await apiClient.put<{ data: Supplier }>(`/suppliers/${id}`, payload)
      return data.data
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['supplier', id] })
      qc.invalidateQueries({ queryKey: ['suppliers'] })
    },
  })
}

export function useActivateSupplier() {
  const qc = useQueryClient()
  return useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: async (id: string) => {
      const { data } = await apiClient.patch<{ data: Supplier }>(`/suppliers/${id}/activate`)
      return data.data
    },
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['supplier', id] })
      qc.invalidateQueries({ queryKey: ['suppliers'] })
    },
  })
}

export function useSuspendSupplier() {
  const qc = useQueryClient()
  return useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: async ({ id, reason }: { id: string; reason: string }) => {
      const { data } = await apiClient.patch<{ data: Supplier }>(`/suppliers/${id}/suspend`, { suspensionReason: reason })
      return data.data
    },
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['supplier', variables.id] })
      qc.invalidateQueries({ queryKey: ['suppliers'] })
    },
  })
}

export function useDeleteSupplier() {
  const qc = useQueryClient()
  return useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: async (id: string) => {
      await apiClient.delete(`/suppliers/${id}`)
    },
    onSuccess: (_, id) => {
      qc.invalidateQueries({ queryKey: ['supplier', id] })
      qc.invalidateQueries({ queryKey: ['suppliers'] })
    },
  })
}

export function useSupplierDocuments(id: string | undefined) {
  return useQuery({
    queryKey: ['supplier', id, 'documents'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierDocument[] }>(`/suppliers/${id}/documents`)
      return data.data
    },
    enabled: !!id,
  })
}

export function useUploadSupplierDocument() {
  const qc = useQueryClient()
  return useMutation({
    onError: (e) => notifyApiError(e),
    mutationFn: async ({ id, formData }: { id: string; formData: FormData }) => {
      const { data } = await apiClient.post<{ data: SupplierDocument }>(`/suppliers/${id}/documents`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      return data.data
    },
    onSuccess: (_, variables) => {
      qc.invalidateQueries({ queryKey: ['supplier', variables.id, 'documents'] })
    },
  })
}

export function useSupplierPerformance(id: string | undefined) {
  return useQuery({
    queryKey: ['supplier', id, 'performance'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SupplierPerformance }>(`/reports/supplier/${id}/performance`)
      return data.data
    },
    enabled: !!id,
  })
}
