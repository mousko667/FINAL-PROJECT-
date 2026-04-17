-- V20__create_webhooks.sql
-- Webhooks and webhook deliveries for ERP integration
-- CRITICAL: webhook_deliveries is APPEND-ONLY — no UPDATE, no DELETE ever

CREATE TABLE webhooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    events VARCHAR(500) NOT NULL, -- comma-separated: INVOICE_SUBMITTED,INVOICE_VALIDATED,etc
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_webhook_url_per_user UNIQUE (url, created_by) -- no duplicate URLs per admin
);

CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    webhook_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL, -- INVOICE_SUBMITTED, INVOICE_VALIDATED, etc
    invoice_id UUID,
    payload TEXT NOT NULL, -- JSON payload sent
    request_headers TEXT, -- HMAC-SHA256 signature in X-OCT-Signature
    response_status INT,
    response_body TEXT, -- response from webhook endpoint (for debugging)
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMP,
    success BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (webhook_id) REFERENCES webhooks(id) ON DELETE CASCADE,
    FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE SET NULL
);

-- Indexes for efficient queries
CREATE INDEX idx_webhooks_created_by ON webhooks(created_by);
CREATE INDEX idx_webhooks_is_active ON webhooks(is_active);
CREATE INDEX idx_webhook_deliveries_webhook_id ON webhook_deliveries(webhook_id);
CREATE INDEX idx_webhook_deliveries_event_type ON webhook_deliveries(event_type);
CREATE INDEX idx_webhook_deliveries_success ON webhook_deliveries(success);
CREATE INDEX idx_webhook_deliveries_created_at ON webhook_deliveries(created_at DESC);
CREATE INDEX idx_webhook_deliveries_invoice_id ON webhook_deliveries(invoice_id);

-- Audit function to prevent UPDATE/DELETE on webhook_deliveries
CREATE OR REPLACE FUNCTION prevent_webhook_deliveries_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' OR TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'webhook_deliveries is append-only: % not permitted', TG_OP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_webhook_deliveries_append_only
BEFORE UPDATE OR DELETE ON webhook_deliveries
FOR EACH ROW
EXECUTE FUNCTION prevent_webhook_deliveries_modification();

COMMENT ON TABLE webhook_deliveries IS 'Append-only audit log of webhook delivery attempts. Never update or delete records here.';
