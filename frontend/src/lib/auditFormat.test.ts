import { describe, it, expect, vi } from 'vitest'

vi.mock('@/lib/format', () => ({ formatAmount: (n: number) => new Intl.NumberFormat('fr-FR').format(n) }))

import { formatAuditActor, formatAuditEntity, formatAuditDetails } from './auditFormat'

describe('formatAuditActor', () => {
  it('uses display name and role when present', () => {
    expect(formatAuditActor({ userDisplayName: 'Ndong Marie', userRole: 'DAF' }, 'Système'))
      .toEqual({ name: 'Ndong Marie', role: 'DAF' })
  })
  it('falls back to truncated uuid', () => {
    expect(formatAuditActor({ userId: '57858018-aaaa-bbbb' }, 'Système').name).toBe('#57858018')
  })
  it('shows system label when no actor', () => {
    expect(formatAuditActor({}, 'Système')).toEqual({ name: 'Système' })
  })
})

describe('formatAuditEntity', () => {
  const inv = (r: string) => `Facture ${r}`
  it('renders invoice reference', () => {
    expect(formatAuditEntity({ entityType: 'INVOICE', entityId: 'FAC-2026-0042' }, inv)).toBe('Facture FAC-2026-0042')
  })
  it('falls back for legacy URL entity', () => {
    expect(formatAuditEntity({ entityType: 'INVOICE', entityId: '/api/v1/invoices' }, inv)).toBe('INVOICE')
  })
  it('renders just the entity type for a legacy URL entityId (no # fragment)', () => {
    expect(formatAuditEntity({ entityType: 'PAYMENT', entityId: '/api/v1/payments/invoice/1eed8-xxx' }, inv)).toBe('PAYMENT')
  })
})

describe('formatAuditDetails', () => {
  it('renders amount, currency and supplier for business rows', () => {
    const row = { newValue: JSON.stringify({ amount: 850000, currency: 'XAF', supplier: 'SOGARA' }) }
    const out = formatAuditDetails(row)
    expect(out).toContain('XAF')
    expect(out).toContain('SOGARA')
    expect(out).toContain('850')
  })
  it('renders method and status for legacy HTTP rows (single-encoded details)', () => {
    const row = { details: JSON.stringify({ duration_ms: 83, method: 'POST', status: 200 }) }
    expect(formatAuditDetails(row)).toBe('POST · 200')
  })
  it('renders method and status for double-encoded legacy HTTP newValue (real data shape)', () => {
    const row = { newValue: JSON.stringify(JSON.stringify({ duration_ms: 759, method: 'POST', status: 200 })) }
    expect(formatAuditDetails(row)).toBe('POST · 200')
  })
})
