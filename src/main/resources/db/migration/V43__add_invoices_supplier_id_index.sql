-- invoices.supplier_id (added in V14) has no index, despite being used as a
-- filter in InvoiceRepository (supplier-portal dashboard and related queries).
CREATE INDEX IF NOT EXISTS idx_invoices_supplier_id ON invoices(supplier_id);
