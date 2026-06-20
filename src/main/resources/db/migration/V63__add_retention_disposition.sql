-- M10 #10 refinement: track disposition of documents past their retention horizon
-- so the compliance card only flags those still PENDING (not the whole expired backlog forever).
ALTER TABLE invoice_documents
    ADD COLUMN retention_disposition    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN retention_disposition_at TIMESTAMP,
    ADD COLUMN retention_disposition_by UUID REFERENCES users(id);
