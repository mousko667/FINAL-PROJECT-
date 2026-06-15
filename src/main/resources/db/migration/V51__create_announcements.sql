-- M2: system announcements shown on dashboards. Admin creates them; all authenticated users read
-- the currently-active ones (active flag + optional expiry).
CREATE TABLE announcements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(200) NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    severity    VARCHAR(20) NOT NULL DEFAULT 'INFO',   -- INFO | WARNING | CRITICAL
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ
);

CREATE INDEX idx_announcements_active ON announcements (active);
