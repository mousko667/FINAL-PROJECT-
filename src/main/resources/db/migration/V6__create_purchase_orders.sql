-- V6 — Purchase orders and their line items (created before invoices for FK ordering).

CREATE TABLE purchase_orders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_number    VARCHAR(50) NOT NULL UNIQUE,
    supplier_id  UUID NOT NULL REFERENCES suppliers(id),
    total_amount NUMERIC(15,2) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by   UUID NOT NULL REFERENCES users(id),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TIMESTAMP
);

CREATE TABLE purchase_order_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    item_description  VARCHAR(255) NOT NULL,
    quantity          NUMERIC(10,2) NOT NULL,
    unit_price        NUMERIC(15,2) NOT NULL,
    line_total        NUMERIC(15,2) NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_po_status     ON purchase_orders(status);
CREATE INDEX idx_po_supplier_id ON purchase_orders(supplier_id);
CREATE INDEX idx_poi_po_id     ON purchase_order_items(purchase_order_id);
