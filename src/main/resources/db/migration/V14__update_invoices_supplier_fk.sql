-- V14__update_invoices_supplier_fk.sql

-- 1. Add supplier_id FK to invoices
ALTER TABLE invoices
ADD COLUMN IF NOT EXISTS supplier_id UUID REFERENCES suppliers(id);

-- 2. Make legacy flat fields nullable for backward compatibility
ALTER TABLE invoices ALTER COLUMN supplier_name DROP NOT NULL;
ALTER TABLE invoices ALTER COLUMN supplier_email DROP NOT NULL;

-- 3. Migrate existing flat supplier data into the new suppliers table
-- Group by supplier_name to avoid duplicates.
-- We assign a generated tax_id for those missing it since tax_id is NOT NULL UNIQUE.
INSERT INTO suppliers (company_name, tax_id, contact_email, bank_details, status, created_at, updated_at)
SELECT 
    supplier_name,
    COALESCE(MAX(supplier_tax_id), 'PENDING-' || md5(supplier_name || random()::text)),
    MAX(supplier_email),
    MAX(supplier_bank_details),
    'ACTIVE',
    NOW(),
    NOW()
FROM invoices
WHERE supplier_name IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM suppliers s WHERE s.company_name = invoices.supplier_name
  )
GROUP BY supplier_name;

-- 4. Set supplier_id on existing invoices
UPDATE invoices i
SET supplier_id = s.id
FROM suppliers s
WHERE i.supplier_name = s.company_name
  AND i.supplier_id IS NULL;
