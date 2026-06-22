-- V16 — Webhooks and their delivery log.
-- Only the SHA-256 hash of the secret is stored (secret_hash) — the raw secret is shown once
-- at registration and is not recoverable from the DB. webhook_deliveries is append-only (V37).

CREATE TABLE webhooks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    url         VARCHAR(1000) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    events      VARCHAR(500) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhooks_created_by ON webhooks(created_by);
CREATE INDEX idx_webhooks_is_active  ON webhooks(is_active);

CREATE TABLE webhook_deliveries (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id        UUID NOT NULL REFERENCES webhooks(id),
    event_type        VARCHAR(100) NOT NULL,
    payload           TEXT NOT NULL,
    response_status   INTEGER,
    attempt_count     INTEGER NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMPTZ,
    success           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_deliveries_webhook_id ON webhook_deliveries(webhook_id);
CREATE INDEX idx_webhook_deliveries_event_type ON webhook_deliveries(event_type);
CREATE INDEX idx_webhook_deliveries_created_at ON webhook_deliveries(created_at);
