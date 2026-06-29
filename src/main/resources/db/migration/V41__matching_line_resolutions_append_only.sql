-- PROB-081: make three_way_matching_line_resolutions append-only at the database level.
-- These rows are a financial audit trail (who accepted a matching discrepancy, why, when):
-- they must never be updated or deleted, even via direct SQL, to preserve non-repudiation.
-- Application-level immutability is also enforced via @org.hibernate.annotations.Immutable.

CREATE OR REPLACE FUNCTION reject_matching_resolution_mutation()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'three_way_matching_line_resolutions is append-only (PROB-081): % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_matching_resolution_append_only
    BEFORE UPDATE OR DELETE ON three_way_matching_line_resolutions
    FOR EACH ROW
EXECUTE FUNCTION reject_matching_resolution_mutation();
