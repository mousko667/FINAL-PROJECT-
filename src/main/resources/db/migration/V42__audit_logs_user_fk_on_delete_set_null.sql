-- audit_logs is append-only (V25): rows must never be deleted, even if the
-- referenced user is later removed. Without ON DELETE SET NULL, deleting a
-- user that has audit_logs entries violates the user_id foreign key.
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS fkjs4iimve3y0xssbtve5ysyef0;
ALTER TABLE audit_logs
    ADD CONSTRAINT fkjs4iimve3y0xssbtve5ysyef0
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
