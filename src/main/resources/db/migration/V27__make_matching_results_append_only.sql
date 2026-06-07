ALTER TABLE three_way_matching_results
DROP CONSTRAINT IF EXISTS three_way_matching_results_invoice_id_key;

CREATE INDEX IF NOT EXISTS idx_twmr_invoice_created
ON three_way_matching_results(invoice_id, created_at DESC);
