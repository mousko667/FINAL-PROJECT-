-- V17 — Approval steps (per-invoice BAP workflow steps with deadlines and decisions).

CREATE TABLE approval_steps (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       UUID NOT NULL REFERENCES invoices(id),
    step_order       INTEGER NOT NULL,
    step_name_fr     VARCHAR(255) NOT NULL,
    step_name_en     VARCHAR(255) NOT NULL,
    approver_id      UUID REFERENCES users(id),
    department_code  VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comments         TEXT,
    rejection_reason TEXT,
    deadline         TIMESTAMPTZ,
    action_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_steps_invoice  ON approval_steps(invoice_id);
CREATE INDEX idx_approval_dept           ON approval_steps(department_code);
CREATE INDEX idx_approval_steps_approver ON approval_steps(approver_id) WHERE status = 'PENDING';
CREATE INDEX idx_approval_steps_deadline ON approval_steps(deadline)    WHERE status = 'PENDING';
