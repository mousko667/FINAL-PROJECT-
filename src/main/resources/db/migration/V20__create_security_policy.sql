-- V20 — Configurable security policy (singleton-style). updated_by is nullable because the
-- startup-seeded default policy has no human author; the admin UI sets it on save.

CREATE TABLE security_policy (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mfa_required            BOOLEAN NOT NULL DEFAULT TRUE,
    session_timeout_minutes INTEGER NOT NULL DEFAULT 60,
    max_login_attempts      INTEGER NOT NULL DEFAULT 5,
    min_password_length     INTEGER NOT NULL DEFAULT 8,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by              UUID REFERENCES users(id),
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_security_policy_active ON security_policy(is_active);
