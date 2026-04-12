-- V13__create_suppliers.sql

CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(100) NOT NULL UNIQUE,
    contact_email VARCHAR(255) NOT NULL,
    contact_phone VARCHAR(50),
    bank_details TEXT,
    address TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    onboarded_by UUID REFERENCES users(id),
    onboarded_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS supplier_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    document_type VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    minio_object_key VARCHAR(500) NOT NULL UNIQUE,
    file_size_bytes BIGINT NOT NULL,
    checksum_sha256 VARCHAR(64) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users(id),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);
