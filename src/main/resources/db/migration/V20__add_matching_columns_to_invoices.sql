-- Phase 9D: Add Three-Way Matching columns to invoices

ALTER TABLE invoices ADD COLUMN purchase_order_id UUID REFERENCES purchase_orders(id);
ALTER TABLE invoices ADD COLUMN matching_status VARCHAR(20);

CREATE INDEX idx_invoices_po_id ON invoices(purchase_order_id);
CREATE INDEX idx_invoices_matching_status ON invoices(matching_status);
