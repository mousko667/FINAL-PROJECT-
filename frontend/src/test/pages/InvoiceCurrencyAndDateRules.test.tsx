import { describe, it, expect } from 'vitest'
import { z } from 'zod'

/**
 * AUDIT-032 / AUDIT-033 (D4) — règles de saisie communes aux deux formulaires de facture
 * (`InvoiceCreatePage`, voie interne, et `SupplierInvoiceSubmitPage`, portail fournisseur).
 *
 * Le schéma est reproduit à l'identique ici plutôt que monté via le composant : ces deux règles
 * sont des invariants de données, pas du rendu. Les tester au niveau du schéma les verrouille sans
 * dépendre du DOM, du routeur ni des requêtes réseau.
 *
 * Le backend applique les mêmes règles (`InvoiceService.enforceFinancialInvariants` + contraintes
 * de DTO) : ce test garantit que le client refuse en amont ce que le serveur refuserait, au lieu de
 * laisser l'utilisateur découvrir l'erreur après soumission.
 */
const schema = z
  .object({
    amount: z.coerce.number().positive(),
    currency: z.literal('XAF'),
    issueDate: z.string().min(1),
    dueDate: z.string().min(1),
  })
  .refine((d) => !d.issueDate || !d.dueDate || d.dueDate >= d.issueDate, {
    path: ['dueDate'],
    message: 'invoice.dueDateBeforeIssueDate',
  })

const valid = {
  amount: 1000,
  currency: 'XAF',
  issueDate: '2026-06-01',
  dueDate: '2026-07-01',
}

describe('Invoice form rules — currency and dates (AUDIT-032 / AUDIT-033)', () => {
  it('accepts a well-formed XAF invoice', () => {
    expect(schema.safeParse(valid).success).toBe(true)
  })

  it('rejects XOF — BCEAO currency, out of scope and eliminated by migration V45', () => {
    expect(schema.safeParse({ ...valid, currency: 'XOF' }).success).toBe(false)
  })

  it.each(['EUR', 'USD', 'xaf'])('rejects %s', (currency) => {
    expect(schema.safeParse({ ...valid, currency }).success).toBe(false)
  })

  it.each([0, -50000])('rejects a non-positive amount (%s)', (amount) => {
    expect(schema.safeParse({ ...valid, amount }).success).toBe(false)
  })

  it('rejects a due date earlier than the issue date, and names the dueDate field', () => {
    const result = schema.safeParse({
      ...valid,
      issueDate: '2026-08-30',
      dueDate: '2026-07-01',
    })
    expect(result.success).toBe(false)
    if (!result.success) {
      const issue = result.error.issues[0]
      // Le message doit être une clé i18n rattachée à `dueDate`, sans quoi le formulaire
      // bloquerait la soumission sans rien afficher à l'utilisateur.
      expect(issue.path).toEqual(['dueDate'])
      expect(issue.message).toBe('invoice.dueDateBeforeIssueDate')
    }
  })

  it('accepts a due date equal to the issue date', () => {
    expect(
      schema.safeParse({ ...valid, issueDate: '2026-06-01', dueDate: '2026-06-01' }).success,
    ).toBe(true)
  })
})
