-- Phase 9D: Create Goods Receipt Notes and Items

CREATE TABLE goods_receipt_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_number VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id UUID NOT NULL,
    received_by UUID NOT NULL,
    receipt_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_grn_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_grn_received_by FOREIGN KEY (received_by) REFERENCES users(id)
);

CREATE TABLE goods_receipt_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goods_receipt_note_id UUID NOT NULL,
    purchase_order_item_id UUID NOT NULL,
    received_quantity DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gri_grn FOREIGN KEY (goods_receipt_note_id) REFERENCES goods_receipt_notes(id) ON DELETE CASCADE,
    CONSTRAINT fk_gri_poi FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id)
);

-- Indexes for performance
CREATE INDEX idx_grn_po_id ON goods_receipt_notes(purchase_order_id);
CREATE INDEX idx_gri_grn_id ON goods_receipt_items(goods_receipt_note_id);
CREATE INDEX idx_gri_poi_id ON goods_receipt_items(purchase_order_item_id);
