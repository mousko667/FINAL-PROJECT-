ALTER TABLE invoices ADD COLUMN folder_id UUID REFERENCES archive_folders(id) ON DELETE SET NULL;

CREATE INDEX idx_invoices_folder ON invoices(folder_id) WHERE folder_id IS NOT NULL;
