CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    invoice_id UUID REFERENCES invoices(id),
    title_fr VARCHAR(255) NOT NULL,
    title_en VARCHAR(255) NOT NULL,
    message_fr TEXT NOT NULL,
    message_en TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Note: The index `idx_notifications_user_unread` is defined in V12__add_indexes.sql, 
-- but we can add it here if it's the standard practice. According to DATABASE.md, 
-- indexes are added in V12, so we just declare the table here.
