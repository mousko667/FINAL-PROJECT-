-- V8 — Invoices (consolidated final shape).
-- Folds the original invoices table plus every later additive column:
--   supplier_id FK + nullable flat supplier fields (legacy compatibility),
--   purchase_order_id + matching_status (three-way matching),
--   data_sensitivity (PUBLIC / INTERNAL / CONFIDENTIAL, default INTERNAL).
-- supplier_bank_details holds AES-GCM ciphertext (encrypted at rest by JPA).
-- The legacy data-migration steps (back-filling suppliers from flat fields, nulling
-- plaintext bank details) are intentionally omitted: this baseline targets a fresh DB.

CREATE TABLE invoices (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_number      VARCHAR(20) NOT NULL UNIQUE,
    department_id         UUID NOT NULL REFERENCES departments(id),
    submitted_by          UUID NOT NULL REFERENCES users(id),
    supplier_name         VARCHAR(255),
    supplier_email        VARCHAR(255),
    supplier_tax_id       VARCHAR(100),
    supplier_bank_details TEXT,
    amount                NUMERIC(15,2) NOT NULL,
    currency              VARCHAR(3) NOT NULL DEFAULT 'XAF',
    issue_date            DATE NOT NULL,
    due_date              DATE NOT NULL,
    description           TEXT,
    status                VARCHAR(30) NOT NULL DEFAULT 'BROUILLON',
    version               INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at            TIMESTAMPTZ,
    supplier_id           UUID REFERENCES suppliers(id),
    purchase_order_id     UUID REFERENCES purchase_orders(id),
    matching_status       VARCHAR(20),
    data_sensitivity      VARCHAR(20) NOT NULL DEFAULT 'INTERNAL'
);

CREATE INDEX idx_invoices_status          ON invoices(status);
CREATE INDEX idx_invoices_department      ON invoices(department_id);
CREATE INDEX idx_invoices_created         ON invoices(created_at);
CREATE INDEX idx_invoices_status_created  ON invoices(status, created_at DESC);
CREATE INDEX idx_invoices_supplier_id     ON invoices(supplier_id);
CREATE INDEX idx_invoices_po_id           ON invoices(purchase_order_id);
CREATE INDEX idx_invoices_matching_status ON invoices(matching_status);
