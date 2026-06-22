-- V26 — Integration connectors (generic external-system connector registry + sync schedule).
-- Includes the optional scheduled-sync fields (interval/last sync status). A framework only —
-- no live external system is contacted by this project.

CREATE TABLE integration_connectors (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(150) NOT NULL,
    type                  VARCHAR(30) NOT NULL,
    endpoint              VARCHAR(500),
    config                VARCHAR(4000),
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    last_status           VARCHAR(20),
    last_checked_at       TIMESTAMPTZ,
    last_message          VARCHAR(1000),
    created_by            UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sync_interval_minutes INTEGER,
    last_sync_at          TIMESTAMPTZ,
    last_sync_status      VARCHAR(20),
    last_sync_message     VARCHAR(1000),
    CONSTRAINT chk_connector_sync_interval_positive
        CHECK (sync_interval_minutes IS NULL OR sync_interval_minutes > 0)
);

CREATE INDEX idx_integration_connectors_enabled ON integration_connectors(enabled);
