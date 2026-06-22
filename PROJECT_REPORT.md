# PROJECT_REPORT.md — OCT Invoice System Technical Audit

> **Purpose:** Source material for Chapters 3 (Design) and 4 (Implementation) of the Bachelor's thesis "Digital and Secure Supplier Invoice Validation Management System" for Owendo Container Terminal (OCT).
> **Method:** Code is ground truth. `docs/TASKS.md` is treated as a stale planning artifact. Every claim below cites `file:line` from the repository root `invoice-system/`.
> **Scope of scan:** All tracked source plus gitignored config (`.env`, `application.yaml` profiles, `CLAUDE.md`, `docs/MEMORY.md`). All secret values redacted as `***REDACTED***`.
> **Audit date:** 2026-06-22. **Git:** accessible, 318 commits, 2026-03-21 → 2026-06-22, branch `fix/a1-cashflow-sqlgrammar` (off `main`).

---

## 0. KEY FINDINGS UP FRONT (read this first)

1. **Six roles in code, "seven actors" in the locked thesis brief.** The `ROLE_AUDITEUR` actor was deliberately removed (`V31__fix_finance_approver_and_remove_auditeur.sql`, `V33__remove_phantom_roles.sql`). `docs/PRD.md:31` explicitly states "exactly **six roles**… No more, no less." **If Chapters 1+2 list a 7th actor "Auditeur", the thesis conflicts with the code.** Auditor duties were split: financial audit → DAF only, system/security audit → ADMIN only (`AuditController.java:60-185`). This is the single biggest thesis-vs-code conflict — see **R1**.
2. **TASKS.md was abandoned as the planning artifact around Phase 9.** Commit prefixes shift from `P{X}-{XX}` (TASKS-driven, ~190 commits) to `M{N} #{n}` (driven by `docs/COMPLIANCE_MATRIX.md`, the real living plan). Migration numbering diverged as early as V16 (TASKS says V16=purchase_orders; code V16=add_role_supplier).
3. **The system is far larger than TASKS.md describes:** 47 JPA entities, 63 Flyway migrations, 39 REST controllers, ~492 `@Test` methods, 64 frontend routes/pages. Whole modules (compliance, retention disposition, escalation rules, announcements, supplier contracts, integration connectors, report builder, payment alert rules, checklists) exist with **no task in TASKS.md**.
4. **Most Chapter 1+2 claims are VERIFIED in code.** OCR, duplicate detection, three-way matching, MFA, RBAC, AES-256-GCM, immutable audit trail, archiving, notifications, reporting/export, webhooks, supplier portal are all implemented. PARTIAL/weak spots: TLS (config-only, no keystore shipped), aging analysis depth, duplicate-detection heuristic (supplier+description, not invoice number), WCAG.
5. **Test coverage:** JaCoCo CSV totals = **66.8 % instruction / 52.8 % branch** (`target/site/jacoco/jacoco.csv`). The HTML `index.html` shows "37 %" but reflects a stale/partial 8-class run — the CSV is authoritative.

---

## 1. EXECUTIVE STATUS — TASKS.md Reconciliation

`docs/TASKS.md` defines Phases 0–9G plus remediation Phases 10–11. Verification of the ✅ marks against code:

### Phases verified as genuinely implemented (spot-checked against code)

| Phase | Scope | Code evidence | Verdict |
|---|---|---|---|
| 0 — Bootstrap | Spring Boot, JWT, Flyway, Swagger, docker-compose | `pom.xml`, `SecurityConfig.java`, `application.yaml:144-152`, `docker-compose.yml` | ✅ real |
| 1 — User & Dept mgmt | Users, roles, departments | `UserController.java`, `DepartmentController.java`, `V1/V2/V3` | ✅ real |
| 2 — Invoice core | CRUD + docs + ref-number + MinIO | `InvoiceController.java`, `InvoiceDocumentService.java`, `StorageService` | ✅ real |
| 3 — Workflow engine | Spring State Machine BAP | `StateMachineConfig.java:48-117`, guards in `domain/workflow/guard/` | ✅ real |
| 4 — Notifications | Email + WebSocket + DB | `domain/notification/`, `WebSocketConfig.java`, `templates/email/*.html` (12 templates) | ✅ real |
| 5 — Payment & archiving | Payment record + auto-archive | `PaymentController.java`, `RemittanceAdvice`, `InvoiceStatus.ARCHIVE` | ✅ real |
| 6 — Audit & reporting | Audit log + KPI + export | `AuditController.java`, `ReportController.java` (24 endpoints) | ✅ real |
| 7 — Frontend | React/TS, FR/EN, role guards | `AppRoutes.tsx` (64 routes), `i18n/`, `ProtectedRoute`/`SupplierRoute` | ✅ real |
| 8 — Integration/hardening | docker-compose, e2e, Swagger | `docker-compose.yml`, `frontend/e2e/*.spec.ts` (3 specs) | ✅ real |
| 9A–9G — Supplier/MFA/Matching/Webhooks | see modules | `domain/supplier/`, `domain/mfa/`, `domain/purchasing/`, `domain/webhook/` | ✅ real |
| 10A — OCR | Tess4J + PDFBox | `OcrService.java:1-249` | ✅ real |
| 10B — JWT RS256 | RSA-2048 asymmetric | `JwtService.java:105` `SignatureAlgorithm.RS256` | ✅ real |
| 10D — TLS | prod-profile SSL block | `application.yaml:332-338` | ⚠ PARTIAL (config only, keystore not provided) |

### Tasks marked ✅ that are NOT (or not verifiably) implemented — false positives

| Task | Claim | Reality |
|---|---|---|
| **P10-C — GitHub Actions CI Pipeline** | "CI pipeline" ticked | `.github/workflows/` in repo root contains only a `java-upgrade` helper, not a build/test CI for this app. **No working build-and-test CI workflow is present.** → **R12** |
| **P10-E — OWASP ZAP Security Scan** | security scan ticked | No ZAP config, no scan report artifact in repo. Unverifiable / not present. → **R13** |
| **P8-08 README** | CLAUDE.md §4 says README created at P8-08 | No root `README.md` in `invoice-system/`. → **R14** |

### Tasks marked ✅ where implementation diverges materially from description — drift

| Task area | Planned (TASKS.md) | Actual (code) | One-line diff |
|---|---|---|---|
| Roles | 7th "Auditeur" actor in original brief | `ROLE_AUDITEUR` dropped; duties split DAF/ADMIN | Auditor removed (`V31`, `V33`) |
| Migration order | TASKS: V16=purchase_orders, V17=GRN, V18=3-way, V19=remittance, V20=webhooks | Code: V17=PO, V18=GRN, V19=3-way, V20=remittance, V22=webhooks | Whole V16+ block shifted by ≥1 |
| Duplicate detection | implied "duplicate invoice" check | `countDuplicatesBySupplierAndDescription` — same **supplier + description**, not invoice number (`InvoiceRepository.java:36`, `InvoiceStateMachineServiceImpl.java:137-154`) | Heuristic, not exact-number match |
| Webhook secret | V26 "add_encrypted_webhook_secret" | Reversed by `V30__remove_webhook_secret_encrypted.sql` | Encrypted-column approach abandoned |
| Matching trigger | "auto-trigger on BROUILLON→SOUMIS" | Implemented but `matching_status` is a plain `String` column on Invoice (`Invoice.java:128`), not an enum | Looser typing than implied |

---

## 1bis. UNDOCUMENTED CHANGES — what the developer did outside TASKS.md

TASKS.md stops describing reality around Phase 9. From ~commit 190 onward the developer worked from `docs/COMPLIANCE_MATRIX.md` (the genuine living plan) with `M{N} #{n}` commit IDs.

### (a) Implemented but never planned in TASKS.md

Entire domains/modules present in code with no corresponding TASKS.md task:

| Feature | Evidence (file:line) |
|---|---|
| Compliance module (incidents, checklist, calendar, backup status, privacy acceptance, archive report) | `domain/compliance/controller/ComplianceController.java:34-139` ; entities `compliance/model/*` ; `V56__create_compliance.sql` |
| Retention policy + disposition (PENDING/RETAINED/PURGED) | `RetentionDispositionController.java:31-39` ; `V62__create_retention_policy.sql` ; `V63__add_retention_disposition.sql` |
| Escalation rules (SLA) | `EscalationRuleController.java:33-59` ; `V61__create_escalation_rules.sql` |
| Announcements | `AnnouncementController.java:33-75` ; `V51__create_announcements.sql` |
| Supplier contracts & communications | `SupplierRelationshipController.java:31-63` ; `V52__create_supplier_contracts_and_comms.sql` |
| Integration connectors + scheduled sync | `IntegrationConnectorController.java:30-79` ; `V55` ; `V60__add_connector_sync_schedule.sql` |
| Report builder (saved definitions, run, preview) | `ReportController.java:167-205` ; `V54__create_report_definitions.sql` |
| Payment alert rules | `PaymentAlertRuleController.java:40-66` ; `V59__create_payment_alert_rules.sql` |
| Validation checklists (templates + per-invoice responses) | `ChecklistTemplateController.java`, `InvoiceChecklistController.java` ; `V58__create_validation_checklists.sql` |
| Access requests (self-service access elevation) | `AccessRequestController.java:41-80` ; `V47__create_access_requests.sql` |
| Document access log (who downloaded what) | `DocumentAccessLog.java` ; `V48__create_document_access_log.sql` |
| Approval delegation (out-of-office) | `DelegationController.java:32-128` ; `V40__create_approval_delegations.sql` |
| Active session management + admin force-logout | `AdminSessionController.java:24-32` ; `V39__create_active_sessions.sql` |
| Security policy (configurable max-login-attempts, MFA toggle) | `SecurityPolicyController.java:32-40` ; `V44__create_security_policy.sql` |
| Data sensitivity classification on invoices | `Invoice.java:104-107` ; `V46__add_invoices_data_sensitivity.sql` |
| Department budget + budget-vs-actual / budget-alerts reports | `V49__add_departments_budget.sql` ; `ReportController.java:149-158` |
| Dedicated matching page (list + line-by-line) | `MatchingQueryController.java:30-44` ; `pages/matching/MatchingListPage.tsx`, `MatchingDetailPage.tsx` |
| Volume/value trend + executive summary reports | `ReportController.java:214-224` |
| Department-access read-only admin view | `DepartmentAccessController.java:27-28` |
| Invoice import (CSV/bulk) | `InvoiceController.java:192-193` ; `InvoiceImportService.java` |
| XML structured-invoice parsing path in OCR | `OcrService.java:64-68` ; `InvoiceXmlParser.java` |

### (b) Refactored away from the plan

| Planned | Actual | Diff |
|---|---|---|
| `auditeur` role | removed | duties split DAF/ADMIN (`V31`,`V33`) |
| Webhook `secret_encrypted` column | dropped; HMAC secret stored differently | `V26`→`V30` reversal |
| Single route guard | two guards `ProtectedRoute` + `SupplierRoute` | per PROB-002 (`AppRoutes.tsx:3,81,130`) |
| MFA endpoints under generic auth | dedicated `/auth/mfa/{setup,confirm,validate}` two-step pre-auth token flow | `AuthController.java:84-102` |
| `matching_status` enum | plain `String` column | `Invoice.java:128` |

### (c) Removed / abandoned

- `ROLE_AUDITEUR` and any "phantom" roles — `V31`, `V33`.
- Webhook encrypted-secret column — `V26` added, `V30` removed.
- Migration versions **V36, V37, V38 do not exist** (numbering gap; see (d)). No evidence of deleted-then-recreated migrations — they were simply skipped.

### (d) Migrations beyond / diverging from TASKS.md numbers

> **⚠ Updated 2026-06-22 — migrations consolidated.** This section describes the migration set
> **as it was at audit time** (60 churned files, V1–V63 with a V36–V38 gap). That set has since
> been rewritten into a clean, contiguous **34-migration baseline (V1…V34)** — see
> `docs/DATABASE.md` and `docs/ARCHITECTURE.md §4.4`. The specific `V##` numbers cited below and
> in Section 5 refer to the **pre-consolidation** history and are kept for traceability. The
> consolidated schema is verified identical to the one analysed here (pg_dump diff empty; 491
> tests green).

- **TASKS.md references up to ~V20 (with wrong mapping) plus V42, V43.** Code contained (pre-consolidation) **63 migration versions V1–V63 with a gap at V36/V37/V38** (60 files); now a clean V1–V34 baseline.
- **Extras not in TASKS.md:** V21, V23–V35, V39–V63 — i.e. **40+ migrations are undocumented by TASKS.md.** Full list and purpose in **Section 5**.
- **Numbering gap:** `V36__`, `V37__`, `V38__` are absent — Flyway tolerates non-contiguous versions, so the schema is valid, but the gap should be explained in the thesis (likely squashed/abandoned during development).

### (e) Dependencies added beyond TASKS.md

Backend (`pom.xml`) notable libs not called out by early TASKS: `bucket4j-core` 7.6.0 (rate limiting), `dev.samstevens.totp` 1.7.1 (MFA), `tess4j` 5.11.0 + `pdfbox` 3.0.3 (OCR), `apache.tika` 2.9.2 (MIME), `poi-ooxml` 5.3.0 (Excel), `itext-core` 8.0.5 (PDF), `spring-statemachine` 4.0.0, `minio` 8.5.13, `springdoc` 2.7.0. Full list in **Section 2**.
Frontend (`frontend/package.json`) notable: `recharts` 3.8.1 (charts), `react-pdf` 9.2.1 (doc viewer), `@stomp/stompjs` 7.3.0 + `sockjs-client` 1.6.1 (WS), `@reduxjs/toolkit`, `@tanstack/react-query`, `zod`, `react-hook-form`, `i18next`.

### (f) Reconstructed chronological "real history" (git accessible)

Timeline from `git log` (318 commits, 2026-03-21 → 2026-06-22):

1. **2026-03-21 — `4945ec2` Initial commit**, then `a350110` Phase 0 bootstrap (JWT, security, Flyway, Swagger).
2. **Phase 2 burst** (`bf579c9`…`051ed30`): P2-01…P2-12 invoice core, MinIO, mappers — the most disciplined TASKS-aligned stretch (20 `P2-` commits).
3. **Phases 1,3,4,5,6,7** in `P{X}-{XX}` order (15× P3-, 10× P4-, 10× P1-, 5× P7-…).
4. **Phase 9 (A–G)** — heaviest TASKS phase (**64× `P9-`** commits): supplier domain, MFA, three-way matching, webhooks.
5. **Phase 10–11 remediation** (**43× `P11-`** commits): audit corrections, IAM gaps, refactors, i18n sweep.
6. **Post-TASKS era (`M{N} #{n}` commits)** — the project switches planning artifact to `docs/COMPLIANCE_MATRIX.md`: M7 (payments export), M11 (reporting trends/executive summary), M9 (retention/purge), M4/M6 (escalation/approval-limit), M13 #3 (department access), M14 #11 (archive compliance), M5 #1/#4 (dedicated matching page). The HEAD branch `fix/a1-cashflow-sqlgrammar` fixes a cash-flow SQL grammar bug and PROB-068 (matching list 500 on `lower(bytea)`).

**Interpretation for the thesis:** Development was plan-driven (TASKS.md) through Phase 11, then transitioned to a compliance-matrix-driven cadence. TASKS.md was frozen; COMPLIANCE_MATRIX.md is the true completion ledger.

---

## 2. STACK & VERSIONS

### Backend (`pom.xml`)
- **Java:** 21 (`java.version`, `maven.compiler.source/target`)
- **Spring Boot:** 3.4.1 (parent)
- **Spring Security** (starter, BOM-managed) — `@EnableWebSecurity`, `@EnableMethodSecurity`
- **JWT:** io.jsonwebtoken jjwt **0.12.6** (api/impl/jackson)
- **Rate limiting:** com.github.vladimir-bukhtoyarov bucket4j-core **7.6.0**
- **MFA TOTP:** dev.samstevens.totp **1.7.1**
- **DB driver:** PostgreSQL (runtime, BOM-managed)
- **Migrations:** Flyway core + flyway-database-postgresql (BOM-managed)
- **Workflow:** spring-statemachine-core & -data-jpa **4.0.0**
- **Object storage:** io.minio **8.5.13**
- **MIME detection:** Apache Tika core **2.9.2**
- **OCR:** net.sourceforge.tess4j **5.11.0**; org.apache.pdfbox **3.0.3**
- **Email/templating:** spring-boot-starter-mail + -thymeleaf
- **WebSocket:** spring-boot-starter-websocket
- **Excel export:** org.apache.poi poi-ooxml **5.3.0**
- **PDF export:** com.itextpdf itext-core **8.0.5**
- **Mapping/boilerplate:** MapStruct **1.6.3**, Lombok **1.18.36**
- **API docs:** springdoc-openapi-starter-webmvc-ui **2.7.0**
- **Build tooling:** Maven (wrapper `.mvn/`), maven-compiler-plugin, spring-boot-maven-plugin, maven-surefire-plugin, flyway-maven-plugin
- **Test:** spring-boot-starter-test (JUnit 5/Mockito/AssertJ), spring-security-test, Testcontainers junit-jupiter + postgresql **1.20.4**, H2 (test runtime)
- **Coverage:** jacoco-maven-plugin **0.8.12**

### Frontend (`frontend/package.json`)
- **React** 19.2.4, **react-dom** 19.2.4, **TypeScript** ~6.0.2, **Vite** 8.0.4
- **Routing:** react-router-dom 7.14.0
- **State/data:** @reduxjs/toolkit 2.11.2, react-redux 9.2.0, @tanstack/react-query 5.96.2
- **Forms/validation:** react-hook-form 7.72.1, @hookform/resolvers 5.2.2, zod 4.3.6
- **HTTP:** axios 1.14.0
- **WebSocket:** @stomp/stompjs 7.3.0, sockjs-client 1.6.1
- **i18n:** i18next 26.0.3, react-i18next 17.0.2, i18next-browser-languagedetector 8.2.1
- **Charts:** recharts 3.8.1 ; **PDF viewer:** react-pdf 9.2.1 ; **Icons:** lucide-react 1.7.0
- **Styling:** tailwindcss 3.4.19, tailwind-merge, class-variance-authority, tailwindcss-animate
- **Test:** vitest 4.1.2, @testing-library/react 16.3.2, jsdom; **E2E:** @playwright/test 1.59.1
- *(Note: `bcryptjs`, `pg`, `@types/pg` appear in devDeps — used by Playwright DB-seed/e2e helpers, not by the app bundle.)*

### Infra (`docker-compose.yml`, `.env`, `application.yaml`)
- **PostgreSQL:** **host-native PG 18 on port 5433**, db `oct_invoice` — *NOT* containerized (compose comment + `.env:12-17`). Test/dev default profile points at `oct_invoice_dev` on 5432 (`application.yaml:175,235`).
- **MinIO:** `minio/minio:latest`, console `minio/mc:latest` bucket-init, ports 9000/9001, bucket `oct-invoices`.
- **MailHog:** `mailhog/mailhog:latest`, SMTP 1025 / UI 8025 (dev email catcher).
- **Backend image:** built from `./Dockerfile` target `runtime`, port 8080.
- **Frontend image:** built from `./frontend/Dockerfile` (nginx), port 3000→80.

---

## 3. CHAPTER 1 + 2 CLAIM VERIFICATION

| # | Claim | Verdict | Proof (file:line) |
|---|---|---|---|
| a | OCR-assisted extraction (PDF/XML/image) | **VERIFIED** | `OcrService.java:60-94` (PDFBox text layer → Tess4J fallback; image OCR; XML structural parse at `:64-68`); `OcrController.java:28` `POST /api/v1/ocr/extract` |
| b | Duplicate detection at submission | **VERIFIED (heuristic)** | `InvoiceStateMachineServiceImpl.java:85-87,137-154` blocks on `countDuplicatesBySupplierAndDescription` (`InvoiceRepository.java:24-36`). Matches **supplier+description**, not exact invoice number → see R9 |
| c | Three-way matching (Invoice/PO/GRN) + configurable tolerance + override w/ justification | **VERIFIED** | `domain/purchasing/` (`ThreeWayMatchingResult`, `MatchingComparator`, `MatchingConfig` table); tolerance in DB (`MatchingConfigController.java:36-46`); override `InvoiceController.java:259-260` `POST /{id}/matching/override` (DAF/ADMIN) |
| d | Configurable multi-level routing + departmental matrix; sequential N1→N2 for IT/Infra/Workshop | **VERIFIED** | `StateMachineConfig.java:62-78` (N1→N2 vs N1→VALIDE via `DepartmentTransitionGuard.requiresN2`); matrix `ApprovalMatrixPage.tsx`; `ApprovalController.java:72-99` |
| e | MFA mandatory for finance/approval roles | **VERIFIED** | `AuthService.java:368-382` deny-list: mandatory for every non-SUPPLIER role; TOTP `MfaService.java:21-89`; two-step pre-auth `AuthController.java:84-102` |
| f | RBAC enforced at every endpoint | **VERIFIED** | `@EnableMethodSecurity` (`SecurityConfig.java:28`) + `@PreAuthorize` on every mapping (audited across all 39 controllers — Section 6) |
| g | AES-256 at rest for supplier bank details | **VERIFIED** | `EncryptionUtil.java:17-22` AES-256-GCM; `Invoice.java:79-81` `@Convert(EncryptionAttributeConverter)` on `supplier_bank_details`; `V35__encrypt_invoice_bank_details.sql` |
| h | TLS in transit (config evidence) | **PARTIAL** | `application.yaml:332-338` prod SSL `protocol: TLSv1.3`, but keystore externalized (`${SSL_KEYSTORE_PATH}`) and **not provided in repo**; dev/test run plain HTTP → R5 |
| i | Immutable, exportable audit trail | **VERIFIED** | `AuditLoggingFilter.java:45-57` persists writes/denials; append-only DB triggers `V25__enforce_append_only_logs.sql`; export `AuditController.java:125-126,184-185` |
| j | Real-time tracking dashboards per actor | **VERIFIED** | `DashboardPage.tsx`, `SupplierDashboardPage.tsx`; WS push `WebSocketConfig.java` + `useWebSocket` hook; report KPIs `ReportController.java:43-58` |
| k | Payment tracking with aging analysis | **VERIFIED** | `PaymentController.java`; aging `ReportController.java:112-113` `/reports/aging`; alert rules `PaymentAlertRuleController.java`. Aging present but basic → R10 |
| l | Digital archiving + metadata search + retention policy | **VERIFIED** | `ArchivePage.tsx`, `InvoiceController.java:272-273` `/invoices/archive`; retention `V62/V63`, `RetentionDispositionController.java`; MinIO storage. SHA-256 integrity claimed in PRD §Module4 — verify in `InvoiceDocument`/storage → R11 |
| m | Email + WebSocket notifications | **VERIFIED** | `domain/notification/`; 12 Thymeleaf templates `templates/email/*.html`; WS via STOMP |
| n | Reporting & analytics with export (PDF/Excel) | **VERIFIED** | `ReportController.java:64-97` Excel (POI) + PDF (iText); 24 report endpoints |
| o | Documented RESTful API for ERP/banking integration (webhooks) | **VERIFIED** | Swagger `application.yaml:144-152`; webhooks `WebhookController.java` HMAC-SHA256 (`WebhookService.java:64,260-264`); integration connectors `IntegrationConnectorController.java` |
| p | Supplier self-service portal (register/submit/track) | **VERIFIED** | `SupplierPortalController.java:48-240` (`/api/v1/supplier/*`, `hasRole('SUPPLIER')`); `AppRoutes.tsx:130-139`; self-register `AuthController.java:52` |

**Net:** 14 VERIFIED, 2 PARTIAL (h TLS, and the heuristic nature of b). No claim is fully NOT IMPLEMENTED. SHA-256 doc integrity (l) and aging depth (k) need confirmation/hardening — see roadmap.

---

## 4. DOMAIN MODEL (Chapter 3 class diagram)

47 `@Entity` classes (table / relationship counts / lifecycle flags). `audited` = `@EntityListeners(AuditingEntityListener.class)` (createdAt/updatedAt); `soft-del` = has `deleted_at`.

| Entity | Table | Relationships | Lifecycle |
|---|---|---|---|
| User | users | @ManyToOne(department-via-id), @OneToMany(userRoles) | soft-del, audited |
| Role | roles | — | audited |
| UserRole | user_roles | 3× @ManyToOne (user, role, assignedBy) | audited |
| Department | departments | — | audited |
| Invoice | invoices | 3× @ManyToOne (department, submittedBy:User, supplier), 2× @OneToMany (items, documents) | **soft-del, audited, @Version (optimistic lock)** |
| InvoiceItem | invoice_items | @ManyToOne(invoice) | — |
| InvoiceDocument | invoice_documents | 3× @ManyToOne | — |
| InvoiceStatusHistory | invoice_status_history | 2× @ManyToOne (invoice, changedBy) | — |
| DocumentAccessLog | document_access_log | 2× @ManyToOne | — |
| ApprovalStep | (no @Table — default `approval_step`) | 2× @ManyToOne | — |
| ApprovalDelegation | approval_delegations | 3× @ManyToOne (delegator, delegatee, createdBy) | audited |
| EscalationRule | escalation_rules | @ManyToOne | audited |
| Supplier | suppliers | @ManyToOne(user link) | soft-del, audited |
| SupplierContract | supplier_contracts | — | — |
| SupplierCommunication | supplier_communications | — | — |
| SupplierDocument | supplier_documents | 2× @ManyToOne | audited |
| PurchaseOrder | purchase_orders | 2× @ManyToOne, @OneToMany(items) | soft-del, audited |
| PurchaseOrderItem | purchase_order_items | @ManyToOne | audited |
| GoodsReceiptNote | goods_receipt_notes | 2× @ManyToOne, @OneToMany(items) | soft-del, audited |
| GoodsReceiptItem | goods_receipt_items | 2× @ManyToOne | audited |
| ThreeWayMatchingResult | three_way_matching_results | 4× @ManyToOne (invoice, po, grn, performedBy) | audited (append-only via `V27`) |
| MatchingConfig | matching_config | @ManyToOne | audited |
| Payment | payments | @ManyToOne(invoice), @OneToOne(remittance) | audited |
| RemittanceAdvice | remittance_advice | @ManyToOne, @OneToOne | audited |
| PaymentAlertRule | payment_alert_rules | @ManyToOne | audited |
| Notification | notifications | 2× @ManyToOne | audited |
| AuditLog | audit_logs | @ManyToOne(user, on-delete-set-null via `V42`) | — (append-only via `V25`) |
| ActiveSession | active_sessions | @ManyToOne(user) | audited |
| SecurityPolicy | security_policy | @ManyToOne(updatedBy) | audited |
| AccessRequest | access_requests | 2× @ManyToOne | audited |
| Announcement | announcements | @ManyToOne | — |
| Webhook | webhooks | @ManyToOne | audited |
| WebhookDelivery | webhook_deliveries | @ManyToOne(webhook) | audited (append-only) |
| IntegrationConnector | integration_connectors | — | — |
| ReportDefinition | report_definitions | — | — |
| RetentionPolicy | retention_policy | @ManyToOne | audited |
| ChecklistTemplate | checklist_templates | @ManyToOne, @OneToMany(items) | audited |
| ChecklistTemplateItem | checklist_template_items | @ManyToOne | — |
| ChecklistResponse | checklist_responses | 2× @ManyToOne, @OneToMany(items) | audited |
| ChecklistResponseItem | checklist_response_items | @ManyToOne | — |
| SecurityIncident | security_incidents | — | — |
| ComplianceChecklistItem | compliance_checklist_items | — | — |
| ComplianceCalendarEntry | compliance_calendar | — | — |
| BackupStatus | backup_status | — | — |
| PrivacyPolicyAcceptance | privacy_policy_acceptances | — | — |

**Invoice key fields** (`Invoice.java`): `id UUID` (PK), `referenceNumber` (unique, len 20), `department`/`submittedBy`/`supplier` (FK), flat `supplierName/Email/TaxId`, `supplierBankDetails` (AES-GCM `@Convert`), `amount` (numeric 15,2), `currency` (default XAF), `issueDate`, `dueDate`, `status` (enum `InvoiceStatus`), `dataSensitivity` (enum, default INTERNAL), `version` (`@Version`), `createdAt/updatedAt/deletedAt`, `purchaseOrderId`, `matchingStatus` (String). For per-field detail of remaining entities, read `domain/**/model/*.java` (each is small and Lombok-annotated).

---

## 5. DATABASE (Chapter 3 ERD + data dictionary)

### 5.1 Flyway migration ledger (V1–V63, gap at V36–V38)

| Version | File | Effect |
|---|---|---|
| V1 | create_users_roles | `users`, `roles` tables |
| V2 | create_departments | `departments` |
| V3 | seed_roles_and_admin | seed roles + admin user |
| V4 | create_invoices | `invoices` |
| V5 | create_invoice_items | `invoice_items` |
| V6 | create_invoice_documents | `invoice_documents` |
| V7 | create_approval_steps | `approval_steps` |
| V8 | create_invoice_status_history | `invoice_status_history` |
| V9 | create_notifications | `notifications` |
| V10 | create_payments | `payments` |
| V11 | create_audit_logs | `audit_logs` |
| V12 | add_indexes | indexes on `invoices(status)` etc. |
| V13 | create_suppliers | `suppliers` |
| V14 | update_invoices_supplier_fk | add `supplier_id` FK to invoices |
| V15 | add_supplier_user_link | link `users`↔supplier |
| V16 | add_role_supplier | seed `ROLE_SUPPLIER` |
| V17 | create_purchase_orders | `purchase_orders` |
| V18 | create_goods_receipt_notes | `goods_receipt_notes` |
| V19 | create_three_way_matching | `three_way_matching_results` |
| V20 | create_remittance_advice | `remittance_advice` |
| V21 | add_matching_columns_to_invoices | `purchase_order_id`, `matching_status` |
| V22 | create_webhooks | `webhooks`, `webhook_deliveries` |
| V23 | seed_required_workflow_roles | seed N1/N2 dept roles |
| V24 | add_password_reset_tokens | reset-token cols on users |
| V25 | enforce_append_only_logs | **triggers blocking UPDATE/DELETE on `audit_logs`** |
| V26 | add_encrypted_webhook_secret | (later reversed) |
| V27 | make_matching_results_append_only | triggers on `three_way_matching_results` |
| V28 | add_staff_profile_fields | staff profile cols |
| V29 | enforce_financial_retention | trigger blocking delete of financial invoices |
| V30 | remove_webhook_secret_encrypted | drop `secret_encrypted` |
| V31 | fix_finance_approver_and_remove_auditeur | DAF=Finance N1; **remove ROLE_AUDITEUR** |
| V32 | seed_test_users_all_roles | seed test users (all roles) |
| V33 | remove_phantom_roles | delete stray roles |
| V34 | fix_users_is_active | normalize `is_active` |
| V35 | encrypt_invoice_bank_details | migrate bank details to AES |
| *(V36–V38)* | **— absent —** | numbering gap (explain in thesis) |
| V39 | create_active_sessions | `active_sessions` |
| V40 | create_approval_delegations | `approval_delegations` |
| V41 | increase_active_sessions_refresh_token_length | widen refresh token col |
| V42 | audit_logs_user_fk_on_delete_set_null | FK ON DELETE SET NULL |
| V43 | add_invoices_supplier_id_index | index |
| V44 | create_security_policy | `security_policy` |
| V45 | security_policy_updated_by_nullable | nullable updated_by |
| V46 | add_invoices_data_sensitivity | `data_sensitivity` col |
| V47 | create_access_requests | `access_requests` |
| V48 | create_document_access_log | `document_access_log` |
| V49 | add_departments_budget | `budget` col |
| V50 | widen_mfa_secret | widen `mfa_secret` (for AES-GCM blob) |
| V51 | create_announcements | `announcements` |
| V52 | create_supplier_contracts_and_comms | `supplier_contracts`, `supplier_communications` |
| V53 | add_invoice_document_versioning | doc version cols |
| V54 | create_report_definitions | `report_definitions` |
| V55 | create_integration_connectors | `integration_connectors` |
| V56 | create_compliance | `security_incidents`, `compliance_*`, `backup_status`, `privacy_policy_acceptances` |
| V57 | add_supplier_category | `category` col on suppliers |
| V58 | create_validation_checklists | `checklist_templates/_items`, `checklist_responses/_items` |
| V59 | create_payment_alert_rules | `payment_alert_rules` |
| V60 | add_connector_sync_schedule | sync-interval cols |
| V61 | create_escalation_rules | `escalation_rules` |
| V62 | create_retention_policy | `retention_policy` |
| V63 | add_retention_disposition | disposition enum + cols on `invoice_documents` |

### 5.2 Data-dictionary tables (paste-ready)

Authoritative column detail lives in the migration SQL. For Chapter 3, generate per-table dictionaries by reading each `Vn__*.sql` — the key central table is reproduced here as the template; replicate the format for the other 40+ tables.

**Table `invoices`** (`V4` + `V14`,`V21`,`V35`,`V46`)

| Column | SQL type | Null | Default | Key / Notes |
|---|---|---|---|---|
| id | uuid | NO | gen | PK |
| reference_number | varchar(20) | NO | — | UNIQUE (`FAC-{YEAR}-{NNNNN}`) |
| department_id | uuid | NO | — | FK → departments |
| submitted_by | uuid | NO | — | FK → users |
| supplier_id | uuid | YES | — | FK → suppliers (nullable, backward-compat) |
| supplier_name | varchar(255) | NO | — | flat copy |
| supplier_email | varchar(255) | NO | — | flat copy |
| supplier_tax_id | varchar(100) | YES | — | |
| supplier_bank_details | text | YES | — | **AES-256-GCM ciphertext** (`GCM:iv:cipher`) |
| amount | numeric(15,2) | NO | — | |
| currency | varchar(3) | NO | 'XAF' | |
| issue_date | date | NO | — | |
| due_date | date | NO | — | |
| description | text | YES | — | |
| status | varchar(30) | NO | 'BROUILLON' | enum InvoiceStatus |
| data_sensitivity | varchar(20) | NO | 'INTERNAL' | enum (`V46`) |
| version | int | NO | 0 | optimistic lock |
| created_at | timestamptz | NO | now | audit |
| updated_at | timestamptz | NO | now | audit |
| deleted_at | timestamptz | YES | — | soft delete |
| purchase_order_id | uuid | YES | — | FK → purchase_orders (`V21`) |
| matching_status | varchar(20) | YES | — | (`V21`) |

*Append-only enforcement:* `audit_logs` (`V25`), `three_way_matching_results` (`V27`); *retention guard:* `invoices` financial delete blocked (`V29`).

---

## 6. API SURFACE (Chapter 3 use cases / Chapter 4 listings)

All paths prefixed `/api/v1`. Roles from `@PreAuthorize`. (39 controllers; grouped.)

### Auth & MFA — `AuthController`
| Method | Path | Role | Purpose |
|---|---|---|---|
| POST | /auth/login | permitAll | login; may return `mfaRequired` + pre-auth token |
| POST | /auth/refresh | permitAll | refresh JWT |
| POST | /auth/register/supplier | permitAll | supplier self-register |
| GET | /auth/verify-email | permitAll | email verification |
| POST | /auth/forgot-password | permitAll | request reset |
| POST | /auth/reset-password | permitAll | reset with token |
| POST | /auth/mfa/setup | isAuthenticated | begin TOTP enrolment (QR) |
| POST | /auth/mfa/confirm | isAuthenticated | confirm TOTP |
| POST | /auth/mfa/validate | permitAll | submit OTP w/ pre-auth token → full JWT |

### Invoices — `InvoiceController` (`/invoices`)
| Method | Path | Role |
|---|---|---|
| GET | / | auth, not SUPPLIER/ADMIN |
| GET | /export | auth, not SUPPLIER/ADMIN |
| GET | /{id} | auth, not SUPPLIER/ADMIN |
| GET | /pending-validation | ADMIN/DAF/ASSISTANT_COMPTABLE (+ N1/N2 authorities) |
| GET | /{id}/matching | auth, not SUPPLIER/ADMIN |
| GET | /{id}/matching/export | auth, not SUPPLIER/ADMIN |
| GET | /{id}/history | auth, not SUPPLIER/ADMIN |
| POST | / | ASSISTANT_COMPTABLE |
| POST | /import | ASSISTANT_COMPTABLE |
| PUT | /{id} | ASSISTANT_COMPTABLE |
| PATCH | /{id}/sensitivity | DAF/ASSISTANT_COMPTABLE |
| DELETE | /{id} | ASSISTANT_COMPTABLE (soft) |
| POST | /{id}/submit | ASSISTANT_COMPTABLE |
| POST | /{id}/resubmit | ASSISTANT_COMPTABLE |
| POST | /{id}/matching/override | DAF/ADMIN |
| GET | /archive | ADMIN/DAF/ASSISTANT_COMPTABLE |
| GET | /{id}/export/pdf | auth, not SUPPLIER |

### Invoice documents / checklist — `InvoiceDocumentController`, `InvoiceChecklistController`
POST `/invoices/{id}/documents` (+`/bulk`) ASSISTANT_COMPTABLE; GET list & `/{docId}/download` isAuthenticated; checklist GET/POST auth & not SUPPLIER.

### Workflow — `ApprovalController` (`/invoices/{invoiceId}/workflow`)
| Method | Path | Role |
|---|---|---|
| GET | /rejection-reasons | auth, not SUPPLIER |
| GET | /steps | auth, not SUPPLIER |
| POST | /assign | ASSISTANT_COMPTABLE/DAF/ADMIN (+authorities) |
| POST | /validate-n1 | DAF (+ N1 authorities) |
| POST | /validate-n2 | N2 authorities (INFO/INFRA/TECH) |
| POST | /bon-a-payer | DAF/ADMIN |
| POST | /reject | DAF/ADMIN (+ N1/N2 authorities) |

### Delegations / Escalation / Validator stats
`DelegationController` (`/approvals/delegations`) ADMIN + approver self-service; `EscalationRuleController` (`/escalation-rules`) ADMIN/DAF; `ValidatorStatsController` (`/workflow/my-stats`) auth not SUPPLIER.

### Purchasing & Matching
`PurchaseOrderController` (`/purchase-orders`) ADMIN/ASSISTANT_COMPTABLE(/DAF read); `GoodsReceiptController` (`/goods-receipts`); `MatchingConfigController` (`/matching-config`, write ADMIN); `MatchingQueryController` (`/matching`, list + `/{invoiceId}/lines`).

### Payments
`PaymentController` (`/payments`) record/batch ASSISTANT_COMPTABLE; reads ASSISTANT_COMPTABLE/DAF/ADMIN; remittance + export. `PaymentAlertRuleController` DAF/ASSISTANT_COMPTABLE.

### Suppliers & Portal
`SupplierController` (`/suppliers`) ADMIN/ASSISTANT_COMPTABLE(/DAF read), activate/suspend, performance, documents; `SupplierRelationshipController` contracts/communications; `SupplierPortalController` (`/supplier/*`) `hasRole('SUPPLIER')`: submit/list/resubmit invoices, profile, dashboard, documents.

### Reports — `ReportController` (`/reports`, all DAF/ASSISTANT_COMPTABLE)
kpis, summary, activity, export/excel, export/pdf/audit/{id}, export/pdf/compliance, aging, cash-flow, supplier/{id}/payments, bottlenecks, supplier/{id}/performance, budget-vs-actual, budget-alerts, definitions (CRUD+run+preview), volume-trend, executive-summary.

### Audit — `AuditController` (`/audit-logs`)
/system (ADMIN), /financial (DAF), / (ADMIN|DAF), /anomalies (ADMIN), /export (ADMIN|DAF), /summary/{system,financial,export}. **Separation of duties: ADMIN never sees financial; DAF never sees system.**

### Admin/config
`UserController` (`/users`, ADMIN — incl. unlock, mfa/reset, import/export CSV), `RoleController` (ADMIN), `UserProfileController` (`/profile`, isAuthenticated), `AdminSessionController` (`/admin/sessions`, ADMIN), `SecurityPolicyController` (`/admin/security-policy`, ADMIN), `DepartmentController`, `DepartmentAccessController` (`/admin/department-access`, ADMIN), `AnnouncementController`, `AccessRequestController`, `ComplianceController` (`/compliance`, mostly ADMIN), `RetentionDispositionController` (ADMIN), `ChecklistTemplateController` (ADMIN).

### Integration — `WebhookController`, `IntegrationConnectorController`, `IntegrationStatusController` (all `/integrations/*`, ADMIN)
Webhooks CRUD + deliveries; connectors CRUD + test + sync-schedule + sync + enable; status.

### Notifications & OCR
`NotificationController` (`/notifications`, isAuthenticated): list, unread-count, read, read-all. `OcrController` (`/ocr/extract`, SUPPLIER/ASSISTANT_COMPTABLE/ADMIN).

---

## 7. WORKFLOW STATE MACHINE (Chapter 3 activity + sequence)

From `StateMachineConfig.java:48-117`. Initial = `BROUILLON`, end = `ARCHIVE`.

| From | Event | To | Guard | Role (endpoint) |
|---|---|---|---|---|
| BROUILLON | SUBMIT | SOUMIS | `documentRequiredGuard` | ASSISTANT_COMPTABLE / SUPPLIER (portal) |
| SOUMIS | ASSIGN_REVIEWER | EN_VALIDATION_N1 | `roleMatchGuard` | ASSISTANT_COMPTABLE/DAF/ADMIN |
| EN_VALIDATION_N1 | VALIDATE_N1 | EN_VALIDATION_N2 | `requiresN2(ctx) && roleMatch` | DAF / N1 authority |
| EN_VALIDATION_N1 | VALIDATE_N1 | VALIDE | `isSingleLevel(ctx) && roleMatch` | DAF / N1 authority |
| EN_VALIDATION_N2 | VALIDATE_N2 | VALIDE | `roleMatchGuard` | N2 authority (INFO/INFRA/TECH) |
| VALIDE | BON_A_PAYER | BON_A_PAYER | `roleMatchGuard` | DAF/ADMIN |
| BON_A_PAYER | RECORD_PAYMENT | PAYE | — | ASSISTANT_COMPTABLE |
| PAYE | ARCHIVE | ARCHIVE | — | system (auto) |
| EN_VALIDATION_N1 | REJECT | REJETE | `rejectionReason && roleMatch` | DAF/ADMIN/N1 |
| EN_VALIDATION_N2 | REJECT | REJETE | `rejectionReason && roleMatch` | N2 |
| VALIDE | REJECT | REJETE | `rejectionReason && roleMatch` | DAF/ADMIN |
| REJETE | RESUBMIT | SOUMIS | — | ASSISTANT_COMPTABLE / SUPPLIER |

**N2 departments** (`DepartmentTransitionGuard`): Informatique, Infrastructure, Atelier/Direction Technique. Guards: `DocumentRequiredGuard` (≥1 attachment before submit), `RoleMatchGuard` (actor role ∈ allowed for transition), `RejectionReasonGuard` (mandatory reason), `DepartmentTransitionGuard` (1-level vs 2-level routing).
*Note:* the state machine has no `VALIDE→REJETE` at the AA-validation stage explicitly (rejection at SOUMIS is via assign/reject path); confirm against thesis activity diagram.

---

## 8. SECURITY IMPLEMENTATION (Chapter 4)

| Control | File:line | Summary (2 lines) |
|---|---|---|
| JWT issuance/refresh | `JwtService.java:78,100-105,117` | RS256 (RSA-2048) signing with PKCS#8 private key; access 24h / refresh 7d (`application.yaml:85-86`); claims include roles, supplierId, departmentId. |
| MFA TOTP | `MfaService.java:21-89`; `AuthService.java:95-173,288-311` | dev.samstevens.totp, SHA1, 6 digits, 30s period, ±1 window; two-step login (pre-auth token 5 min) then OTP validate. |
| Password hashing | `SecurityConfig.java:90-92` | BCrypt strength 12 via `BCryptPasswordEncoder`. |
| AES-256 encryption | `EncryptionUtil.java:17-55`; `EncryptionAttributeConverter` | AES/GCM/NoPadding, 256-bit key, random 12-byte IV, 128-bit tag, stored `GCM:iv:cipher`; applied to bank details + MFA secret. |
| RBAC enforcement | `SecurityConfig.java:28,55-71` + `@PreAuthorize` everywhere | `@EnableMethodSecurity`; stateless; permitAll only for auth/swagger/actuator/ws; method-level role checks on every endpoint. |
| Input validation | `spring-boot-starter-validation`; DTO `@Valid`; `GlobalExceptionHandler` (`shared/exception/`) | Bean Validation on request DTOs; centralized error mapping to i18n messages. |
| Audit logging filter | `AuditLoggingFilter.java:23-119` | OncePerRequestFilter persists all non-GET + 401/403 to `audit_logs` with action/entity classification, IP, UA; failures never break response. |
| File MIME validation | `InvoiceDocumentService.java`, `OcrService.java:61` | Apache Tika content-type detection before storage; OCR re-detects to route PDF/image/XML. |
| Account lockout | `AuthService.java:384-398`; `User.java:117-122,170` | Failed-attempt counter; locks `locked_until` after `securityPolicy.maxLoginAttempts` (configurable) for `ACCOUNT_LOCK_MINUTES`; ADMIN unlock endpoint. |
| Rate limiting | `RateLimitingFilter.java` (bucket4j 7.6.0) | Token-bucket throttling on auth endpoints. |
| Security headers | `HttpSecurityHeadersFilter.java` | Adds standard hardening headers pre-auth. |
| WS auth | `WebSocketAuthChannelInterceptor.java` | JWT validated on STOMP CONNECT frame (handshake permitAll at HTTP layer). |
| Prod secret guard | `ProdSecretConfigValidator.java` | Fails startup in prod if required secrets are absent. |

---

## 9. FRONTEND PAGE INVENTORY (Chapter 4 screenshots)

From `AppRoutes.tsx`. Guards: `ProtectedRoute` (staff) / `SupplierRoute` (suppliers). Page-level role gating via `PageRoleGuard` inside components. Capture FR locale by default.

**Public:** `/login` (LoginPage — incl. OTP second step), `/register` (RegisterPage), `/forgot-password`, `/reset-password`, `/register/supplier` (SupplierRegisterPage), `/verify-email`.

**Staff (ProtectedRoute + AppShell):**
| Route | Component | Roles (effective) | Screenshot |
|---|---|---|---|
| /dashboard | DashboardPage | all staff | role-aware KPI dashboard |
| /profile | ProfilePage | all | profile + MFA setup + language switcher |
| /access-requests | MyAccessRequestsPage | staff | my access requests |
| /my-delegations | MyDelegationsPage | approvers | delegation list |
| /invoices, /invoices/new, /invoices/:id | InvoiceList/Create/Detail | AA + approvers (not SUPPLIER/ADMIN) | list, create form, detail w/ timeline + action panel |
| /approvals | ApprovalQueuePage | approvers | pending validation queue |
| /financial-audit | FinancialAuditPage | DAF | financial audit trail |
| /purchase-orders | PurchaseOrdersPage | AA/DAF/ADMIN | PO list |
| /goods-receipts | GoodsReceiptsPage | AA/DAF/ADMIN | GRN list |
| /matching, /matching/:invoiceId | MatchingList/Detail | not SUPPLIER/ADMIN | 3-way match list + line-by-line |
| /reports, /reports/builder | Reports/ReportBuilder | DAF/AA | charts, exports, saved report builder |
| /payments, /payments/alert-rules | Payments/PaymentAlertRules | AA/DAF/ADMIN | payment register + aging, alert rules |
| /notifications | NotificationsPage | all | notification center |
| /archive | ArchivePage | AA/DAF/ADMIN | searchable archive |
| /admin/users(+/new) | AdminUsers/Form | ADMIN | user CRUD, unlock, MFA reset |
| /admin/permissions | AdminPermissionMatrixPage | ADMIN | permission matrix |
| /admin/access-requests | AdminAccessRequestsPage | ADMIN | approve access requests |
| /admin/announcements | AdminAnnouncementsPage | ADMIN | announcements |
| /admin/compliance | AdminCompliancePage | ADMIN | incidents/checklist/calendar/backup |
| /admin/departments(+/new) | AdminDepartments/Form | ADMIN | dept CRUD + budget |
| /admin/audit | AdminAuditPage | ADMIN | system/security audit |
| /admin/approval-matrix | ApprovalMatrixPage | ADMIN | dept→approver matrix |
| /admin/delegations | AdminDelegationsPage | ADMIN | all delegations |
| /admin/matching-config | AdminMatchingConfigPage | ADMIN | tolerance thresholds |
| /admin/checklist-templates | AdminChecklistTemplatesPage | ADMIN | checklist templates |
| /admin/escalation-rules | EscalationRulesPage | ADMIN/DAF | SLA escalation rules |
| /admin/retention-policy | AdminRetentionPolicyPage | ADMIN | retention policy |
| /admin/archive-compliance | AdminArchiveCompliancePage | ADMIN | archive compliance report |
| /admin/retention-disposition | AdminRetentionDispositionPage | ADMIN | purge/retain controls |
| /admin/security | SecuritySettingsPage | ADMIN | security policy + health |
| /admin/integrations | IntegrationsPage | ADMIN | webhooks + connectors + status |
| /admin/department-access | DepartmentAccessPage | ADMIN | read-only access-by-dept |
| /admin/suppliers(+/new,/:id,/:id/edit) | Suppliers/Detail/Form | ADMIN/AA/DAF | supplier CRUD, performance, docs |

**Supplier (SupplierRoute + SupplierLayout):** /supplier/dashboard, /supplier/invoices, /supplier/invoices/new, /supplier/profile, /supplier/documents.

**Non-page components worth capturing:** NotificationDropdown, DocumentUploader, InvoiceTimeline, InvoiceActionPanel, MFA setup (in ProfilePage), language switcher (AppShell header), DocumentViewer (react-pdf zoom/rotate).

---

## 10. TESTS INVENTORY (Chapter 4 testing section)

- **Backend test files:** 85 (`src/test`). **`@Test` methods:** **492**.
- **Frontend unit (vitest):** specs under `frontend/src/test/**` and `pages/admin/__tests__` (≈67 passing per memory ledger).
- **E2E (Playwright):** 3 specs — `frontend/e2e/bap-single-level.spec.ts` (single-level BAP happy path), `bap-two-level.spec.ts` (N1→N2 path), `security-audit.spec.ts` (RBAC 403 assertions).
- **Key backend integration/unit tests by name:**
  - `StateMachineTransitionExhaustiveTest` — every transition + guard (exhaustive).
  - `InvoiceStateMachineServiceTest` — submit, duplicate check, matching trigger.
  - Controller slice/integration tests per domain (auth, invoice, payment, supplier, webhook, report, audit) asserting `@PreAuthorize` enforcement (Testcontainers PostgreSQL).
- **Coverage (JaCoCo, `target/site/jacoco/jacoco.csv`):** **Instructions 66.8 %** (22 031/32 979), **Branches 52.8 %** (1 130/2 140), **Lines 4 348 covered / 1 878 missed**. *(The `index.html` "37 %" is a stale partial run; the CSV is the full figure.)* To regenerate: `./mvnw test jacoco:report` (requires a reachable PostgreSQL on 5432 `oct_invoice_dev`).

---

## 11. NON-VERSIONED CONFIG (values redacted)

| File | In VCS? | Purpose | Keys present (values redacted) |
|---|---|---|---|
| `.env` | No (.gitignore) | runtime secrets for docker-compose | APP_PORT, FRONTEND_PORT, SPRING_PROFILES_ACTIVE, DB_HOST/PORT/NAME/USER, DB_PASSWORD=`***REDACTED***`, MINIO_ENDPOINT/ACCESS_KEY, MINIO_SECRET_KEY=`***REDACTED***`, MINIO_BUCKET/CONSOLE_PORT, JWT_PRIVATE_KEY=`***REDACTED***`, JWT_PUBLIC_KEY=`***REDACTED***`, JWT_EXPIRATION_MS, JWT_REFRESH_EXPIRATION_MS, ENCRYPTION_KEY=`***REDACTED***`, MAIL_HOST/PORT, MAIL_USERNAME=`***REDACTED***`, MAIL_PASSWORD=`***REDACTED***`, MAIL_FROM/_NAME, MAILHOG_*, VITE_API_BASE_URL, VITE_WS_URL |
| `src/main/resources/application.yaml` | Yes | Spring profiles (base/dev/test/prod) | jpa.ddl-auto=validate, flyway.*, jwt.{private-key,public-key,expiration-ms,refresh,pre-auth}=env refs, encryption.key=env ref, ocr.{tessdata-path,language}, minio.*, management.health.mail.enabled=false, server.ssl(prod)={protocol TLSv1.3, key-store=`${SSL_KEYSTORE_PATH}`, key-store-password=`***REDACTED***`}. **Note:** test profile embeds a hard-coded RSA test key pair + `encryption.key: TestEncryptionKey...` (`application.yaml:259-266`) — acceptable for tests but should be flagged as non-prod. |
| `src/test/resources/application-test.yml` | Yes | test overrides | datasource (localhost:5432 oct_invoice_dev, user postgres, password=`***REDACTED***`) |
| `CLAUDE.md` | Yes | AI-agent directives | project identity, mandatory reading order, architecture/security/quality constraints, BAP workflow rules, MFA/matching/webhook constraints, bug-prevention rules (PROB-001…013) |
| `docs/MEMORY.md` | Yes (101 KB) | session-checkpoint ledger | per-task checkpoints, last commit, blockers; **not secrets** |
| `.cursor/rules/` , `.github/`, `.superpowers/` | Yes | IDE/agent rules, workflows, SDD | cursor rules, GH java-upgrade helper, superpowers SDD specs/plans |
| `qa-audit/`, `.playwright-mcp/`, root `*.png/*.jpeg` | mixed | QA screenshots / MCP artifacts | non-config images |

**Secret-hygiene findings for the thesis security chapter:** the dev `.env` ships a real RSA key pair, a 32-char AES key, and DB/MinIO passwords in plaintext (dev defaults), and `pom.xml` flyway plugin hard-codes `password=dany`. These are dev conveniences but should be called out as "must be replaced by a secrets manager in production" (the code already supports env-injection in the prod profile). → **R6**.

---

## 12. COMPLETION ROADMAP — what remains to do

Ordered by priority then dependency. Every gap from Sections 1, 1bis, 3 is included.

| ID | Source | What is missing / partial | Why needed | Suggested implementation | Effort | Prio | Deps |
|---|---|---|---|---|---|---|---|
| **R1** | Thesis Ch.1/2 "7 actors" vs PRD §3 "6 roles" | The locked brief lists an **Auditeur** actor; code removed it (`V31`,`V33`), splitting audit between DAF & ADMIN | Thesis truthfulness — a class/use-case diagram showing 7 actors would not match the system | **Decision required (you):** (a) re-introduce `ROLE_AUDITEUR` as a read-only audit role → new migration `V64__reintroduce_auditeur_role.sql`, seed user, add `@PreAuthorize hasAnyRole('AUDITEUR',...)` to `AuditController` read endpoints, frontend route `/audit (read-only)`, tests; **OR** (b) amend the thesis text to 6 roles with a one-paragraph justification of the DAF/ADMIN split. Recommend (b) if Ch.1/2 are truly locked but you can footnote; (a) if the diagram must show 7. | (a) M / (b) S | **P0** | — |
| **R2** | §1 false-positive | No build-and-test **CI** for this app (P10-C ticked but absent) | Ch.4 "DevOps/CI" claim must be backed | Add `.github/workflows/ci.yml`: jobs `backend` (`./mvnw -B verify`, Testcontainers PG) and `frontend` (`npm ci && npm run lint && npm run test && npm run build`); upload JaCoCo + Playwright artifacts | S | **P0** | — |
| **R3** | §3(h) PARTIAL | **TLS** is prod-config only; no keystore, dev/test plain HTTP; no evidence artifact | Ch.1/2 "secure / TLS in transit" claim needs proof | Generate self-signed `keystore.p12` (script in `docs/`), document `SSL_KEYSTORE_PATH/PASSWORD`, add a profile or nginx TLS termination, capture a `curl -v https://` / browser-lock screenshot for Ch.4 | S | **P0** | — |
| **R4** | §3(l), PRD Module 4 | **SHA-256 document integrity** claimed but not confirmed in code | Archiving claim ("SHA-256 integrity check") must exist | Verify/Add: compute SHA-256 on upload in `InvoiceDocumentService`, store `checksum` column (`V64`/`V65`), verify on download, surface in UI; unit test | M | **P0** | — |
| **R5** | §1bis(d) | Migration **gap V36–V38** undocumented | A data dictionary with a numbering gap invites a viva question | Add a one-line note in `docs/DATABASE.md` and thesis Ch.3 explaining the squashed/abandoned versions; no code change | S | P1 | — |
| **R6** | §11 | Dev secrets (RSA key, AES key, DB/MinIO pw, flyway `password=dany`) committed as defaults | Security chapter credibility | Move flyway creds to env in `pom.xml`; add a `docs/SECURITY.md` "secrets management" section; confirm prod profile uses only env refs (it does). Rotate the embedded dev key before any public submission | S | P1 | — |
| **R7** | §1bis(a) | ~20 implemented modules have **no design coverage** in TASKS/thesis | Ch.3 must describe what the code does | For each undocumented module (compliance, retention, escalation, announcements, contracts, connectors, report-builder, alert-rules, checklists, access-requests, delegation, sessions), add a short design subsection + include their entities in the ERD (already enumerated in Section 4/5) | L | P1 | — |
| **R8** | §3(b) drift | **Duplicate detection** is supplier+description, not invoice-number | Thesis likely claims duplicate-invoice detection; current heuristic is weaker | Strengthen: add `(supplier_id, supplier_invoice_number)` uniqueness check; new column if needed (`V64`), update `performDuplicateCheck`, tests; or document the heuristic precisely in Ch.4 | M | P1 | — |
| **R9** | §3(k) | **Aging analysis** is basic (`/reports/aging`) | "Aging analysis" is a named Ch.1/2 feature | Add bucketed aging (0-30/31-60/61-90/90+), per-supplier rollup, chart in ReportsPage; backend service method + endpoint test | M | P1 | R7 |
| **R10** | §1 false-positive | **OWASP ZAP** scan (P10-E) ticked, no artifact | If thesis cites a security scan, an artifact is expected | Run ZAP baseline against the running app, commit `docs/audit/zap-report.html`, summarize findings in Ch.4; or remove the claim | S | P1 | R2,R3 |
| **R11** | §1 false-positive | No root **README** (P8-08 ticked) | Submission polish / reproducibility | Write `invoice-system/README.md`: stack, `docker-compose up` + host-PG note, profiles, test commands, default credentials | S | P2 | — |
| **R12** | NFR PRD §7 | **WCAG 2.1 AA** accessibility unverified | NFR claim | Run axe/Lighthouse a11y pass on key pages, fix top issues (labels, contrast, focus), note results in Ch.4 | M | P2 | — |
| **R13** | §7 note | State machine lacks explicit **AA-stage rejection** transition from validation start | Activity diagram completeness | Confirm rejection-at-SOUMIS path; if business requires AA reject before N1, add transition + guard + test | S | P2 | — |
| **R14** | §3(m)/infra | Email in dev relies on MailHog; prod Gmail SMTP untested | Notifications NFR | Document prod SMTP setup + a screenshot of a delivered email for Ch.4 | S | P2 | — |

**Suggested execution order (1–3 sprints):**
- **Sprint 1 (truthfulness, P0):** R1 (decide roles) → R2 (CI) → R3 (TLS) → R4 (SHA-256 integrity). These remove every claim that would otherwise be untrue in the thesis.
- **Sprint 2 (credibility, P1):** R5 (doc the gap) → R6 (secrets) → R7 (design coverage for undocumented modules) → R8 (duplicate detection) → R9 (aging) → R10 (ZAP). R7 is the largest; start it early in the sprint and run in parallel with the smaller items.
- **Sprint 3 (polish, P2):** R11 (README) → R12 (WCAG) → R13 (workflow edge) → R14 (email proof).

---

## 13. CONSOLIDATED GAP TABLE

| Thesis claim | Code reality | Action |
|---|---|---|
| 7 actors incl. Auditeur | 6 roles; Auditeur removed, audit split DAF/ADMIN (`V31`,`V33`; PRD §3) | **R1** |
| CI/CD pipeline | none for the app (only a java-upgrade helper) | **R2** |
| TLS in transit | prod-profile config only, no keystore, dev plain HTTP (`application.yaml:332-338`) | **R3** |
| SHA-256 document integrity (archiving) | claimed in PRD; not confirmed in `InvoiceDocumentService`/storage | **R4** |
| Complete, contiguous schema history | V36–V38 missing (numbering gap) | **R5** |
| Secure secrets handling | dev RSA/AES/DB secrets committed as defaults; flyway pw hard-coded | **R6** |
| Designed feature set = built feature set | ~20 modules built beyond TASKS/thesis design coverage | **R7** |
| Duplicate invoice detection | heuristic on supplier+description, not invoice number | **R8** |
| Payment aging analysis | basic `/reports/aging`, no buckets | **R9** |
| OWASP security scan performed | P10-E ticked, no artifact | **R10** |
| README / reproducible setup | no root README (P8-08 ticked) | **R11** |
| WCAG 2.1 AA | unverified | **R12** |
| Rejection at every review stage | N1/N2/VALIDE covered; AA-stage rejection unclear | **R13** |
| Email notifications (prod) | works via MailHog in dev; prod SMTP unproven | **R14** |
| OCR (PDF/XML/image) | VERIFIED (`OcrService.java`) | none |
| Three-way matching + tolerance + override | VERIFIED (`domain/purchasing/`, `InvoiceController.java:259`) | none |
| MFA mandatory finance/approval | VERIFIED (`AuthService.java:368-382`, `MfaService.java`) | none |
| RBAC every endpoint | VERIFIED (`@PreAuthorize` on all 39 controllers) | none |
| AES-256 bank details | VERIFIED (`EncryptionUtil.java` AES-GCM-256) | none |
| Immutable exportable audit trail | VERIFIED (`AuditLoggingFilter.java`, `V25`, export endpoints) | none |
| Dashboards / WebSocket / email notif | VERIFIED | none |
| Reporting + PDF/Excel export | VERIFIED (`ReportController.java`, POI+iText) | none |
| Webhooks (ERP/banking integration) | VERIFIED (HMAC-SHA256, `WebhookService.java`) | none |
| Supplier self-service portal | VERIFIED (`SupplierPortalController.java`) | none |

---

*End of report. All `file:line` references are relative to `invoice-system/`. Regenerate coverage with `./mvnw test jacoco:report` against a reachable PostgreSQL.*
