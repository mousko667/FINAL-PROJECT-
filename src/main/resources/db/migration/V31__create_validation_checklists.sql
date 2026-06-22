-- V31 — Validation checklists: templates (+ items) and per-invoice responses (+ items).

CREATE TABLE checklist_templates (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    department_id UUID REFERENCES departments(id),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_by    UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checklist_templates_active     ON checklist_templates(active);
CREATE INDEX idx_checklist_templates_department ON checklist_templates(department_id);

CREATE TABLE checklist_template_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   UUID NOT NULL REFERENCES checklist_templates(id) ON DELETE CASCADE,
    label         VARCHAR(500) NOT NULL,
    required      BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_checklist_template_items_template ON checklist_template_items(template_id);

CREATE TABLE checklist_responses (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id   UUID NOT NULL REFERENCES invoices(id),
    template_id  UUID NOT NULL REFERENCES checklist_templates(id),
    responded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    responded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_checklist_responses_invoice ON checklist_responses(invoice_id);

CREATE TABLE checklist_response_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    response_id      UUID NOT NULL REFERENCES checklist_responses(id) ON DELETE CASCADE,
    template_item_id UUID NOT NULL REFERENCES checklist_template_items(id),
    checked          BOOLEAN NOT NULL DEFAULT FALSE,
    note             VARCHAR(1000)
);

CREATE INDEX idx_checklist_response_items_response ON checklist_response_items(response_id);
