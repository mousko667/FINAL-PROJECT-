-- V24 — Supplier relationship management: contracts, communications, and documents.

CREATE TABLE supplier_contracts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    reference   VARCHAR(100) NOT NULL,
    title       VARCHAR(255) NOT NULL,
    start_date  DATE,
    end_date    DATE,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes       VARCHAR(2000),
    created_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_contracts_supplier ON supplier_contracts(supplier_id);

CREATE TABLE supplier_communications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES suppliers(id) ON DELETE CASCADE,
    channel     VARCHAR(20) NOT NULL DEFAULT 'NOTE',
    subject     VARCHAR(255) NOT NULL,
    body        VARCHAR(2000),
    logged_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    logged_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_supplier_comms_supplier ON supplier_communications(supplier_id);

CREATE TABLE supplier_documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id       UUID NOT NULL REFERENCES suppliers(id),
    document_type     VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    minio_object_key  VARCHAR(500) NOT NULL,
    file_size_bytes   BIGINT NOT NULL,
    checksum_sha256   VARCHAR(64) NOT NULL,
    uploaded_by       UUID NOT NULL REFERENCES users(id),
    uploaded_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at        TIMESTAMPTZ
);
