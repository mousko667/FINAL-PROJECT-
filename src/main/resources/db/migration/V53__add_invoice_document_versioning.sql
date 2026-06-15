-- M9: document versioning. Re-uploading a document with the same filename for the same invoice
-- creates a new version and marks the previous one superseded (history is preserved, never overwritten).
ALTER TABLE invoice_documents
    ADD COLUMN version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN superseded_by_document_id UUID REFERENCES invoice_documents (id) ON DELETE SET NULL;

CREATE INDEX idx_invoice_documents_invoice ON invoice_documents (invoice_id);
