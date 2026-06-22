-- V13 — In-app notifications (bilingual title/message, read tracking).

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    invoice_id UUID REFERENCES invoices(id),
    title_fr   VARCHAR(255) NOT NULL,
    title_en   VARCHAR(255) NOT NULL,
    message_fr TEXT NOT NULL,
    message_en TEXT NOT NULL,
    type       VARCHAR(50) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_unread ON notifications(user_id, is_read);
CREATE INDEX idx_notif_user   ON notifications(user_id, created_at DESC);
