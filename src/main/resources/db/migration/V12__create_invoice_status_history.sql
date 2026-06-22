-- V12 — Invoice status history (full audit of every BAP state change).

CREATE TABLE invoice_status_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id    UUID NOT NULL REFERENCES invoices(id),
    from_status   VARCHAR(30) NOT NULL,
    to_status     VARCHAR(30) NOT NULL,
    changed_by    UUID NOT NULL REFERENCES users(id),
    change_reason TEXT,
    changed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_status_history_invoice ON invoice_status_history(invoice_id);
