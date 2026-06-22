-- V23 — System announcements (admin-published, severity-tagged, optional expiry).

CREATE TABLE announcements (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    severity   VARCHAR(20) NOT NULL DEFAULT 'INFO',
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_announcements_active ON announcements(active);
