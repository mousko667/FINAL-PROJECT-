-- V19: Create remittance_advice table for payment documentation
-- Stores PDF metadata for remittance advice documents generated after payments

CREATE TABLE remittance_advice (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id) ON DELETE CASCADE,
    pdf_object_key VARCHAR(255) NOT NULL, -- MinIO object key: remittance/{paymentId}/{timestamp}.pdf
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    generated_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_remittance_advice_payment_id ON remittance_advice(payment_id);
CREATE INDEX idx_remittance_advice_generated_by ON remittance_advice(generated_by);
CREATE INDEX idx_remittance_advice_created_at ON remittance_advice(created_at);

COMMENT ON TABLE remittance_advice IS 'Remittance advice documents generated for payments. One per payment (unique constraint on payment_id).';
COMMENT ON COLUMN remittance_advice.payment_id IS 'Foreign key to payment record. Unique - one remittance per payment.';
COMMENT ON COLUMN remittance_advice.pdf_object_key IS 'MinIO object key where PDF is stored. Format: remittance/{paymentId}/{timestamp}.pdf';
COMMENT ON COLUMN remittance_advice.generated_by IS 'User who triggered the remittance generation (typically system via scheduled job).';
