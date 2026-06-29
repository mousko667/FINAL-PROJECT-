# Phase 8 — Consolidated Issues Register

> Every actionable finding from Phases 1-6 of this audit cycle, in one place, sorted by severity.
> `P{phase}-{nn}` IDs come from the architecture/security/performance/frontend/infra audits
> (Phases 1-5). `REQ-{nn}` IDs come from the 14-module requirements coverage audit (Phase 6).
> Items marked **CLOSED** were found AND fixed within this same audit cycle (proof of execution
> in the source phase doc) — listed here for completeness/traceability only, no Phase 10 action
> needed. Items marked **🔹 hors-scope** are documented in `docs/SCOPE.md`, not tracked for Phase 10.
>
> This register is the direct input to Phase 9 (`PLAN-CORRECTIONS.md` + `TASKS.md`).

---

## CRITIQUE

| ID | Source | Summary | File:line |
|---|---|---|---|
| REQ-17 | M10 | `AuditController`'s `/audit-logs/system` (ADMIN) and `/audit-logs/financial` (DAF) endpoints filter on `action IN (SYSTEM_ACTIONS\|FINANCIAL_ACTIONS)`, but `AuditLoggingFilter` (the only writer for normal traffic) never produces any of those action values — both endpoints structurally return zero rows for ALL real traffic. The entire audit "sub-typing" design is dead code. | `AuditController.java` (SYSTEM_ACTIONS/FINANCIAL_ACTIONS lists), `AuditLoggingFilter.java:classifyAction()`, `FinancialAuditPage.tsx:48-49` |

## CLOSED (fixed within this audit cycle)

| ID | Source | Summary | File:line | Proof |
|---|---|---|---|---|
| P2-01 | Phase 2 | RS256 refresh tokens (506 chars) exceeded `active_sessions.refresh_token VARCHAR(500)` — caused HTTP 409 on first authenticated request after login, system-wide. **Fixed via V41 migration + entity column-length edit.** | `V41__increase_active_sessions_refresh_token_length.sql`, `ActiveSession.java:26` | `SupplierPortalIntegrationTest` exit=0, all 8 steps `200` |

---

## HAUTE

| ID | Source | Summary | File:line |
|---|---|---|---|
| P1-02 | Phase 1 | `docs/ARCHITECTURE.md` §4.1 "Known Implementation Gaps" table is entirely stale — all 8 listed gaps are resolved (7 fully, 1 partially: GAP6 backend done, frontend missing → same root cause as P4-02/REQ-22 family) | `docs/ARCHITECTURE.md` §4.1 |
| P1-03 | Phase 1 | `docs/ARCHITECTURE.md` §5 security filter chain diagram omits 3 real filters, misplaces CORS (not actually in the security chain), and shows the wrong relative filter order | `docs/ARCHITECTURE.md` §5 vs `SecurityConfig.java:71-75` |
| P1-05 | Phase 1 | 5 controllers inject repositories directly, bypassing the service layer (CLAUDE.md §3 violation) — `AdminSessionController` and `IntegrationStatusController` have ZERO service layer at all; these are NEW regressions introduced within the same T3/T6/T7 correction cycle that claimed to fix this class of bug | `AdminSessionController.java`, `IntegrationStatusController.java`, `WebhookController.java`, `DelegationController.java`, `InvoiceDocumentController.java` |
| P2-02 | Phase 2 | `GET /api/v1/supplier/profile` returns the raw `Supplier` JPA entity — decrypted `bankDetails` ("BANK-DETAILS-X") leaks into the JSON response; violates CLAUDE.md "never expose JPA entities directly" and contradicts `SupplierResponse`'s deliberate exclusion of this exact field | `SupplierPortalController.java:147-153` (cf. correct sibling pattern `:155-164`); `Supplier.java:58-60` (no `@JsonIgnore`); `SupplierResponse.java` |
| P3-02 | Phase 3 | `invoices.supplier_id` has no database index, despite being used as a filter in 4 separate queries — including the supplier-portal dashboard (now reachable post-P2-01 fix) | `V14__update_invoices_supplier_fk.sql:5` (no index), `InvoiceRepository.java:55-63,85-86,88-89,91` |
| P3-04 | Phase 3 | Webhook delivery: `deliveryTimeoutSeconds` config property is read nowhere, `RestTemplate` has no timeout configured (infinite default), and the 3-retry chain uses blocking `Thread.sleep` (5s/25s/125s) inside an `@Async`/`@Transactional` service — worst case ~755s per delivery, holding a thread + DB connection the whole time | `WebhookService.java:46-47` (unused config), `WebConfig.java:10-13` (no timeout factory), `WebhookService.java:131-175` (retry chain) |
| P4-02 | Phase 4 | Approval Delegation (T6/V40) has a complete, working backend (`DelegationController`, 3 endpoints) but **zero frontend** — no UI exists to create, view, or revoke a delegation. First instance of the recurring backend-complete/frontend-absent pattern. | `DelegationController.java`, `frontend/src/**` (0 matches for "delegation") |
| P5-01 | Phase 5 | `docker-compose.yml`'s `postgres` service is orphaned — `backend` connects to a host-native Postgres on port 5433/`oct_invoice` via `host.docker.internal`, while the committed `postgres` service publishes port 5432/`oct_invoice_dev` and is absent from `backend.depends_on`. `docker-compose up -d` on a fresh clone, as the file's own header comment instructs, would not produce a working backend. **Requires a user/architectural decision (Option A: Docker-only Postgres, vs Option B: remove the orphaned service and document host-Postgres as a prerequisite) — not a unilateral fix.** | `docker-compose.yml:13-33` (postgres svc), `:97-103` (backend env), `:119-123` (depends_on) |
| REQ-23 | M13/M14 | Module 13 items 4/7/8/9 (data-sensitivity classification, bulk import/export, access-request workflow, permission-matrix editor) and Module 14 item 1 (role-permission matrix, same gap) are entirely absent — an "enterprise IAM platform" tier with no PRD/WORKFLOW mandate, on top of role/department assignment that DOES work | `grep -rln "sensitivit\|bulk.*import\|access.*request\|permission.*matrix" -i src frontend/src` → zero matches |

---

## MOYENNE

| ID | Source | Summary | File:line |
|---|---|---|---|
| P1-01 | Phase 1 | Package names in `docs/ARCHITECTURE.md` §2/§10 don't match actual packages (`matching`→`purchasing`, `integration`→`webhook`, `reporting`→`report`); §2 also omits 6 existing packages | `docs/ARCHITECTURE.md` §2, §10 |
| P3-01 | Phase 3 | `GET /api/v1/purchase-orders` (when called without `supplierId`) returns ALL purchase orders, unpaginated | `PurchaseOrderController.java:90-102`, `PurchaseOrderService.java:126-129` |
| P4-01 | Phase 4 | 94 of 413 (~23%) `t(key, fallback)` calls reference i18n keys that exist in NEITHER `en.json` nor `fr.json` — the hardcoded single-language fallback string displays regardless of active locale (violates CLAUDE.md §1 bilingual rule). Concentrated in MFA setup (16 keys, French fallbacks shown to EN users) and supplier self-registration (13 keys, English fallbacks shown to FR users), plus archive/payments/grn/invoice/dashboard/admin/nav/auth (61 more) | `ProfilePage.tsx` (mfa.*), `SupplierRegisterPage.tsx` (supplier.register.*), + 8 other files |
| REQ-02 | M1/M13/M14 | `SecuritySettingsPage.tsx`'s security-policy form (MFA-required toggle, session timeout, max login attempts, min password length) is **explicitly simulation-only** — `handleSave` makes no API call, and the success banner's own text admits *"settings saved (simulation only in this version)"*. The SAME page's "Active Sessions" sub-section (list/revoke) IS fully real (re-confirms T6) — confirming this is a half-real/half-fake page, not a fully-fake one. An admin who changes these values believes system security policy changed; nothing does. | `SecuritySettingsPage.tsx:35-39,51` (fake), `:135-181` (real, T6 ActiveSession) |
| REQ-04 | M2 | Validator/manager dashboard: "Traitées ce mois" and "Approuvées" KPI tiles render the literal placeholder `—` (never wired to a real query, unlike the AA/DAF KPI cards which DO call `reportService.getKpis`); no recent-activity feed exists anywhere on the dashboard | `DashboardPage.tsx:258,262` (placeholders), `:243-300` (validator branch, no activity feed) |
| REQ-08 | (carried from earlier modules, see REQUIREMENTS-COVERAGE.md) | Backend-complete/frontend-absent — second instance of the recurring pattern (MatchingConfig) | see `docs/audit/REQUIREMENTS-COVERAGE.md` |
| REQ-11 | (carried from earlier modules) | Backend-complete/frontend-absent — third instance of the recurring pattern (Remittance Advice) | see `docs/audit/REQUIREMENTS-COVERAGE.md` |
| REQ-19 | M10 | No anomaly-detection of any kind exists; no real-time/auto-refreshing audit dashboard exists — `AdminAuditPage.tsx`/`FinancialAuditPage.tsx` are plain paginated tables with manual filters only | `grep -rln "anomal" src frontend/src` → zero matches |
| REQ-21 | M11 | Reporting & Analytics has no custom report builder, no scheduled/recurring report config, no report-distribution manager, no executive-summary generator, and no budget-vs-actual comparison (5 of 13 matrix items absent) — the "configuration/automation" layer on top of an otherwise solid operational reporting layer (8/13 items work) | `grep -rln "report.*builder\|ScheduledReport\|ReportSchedule" -i` → zero matches |
| REQ-22 | M12 | `WebhookController` + `IntegrationStatusController` are a complete, correctly-architected, ADMIN-only backend (HMAC-SHA256, 3-retry backoff per CLAUDE.md §9, append-only delivery log) — but **zero frontend exists** (no page, API client method, nav item, or route). Sixth instance of the backend-complete/frontend-absent pattern; per `docs/SCOPE.md`, explicitly NOT part of Module 12's hors-scope exclusion. | `WebhookController.java`, `IntegrationStatusController.java`, `grep -rln "webhook" -i frontend/src` → zero matches |

---

## BASSE

| ID | Source | Summary | File:line |
|---|---|---|---|
| P1-06 | Phase 1 | `invoice`↔`purchasing` bidirectional package dependency is undocumented (not a Spring bean cycle — a by-design reconciliation pattern that just needs a doc note) | `docs/ARCHITECTURE.md` §2 (missing "Inter-domain Dependencies" subsection) |
| P1-07 | Phase 1 | `ApprovalController.getApprovalSteps` returns `List<Map<String,Object>>` instead of a typed DTO — style/consistency only, no entity leak (all fields already safe) | `ApprovalController.java:36` |
| P3-03 | Phase 3 | Flyway migration sequence has an undocumented gap — V36, V37, V38 never existed (confirmed via `git log --all --diff-filter=D`); prior "covered by V17-19" claim is unsubstantiated | `src/main/resources/db/migration/` (V35 → V39 jump) |
| P4-03 | Phase 4 | TypeScript `strict`, `noImplicitAny`, `noUnusedLocals`, `noUnusedParameters` are all `false` — reduces compile-time safety for an "enterprise-grade" project per CLAUDE.md §1. Not recommended for Phase 10 (retrofitting `strict: true` onto ~400 components would surface dozens-to-hundreds of new errors) — documented as a known limitation instead. | `tsconfig.app.json:9-10,21-22` |
| P4-04 | Phase 4 | The MFA setup QR-code `<img>` (the ONLY `<img>` tag in the entire frontend) has no `alt` attribute (accessibility gap); separately, the `otpauth://` URI containing the TOTP secret is sent as a URL query parameter to a third-party QR-rendering API (`api.qrserver.com`) | `ProfilePage.tsx:150-151` |
| P5-02 | Phase 5 | `docker-compose.yml`'s `MINIO_SECRET_KEY` default (`dany`, in `minio_init`) doesn't match `MINIO_ROOT_PASSWORD` default (`dany1234`, in `minio`) — fresh-clone-only risk (this environment's `.env` sets the var consistently, so currently masked); if unset, `minio_init` auth fails and blocks `backend` startup via `depends_on` | `docker-compose.yml:43` vs `:66` |
| REQ-01 | M1 | `ForgotPasswordPage.tsx`/`ResetPasswordPage.tsx` have ZERO `t()` calls — 100% hardcoded English shown to French-locale users too, violating CLAUDE.md §1's bilingual rule. Distinct from P4-01 (which only covers missing keys for EXISTING `t()` calls). | `ForgotPasswordPage.tsx`, `ResetPasswordPage.tsx` |
| REQ-03 | M1 | `LoginPage.tsx` shows the same generic "invalid credentials" message for ALL auth errors, including HTTP 423 (account locked) — the backend correctly differentiates (`AccountLockedException` → 423 `account.locked`), but the frontend doesn't branch on it, misleading locked-out users into retrying their password | `LoginPage.tsx:112-117` vs `GlobalExceptionHandler.java:91-95` |
| REQ-05 | M3 | No bulk/multi-file invoice upload UI exists; `InvoiceController` has 10 endpoints, none accept multiple invoices in one call | `grep -rln "bulk\|batch.*upload" frontend/src/pages` → only `PurchaseOrdersPage.tsx` (unrelated) |
| REQ-14 | M9 | `ArchivePage.tsx:175` displays a static, false claim — *"SHA-256 vérifié à chaque téléchargement"* and "10-year retention policy" — neither is actually enforced: `computeSha256()` runs only at upload time (no re-verification on download), and no `RetentionPolicy` entity/config/job exists anywhere. Also the basis for Module 14 item 6 (not double-counted). | `ArchivePage.tsx:175`, `InvoiceDocumentService.computeSha256()`, `InvoiceDocumentController.download()` |
| REQ-15 | M9 | `ArchivePage.tsx:150-152` "Download PDF" button has no `onClick` handler — fourth instance of the backend-complete/frontend-absent (here: frontend-rendered-but-dead) pattern. The underlying capability works fine from `InvoiceDetailPage.tsx`. | `ArchivePage.tsx:150-152` |
| REQ-16 | M9 | No document-viewer with zoom/rotation, no invoice versioning, and no document-access audit logging — downloading a financial document leaves no audit trail at all | `grep -rln "DocumentAccessLog\|InvoiceVersion"` → zero matches |
| REQ-18 | M10 | `AuditLoggingFilter.resolveUserId()` (`:73-77`) is hardcoded to always `return null` — every filter-originated `AuditLog` row has `user_id = NULL`, so the "who did this" column renders `—` for nearly all audit entries | `AuditLoggingFilter.java:73-77` |
| REQ-20 | M11 | `AdminDepartmentFormPage.tsx` collects a Zod-validated `budget` field for every department, but `Department.java`/Flyway have no `budget` column — a frontend-only "ghost field", silently dropped before persistence. Folded into REQ-21. | `AdminDepartmentFormPage.tsx:13,82,87`, `Department.java` (no `budget`) |
| REQ-24 | M14 | 8 of 12 Module 14 items (encryption-status indicators, backup status, privacy-policy acceptance tracking, security-incident reporting, SOX/IFRS compliance checklist, compliance calendar, security-health dashboard) have NO existing implementation to build on — a "compliance/security meta-layer" that would sit on top of the system's existing, individually-correct controls. Unlike Module 12, no explicit hors-scope confirmation exists in `REQUIREMENTS-MATRIX.md` for this module — flagging for Phase 9 triage (candidates for `docs/SCOPE.md`). | `grep -rln` (8 separate zero-match searches) — see `docs/audit/REQUIREMENTS-COVERAGE.md` Module 14 |

---

## ⚠️ Outstanding decision required before Phase 10 (not a code fix)

- **P5-01** (HAUTE, above) requires the user to choose Option A (Docker-only Postgres) or Option B (host-Postgres as documented prerequisite) before any docker-compose edit is made.

## 🔹 Hors-scope assumé (see `docs/SCOPE.md`, no Phase 10 action)

- Module 12 items 1-7, 10, 12 (named third-party ERP/accounting/banking/GED connectors + surrounding config/scheduling/testing tooling) — 9 items, confirmed with project owner 2026-06-12.

## ✅ Clean / positive findings (no action needed)

| ID | Source | Summary |
|---|---|---|
| P1-04 | Phase 1 | Three-Way Matching integration point verified correct AND exceeds the documented design (undocumented duplicate-invoice check) |
| P2-03 | Phase 2 | `@PreAuthorize` coverage — 21/21 controllers, fully clean |
| P2-04 | Phase 2 | MFA secret (`User.mfaSecret`) never serialized in any response |
| P2-05 | Phase 2 | Soft-delete enforcement — zero hard deletes anywhere |
| P2-06 | Phase 2 | CORS configuration — fail-closed in production |
| REQ-MFA-01 | M14 (resolved) | `MfaSetupEnforcementFilter.requiresMandatoryMfa()` correctly includes `ROLE_ASSISTANT_COMPTABLE` alongside `ROLE_ADMIN`/`ROLE_DAF`/`ROLE_VALIDATEUR_N1_*`/`ROLE_VALIDATEUR_N2_*`, exactly per CLAUDE.md §9. Closes a long-outstanding low-confidence follow-up with no bug. |
| Module 1-8, Module 13 | Phase 6 | All ✅ COMPLET items not listed above — see `docs/audit/REQUIREMENTS-COVERAGE.md` for full per-module tables (Module 13 in particular: 6/12 items fully working, the audit's strongest module) |

---

## Severity totals

| Severity | Count |
|---|---|
| CRITIQUE | 1 (REQ-17) |
| CLOSED (fixed this cycle) | 1 (P2-01) |
| HAUTE | 9 (P1-02, P1-03, P1-05, P2-02, P3-02, P3-04, P4-02, P5-01, REQ-23) |
| MOYENNE | 9 (P1-01, P3-01, P4-01, REQ-02, REQ-04, REQ-08, REQ-11, REQ-19, REQ-21, REQ-22) — *note: 10 rows, REQ-08/REQ-11 counted individually* |
| BASSE | 12 (P1-06, P1-07, P3-03, P4-03, P4-04, P5-02, REQ-01, REQ-03, REQ-05, REQ-14, REQ-15, REQ-16, REQ-18, REQ-20, REQ-24) — *note: 15 rows* |
| Positive/clean | 6 entries + 2 full modules |

**Total actionable items for Phase 9 triage: 1 CRITIQUE + 9 HAUTE + 10 MOYENNE + 15 BASSE = 35** (P2-01 already closed, REQ-MFA-01 already resolved, 9 Module-12 items hors-scope — none of these 11 need Phase 10 work).
