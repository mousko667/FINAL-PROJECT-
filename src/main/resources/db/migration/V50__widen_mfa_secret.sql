-- Fix: MFA setup failed with "value too long for type character varying(64)".
-- The TOTP secret is stored AES-GCM-encrypted (EncryptionAttributeConverter): the ciphertext is
-- "GCM:<base64 iv>:<base64 cipher+tag>", ~85+ chars for a 32-char base32 secret — well over 64.
-- Widen the column so MFA enrolment can persist the encrypted secret.
ALTER TABLE users
    ALTER COLUMN mfa_secret TYPE VARCHAR(255);
