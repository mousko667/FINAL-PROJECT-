# Phase 9 — Correction Plan

> Translates `docs/audit/ISSUES.md` (35 actionable items) into an ordered, executable plan.
> Per CLAUDE.md §2 ("the development plan already exists in full in `docs/TASKS.md`"), the
> actual checklist lives in `docs/TASKS.md` as a new **Phase 11 — Audit Correction Cycle**,
> following the exact `[ ] P11-NN` format used by Phase 9A-9G and Phase 10. This document is
> the narrative companion: WHY the tasks are grouped/ordered this way, what decisions were
> already made, and what each sub-phase's exit criteria are.
>
> Phase 10 (the correction loop) executes `docs/TASKS.md`'s new Phase 11 top-to-bottom.
> CLAUDE.md §3's anti-incomplete-implementation rules apply to every task: code compiles,
> `.\mvnw.cmd test` passes, a test covering the specific fix exists and passes, none of the
> 10 absolute architecture/security rules are violated, and the frontend builds without TS
> errors if touched.

---

## 1. Two decisions made before sequencing (both confirmed by project owner, 2026-06-12)

### Decision 1 — P5-01 (docker-compose Postgres): **Option B (host-Postgres)**
The committed `postgres` service (lines 13-33 of `docker-compose.yml`) and its
`postgres_data` volume will be **removed**. `docker-compose up` will continue to manage
`backend`, `frontend`, `minio`, `minio_init`, `mailhog` only. `docs/ARCHITECTURE.md §4.3`
will document a host-native PostgreSQL 18 (port 5433, db `oct_invoice`) as a prerequisite
that must be running before `docker-compose up`. This matches the `.env`/`backend` config
already in place — smallest diff, no behavior change to the currently-working setup.

### Decision 2 — Large items (REQ-21, REQ-22, REQ-23, REQ-24, REQ-16, P4-01): **include all, sequence by size**
None of these are pushed to `docs/SCOPE.md`. All 35 `ISSUES.md` items get a `P11-NN` task.
Tasks are ordered so CRITIQUE/HAUTE and small/localized fixes come first; large items are
broken into the smallest independently-completable sub-tasks (e.g., P4-01's 94 i18n keys
become 13 sub-tasks, one per feature area from `PHASE4-FRONTEND.md`'s table). If Phase 10
runs out of time/budget partway through, remaining `[ ]` tasks stay honestly unchecked —
this is itself CLAUDE.md-compliant (no silent TODOs, an accurate task list IS the record).

---

## 2. Sequencing rationale

The 35 items are grouped into **11 sub-phases (P11-A through P11-K)**, ordered by:
1. **Severity** (CRITIQUE → HAUTE → MOYENNE → BASSE) as the primary axis
2. **File/area locality** within a severity band, so related changes land together and are
   tested together (e.g., all 5 controller→service refactors in one sub-phase, all docker
   changes in one sub-phase)
3. **Dependency ordering** — e.g., REQ-23's permission-matrix editor (P11-F) is independent
   of everything else and can run any time; but P11-D's controller refactors should land
   before any new controller endpoints are added in P11-J/K, so new code follows the
   corrected pattern from the start rather than needing to be written twice.

| Sub-phase | Theme | Severity range | # tasks | Source IDs |
|---|---|---|---|---|
| P11-A | Audit log dead-code (CRITIQUE) | CRITIQUE | 1 | REQ-17 |
| P11-B | Security fixes | HAUTE | 2 | P2-02, REQ-18 |
| P11-C | Performance fixes | HAUTE | 3 | P3-01, P3-02, P3-04 |
| P11-D | Controller → service layer refactor | HAUTE | 5 | P1-05 (×5 controllers) |
| P11-E | Docker/infra cleanup | HAUTE + BASSE | 2 | P5-01 (Option B), P5-02 |
| P11-F | IAM/permission gaps | HAUTE | 4 | REQ-23 (×4 items) |
| P11-G | Documentation corrections | MOYENNE + BASSE | 6 | P1-01, P1-02, P1-03, P1-06, P1-07, P3-03 |
| P11-H | i18n sweep | MOYENNE + BASSE | 15 | P4-01 (×13 groups), REQ-01, P4-04 |
| P11-I | Frontend correctness fixes | MOYENNE + BASSE | 4 | REQ-02, REQ-03, REQ-04, REQ-15 |
| P11-J | Backend-complete/frontend-absent UIs | MOYENNE + BASSE | 4 | P4-02, REQ-08, REQ-11, REQ-22 |
| P11-K | Larger feature builds | MOYENNE + BASSE | 7 | REQ-05, REQ-14, REQ-16, REQ-19, REQ-20/21, REQ-24 (×2) |

**Total: 53 individual `P11-NN` tasks** (35 ISSUES.md items, several split into sub-tasks
as agreed in Decision 2).

---

## 3. Sub-phase detail

### P11-A — Audit Log Dead Code (CRITIQUE)
**REQ-17**: `AuditController`'s `/audit-logs/system` and `/audit-logs/financial` endpoints
filter on action values that `AuditLoggingFilter` never produces — structurally always
empty. Fix: either (a) extend `AuditLoggingFilter.classifyAction()` to actually produce
`SYSTEM_ACTIONS`/`FINANCIAL_ACTIONS`-classified entries, or (b) remove the dead
sub-typing and have both endpoints query the full audit log with role-appropriate
filtering. Given `FinancialAuditPage.tsx` already calls one of these endpoints expecting
real data, **option (a)** preserves the existing frontend contract. Exit: both endpoints
return non-empty results for realistic traffic, with a new test asserting this.

### P11-B — Security Fixes (HAUTE)
- **P2-02**: `SupplierPortalController.getProfile()` (or equivalent) must map `Supplier` →
  `SupplierResponse` (which already correctly excludes `bankDetails`), matching the
  sibling endpoint's pattern at `:155-164`. Exit: integration test asserts
  `bankDetails`/`bank_details` is absent from the JSON response.
- **REQ-18**: `AuditLoggingFilter.resolveUserId()` must extract the authenticated user's ID
  from the `SecurityContext` (same pattern used elsewhere for `Authentication.getPrincipal()`)
  instead of hardcoded `null`. Exit: new audit log entries from authenticated requests have
  non-null `user_id`; existing NULL rows are historical and untouched (append-only).

### P11-C — Performance Fixes (HAUTE)
- **P3-01**: `GET /api/v1/purchase-orders` without `supplierId` must use `Pageable`/`Page<T>`
  like other list endpoints, returning `PagedResponse<PurchaseOrderResponse>`.
- **P3-02**: New Flyway migration `V42__add_invoices_supplier_id_index.sql` adding
  `CREATE INDEX idx_invoices_supplier_id ON invoices(supplier_id)`.
- **P3-04**: `WebhookService` — wire `deliveryTimeoutSeconds` into a `RestTemplate`
  `ClientHttpRequestFactory` with connect/read timeouts; replace blocking `Thread.sleep`
  retry with `@Async`-scheduled retries (e.g., `TaskScheduler`/`@Scheduled` re-queue) so no
  thread holds a DB connection for up to 755s. Given CLAUDE.md §9's exact retry intervals
  (5s/25s/125s) must be preserved — only the *mechanism* changes, not the timing contract.

### P11-D — Controller → Service Layer Refactor (HAUTE)
**P1-05**: 5 controllers (`AdminSessionController`, `IntegrationStatusController`,
`WebhookController`, `DelegationController`, `InvoiceDocumentController`) inject
repositories directly. Each gets its own task (P11-D1..D5) since each requires creating
or extending a service class with its own Javadoc + unit tests per CLAUDE.md §8.
`AdminSessionController` and `IntegrationStatusController` need a NEW service class each
(currently zero service layer); the other 3 likely need an existing service extended.

### P11-E — Docker/Infra Cleanup (HAUTE + BASSE)
**P5-01 + P5-02, bundled** (same file, same "fresh clone" theme, per PHASE5-INFRA.md's own
recommendation):
1. Remove `docker-compose.yml`'s `postgres` service (lines 13-33) and `postgres_data` volume
2. Fix `MINIO_SECRET_KEY` default mismatch (`:66` → `dany1234` to match `:43`)
3. Add `docs/ARCHITECTURE.md §4.3` prerequisite documentation for host-Postgres (port 5433,
   db `oct_invoice`)

Exit: `docker compose config` shows no `postgres` service; a fresh clone with `.env` unset
for `MINIO_SECRET_KEY` would still have `minio_init` succeed.

### P11-F — IAM/Permission Gaps (HAUTE)
**REQ-23**, 4 independent sub-tasks (each its own small feature):
1. Data-sensitivity classification labels on financial records (new enum + column via Flyway)
2. Bulk user import/export (CSV) on `AdminUsersPage.tsx` + `UserController` endpoint
3. Self-service access-request workflow (new entity + approval-lite flow)
4. Visual permission-matrix editor (frontend grid over existing `PUT /{id}/roles`)

Each is genuinely a small standalone feature — no PRD/WORKFLOW mandate exists for any of
these (per REQ-23's own text), so each task's "definition of done" includes adding it to
`docs/REQUIREMENTS-MATRIX.md` as newly-implemented, not just code+tests.

### P11-G — Documentation Corrections (MOYENNE + BASSE)
Pure doc edits, no code changes, but each still needs the "is this still true" check:
- **P1-01**: Fix package names in `docs/ARCHITECTURE.md` §2/§10 (`matching`→`purchasing`,
  `integration`→`webhook`, `reporting`→`report`); add 6 missing packages
- **P1-02**: Rewrite `docs/ARCHITECTURE.md` §4.1 gap table — all 8 stale entries
- **P1-03**: Redraw `docs/ARCHITECTURE.md` §5 security filter chain diagram (3 missing
  filters, correct order, CORS placement)
- **P1-06**: Add "Inter-domain Dependencies" subsection documenting `invoice`↔`purchasing`
- **P1-07**: `ApprovalController.getApprovalSteps` — replace `List<Map<String,Object>>` with
  a typed `ApprovalStepResponse` DTO (this one IS a code change, grouped here because it's
  small and doc-adjacent — P1-07 was filed as "style/consistency only")
- **P3-03**: Document the V36-38 Flyway gap in `docs/ARCHITECTURE.md` (or wherever migration
  history is tracked) — V35→V39 jump is real, not an error, just undocumented

### P11-H — i18n Sweep (MOYENNE + BASSE)
**P4-01's 94 keys**, split into the 13 feature-area sub-tasks from `PHASE4-FRONTEND.md`'s
table (each: add missing keys to BOTH `en.json`/`fr.json`, verify in both locales):
1. `supplier.register.*` (13 keys)
2. `supplier.verify.*` (5 keys)
3. `supplier.tracking.*` (8 keys)
4. `supplier.portal.*` (3 keys)
5. `mfa.*` (16 keys)
6. `payments.*` (7 keys)
7. `archive.*` (6 keys)
8. `auth.*` incl. password-strength indicator (6 keys)
9. `grn.*` (6 keys)
10. `invoice.*` (6 keys)
11. `dashboard.*` (4 keys)
12. `admin.*` (3 keys)
13. `nav.*`, `register.*`, `notifications.*`, `profile.*` (7 keys, grouped — smallest leftovers)

Plus 2 more, same theme:
14. **REQ-01**: Add full `t()` coverage to `ForgotPasswordPage.tsx`/`ResetPasswordPage.tsx`
    (currently zero `t()` calls — needs NEW keys, not just missing ones)
15. **P4-04**: Add `alt` attribute to the MFA QR `<img>` — this is the 95th new key
    (`mfa.qrCodeAlt`), grouped here since it's an i18n addition

### P11-I — Frontend Correctness Fixes (MOYENNE + BASSE)
- **REQ-02**: `SecuritySettingsPage.tsx`'s security-policy form — either (a) wire it to a
  real backend (new `SecurityPolicyController`/`SecurityPolicy` entity+table actually
  enforced by `MfaSetupEnforcementFilter`/`RateLimitingFilter`/password-validation), or
  (b) remove the fake form entirely and keep only the real Active Sessions section.
  **Given REQ-23 (P11-F) already adds a permission-matrix editor to this same admin area,
  and REQ-24 (P11-K) adds security-health dashboards, option (a) — making this real — is
  the better fit** for an "enterprise-grade" FYP and avoids a regression (removing a
  visible admin feature). Scope: MFA-required toggle + min-password-length are realistically
  wireable to existing enforcement points; session-timeout and max-login-attempts already
  have backend equivalents (`active_sessions` TTL, account-lock-after-5 in CLAUDE.md §9) that
  this form should read/write instead of local `useState`.
- **REQ-03**: `LoginPage.tsx` — branch on HTTP 423 (`account.locked`) to show a distinct,
  translated "account locked, contact admin" message instead of the generic invalid-credentials text.
- **REQ-04**: Dashboard validator/manager KPI tiles ("Traitées ce mois", "Approuvées") — wire
  to `reportService.getKpis` (same call the AA/DAF cards already use, filtered/scoped
  appropriately for the validator's role).
- **REQ-15**: `ArchivePage.tsx:150-152` "Download PDF" button — add the `onClick` handler,
  reusing the working download logic from `InvoiceDetailPage.tsx`.

### P11-J — Backend-Complete/Frontend-Absent UIs (MOYENNE + BASSE)
Four instances of the same pattern, each gets a minimal UI over an existing, working backend:
- **P4-02**: Approval Delegation — new section in `ProfilePage.tsx` (or small dedicated
  page): list active delegations, create form (target user + date range), revoke button.
  Uses `DelegationController`'s existing 3 endpoints.
- **REQ-08**: MatchingConfig UI — admin page to view/edit `matching_config` tolerance
  thresholds (currently DB-only per CLAUDE.md §9's "stored in DB, not hardcoded" rule).
- **REQ-11**: Remittance Advice UI — page/section to view & download remittance advices
  for processed payments.
- **REQ-22**: Webhooks/Integration Status UI — new admin page wrapping `WebhookController`
  (register/list/update/delete webhooks, view delivery log) + `IntegrationStatusController`
  (integration health). Depends on P11-D3/D2 (service-layer refactor) landing first so the
  new frontend talks to a properly-layered backend.

### P11-K — Larger Feature Builds (MOYENNE + BASSE)
The biggest remaining items — each independently scoped, lowest priority by severity:
1. **REQ-05**: Bulk/multi-file invoice upload — new endpoint accepting multiple files +
   frontend multi-select upload UI (likely on `InvoicesPage.tsx`/a new upload page)
2. **REQ-14**: Either implement real SHA-256 re-verification on download + a
   `RetentionPolicy` config/job, OR correct `ArchivePage.tsx:175`'s text to stop claiming
   capabilities that don't exist. Given the scale of a real retention-policy job, **the
   text correction is the realistic Phase 10 scope**; a follow-up note will be added to
   `docs/KNOWN_ISSUES_REGISTRY.md` for the full retention-policy feature as future work.
3. **REQ-16**: Document-access audit logging — new `document_access_log` table (append-only,
   per the soft-delete/append-only pattern already used for matching/webhook logs) +
   logging hook in `InvoiceDocumentController.download()`. (Versioning and the
   zoom/rotation viewer are NOT included — flag as future work in
   `docs/KNOWN_ISSUES_REGISTRY.md`, as they're substantially larger than the access-log
   piece and not tied to an audit-trail correctness issue like REQ-17/18 are.)
4. **REQ-19**: No anomaly detection exists, and no real-time audit dashboard — given
   REQ-17/18 (P11-A/B) will make the underlying audit data correct for the first time,
   a basic "recent activity" auto-refreshing panel on `AdminAuditPage.tsx` is now
   meaningful to build. Full anomaly-detection (statistical/ML) is flagged as future work.
5. **REQ-20/21, combined**: Remove the frontend-only `budget` ghost field from
   `AdminDepartmentFormPage.tsx` (REQ-20) UNLESS a `Department.budget` column is added via
   Flyway — given REQ-21 already calls for a "budget-vs-actual comparison" report, **add
   the column** (small Flyway migration) so REQ-20's field becomes real AND seeds REQ-21's
   data requirement. The other 4 REQ-21 items (custom report builder, scheduled reports,
   distribution manager, executive-summary generator) are genuinely large — scope only the
   budget-vs-actual comparison report as the Phase 10 deliverable; flag the rest in
   `docs/KNOWN_ISSUES_REGISTRY.md`.
6. **REQ-24a**: Encryption-status indicator — small admin widget showing AES-256 coverage
   (e.g., "N suppliers with encrypted bank details") — cheap, high-visibility, reuses
   existing `EncryptionAttributeConverter`-protected fields.
7. **REQ-24b**: Security-health dashboard — roll up MFA adoption %, login-failure trends
   (now meaningful post-REQ-18), encryption coverage (REQ-24a), and
   `webhookDeliverySuccessRate` (already in `DashboardKpiDTO`) into one admin view. The
   remaining REQ-24 items (backup status, privacy-policy tracking, incident reporting,
   SOX/IFRS checklist, compliance calendar) have no existing data to surface and are
   flagged as future work in `docs/KNOWN_ISSUES_REGISTRY.md`, consistent with REQ-24's
   own text noting the absence of any PRD/WORKFLOW mandate for SOX/IFRS-specific tooling.

---

## 4. Items NOT given a P11 task (already closed / no action needed)

- **P2-01** — already fixed (V41), CLOSED in `ISSUES.md`
- **REQ-MFA-01** — already resolved, no bug
- **P1-04, P2-03, P2-04, P2-05, P2-06** — clean findings, no action
- **Module 12 items 1-7,10,12** — hors-scope assumé per `docs/SCOPE.md`, unchanged
- **P4-03** (TS strict mode) — explicitly NOT recommended for Phase 10 per `PHASE4-FRONTEND.md`'s
  own text; remains a documented known limitation in `docs/KNOWN_ISSUES_REGISTRY.md`

## 5. Items where Phase 10 scope is narrower than the full ISSUES.md description

Per CLAUDE.md §3 ("no silent TODOs — anything left unfinished MUST appear in ISSUES.md with
severity"), the following tasks deliver a **partial** fix by design, with the remainder
explicitly logged as future work in `docs/KNOWN_ISSUES_REGISTRY.md` (not silently dropped):

| Task | Full ISSUES.md scope | Phase 10 scope | Deferred remainder |
|---|---|---|---|
| P11-K2 (REQ-14) | Real SHA-256 re-verify + retention policy job | Correct the misleading UI text only | Re-verify-on-download + retention policy job |
| P11-K3 (REQ-16) | Access log + versioning + zoom/rotation viewer | Access log only | Versioning, document viewer |
| P11-K4 (REQ-19) | Full anomaly detection + real-time dashboard | Auto-refreshing recent-activity panel | Statistical/ML anomaly detection |
| P11-K5 (REQ-20/21) | 5 reporting items | Budget column + budget-vs-actual report | Report builder, scheduling, distribution, exec summaries |
| P11-K7 (REQ-24) | 8 compliance items | Encryption-status + security-health dashboard (2 items) | Backup status, privacy-policy tracking, incident reporting, SOX/IFRS checklist, compliance calendar (5 items) |

These deferrals will be written to `docs/KNOWN_ISSUES_REGISTRY.md` as part of Phase 11
(documentation updates), each with its own entry per the Living Documentation Rule
(CLAUDE.md §12): root cause = "out of scope for FYP correction cycle", preventive
rule = "tracked here for future development phases".

---

**Phase 9 complete.** Next: append the 53-task Phase 11 checklist to `docs/TASKS.md`,
then proceed to Phase 10 (correction loop), executing P11-A through P11-K in order.
