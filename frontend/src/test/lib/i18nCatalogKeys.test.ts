import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { translateApiMessage } from '@/types/apiError'

// AUDIT-015 / AUDIT-044 — catalog guarantees for the keys the UI and the backend rely on.
//
// AUDIT-044: the ErrorBoundary (a class component) now renders i18n keys via i18next.t. Those keys
// must exist in BOTH catalogs, or the crash screen shows the raw key.
// AUDIT-015: the backend returns i18n KEYS as success/error messages (PROB-006); translateApiMessage
// passes them through t(). The 7 success keys must exist frontend-side, or a confirmation shows the
// raw key like `payment.recorded.success`. Parity FR<->EN alone does NOT catch a key absent from BOTH
// (that is exactly what produced AUDIT-039), so we assert presence explicitly in each catalog.

const dir = path.dirname(fileURLToPath(import.meta.url))
const FR = JSON.parse(readFileSync(path.resolve(dir, '../../i18n/fr.json'), 'utf-8'))
const EN = JSON.parse(readFileSync(path.resolve(dir, '../../i18n/en.json'), 'utf-8'))

function get(obj: unknown, dotted: string): unknown {
  return dotted.split('.').reduce<unknown>((cur, part) => {
    if (cur && typeof cur === 'object' && part in (cur as Record<string, unknown>)) {
      return (cur as Record<string, unknown>)[part]
    }
    return undefined
  }, obj)
}

const ERROR_BOUNDARY_KEYS = ['errorBoundary.title', 'errorBoundary.message', 'errorBoundary.retry']

// The 7 backend success keys sent as ApiResponse.message (InvoiceController / PaymentController).
const BACKEND_SUCCESS_KEYS = [
  'action.submit.success',
  'action.assign.success',
  'action.validate.success',
  'action.bon_a_payer.success',
  'action.reject.success',
  'payment.recorded.success',
  'payment.processed.success',
]

describe('i18n catalog keys (AUDIT-044 / AUDIT-015)', () => {
  it.each(ERROR_BOUNDARY_KEYS)('ErrorBoundary key "%s" exists in both catalogs', (key) => {
    expect(get(FR, key), `${key} missing from fr.json`).toBeTypeOf('string')
    expect(get(EN, key), `${key} missing from en.json`).toBeTypeOf('string')
  })

  it.each(BACKEND_SUCCESS_KEYS)('backend success key "%s" exists in both catalogs', (key) => {
    expect(get(FR, key), `${key} missing from fr.json`).toBeTypeOf('string')
    expect(get(EN, key), `${key} missing from en.json`).toBeTypeOf('string')
  })
})

describe('translateApiMessage (AUDIT-015)', () => {
  it('translates a known backend i18n key instead of showing it raw', () => {
    const frLabel = get(FR, 'payment.recorded.success') as string
    const error = { response: { data: { message: 'payment.recorded.success' } } }
    const t = (key: string) => (get(FR, key) as string) ?? key
    expect(translateApiMessage(error, t)).toBe(frLabel)
    expect(translateApiMessage(error, t)).not.toBe('payment.recorded.success')
  })

  it('falls back to the raw message when the key is not in the catalog', () => {
    // A backend that already localised its message (via Accept-Language) must survive verbatim.
    const raw = 'Un paiement ne peut être enregistré que pour les factures BON_A_PAYER.'
    const error = { response: { data: { message: raw } } }
    const t = (key: string) => (get(FR, key) as string) ?? key // returns key when unknown
    expect(translateApiMessage(error, t)).toBe(raw)
  })

  it('returns undefined when the error carries no message, so the caller keeps its generic fallback', () => {
    expect(translateApiMessage(new Error('boom'), (k) => k)).toBeUndefined()
    expect(translateApiMessage(undefined, (k) => k)).toBeUndefined()
  })
})
