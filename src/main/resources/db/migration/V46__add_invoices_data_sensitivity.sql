-- P11-15 (REQ-23 item 1): data-sensitivity classification on financial records.
-- Every invoice carries a sensitivity level (PUBLIC / INTERNAL / CONFIDENTIAL); existing rows
-- default to INTERNAL (all company invoice data is at least internal).
ALTER TABLE invoices
    ADD COLUMN data_sensitivity VARCHAR(20) NOT NULL DEFAULT 'INTERNAL';
