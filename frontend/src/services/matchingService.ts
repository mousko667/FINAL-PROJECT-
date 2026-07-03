import apiClient from '@/services/apiClient'

export interface MatchingSummary {
  invoiceId: string
  invoiceNumber: string
  supplierName: string
  purchaseOrderId: string | null
  purchaseOrderNumber: string | null
  grnPresent: boolean
  status: 'MATCHED' | 'PARTIAL' | 'MISMATCH' | 'OVERRIDDEN'
  lineCount: number
  discrepancyLineCount: number
  matchedAt: string | null
}

export interface LineComparison {
  description: string
  poQuantity: number | null
  poUnitPrice: number | null
  receivedQuantity: number | null
  invoiceQuantity: number
  invoiceUnitPrice: number
  qtyVariancePct: number | null
  priceVariancePct: number | null
  verdict: 'MATCHED' | 'WITHIN_TOLERANCE' | 'MISMATCH' | 'MISSING_IN_PO'
  poLineId?: string
  resolutionStatus?: string
  resolutionReason?: string
  resolvedBy?: string
  resolvedAt?: string
}

export interface MatchingDetail {
  summary: MatchingSummary
  discrepancyNotes: string | null
  overriddenBy: string | null
  overrideReason: string | null
  lines: LineComparison[]
}

export async function listMatching(params: { status?: string; search?: string; page?: number; size?: number }) {
  const { data } = await apiClient.get('/matching', { params })
  return data.data as { content: MatchingSummary[]; totalPages: number; page: number }
}

export async function getMatchingLines(invoiceId: string) {
  const { data } = await apiClient.get(`/matching/${invoiceId}/lines`)
  return data.data as MatchingDetail
}

export async function resolveMatchingLine(invoiceId: string, poLineId: string, reason: string) {
  await apiClient.post(`/matching/${invoiceId}/lines/${poLineId}/resolve`, { reason })
}
