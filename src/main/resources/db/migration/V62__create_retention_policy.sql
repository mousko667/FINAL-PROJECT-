-- V62: Singleton document-retention policy (B2, M9 #7 / M14 #6)
-- Replaces the hard-coded app.retention.years value with a DB-backed, ADMIN-editable policy
-- read by DocumentRetentionJob. A single row; seeded with the previous default (10 years).
CREATE TABLE retention_policy (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    retention_years     INTEGER NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    last_sweep_at       TIMESTAMPTZ,
    last_flagged_count  INTEGER,
    updated_by          UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed the singleton row with the previous hard-coded default.
INSERT INTO retention_policy (retention_years, active) VALUES (10, TRUE);
