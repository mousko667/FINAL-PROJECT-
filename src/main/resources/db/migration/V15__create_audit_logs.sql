-- V15 — Immutable audit trail. user_id FK is ON DELETE SET NULL so an audit row survives
-- deletion of its actor (append-only is additionally enforced by triggers in V37).

CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id) ON DELETE SET NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   VARCHAR(100) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    old_value   JSONB,
    new_value   JSONB,
    ip_address  VARCHAR(50),
    user_agent  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_type    ON audit_logs(entity_type, created_at DESC);
CREATE INDEX idx_audit_user    ON audit_logs(user_id, created_at DESC);
