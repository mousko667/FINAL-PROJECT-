-- V2 — Suppliers (consolidated, with category)
-- Authoritative source for supplier data. Bank details are encrypted at rest (AES-GCM) by JPA.
-- Soft-delete only (deleted_at). Status lifecycle: PENDING_VERIFICATION -> ACTIVE -> SUSPENDED.

CREATE TABLE suppliers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name   VARCHAR(255) NOT NULL,
    tax_id         VARCHAR(100) NOT NULL UNIQUE,
    contact_email  VARCHAR(255) NOT NULL,
    contact_phone  VARCHAR(50),
    bank_details   TEXT,
    address        TEXT,
    status         VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    onboarded_by   UUID,
    onboarded_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ,
    category       VARCHAR(30)
);

CREATE INDEX idx_suppliers_category ON suppliers(category);
