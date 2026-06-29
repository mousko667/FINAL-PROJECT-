CREATE TABLE three_way_matching_line_resolutions (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    po_line_id UUID NOT NULL REFERENCES purchase_order_items(id),
    status VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    resolved_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invoice_po_line UNIQUE (invoice_id, po_line_id)
);
