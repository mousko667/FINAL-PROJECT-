CREATE TABLE backup_audit_logs (
    id UUID PRIMARY KEY,
    operation VARCHAR(50) NOT NULL,
    filename VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    triggered_by VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_backup_audit_logs_created_at ON backup_audit_logs(created_at DESC);
