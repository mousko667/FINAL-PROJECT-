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
