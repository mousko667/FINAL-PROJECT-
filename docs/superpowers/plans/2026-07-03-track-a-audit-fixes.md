# Track A — Audit Fixes (AUDIT_GENERAL_2026-07-02) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 🔴 blocker + confirmed 🟠 majeurs + rentable 🟡 minors from the 2026-07-02 audit, leaving the BAP workflow functional and closing the confirmed security/integrity/i18n gaps — before the Track B visual redesign.

**Architecture:** Backend = Spring Boot 3.4 / Java 21 layered (controller → service → repository), `@PreAuthorize` on every endpoint, DTOs never expose entities, Flyway for schema. Frontend = React 19 + TS + React-Query + i18next, axios `apiClient`. Fixes are surgical, each an independently-testable task with its own commit and `KNOWN_ISSUES_REGISTRY` entry.

**Tech Stack:** Spring Boot 3.4, Java 21, PostgreSQL 18, Flyway, JUnit 5 + AssertJ + MockMvc, React 19, TypeScript, Vitest + Testing Library, i18next.

## Global Constraints

- **Test gate:** a task is done only at `./mvnw test` (0 failures/errors) + `npx vitest run` (0 failures) + `npx tsc --noEmit` (0 errors). No "pre-existing failure" excuse except the documented flaky vitest timeout (green in isolation).
- **Backend `messages_fr.properties` is ISO-8859-1 (Latin-1):** append via `iconv`, never direct UTF-8, never em-dash/curly quotes. `fr.json`/`en.json` frontend are UTF-8.
- **`ROLE_ADMIN` never accesses financial data** (separation of duties).
- **Every user-facing string** goes through `t()` (frontend) / `MessageSource` (backend), in both FR and EN. Never hardcode.
- **Every bug fixed** → `docs/KNOWN_ISSUES_REGISTRY.md` entry (root cause + fix + preventive rule) BEFORE the commit; update `docs/TASKS.md`.
- **Commits:** one per task, message `type(scope): description`, never `--no-verify`. **Push:** only on explicit user approval (propose a push once unpushed count hits 10).
- **PostgreSQL dev DB** at `localhost:5433` (integration tests via `AbstractPostgresIntegrationTest`, Flyway off there — those tests must stay read-only and self-provision their own writable fixtures inside the rolled-back transaction).
- **Next Flyway migration number = V43** (contiguous after V42). V43 = admin account (A9); V44 = department names (A15.5).
- **apiClient base URL already includes `/api/v1`** — frontend calls use paths like `/invoices/...` (no `/api/v1` prefix). Verify against existing calls before adding new ones (PROB-038 double-prefix lesson).

---

## Task 0: Make ArchiveFolderIntegrationTest independent of admin mfa_secret (unblock the tree)

**Why:** `./mvnw test` = 539/0/**3**; all 3 errors are `ArchiveFolderIntegrationTest.setUp:37` → `AEADBadTagException` decrypting `admin.mfaSecret` (encrypted with an ad-hoc AES key from the 2026-07-02 audit session; the `test` profile key differs). Not an archive bug. `createFolder`/`updateFolder` only use the `User` for the `createdBy` FK, never its encrypted fields — a lazy proxy avoids the decryption entirely.

**Files:**
- Modify: `src/test/java/com/oct/invoicesystem/domain/invoice/service/ArchiveFolderIntegrationTest.java:35-38`
- Reference: `src/main/java/com/oct/invoicesystem/domain/invoice/service/ArchiveFolderServiceImpl.java:31-57` (uses `user` only via `.createdBy(user)`)

**Interfaces:**
- Consumes: `UserRepository` (already `@Autowired`), Spring Data JPA `getReferenceById(UUID)`.
- Produces: a green backend suite (539/0/0) that other tasks depend on as the gate baseline.

- [ ] **Step 1: Run the failing tests to capture the baseline**

Run: `./mvnw test -Dtest=ArchiveFolderIntegrationTest`
Expected: 3 errors, `JpaSystemException: Error attempting to apply AttributeConverter` … `AEADBadTagException: Tag mismatch` at `setUp:37`.

- [ ] **Step 2: Replace the eager admin fetch with an id-only lookup + lazy reference**

In `ArchiveFolderIntegrationTest.java`, change the `setUp` so it never materializes an encrypted column. Fetch **any** existing user's id via a lightweight query and use a lazy proxy for the FK:

```java
    @BeforeEach
    void setUp() {
        // Do NOT load the admin entity: its mfa_secret was encrypted with a different AES key
        // (2026-07-02 audit session) and is undecryptable under the test profile key
        // (AEADBadTagException). createFolder/updateFolder only need the createdBy FK, so a
        // lazy reference by id avoids loading — and decrypting — any column of the user row.
        UUID adminId = userRepository.findByUsername("admin")
                .map(User::getId)
                .orElseGet(() -> userRepository.findAll().stream().findFirst()
                        .map(User::getId)
                        .orElseThrow(() -> new IllegalStateException("No user seeded in dev DB")));
        admin = userRepository.getReferenceById(adminId);
    }
```

Note: `findByUsername(...).map(User::getId)` still triggers a load of the admin row — **that is the problem**. Use a projection that selects only the id instead. Add to `UserRepository`:

```java
    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.username = :username")
    java.util.Optional<UUID> findIdByUsername(String username);
```

Then in the test:

```java
    @BeforeEach
    void setUp() {
        UUID adminId = userRepository.findIdByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("admin not seeded in dev DB"));
        admin = userRepository.getReferenceById(adminId); // lazy proxy, no column read/decryption
    }
```

Add the `import java.util.UUID;` if not present.

- [ ] **Step 3: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=ArchiveFolderIntegrationTest`
Expected: `Tests run: 3, Failures: 0, Errors: 0`.

- [ ] **Step 4: Run the full backend suite to confirm the gate**

Run: `./mvnw test`
Expected: `Tests run: 539, Failures: 0, Errors: 0, Skipped: 0` (or higher if a new test was seeded — none here).

- [ ] **Step 5: Log the issue and commit**

Add to `docs/KNOWN_ISSUES_REGISTRY.md` a PROB entry (next free number): root cause = admin `mfa_secret` encrypted with a non-test AES key from the audit session; fix = id-only projection + `getReferenceById` lazy proxy; preventive rule = "integration tests against the shared dev DB must never eagerly load a row carrying an `@Convert(EncryptionAttributeConverter)` column unless they need that field — use an id projection + lazy reference."

```bash
git add src/test/java/com/oct/invoicesystem/domain/invoice/service/ArchiveFolderIntegrationTest.java src/main/java/com/oct/invoicesystem/domain/invoice/repository/UserRepository.java docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(test): decouple ArchiveFolderIntegrationTest from admin mfa_secret (AES key drift)"
```

---

## Task 1: Commit the existing uncommitted tree in thematic commits

**Why:** The tree carries ~75 uncommitted files (M8#10 onboarding, archive folders, backups, matching-line resolution, i18n/refactor cleanups). With the gate now green (Task 0), commit this prior work in clean thematic commits so audit fixes layer on a legible base. **This is a human-supervised bundling step — no code changes.**

**Files:** all currently-uncommitted paths (see `git status`).

- [ ] **Step 1: Re-confirm the full gate is green**

Run: `./mvnw test` → 539/0/0. `cd frontend && npx tsc --noEmit` → 0. `npx vitest run` → 80/80 (rerun the single flaky `InvoiceCreateDuplicateWarning` file in isolation if it times out under load).

- [ ] **Step 2: Group and commit by theme** (adjust paths to actual `git status`)

```bash
# archive folders (feature)
git add src/main/java/com/oct/invoicesystem/domain/invoice/**/ArchiveFolder* \
        src/main/resources/db/migration/V36__create_archive_folders.sql \
        src/main/resources/db/migration/V37__add_invoice_folder_id.sql \
        frontend/src/components/archive frontend/src/types/archive.ts \
        frontend/src/test/components/ArchiveFolderTree.test.tsx \
        src/test/java/com/oct/invoicesystem/domain/invoice/service/ArchiveFolder*Test.java
git commit -m "feat(archive): folder tree (M9 #1) — backend + UI + tests"

# backups admin
git add src/main/java/com/oct/invoicesystem/domain/compliance/**/Backup* \
        src/main/resources/db/migration/V39__create_backup_audit_logs.sql \
        frontend/src/api/backups.ts frontend/src/pages/admin/AdminBackupsPage.tsx \
        src/test/java/com/oct/invoicesystem/domain/compliance/service/BackupServiceIntegrationTest.java
git commit -m "feat(backups): admin backup listing/restore (M9 #8) — backend + UI + tests"

# matching line resolution
git add src/main/java/com/oct/invoicesystem/domain/purchasing/**/*LineResolution* \
        src/main/resources/db/migration/V38__* src/main/resources/db/migration/V40__* \
        src/main/resources/db/migration/V41__* src/main/resources/db/migration/V42__* \
        frontend/src/components/matching frontend/src/services/matchingService.ts \
        frontend/src/pages/matching/MatchingDetailPage.tsx
git commit -m "feat(matching): per-line resolution (M5 #10 scaffold) — backend + UI"

# onboarding + remaining supplier/report/i18n cleanups (the rest)
git add -A
git commit -m "chore: supplier onboarding wizard + i18n/refactor cleanups (M8 #10)"
```

- [ ] **Step 3: Verify a clean tree**

Run: `git status` → nothing to commit, working tree clean. `git log --oneline -5`.

*(No test step beyond Step 1 — this task ships no behavior change.)*

---

## Task 2: 🔴 BLOCKER — Expose "Start review" (SOUMIS → EN_VALIDATION_N1) + surface SOUMIS in the queue

**Why:** Nothing in `frontend/src` calls `POST /invoices/{id}/workflow/assign` (event `ASSIGN_REVIEWER`). `InvoiceActionPanel` shows no button on `SOUMIS`; `ApprovalQueuePage` only queries `EN_VALIDATION_N1/N2/VALIDE`. Every submitted invoice is stuck forever. Backend is ready (`ApprovalController.java:57-70`, allows AA + DAF + ADMIN + all N1/N2).

**Files:**
- Modify: `frontend/src/components/invoice/InvoiceActionPanel.tsx` (mutation switch + button list)
- Modify: `frontend/src/pages/ApprovalQueuePage.tsx` (include SOUMIS for N1/AA)
- Modify: `frontend/src/i18n/fr.json`, `frontend/src/i18n/en.json` (`invoice.startReview`)
- Test: `frontend/src/test/components/InvoiceActionPanel.startReview.test.tsx` (new)

**Interfaces:**
- Consumes: `apiClient.post('/invoices/{id}/workflow/assign')`; `invoice.status`, `user.roles`.
- Produces: an `ASSIGN_REVIEWER` action wired end-to-end; N1/AA queues that list `SOUMIS`.

- [ ] **Step 1: Add the i18n keys**

`fr.json` → `invoice.startReview`: `"Démarrer la revue"`; also `approvals.roleLabel.soumis`: `"Factures à prendre en charge"` and `approvals.tab.toAssign`/`approvals.tab.toValidate` if tabs are used. `en.json` → `"Start review"`, `"Invoices to take charge of"`, etc. Keep the two files symmetric.

- [ ] **Step 2: Write the failing test**

```tsx
// InvoiceActionPanel.startReview.test.tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InvoiceActionPanel } from '@/components/invoice/InvoiceActionPanel'
// (reuse the store/query/i18n test harness pattern from existing InvoiceActionPanel tests)

it('shows Start review on a SOUMIS invoice for an AA and posts /workflow/assign', async () => {
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { data: null } })
  renderWithProviders(<InvoiceActionPanel invoice={{ id: 'inv-1', status: 'SOUMIS', /* ... */ } as any} />,
    { user: { roles: ['ROLE_ASSISTANT_COMPTABLE'] } })
  const btn = screen.getByRole('button', { name: /Démarrer la revue|Start review/ })
  await userEvent.click(btn)
  expect(post).toHaveBeenCalledWith('/invoices/inv-1/workflow/assign')
})
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npx vitest run src/test/components/InvoiceActionPanel.startReview.test.tsx`
Expected: FAIL — no "Démarrer la revue" button found.

- [ ] **Step 4: Add the action to the mutation and the button list**

In `InvoiceActionPanel.tsx` mutation switch, add a case:

```tsx
        case 'ASSIGN_REVIEWER':
          return apiClient.post(`/invoices/${invoiceId}/workflow/assign`)
```

Add the button (visible on SOUMIS for AA + N1; mirror backend authz — AA and N1 are the natural "take charge" actors):

```tsx
  // Take charge of a submitted invoice (SOUMIS → EN_VALIDATION_N1)
  if ((isAA || isN1) && status === 'SOUMIS') {
    buttons.push({ action: 'ASSIGN_REVIEWER', label: t('invoice.startReview', 'Start review'), variant: 'primary' })
  }
```

- [ ] **Step 5: Surface SOUMIS in the approval queue**

In `ApprovalQueuePage.tsx`, N1 and AA must see `SOUMIS`. Change the query so N1/AA fetch both `SOUMIS` and `EN_VALIDATION_N1` (two requests merged, or a repeated `status` param if the backend supports it). Minimal robust approach — fetch two statuses and concatenate:

```tsx
  // N1/AA take charge of SOUMIS, then validate EN_VALIDATION_N1; N2 sees N2; DAF sees VALIDE.
  const statuses = isDaf ? ['VALIDE'] : isN2 ? ['EN_VALIDATION_N2'] : ['SOUMIS', 'EN_VALIDATION_N1']

  const { data, isLoading, refetch } = useQuery({
    queryKey: ['approval-queue', statuses.join(','), departmentId],
    queryFn: async () => {
      const results = await Promise.all(statuses.map((status) => {
        const params: Record<string, string> = { status, size: '50', sort: 'createdAt,asc' }
        if (departmentId) params.department = departmentId
        return apiClient.get('/invoices', { params }).then((r) => r.data.data.content as PendingInvoice[])
      }))
      return { content: results.flat() }
    },
  })
```

**Before coding, verify** `GET /invoices?status=SOUMIS` returns the right rows for an N1 (PROB-005: page-empty is usually a wrong endpoint/param, not an empty DB). If the backend restricts `/invoices` by role such that N1 can't see SOUMIS, adjust to the correct listing endpoint instead.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `npx vitest run src/test/components/InvoiceActionPanel.startReview.test.tsx` → PASS.
Run: `npx tsc --noEmit` → 0 errors.

- [ ] **Step 7: Log and commit**

`KNOWN_ISSUES_REGISTRY.md`: root cause = frontend never called `/workflow/assign`; fix = ASSIGN_REVIEWER button on SOUMIS + queue includes SOUMIS; preventive rule = "for every backend workflow transition, confirm a UI path triggers it (grep the event/endpoint in frontend/src)." Update `docs/TASKS.md` Module 4.

```bash
git add frontend/src/components/invoice/InvoiceActionPanel.tsx frontend/src/pages/ApprovalQueuePage.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json frontend/src/test/components/InvoiceActionPanel.startReview.test.tsx docs/KNOWN_ISSUES_REGISTRY.md docs/TASKS.md
git commit -m "fix(workflow): expose Start review (SOUMIS→EN_VALIDATION_N1) + surface SOUMIS in queue"
```

---

## Task 3: 🟠 MAJEUR-4 — Remove the broken "Record Payment" button (BANK_TRANSFER)

**Why:** `InvoiceActionPanel.tsx:53-59` posts `paymentMethod: 'BANK_TRANSFER'`, absent from enum `VIREMENT/CHEQUE/ESPECES/MOBILE_MONEY` → always 400. Redundant with the `PaymentsPage` modal (which sends `VIREMENT` and captures method/date/reference). Decision (spec A6): delete the button.

**Files:** Modify `frontend/src/components/invoice/InvoiceActionPanel.tsx`.

- [ ] **Step 1: Remove the `MARK_PAID` button and mutation branch**

Delete the `case 'MARK_PAID':` block (lines ~53-59) from the mutation switch, and delete the button push:

```tsx
  // (DELETE) AA: record payment once BON_A_PAYER issued
  if (isAA && status === 'BON_A_PAYER') {
    buttons.push({ action: 'MARK_PAID', label: t('invoice.markPaid', 'Record Payment'), variant: 'primary' })
  }
```

Leave a short comment pointing to `PaymentsPage` as the correct path.

- [ ] **Step 2: Verify no other reference to MARK_PAID / markPaid remains**

Run: `grep -rn "MARK_PAID\|markPaid\|BANK_TRANSFER" frontend/src`
Expected: no matches in `InvoiceActionPanel.tsx` (the `invoice.markPaid` key may remain in i18n unused — leave it or remove; removing is cleaner).

- [ ] **Step 3: Run the gate**

Run: `npx tsc --noEmit` → 0. `npx vitest run src/test/components` → existing InvoiceActionPanel tests still pass (update any that asserted the MARK_PAID button).

- [ ] **Step 4: Log and commit**

`KNOWN_ISSUES_REGISTRY.md`: root cause = frontend posted a non-existent PaymentMethod enum value; fix = removed the redundant button (PaymentsPage modal is the real path); preventive rule = "frontend enum literals must match backend enums — never invent values."

```bash
git add frontend/src/components/invoice/InvoiceActionPanel.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(invoice): remove broken Record Payment button (invalid BANK_TRANSFER method)"
```

---

## Task 4: 🟠 MAJEUR-1 — Restrict invoice document endpoints (IDOR)

**Why:** `InvoiceDocumentController.java:65-84` (`GET /documents`, `GET /{docId}/download`) are `@PreAuthorize("isAuthenticated()")` — any authenticated user (incl. SUPPLIER) can list/download another supplier's or an internal invoice's documents. The supplier portal has its own guarded routes; these generic endpoints are for staff.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceDocumentController.java:66,77`
- Test: `src/test/java/.../InvoiceDocumentControllerTest.java` (existing or new)

**Interfaces:**
- Produces: `GET /documents` and `/download` return 403 for SUPPLIER, 200 for staff.

- [ ] **Step 1: Write/extend the failing test**

MockMvc test: a user with `ROLE_SUPPLIER` calling `GET /api/v1/invoices/{id}/documents` → expect `403`. A `ROLE_ASSISTANT_COMPTABLE` → `200`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=InvoiceDocumentControllerTest`
Expected: FAIL — SUPPLIER currently gets 200.

- [ ] **Step 3: Tighten the annotations**

Change both `@PreAuthorize("isAuthenticated()")` (lines 66 and 77) to:

```java
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -Dtest=InvoiceDocumentControllerTest` → PASS.

- [ ] **Step 5: Log and commit**

`KNOWN_ISSUES_REGISTRY.md`: IDOR — generic document endpoints open to SUPPLIER; fix = `!hasRole('SUPPLIER')`; preventive rule = "generic (non-portal) invoice endpoints must exclude SUPPLIER; suppliers use `/supplier/*` with `ensureOwnInvoice`."

```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceDocumentController.java src/test/java/**/InvoiceDocumentControllerTest.java docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(security): restrict invoice document list/download to non-supplier staff (IDOR)"
```

---

## Task 5: 🟠 MAJEUR-2 — Remove ADMIN from payment read endpoints (SoD)

**Why:** `PaymentController.java:72,80,91,99` allow `ADMIN` on the 4 payment reads. Rule `admin-no-financial-access` (PROB-065): ADMIN must not access financial data. `ReportController` already excludes ADMIN.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/controller/PaymentController.java:72,80,91,99`
- Test: `src/test/java/.../PaymentControllerTest.java`

- [ ] **Step 1: Write/extend the failing test**

MockMvc: `ROLE_ADMIN` calling each of `GET /payments`, `GET /payments/invoice/{id}`, `GET /payments/{id}/remittance`, `GET /payments/export` → expect `403`. `ROLE_DAF` → `200`/`2xx`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=PaymentControllerTest` → FAIL (ADMIN gets 2xx).

- [ ] **Step 3: Edit the 4 annotations**

Change each `@PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")` (lines 72, 80, 91, 99) to:

```java
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')")
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./mvnw test -Dtest=PaymentControllerTest` → PASS.

- [ ] **Step 5: Log and commit**

`KNOWN_ISSUES_REGISTRY.md` + reference PROB-065. Preventive rule already exists; note the 4 endpoints.

```bash
git add src/main/java/com/oct/invoicesystem/domain/payment/controller/PaymentController.java src/test/java/**/PaymentControllerTest.java docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(security): remove ADMIN from payment read endpoints (SoD, PROB-065)"
```

---

## Task 6: 🟠 MAJEUR-11 & MAJEUR-12 — Supplier performance: remove ADMIN + stop faking metrics

**Why:** MAJEUR-11 — `SupplierController.java:147` `GET /{id}/performance` allows ADMIN (financial data → SoD). MAJEUR-12 — `getPerformanceMetrics` (:150-164) fabricates metrics (`invoiceAccuracyRate=1.0`, `rejectionRate=0.0`, `averagePaymentDays=0.0`) on `ResourceNotFoundException` and calls `getSupplier(id)` twice.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java:147,150-164`
- Test: `src/test/java/.../SupplierControllerTest.java` or `SupplierIntegrationTest.java`

- [ ] **Step 1: Write the failing tests**

(a) `ROLE_ADMIN` → `GET /suppliers/{id}/performance` expects `403`; `ROLE_DAF` → `200`.
(b) `getPerformanceMetrics` for a non-existent supplier id → `404` (no fabricated metrics body).

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw test -Dtest=SupplierControllerTest` (or the integration test) → FAIL.

- [ ] **Step 3: Fix the annotation and the fallback**

Line 147: change `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` → `hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')`.
In `getPerformanceMetrics`: remove the `catch (ResourceNotFoundException …)` that fabricates metrics — let it propagate (GlobalExceptionHandler → 404). Remove the duplicate `getSupplier(id)` call.

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw test -Dtest=SupplierControllerTest` → PASS.

- [ ] **Step 5: Log and commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java src/test/java/**/Supplier*Test.java docs/KNOWN_ISSUES_REGISTRY.md docs/TASKS.md
git commit -m "fix(security): supplier /performance excludes ADMIN + no fabricated metrics on 404"
```

---

## Task 7: 🟠 MAJEUR-3 — Matching check must not swallow exceptions

**Why:** `InvoiceStateMachineServiceImpl.performMatchingCheck` (:198-204) only re-throws `WorkflowException`; any other exception (`ValidationException` "No active matching configuration", "no line items", data errors) is logged and swallowed → an invoice with a PO but no lines / inactive config passes `SOUMIS` without matching being evaluated. The MISMATCH guard becomes bypassable.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java:198-204`
- Test: `src/test/java/.../InvoiceStateMachineServiceTest.java` (or `InvoiceServiceTest`)

- [ ] **Step 1: Write the failing test**

Mock `threeWayMatchingService.match(...)` to throw a `ValidationException("No active matching configuration")`; assert that submitting an invoice with a `purchaseOrderId` **throws** (submission fails) rather than silently proceeding to `SOUMIS`.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=InvoiceStateMachineServiceTest` → FAIL (exception swallowed, no throw).

- [ ] **Step 3: Re-throw all exceptions on this critical path**

Replace the `catch` block:

```java
        } catch (WorkflowException e) {
            throw e;
        } catch (Exception e) {
            // Critical path: matching MUST be evaluated. Do not proceed to SOUMIS if it
            // cannot be — surface the failure instead of silently degrading (MAJEUR-3).
            log.error("Matching check could not be evaluated for invoice {}: {}", invoice.getId(), e.getMessage(), e);
            throw new WorkflowException("error.matching.evaluation_failed");
        }
```

Add `error.matching.evaluation_failed` to `messages_fr.properties` (via `iconv`, Latin-1) and `messages_en.properties`.

- [ ] **Step 4: Run to verify it passes + full suite**

Run: `./mvnw test -Dtest=InvoiceStateMachineServiceTest` → PASS. Then `./mvnw test` → confirm no other test relied on the swallow behavior (fix any that submitted a PO-linked invoice without a valid matching config in their fixture).

- [ ] **Step 5: Log and commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java src/main/resources/i18n/messages_fr.properties src/main/resources/i18n/messages_en.properties src/test/java/**/InvoiceStateMachineServiceTest.java docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(workflow): fail submission if three-way matching cannot be evaluated (MAJEUR-3)"
```

---

## Task 8: 🟠 MAJEUR-5 — Fill the 29 missing i18n keys + fix common.* → app.*

**Why:** Verified missing in `fr.json`/`en.json`: `supplier.onboarding.*`, `admin.backups.*`, `archiveFolders.*` (whole namespaces), and 3 wrong-namespace calls `common.actions`/`common.cancel`/`common.loading` (correct is `app.*`), plus `invoice.duplicateWarning`. Screens render raw keys or frozen English.

**Files:**
- Modify: `frontend/src/i18n/fr.json`, `frontend/src/i18n/en.json`
- Modify: `frontend/src/components/matching/MatchingLineResolveModal.tsx:73` (`common.cancel`→`app.cancel`)
- Modify: `frontend/src/pages/matching/MatchingDetailPage.tsx` (`common.actions`→`app.actions`)
- Modify: `frontend/src/pages/admin/DepartmentAccessPage.tsx:21` (`common.loading`→`app.loading`)
- Modify: `frontend/src/pages/admin/AdminBackupsPage.tsx:115` (`common.actions`→`app.actions`)
- Modify: `frontend/src/pages/InvoiceCreatePage.tsx:298` (ensure `invoice.duplicateWarning` key exists)

- [ ] **Step 1: Enumerate the exact keys each screen calls**

Run: `grep -rn "t('supplier.onboarding\|t('admin.backups\|t('archiveFolders\|t(\`archiveFolders\|duplicateWarning" frontend/src` and list every distinct key. Resolve each — this is the authoritative list to add.

- [ ] **Step 2: Add the namespaces to fr.json and en.json (symmetric)**

Add `supplier.onboarding`, `admin.backups`, `archiveFolders` objects with every key from Step 1, FR and EN. Add `invoice.duplicateWarning` (FR: "Une facture similaire existe déjà …"; EN: "A similar invoice already exists …").

- [ ] **Step 3: Fix the 4 common.* → app.* call sites**

Edit each file listed above, replacing `common.X` with `app.X`.

- [ ] **Step 4: Verify resolution**

Run: `node -e "const fr=require('./frontend/src/i18n/fr.json'); const en=require('./frontend/src/i18n/en.json'); const g=(o,p)=>p.split('.').reduce((a,k)=>a&&a[k],o); ['supplier.onboarding','admin.backups','archiveFolders','invoice.duplicateWarning','app.actions','app.cancel','app.loading'].forEach(k=>console.log(k, g(fr,k)!==undefined, g(en,k)!==undefined))"`
Expected: every line `true true`.
Run: `grep -rn "common\.\(actions\|cancel\|loading\)" frontend/src` → no matches.

- [ ] **Step 5: Run the gate + commit**

Run: `npx tsc --noEmit` → 0. `npx vitest run` → green.

```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json frontend/src/components/matching/MatchingLineResolveModal.tsx frontend/src/pages/matching/MatchingDetailPage.tsx frontend/src/pages/admin/DepartmentAccessPage.tsx frontend/src/pages/admin/AdminBackupsPage.tsx frontend/src/pages/InvoiceCreatePage.tsx docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(i18n): add supplier.onboarding/admin.backups/archiveFolders namespaces + common.*→app.*"
```

---

## Task 9: 🟠 MAJEUR-7 — ExportMenu error handling

**Why:** `ExportMenu.tsx:31-50` `download()` has no `catch`; on 403/500/network a blob of an error can be written as if it were the file, with no user feedback.

**Files:**
- Modify: `frontend/src/components/ui/ExportMenu.tsx:31-50`
- Modify: `frontend/src/i18n/fr.json`, `en.json` (`app.exportError` or `export.error`)
- Test: `frontend/src/test/components/ExportMenu.test.tsx` (new or existing)

- [ ] **Step 1: Write the failing test**

Mock `apiClient.get` to reject; assert `download` does not create/click an anchor and surfaces an error message (state or a callback). Assert on success it still downloads.

- [ ] **Step 2: Run to verify it fails**

Run: `npx vitest run src/test/components/ExportMenu.test.tsx` → FAIL.

- [ ] **Step 3: Add try/catch + error state**

```tsx
  const [error, setError] = useState<string | null>(null)

  const download = async (format: string, ext: string, mime: string) => {
    setBusy(format)
    setError(null)
    try {
      const res = await apiClient.get(endpoint, { params: { ...params, format }, responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data], { type: mime }))
      const a = document.createElement('a')
      a.href = url; a.download = `${filename}.${ext}`
      document.body.appendChild(a); a.click(); a.remove()
      window.URL.revokeObjectURL(url)
    } catch {
      setError(t('app.exportError', 'Export failed. Please try again.'))
    } finally {
      setBusy(null); setOpen(false)
    }
  }
```

Render `{error && <p className="...text-red-600">{error}</p>}` under the button. Add `app.exportError` FR/EN.

- [ ] **Step 4: Run to verify it passes + gate**

Run: `npx vitest run src/test/components/ExportMenu.test.tsx` → PASS. `npx tsc --noEmit` → 0.

- [ ] **Step 5: Log and commit**

```bash
git add frontend/src/components/ui/ExportMenu.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json frontend/src/test/components/ExportMenu.test.tsx docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(export): handle download errors in ExportMenu (no silent error-blob download)"
```

---

## Task 10: 🟠 MAJEUR-6 — V43 migration to fix the admin account

**Why:** Seeded `admin` has `mfa_verified=false` (+ drifted `mfa_secret`), so `MfaSetupEnforcementFilter` blocks all admin actions with `mfa_setup_required`. Previously hand-fixed in DB → returns on re-seed. Fix in Flyway.

**Files:**
- Create: `src/main/resources/db/migration/V43__fix_admin_account.sql`
- Reference: `V34__seed_test_users.sql` (copy the exact BCrypt hash of `Test1234!` used there)

- [ ] **Step 1: Extract the canonical BCrypt hash + column names from V34**

Run: `grep -n "admin\|password\|mfa_verified\|bcrypt\|\$2" src/main/resources/db/migration/V34__seed_test_users.sql`
Note the exact `password_hash` column name and the BCrypt string used for the other seeded users.

- [ ] **Step 2: Write the idempotent migration**

```sql
-- V43: realign the seeded `admin` account so MFA setup enforcement doesn't brick it
-- (MAJEUR-6). Idempotent UPDATE — safe to run against any environment.
UPDATE users
SET password_hash = '<exact BCrypt hash of Test1234! from V34>',
    mfa_verified  = true
WHERE username = 'admin';
```

(Use the real column names found in Step 1. If `mfa_secret` must be nulled/reset for a clean re-enrollment, set `mfa_secret = NULL, mfa_enabled = false` too — decide based on V34's admin row shape. Do NOT write a plaintext secret.)

- [ ] **Step 3: Verify it applies cleanly**

Run: `./mvnw test` — the app context boots with Flyway (test profile uses H2 with Flyway on). Expected: `Successfully validated 43 migrations` / no Flyway error, suite green.

- [ ] **Step 4: Log and commit**

`KNOWN_ISSUES_REGISTRY.md`: root cause = admin seed left MFA unverified, hand-fixes don't survive re-seed; fix = V43 idempotent UPDATE; preventive rule = "environment state fixes belong in Flyway, never manual SQL."

```bash
git add src/main/resources/db/migration/V43__fix_admin_account.sql docs/KNOWN_ISSUES_REGISTRY.md docs/TASKS.md
git commit -m "fix(auth): V43 realign admin account (mfa_verified) so MFA enforcement doesn't brick it"
```

---

## Task 11: 🟠 MAJEUR-10 — Whitelist self-requestable roles (exclude ADMIN/DAF)

**Why:** `AccessRequestService.create` only forbids `ROLE_SUPPLIER`. An ASSISTANT_COMPTABLE could request `ROLE_ADMIN`/`ROLE_DAF`; an inadvertent approval = privilege escalation to a financial role.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/access/service/AccessRequestService.java` (~l.38-57)
- Test: `src/test/java/.../AccessRequestServiceTest.java`

- [ ] **Step 1: Write the failing test**

Assert `create(...)` for `requestedRole = "ROLE_ADMIN"` throws `ValidationException` (or the project's equivalent), and same for `ROLE_DAF`. A benign role (e.g. `ROLE_VALIDATEUR_N1_DRH`) still succeeds.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=AccessRequestServiceTest` → FAIL (ADMIN/DAF accepted).

- [ ] **Step 3: Add the deny-list of privileged roles**

In `create`, after the existing SUPPLIER check, reject a small deny-set:

```java
    private static final java.util.Set<String> NON_SELF_REQUESTABLE_ROLES =
            java.util.Set.of("ROLE_SUPPLIER", "ROLE_ADMIN", "ROLE_DAF");
    // ...
    if (NON_SELF_REQUESTABLE_ROLES.contains(request.requestedRole())) {
        throw new ValidationException("error.access_request.role_not_self_requestable");
    }
```

Add the message key FR (Latin-1)/EN.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=AccessRequestServiceTest` → PASS.

- [ ] **Step 5: Log and commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/access/service/AccessRequestService.java src/main/resources/i18n/messages_fr.properties src/main/resources/i18n/messages_en.properties src/test/java/**/AccessRequestServiceTest.java docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(security): forbid self-requesting ADMIN/DAF via access requests (escalation)"
```

---

## Task 12: 🟠 MAJEUR-13 — Encrypt IntegrationConnector.config

**Why:** `IntegrationConnector.java:37-38` `config` (ERP/BANKING/DMS config, possibly credentials/keys) is `@Column(length=4000)` **without** `@Convert(EncryptionAttributeConverter.class)`, unlike `Supplier.bankDetails`. DB read exfiltrates it in clear.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/integration/model/IntegrationConnector.java:37-38` (path per grep)
- Test: `src/test/java/.../IntegrationConnector*Test.java`

- [ ] **Step 1: Confirm the field + existing data**

Run: `grep -rn "class IntegrationConnector\|config" src/main/java/**/IntegrationConnector.java`. Check whether any connector rows exist in the dev DB with plaintext `config` (if the app is seeded with connectors). If existing plaintext rows exist, note it — decide: re-encrypt via a one-off, or accept only-new-rows-encrypted and document it. (For a FYP with mock connectors, likely no sensitive rows.)

- [ ] **Step 2: Write the failing test**

Round-trip: save a connector with `config = "secret-token"`, flush/clear, reload → `config` equals `"secret-token"` (converter transparent), and a raw SQL/native query of the stored column is NOT `"secret-token"` (ciphertext). Mirror the `Supplier.bankDetails` test pattern if one exists.

- [ ] **Step 3: Add the converter**

```java
    @Convert(converter = com.oct.invoicesystem.shared.util.EncryptionAttributeConverter.class)
    @Column(name = "config", length = 4000)
    private String config;
```

(Ciphertext is longer than plaintext — confirm the column length still fits AES-GCM output; widen the column via a migration if 4000 is tight for the max expected config. If a migration is needed it becomes V45.)

- [ ] **Step 4: Run to verify + full suite**

Run: `./mvnw test -Dtest=IntegrationConnector*Test` → PASS. `./mvnw test` → green.

- [ ] **Step 5: Log and commit**

```bash
git add src/main/java/**/IntegrationConnector.java src/test/java/**/IntegrationConnector*Test.java docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(security): encrypt IntegrationConnector.config at rest (AES-GCM, MAJEUR-13)"
```

---

## Task 13: 🟠 MAJEUR-9 — Remove budget from the public DepartmentDTO

**Why:** `DepartmentDTO.java:19` includes `budget`; `GET /departments` + `/{id}` are `isAuthenticated()` (SUPPLIER + ADMIN included). Latent leak of a financial figure the moment a budget is set.

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/.../DepartmentDTO.java` + its mapper + any consumer
- Test: `src/test/java/.../DepartmentControllerTest.java`

- [ ] **Step 1: Find every use of DepartmentDTO.budget**

Run: `grep -rn "budget" src/main/java/**/department* src/main/java/**/Department*` and the frontend, to see who reads it (reports may need it → if so, keep it on a separate internal DTO/endpoint, not the public one).

- [ ] **Step 2: Write the failing test**

`GET /departments` as `ROLE_SUPPLIER` → response JSON contains no `budget` field (even when a department has a non-null budget). Seed/mock a department with a budget for the assertion.

- [ ] **Step 3: Remove `budget` from the public DTO**

Drop the `budget` field from `DepartmentDTO` (record component + mapper). If a report path genuinely needs budget, expose it via a restricted internal DTO/endpoint gated to DAF/AA — do NOT keep it on the public one.

- [ ] **Step 4: Run to verify + fix consumers**

Run: `./mvnw test -Dtest=DepartmentControllerTest` → PASS. `./mvnw test` → green (fix any budget-vs-actual report that read it from this DTO; use the internal path). `npx tsc --noEmit` if a frontend type referenced `budget`.

- [ ] **Step 5: Log and commit**

```bash
git add -A
git commit -m "fix(security): drop budget from public DepartmentDTO (SoD/financial leak, MAJEUR-9)"
```

---

## Task 14: 🟠 B-2 — Sanitize backup filename (path traversal)

**Why:** `BackupController.restoreBackup` concatenates `"backups/" + filename` without sanitization → MinIO path traversal (ADMIN-gated, but still). 

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/compliance/**/Backup*.java` (controller or service `restoreBackup`)
- Test: `src/test/java/.../Backup*Test.java`

- [ ] **Step 1: Write the failing test**

`restoreBackup("../../etc/passwd")` (or via the endpoint) → expect `400`/`ValidationException`, not an attempted restore of a traversed path.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=Backup*Test` → FAIL.

- [ ] **Step 3: Add filename validation**

Reject any `filename` containing `..`, `/`, `\`, or a leading path/drive; allow only a safe pattern (e.g. `^[A-Za-z0-9._-]+$`):

```java
    if (filename == null || !filename.matches("[A-Za-z0-9._-]+")) {
        throw new ValidationException("error.backup.invalid_filename");
    }
```

Add the message key FR (Latin-1)/EN.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=Backup*Test` → PASS.

- [ ] **Step 5: Log and commit**

```bash
git add -A
git commit -m "fix(security): sanitize backup restore filename (path traversal, B-2)"
```

---

## Task 15: 🟡 Minors — currency XAF→XOF + force fr-FR number/date formatting

**Why:** `DashboardPage.tsx:447` shows "XAF"; `PurchaseOrdersPage.tsx` inits `currency:'XAF'` (MAJEUR-F4) while the select offers only XOF/EUR/USD. Dates render M/D/YYYY and amounts use US separators because `toLocaleString`/`Intl` aren't pinned to `fr-FR`.

**Files:**
- Modify: `frontend/src/pages/DashboardPage.tsx:447`, `frontend/src/pages/PurchaseOrdersPage.tsx:30,62,159-161`
- Modify: the shared formatter(s) or each `toLocaleString`/`Intl` call site (prefer a small shared helper `formatCurrency`/`formatDate` in `frontend/src/lib/` if one doesn't exist; check first).

- [ ] **Step 1: Find the offenders**

Run: `grep -rn "XAF\|toLocaleString\|Intl.NumberFormat\|Intl.DateTimeFormat\|toLocaleDateString" frontend/src`.

- [ ] **Step 2: Replace XAF with XOF; pin locale**

Change all `'XAF'` literals/defaults to `'XOF'`. For each number/date format, pass `'fr-FR'` (or use a shared helper). If no shared helper exists, add `frontend/src/lib/format.ts`:

```ts
export const formatAmount = (n: number) => new Intl.NumberFormat('fr-FR').format(n)
export const formatDate = (d: string | Date) => new Date(d).toLocaleDateString('fr-FR')
```

and route the high-traffic call sites (dashboards, invoice/payment lists, invoice detail, approval queue) through it. **Note:** the *typographic* mono/tabular-nums styling is Track B — here only the *formatting/locale*.

- [ ] **Step 3: Run the gate**

Run: `npx tsc --noEmit` → 0. `npx vitest run` → green (update any snapshot/assertion expecting US format or XAF).

- [ ] **Step 4: Log and commit**

```bash
git add frontend/src docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(ui): XAF→XOF and force fr-FR number/date formatting"
```

---

## Task 16: 🟡 Minors — hardcoded FR strings breaking EN mode

**Why:** Several components render frozen FR (or FR-only) text: `RoleGuard.tsx`, `DashboardPage.tsx` (many), `ArchivePage.tsx`, `AdminBackupsPage.tsx` (table headers), `Sidebar.tsx:226` ("Système opérationnel"), `Header.tsx` (whole breadcrumb + `BREADCRUMB_MAP`), `DocumentUploader.tsx` (entire component; `useTranslation` imported, `t` unused). All break EN mode.

**Files:** each component above + `frontend/src/i18n/fr.json`/`en.json`.

- [ ] **Step 1: Inventory the hardcoded strings**

Read each file; list every literal user-facing string. Group into i18n keys (reuse existing namespaces where they fit: `nav.*`, `dashboard.*`, `archive.*`, `admin.backups.*`, `documentUploader.*`).

- [ ] **Step 2: Add keys (FR+EN) and replace literals with `t()`**

For `DocumentUploader.tsx`, actually use the imported `t`. For `Header.tsx`, replace `BREADCRUMB_MAP` values with i18n keys resolved at render. For `Sidebar.tsx:226`, key `sidebar.systemOperational`.

- [ ] **Step 3: Verify no stray literals remain in these files**

Re-grep each file for non-`t()` French strings. Confirm switching `i18n.language` to `en` shows English (spot-check via a test that renders `Header`/`DocumentUploader` with EN and asserts English text).

- [ ] **Step 4: Run the gate + commit**

Run: `npx tsc --noEmit` → 0. `npx vitest run` → green.

```bash
git add frontend/src docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(i18n): route hardcoded FR strings through t() (RoleGuard/Header/Sidebar/DocumentUploader/…)"
```

---

## Task 17: 🟡 Minors — 15 missing backend EN keys + V44 department names + confirmations + import button

**Why:** grouped low-risk closers. (a) 15 MFA/lock keys present in FR but absent from `messages_en.properties`. (b) RT-5 departments seeded in English → migration V44 renames `department.name` to FR. (c) MAJEUR-F2 ~11 destructive actions lack confirmation → shared `ConfirmDialog`. (d) MAJEUR-F3 PO "Import" button does nothing → disable with an honest message.

**Files:**
- Modify: `src/main/resources/i18n/messages_en.properties`
- Create: `src/main/resources/db/migration/V44__rename_departments_fr.sql`
- Create: `frontend/src/components/ui/ConfirmDialog.tsx` + wire into the ~11 sites
- Modify: `frontend/src/pages/PurchaseOrdersPage.tsx:99`

- [ ] **Step 1: Add the 15 EN keys**

Run: `comm -23 <(grep -oE '^[^=]+' src/main/resources/i18n/messages_fr.properties | sort) <(grep -oE '^[^=]+' src/main/resources/i18n/messages_en.properties | sort)` to list FR-only keys. Add each to EN with an English translation.

- [ ] **Step 2: V44 department rename migration (idempotent)**

```sql
-- V44: department names were seeded in English; the app shows department.name verbatim.
UPDATE departments SET name = 'Direction Générale'                 WHERE code = 'DG';
UPDATE departments SET name = 'Direction des Ressources Humaines'  WHERE code = 'DRH';
UPDATE departments SET name = 'Direction des Systèmes d''Information' WHERE code = 'INFO';
UPDATE departments SET name = 'Direction Technique'                WHERE code = 'TECH';
-- …all department codes; use exact codes/labels from V1__create_departments.sql
```

(Confirm codes + intended FR labels against `V1__create_departments.sql`. Non-ASCII in SQL is fine — the file is UTF-8, only `messages_fr.properties` is Latin-1.)

- [ ] **Step 3: Shared ConfirmDialog + wire into destructive actions**

Create a generic `ConfirmDialog` (title, message, confirm/cancel, i18n). Wrap the ~11 destructive actions from audit MAJEUR-F2 (reject access request, delete announcement, delete checklist/calendar, revoke delegation ×2, disable user / reset MFA, delete webhook, revoke session, delete report, delete connector, delete contract). Reuse the pattern already correct in `PaymentAlertRulesPage`/`SuppliersPage`.

- [ ] **Step 4: Disable the PO Import button honestly**

`PurchaseOrdersPage.tsx:99` — replace the fake "in progress" handler with a disabled button + tooltip/message `po.import.unavailable` ("Import ERP indisponible — saisie manuelle", FR/EN), since ERP import is out of scope (Module 12).

- [ ] **Step 5: Run the gate + commit** (split into 2 commits if large: i18n/migration vs ConfirmDialog)

Run: `./mvnw test` → green (V44 applies). `npx tsc --noEmit` → 0. `npx vitest run` → green.

```bash
git add -A
git commit -m "fix(i18n): 15 missing EN keys + V44 FR department names"
# then
git commit -m "feat(ui): shared ConfirmDialog on destructive actions + honest PO import button"
```

---

## Task 18: 🟡 Minors — MAJEUR-F1 session revoke label, dead code, OCR 400

**Why:** final cleanups. (a) MAJEUR-F1 `SecuritySettingsPage.tsx:84` "Revoke" per-session actually revokes ALL sessions — fix the label to match scope (or target one session if the endpoint allows). (b) dead code: `InvoiceCreatePage.tsx:67` throwaway 2nd form; `InvoiceDocumentService.generateDownloadUrl` (confirm unused before removing). (c) `POST /ocr/extract` with no file → 500 instead of 400.

**Files:** `frontend/src/pages/SecuritySettingsPage.tsx:84,275`, `frontend/src/pages/InvoiceCreatePage.tsx:67`, `src/main/java/.../InvoiceDocumentService.java` (dead method), OCR controller.

- [ ] **Step 1: Session revoke — correct the label to its real scope**

Check the endpoint (`DELETE /admin/sessions/user/{userId}` = all sessions). Change the button label/i18n to "Déconnecter toutes les sessions" / "Log out all sessions". (Targeting a single session would need a different endpoint — out of scope unless it exists; if it doesn't, the label fix is the correct minimal action.)

- [ ] **Step 2: Remove confirmed dead code**

Confirm `InvoiceCreatePage.tsx:67`'s 2nd `useForm().watch('supplierId')` is unused (its query is superseded by `supplierPOs`) → remove it. Run `grep -rn "generateDownloadUrl\b" src` to confirm `generateDownloadUrl` (non-`AndLog`) is unreferenced → remove it (keep `generateDownloadUrlAndLog`).

- [ ] **Step 3: OCR missing-file → 400**

In the OCR controller/service, if the multipart file is null/empty, throw a `ValidationException` (→ 400 via GlobalExceptionHandler) instead of letting it NPE into a 500. Add/extend a test asserting 400.

- [ ] **Step 4: Run the gate + commit**

Run: `./mvnw test` → green. `npx tsc --noEmit` → 0. `npx vitest run` → green.

```bash
git add -A
git commit -m "fix(ui/api): session-revoke label scope + remove dead code + OCR missing-file 400"
```

---

## Final: full-gate verification + push decision

- [ ] Run the complete gate one last time: `./mvnw test` (0/0), `cd frontend && npx tsc --noEmit` (0), `npx vitest run` (all green; flaky file green in isolation).
- [ ] Confirm `docs/TASKS.md` and `docs/KNOWN_ISSUES_REGISTRY.md` reflect every fix.
- [ ] Report the unpushed-commit count; propose a push + PR to the user (never push without explicit approval this session).
- [ ] Hand off: the tree is ready for **Track B (Registre redesign)** — write that plan next.

---

## Deferred (NOT in this batch — leave in TASKS.md §A)
- Business-rule questions (§4 audit): VALIDE→REJETE, `ensureWithinApprovalLimit` on BAP, ADMIN PO/GRN reads, checklist B-1 cloisonnement — need a business decision.
- MAJEUR-8 (delegation guard): PLAUSIBLE only. Optional Task — write a test reproducing "delegatee accepted by service but refused by `RoleMatchGuard`"; fix only if the test confirms the bug, else document and close. (Kept out of the numbered flow to avoid speculative changes; add as Task 19 if the user wants it verified now.)
- Low-value minors: `AdminDepartmentsPage.tsx:20-21` conditional hook, i18n pluralization (RT-6), "to" connector (RT-7), backend minors B-3…B-13 (evaluate opportunistically, not committed to).
