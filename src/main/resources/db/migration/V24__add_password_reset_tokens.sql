ALTER TABLE users
ADD COLUMN password_reset_token VARCHAR(255),
ADD COLUMN password_reset_token_expiry TIMESTAMPTZ;

CREATE INDEX idx_users_password_reset_token ON users(password_reset_token);
