import { describe, it, expect } from 'vitest'
import { formatAmount, formatDate, formatDateTime } from '@/lib/format'

describe('formatAmount', () => {
  it('formats a large number with FR thousands grouping (no US comma)', () => {
    const result = formatAmount(38900000)
    expect(result).not.toContain(',')
    expect(result).toContain('900')
    expect(result).toContain('38')
  })

  it('accepts a numeric string', () => {
    const result = formatAmount('1500000')
    expect(result).not.toContain(',')
    expect(result).toContain('500')
  })

  it('returns em-dash for null/undefined/empty/invalid', () => {
    expect(formatAmount(null)).toBe('—')
    expect(formatAmount(undefined)).toBe('—')
    expect(formatAmount('')).toBe('—')
    expect(formatAmount('not-a-number')).toBe('—')
  })

  it('formats zero as 0, not em-dash', () => {
    expect(formatAmount(0)).toBe('0')
  })
})

describe('formatDate', () => {
  it('formats an ISO date string as JJ/MM/AAAA', () => {
    expect(formatDate('2026-07-31')).toBe('31/07/2026')
  })

  it('formats a Date object as JJ/MM/AAAA', () => {
    expect(formatDate(new Date(2026, 6, 4))).toBe('04/07/2026')
  })

  it('returns em-dash for null/undefined/empty/invalid', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate(undefined)).toBe('—')
    expect(formatDate('')).toBe('—')
    expect(formatDate('not-a-date')).toBe('—')
  })
})

describe('formatDateTime', () => {
  it('formats an ISO datetime string in fr-FR (date + time)', () => {
    const result = formatDateTime('2026-07-31T14:30:00')
    expect(result).toContain('31/07/2026')
    // fr-FR uses 24h time, so it must not show AM/PM
    expect(result).not.toMatch(/AM|PM/i)
  })

  it('returns em-dash for null/undefined/empty/invalid', () => {
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime(undefined)).toBe('—')
    expect(formatDateTime('')).toBe('—')
    expect(formatDateTime('not-a-date')).toBe('—')
  })
})
