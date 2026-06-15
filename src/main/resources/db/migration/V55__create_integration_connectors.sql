-- M12: configurable integration connectors (ERP / accounting / banking / DMS).
-- Generic framework: each connector has a type, a target endpoint and a JSON-ish config blob.
-- A built-in MOCK type lets the system demonstrate connect/sync/health without a real external system.
CREATE TABLE integration_connectors (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(150) NOT NULL,
    type          VARCHAR(30) NOT NULL,                 -- ERP | ACCOUNTING | BANKING | DMS | MOCK
    endpoint      VARCHAR(500),
    config        VARCHAR(4000),
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    last_status   VARCHAR(20),                          -- UP | DOWN | UNKNOWN
    last_checked_at TIMESTAMPTZ,
    last_message  VARCHAR(1000),
    created_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_integration_connectors_enabled ON integration_connectors (enabled);
