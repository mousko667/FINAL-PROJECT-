-- V61: Configurable SLA escalation rules (B1, M4 #11 / M6 #6)
CREATE TABLE escalation_rules (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hours_after_deadline INTEGER NOT NULL,
    label                VARCHAR(255),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by           UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_escalation_rules_active ON escalation_rules(active);
