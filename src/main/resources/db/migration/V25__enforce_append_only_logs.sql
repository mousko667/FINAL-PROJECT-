CREATE OR REPLACE FUNCTION prevent_append_only_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only and cannot be modified or deleted', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_logs_append_only_update ON audit_logs;
CREATE TRIGGER trg_audit_logs_append_only_update
BEFORE UPDATE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();

DROP TRIGGER IF EXISTS trg_audit_logs_append_only_delete ON audit_logs;
CREATE TRIGGER trg_audit_logs_append_only_delete
BEFORE DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();

DROP TRIGGER IF EXISTS trg_webhook_deliveries_append_only_update ON webhook_deliveries;
CREATE TRIGGER trg_webhook_deliveries_append_only_update
BEFORE UPDATE ON webhook_deliveries
FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();

DROP TRIGGER IF EXISTS trg_webhook_deliveries_append_only_delete ON webhook_deliveries;
CREATE TRIGGER trg_webhook_deliveries_append_only_delete
BEFORE DELETE ON webhook_deliveries
FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();
