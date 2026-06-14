-- P11-50 (REQ-16, partial scope): append-only access trail for invoice-document downloads.
-- Every time a pre-signed download URL is generated we record who accessed which document, when,
-- and from where. The table is append-only (no UPDATE/DELETE) — same guarantee as audit_logs (V25).
CREATE TABLE document_access_log (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID NOT NULL REFERENCES invoice_documents (id) ON DELETE CASCADE,
    invoice_id   UUID NOT NULL,
    accessed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    action       VARCHAR(50) NOT NULL DEFAULT 'DOWNLOAD',
    ip_address   VARCHAR(50),
    user_agent   TEXT,
    accessed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Common queries: per-document trail and per-invoice trail.
CREATE INDEX idx_document_access_log_document ON document_access_log (document_id);
CREATE INDEX idx_document_access_log_invoice ON document_access_log (invoice_id);

-- Append-only: reuse the prevent_append_only_mutation() function created in V25.
DROP TRIGGER IF EXISTS trg_document_access_log_append_only_update ON document_access_log;
CREATE TRIGGER trg_document_access_log_append_only_update
BEFORE UPDATE ON document_access_log
FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();

DROP TRIGGER IF EXISTS trg_document_access_log_append_only_delete ON document_access_log;
CREATE TRIGGER trg_document_access_log_append_only_delete
BEFORE DELETE ON document_access_log
FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation();
