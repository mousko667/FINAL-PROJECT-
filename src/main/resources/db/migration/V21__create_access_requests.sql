-- V21 — Self-service access (role-elevation) requests, reviewed by an admin.

CREATE TABLE access_requests (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_role VARCHAR(100) NOT NULL,
    reason         VARCHAR(1000) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by    UUID REFERENCES users(id) ON DELETE SET NULL,
    review_comment VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at    TIMESTAMPTZ
);

CREATE INDEX idx_access_requests_requester ON access_requests(requester_id);
CREATE INDEX idx_access_requests_status    ON access_requests(status);
