-- Phase 9B: Add supplier_id to users and MFA + login tracking columns

ALTER TABLE users 
ADD COLUMN supplier_id UUID REFERENCES suppliers(id),
ADD COLUMN mfa_enabled BOOLEAN DEFAULT FALSE,
ADD COLUMN mfa_secret VARCHAR(64),
ADD COLUMN mfa_verified BOOLEAN DEFAULT FALSE,
ADD COLUMN failed_login_attempts INT DEFAULT 0,
ADD COLUMN locked_until TIMESTAMPTZ,
ADD COLUMN email_verification_token VARCHAR(255),
ADD COLUMN email_verification_token_expiry TIMESTAMPTZ;

-- Add index on supplier_id
CREATE INDEX idx_users_supplier_id ON users(supplier_id);
-- Add index on email verification token for fast lookups
CREATE INDEX idx_users_email_verification_token ON users(email_verification_token);
