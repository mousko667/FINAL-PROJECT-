-- V25 — Saved report definitions (report builder: dataset + format + schedule).

CREATE TABLE report_definitions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    dataset     VARCHAR(40) NOT NULL,
    format      VARCHAR(10) NOT NULL DEFAULT 'CSV',
    frequency   VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    recipients  VARCHAR(2000),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_run_at TIMESTAMPTZ
);

CREATE INDEX idx_report_definitions_active_freq ON report_definitions(active, frequency);
