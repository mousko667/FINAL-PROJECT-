-- V12__add_indexes.sql
-- Database indexes for performance optimization (Phase 8)
-- Adding supplementary indexes to existing tables

-- Additional indexes on invoices table
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_department ON invoices(department_id);
CREATE INDEX IF NOT EXISTS idx_invoices_created ON invoices(created_at);
CREATE INDEX IF NOT EXISTS idx_invoices_status_created ON invoices(status, created_at DESC);

-- Additional indexes on audit_logs table
CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_type ON audit_logs(entity_type, created_at DESC);

-- Additional indexes on approval_steps table
CREATE INDEX IF NOT EXISTS idx_approval_dept ON approval_steps(department_code);

-- Additional indexes on notifications table
CREATE INDEX IF NOT EXISTS idx_notif_user ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notif_unread ON notifications(user_id, is_read);

-- Additional indexes on payments table
CREATE INDEX IF NOT EXISTS idx_payment_created ON payments(created_at DESC);


