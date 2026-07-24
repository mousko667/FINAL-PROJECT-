/**
 * Roles allowed to read invoice data in the UI.
 *
 * AUDIT-003: `InvoiceListPage` guarded its own copy of this list while
 * `InvoiceDetailPage` had no guard at all, so an ADMIN opening `/invoices/:id`
 * hit a generic "Une erreur est survenue" screen instead of the explicit refusal
 * shown on the other 18 financial pages. Both pages now share one definition —
 * two copies would drift.
 *
 * Mirrors the backend guard on `GET /api/v1/invoices/{id}`
 * (`InvoiceController:99`, `!hasRole('ADMIN')`): ADMIN is excluded, financial
 * data is not a technical-administration surface (SoD).
 */
export const INVOICE_VIEW_ROLES = [
  'ROLE_ASSISTANT_COMPTABLE',
  'ROLE_DAF',
  'ROLE_VALIDATEUR_N1_DRH',
  'ROLE_VALIDATEUR_N1_DG',
  'ROLE_VALIDATEUR_N1_INFO',
  'ROLE_VALIDATEUR_N2_INFO',
  'ROLE_VALIDATEUR_N1_TERM',
  'ROLE_VALIDATEUR_N1_COM',
  'ROLE_VALIDATEUR_N1_QHSSE',
  'ROLE_VALIDATEUR_N1_INFRA',
  'ROLE_VALIDATEUR_N2_INFRA',
  'ROLE_VALIDATEUR_N1_TECH',
  'ROLE_VALIDATEUR_N2_TECH',
]
