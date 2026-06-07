# OCT Invoice Validation System — Complete Technical & Functional Briefing
> **Purpose:** This document is the single source of truth for the Digital and Secure Supplier Invoice Validation Management System developed for OCT (Owendo Container Terminal). It is intended for any developer or AI coding assistant working on this codebase to understand the full scope, all roles, all business rules, and every module before touching any code.

---

## 1. Project Identity

| Field | Value |
|---|---|
| **Full name** | Digital and Secure Supplier Invoice Validation Management System |
| **Client** | OCT — Owendo Container Terminal, Libreville, Gabon |
| **Student / Author** | NDONG SIMA DANY MICKAEL — Reg. 25627, AUCA |
| **Degree** | BSc Information Technology, Software Engineering |
| **Supervisor** | Mr. Justin NSHUNGUYE |

---

## 2. Organisation Context

OCT is a container terminal operator at the Port of Owendo near Libreville, Gabon. It is a subsidiary of Africa Global Logistics (AGL). It employs ~300 Gabonese staff, handles ~175,000 TEUs per year, and holds triple ISO certification (9001, 14001, 45001).

OCT has **nine departments**, each generating supplier invoices that require formal validation before payment:

1. Human Resources
2. General Management
3. Finance
4. Information Technology (IT)
5. Terminal Operations
6. Communication & CSR
7. QHSSE (Quality, Health, Safety, Security, Environment)
8. Infrastructure
9. Workshop & Technical Department

Financial administration is centralised in the Finance Department under the CFO. Day-to-day accounts-payable work is handled by Accounting Assistants.

---

## 3. Problem Being Solved

OCT's current process is entirely manual: invoices arrive through uncontrolled channels (email, physical drop-off, informal handoff), are routed from memory, approved without checking procurement records, and stored across paper files and email inboxes. This causes:

- Duplicate payments
- Lost invoices
- No real-time tracking
- Impossible audit reconstruction
- No enforcement of the approval matrix
- No duplicate detection
- No three-way matching

This system replaces that process end-to-end.

---

## 4. Technology Stack

This is the **actual implemented stack**. All code in this repository uses these technologies. Do not reference Python, FastAPI, Alembic, pyotp, or Pytest anywhere — the backend is Java/Spring Boot.

### 4.1 Core Stack

| Layer | Technology | Version | Status |
|---|---|---|---|
| **Frontend** | React.js | 19.2.4 | ✅ |
| **Backend language** | Java | 21 | ✅ |
| **Backend framework** | Spring Boot | 3.4.1 | ✅ |
| **Database** | PostgreSQL | 18-alpine | ✅ |
| **Auth tokens** | JWT — HS256 symmetric (JJWT) | 0.12.6 | ⚠️ Must be changed to RS256 — see Section 4.3 |
| **Password hashing** | BCrypt — Spring Security | Strength 12 | ✅ |
| **MFA** | TOTP — dev.samstevens.totp | 1.7.1 | ✅ |
| **Encryption in transit** | TLS 1.3 | — | ⚠️ Infrastructure-level only — see Section 4.3 |
| **Encryption at rest** | AES-256/GCM via EncryptionUtil | — | ✅ |
| **OCR** | ⚠️ NOT IMPLEMENTED — Apache Tika (MIME detection only) | 2.9.2 | 🔴 Critical gap — see Section 4.3 |
| **DB migrations** | Flyway | — | ✅ |
| **Object storage** | MinIO | 8.5.13 | ✅ |
| **Invoice state machine** | Spring State Machine | 4.0.0 | ✅ |
| **PDF generation** | iText | 8.0.5 | ✅ |
| **Excel export** | Apache POI | 5.3.0 | ✅ |
| **Email (dev)** | MailHog | — | ✅ (dev only — replace with real SMTP for production) |
| **Containerisation** | Docker + Docker Compose | — | ✅ |
| **CI pipeline** | ⚠️ NOT IMPLEMENTED | — | 🟡 Missing — see Section 4.3 |

### 4.2 Testing Stack

| Type | Technology | Status |
|---|---|---|
| **Backend unit / integration** | JUnit 5 + Mockito + Testcontainers | ✅ |
| **Frontend component** | Vitest + React Testing Library | ✅ |
| **End-to-end** | Playwright | ✅ |
| **Security scanning** | OWASP ZAP | ⚠️ Not implemented — see Section 4.3 |
| **Load testing** | Not implemented | ⚠️ Not implemented |

### 4.3 Known Gaps — Must Be Fixed

These are missing or incorrect items that must be addressed before the system is complete and compliant with the project requirements.

---

#### GAP 1 — OCR not implemented 🔴 Critical

**Current state:** Apache Tika 2.9.2 is in the codebase but only performs MIME type detection. It cannot read text from invoice images or scanned PDFs. The Supplier's "confirm/correct OCR data" flow in the submission portal has no working backend.

**What OCR must do:** When a supplier uploads an invoice (PDF, JPEG, PNG, TIFF), the system must extract: invoice number, invoice date, total amount, line items (description, quantity, unit price), supplier identifier, and PO reference number. The supplier then reviews and corrects the extracted fields before final submission.

**Fix:** Add **Tess4J** (the official Java JNA wrapper for Tesseract OCR) as a dependency. Implement an `OcrService` that:
1. Accepts an uploaded file.
2. Uses Tika (already present) to detect MIME type.
3. If the file is a text-based PDF, extracts text via a PDF text-layer reader (PDFBox, already likely available).
4. If the file is an image or scanned PDF, converts pages to images and runs Tess4J to extract text.
5. Parses the raw extracted text into structured fields (invoice number, date, amount, line items, PO reference).
6. Returns the structured fields to the frontend for supplier confirmation.

Keep Tika for MIME detection. Add Tess4J for actual OCR.

---

#### GAP 2 — JWT uses HS256 instead of RS256 🟠 High

**Current state:** JJWT 0.12.6 configured with a symmetric HS256 shared secret. Any service that can verify tokens can also forge them.

**Why it matters:** RS256 (asymmetric) is the standard for financial APIs and is explicitly specified in the project security requirements. With RS256, the private key signs tokens and is kept secret on the server; the public key verifies them and can be distributed safely.

**Fix:** In the Spring Security / JJWT configuration:
1. Generate an RSA-2048 key pair (or load from a Java KeyStore / `.pem` files).
2. Replace `Keys.hmacShaKeyFor(secret.getBytes())` with the `RsaKey` / `PrivateKey` + `PublicKey` pattern in JJWT.
3. Use the private key when calling `.signWith(privateKey, SignatureAlgorithm.RS256)`.
4. Use the public key when calling `.setSigningKey(publicKey)` in the parser.
5. Store the private key securely (environment variable or secrets manager — never commit to the repository).

---

#### GAP 3 — GitHub Actions CI pipeline not implemented 🟡 Medium

**Fix:** Create `.github/workflows/ci.yml`. The pipeline must run on every pull request and push to main:
1. Checkout the repository.
2. Set up Java 21 and build the backend (Maven or Gradle).
3. Run JUnit 5 tests.
4. Set up Node.js and install frontend dependencies.
5. Run Vitest tests.
6. Build Docker images.
7. Report pass/fail status on the pull request.

---

#### GAP 4 — TLS 1.3 not explicitly configured in Spring Boot 🟡 Medium

**Current state:** TLS is handled at infrastructure level only (reverse proxy / load balancer). The Spring Boot application has no SSL configuration.

**Fix:** In `application-prod.yml` (production profile):
```yaml
server:
  ssl:
    enabled: true
    protocol: TLSv1.3
    enabled-protocols: TLSv1.3
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```
This ensures TLS is enforced at the application layer regardless of infrastructure configuration.

---

#### GAP 5 — OWASP ZAP security scan not implemented 🟡 Medium

**Fix:** Add a security scan job to the CI pipeline (or a separate `security-scan.yml` workflow). Run an OWASP ZAP baseline scan against the test deployment environment. At minimum, configure ZAP to scan all authenticated API endpoints. For a financial system handling encrypted bank details and personal data, a baseline automated scan is the minimum acceptable security validation.

---

## 5. Roles — Complete Definition

There are exactly **six roles** in this system. No more, no less.

> **Critical rule:** Internal staff accounts (all roles except Supplier) are **created by the Administrator**. There is no public self-registration for staff. Suppliers may be onboarded through the Accounting Assistant's Manage Suppliers workflow or via a supplier request flow pending admin activation. No role can create its own account except through the Admin-controlled onboarding process.

---

### 5.1 Supplier

**Who they are:** External companies or individuals that provide goods or services to OCT and submit invoices for payment.

**Account creation:** Created and activated by the Accounting Assistant via the Supplier Management module after onboarding verification. The AA initiates the onboarding, verifies documents, and activates the supplier account. This is distinct from staff account creation, which is the Administrator's responsibility.

**MFA:** NOT required. The Supplier is the only role in the system exempt from MFA.

**What they can do:**

| Use Case | Description |
|---|---|
| Log In / Log Out | Access the supplier portal with their credentials |
| Reset Password | Self-service password recovery |
| Manage Profile | Update their own account profile |
| View Dashboard | See their personalised supplier dashboard (submitted invoices, payment status, pending actions) |
| Receive Notifications | Receive automated alerts (submission confirmed, invoice approved, invoice rejected, payment processed) |
| Submit Invoice | Upload invoice in PDF, XML, or image format. The system automatically extracts fields via OCR (invoice number, date, total, line items, supplier ID, PO reference). Supplier confirms/corrects extracted data. System assigns unique tracking reference and performs immediate duplicate check. |
| Track Invoice & Payment Status | See the real-time status of any invoice they submitted: submitted → under validation → pending approval → approved → payment scheduled → paid / rejected |
| Manage Company / Bank Details | Update company name, tax ID, contact information, bank account details (encrypted at rest) |
| Resubmit Rejected Invoice | After receiving a rejection notification at **any stage** (validation failure, Level 1 rejection, Level 2 rejection, or CFO rejection), submit a corrected version of the invoice with the issues addressed |

**What they CANNOT do:** Access any other supplier's data, view internal approval decisions or comments in detail, access financial reports, access the audit trail, access any admin functions.

---

### 5.2 Accounting Assistant (Accounts Payable Clerk)

**Who they are:** OCT Finance Department staff responsible for receiving, processing, routing, and tracking all supplier invoices. The **initiator** of every invoice in the approval workflow.

**Account creation:** Created by the Administrator.

**MFA:** **MANDATORY** (finance staff).

**What they can do:**

| Use Case | Description |
|---|---|
| Log In / Log Out | |
| Reset Password | |
| Manage Profile | |
| View Dashboard | Finance staff dashboard: validation queue, processing metrics, aging analysis, pending tasks |
| Receive Notifications | New invoice submissions, validation failures, approval completions, rejections, SLA breaches, payment due dates |
| Receive & Register Invoice | Accept invoices submitted by suppliers through the portal. System automatically logs receipt timestamp and assigns tracking reference. |
| Validate Invoice | Run automated validation before routing for approval: (1) completeness check — all required fields present; (2) supplier verification — supplier is in the active register; (3) purchase order verification — linked PO exists and is open; (4) amount threshold check; (5) duplicate detection against rolling history window. Failed invoices are returned to the supplier with specific rejection reasons and resubmission guidance. |
| Perform Three-Way Matching | Compare invoice line items against the linked Purchase Order (PO) and Goods Receipt Note (GRN). Each line item is checked for: quantity match across all three documents; unit price match between invoice and PO within configured tolerance threshold. |
| Flag Discrepancy | When three-way matching finds a mismatch beyond the tolerance threshold, flag it for manual review. The invoice is held; the discrepancy is recorded. |
| Override Discrepancy | When a discrepancy is flagged, the AA may manually override it with a written justification. The override and justification are permanently recorded in the audit trail. |
| Initiate Approval Routing | After validation and matching pass (or a discrepancy is overridden), trigger the approval workflow engine. The engine reads the approval matrix and routes to the correct Level 1 approver based on which department originated the purchase. |
| Record & Track Payment | After final approval, record payment details and track from scheduling through to settlement. Monitor payment due dates and aging. |
| Generate Remittance Advice | Automatically generated upon payment confirmation and sent to the supplier. |
| Manage Suppliers | Full supplier lifecycle management: onboard new suppliers (trigger account creation), update supplier profiles, deactivate suppliers, view supplier performance metrics (invoice accuracy, payment history), manage contracts and documents. |
| Archive Invoice | Store fully processed invoices and supporting documents in the digital archive with metadata. |
| Generate Operational Reports | Invoice processing time, approval cycle duration by department, aging analysis, bottleneck identification, volume and value trends, payment aging. Export as PDF or Excel. |
| Reset Password | |

**What they CANNOT do:** Approve or reject invoices (they are the initiator, not an approver), access the financial audit trail (that belongs to the CFO), access system/security logs (that belongs to the Administrator), manage user accounts.

---

### 5.3 Level 1 Approvers

**Who they are:** The department managers who provide first-level approval for invoices related to their department. There are **eight** Level 1 Approver roles:

| Role Title | Department |
|---|---|
| HR Director (HRD) | Human Resources |
| General Manager (GM) | General Management |
| IT Manager | Information Technology |
| Terminal Manager | Terminal Operations |
| Communication Manager | Communication & CSR |
| QHSSE Manager | QHSSE |
| Infrastructure Manager | Infrastructure |
| Workshop Manager | Workshop & Technical Dept |

> **Note:** The CFO is also a Level 1 Approver for the Finance department but has additional responsibilities and is treated as a separate role — see Section 5.5.

**Account creation:** Created by the Administrator.

**MFA:** **MANDATORY** (finance/approval staff).

**What they can do:**

| Use Case | Description |
|---|---|
| Log In / Log Out | |
| Reset Password | |
| Manage Profile | |
| View Dashboard | Manager dashboard: invoices pending their approval, budget alerts, processing metrics, approval analytics |
| Receive Notifications | New approval requests, SLA breach alerts, escalation notices |
| Review Invoice (Level 1) | View full invoice details, OCR-extracted data, three-way matching result, validation outcome, any flagged discrepancies, supplier profile, linked PO and GRN |
| Approve / Reject (Level 1) | Record an approval or rejection decision with a mandatory comment/reason. Decision is timestamped and attributed to the user. **On approval:** for single-level departments the invoice proceeds to payment scheduling; for two-level departments (IT, Infrastructure, Workshop) the invoice is automatically forwarded to the Level 2 approver. **On rejection:** the invoice is returned to the Accounting Assistant who notifies the supplier; the invoice does NOT escalate to Level 2. |
| Delegate Approval (Level 1) | Transfer approval authority to a designated substitute during absence. The delegation is recorded with start/end date. The substitute receives all notifications and approval requests during the delegation period. |
| Escalate on SLA Breach (Level 1) | System-triggered: when an invoice has been pending Level 1 approval beyond the configured SLA deadline without a decision, the system automatically escalates — notifying the approver, the Accounting Assistant, and (if configured) a senior manager. |

**What they CANNOT do:** Approve invoices from departments other than their own, access financial audit trail, access system logs, manage users, view other approvers' dashboards, access the CFO's reports.

---

### 5.4 Level 2 Approvers

**Who they are:** Senior officers who provide the second and final level of approval, but **only** for three specific departments. There are **three** Level 2 Approver roles:

| Role Title | Department | Their Level 1 |
|---|---|---|
| Chief Information Officer (CIO) | IT | IT Manager |
| Infrastructure Director | Infrastructure | Infrastructure Manager |
| Technical Director | Workshop & Technical | Workshop Manager |

**Account creation:** Created by the Administrator.

**MFA:** **MANDATORY** (finance/approval staff).

**Important rule:** Level 2 approval is only triggered **after** Level 1 has been granted. If Level 1 rejects, the process stops and returns to the supplier — Level 2 is never involved. Level 2 approval cannot happen before Level 1.

**What they can do:**

| Use Case | Description |
|---|---|
| Log In / Log Out | |
| Reset Password | |
| Manage Profile | |
| View Dashboard | Same structure as Level 1 manager dashboard but scoped to Level 2 items |
| Receive Notifications | Notification only arrives after Level 1 has approved. Contains full invoice details. |
| Review Invoice (Level 2) | Same review capability as Level 1: full invoice, matching results, Level 1 decision and comment visible |
| Approve / Reject (Level 2) | Record final approval or rejection with mandatory comment. **On approval:** invoice proceeds to payment scheduling. **On rejection:** invoice returns to Accounting Assistant; supplier is notified and may resubmit. |
| Delegate Approval (Level 2) | Same delegation mechanism as Level 1 |
| Escalate on SLA Breach (Level 2) | Same system-triggered mechanism as Level 1 |

**What they CANNOT do:** Approve invoices outside their three departments (IT, Infrastructure, Workshop), bypass Level 1 (they only ever receive invoices that have already been Level 1 approved), access financial audit trail, access system logs, manage users.

---

### 5.5 CFO (Chief Financial Officer)

**Who they are:** The head of OCT's Finance Department. The CFO has a **dual role**: they are the Level 1 Approver for Finance department invoices AND the financial oversight authority for audit trail and compliance.

**Account creation:** Created by the Administrator.

**MFA:** **MANDATORY** (finance/approval staff).

**What they can do:**

| Use Case | Description |
|---|---|
| Log In / Log Out | |
| Reset Password | |
| Manage Profile | |
| View Dashboard | Finance leadership dashboard: invoice pipeline status, payment obligations, overall system metrics, compliance alerts |
| Receive Notifications | Finance-department approval requests, SLA breaches, compliance alerts, payment due date alerts |
| Approve / Reject (Finance Dept) | Level 1 approval for all invoices belonging to the Finance department. Same mechanism as other Level 1 approvers. Finance is a single-level department — no Level 2 exists. |
| Review Financial Audit Trail | Access the complete, immutable, tamper-evident log of all financial activity in the system: every invoice submission, every validation result, every approval/rejection decision with timestamps and user attribution, every payment status change, every document access by finance staff. This is **financial information** — the Administrator does NOT have access to this. |
| Export Audit Log | Export the financial audit trail as a filtered, downloadable file (PDF or Excel) for internal or external audit purposes. This is an integral part of reviewing the audit trail. |
| Generate Compliance & Audit Reports | Produce compliance reports for internal governance and external audit: approval sequence compliance, processing time analysis, duplicate payment analysis, SLA compliance by department, exception and override reports. |

**What they CANNOT do:** Access system/security logs (Administrator's domain), manage user accounts, configure the system, access other roles' private dashboards.

---

### 5.6 Administrator

**Who they are:** The system administrator — an IT or operations staff member responsible for the technical and access management of the platform. **This is a system role, not a financial role.** The Administrator has zero access to financial data, invoices, approval decisions, or the financial audit trail.

**Account creation:** The first Administrator account is seeded during deployment. Additional Admin accounts are created by an existing Administrator.

**MFA:** **MANDATORY.** The Administrator has the highest system privileges (creates all accounts, configures security, manages the approval matrix). MFA is required for this role regardless of it being a system role rather than a financial one.

**What they can do:**

| Use Case | Description |
|---|---|
| Log In / Log Out | |
| Reset Password | |
| Manage Profile | |
| View Dashboard | Administrator dashboard: user activity overview, system health, integration status, security incident alerts, backup status |
| Receive Notifications | System alerts, integration failures, security incidents, backup failures |
| Manage Users, Roles & Permissions | Create, edit, activate, deactivate user accounts for all roles. Assign roles and departments. Set approval limits for approvers. Bulk import/export users. Process access requests. **This is the only way internal staff accounts are created** — there is no self-registration for staff. |
| Configure Approval Matrix | Update the departmental approval routing rules without code changes. The matrix is stored as a configurable database entity. Add/edit departments, Level 1 and Level 2 approvers per department, approval thresholds. |
| Configure Integrations | Set up and manage connections to external enterprise systems: ERP (SAP, Oracle, MS Dynamics), procurement system (for PO data), accounting software (for GL posting), banking system (for payment confirmation). API key management, webhook configuration, sync schedules, integration health monitoring. |
| Manage Security & System Settings | Manage password policies, session timeout controls, MFA policy settings, data encryption configuration, data backup schedule, retention policy configuration, privacy settings, compliance checklist management. |
| Monitor System & Security Logs | Access system-level and security-level logs ONLY: user login/logout events, failed login attempts, account lockouts, session activity, role changes, integration health events, security incidents, system errors. **This is NOT financial data** — the Admin sees operational/security logs, not invoice data or approval decisions. |

**What they CANNOT do:** View, approve, reject, or interact with any invoice. Access the financial audit trail (CFO only). Generate financial or compliance reports. See supplier banking details (those are encrypted and only accessible to finance roles with appropriate permission).

---

## 6. Departmental Approval Matrix

This matrix is authoritative. It is what the routing engine implements. **The Accounting Assistant (Assistant comptable) is the initiator for all departments.**

The system stores role codes internally in French (as used in the codebase). Both English and French names are listed below to eliminate translation ambiguity.

| Department (EN) | Department (FR) | Initiator | Level 1 Approver (EN) | Level 1 Code | Level 2 Approver (EN) | Level 2 Code |
|---|---|---|---|---|---|---|
| Human Resources | Direction des Ressources Humaines | Accounting Assistant | HR Director | `DRH` | — | — |
| General Management | Direction Générale | Accounting Assistant | General Manager | `DG` | — | — |
| Finance | Finance | Accounting Assistant | CFO | `DAF` | — | — |
| Information Technology | Informatique | Accounting Assistant | IT Manager | `RSI` | CIO | `DSI` |
| Terminal Operations | Terminal | Accounting Assistant | Terminal Manager | `DEX` | — | — |
| Communication & CSR | Communication & RSE | Accounting Assistant | Com. Manager | `Resp. Com` | — | — |
| QHSSE | QHSSE | Accounting Assistant | QHSSE Manager | `Resp. QHSSE` | — | — |
| Infrastructure | Direction des Infrastructures | Accounting Assistant | Infra. Manager | `Resp. INFRA` | Infra. Director | `Directeur INFRA` |
| Workshop & Technical | Direction Technique | Accounting Assistant | Workshop Manager | `Resp. Atelier` | Technical Director | `Directeur Technique` |

> **Note on code naming:** The codebase uses French abbreviations for role codes (e.g., `ROLE_DAF` for CFO, `ROLE_VALIDATEUR_N1_INFO` for IT Manager/RSI). These are OCT's internal designations and must not be changed. When displaying role names to users, the system must use the appropriate language based on the user's preference.

**Two-level departments (Level 2 required):** IT/Informatique, Infrastructure, Workshop & Technical / Direction Technique.
**Single-level departments (all others):** HR, General Management, Finance, Terminal, Communication & CSR, QHSSE.

**Routing rules:**
- Every invoice is routed based on the department that **originated the purchase** (not the Finance department).
- Level 2 approval is only triggered after Level 1 is granted.
- Rejection at Level 1 returns the invoice directly to the supplier — Level 2 is never notified.
- Rejection at Level 2 returns the invoice to the supplier via the Accounting Assistant.
- The Accounting Assistant cannot approve any invoice — they are the initiator only.

---

## 7. Complete Invoice Lifecycle

This is the authoritative state machine. The codebase stores invoice statuses as **French enum values** — these are the database values. English labels are display-only translations. Both are listed for every state.

### 7.1 Invoice State Machine

```
SUPPLIER submits invoice
        │
        ▼
[BROUILLON / DRAFT]
System: OCR extracts fields → Supplier confirms/corrects → Reference assigned
Supplier submits → triggers duplicate check and validation
        │
        ▼
[SOUMIS / SUBMITTED]
        │   Accounting Assistant performs validation:
        │   completeness, supplier active, PO exists & open, amount threshold, duplicate detection
        │   Three-Way Matching: Invoice ↔ PO ↔ GRN (quantity + unit price within tolerance)
        │
        ├─── Validation fails ──→ [REJETE / REJECTED] → Supplier notified → Supplier may RESUBMIT
        │
        ├─── Matching discrepancy beyond tolerance → FLAGGED FOR REVIEW
        │       ├─── AA overrides with written justification (recorded in audit trail) → continue
        │       └─── AA rejects → [REJETE / REJECTED] → Supplier notified → Supplier may RESUBMIT
        │
        └─── Validation + Matching pass → AA assigns to Level 1 Approver
                │
                ▼
        [EN_VALIDATION_N1 / UNDER_REVIEW_L1]  ← Level 1 Approver reviews
                │
                ├─── SLA breach → system escalates (notifies approver + AA)
                │
                ├─── Level 1 REJECTS → [REJETE / REJECTED] → AA + Supplier notified → Supplier may RESUBMIT
                │                        (Level 2 is NEVER notified on L1 rejection)
                │
                └─── Level 1 APPROVES →
                        │
                        ├─── Single-level dept (HR, GM, Finance, Terminal, Com., QHSSE) →
                        │        │
                        │        ▼
                        │   [VALIDE / VALIDATED]
                        │
                        └─── Two-level dept (IT / Infrastructure / Workshop) →
                                │
                                ▼
                        [EN_VALIDATION_N2 / UNDER_REVIEW_L2]  ← Level 2 Approver reviews
                                │
                                ├─── SLA breach → system escalates
                                │
                                ├─── Level 2 REJECTS → [REJETE / REJECTED] → AA + Supplier notified → Supplier may RESUBMIT
                                │
                                └─── Level 2 APPROVES →
                                        │
                                        ▼
                                [VALIDE / VALIDATED]
                                        │
                                        ▼
                        ─────────────────────────────────────────────
                        ALL approved invoices reach this step:
                        [BON_A_PAYER / AUTHORISED TO PAY]
                        ← DAF (CFO) issues final payment authorisation
                        ─────────────────────────────────────────────
                        This step is the core of the "Bon à Payer" process.
                        It applies to ALL departments, not just Finance.
                        Departmental approvals confirm the invoice is valid.
                        The DAF BON_A_PAYER formally authorises release of funds.
                        These are two distinct financial control gates.
                                │
                                ├─── DAF REJECTS → [REJETE / REJECTED] → AA + Supplier notified → Supplier may RESUBMIT
                                │
                                └─── DAF ISSUES BON_A_PAYER →
                                        │
                                        ▼
                                [PAYE / PAID]
                                ← Accounting Assistant records payment details
                                ← Remittance advice auto-generated and sent to Supplier
                                        │
                                        ▼
                                [ARCHIVE / ARCHIVED]  ← Automatic, no manual step
                                  Stored in digital archive with full metadata
```

**Resubmission:** Any invoice in `REJETE` status can be corrected and resubmitted by the Supplier. Resubmission restarts the full lifecycle from `SOUMIS`.

---

### 7.2 Invoice Status Reference (French code names ↔ English display labels)

The database stores French enum values. The frontend displays the English or French label based on the user's language preference.

| French (DB / Code) | English (Display) | Description |
|---|---|---|
| `BROUILLON` | Draft | Entered but not yet submitted |
| `SOUMIS` | Submitted | Submitted — pending AA validation and matching |
| `EN_VALIDATION_N1` | Under Review — Level 1 | Assigned to Level 1 approver |
| `EN_VALIDATION_N2` | Under Review — Level 2 | Assigned to Level 2 approver (IT/Infra/Workshop only) |
| `VALIDE` | Validated | All departmental approvals obtained |
| `BON_A_PAYER` | Authorised to Pay | CFO/DAF has issued final payment authorisation |
| `PAYE` | Paid | Payment recorded by Accounting Assistant |
| `ARCHIVE` | Archived | Final state — stored for compliance |
| `REJETE` | Rejected | Rejected at any stage — reason mandatory |

**Rejection is always terminal until resubmitted.** A rejected invoice moves to `REJETE` regardless of which stage it was rejected at. The rejection reason must always be recorded.

---

## 8. All 14 System Modules

### Module 1 — User Authentication & Access Control
- Secure login with username/email + password for all roles.
- **MFA (TOTP) is MANDATORY for:** Accounting Assistant, all Level 1 Approvers, all Level 2 Approvers, CFO, and Administrator. It is NOT required for Supplier only.
- Role-based dashboard redirection after login.
- Session management with configurable timeout.
- Login attempt tracking and account lockout after repeated failures.
- Password strength enforcement.
- Email verification for new accounts.
- Password recovery/reset flow for all roles.
- **No self-registration for internal staff.** Admin creates staff accounts. Suppliers are onboarded through the Accounting Assistant's Supplier Management workflow.

### Module 2 — Role-Based Dashboards
Each role has a distinct dashboard:
- **Supplier:** submitted invoices, payment status, pending actions, tracking references.
- **Accounting Assistant:** validation queue, processing metrics, aging analysis, SLA alerts.
- **Level 1 / Level 2 Approvers:** pending approval requests, processing metrics, budget alerts.
- **CFO:** full pipeline view, payment obligations, compliance alerts, financial KPIs.
- **Administrator:** user activity, system health, integration status, security incidents, backup status.

All dashboards include: notification centre, quick action buttons, mobile-responsive layout.

### Module 3 — Invoice Reception
- Supplier-facing submission portal.
- Accepted formats: PDF, XML, standard image formats (JPEG, PNG, TIFF).
- OCR engine (Tess4J — **must be implemented, see Section 4.3 Gap 1**) must extract: invoice number, date, total amount, line items, supplier identifier, PO reference.
- Supplier reviews and confirms/corrects OCR-extracted data before final submission.
- System assigns unique tracking reference on submission.
- Immediate duplicate detection on submission.
- Submission confirmation with reference number sent to supplier.
- Bulk invoice upload for high-volume suppliers.
- Supplier can view their full submission history.
- API integration endpoint for automated submission by supplier systems.

### Module 4 — Validation Workflow
Automated checks run in sequence before routing:
1. **Completeness:** all required fields present (invoice number, date, amount, supplier ID, PO reference, line items).
2. **Format validation:** data matches defined standards.
3. **Supplier verification:** supplier is in the active register and is not blocked/deactivated.
4. **PO verification:** linked purchase order exists, is open, and has not been fully consumed.
5. **Amount threshold:** invoice amount is within configured thresholds.
6. **Duplicate detection:** no existing invoice with the same number from the same supplier in the rolling detection window.

On failure: invoice is returned to Supplier with specific rejection reason(s) and resubmission guidance. Supplier may correct and resubmit.

On pass: invoice advances to Three-Way Matching.

Configurable validation rules — no hard-coded thresholds. Escalation rules for delayed processing.

### Module 5 — Three-Way Matching
- Compares Invoice ↔ Purchase Order ↔ Goods Receipt Note at **line-item level**.
- For each line item, checks: quantity delivered (GRN) = quantity invoiced (Invoice) = quantity ordered (PO); unit price (Invoice) = unit price (PO) within configured tolerance %.
- **Matched:** all items within tolerance → invoice proceeds to approval routing.
- **Mismatched:** one or more items exceed tolerance → invoice flagged, held for AA manual review.
- AA can override with written justification (recorded permanently in audit trail).
- Complete matching history stored per invoice (basis for payment authorisation).
- Export matching reports.
- Integration with procurement and inventory systems for PO/GRN data.

### Module 6 — Approval Workflow
- Implements the departmental approval matrix as a configurable routing engine.
- Routing is based on the **department that initiated the purchase** (not Finance).
- Sequential enforcement: Level 2 is only triggered after Level 1 approval — never in parallel.
- Approval/Rejection recorded with: decision, timestamp, user identity, mandatory comment.
- Delegation: approvers can transfer authority to a named substitute for a defined period.
- SLA monitoring: configurable deadline per approval level. Automatic escalation on breach (notifies approver, AA, and optionally senior management).
- Mobile-friendly approval interface.
- Approvers can see: full invoice, OCR data, matching result, validation outcome, Level 1 decision (for Level 2 approvers), supplier profile, linked PO and GRN.
- Approval history viewer per invoice.

### Module 7 — Payment Tracking
- Tracks approved invoices from scheduling through to final settlement.
- Payment statuses: `SCHEDULED` → `PROCESSING` → `CONFIRMED` / `OVERDUE`.
- Due date monitoring with configurable alert thresholds.
- Aging analysis (0–30 days, 31–60, 61–90, 90+ days).
- Payment batch processing.
- Remittance advice auto-generated on payment confirmation and sent to supplier.
- Multiple payment method support (bank transfer, cheque, mobile money).
- Payment confirmation recording.
- Supplier payment history view.
- Cash flow impact analysis.
- Export payment reports.

### Module 8 — Supplier Management
- Comprehensive supplier database: company name, tax ID, contact information, bank account details (AES-256 encrypted at rest), contracts, certificates.
- Supplier performance metrics: invoice accuracy rate, average processing time, payment history.
- Supplier onboarding workflow: AA initiates → document verification → account activation.
- Supplier self-service portal access provisioned by AA.
- Communication log per supplier.
- Supplier segmentation and categorisation.
- Document repository per supplier.
- Export supplier reports.

### Module 9 — Digital Archiving
- All invoices and supporting documents stored with structured metadata.
- Metadata: invoice number, supplier, department, date, amount, status, processing timestamps, all approval decisions.
- Advanced search: by invoice number, supplier, department, date range, amount range, status.
- Document viewer (in-browser, no download required for review).
- Version control for invoice revisions.
- Configurable retention policies (statutory + organisational compliance).
- Archive access logs (who accessed which document and when).
- Export archived documents.

### Module 10 — Audit Trail
- **Immutable** log — records cannot be modified or deleted after creation.
- Captures every system action:
  - Invoice submissions, modifications
  - Validation decisions and reasons
  - Three-way matching results and overrides
  - Approval and rejection decisions (with comments and timestamps)
  - Payment status changes
  - Document access events
  - User login/logout events (Admin log)
  - Role and permission changes (Admin log)
- **Financial audit trail** (invoice, approval, payment events) → accessible to **CFO only**.
- **System/security audit trail** (logins, role changes, integrations) → accessible to **Administrator only**.
- Anomaly detection: flags unusual patterns (e.g., same invoice submitted multiple times, unusual payment amounts, access outside working hours).
- Filtering by date range, user, action type, invoice ID.
- Export to PDF or Excel for external audit.
- Real-time monitoring dashboard.
- Forensic investigation support.

### Module 11 — Reporting & Analytics
**CFO generates:**
- Compliance & audit reports (approval sequence compliance, exceptions, overrides).
- Financial control reports.

**Accounting Assistant generates:**
- Invoice processing time analysis.
- Supplier performance reports.
- Aging and cash flow analysis.
- Approval bottleneck identification (which department/approver is slowest).
- Volume and value trends.
- Budget vs. actual comparison.

**All reports:**
- Exportable as PDF or Excel.
- Configurable for scheduled automated delivery.
- Custom report builder.
- Executive summary generator.

### Module 12 — Integration
- RESTful API for data exchange with external systems.
- **Procurement system:** read PO data (for matching).
- **Inventory system:** read GRN data (for matching).
- **Accounting software (ERP):** post approved invoices to general ledger.
- **Banking system:** receive payment confirmations (payment tracking, not payment initiation — the system does NOT execute banking transactions).
- Webhook support for real-time event notifications.
- Integration health monitoring (per-connection status).
- Sync schedule configuration.
- Error logging and resolution interface.
- API key management.
- Test connection interface.

**Out of scope:** Direct execution of banking transactions. The system tracks payment confirmation received from banking systems but does not initiate payments.

### Module 13 — User & Access Management
- User management console (Admin only).
- Role templates with default permissions per role category.
- Individual permission adjustment within role boundaries.
- Department-level access restrictions (users see only their relevant invoices unless role explicitly grants broader access).
- Account status management: active, inactive, locked, pending.
- Bulk user import/export.
- Access request and approval workflow.
- User activity monitoring.
- Role-based menu and interface configuration.
- Full user activity audit trail (system level, Admin only).

### Module 14 — Security & Compliance
- Role-based access control enforced at API level (not just UI level).
- Data encryption: TLS 1.3 in transit; AES-256 at rest for sensitive fields (bank details, tax IDs).
- MFA policy: mandatory for finance roles (AA, all approvers, CFO) and Administrator.
- Automated backup with tested recovery procedures.
- Data retention policy enforcement (configurable per document type).
- Compliance monitoring: SOX, IFRS, local Gabonese financial regulations.
- Security incident detection and reporting.
- Audit-ready documentation.
- Permission matrix viewer.
- Security health dashboard.
- Privacy policy acceptance tracking.
- Session management: configurable timeout, concurrent session control.
- Input validation on all endpoints (prevent injection attacks).
- Passwords stored as bcrypt hashes only — never plaintext.

---

## 9. Key Business Rules

1. **No self-registration for staff.** Supplier, AA, all Approvers, CFO, Admin accounts are created by the Administrator. Suppliers are onboarded by the AA.
2. **MFA mandatory for all roles except Supplier:** Accounting Assistant, Level 1 Approvers (all 8), Level 2 Approvers (all 3), CFO, and Administrator all require MFA. Supplier is the only role exempt from MFA.
3. **Routing is department-based**, not Finance-based. The department on the invoice determines the approver.
4. **Level 2 is sequential**, never parallel. CIO, Infrastructure Director, Technical Director only receive an invoice after their respective Level 1 has approved.
5. **Any rejection at any stage** sends the invoice back to the supplier (via notification). The supplier may resubmit a corrected version.
6. **Rejection at Level 1 stops the workflow** — it does not escalate to Level 2.
7. **The Accounting Assistant is an initiator, not an approver.** They cannot approve any invoice regardless of department.
8. **Override of a three-way matching discrepancy requires a written justification** permanently recorded in the audit trail.
9. **The financial audit trail belongs to the CFO** — not the Administrator, not the Accounting Assistant.
10. **The system/security audit trail belongs to the Administrator** — not any finance role.
11. **Duplicate detection:** if the same invoice number from the same supplier already exists in the system within the detection window, the second submission is rejected automatically before it ever reaches the AA.
12. **All approval decisions are timestamped and attributed** — they cannot be deleted or modified retroactively.
13. **Approved invoices are immutable** — they can only be archived, not edited.
14. **Rejected invoices that are resubmitted** are treated as new submissions and go through the full validation cycle again from the start.
15. **Delegation of approval** is a formal action with a recorded start/end date — it does not silently transfer authority.
16. **SLA escalation is system-triggered** — it does not require any manual action from the approver or the AA.

---

## 10. Security Requirements

| Requirement | Specification |
|---|---|
| Transport encryption | TLS 1.3 for all HTTP traffic |
| At-rest encryption | AES-256 for: bank account numbers, IBAN/SWIFT, tax identification numbers |
| Password storage | bcrypt with appropriate work factor — never plaintext, never reversible hash |
| Session management | Configurable timeout (default: 30 min inactivity), session token invalidation on logout |
| Authentication tokens | JWT with RS256 asymmetric signing — **currently implemented as HS256, must be corrected (see Section 4.3 Gap 2)** |
| MFA | TOTP (RFC 6238) via dev.samstevens.totp 1.7.1 — compatible with Google Authenticator, Authy etc. Mandatory for all roles except Supplier. |
| RBAC enforcement | Enforced at API/backend level, not only at UI level |
| Audit log | Immutable — append-only, no update or delete operations permitted on audit records |
| Input validation | All API endpoints validate and sanitise inputs |
| Login protection | Account lockout after configurable failed attempts, login attempt logging |
| Sensitive data access | Bank details and tax IDs encrypted at rest, accessible only to authorised roles through defined API endpoints |

---

## 11. Data Entities (Core Model)

Key entities the database must contain:

- **User** — id, name, email, password_hash, role, department, approval_limit, mfa_secret, mfa_enabled, status, created_by (Admin), created_at
- **Supplier** — extends User: company_name, tax_id (encrypted), bank_account (encrypted), contact_info, onboarding_status, performance_metrics
- **Invoice** — id, tracking_reference, supplier_id, department_id, status, submitted_at, amounts, line_items, po_reference, grn_reference, current_approver
- **InvoiceLineItem** — invoice_id, description, quantity, unit_price, total
- **PurchaseOrder** — id, department_id, supplier_id, line_items, status, created_at
- **GoodsReceiptNote** — id, po_id, received_items, received_at
- **ApprovalWorkflow** — id, invoice_id, department_id, required_levels
- **ApprovalStep** — workflow_id, level (1 or 2), approver_user_id, decision, comment, decided_at, sla_deadline
- **ThreeWayMatch** — invoice_id, match_status, tolerance_threshold, discrepancy_details, override_by, override_justification, override_at
- **Payment** — invoice_id, scheduled_date, processed_date, confirmed_date, method, remittance_sent
- **AuditLog** — id, timestamp, user_id, action_type, entity_type, entity_id, details, ip_address — IMMUTABLE (no UPDATE/DELETE)
- **ApprovalMatrix** — department_id, level_1_role, level_2_role (nullable), configurable via Admin
- **Delegation** — delegator_id, delegate_id, start_date, end_date, is_active

---

## 12. Non-Functional Requirements

| Category | Requirement |
|---|---|
| **Performance** | Routine pages and API responses < 3 seconds under normal load |
| **Scalability** | Architecture supports growth in users, invoice volumes, and departments without redesign |
| **Availability** | Available during OCT working hours with scheduled maintenance windows; data protected by automated backup and tested recovery |
| **Usability** | Interface usable by staff with basic computer skills; mobile-responsive on all devices |
| **Compatibility** | Works on current versions of Chrome, Firefox, Safari, Edge on desktop and mobile |
| **Maintainability** | Modular architecture; documented API; code accompanied by developer documentation supporting future extension |
| **Data integrity** | ACID-compliant database transactions; all inputs validated before persistence; audit logs append-only |
| **Internationalisation** | French and English fully supported. French is the default language (OCT operates in Gabon). English is switchable per user preference. All user-facing strings use message keys — no hardcoded text in Java or React code. Backend: `messages_fr.properties` + `messages_en.properties`. Frontend: `fr.json` + `en.json`. Language is detected from the `Accept-Language` header (backend) and stored user preference (frontend). Invoice status names are stored in the database as French enum values and translated for display only. |

---

---

## 13. Bilingual Reference — French / English Mappings

The system is fully bilingual. **French is the default language.** English is available per user preference. This section is the authoritative reference for all translatable terms. No string should be hardcoded in any source file.

### 13.1 Invoice Statuses

| French (DB enum — never change) | English label | Description |
|---|---|---|
| `BROUILLON` | Draft | Invoice created, not yet submitted |
| `SOUMIS` | Submitted | Submitted, awaiting AA validation |
| `EN_VALIDATION_N1` | Under Review — L1 | With Level 1 approver |
| `EN_VALIDATION_N2` | Under Review — L2 | With Level 2 approver |
| `VALIDE` | Validated | All departmental approvals obtained |
| `BON_A_PAYER` | Authorised to Pay | CFO/DAF final payment authorisation issued |
| `PAYE` | Paid | Payment recorded |
| `ARCHIVE` | Archived | Final state |
| `REJETE` | Rejected | Rejected at any stage |

### 13.2 Role Names

| English title (Briefing / display) | French title (OCT internal) | Spring role code |
|---|---|---|
| Accounting Assistant | Assistant comptable | `ROLE_ASSISTANT_COMPTABLE` |
| CFO (Chief Financial Officer) | DAF (Directeur Administratif et Financier) | `ROLE_DAF` |
| HR Director | DRH (Directeur des Ressources Humaines) | `ROLE_VALIDATEUR_N1_DRH` |
| General Manager | DG (Directeur Général) | `ROLE_VALIDATEUR_N1_DG` |
| IT Manager | RSI (Responsable du Système d'Information) | `ROLE_VALIDATEUR_N1_INFO` |
| CIO | DSI (Directeur du Système d'Information) | `ROLE_VALIDATEUR_N2_INFO` |
| Terminal Manager | DEX (Directeur d'Exploitation) | `ROLE_VALIDATEUR_N1_TERM` |
| Communication Manager | Resp. Com | `ROLE_VALIDATEUR_N1_COM` |
| QHSSE Manager | Resp. QHSSE | `ROLE_VALIDATEUR_N1_QHSSE` |
| Infrastructure Manager | Resp. INFRA | `ROLE_VALIDATEUR_N1_INFRA` |
| Infrastructure Director | Directeur INFRA | `ROLE_VALIDATEUR_N2_INFRA` |
| Workshop Manager | Resp. Atelier | `ROLE_VALIDATEUR_N1_TECH` |
| Technical Director | Directeur Technique | `ROLE_VALIDATEUR_N2_TECH` |
| Supplier | Fournisseur | `ROLE_SUPPLIER` |
| Administrator | Administrateur | `ROLE_ADMIN` |

### 13.3 Department Names

| English | French | Code |
|---|---|---|
| Human Resources | Direction des Ressources Humaines | `DRH` |
| General Management | Direction Générale | `DG` |
| Finance | Finance | `FIN` |
| Information Technology | Informatique | `INFO` |
| Terminal Operations | Terminal | `TERM` |
| Communication & CSR | Communication & RSE | `COM` |
| QHSSE | QHSSE | `QHSSE` |
| Infrastructure | Direction des Infrastructures | `INFRA` |
| Workshop & Technical | Direction Technique | `TECH` |

### 13.4 Workflow Actions (UI labels)

| English | French |
|---|---|
| Submit | Soumettre |
| Validate | Valider |
| Reject | Rejeter |
| Delegate | Déléguer |
| Override | Substituer / Passer outre |
| Issue Bon à Payer | Émettre le Bon à Payer |
| Record Payment | Enregistrer le paiement |
| Resubmit | Resoumettre |
| Archive | Archiver |

### 13.5 Implementation Rules for Bilingualism

1. **All user-facing strings** use message keys. Never hardcode French or English text in Java or React.
2. **Backend:** `messages_fr.properties` (default) + `messages_en.properties`. Language from `Accept-Language` header.
3. **Frontend:** `fr.json` + `en.json` in `src/i18n/`. Language stored in user profile preference.
4. **Database:** Status enums stored as French strings (`BROUILLON`, `SOUMIS`, etc.) — these are never translated at the DB level, only at the display layer.
5. **Role codes** (`ROLE_DAF`, `ROLE_ASSISTANT_COMPTABLE`, etc.) are internal Spring Security identifiers — never displayed directly to users. Always translate to the appropriate language label.
6. **Emails** sent to users must respect their language preference.
7. **PDF reports and remittance advice** must respect the language of the requesting user.

---

## 14. What Is Out of Scope

The following are explicitly excluded from this system:

- Replacing or migrating OCT's existing ERP system
- Initiating or executing banking transactions (payment tracking only)
- Bulk migration of historical paper-based invoice records
- AI or ML components beyond OCR-assisted data extraction (OCR via Tess4J — see Section 4.3 Gap 1)
- Native mobile applications (system is mobile-responsive web only)
- Live integration testing with production banking or ERP systems during the project period
- Payroll or HR financial processes (supplier invoices only)

---

## 15. Things Commonly Missed or Misimplemented

The following are areas most likely to be wrong or incomplete in the code:

1. **MFA not applied to the Administrator.** The Supplier is the only role exempt from MFA. All other roles — Accounting Assistant, all Level 1 Approvers, all Level 2 Approvers, CFO, and Administrator — must have MFA enforced.
2. **Self-registration endpoint for staff.** There must be no public registration for AA, Approvers, CFO, or Admin. Only the Admin creates these accounts.
3. **Routing uses the Finance department instead of the originating department.** The department field on the invoice must be the requesting department, not Finance.
4. **Level 2 triggering without waiting for Level 1.** Level 2 notifications must never fire until Level 1 has returned an `APPROVED` decision.
5. **Rejection only wired to the validation stage.** Rejection must be possible at: validation (AA), Level 1 approval, Level 2 approval, and CFO Finance approval — and all four must trigger supplier notification + allow resubmission.
6. **Admin can see financial audit trail.** Admin's audit access is restricted to system/security events only — login activity, role changes, integration health. Invoice, approval, and payment events belong to the CFO's audit trail exclusively.
7. **Duplicate detection only checked at validation.** Duplicate detection must happen at the moment of submission (before the invoice even reaches the AA queue) AND again during the formal validation step.
8. **Three-way matching override not recorded.** Any manual override of a discrepancy must write a record to the audit trail containing the user, timestamp, and justification. This record is immutable.
9. **Audit log records can be updated or deleted.** The audit log table must be append-only at the database level. No UPDATE or DELETE operations should ever be permitted on audit records.
10. **Missing delegation expiry enforcement.** Delegations have an end date. The system must automatically deactivate expired delegations and restore authority to the original approver.
11. **SLA escalation is a manual process.** It must be fully automated — a background job must check SLA deadlines and trigger escalation notifications without any human initiating it.
12. **Remittance advice sent only if manually triggered.** It must be auto-generated and auto-sent to the supplier upon payment confirmation with no manual step required.
13. **Sensitive fields stored unencrypted.** Bank account details and tax identification numbers must be encrypted with AES-256 before database storage, not stored as plaintext.
14. **Role-based access enforced only at UI level.** Every API endpoint must independently verify the caller's role and permissions — the backend must never trust the frontend alone for access control decisions.

---

*End of briefing document. Version corresponds to use case diagram v5 (final approved). Technology stack reflects actual implementation (Java 21 / Spring Boot 3.4.1 / React 19.2.4 / PostgreSQL 18). All content sourced from: Project Requirements Document, Final Year Project Proposal (Proposal_25627_FINAL_CORRECTED), Chapters 1–2 of the Final Year Project Report (Memoire_NDONG_SIMA_Ch1_Ch2_2), and codebase stack analysis.*
