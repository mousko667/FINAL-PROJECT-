-- V32 — Append-only enforcement for log tables: audit_logs, webhook_deliveries,
-- document_access_log. Any UPDATE or DELETE on these tables raises an exception.

CREATE OR REPLACE FUNCTION prevent_append_only_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only and cannot be modified or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_append_only_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();
CREATE TRIGGER trg_audit_logs_append_only_delete
    BEFORE DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();

CREATE TRIGGER trg_webhook_deliveries_append_only_update
    BEFORE UPDATE ON webhook_deliveries
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();
CREATE TRIGGER trg_webhook_deliveries_append_only_delete
    BEFORE DELETE ON webhook_deliveries
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();

CREATE TRIGGER trg_document_access_log_append_only_update
    BEFORE UPDATE ON document_access_log
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();
CREATE TRIGGER trg_document_access_log_append_only_delete
    BEFORE DELETE ON document_access_log
    FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();
