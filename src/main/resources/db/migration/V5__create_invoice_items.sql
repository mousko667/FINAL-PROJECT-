-- V5__create_invoice_items.sql

CREATE TABLE invoice_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id   UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    line_number  INTEGER NOT NULL,
    description  TEXT NOT NULL,
    quantity     NUMERIC(10,3) NOT NULL,
    unit_price   NUMERIC(15,2) NOT NULL,
    total_price  NUMERIC(15,2) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
