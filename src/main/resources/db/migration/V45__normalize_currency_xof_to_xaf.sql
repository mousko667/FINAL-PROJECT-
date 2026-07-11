-- V45 — Normalize legacy XOF invoices to XAF.
--
-- The system operates in XAF (Franc CFA BEAC, Gabon / CEMAC). Early seed data
-- carried XOF (BCEAO / West-African CFA), which is out of scope and leaked into
-- exported reports (compliance PDF, Excel/CSV). This one-off data fix converts
-- the amount-preserving currency label; XAF and XOF share the same nominal value,
-- so only the ISO code changes. The column default is already 'XAF' (see prior
-- migrations), so no new rows can reintroduce XOF.
--
-- Idempotent: re-running affects zero rows once all invoices are XAF.

UPDATE invoices
SET currency = 'XAF'
WHERE currency = 'XOF';
