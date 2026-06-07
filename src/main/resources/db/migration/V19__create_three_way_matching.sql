-- Phase 9D: Create Three-Way Matching Results and Config

CREATE TABLE three_way_matching_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    goods_receipt_note_id UUID,
    status VARCHAR(20) NOT NULL,
    discrepancy_notes TEXT,
    overridden_by UUID,
    override_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_twmr_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id),
    CONSTRAINT fk_twmr_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_twmr_grn FOREIGN KEY (goods_receipt_note_id) REFERENCES goods_receipt_notes(id),
    CONSTRAINT fk_twmr_overridden_by FOREIGN KEY (overridden_by) REFERENCES users(id),
    UNIQUE(invoice_id)
);

CREATE TABLE matching_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tolerance_percentage DECIMAL(5, 2) NOT NULL DEFAULT 2.00,
    tolerance_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    require_grn BOOLEAN NOT NULL DEFAULT TRUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mc_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

-- Indexes for performance
CREATE INDEX idx_twmr_invoice_id ON three_way_matching_results(invoice_id);
CREATE INDEX idx_twmr_po_id ON three_way_matching_results(purchase_order_id);
CREATE INDEX idx_twmr_grn_id ON three_way_matching_results(goods_receipt_note_id);
CREATE INDEX idx_twmr_status ON three_way_matching_results(status);

-- Insert default matching config (system admin user ID will be replaced in next migration)
INSERT INTO matching_config (tolerance_percentage, tolerance_amount, require_grn, updated_by)
VALUES (2.00, 0.00, true, (SELECT id FROM users WHERE username = 'admin' LIMIT 1));
