-- V12__add_indexes.sql
-- Database indexes for performance optimization (Phase 8)
-- Per docs/DATABASE.md, use partial indexes with WHERE deleted_at IS NULL where applicable

-- ── Invoices Table Indexes ──────────────────────────────────
-- Index on status for filtering by invoice status
CREATE INDEX IF NOT EXISTS idx_invoices_status 
ON invoices(status) WHERE deleted_at IS NULL;

-- Index on department_id for department-scoped queries
CREATE INDEX IF NOT EXISTS idx_invoices_department_id 
ON invoices(department_id) WHERE deleted_at IS NULL;

-- Index on created_at for sorting and date range queries
CREATE INDEX IF NOT EXISTS idx_invoices_created_at 
ON invoices(created_at) WHERE deleted_at IS NULL;

-- Composite index for common queries (status + created_at)
CREATE INDEX IF NOT EXISTS idx_invoices_status_created_at 
ON invoices(status, created_at DESC) WHERE deleted_at IS NULL;

-- ── Audit Logs Table Indexes ───────────────────────────────
-- Index on created_at for audit log retrieval and reporting
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at 
ON audit_logs(created_at DESC);

-- Index on user_id for user-scoped audit trails
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id 
ON audit_logs(user_id, created_at DESC);

-- Index on entity_type for filtering audit trails
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type 
ON audit_logs(entity_type, created_at DESC);

-- ── Approval Steps Table Indexes ────────────────────────────
-- Index on invoice_id for step retrieval
CREATE INDEX IF NOT EXISTS idx_approval_steps_invoice_id 
ON approval_steps(invoice_id);

-- Index on reviewer_id for user-scoped approval tasks
CREATE INDEX IF NOT EXISTS idx_approval_steps_reviewer_id 
ON approval_steps(reviewer_id) WHERE status != 'COMPLETED';

-- ── Invoice Status History Table Indexes ────────────────────
-- Index on invoice_id for timeline queries
CREATE INDEX IF NOT EXISTS idx_invoice_status_history_invoice_id 
ON invoice_status_history(invoice_id);

-- Index on created_at for reporting
CREATE INDEX IF NOT EXISTS idx_invoice_status_history_created_at 
ON invoice_status_history(created_at DESC);

-- ── Notifications Table Indexes ────────────────────────────
-- Index on user_id for user-scoped notification retrieval
CREATE INDEX IF NOT EXISTS idx_notifications_user_id 
ON notifications(user_id, created_at DESC);

-- Index on is_read for filtering unread notifications
CREATE INDEX IF NOT EXISTS idx_notifications_is_read 
ON notifications(user_id, is_read) WHERE is_read = false;

-- ── Payments Table Indexes ────────────────────────────────
-- Index on invoice_id for payment lookup
CREATE INDEX IF NOT EXISTS idx_payments_invoice_id 
ON payments(invoice_id);

-- Index on created_at for reporting
CREATE INDEX IF NOT EXISTS idx_payments_created_at 
ON payments(created_at DESC);
