-- M11: saved report definitions for the custom report builder + scheduled distribution.
CREATE TABLE report_definitions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(150) NOT NULL,
    dataset       VARCHAR(40) NOT NULL,                 -- INVOICES | SUPPLIERS | AUDIT | BUDGET
    format        VARCHAR(10) NOT NULL DEFAULT 'CSV',   -- CSV | EXCEL | PDF
    frequency     VARCHAR(20) NOT NULL DEFAULT 'MANUAL',-- MANUAL | DAILY | WEEKLY | MONTHLY
    recipients    VARCHAR(2000),                        -- comma-separated emails for distribution
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_run_at   TIMESTAMPTZ
);
CREATE INDEX idx_report_definitions_active_freq ON report_definitions (active, frequency);
