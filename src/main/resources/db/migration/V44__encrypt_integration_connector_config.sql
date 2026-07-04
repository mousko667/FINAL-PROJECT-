-- V44 — Widen integration_connectors.config to TEXT so AES-GCM+Base64 ciphertext fits
-- (MAJEUR-13). The column is now encrypted at rest via EncryptionAttributeConverter on the
-- JPA entity (mirrors Supplier.bank_details). No existing rows are seeded, so no backfill/
-- re-encryption is required; any connector rows created before this change in a dev DB would
-- hold plaintext that can no longer be decrypted and should be recreated.
ALTER TABLE integration_connectors ALTER COLUMN config TYPE TEXT;
