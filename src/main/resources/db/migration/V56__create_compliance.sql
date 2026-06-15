-- M14: compliance & security extras — incidents, SOX/IFRS checklist, compliance calendar,
-- privacy-policy acceptance, backup status.

CREATE TABLE security_incidents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    description  VARCHAR(4000),
    severity     VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',  -- LOW | MEDIUM | HIGH | CRITICAL
    status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',    -- OPEN | INVESTIGATING | RESOLVED | CLOSED
    reported_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    reported_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ
);
CREATE INDEX idx_security_incidents_status ON security_incidents (status);

CREATE TABLE compliance_checklist_items (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    framework    VARCHAR(30) NOT NULL,                   -- SOX | IFRS | LOCAL
    label        VARCHAR(500) NOT NULL,
    completed    BOOLEAN NOT NULL DEFAULT FALSE,
    notes        VARCHAR(2000),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE compliance_calendar (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    due_date     DATE NOT NULL,
    description  VARCHAR(2000),
    completed    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_compliance_calendar_due ON compliance_calendar (due_date);

CREATE TABLE privacy_policy_acceptances (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    policy_version VARCHAR(40) NOT NULL,
    accepted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, policy_version)
);

-- Backup status: a single-row table the backup process (or an admin) updates.
CREATE TABLE backup_status (
    id            INTEGER PRIMARY KEY DEFAULT 1,
    last_backup_at TIMESTAMPTZ,
    status        VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN', -- OK | FAILED | UNKNOWN
    detail        VARCHAR(1000),
    CONSTRAINT backup_status_singleton CHECK (id = 1)
);
INSERT INTO backup_status (id, status, detail) VALUES (1, 'UNKNOWN', 'No backup recorded yet.');
