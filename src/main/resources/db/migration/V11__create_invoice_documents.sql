-- V11 — Invoice documents (consolidated: versioning + retention disposition).
-- Stores MinIO object metadata + SHA-256 integrity checksum. version/superseded_by support
-- document versioning; retention_disposition (PENDING/RETAINED/PURGED) supports the 10-year
-- retention lifecycle. retention_disposition_at is a plain timestamp (no tz) per the entity.

CREATE TABLE invoice_documents (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id                UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    original_filename         VARCHAR(255) NOT NULL,
    minio_object_key          VARCHAR(500) NOT NULL UNIQUE,
    file_type                 VARCHAR(100) NOT NULL,
    file_size_bytes           BIGINT NOT NULL,
    checksum_sha256           VARCHAR(64) NOT NULL,
    uploaded_by               UUID NOT NULL REFERENCES users(id),
    uploaded_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                   INTEGER NOT NULL DEFAULT 1,
    superseded_by_document_id UUID REFERENCES invoice_documents(id) ON DELETE SET NULL,
    retention_disposition     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retention_disposition_at  TIMESTAMP,
    retention_disposition_by  UUID REFERENCES users(id)
);

CREATE INDEX idx_invoice_documents_invoice ON invoice_documents(invoice_id);
