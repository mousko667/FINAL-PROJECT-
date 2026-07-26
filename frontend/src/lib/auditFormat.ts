import { formatAmount } from '@/lib/format'

export type AuditRow = {
  userId?: string
  userDisplayName?: string
  userRole?: string
  ipAddress?: string
  entityType?: string
  entityId?: string
  details?: string
  newValue?: string
}

export function formatAuditActor(row: AuditRow, systemLabel: string): { name: string; role?: string } {
  if (row.userDisplayName) return { name: row.userDisplayName, role: row.userRole }
  if (row.userId) return { name: '#' + row.userId.slice(0, 8) }
  return { name: systemLabel }
}

export function formatAuditEntity(row: AuditRow, invoiceLabel: (ref: string) => string): string {
  const id = row.entityId
  if (id && !id.startsWith('/')) return invoiceLabel(id)
  if (id && id.startsWith('/')) return `${row.entityType ?? ''}#${id.slice(0, 8)}`
  return row.entityType ?? '—'
}

function tryParse(s?: string): any | null {
  if (!s) return null
  try { return JSON.parse(s) } catch { return null }
}

export function formatAuditDetails(row: AuditRow): string {
  const biz = tryParse(row.newValue)
  if (biz && (biz.amount != null || biz.currency || biz.supplier)) {
    const money = `${formatAmount(biz.amount)} ${biz.currency ?? ''}`.trim()
    return biz.supplier ? `${money} · ${biz.supplier}` : money
  }
  const http = tryParse(row.details) ?? tryParse(row.newValue)
  if (http && (http.method || http.status != null)) {
    return `${http.method ?? ''} · ${http.status ?? ''}`.replace(/^ · | · $/g, '').trim()
  }
  return row.details ?? row.newValue ?? '—'
}
