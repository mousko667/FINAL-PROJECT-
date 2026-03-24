-- V4__create_invoices.sql

CREATE TABLE invoices (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number     VARCHAR(20) NOT NULL UNIQUE,
    department_id        UUID NOT NULL REFERENCES departments(id),
    submitted_by         UUID NOT NULL REFERENCES users(id),
    supplier_name        VARCHAR(255) NOT NULL,
    supplier_email       VARCHAR(255) NOT NULL,
    supplier_tax_id      VARCHAR(100),
    supplier_bank_details TEXT,
    amount               NUMERIC(15,2) NOT NULL,
    currency             VARCHAR(3) NOT NULL DEFAULT 'XAF',
    issue_date           DATE NOT NULL,
    due_date             DATE NOT NULL,
    description          TEXT,
    status               VARCHAR(30) NOT NULL DEFAULT 'BROUILLON',
    version              INTEGER NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at           TIMESTAMPTZ
);
