-- V22 — Document access log (who downloaded/viewed which invoice document). Append-only (V37).

CREATE TABLE document_access_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES invoice_documents(id) ON DELETE CASCADE,
    invoice_id  UUID NOT NULL REFERENCES invoices(id),
    accessed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(50) NOT NULL DEFAULT 'DOWNLOAD',
    ip_address  VARCHAR(50),
    user_agent  TEXT,
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_document_access_log_document ON document_access_log(document_id);
CREATE INDEX idx_document_access_log_invoice  ON document_access_log(invoice_id);
