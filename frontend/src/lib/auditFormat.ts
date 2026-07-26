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
  if (id && id.startsWith('/')) return row.entityType ?? '—'
  return row.entityType ?? '—'
}

function tryParse(s?: string): any | null {
  if (!s) return null
  try { return JSON.parse(s) } catch { return null }
}

/**
 * Parses a value and, if the result is itself a string (double-encoded JSON,
 * as produced by the legacy HTTP-telemetry audit rows), parses it once more.
 * Defensive: never throws; an unparseable inner value yields null.
 */
function tryParseDeep(s?: string): any | null {
  const first = tryParse(s)
  if (typeof first === 'string') return tryParse(first)
  return first
}

export function formatAuditDetails(row: AuditRow): string {
  const biz = tryParseDeep(row.newValue)
  if (biz && (biz.amount != null || biz.currency || biz.supplier)) {
    const money = `${formatAmount(biz.amount)} ${biz.currency ?? ''}`.trim()
    return biz.supplier ? `${money} · ${biz.supplier}` : money
  }
  const http = tryParseDeep(row.details) ?? tryParseDeep(row.newValue)
  if (http && (http.method || http.status != null)) {
    return `${http.method ?? ''} · ${http.status ?? ''}`.replace(/^ · | · $/g, '').trim()
  }
  return row.details ?? row.newValue ?? '—'
}
