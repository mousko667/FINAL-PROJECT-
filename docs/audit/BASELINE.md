# BASELINE — Audit Phase 0

**Date:** 2026-06-11
**Branch:** `main` (13 commits ahead of `origin/main`, unpushed)
**Working tree:** 3 modified files (`CLAUDE.md`, `docker-compose.yml`, `pom.xml`) + 1 untracked (`docs/REQUIREMENTS-MATRIX.md`)

This document captures the state of the codebase **as found**, before any audit corrections are applied. All commands below were executed and their real output captured — see referenced log files in `target/`.

---

## 1. Uncommitted Working-Tree Changes

Three files are modified but not committed, representing unrecorded "Phase 10" work:

### `pom.xml`
Adds OCR dependencies:
- `tess4j` 5.11.0 (Tesseract OCR via JNA)
- `org.apache.pdfbox:pdfbox` 3.0.3

### `docker-compose.yml`
Backend service reconfigured to point at a **host-side PostgreSQL** instead of the `postgres` container:
- `extra_hosts: host.docker.internal:host-gateway` added
- `DB_HOST` default changed `postgres` → `host.docker.internal`
- `DB_PORT` default changed `5432` → `5433`
- `DB_NAME` default changed `oct_invoice_dev` → `oct_invoice`
- JWT changed from a single `JWT_SECRET` (HS256) to `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` (RS256)
- Backend's `depends_on: postgres: condition: service_healthy` removed

The committed `postgres` service definition (container `oct_postgres`, port 5432, db `oct_invoice_dev`) is unchanged — it now appears to be a legacy/unused service definition under the new topology.

`.env` (gitignored) is **consistent** with this new topology: `DB_HOST=host.docker.internal`, `DB_PORT=5433`, `DB_NAME=oct_invoice`, RS256 key env vars present.

**Status:** Nothing currently listens on port 5432 or 5433 (`netstat` confirmed empty). All 6 docker containers exist but are stopped (`Exited`, last active 3-5 days ago). No native Postgres service found.

**Decision (user, 2026-06-11):** Do not resolve this now. Use Testcontainers-based `mvnw test` for the Phase 0 baseline (works regardless of host DB state). The `docker-compose up` / host-DB question remains **open** and is tracked as an issue for Phase 5/9.

### `CLAUDE.md`
Adds (already present in working tree, reviewed): supplier-portal correction, §12 Living Documentation Rule, §13 Bug Prevention Rules (PROB-001..016), Phase 9 domain constraints (Supplier/MFA/Three-Way Matching/Webhooks).

### Untracked: `docs/REQUIREMENTS-MATRIX.md`
New 14-module requirements matrix (206 lines) — the reference document for Phase 6.

---

## 2. Backend Build — `mvnw clean compile`

```
[INFO] Compiling 224 source files with javac [debug parameters release 21] to target\classes
[WARNING] .../domain/user/mapper/UserMapper.java:[33,10] Unmapped target properties: "supplier, mfaEnabled, mfaSecret,
          mfaVerified, failedLoginAttempts, lockedUntil, emailVerificationToken, emailVerificationTokenExpiry,
          passwordResetToken, passwordResetTokenExpiry".
[WARNING] .../domain/invoice/mapper/InvoiceMapper.java:[24,13] Unmapped target property: "supplierBankDetails".
[INFO] .../config/security/RateLimitingFilter.java: Some input files use or override a deprecated API.
[INFO] BUILD SUCCESS
[INFO] Total time:  44.642 s
```

**Result: ✅ BUILD SUCCESS.** The uncommitted Phase 10 code (OCR/Tess4J/PDFBox) compiles cleanly. Two pre-existing MapStruct "unmapped target property" warnings (likely intentional — these are sensitive/internal fields not meant to be DTO-mapped, but should be confirmed in Phase 1) and one deprecation warning in `RateLimitingFilter` (Phase 1/3).

---

## 3. Backend Tests — `mvnw test`

Full output: `target/baseline-test-output-utf8.log` (UTF-8 converted from PowerShell's UTF-16LE `Tee-Object` output).

```
[ERROR] Tests run: 252, Failures: 29, Errors: 2, Skipped: 0
[INFO] BUILD FAILURE
[INFO] Total time:  04:01 min
```

**Result: ❌ 221/252 pass (87.7%). 31 total failures/errors across 9 test classes.**

This is **substantially different** from the prior session's memory claim of "29 failures, 2 errors in `ApprovalControllerTest`, `StateMachineTransitionExhaustiveTest`, `UserServiceTest.createUser_Success`" (3 classes). The actual count (31) is close, but the **distribution across classes is much wider** — memory significantly understated which areas are affected. Full breakdown:

| Test Class | Failures | Likely Cause (to verify in Phase 7/8) |
|---|---|---|
| `AuditControllerTest` | 3 | **Root-caused**: test calls `GET /api/audit-logs`, controller is mapped at `/api/v1/audit-logs` → all 404. Stale test path, single-line fix per failure. |
| `InvoiceControllerTest` | 1 | `listInvoices_AsAdmin_Returns200` gets 403 — possible `@PreAuthorize` regression on admin role. |
| `InvoicePerformanceTest` | 2 | Same 403-instead-of-200 pattern as above — likely same root cause. |
| `NotificationControllerTest` | 3 | All return 400 instead of 200 (`getNotifications`, `markAllAsRead`, `unreadCount`) — possible request validation or missing param regression. |
| `PaymentControllerTest` | 3 | One returns body `mfa_setup_required` instead of expected business error — **MFA enforcement filter likely intercepting test requests** that don't expect MFA setup. Other two: 400 instead of 200. |
| `ReportControllerTest` | 9 | Mixed: some 403-instead-of-200 (`getKpis`, `exportAuditPdf`, `exportCompliancePdf`, `getApprovalBottlenecks` w/ Admin, `getSupplierPerformance` w/ Admin, both w/ Auditeur), and some **200-instead-of-403** (`getApprovalBottlenecks`/`getSupplierPerformance` w/ AssistantComptable) — looks like **role-permission matrix is inverted/misconfigured** for these endpoints. |
| `SupplierPortalIntegrationTest` | 1 | `fullSupplierFlow_IntegrationTest` gets 409 instead of 200 — possible duplicate-resource conflict in test setup ordering. |
| `UserServiceTest` | 1 | `createUser_Success` — mock verifies `save()` called once, but `UserService.createUser()` (lines 89 and 101) calls it **twice**. Matches memory's prior claim. |
| `ApprovalControllerTest` | 4 | All 4 lifecycle/permission tests return 400 instead of 200/403 — likely upstream of the `ApprovalServiceTest` NPE below (state machine or delegation check throwing before reaching expected logic). |
| `ApprovalServiceTest` | 1 | **Root-caused — REGRESSION from T6**: `assignReviewer_WhenSoumis_WithWrongRole_ThrowsAccessDenied` throws `NullPointerException` because `ApprovalServiceImpl.checkRole()` (line 214, added by commit `41d4369` T6) calls `delegationRepository.findActiveDelegationsForDelegatee(...)` but `delegationRepository` is `null` — the test's `@Mock` setup was never updated to inject this new dependency. |
| `StateMachineTransitionExhaustiveTest` | 2 (errors) | `t5_en_validation_n2_to_valide` and `t10_en_validation_n2_to_rejete` — both fail with "Workflow Transition denied for event ASSIGN_REVIEWER from state SOUMIS" inside the `advanceTo` test helper. |

**High-confidence hypothesis (to verify in Phase 8):** `ApprovalControllerTest`'s 4 failures and `StateMachineTransitionExhaustiveTest`'s 2 errors may share a **single root cause** with the `ApprovalServiceTest` NPE — all involve `ASSIGN_REVIEWER`/`assignReviewer` → `checkRole()` → the new (improperly-mocked/wired) `delegationRepository`. If so, this could be **one fix resolving 7 of the 31 failures** (T6 regression).

### Notable passing evidence (positive baseline signal)

- **`ThreeWayMatchingServiceTest`: 10/10 pass.** **`ThreeWayMatchingIntegrationTest`: 3/3 pass.** This directly contradicts `docs/TASKS.md` Phase 9D (P9-35–P9-47) showing `[ ]` unchecked — the underlying implementation IS complete and tested. TASKS.md checkboxes are stale (Phase 9 doc-sync issue, not a code issue).
- **`OcrServiceTest`: 9/9 pass.** Uncommitted OCR (Tess4J/PDFBox) integration is functional at the unit level.
- **`DelegationServiceTest`: 3/3 pass.** The `DelegationService` itself (T6) is correctly tested in isolation — the regression is specifically in `ApprovalServiceImpl`'s integration with it, not in `DelegationService`.
- **T2 runtime proof obtained directly from this run**: `ReportControllerTest` log shows `AuditLoggingFilter` firing on real requests:
  ```
  2026-06-11 22:54:33 [main] INFO  c.o.i.s.filter.AuditLoggingFilter - AUDIT | GET /api/v1/reports/kpis | Status: 403 | User: anonymous | Time: 5ms
  2026-06-11 22:54:33 [Async-1] DEBUG c.o.i.d.a.service.AuditServiceImpl - Async writing audit log for entityType=HTTP_REQUEST entityId=/api/v1/reports/kpis action=GET /api/v1/reports/kpis -> 403
  ```
- MinIO connection refused (`localhost:9000`) logged repeatedly as `WARN` — expected since MinIO container is stopped; does not appear to fail any test (storage calls are likely mocked/skippable in test profile), but should be confirmed in Phase 3.

---

## 4. Frontend Build — `npm run build`

Full output: `target/baseline-frontend-build.log`.

```
dist/assets/index-B7I5yqHN.js   496.09 kB │ gzip: 158.29 kB
✓ built in 6.19s
```

**Result: ✅ SUCCESS.** No TypeScript errors, `dist/` produced.

## 4b. Frontend Tests — `npm test -- --run`

Full output: `target/baseline-frontend-test-utf8.log`.

```
Test Files  6 failed | 3 passed (9)
     Tests  10 failed | 17 passed (27)
  Duration  21.99s
```

**Result: ❌ 17/27 pass (63%).** Two distinct, unrelated root causes:

### Root cause A — Playwright e2e specs collected by Vitest (3 "failed" files, 0 real failures)

`frontend/vitest.config.ts` has no `exclude` for `e2e/`. Vitest's default glob picks up `e2e/bap-single-level.spec.ts`, `e2e/bap-two-level.spec.ts`, `e2e/security-audit.spec.ts` — these use Playwright's `test.describe`, which Vitest's transform cannot handle:
```
FAIL  e2e/bap-single-level.spec.ts [ e2e/bap-single-level.spec.ts ]
 ❯ Function.describe node_modules/playwright/lib/transform/transform.js:275:12
```
**Fix:** add `exclude: ['e2e/**', 'node_modules/**']` to `test` block in `vitest.config.ts`. Config-only, zero risk. Resolves 3/6 failing files (all false failures — these are Playwright specs meant to run via `npx playwright test`, not Vitest).

### Root cause B — Real component test failures (3 files, 10 tests)

| File | Tests failing | Symptom |
|---|---|---|
| `InvoiceActionPanel.test.tsx` | 6/9 | Component renders `<body><div /></body>` (empty) — `getAllByText(/valider/i)` finds nothing. Likely missing provider/context in `renderPanel()` test helper causing silent render failure. |
| `InvoiceTimeline.test.tsx` | 3/3 | `useQueryClient` throws inside `InvoiceTimeline.tsx:22` (`useQuery` call) — component rendered without a `QueryClientProvider` wrapper in test. |
| `useAuth.test.tsx` | 1/4 | `navigates to /dashboard on successful login` — `mockNavigate` never called (0 times). |

These three look related (B's first two share the "missing test wrapper/provider" pattern) but root-cause confirmation deferred to Phase 8.

---

## 5. Docker Container Status

```
NAMES            STATUS                    PORTS
oct_backend      Exited (143) 3 days ago
oct_postgres     Exited (0) 3 days ago
oct_frontend     Exited (0) 3 days ago
oct_minio_init   Exited (0) 5 days ago
oct_minio        Exited (0) 3 days ago
oct_mailhog      Exited (2) 3 days ago
```

All 6 containers exist (not deleted), all stopped. `oct_backend`'s exit code 143 (SIGTERM) suggests a clean shutdown, not a crash. Containers have not been started this session — `docker logs` not yet pulled (would show pre-existing-state logs only, predating uncommitted changes).

---

## 6. T1/T2 Pre-Verification (completed during Phase 0 via static code read)

These were verified by direct code/migration inspection — full Phase 7 verdict still pending but high confidence:

- **T1a** — `User.isAccountNonLocked()` ([User.java:167-170](../../src/main/java/com/oct/invoicesystem/domain/user/model/User.java#L167-L170)): checks `active` AND `lockedUntil` vs `Instant.now()`. ✅ Matches plan.
- **T1b** — `Invoice.supplierBankDetails` ([Invoice.java:79-81](../../src/main/java/com/oct/invoicesystem/domain/invoice/model/Invoice.java#L79-L81)): `@Convert(converter = EncryptionAttributeConverter.class)`. ✅
- **T1c** — `V35__encrypt_invoice_bank_details.sql`: nullifies plaintext legacy bank details (cannot re-encrypt via SQL), documents production backup procedure. ✅ Matches plan. SQL-level proof against a live DB still pending (needs DB resolution).
- **EncryptionUtil**: AES/GCM/NoPadding, 256-bit key (32 bytes), validates key length at `@PostConstruct`. ✅ Genuinely AES-256.
- **T2** — `SecurityConfig.java:75`: `.addFilterAfter(auditLoggingFilter, UsernamePasswordAuthenticationFilter.class)`. ✅ Matches the corrected anchor (commit `bb05834`), NOT the original plan's `JwtAuthenticationFilter` anchor (which would throw — `JwtAuthenticationFilter` has no registered Spring Security order). **Runtime proof obtained** (see §3 above) — filter fires and writes async audit log on real requests.

---

## 7. T3 Pre-Verification (completed during Phase 0 via exhaustive grep)

All **8 controllers explicitly named in the T3 plan** (`InvoiceController`, `ApprovalController`, `UserProfileController`, `SupplierController`, `SupplierPortalController`, `MatchingConfigController`, `PurchaseOrderController`, `ReportController`) have **zero** `*Repository` field injections. 7 of 8 use `SecurityHelper` (✅ — `ApprovalController` doesn't need it, no current-user-by-repo lookups in original scope).

**However**, 5 *other* controllers — all built in T6/Phase 9-10, **outside T3's original scope** — DO inject repositories directly and contain query logic:

| Controller | Repository | Usage |
|---|---|---|
| `InvoiceDocumentController` | `UserRepository` | `findByUsername()` for current-user resolution (should use `SecurityHelper`) |
| `AdminSessionController` | `ActiveSessionRepository` | `findAllActive()`, `revokeAllForUser()` — direct queries |
| `IntegrationStatusController` | `WebhookRepository`, `WebhookDeliveryRepository` | `findByIsActiveTrue()`, `findLatestDeliveryByWebhook()` |
| `WebhookController` | `WebhookRepository`, `WebhookDeliveryRepository` | `findByIsActiveTrue()`, `findById()`, `findByWebhookOrderByCreatedAtDesc()` |
| `DelegationController` | `UserRepository` | `findById()` ×2 for delegator/delegatee resolution |

This is a genuine architecture gap (CLAUDE.md §3: "never bypass service layer from controller") introduced by code written **after** T3, not a regression of T3 itself. To be logged as a Phase 1 architecture issue.

---

## 8. Open Items Carried Into Phase 1+

1. **Postgres host/port topology** (5432 container vs 5433 host) — unresolved, needed before `docker-compose up`, T1 SQL-level proof, and any Docker-based runtime testing.
2. **31 backend test failures** — full root-cause analysis deferred to Phase 8 (`ISSUES.md`), but `ApprovalServiceTest` NPE and `AuditControllerTest` 404s are already root-caused above as quick wins. Hypothesis that 7 failures share one root cause (delegation/checkRole) to be confirmed.
3. **10 frontend test failures** — Root cause A (Playwright/Vitest collision, 3 files) is a trivial config fix. Root cause B (3 files, 10 tests — provider/context wrapper issues) needs investigation.
4. **5 controllers with direct repository injection** (T6/Phase 10 code) — architecture gap, Phase 1.
5. **Docker container logs** not yet pulled (containers stopped).

## 9. ApprovalServiceTest NPE — Root Cause CONFIRMED (REGRESSION-T6-01)

`ApprovalServiceImpl` ([ApprovalServiceImpl.java:33-41](../../src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java#L33-L41)) is `@RequiredArgsConstructor` with 4 final fields, the last being `ApprovalDelegationRepository delegationRepository` (added by T6, commit `41d4369`, used in `checkRole()` at line 214).

`ApprovalServiceTest` ([ApprovalServiceTest.java:40-50](../../src/test/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceTest.java#L40-L50)) declares only **3** `@Mock` fields (`approvalStepRepository`, `invoiceRepository`, `invoiceStateMachineService`) for `@InjectMocks ApprovalServiceImpl`. Mockito's constructor injection passes `null` for the unmatched 4th parameter (`delegationRepository`), causing the NPE whenever `checkRole()` reaches the delegation-check branch (i.e., whenever the user lacks the direct role — exactly the `..._WrongRole_ThrowsAccessDenied` test case).

**Fix (Phase 10):** add `@Mock private ApprovalDelegationRepository delegationRepository;` to `ApprovalServiceTest`, stub `findActiveDelegationsForDelegatee(any(), any())` → `List.of()` for the wrong-role test. **This is a test-only change — `ApprovalServiceImpl` itself is correct** (production Spring DI always supplies the real bean).

**Hypothesis to verify in Phase 8**: `ApprovalControllerTest`'s 4 failures (all return 400) and `StateMachineTransitionExhaustiveTest`'s 2 errors (`ASSIGN_REVIEWER` denied from `SOUMIS`) may ALSO trace to this same `checkRole()`/`delegationRepository` path if those tests use a real (non-mocked) `ApprovalServiceImpl` via `@SpringBootTest` — but `@SpringBootTest` would inject the real `ApprovalDelegationRepository` bean (not null), so the failure mode would differ (likely a real `findActiveDelegationsForDelegatee` query against an empty/Testcontainers DB returning unexpected results, OR a different issue entirely). **Do not assume — verify independently in Phase 8.**

---

## 10. T4 Verdict — CI / Security-Scan Workflows

**Verdict: ✅ VERIFIED (structural).**

All three artifacts exist, are syntactically valid, and structurally match the post-audit plan:

- [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) (128 lines, parsed via Node `js-yaml`, top-level keys `name/on/jobs` valid). 3 jobs:
  - `backend`: services `postgres:18-alpine` (db `oct_invoice_dev`, healthcheck `pg_isready`) + `minio` (health via `curl /minio/health/live`); `setup-java@v4` (21/temurin, maven cache); creates MinIO bucket via `mc`; `./mvnw test --batch-mode` with `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` from `secrets.JWT_PRIVATE_KEY_TEST`/`JWT_PUBLIC_KEY_TEST` and `ENCRYPTION_KEY: TestEncryptionKey1234567890ABCDEF` (32 chars, hardcoded); uploads JaCoCo report `if: always()`.
  - `frontend`: `setup-node@v4` (20, npm cache); `npm ci` → `npm test -- --run` → `npm run build`.
  - `docker`: `needs: [backend, frontend]`; `docker compose build --no-cache` with placeholder JWT keys + same `ENCRYPTION_KEY`.
- [`.github/workflows/security-scan.yml`](../../.github/workflows/security-scan.yml) (78 lines, valid YAML). Triggers on `workflow_run` (after "CI" succeeds on `main`) or `workflow_dispatch`. Job `zap-scan`: same postgres service, `mvnw package -DskipTests`, starts backend jar in background + `sleep 30` + curl health-check `|| exit 1`, runs `zaproxy/action-baseline@v0.12.0` against `http://localhost:8080` with `.github/zap-rules.tsv`, `fail_action: true`, uploads html+json report `if: always()`.
- [`.github/zap-rules.tsv`](../../.github/zap-rules.tsv) (7 lines): 3 `IGNORE` rules (10035 CSP header, 10016 X-XSS-Protection, 10038 CSP report-only) each with a documented justification (handled by `HttpSecurityHeadersFilter` / dev-only).

**Caveats:**
1. This verifies *structure and syntax only* — no `gh run list` was executed to confirm either workflow has ever successfully run on GitHub. Given the branch is 13 commits ahead of `origin/main` (unpushed), neither workflow has triggered on this work. Tracked as a Phase 5 follow-up.
2. `ENCRYPTION_KEY: TestEncryptionKey1234567890ABCDEF` is a **hardcoded test secret committed to a tracked file** (`ci.yml`). For a CI-only AES key (not a real production secret) this is common practice, but it is inconsistent with the `secrets.*` pattern used for JWT keys in the same job. Flag for Phase 2 security audit — recommend moving to a repo secret for consistency, low severity.

---

## 11. T5 Verdict — CORS Env Var + Three-Way Matching (Phase 9D) Completeness

**Verdict: ⚠️ PARTIAL.**

### T5a — CORS reads from env var: ✅ VERIFIED, with a deployment gap

- [`CorsConfig.java:12`](../../src/main/java/com/oct/invoicesystem/config/CorsConfig.java#L12): `@Value("${app.cors.allowed-origins:http://localhost:3000}")`, splits on `,`, applies to `/**` with credentials + 1h max-age.
- [`application.yaml:112`](../../src/main/resources/application.yaml#L112) (default profile): `${ALLOWED_ORIGINS:http://localhost:3000}` — has a localhost fallback.
- [`application.yaml:336`](../../src/main/resources/application.yaml#L336) (`prod` profile): `${ALLOWED_ORIGINS}` — **no fallback**, must be set or Spring throws `PlaceholderResolutionException` at startup.
- **Gap found:** `ALLOWED_ORIGINS` is **not set anywhere** in `docker-compose.yml`, `.env`, or `.env.example`. Under the default profile this silently degrades to `localhost:3000` (CORS would reject any non-localhost frontend origin in a Docker/deployed context); under the `prod` profile the backend would **fail to start**. This is a real configuration gap — logged for Phase 5/Phase 8 (`ISS-` or `REQ-` TBD), not a T5 code defect (the code itself correctly reads the env var as designed).

### T5b — Three-Way Matching (Phase 9D) completeness: ⚠️ PARTIAL

**Backend — ✅ complete:**
- Domain `purchasing`: `MatchingConfig`, `MatchingStatus`, `ThreeWayMatchingResult` (+ repositories), `MatchingConfigService`, `ThreeWayMatchingService`.
- Endpoints: `GET/POST /api/v1/matching-config` ([MatchingConfigController.java](../../src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingConfigController.java), `@PreAuthorize` ADMIN for update, ADMIN+ASSISTANT_COMPTABLE for read — tolerance %, tolerance amount, require-GRN flag, all DB-stored per CLAUDE.md §Phase9); `GET /api/v1/invoices/{id}/matching` and `POST /api/v1/invoices/{id}/matching/override` on [InvoiceController.java:121,180](../../src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java#L121).
- Tests (Phase 0 baseline): `ThreeWayMatchingServiceTest` 10/10 ✅, `ThreeWayMatchingIntegrationTest` 3/3 ✅.
- `MatchingResultDTO` includes `purchaseOrderId` and `goodsReceiptNoteId` fields.
- `PurchaseOrdersPage.tsx` (232 lines) and `GoodsReceiptsPage.tsx` (211 lines) exist as standalone CRUD/list pages with line-item data (`items: { itemDescription, quantity, unitPrice }[]`).

**Frontend — ⚠️ partial, embedded in `InvoiceDetailPage.tsx` (no dedicated matching component):**
- ✅ Status indicator (`MatchingBadge`: MATCHED/PARTIAL/MISMATCH/OVERRIDDEN, [InvoiceDetailPage.tsx:37-51](../../frontend/src/pages/InvoiceDetailPage.tsx#L37-L51))
- ✅ Discrepancy display (`discrepancyNotes` text blob, [InvoiceDetailPage.tsx:203-208](../../frontend/src/pages/InvoiceDetailPage.tsx#L203-L208))
- ✅ Manual override with justification — gated by role (`ROLE_DAF`/`ROLE_ADMIN`/`ROLE_ASSISTANT_COMPTABLE`), status (`MISMATCH` only), and invoice state (`BROUILLON`/`REJETE` only); min-10-char validation ([InvoiceDetailPage.tsx:218-252](../../frontend/src/pages/InvoiceDetailPage.tsx#L218-L252))
- ❌ **No PO/GRN line-item display in the matching panel itself** — `purchaseOrderId`/`goodsReceiptNoteId` from `MatchingResultDTO` are never fetched/rendered; PO data is only visible by navigating away to `PurchaseOrdersPage`
- ❌ **No line-by-line comparison table** (Module 5: "Comparaison ligne à ligne") — discrepancies are a free-text note, not structured per-line data
- ❌ **No matching-config UI** — tolerance thresholds are configurable via API (ADMIN-only) but there is no admin page to view/edit them
- ❌ **No matching history view** — `ThreeWayMatchingResult` is append-only (per CLAUDE.md §Phase9), implying re-matching could produce multiple records, but the UI only fetches the single latest result via `GET /{id}/matching`
- ❌ **No export of matching reports** (Module 5: "Export des rapports de matching")

**Conclusion:** The T5 plan's literal scope ("service+tests+endpoints+UI exist and function") is met — the backend is fully built and tested, and the frontend UI is functional for the core workflow (view status, see why it mismatched, override with justification). However, against the full **Module 5 REQUIREMENTS-MATRIX** criteria, 5 of 10 sub-items are absent from the UI. These gaps are logged as `REQ-` items for Phase 6/8, not as T5 regressions — T5 (commit `aaeabdd`, "V17-V19 already existed, service was complete") is accurate as far as it goes; it did not claim full Module 5 UI parity.

---

## 12. T6 Verdict — V39/V40 Migrations + ActiveSession/ApprovalDelegation End-to-End

**Verdict: ⚠️ PARTIAL — ActiveSession ✅ complete; ApprovalDelegation ❌ backend-only, no frontend.**

### Migrations — ✅ both verified, well-formed

- [`V39__create_active_sessions.sql`](../../src/main/resources/db/migration/V39__create_active_sessions.sql): `active_sessions` table (`user_id` FK, `refresh_token` UNIQUE, `ip_address`, `user_agent`, `expires_at`, `revoked`/`revoked_at`); 2 partial indexes on `user_id`/`expires_at` WHERE `revoked = FALSE`.
- [`V40__create_approval_delegations.sql`](../../src/main/resources/db/migration/V40__create_approval_delegations.sql): `approval_delegations` table (`delegator_id`/`delegatee_id`/`created_by` FKs, `department_code`, `from_date`/`to_date`, `reason`); `CHECK (to_date >= from_date)` and `CHECK (delegator_id <> delegatee_id)` constraints; 2 partial indexes on `(delegatee_id, to_date)` and `(department_code, to_date)` WHERE `revoked = FALSE`.

### ActiveSession (V39) — ✅ COMPLETE end-to-end

- Backend: [`ActiveSession.java`](../../src/main/java/com/oct/invoicesystem/domain/auth/model/ActiveSession.java) entity, [`ActiveSessionRepository.java`](../../src/main/java/com/oct/invoicesystem/domain/auth/repository/ActiveSessionRepository.java) (`findAllActive`, `revokeAllForUser`), [`AdminSessionController.java`](../../src/main/java/com/oct/invoicesystem/domain/user/controller/AdminSessionController.java): `GET /api/v1/admin/sessions` (list all active, ADMIN-only) + `DELETE /api/v1/admin/sessions/user/{userId}` (revoke all for user, ADMIN-only).
- Frontend: [`SecuritySettingsPage.tsx`](../../frontend/src/pages/admin/SecuritySettingsPage.tsx) (183 lines) — "Active Sessions" section ([lines 135-183](../../frontend/src/pages/admin/SecuritySettingsPage.tsx#L135-L183)): fetches via `useQuery(['admin','sessions'])` → `GET /api/v1/admin/sessions`, renders a table (username, IP, created/expires), "Revoke" button → `DELETE /api/v1/admin/sessions/user/{userId}` with `useMutation` + query invalidation. Loading and empty states present (`sessionsLoading`, "Aucune session active.").
- Module 13 ("Vue de gestion des sessions") requirement: ✅ satisfied.

**Note:** `AdminSessionController` injects `ActiveSessionRepository` directly (no service layer) — this is one of the 5 controllers flagged in BASELINE.md §8 item 4 / Phase 1 architecture gap. Functionally correct, but a CLAUDE.md §3 layering violation.

### ApprovalDelegation (V40) — ❌ BACKEND-ONLY, no frontend consumer

- Backend — fully built and tested:
  - [`ApprovalDelegation.java`](../../src/main/java/com/oct/invoicesystem/domain/workflow/model/ApprovalDelegation.java) entity, [`ApprovalDelegationRepository.java`](../../src/main/java/com/oct/invoicesystem/domain/workflow/repository/ApprovalDelegationRepository.java) (`findActiveDelegationsForDepartment`, `findActiveDelegationsForDelegatee` — both correctly filter `revoked=false AND fromDate<=today<=toDate`).
  - [`DelegationService.java`](../../src/main/java/com/oct/invoicesystem/domain/workflow/service/DelegationService.java): `createDelegation` (validates no self-delegation, `toDate >= fromDate`, `@Slf4j` logging), `getActiveDelegationsForDepartment`, `revokeDelegation` (soft: sets `revoked=true`+`revokedAt`). `DelegationServiceTest` 3/3 ✅ (per Phase 0 baseline).
  - [`DelegationController.java`](../../src/main/java/com/oct/invoicesystem/domain/workflow/controller/DelegationController.java): `POST /api/v1/approvals/delegations`, `GET /api/v1/approvals/delegations?departmentCode=`, `DELETE /api/v1/approvals/delegations/{id}` — all `@PreAuthorize("hasRole('ADMIN')")`.
  - **Integration confirmed** (from prior session analysis): `ApprovalServiceImpl.checkRole()` ([ApprovalServiceImpl.java:204-221](../../src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java#L204-L221)) calls `delegationRepository.findActiveDelegationsForDelegatee(user.getId(), LocalDate.now())` as a fallback when the user lacks the direct required role — i.e., delegation actually affects the approval workflow at runtime, not just CRUD.
- Frontend — **nothing**:
  - No `.tsx`/`.ts` file references `/api/v1/approvals/delegations`, `DelegationService`, or any delegation API call.
  - i18n keys exist in both [`en.json:367-377`](../../frontend/src/i18n/en.json#L367-L377) and `fr.json` (`delegate`, `delegation.title/to/from/until/reason/save/active/none`) but are **orphaned** — grep across `frontend/src/**/*.{ts,tsx}` for `delegation.` returns zero matches. The translation keys were added (likely during T7's i18n pass) anticipating a UI that was never built.
  - No admin page exists to create, list, or revoke delegations. An ADMIN can only manage delegations via direct API calls (curl/Postman/Swagger UI).

**Note:** `DelegationController` injects `UserRepository` directly (no `UserService`/`SecurityHelper` for the `findById` lookups) — the 5th of the 5 controllers flagged in BASELINE.md §8 item 4.

### Conclusion

`V39`/`V40` migrations are both correctly written and applied (assumed — Flyway runs on `mvnw test` via Testcontainers, and `DelegationServiceTest` 3/3 passing implies the schema loads). **ActiveSession is fully end-to-end (T6 claim ✅ accurate for this half).** **ApprovalDelegation is backend-complete but has zero frontend — T6's claim of "functional end-to-end backend+frontend" is ❌ NOT accurate for this half.** This is a genuine gap: Module 6 ("Délégation d'approbation") and Module 13 list delegation management as a UI requirement, and orphaned i18n keys are direct evidence the UI was planned but not delivered. Logged as `REGRESSION-T6-02` (frontend delegation UI missing) for Phase 8/`ISSUES.md` — distinct from `REGRESSION-T6-01` (the `ApprovalServiceTest` mock NPE, already root-caused in §9).

---

## 13. T7 Verdict — i18n Sidebar, @Operation Coverage, Missing Tests (m-01..m-11)

**Verdict: ⚠️ PARTIAL — 7/9 checked items ✅, 2 REGRESSIONS, plus 1 NEW finding outside m-01..m-11's original scope.**

Per [`docs/superpowers/plans/2026-06-06-audit-corrections-completes.md:2032`](../superpowers/plans/2026-06-06-audit-corrections-completes.md#L2032), T7 claimed to resolve m-01 through m-11. Each verified individually:

| ID | Description | Verdict | Evidence |
|---|---|---|---|
| m-01 | Hardcoded labels in `Sidebar.tsx` | ❌ **REGRESSION** | 3 nav keys (`nav.payments`, `nav.goodsReceipts`, `nav.archive`) used at [Sidebar.tsx:144-145,149](../../frontend/src/components/layout/Sidebar.tsx#L144-L149) are **missing from both `en.json` and `fr.json`**. Each call site has an inline fallback string in **French** ("Paiements", "Bons de Réception", "Archive") — when locale=`en`, i18next falls back to these French strings, so the English UI shows French labels for Payments/Goods Receipts/Archive nav items. |
| m-02 | `@Operation` on `AuditController` | ✅ verified | All endpoints annotated, confirmed via grep. |
| m-03 | `@Operation` on `AuthController` | ✅ verified | All endpoints annotated, confirmed via grep. |
| m-04 | Archive full-text search | ✅ verified | [`ArchivePage.tsx:24-38`](../../frontend/src/pages/ArchivePage.tsx#L24-L38): `search`/`deptFilter`/date-range state wired to `useQuery(['archive', search, deptFilter, fromDate, toDate, page])` → `params.keyword`/`params.department`. Backend support confirmed present in `InvoiceController`/`InvoiceRepository`/`InvoiceService`. |
| m-05 | (T6 scope, not T7 — see §12) | — | n/a here |
| m-06 | Tests for `AuthRehydrator` + `SupplierRoute` | ❌ **REGRESSION** | `AuthRehydrator` exists at [`App.tsx:23`](../../frontend/src/App.tsx#L23) (calls `GET /profile` on mount, dispatches to Redux store — implements PROB-001's rehydration rule). `SupplierRoute` exists in [`ProtectedRoute.tsx`](../../frontend/src/components/auth/ProtectedRoute.tsx) and is used in `AppRoutes.tsx` (PROB-002's separate-guard pattern). **However, [`App.test.tsx`](../../frontend/src/App.test.tsx) is a placeholder stub** (`test('true is true', () => expect(true).toBe(true))`) — it does not exercise `AuthRehydrator` at all. No dedicated test file exists for `SupplierRoute` either. |
| m-07 | (DATABASE.md update) | not checked | Documentation-only item, deferred to Phase 11 doc review. |
| m-08 | Audit sub-typing | ✅ verified | `AuditLog.action` ([AuditLog.java:37](../../src/main/java/com/oct/invoicesystem/domain/audit/model/AuditLog.java#L37)) is a plain `String`, not a Java enum — but [`AuditController.java:41-47`](../../src/main/java/com/oct/invoicesystem/domain/audit/controller/AuditController.java#L41-L47) defines `SYSTEM_ACTIONS` (Admin-visible: `HTTP_REQUEST`, `LOGIN`, `LOGOUT`, ...) and `FINANCIAL_ACTIONS` (DAF-visible: `INVOICE`, `APPROVE`, `REJECT`, ...) keyword lists, and `auditService.searchLogsWithActionFilter(...)` filters by these categories on 2 of 3 endpoints. This is genuine differentiation (string-keyword categorization), satisfying the "not all generic" requirement even without a Java enum. |
| m-09 | Duplicate "Suppliers" entry in `Sidebar.tsx` | ❌ **REGRESSION** | `/admin/suppliers` `NavItem` appears **twice**: [Sidebar.tsx:138](../../frontend/src/components/layout/Sidebar.tsx#L138) (inside `RoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE']}`, labeled "AA: Suppliers") and [Sidebar.tsx:172](../../frontend/src/components/layout/Sidebar.tsx#L172) (inside the `ROLE_ADMIN` admin section). A user holding both `ROLE_ASSISTANT_COMPTABLE` and `ROLE_ADMIN` would see "Suppliers" twice in the nav. |
| m-10 | `@Slf4j` on `SupplierController` | ✅ verified | [SupplierController.java:49](../../src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java#L49). |
| m-11 | Audit trail for `UserProfileController` | ✅ verified | [`UserService.java:185`](../../src/main/java/com/oct/invoicesystem/domain/user/service/UserService.java#L185): `updateProfile()` calls `auditService.logAction(userId, "USER", userId.toString(), "PROFILE_UPDATE", ...)`. Correctly placed at the service layer, not the controller. |

### NEW finding — `@Operation`/`@Tag` entirely absent on 3 controllers (outside m-01..m-11's original scope)

T7's commit description claims "Swagger @Operation on all endpoints" as a general (not just m-02/m-03-scoped) outcome. A full sweep of all `*Controller.java` files found **3 controllers with zero `@Operation` AND zero `@Tag`** — completely undocumented in the OpenAPI/Swagger contract:

- [`SupplierController.java`](../../src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java) — **12 endpoints**, 0 `@Operation`, 0 `@Tag` (verified via direct grep of all `@Get/Post/Put/Patch/DeleteMapping` and `@Operation`/`@Tag` lines — the file has m-10's `@Slf4j` but no Swagger annotations at all)
- [`WebhookController.java`](../../src/main/java/com/oct/invoicesystem/domain/webhook/controller/WebhookController.java) — 4 endpoints (`POST`/`GET /`, `DELETE /{id}`, `GET /{id}/deliveries`), 0 `@Operation`, 0 `@Tag`
- [`IntegrationStatusController.java`](../../src/main/java/com/oct/invoicesystem/domain/webhook/controller/IntegrationStatusController.java) — 1 endpoint, 0 `@Operation`, 0 `@Tag`

This affects **Module 8 (Supplier Management)** and **Module 12 (Integration/Webhooks)** API documentation coverage. Logged as a new `ISS-` item for Phase 8 (not a `REGRESSION-T7-xx` since it was never claimed as fixed by name in m-01..m-11 — but it does contradict T7's general "Swagger @Operation on all endpoints" framing).

### Summary

- ✅ **6/9** directly-checked m-items hold (m-02, m-03, m-04, m-08, m-10, m-11)
- ❌ **2/9** are regressions: **m-01** (3 sidebar i18n keys missing, French leaking into English UI) and **m-09** (duplicate Suppliers nav entry persists)
- ❌ **1/9** (m-06) is a regression: tests for `AuthRehydrator`/`SupplierRoute` were never written (`App.test.tsx` is a placeholder)
- ⏸ m-05 is T6-scoped (see §12), m-07 (DATABASE.md) deferred to Phase 11
- 🆕 1 new finding: 3 controllers (17 endpoints total) with zero Swagger documentation, contradicting T7's general API-doc claim

Logged for Phase 8: `REGRESSION-T7-01` (m-01, sidebar i18n), `REGRESSION-T7-02` (m-09, duplicate nav), `REGRESSION-T7-03` (m-06, missing AuthRehydrator/SupplierRoute tests), `ISS-` TBD (SupplierController/WebhookController/IntegrationStatusController missing `@Operation`/`@Tag`).

---

## 14. T1-T7 Cross-Verification Summary (Phase 7, completed early per audit prompt requirement)

| Task | Verdict | Notes |
|---|---|---|
| T1 | ✅ (code-level) | `isAccountNonLocked()` ✅, bank-details `@Convert(EncryptionAttributeConverter)` ✅, `EncryptionUtil` genuinely AES-256/GCM ✅, V35 migration well-formed ✅. SQL-level proof against a live DB deferred (Postgres topology open item, §8.1). |
| T2 | ✅ | `AuditLoggingFilter` correctly wired via `addFilterAfter(..., UsernamePasswordAuthenticationFilter.class)` at [SecurityConfig.java:75](../../src/main/java/com/oct/invoicesystem/config/SecurityConfig.java#L75). Runtime proof captured incidentally (filter fired on a real 403 during `ReportControllerTest`). |
| T3 | ✅ (original scope) | 8 originally-targeted controllers are 100% clean of repository injection and use `SecurityHelper`. **Separately**, 5 *other* controllers (`InvoiceDocumentController`, `AdminSessionController`, `IntegrationStatusController`, `WebhookController`, `DelegationController`) inject repositories directly — a **new** Phase 1 architecture finding, not a T3 regression (these were built in T6/Phase 10, after T3's scope was defined). |
| T4 | ✅ (structural) | `ci.yml`, `security-scan.yml`, `zap-rules.tsv` all exist, valid YAML/TSV, structurally match plan. Never run on GitHub yet (branch unpushed) — minor caveat. |
| T5 | ⚠️ PARTIAL | CORS env var ✅ but `ALLOWED_ORIGINS` unset in docker-compose/.env (deployment gap). Three-Way Matching backend ✅ complete+tested; frontend UI functional for core flow but missing 5/10 Module-5 sub-items (PO/GRN line display, comparison table, config UI, history, export). |
| T6 | ⚠️ PARTIAL | V39/V40 migrations ✅ well-formed. ActiveSession ✅ fully end-to-end (backend+frontend). ApprovalDelegation backend ✅ complete+tested+integrated into `checkRole()`, but **zero frontend** — orphaned i18n keys prove UI was planned but never built (`REGRESSION-T6-02`). |
| T7 | ⚠️ PARTIAL | 6/9 m-items ✅. 3 regressions: m-01 (sidebar i18n, 3 keys), m-09 (duplicate Suppliers nav), m-06 (no tests for AuthRehydrator/SupplierRoute). New finding: 3 controllers (17 endpoints) with zero Swagger `@Operation`/`@Tag`. |

**Overall**: T1/T2/T3/T4 hold up under cross-verification (T1 pending only a deferred SQL-level check). T5/T6/T7 each have real, specific gaps — none are catastrophic, all are scoped and actionable for Phase 10. No task was found to be entirely false or fabricated. Total new `REGRESSION-` items opened: **3** (`REGRESSION-T6-02`, `REGRESSION-T7-01`, `REGRESSION-T7-02`, `REGRESSION-T7-03` — 4 actually, see list below) plus the pre-existing `REGRESSION-T6-01` (ApprovalServiceTest NPE, §9).

**Full regression list for `docs/audit/ISSUES.md` (Phase 8):**
1. `REGRESSION-T6-01` — `ApprovalServiceTest` NPE, missing `@Mock ApprovalDelegationRepository` (§9)
2. `REGRESSION-T6-02` — ApprovalDelegation has no frontend UI (§12)
3. `REGRESSION-T7-01` — Sidebar i18n: 3 missing nav keys, French fallback leaks into English UI (§13)
4. `REGRESSION-T7-02` — Duplicate "Suppliers" nav entry for AA+ADMIN users (§13)
5. `REGRESSION-T7-03` — No tests for `AuthRehydrator`/`SupplierRoute` (§13)
