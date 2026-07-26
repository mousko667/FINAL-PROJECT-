-- V50: Seed a few ACTIVE suppliers (dev/test) so report/filter selectors are not near-empty.
--
-- Why: only one supplier was ACTIVE, so the supplier dropdowns on the reports page
-- (payment cycle, supplier performance) had a single option and looked empty. These names
-- already appear on seeded invoices (plain-text supplierName), so promoting them to real
-- ACTIVE supplier rows makes the selectors representative for demos and for verifying the
-- GET /suppliers/options endpoint (DAF-readable) added alongside this migration.
--
-- Data-only and idempotent: each row is inserted only when its stable tax_id is absent, so
-- re-runs and existing rows are left untouched. bank_details is left NULL (nullable column;
-- no encryption needed at seed time).
INSERT INTO suppliers (id, company_name, tax_id, contact_email, contact_phone, address, status, created_at, updated_at)
SELECT gen_random_uuid(), 'SETRAG SA', 'GA-2026-20001', 'contact@setrag.ga', '+241 011 79 00 00',
       'Owendo, Libreville, Gabon', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM suppliers WHERE tax_id = 'GA-2026-20001');

INSERT INTO suppliers (id, company_name, tax_id, contact_email, contact_phone, address, status, created_at, updated_at)
SELECT gen_random_uuid(), 'Gabon Net Services', 'GA-2026-20002', 'info@gabonnet.ga', '+241 011 44 55 66',
       'Zone portuaire d''Owendo, Libreville, Gabon', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM suppliers WHERE tax_id = 'GA-2026-20002');

INSERT INTO suppliers (id, company_name, tax_id, contact_email, contact_phone, address, status, created_at, updated_at)
SELECT gen_random_uuid(), 'Sogec BTP Gabon', 'GA-2026-20003', 'contact@sogec-btp.ga', '+241 011 22 33 44',
       'BP 6246, Libreville, Gabon', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM suppliers WHERE tax_id = 'GA-2026-20003');
