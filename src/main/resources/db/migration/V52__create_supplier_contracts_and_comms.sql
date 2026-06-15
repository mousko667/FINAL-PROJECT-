-- M8: supplier contract/agreement tracking + communication log.
CREATE TABLE supplier_contracts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id   UUID NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    reference     VARCHAR(100) NOT NULL,
    title         VARCHAR(255) NOT NULL,
    start_date    DATE,
    end_date      DATE,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | EXPIRED | TERMINATED
    notes         VARCHAR(2000),
    created_by    UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_supplier_contracts_supplier ON supplier_contracts (supplier_id);

CREATE TABLE supplier_communications (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id   UUID NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    channel       VARCHAR(20) NOT NULL DEFAULT 'NOTE',      -- EMAIL | PHONE | MEETING | NOTE
    subject       VARCHAR(255) NOT NULL,
    body          VARCHAR(2000),
    logged_by     UUID REFERENCES users (id) ON DELETE SET NULL,
    logged_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_supplier_comms_supplier ON supplier_communications (supplier_id);
