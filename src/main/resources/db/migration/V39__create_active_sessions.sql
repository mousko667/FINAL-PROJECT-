CREATE TABLE active_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    refresh_token   VARCHAR(500) NOT NULL UNIQUE,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_active_sessions_user ON active_sessions(user_id) WHERE revoked = FALSE;
CREATE INDEX idx_active_sessions_expires ON active_sessions(expires_at) WHERE revoked = FALSE;
