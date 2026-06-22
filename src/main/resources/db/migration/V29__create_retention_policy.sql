-- V29 — Document retention policy (singleton: retention years + sweep tracking).

CREATE TABLE retention_policy (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    retention_years    INTEGER NOT NULL,
    active             BOOLEAN NOT NULL DEFAULT TRUE,
    last_sweep_at      TIMESTAMPTZ,
    last_flagged_count INTEGER,
    updated_by         UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
