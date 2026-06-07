# PRD — Système de Gestion des Factures Fournisseurs (OCT)

**Version:** 1.0  
**Status:** Approved  
**Last Updated:** 2026  

---

## 1. Problem Statement

Owendo Container Terminal (OCT) processes supplier invoices manually across 9 departments. The current process is paper-based: invoices arrive by email, are printed, physically circulated for signatures, and filed in folders. This causes:

- Lost or duplicated invoices
- No visibility on approval status
- Payment delays due to missing signatures
- No audit trail for compliance
- Manual data re-entry errors

---

## 2. Solution

A **web-based system** that digitizes the entire invoice lifecycle — from supplier invoice submission through validation, multi-level departmental approval, CFO payment authorisation, payment recording, and final archiving.

Suppliers are **external companies and individuals** who interact with OCT via a dedicated supplier portal. They can register, log in, submit invoices, track status, and receive notifications. Internal staff (AA, approvers, CFO, Admin) manage the full BAP workflow.

---

## 3. Users & Roles

There are exactly **six roles** in this system. No more, no less.

| Role (FR) | Role (EN) | Spring Role Code | Description |
|---|---|---|---|
| Fournisseur | Supplier | `ROLE_SUPPLIER` | External company/individual. Submits invoices via the supplier portal, tracks status, receives notifications. Account activated by the Accounting Assistant. MFA not required. |
| Assistant Comptable | Accounting Assistant | `ROLE_ASSISTANT_COMPTABLE` | Receives and validates invoices, initiates the BAP workflow, manages suppliers, records payments. MFA mandatory. |
| Validateur N1 | Level 1 Approver | `ROLE_VALIDATEUR_N1_{DEPT}` | Department head — first approval gate for their department. MFA mandatory. |
| Validateur N2 | Level 2 Approver | `ROLE_VALIDATEUR_N2_{DEPT}` | Director — second approval gate (only for IT, Infrastructure, Workshop). MFA mandatory. |
| DAF (Directeur Administratif et Financier) | CFO (Chief Financial Officer) | `ROLE_DAF` | Level 1 approver for Finance department invoices AND final payment authorisation (Bon à Payer) for ALL departments. Access to financial audit trail. MFA mandatory. |
| Administrateur | Administrator | `ROLE_ADMIN` | Manages users, roles, departments, system configuration, and system/security audit trail. Zero access to financial data. MFA mandatory. |

> **Note on staff accounts:** Internal staff accounts (all roles except Supplier) are created by the Administrator. There is no self-registration for staff. Suppliers may self-register and are then activated by the Accounting Assistant.

---

## 4. Core Modules

### Module 1 — Réception & Saisie des Factures (Invoice Reception)
- Accounting assistant enters invoice data from supplier email
- Attaches scanned PDF/image of the original invoice
- Assigns the invoice to the correct department
- System auto-generates reference number (FAC-{YEAR}-{NNNNN})

### Module 2 — Workflow de Validation BAP (Validation Workflow)
- Routes invoice through department-specific approval chain
- 1-level approval: DRH, DG, Finance, Terminal, Com & RSE, QHSSE
- 2-level approval: Informatique (RSI→DSI), Infrastructure (Resp.→Dir.), Atelier (Resp.→Dir.)
- Each approver can validate with comment or reject with mandatory reason
- Rejected invoices return to "Soumis" for correction and resubmission

### Module 3 — Suivi des Paiements (Payment Tracking)
- Records payment details once invoice is approved (BON_A_PAYER)
- Tracks payment date, method, and reference
- Generates payment confirmation

### Module 4 — Archivage Numérique (Digital Archiving)
- All approved and paid invoices archived automatically
- Full-text searchable by reference, supplier, amount, date, department
- Secure file storage in MinIO
- SHA-256 integrity check on all documents

### Module 5 — Audit & Conformité (Audit & Compliance)
- Immutable audit log of every action (who, what, when, from which IP) — append-only, no UPDATE or DELETE ever permitted
- Complete status history per invoice
- **Financial audit trail** (invoice submissions, validation results, approval/rejection decisions, payment changes) → accessible to **CFO (DAF) only**
- **System/security audit trail** (user logins, role changes, integration events, security incidents) → accessible to **Administrator only**
- Compliance reports exportable as PDF

### Module 6 — Sécurité & Contrôle d'Accès (Security & Access Control)
- JWT authentication
- Role-based access control per department
- AES-256 encryption for sensitive supplier financial data
- Session management and audit logging

### Module 7 — Reporting & Analytique (Reporting & Analytics)
- KPI dashboard: processing times, volumes, rejection rates
- Export to Excel and PDF
- Overdue invoice alerts

---

## 5. Invoice Status Lifecycle

The database stores statuses as **French enum values** (DB column values). English labels are for display only.

| French (DB value) | English (Display) | Description |
|---|---|---|
| `BROUILLON` | Draft | Created but not yet submitted |
| `SOUMIS` | Submitted | Submitted — pending AA validation and three-way matching |
| `EN_VALIDATION_N1` | Under Review — L1 | Assigned to Level 1 approver |
| `EN_VALIDATION_N2` | Under Review — L2 | Assigned to Level 2 approver (IT/Infrastructure/Workshop only) |
| `VALIDE` | Validated | All departmental approvals obtained |
| `BON_A_PAYER` | Authorised to Pay | CFO/DAF has issued final payment authorisation (applies to all departments) |
| `PAYE` | Paid | Payment recorded by Accounting Assistant |
| `ARCHIVE` | Archived | Final state — stored for compliance (automatic) |
| `REJETE` | Rejected | Rejected at any stage — reason mandatory — supplier may resubmit |

**Rejection points:** Validation (AA), Level 1 approval, Level 2 approval (IT/Infra/Workshop only), and CFO Bon à Payer. All four trigger supplier notification and allow resubmission.

---

## 6. Department → Approval Mapping

The Accounting Assistant (Assistant comptable) is the **initiator** for all departments. The DAF (CFO) issues the final **Bon à Payer** for all departments after departmental approvals are complete.

| Department (EN) | Department (FR) | Code | Level 1 Approver | Level 2 Approver |
|---|---|---|---|---|
| Human Resources | Direction des Ressources Humaines | `DRH` | HR Director (DRH) | — |
| General Management | Direction Générale | `DG` | General Manager (DG) | — |
| Finance | Finance | `FIN` | CFO (DAF) | — |
| Information Technology | Informatique | `INFO` | IT Manager (RSI) | CIO (DSI) |
| Terminal Operations | Terminal | `TERM` | Terminal Manager (DEX) | — |
| Communication & CSR | Communication & RSE | `COM` | Com. Manager (Resp. Com) | — |
| QHSSE | QHSSE | `QHSSE` | QHSSE Manager (Resp. QHSSE) | — |
| Infrastructure | Direction des Infrastructures | `INFRA` | Infra. Manager (Resp. INFRA) | Infra. Director (Directeur INFRA) |
| Workshop & Technical | Direction Technique | `TECH` | Workshop Manager (Resp. Atelier) | Technical Director (Directeur Technique) |

---

## 7. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Performance | Invoice list loads in < 2s for up to 10,000 records |
| Security | JWT expiry 24h, refresh 7 days, AES-256 for sensitive fields |
| Availability | System available during OCT business hours |
| Data Retention | Financial records kept minimum 10 years (soft delete only) |
| Languages | French (default) + English (switchable per user) |
| File Support | PDF, PNG, JPG, TIFF — max 10MB per file |
| Audit | Every state change logged immutably with actor + timestamp + IP |
| Accessibility | WCAG 2.1 AA compliance for the web interface |

---

## 8. Out of Scope

- Replacing or migrating OCT's existing ERP system
- Initiating or executing banking transactions (payment tracking only — the system does NOT initiate payments)
- Bulk migration of historical paper-based invoice records
- Native mobile applications (system is mobile-responsive web only)
- Electronic signature (future phase)
- Live integration testing with production banking or ERP systems during the project period
- Payroll or HR financial processes (supplier invoices only)
- AI or ML components beyond OCR-assisted data extraction
