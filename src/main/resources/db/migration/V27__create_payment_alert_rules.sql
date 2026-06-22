-- V27 — Configurable payment due-date alert rules (N days before due).

CREATE TABLE payment_alert_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    days_before_due INTEGER NOT NULL,
    label           VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_payment_alert_days_nonneg CHECK (days_before_due >= 0)
);
