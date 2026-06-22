-- V33 — Enforce the 10-year minimum retention rule for financial records. These records may
-- be soft-deleted where the application allows it, but hard DELETE is blocked until the
-- configured legal retention horizon has elapsed.

CREATE OR REPLACE FUNCTION prevent_financial_delete_before_10_years()
RETURNS trigger AS $$
DECLARE
    record_timestamp timestamptz;
BEGIN
    IF TG_TABLE_NAME = 'invoice_documents' THEN
        record_timestamp := OLD.uploaded_at;
    ELSIF TG_TABLE_NAME = 'invoice_status_history' THEN
        record_timestamp := OLD.changed_at;
    ELSIF TG_TABLE_NAME = 'remittance_advice' THEN
        record_timestamp := COALESCE(OLD.created_at, OLD.generated_at);
    ELSE
        record_timestamp := OLD.created_at;
    END IF;

    IF record_timestamp IS NULL OR record_timestamp > (now() - interval '10 years') THEN
        RAISE EXCEPTION 'Financial records must be retained for at least 10 years';
    END IF;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_invoices_retention_delete
    BEFORE DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION prevent_financial_delete_before_10_years();

CREATE TRIGGER trg_invoice_documents_retention_delete
    BEFORE DELETE ON invoice_documents
    FOR EACH ROW EXECUTE FUNCTION prevent_financial_delete_before_10_years();

CREATE TRIGGER trg_invoice_status_history_retention_delete
    BEFORE DELETE ON invoice_status_history
    FOR EACH ROW EXECUTE FUNCTION prevent_financial_delete_before_10_years();

CREATE TRIGGER trg_payments_retention_delete
    BEFORE DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION prevent_financial_delete_before_10_years();

CREATE TRIGGER trg_remittance_advice_retention_delete
    BEFORE DELETE ON remittance_advice
    FOR EACH ROW EXECUTE FUNCTION prevent_financial_delete_before_10_years();
