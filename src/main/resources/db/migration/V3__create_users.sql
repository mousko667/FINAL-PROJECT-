-- V3 — Users (consolidated: identity + MFA + login tracking + email/reset tokens + staff profile)
-- Folds the original users table and every later additive column (MFA, supplier link,
-- verification/reset tokens, employee_id/department_id/approval_limit). mfa_secret is sized
-- for the AES-GCM-encrypted TOTP secret (~85+ chars), hence VARCHAR(255).

CREATE TABLE users (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username                        VARCHAR(100) NOT NULL UNIQUE,
    email                           VARCHAR(255) NOT NULL UNIQUE,
    password_hash                   VARCHAR(255) NOT NULL,
    first_name                      VARCHAR(100) NOT NULL,
    last_name                       VARCHAR(100) NOT NULL,
    preferred_lang                  VARCHAR(2) DEFAULT 'fr',
    is_active                       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at                      TIMESTAMPTZ,
    supplier_id                     UUID REFERENCES suppliers(id),
    mfa_enabled                     BOOLEAN DEFAULT FALSE,
    mfa_secret                      VARCHAR(255),
    mfa_verified                    BOOLEAN DEFAULT FALSE,
    failed_login_attempts           INTEGER DEFAULT 0,
    locked_until                    TIMESTAMPTZ,
    email_verification_token        VARCHAR(255),
    email_verification_token_expiry TIMESTAMPTZ,
    password_reset_token            VARCHAR(255),
    password_reset_token_expiry     TIMESTAMPTZ,
    employee_id                     VARCHAR(100),
    department_id                   UUID REFERENCES departments(id),
    approval_limit                  NUMERIC(15,2)
);

CREATE INDEX idx_users_supplier_id              ON users(supplier_id);
CREATE INDEX idx_users_department_id            ON users(department_id);
CREATE INDEX idx_users_employee_id              ON users(employee_id);
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
CREATE INDEX idx_users_password_reset_token     ON users(password_reset_token);

-- Deferred FK: suppliers.onboarded_by references users (suppliers was created first).
ALTER TABLE suppliers
    ADD CONSTRAINT suppliers_onboarded_by_fkey FOREIGN KEY (onboarded_by) REFERENCES users(id);
