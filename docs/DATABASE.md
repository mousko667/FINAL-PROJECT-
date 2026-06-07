# DATABASE — Schema Reference

---

## Migration Order

```
V1__create_users_roles.sql
V2__create_departments.sql
V3__seed_roles_and_admin.sql
V4__create_invoices.sql
V5__create_invoice_items.sql
V6__create_invoice_documents.sql
V7__create_approval_steps.sql
V8__create_invoice_status_history.sql
V9__create_notifications.sql
V10__create_payments.sql
V11__create_audit_logs.sql
V12__add_indexes.sql
...
V30__remove_webhook_secret_encrypted.sql
V31__fix_finance_approver_and_remove_auditeur.sql
V32__seed_test_users_all_roles.sql
V33__remove_phantom_roles.sql
V34__fix_users_is_active.sql
V35__encrypt_invoice_bank_details.sql
V39__create_active_sessions.sql
V40__create_approval_delegations.sql
```

---

## Tables

### users
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
username        VARCHAR(100) NOT NULL UNIQUE
email           VARCHAR(255) NOT NULL UNIQUE
password_hash   VARCHAR(255) NOT NULL        -- BCrypt strength 12
first_name      VARCHAR(100) NOT NULL
last_name       VARCHAR(100) NOT NULL
preferred_lang  VARCHAR(2) DEFAULT 'fr'      -- 'fr' or 'en'
is_active       BOOLEAN NOT NULL DEFAULT TRUE
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
deleted_at      TIMESTAMPTZ
```

### roles
```sql
id          UUID PRIMARY KEY DEFAULT gen_random_uuid()
name        VARCHAR(100) NOT NULL UNIQUE    -- ROLE_ASSISTANT_COMPTABLE, etc.
description VARCHAR(255)
created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### user_roles
```sql
user_id     UUID NOT NULL REFERENCES users(id)
role_id     UUID NOT NULL REFERENCES roles(id)
assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
assigned_by UUID REFERENCES users(id)
PRIMARY KEY (user_id, role_id)
```

### departments
```sql
id          UUID PRIMARY KEY DEFAULT gen_random_uuid()
code        VARCHAR(20) NOT NULL UNIQUE     -- DRH, INFO, INFRA, TECH, etc.
name_fr     VARCHAR(255) NOT NULL
name_en     VARCHAR(255) NOT NULL
requires_n2 BOOLEAN NOT NULL DEFAULT FALSE
n1_role     VARCHAR(100) NOT NULL           -- Spring role name
n2_role     VARCHAR(100)                    -- NULL if requires_n2 = false
is_active   BOOLEAN NOT NULL DEFAULT TRUE
created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### Seed data for departments (V2)
```sql
INSERT INTO departments (code, name_fr, name_en, requires_n2, n1_role, n2_role) VALUES
('DRH',   'Direction des Ressources Humaines', 'Human Resources Department', FALSE, 'ROLE_VALIDATEUR_N1_DRH',  NULL),
('DG',    'Direction Générale',                 'General Management',          FALSE, 'ROLE_VALIDATEUR_N1_DG',   NULL),
('FIN',   'Finance',                            'Finance',                     FALSE, 'ROLE_VALIDATEUR_N1_FIN',  NULL),
('INFO',  'Informatique',                       'Information Technology',      TRUE,  'ROLE_VALIDATEUR_N1_INFO', 'ROLE_VALIDATEUR_N2_INFO'),
('TERM',  'Terminal',                           'Terminal',                    FALSE, 'ROLE_VALIDATEUR_N1_TERM', NULL),
('COM',   'Communication & RSE',                'Communication & CSR',         FALSE, 'ROLE_VALIDATEUR_N1_COM',  NULL),
('QHSSE', 'QHSSE',                              'QHSSE',                       FALSE, 'ROLE_VALIDATEUR_N1_QHSSE',NULL),
('INFRA', 'Infrastructure',                     'Infrastructure',              TRUE,  'ROLE_VALIDATEUR_N1_INFRA','ROLE_VALIDATEUR_N2_INFRA'),
('TECH',  'Atelier / Direction Technique',      'Technical Department',        TRUE,  'ROLE_VALIDATEUR_N1_TECH', 'ROLE_VALIDATEUR_N2_TECH');
```

### invoices
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
reference_number    VARCHAR(20) NOT NULL UNIQUE    -- FAC-2026-00041
department_id       UUID NOT NULL REFERENCES departments(id)
submitted_by        UUID NOT NULL REFERENCES users(id)  -- always ASSISTANT_COMPTABLE
supplier_name       VARCHAR(255) NOT NULL
supplier_email      VARCHAR(255) NOT NULL
supplier_tax_id     VARCHAR(100)
supplier_bank_details TEXT                         -- AES-256 encrypted
amount              NUMERIC(15,2) NOT NULL
currency            VARCHAR(3) NOT NULL DEFAULT 'XAF'
issue_date          DATE NOT NULL
due_date            DATE NOT NULL
description         TEXT
status              VARCHAR(30) NOT NULL DEFAULT 'BROUILLON'
version             INTEGER NOT NULL DEFAULT 0     -- optimistic locking
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
deleted_at          TIMESTAMPTZ                    -- soft delete
```

### invoice_items
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
invoice_id      UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE
line_number     INTEGER NOT NULL
description     TEXT NOT NULL
quantity        NUMERIC(10,3) NOT NULL
unit_price      NUMERIC(15,2) NOT NULL
total_price     NUMERIC(15,2) NOT NULL              -- computed: qty × unit_price
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### invoice_documents
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
invoice_id          UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE
original_filename   VARCHAR(255) NOT NULL
minio_object_key    VARCHAR(500) NOT NULL UNIQUE
file_type           VARCHAR(100) NOT NULL            -- MIME type
file_size_bytes     BIGINT NOT NULL
checksum_sha256     VARCHAR(64) NOT NULL
uploaded_by         UUID NOT NULL REFERENCES users(id)
uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### approval_steps
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
invoice_id          UUID NOT NULL REFERENCES invoices(id)
step_order          INTEGER NOT NULL                -- 1, 2, or 3
step_name_fr        VARCHAR(255) NOT NULL
step_name_en        VARCHAR(255) NOT NULL
approver_id         UUID REFERENCES users(id)       -- NULL until assigned
department_code     VARCHAR(20) NOT NULL
status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING|APPROVED|REJECTED
comments            TEXT
rejection_reason    TEXT
deadline            TIMESTAMPTZ
action_at           TIMESTAMPTZ
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
UNIQUE (invoice_id, step_order)
```

### invoice_status_history
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
invoice_id      UUID NOT NULL REFERENCES invoices(id)
from_status     VARCHAR(30) NOT NULL
to_status       VARCHAR(30) NOT NULL
changed_by      UUID NOT NULL REFERENCES users(id)
change_reason   TEXT
changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### notifications
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
user_id         UUID NOT NULL REFERENCES users(id)
invoice_id      UUID REFERENCES invoices(id)
title_fr        VARCHAR(255) NOT NULL
title_en        VARCHAR(255) NOT NULL
message_fr      TEXT NOT NULL
message_en      TEXT NOT NULL
type            VARCHAR(50) NOT NULL  -- SUBMISSION|VALIDATION|REJECTION|APPROVAL|PAYMENT|DEADLINE
is_read         BOOLEAN NOT NULL DEFAULT FALSE
read_at         TIMESTAMPTZ
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### payments
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
invoice_id          UUID NOT NULL UNIQUE REFERENCES invoices(id)
amount              NUMERIC(15,2) NOT NULL
currency            VARCHAR(3) NOT NULL DEFAULT 'XAF'
payment_date        DATE NOT NULL
payment_method      VARCHAR(50) NOT NULL   -- VIREMENT|CHEQUE|ESPECES
payment_reference   VARCHAR(255) NOT NULL
notes               TEXT
processed_by        UUID NOT NULL REFERENCES users(id)
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### audit_logs
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
user_id         UUID REFERENCES users(id)   -- NULL for system actions
entity_type     VARCHAR(50) NOT NULL        -- INVOICE|USER|PAYMENT|etc.
entity_id       VARCHAR(100) NOT NULL       -- UUID as string
action          VARCHAR(100) NOT NULL       -- CREATE|SUBMIT|APPROVE|REJECT|etc.
old_value       JSONB
new_value       JSONB
ip_address      VARCHAR(50)
user_agent      TEXT
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- NO updated_at — append-only table, never modified
```

---

## Indexes (V12)

```sql
-- Performance indexes
CREATE INDEX idx_invoices_status         ON invoices(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_department_id  ON invoices(department_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_invoices_submitted_by   ON invoices(submitted_by);
CREATE INDEX idx_invoices_created_at     ON invoices(created_at DESC);
CREATE INDEX idx_invoices_reference      ON invoices(reference_number);

CREATE INDEX idx_approval_steps_invoice  ON approval_steps(invoice_id);
CREATE INDEX idx_approval_steps_approver ON approval_steps(approver_id) WHERE status = 'PENDING';
CREATE INDEX idx_approval_steps_deadline ON approval_steps(deadline) WHERE status = 'PENDING';

CREATE INDEX idx_notifications_user_unread ON notifications(user_id) WHERE is_read = FALSE;
CREATE INDEX idx_audit_logs_entity         ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_created_at     ON audit_logs(created_at DESC);
CREATE INDEX idx_status_history_invoice    ON invoice_status_history(invoice_id);
```

---

## Constraints & Rules

1. `audit_logs` — no UPDATE or DELETE ever. The repository must only have `save()` — no `delete()`, no `saveAndFlush()` with modifications
2. `invoices.deleted_at` — set to NOW() for soft delete; never remove the row
3. `invoice_items.total_price` — always computed as `quantity × unit_price` in the service layer before saving
4. `approval_steps` — `(invoice_id, step_order)` is UNIQUE — no two steps at the same level for the same invoice
5. `invoices.version` — managed by Hibernate `@Version`, never set manually


---

## New Tables (Phase 9)

### suppliers
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
company_name        VARCHAR(255) NOT NULL
tax_id              VARCHAR(100) NOT NULL UNIQUE
contact_email       VARCHAR(255) NOT NULL
contact_phone       VARCHAR(50)
bank_details        TEXT                         -- AES-256 encrypted
address             TEXT
status              VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION'
                    -- PENDING_VERIFICATION | ACTIVE | SUSPENDED
onboarded_by        UUID REFERENCES users(id)
onboarded_at        TIMESTAMPTZ
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
deleted_at          TIMESTAMPTZ
```

### supplier_documents
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
supplier_id         UUID NOT NULL REFERENCES suppliers(id)
document_type       VARCHAR(50) NOT NULL    -- TAX_CERTIFICATE | CONTRACT | OTHER
original_filename   VARCHAR(255) NOT NULL
minio_object_key    VARCHAR(500) NOT NULL UNIQUE
file_size_bytes     BIGINT NOT NULL
checksum_sha256     VARCHAR(64) NOT NULL
uploaded_by         UUID NOT NULL REFERENCES users(id)
uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
expires_at          TIMESTAMPTZ
```

### purchase_orders
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
po_number       VARCHAR(50) NOT NULL UNIQUE
supplier_id     UUID NOT NULL REFERENCES suppliers(id)
department_id   UUID NOT NULL REFERENCES departments(id)
total_amount    NUMERIC(15,2) NOT NULL
currency        VARCHAR(3) NOT NULL DEFAULT 'XAF'
status          VARCHAR(30) NOT NULL DEFAULT 'OPEN'  -- OPEN | CLOSED | CANCELLED
created_by      UUID NOT NULL REFERENCES users(id)
issue_date      DATE NOT NULL
expiry_date     DATE
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### purchase_order_items
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
po_id           UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE
line_number     INTEGER NOT NULL
description     TEXT NOT NULL
quantity        NUMERIC(10,3) NOT NULL
unit_price      NUMERIC(15,2) NOT NULL
total_price     NUMERIC(15,2) NOT NULL
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
UNIQUE (po_id, line_number)
```

### goods_receipt_notes
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
grn_number      VARCHAR(50) NOT NULL UNIQUE
po_id           UUID NOT NULL REFERENCES purchase_orders(id)
received_by     UUID NOT NULL REFERENCES users(id)
receipt_date    DATE NOT NULL
notes           TEXT
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### goods_receipt_items
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
grn_id              UUID NOT NULL REFERENCES goods_receipt_notes(id) ON DELETE CASCADE
po_item_id          UUID NOT NULL REFERENCES purchase_order_items(id)
received_quantity   NUMERIC(10,3) NOT NULL
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### three_way_matching_results
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
invoice_id          UUID NOT NULL REFERENCES invoices(id)
po_id               UUID REFERENCES purchase_orders(id)
grn_id              UUID REFERENCES goods_receipt_notes(id)
status              VARCHAR(20) NOT NULL    -- MATCHED | PARTIAL | MISMATCH | OVERRIDDEN
discrepancy_notes   TEXT
overridden_by       UUID REFERENCES users(id)
override_reason     TEXT
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- NO updated_at — append-only
```

### matching_config
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
tolerance_percent   NUMERIC(5,2) NOT NULL DEFAULT 2.00
tolerance_amount    NUMERIC(15,2) NOT NULL DEFAULT 5000.00
require_grn         BOOLEAN NOT NULL DEFAULT TRUE
updated_by          UUID REFERENCES users(id)
updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### mfa_secrets (added to users via V15 migration)
```sql
-- Columns added to users table:
mfa_enabled             BOOLEAN NOT NULL DEFAULT FALSE
mfa_secret              VARCHAR(64)          -- TOTP secret, encrypted at rest
mfa_verified            BOOLEAN NOT NULL DEFAULT FALSE
failed_login_attempts   INTEGER NOT NULL DEFAULT 0
locked_until            TIMESTAMPTZ
supplier_id             UUID REFERENCES suppliers(id)   -- NULL for staff users
```

### webhooks
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
name            VARCHAR(100) NOT NULL
url             VARCHAR(1000) NOT NULL
secret_hash     VARCHAR(64) NOT NULL        -- SHA-256 of the raw secret, for signature
events          VARCHAR(500) NOT NULL       -- comma-separated event names
is_active       BOOLEAN NOT NULL DEFAULT TRUE
created_by      UUID NOT NULL REFERENCES users(id)
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### webhook_deliveries
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
webhook_id          UUID NOT NULL REFERENCES webhooks(id)
event_type          VARCHAR(100) NOT NULL
payload             TEXT NOT NULL
response_status     INTEGER
attempt_count       INTEGER NOT NULL DEFAULT 0
last_attempted_at   TIMESTAMPTZ
success             BOOLEAN NOT NULL DEFAULT FALSE
created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
-- NO updated_at — append-only
```

### remittance_advice
```sql
id                  UUID PRIMARY KEY DEFAULT gen_random_uuid()
payment_id          UUID NOT NULL UNIQUE REFERENCES payments(id)
pdf_object_key      VARCHAR(500) NOT NULL
generated_by        UUID NOT NULL REFERENCES users(id)
generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

---

## Modified Tables (Phase 9)

### invoices (additions)
```sql
supplier_id         UUID REFERENCES suppliers(id)       -- nullable, FK to suppliers
purchase_order_id   UUID REFERENCES purchase_orders(id) -- nullable, for 3-way matching
matching_status     VARCHAR(20)                          -- MATCHED|PARTIAL|MISMATCH|OVERRIDDEN|NULL
```

## Migration Order (additions)

## Constraints & Rules (additions)
6. `three_way_matching_results` — append-only; never UPDATE or DELETE
7. `webhook_deliveries` — append-only; never UPDATE or DELETE
8. `supplier.bank_details` — always encrypted via `@Convert(EncryptionAttributeConverter)`
9. `users.mfa_secret` — always encrypted; never returned in any DTO
10. `invoices.matching_status` — set by `ThreeWayMatchingService`, never by controller directly

---

## New Tables (T6 — Sessions & Delegations)

### active_sessions (V39)
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
user_id         UUID NOT NULL REFERENCES users(id)
refresh_token   VARCHAR(500) NOT NULL UNIQUE
ip_address      VARCHAR(50)
user_agent      TEXT
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
expires_at      TIMESTAMPTZ NOT NULL
revoked         BOOLEAN NOT NULL DEFAULT FALSE
revoked_at      TIMESTAMPTZ

-- Indexes
CREATE INDEX idx_active_sessions_user    ON active_sessions(user_id) WHERE revoked = FALSE;
CREATE INDEX idx_active_sessions_expires ON active_sessions(expires_at) WHERE revoked = FALSE;
```

### approval_delegations (V40)
```sql
id              UUID PRIMARY KEY DEFAULT gen_random_uuid()
delegator_id    UUID NOT NULL REFERENCES users(id)
delegatee_id    UUID NOT NULL REFERENCES users(id)
department_code VARCHAR(20) NOT NULL
from_date       DATE NOT NULL
to_date         DATE NOT NULL
reason          TEXT
created_by      UUID NOT NULL REFERENCES users(id)
created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
revoked         BOOLEAN NOT NULL DEFAULT FALSE
revoked_at      TIMESTAMPTZ
CONSTRAINT chk_delegation_dates CHECK (to_date >= from_date)
CONSTRAINT chk_no_self_delegation CHECK (delegator_id <> delegatee_id)

-- Indexes
CREATE INDEX idx_delegations_delegatee ON approval_delegations(delegatee_id)
    WHERE revoked = FALSE AND to_date >= CURRENT_DATE;
CREATE INDEX idx_delegations_dept ON approval_delegations(department_code)
    WHERE revoked = FALSE AND to_date >= CURRENT_DATE;
```

## Constraints & Rules (T6 additions)
11. `active_sessions` — `revoked` must be set to TRUE on logout/token rotation; never delete rows
12. `approval_delegations` — `delegator_id <> delegatee_id` enforced by DB constraint; `to_date >= from_date` enforced by DB constraint