CREATE TABLE approval_delegations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delegator_id    UUID NOT NULL REFERENCES users(id),
    delegatee_id    UUID NOT NULL REFERENCES users(id),
    department_code VARCHAR(20) NOT NULL,
    from_date       DATE NOT NULL,
    to_date         DATE NOT NULL,
    reason          TEXT,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT chk_delegation_dates CHECK (to_date >= from_date),
    CONSTRAINT chk_no_self_delegation CHECK (delegator_id <> delegatee_id)
);

CREATE INDEX idx_delegations_delegatee ON approval_delegations(delegatee_id, to_date)
    WHERE revoked = FALSE;
CREATE INDEX idx_delegations_dept ON approval_delegations(department_code, to_date)
    WHERE revoked = FALSE;
