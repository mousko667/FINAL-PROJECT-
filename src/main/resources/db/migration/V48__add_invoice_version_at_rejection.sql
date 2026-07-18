-- N1-A (PROB-118): capture the optimistic-lock version at the moment an invoice is rejected so
-- that resubmission (REJETE -> SOUMIS) can require a real correction (version increment) since the
-- rejection. NULL means the invoice has never been rejected (or predates this column).
ALTER TABLE invoices ADD COLUMN version_at_rejection INTEGER;
