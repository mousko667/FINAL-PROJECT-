# WORKFLOW — Processus Bon à Payer (BAP) — OCT

> This document is the authoritative source for all business rules.
> When in doubt about workflow behavior, this file wins.

---

## 1. Overview

The "Bon à Payer" (BAP) is OCT's process for authorizing supplier invoice payments. It is a **multi-level, department-specific approval workflow**.

**Key facts:**
- Suppliers submit invoices through the **supplier portal** (they log in with their own credentials)
- Suppliers can also send invoices by email — in that case the **Assistant Comptable** manually enters them
- The **Assistant Comptable** is always the workflow initiator (validates, routes, records payment)
- Approval levels depend on the **department** the invoice belongs to
- The **DAF (CFO)** gives the final BAP authorisation for **all departments** — this is distinct from the departmental Level 1/Level 2 approvals
- Both **internal staff** and **suppliers** receive notifications at appropriate workflow stages

---

## 2. Complete Approval Chains by Department

### Single-Level Approval (Assistant Comptable → N1 → DAF)

| Department | N1 Approver | Role Code |
|---|---|---|
| Direction des Ressources Humaines (DRH) | Directeur DRH | VALIDATEUR_N1_DRH |
| Direction Générale (DG) | Directeur Général | VALIDATEUR_N1_DG |
| Finance (FIN) | DAF | VALIDATEUR_N1_FIN |
| Terminal (TERM) | Directeur d'Exploitation (DEX) | VALIDATEUR_N1_TERM |
| Communication & RSE (COM) | Responsable Communication | VALIDATEUR_N1_COM |
| QHSSE | Responsable QHSSE | VALIDATEUR_N1_QHSSE |

### Two-Level Approval (Assistant Comptable → N1 → N2 → DAF)

| Department | N1 Approver | N1 Code | N2 Approver | N2 Code |
|---|---|---|---|---|
| Informatique (INFO) | RSI | VALIDATEUR_N1_INFO | DSI | VALIDATEUR_N2_INFO |
| Infrastructure (INFRA) | Resp. Infrastructure | VALIDATEUR_N1_INFRA | Directeur Infrastructure | VALIDATEUR_N2_INFRA |
| Atelier / Direction Technique (TECH) | Resp. Atelier | VALIDATEUR_N1_TECH | Directeur Technique | VALIDATEUR_N2_TECH |

---

## 3. State Machine — All Transitions

```
[BROUILLON] ──submit──────────────────────────────→ [SOUMIS]
[SOUMIS] ────assign_reviewer──────────────────────→ [EN_VALIDATION_N1]
[EN_VALIDATION_N1] ──validate_n1 (2-level dept)──→ [EN_VALIDATION_N2]
[EN_VALIDATION_N1] ──validate_n1 (1-level dept)──→ [VALIDE]
[EN_VALIDATION_N2] ──validate_n2──────────────────→ [VALIDE]
[VALIDE] ────bon_a_payer (DAF approval)───────────→ [BON_A_PAYER]
[BON_A_PAYER] ──record_payment────────────────────→ [PAYE]
[PAYE] ──────archive (automatic)──────────────────→ [ARCHIVE]

Rejection (from any review state):
[EN_VALIDATION_N1] ──reject──→ [REJETE]
[EN_VALIDATION_N2] ──reject──→ [REJETE]
[VALIDE] ────────────reject──→ [REJETE]

Resubmission:
[REJETE] ──resubmit──→ [SOUMIS]
```

---

## 4. Transition Rules

### submit
- **From:** BROUILLON
- **To:** SOUMIS
- **Actor:** ASSISTANT_COMPTABLE
- **Guard:** Invoice has at least one document attached + all required fields filled
- **Side effects:** Notify all available N1 reviewers for the department

### assign_reviewer
- **From:** SOUMIS
- **To:** EN_VALIDATION_N1
- **Actor:** VALIDATEUR_N1 (self-assigns)
- **Guard:** User's role matches the department's N1 approver role
- **Side effects:** Log assignment + start deadline timer (3 business days)

### validate_n1
- **From:** EN_VALIDATION_N1
- **To:** EN_VALIDATION_N2 (if 2-level dept) OR VALIDE (if 1-level dept)
- **Actor:** VALIDATEUR_N1 (the assigned reviewer)
- **Guard:** Comment optional but allowed
- **Side effects:** If 2-level → notify N2 approvers; If 1-level → notify DAF

### validate_n2
- **From:** EN_VALIDATION_N2
- **To:** VALIDE
- **Actor:** VALIDATEUR_N2 (self-assigns like N1)
- **Guard:** User's role matches the department's N2 approver role
- **Side effects:** Notify DAF

### bon_a_payer
- **From:** VALIDE
- **To:** BON_A_PAYER
- **Actor:** DAF (DIRECTEUR_FINANCIER)
- **Guard:** All prior approvals recorded in approval_steps
- **Side effects:** Notify ASSISTANT_COMPTABLE to process payment

### record_payment
- **From:** BON_A_PAYER
- **To:** PAYE
- **Actor:** ASSISTANT_COMPTABLE
- **Guard:** Payment reference, date, and method provided
- **Side effects:** Auto-trigger archive transition

### archive (automatic)
- **From:** PAYE
- **To:** ARCHIVE
- **Actor:** System (triggered automatically after PAYE)
- **Guard:** None
- **Side effects:** None

### reject
- **From:** EN_VALIDATION_N1, EN_VALIDATION_N2, VALIDE
- **To:** REJETE
- **Actor:** VALIDATEUR_N1, VALIDATEUR_N2, or DAF
- **Guard:** Rejection reason is MANDATORY (minimum 10 characters)
- **Side effects:** Notify ASSISTANT_COMPTABLE

### resubmit
- **From:** REJETE
- **To:** SOUMIS
- **Actor:** ASSISTANT_COMPTABLE
- **Guard:** Invoice must have been updated since rejection (version incremented)
- **Side effects:** Notify N1 approvers for the department

---

## 5. Approval Steps Data Model

Each transition that requires human approval creates a record in `approval_steps`:

```
approval_steps {
  id            UUID PK
  invoice_id    UUID FK
  step_order    INT          -- 1 = N1, 2 = N2, 3 = DAF
  step_name     VARCHAR      -- e.g. "Validation N1 - Informatique"
  step_name_fr  VARCHAR      -- French label
  step_name_en  VARCHAR      -- English label
  approver_id   UUID FK      -- who acted
  department    VARCHAR      -- OCT department code
  status        ENUM         -- PENDING | APPROVED | REJECTED
  comments      TEXT
  rejection_reason TEXT
  deadline      TIMESTAMP
  action_at     TIMESTAMP
  created_at    TIMESTAMP
}
```

---

## 6. Department Config (stored in DB, not hardcoded)

The department → approval chain mapping must be stored in a `departments` table so it can be managed by the admin without code changes:

```
departments {
  id              UUID PK
  code            VARCHAR UNIQUE   -- DRH, INFO, INFRA, etc.
  name_fr         VARCHAR
  name_en         VARCHAR
  requires_n2     BOOLEAN          -- true for INFO, INFRA, TECH
  n1_role         VARCHAR          -- Spring Security role name
  n2_role         VARCHAR NULLABLE -- null if requires_n2 = false
  is_active       BOOLEAN
}
```

---

## 7. Notifications Matrix

Notifications flow to both internal staff and suppliers at appropriate stages.

### Internal Notifications

| Event | Notified Users | Channel |
|---|---|---|
| Invoice submitted (by supplier portal or AA) | N1 approvers of the department | In-app + Email |
| N1 validation done (2-level dept) | N2 approvers of the department | In-app + Email |
| N1 validation done (1-level dept) | DAF | In-app + Email |
| N2 validation done | DAF | In-app + Email |
| BON_A_PAYER issued | Assistant Comptable | In-app + Email |
| Invoice rejected (at any stage) | Assistant Comptable | In-app + Email |
| Deadline approaching (24h) | Assigned reviewer | Email only |
| Invoice overdue | DAF + Admin | In-app + Email |

### Supplier Notifications

| Event | Notified Users | Channel |
|---|---|---|
| Invoice submission confirmed | Supplier | Email + In-app (supplier portal) |
| Invoice rejected (validation, L1, L2, or DAF) | Supplier | Email + In-app |
| Invoice approved (BON_A_PAYER issued) | Supplier | Email + In-app |
| Payment processed (PAYE) | Supplier | Email + In-app |
| Remittance advice available | Supplier | Email (with download link) |

---

## 8. Business Rules Summary

1. An invoice cannot be submitted without at least one attached document
2. A rejection reason is mandatory — minimum 10 characters
3. An approver cannot approve their own submitted invoice
4. Resubmission requires the invoice to have been modified (version > version at rejection)
5. The DAF approves ALL invoices regardless of department (final gate)
6. Only the ASSISTANT_COMPTABLE can create and submit invoices
7. Archiving happens automatically — no manual archive action
8. Financial records are never hard-deleted — soft delete only
9. All monetary amounts stored in XAF (Central African Franc) by default
10. Reference number format: `FAC-{YYYY}-{NNNNN}` — resets each year

## 9. Supplier Onboarding Workflow

**Key rules:**
- Supplier cannot log in until status is `ACTIVE`
- Supplier bank details are AES-256 encrypted at rest
- The **Accounting Assistant** (`ROLE_ASSISTANT_COMPTABLE`) manages the full supplier lifecycle: initiates onboarding, verifies documents, and activates the supplier account (`PENDING_VERIFICATION → ACTIVE`)
- The Administrator (`ROLE_ADMIN`) may also activate/suspend suppliers
- Suspension is soft — supplier data is never deleted

---

## 10. Supplier Invoice Submission Flow

When a supplier submits their own invoice via the portal:
- The invoice `submitted_by` is set to the supplier's user account
- The invoice `supplier_id` is automatically set from the JWT claim
- The normal BAP workflow (BROUILLON → SOUMIS → ...) applies identically
- The supplier can only view their own invoices — never other suppliers' data

---

## 11. Three-Way Matching Flow

**Tolerance rules** (stored in `matching_config`, admin-configurable):
- Default: 2% or 5,000 XAF, whichever is greater
- If `require_grn = true`, invoice cannot match without a GRN linked to the PO
- Override is mandatory documented and logged in audit trail

---

## 12. MFA Login Flow

**Business rules:**
- `pre_auth_token` expires in 5 minutes
- Full JWT not issued until OTP confirmed
- The following roles MUST complete MFA setup before first access to protected resources:
  `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF`, `ROLE_VALIDATEUR_N1_*`, `ROLE_VALIDATEUR_N2_*`, `ROLE_ADMIN`
- `ROLE_SUPPLIER` is the **only role exempt from MFA**