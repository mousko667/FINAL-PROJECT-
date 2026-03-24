-- V6__create_invoice_documents.sql

CREATE TABLE invoice_documents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id         UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    original_filename  VARCHAR(255) NOT NULL,
    minio_object_key   VARCHAR(500) NOT NULL UNIQUE,
    file_type          VARCHAR(100) NOT NULL,
    file_size_bytes    BIGINT NOT NULL,
    checksum_sha256    VARCHAR(64) NOT NULL,
    uploaded_by        UUID NOT NULL REFERENCES users(id),
    uploaded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
