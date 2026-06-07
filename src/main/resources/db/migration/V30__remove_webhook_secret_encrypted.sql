-- Remove the recoverable secret copy from webhooks.
-- The SHA-256 hash in secret_hash is sufficient for HMAC verification.
-- Raw secret was returned once at registration and must not be re-derivable from DB.
ALTER TABLE webhooks DROP COLUMN IF EXISTS secret_encrypted;
