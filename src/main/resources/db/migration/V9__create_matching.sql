-- V8 — Three-way matching results (append-only) and the matching tolerance config.
-- three_way_matching_results carries no UNIQUE(invoice_id): an invoice may be re-matched,
-- and every attempt is kept (append-only is enforced by app logic; results are never
-- overwritten — overrides are recorded as new rows / override fields).

CREATE TABLE three_way_matching_results (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id            UUID NOT NULL REFERENCES invoices(id),
    purchase_order_id     UUID NOT NULL REFERENCES purchase_orders(id),
    goods_receipt_note_id UUID REFERENCES goods_receipt_notes(id),
    status                VARCHAR(20) NOT NULL,
    discrepancy_notes     TEXT,
    overridden_by         UUID REFERENCES users(id),
    override_reason       VARCHAR(500),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_twmr_invoice_id      ON three_way_matching_results(invoice_id);
CREATE INDEX idx_twmr_invoice_created ON three_way_matching_results(invoice_id, created_at DESC);
CREATE INDEX idx_twmr_po_id           ON three_way_matching_results(purchase_order_id);
CREATE INDEX idx_twmr_grn_id          ON three_way_matching_results(goods_receipt_note_id);
CREATE INDEX idx_twmr_status          ON three_way_matching_results(status);

CREATE TABLE matching_config (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tolerance_percentage NUMERIC(5,2) NOT NULL DEFAULT 2.00,
    tolerance_amount     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    require_grn          BOOLEAN NOT NULL DEFAULT TRUE,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by           UUID NOT NULL REFERENCES users(id),
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
