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

A **web-based internal system** that digitizes the entire invoice lifecycle from the moment the accounting assistant receives a supplier email to the final archiving after payment.

Suppliers are **external** and interact with OCT by email only. They have **no access to the system**.

---

## 3. Users & Roles

| Role (FR) | Role (EN) | Description |
|---|---|---|
| Assistant Comptable | Accounting Assistant | Receives supplier emails, enters invoices into the system, initiates the BAP workflow |
| Validateur N1 | Level 1 Approver | Department head — first approval gate |
| Validateur N2 | Level 2 Approver | Director — second approval gate (only for 3 departments) |
| DAF / Directeur Financier | Finance Director | Gives the final "Bon à Payer" authorization |
| Administrateur | System Administrator | Manages users, roles, departments, system config |
| Auditeur | Auditor | Read-only access to all invoices, audit logs, and reports |

> **Note:** There is no "Supplier" role. Suppliers never log into the system.

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
- Immutable audit log of every action (who, what, when, from which IP)
- Complete status history per invoice
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

| Status (FR) | Status (EN) | Description |
|---|---|---|
| BROUILLON | DRAFT | Entered but not yet submitted |
| SOUMIS | SUBMITTED | Submitted for review, awaiting assignment |
| EN_VALIDATION_N1 | UNDER_REVIEW_L1 | Under review by Level 1 approver |
| EN_VALIDATION_N2 | UNDER_REVIEW_L2 | Under review by Level 2 approver |
| VALIDE | VALIDATED | All approvals collected |
| BON_A_PAYER | APPROVED | Finance director authorized payment |
| PAYE | PAID | Payment recorded |
| ARCHIVE | ARCHIVED | Final state — stored for compliance |
| REJETE | REJECTED | Rejected at any stage — reason mandatory |

---

## 6. Department → Approval Mapping

| Department | Code | Approver N1 | Approver N2 |
|---|---|---|---|
| Direction des Ressources Humaines | DRH | DRH | — |
| Direction Générale | DG | DG | — |
| Finance | FIN | DAF | — |
| Informatique | INFO | RSI | DSI |
| Terminal | TERM | DEX | — |
| Communication & RSE | COM | Resp. Com | — |
| QHSSE | QHSSE | Resp. QHSSE | — |
| Infrastructure | INFRA | Resp. INFRA | Directeur INFRA |
| Atelier / Direction Technique | TECH | Resp. Atelier | Directeur Technique |

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

- Supplier portal or supplier login
- Notifications to suppliers
- ERP or accounting software integration (future phase)
- Mobile application (future phase)
- Electronic signature (future phase)
- Automatic bank transfer initiation
