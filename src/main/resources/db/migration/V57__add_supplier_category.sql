-- B5 (M8): supplier segmentation by spend type. Nullable so existing suppliers stay valid;
-- values are constrained to the SupplierCategory enum (GOODS / SERVICES / WORKS / CONSULTING).
ALTER TABLE suppliers
    ADD COLUMN category VARCHAR(30);

CREATE INDEX idx_suppliers_category ON suppliers (category);
