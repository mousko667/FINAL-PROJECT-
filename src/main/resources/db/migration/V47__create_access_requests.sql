-- P11-17 (REQ-23 item 3): self-service access-request workflow.
-- A staff user can request a single additional role (with a reason). An ADMIN reviews the
-- pending queue and approves (role is added to the requester) or rejects (with an optional comment).
CREATE TABLE access_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_role  VARCHAR(100) NOT NULL,
    reason          VARCHAR(1000) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    review_comment  VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at     TIMESTAMPTZ
);

-- The admin review queue filters by status; the "my requests" view filters by requester.
CREATE INDEX idx_access_requests_status ON access_requests (status);
CREATE INDEX idx_access_requests_requester ON access_requests (requester_id);
