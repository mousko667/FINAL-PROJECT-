-- V7 — Goods Receipt Notes (GRN) and their line items.

CREATE TABLE goods_receipt_notes (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_number        VARCHAR(50) NOT NULL UNIQUE,
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id),
    received_by       UUID NOT NULL REFERENCES users(id),
    receipt_date      DATE NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP
);

CREATE TABLE goods_receipt_items (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goods_receipt_note_id  UUID NOT NULL REFERENCES goods_receipt_notes(id) ON DELETE CASCADE,
    purchase_order_item_id UUID NOT NULL REFERENCES purchase_order_items(id),
    received_quantity      NUMERIC(10,2) NOT NULL,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_grn_po_id  ON goods_receipt_notes(purchase_order_id);
CREATE INDEX idx_gri_grn_id ON goods_receipt_items(goods_receipt_note_id);
CREATE INDEX idx_gri_poi_id ON goods_receipt_items(purchase_order_item_id);
