-- P11-40 (REQ-02): system-wide security policy, made real (was simulation-only in the UI).
-- Singleton-style table (one active row), versioned on update like matching_config.
CREATE TABLE security_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mfa_required BOOLEAN NOT NULL DEFAULT TRUE,
    session_timeout_minutes INTEGER NOT NULL DEFAULT 60,
    max_login_attempts INTEGER NOT NULL DEFAULT 5,
    min_password_length INTEGER NOT NULL DEFAULT 8,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sp_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE INDEX idx_security_policy_active ON security_policy(is_active);

-- Default policy row (matches the previously-hardcoded values).
INSERT INTO security_policy (mfa_required, session_timeout_minutes, max_login_attempts, min_password_length, updated_by)
VALUES (TRUE, 60, 5, 8, (SELECT id FROM users WHERE username = 'admin' LIMIT 1));
