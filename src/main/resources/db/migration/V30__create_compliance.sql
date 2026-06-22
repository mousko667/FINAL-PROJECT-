-- V30 — Compliance & security meta-layer: incidents, regulatory checklist, deadline calendar,
-- backup status (singleton), and privacy-policy acceptances.

CREATE TABLE security_incidents (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(4000),
    severity    VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reported_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reported_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_security_incidents_status ON security_incidents(status);

CREATE TABLE compliance_checklist_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    framework  VARCHAR(30) NOT NULL,
    label      VARCHAR(500) NOT NULL,
    completed  BOOLEAN NOT NULL DEFAULT FALSE,
    notes      VARCHAR(2000),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE compliance_calendar (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    due_date    DATE NOT NULL,
    description VARCHAR(2000),
    completed   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_compliance_calendar_due ON compliance_calendar(due_date);

-- Singleton row (id is always 1).
CREATE TABLE backup_status (
    id             INTEGER PRIMARY KEY DEFAULT 1,
    last_backup_at TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    detail         VARCHAR(1000),
    CONSTRAINT backup_status_singleton CHECK (id = 1)
);

CREATE TABLE privacy_policy_acceptances (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    policy_version VARCHAR(40) NOT NULL,
    accepted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
