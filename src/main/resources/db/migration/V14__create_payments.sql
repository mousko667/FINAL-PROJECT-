-- V14 — Payments and remittance advice.
-- payments.id has no DB default (UUID assigned by the application). Soft-delete via `deleted`.

CREATE TABLE payments (
    id             UUID PRIMARY KEY,
    invoice_id     UUID NOT NULL REFERENCES invoices(id),
    amount_paid    NUMERIC(15,2) NOT NULL,
    payment_date   TIMESTAMP NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    reference      VARCHAR(100),
    recorded_by    UUID REFERENCES users(id),
    deleted        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP NOT NULL,
    updated_at     TIMESTAMP
);

CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payment_created  ON payments(created_at DESC);

CREATE TABLE remittance_advice (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id     UUID NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    pdf_object_key VARCHAR(255) NOT NULL,
    generated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_by   UUID NOT NULL REFERENCES users(id),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_remittance_advice_payment_id   ON remittance_advice(payment_id);
CREATE INDEX idx_remittance_advice_generated_by ON remittance_advice(generated_by);
CREATE INDEX idx_remittance_advice_created_at   ON remittance_advice(created_at);
