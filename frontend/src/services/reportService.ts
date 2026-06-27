import apiClient from '@/services/apiClient'
import type { ApiResponse } from '@/types/invoice'

export interface DashboardKpi {
  totalInvoices: number
  countByStatus: Record<string, number>
  averageProcessingTimeDays: number
  rejectionRate: number
  overdueCount: number
  volumeBySupplier: Record<string, number>
}

export interface SupplierPerformance {
  supplierId: string
  supplierName: string
  invoiceAccuracyRate: number | null
  rejectionRate: number | null
  averagePaymentDays: number | null
  totalInvoicesSubmitted: number
  matchedInvoices: number
  mismatchedInvoices: number
}

export const reportService = {
  getKpis: async (): Promise<DashboardKpi> => {
    const { data } = await apiClient.get<ApiResponse<DashboardKpi>>('/reports/kpis')
    return data.data
  },

  getSupplierPerformance: async (supplierId: string): Promise<SupplierPerformance> => {
    const { data } = await apiClient.get<ApiResponse<SupplierPerformance>>(
      `/reports/supplier/${supplierId}/performance`
    )
    return data.data
  },

  exportExcel: async (params: {
    status?: string
    fromDate?: string
    toDate?: string
    reference?: string
  }): Promise<void> => {
    const response = await apiClient.get('/reports/export/excel', {
      params,
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([response.data as BlobPart]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'invoices_report.xlsx')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  exportAuditPdf: async (id: string): Promise<void> => {
    const response = await apiClient.get(`/reports/export/pdf/audit/${id}`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([response.data as BlobPart]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `invoice_audit_${id}.pdf`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  exportCompliancePdf: async (startDate: string, endDate: string): Promise<void> => {
    const response = await apiClient.get('/reports/export/pdf/compliance', {
      params: { startDate, endDate },
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([response.data as BlobPart]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'compliance_report.pdf')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
}
