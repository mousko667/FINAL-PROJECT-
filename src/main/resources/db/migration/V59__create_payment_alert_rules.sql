-- B4 (M7): configurable payment due-date alert rules. Each rule fires an alert for invoices in
-- BON_A_PAYER whose due date falls N days ahead (J-N). Replaces the hard-coded 7-day threshold of
-- DeadlineReminderJob.sendPaymentDueAlerts. Multiple rules (e.g. J-7, J-3, J-1) can be active.
CREATE TABLE payment_alert_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    days_before_due INTEGER NOT NULL,
    label           VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_alert_days UNIQUE (days_before_due),
    CONSTRAINT chk_payment_alert_days_nonneg CHECK (days_before_due >= 0)
);

-- Seed the historical default (J-7) so existing behaviour is preserved out of the box.
INSERT INTO payment_alert_rules (days_before_due, label, active)
VALUES (7, 'Default 7-day reminder', TRUE);
