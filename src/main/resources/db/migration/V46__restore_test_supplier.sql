-- V46: Restore the seeded test supplier ("Fournisseur Test SA", tax_id GA-TEST-001)
-- linked to the `supplier` portal test account.
--
-- Why: a prior test session soft-deleted this supplier (deleted_at set), which broke the
-- supplier portal test account: POST /supplier/invoices returned 400 "Supplier not found or
-- deleted" because InvoiceService.populateSupplierFields resolves the linked supplier via
-- findByIdAndDeletedAtIsNull. The `supplier` user (username='supplier') stays linked to this
-- supplier_id, so clearing deleted_at re-enables portal submissions for demos and for verifying
-- the rejection / BON_A_PAYER supplier-notification flow (audit findings N5/N6) via MailHog.
--
-- Idempotent and data-only: targets the seed supplier by its stable tax_id and only clears the
-- soft-delete flag; other suppliers (some intentionally soft-deleted in demos) are left untouched.
UPDATE suppliers
SET deleted_at = NULL,
    updated_at = NOW()
WHERE tax_id = 'GA-TEST-001'
  AND deleted_at IS NOT NULL;
