-- V40 — Seed a default active three-way matching configuration.
-- Without an active row in matching_config, submitting any invoice that carries a
-- purchaseOrderId fails (the matching check throws "No active matching configuration found",
-- which silently marks the transaction rollback-only and surfaces as a 500). The tolerance
-- values remain DB-driven (editable via the admin Matching Config screen) — this migration only
-- provides a sane initial row so the feature works out of the box. updated_by points at the
-- bootstrap admin seeded in V5.
INSERT INTO matching_config (tolerance_percentage, tolerance_amount, require_grn, is_active, updated_by)
SELECT 2.00, 0.00, TRUE, TRUE, u.id
FROM users u
WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM matching_config WHERE is_active = TRUE);
