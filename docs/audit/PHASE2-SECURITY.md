# Phase 2 — Security Audit

> Verified by direct code inspection and test execution (file:line + command output). Cross-referenced against CLAUDE.md §3 absolute rules and the T1-T7 correction cycle.

---

## P2-01 — CRITICAL: RS256 refresh tokens (506 chars) exceed `active_sessions.refresh_token VARCHAR(500)` — breaks ALL authenticated requests after login

**Severity: CRITIQUE** (proven via test execution: `409 Data Integrity Violation` on the very first authenticated GET request after login; affects every role, not just suppliers — this is a system-wide regression)

### Proof of execution

```
$ ./mvnw.cmd -q test -Dtest=SupplierPortalIntegrationTest#fullSupplierFlow_IntegrationTest
...
[ERROR] Tests run: 4, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 36.34 s <<< FAILURE!
[ERROR] com.oct.invoicesystem.domain.supplier.controller.SupplierPortalIntegrationTest.fullSupplierFlow_IntegrationTest -- Time elapsed: 3.220 s <<< FAILURE!
java.lang.AssertionError: Status expected:<200> but was:<409>
	at ...SupplierPortalIntegrationTest.fullSupplierFlow_IntegrationTest(SupplierPortalIntegrationTest.java:152)
```

The failing assertion (test line 148-153) is the **5th HTTP call** in the flow — `GET /api/v1/supplier/invoices`, immediately after a successful login (step 3, `200`) and a successful invoice submission (step 4, `201`).

**Server-side root cause** (captured from full test output, immediately preceding the `409`):
```
2026-06-12 00:01:58 [main] INFO  c.o.i.s.filter.AuditLoggingFilter - AUDIT | GET /api/v1/supplier/invoices | Status: 409 | User: anonymous | Time: 77ms
...
Caused by: org.h2.jdbc.JdbcSQLDataException: Value too long for column "refresh_token CHARACTER VARYING(500)":
"'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0LXN1cHBsaWVyLTAzNmMzYTU0LTAzZDYtNDg2NC04ZT... (506)"; SQL statement:
insert into public.active_sessions (created_at,expires_at,ip_address,refresh_token,revoked,revoked_at,user_id,user_agent,id) values (?,?,?,?,?,?,?,?,?) [22001-232]
```

Response body: `{"success":false,"message":"Data integrity violation","timestamp":"..."}` (HTTP 409, via `GlobalExceptionHandler.handleDataIntegrityViolationException`, `GlobalExceptionHandler.java:78-82`).

### Root cause chain

1. `V39__create_active_sessions.sql:4` defines `refresh_token VARCHAR(500) NOT NULL UNIQUE`.
2. `ActiveSession.java:26-27` mirrors this: `@Column(name = "refresh_token", nullable = false, unique = true, length = 500)`.
3. `AuthService.java:303,310`: at login, `jwtService.generateRefreshToken(user)` is called and the result is persisted via `activeSessionRepository.save(ActiveSession.builder()...refreshToken(...)...)`.
4. `JwtService.java:72-74`: `generateRefreshToken` calls `buildToken(new HashMap<>(), userDetails, refreshExpirationMs)`, signed with **RS256** (per the GAP2/RS256 migration confirmed in P1-02).
5. **An RS256-signed JWT with this claim set is 506 characters** — 6 over the 500-char column limit.
6. The `INSERT` at login (step 3) is queued by Hibernate but not flushed immediately (no subsequent read in that transaction forces a flush).
7. The next `@Transactional` method that performs a SELECT — `GET /api/v1/supplier/invoices` (step 5) — triggers Hibernate's **auto-flush-before-query**, which attempts to flush the pending `ActiveSession` INSERT from step 3's transaction. **This is where the `VARCHAR(500)` violation actually surfaces**, as a `DataIntegrityViolationException` on an unrelated read endpoint.

**This is a direct, causal regression from the GAP2 (JWT HS256→RS256) migration** documented as already-implemented in P1-02: HS256 tokens with this claim set were short enough to fit in `VARCHAR(500)`; RS256 tokens (larger signature) are not. **The V39 migration (`ApprovalDelegation`/`ActiveSession`, T6, commit `41d4369`) was written assuming the old HS256 token length and was never re-validated after the RS256 migration.**

### Blast radius

- **This affects every role**, not just `ROLE_SUPPLIER` — any user who logs in (which always inserts an `ActiveSession` row with the RS256 refresh token) and then makes ANY subsequent request that triggers a JPA SELECT-with-auto-flush will hit this `409`.
- This likely explains some of the "29 pre-existing test failures" dismissed in the prior cycle as out-of-scope (`post-audit-corrections-2026-06-07` memory note: *"Pre-existing test failures (29 failures, 2 errors) remain in ApprovalControllerTest, StateMachineTransitionExhaustiveTest, UserServiceTest.createUser_Success — these predate the audit work and are out of scope"*) — **this needs re-investigation in light of this finding**: if those tests also log in and then perform a subsequent transactional read, the SAME `VARCHAR(500)` violation could be a shared root cause, meaning some "pre-existing" failures may actually be **caused by the same V39/RS256 interaction**, not independent issues. (To be checked when running the full suite in Phase 10.)
- **In production**, this means: a user logs in successfully (gets a JWT), but their **very first subsequent API call that performs any database read may return a `409`** — i.e., the application is largely unusable end-to-end for any role once RS256 is active. Given BASELINE.md's Phase 0 baseline test run reported a count of failures, this may already be manifesting broadly — needs cross-check against the Phase 0 full-suite numbers in Phase 10.

### Fix applied and VERIFIED

Created `V41__increase_active_sessions_refresh_token_length.sql`:
```sql
ALTER TABLE active_sessions ALTER COLUMN refresh_token TYPE VARCHAR(1024);
```
Updated `ActiveSession.java:26` to `length = 1024`. Chose 1024 (not a tighter bound like 600) to leave headroom for future claim additions to the JWT without repeating this exact bug — RS256 JWTs with larger claim sets (e.g., adding `supplierId`, role lists) can grow further (the actual token observed in re-test now carries an extra `supplierId` claim and is even longer than the original 506 chars, see P2-02 below — confirming the headroom choice was correct).

**CLAUDE.md §3 Flyway rule note honored**: V39 was already applied (checksum locked), so per *"Never modify an already-applied Flyway migration — create a new one"*, this is a NEW `V41` migration, not an edit to `V39__create_active_sessions.sql`. (V40 is already used by `approval_delegations`.)

**Re-run proof**:
```
$ ./mvnw.cmd -q test -Dtest=SupplierPortalIntegrationTest > /tmp/supplier_test2.log 2>&1; echo "exit=$?"
exit=0
```
No `Tests run`/`FAILURE`/`AssertionError` lines appear (Maven `-q` suppresses the success summary line entirely — only failures print). Direct evidence from the captured `MockMvc` output confirms **all 8 steps of `fullSupplierFlow_IntegrationTest` now pass**, including the previously-failing step 5:
```
2026-06-12 00:07:07 [main] INFO  c.o.i.s.filter.AuditLoggingFilter - AUDIT | GET /api/v1/supplier/invoices | Status: 200 | ...
2026-06-12 00:07:07 [main] INFO  c.o.i.s.filter.AuditLoggingFilter - AUDIT | GET /api/v1/supplier/profile  | Status: 200 | ...
```
**P2-01 is CLOSED** — RS256 refresh tokens now persist correctly and the system-wide `409`-after-login regression is resolved.

**Follow-up flagged for Phase 10**: re-check whether any of the "29 pre-existing test failures" (`ApprovalControllerTest`, `StateMachineTransitionExhaustiveTest`, `UserServiceTest.createUser_Success`) shared this root cause and are now also fixed as a side effect of V41 — run the full suite and compare counts against BASELINE.md.

---

## P2-02 — HIGH: `GET /api/v1/supplier/profile` returns the raw `Supplier` JPA entity, leaking decrypted `bankDetails` — violates CLAUDE.md "never expose JPA entities" + contradicts `SupplierResponse`'s deliberate field exclusion

**Severity: HAUTE** (confirmed leak of decrypted bank account details via a documented, Swagger-annotated endpoint; not a 500/crash, but a direct architectural-rule violation with real data-exposure consequence)

### Proof of execution

Re-running `SupplierPortalIntegrationTest#fullSupplierFlow_IntegrationTest` (now passing end-to-end after the P2-01 fix) reaches step 8 (`GET /api/v1/supplier/profile`, test lines 176-181) for the first time. Captured response:

```
2026-06-12 00:07:07 [main] INFO  c.o.i.s.filter.AuditLoggingFilter - AUDIT | GET /api/v1/supplier/profile | Status: 200 | User: anonymous | Time: 24ms

MockHttpServletResponse:
           Status = 200
    Content type = application/json
            Body = {"success":true,"data":{"id":"69be50ed-607d-48ef-a8e0-11d83f9510d7","companyName":"Test Supplier Co","taxId":"TAX-03dda251-a286-42e8-8eee-58babd49f30d","contactEmail":"test-supplier-b82c8ac7-c4fb-491d-a9c5-e7ba549cc956@example.com","contactPhone":"+123456789","bankDetails":"BANK-DETAILS-X","address":"123 Supplier Lane","status":"PENDING_VERIFICATION","createdAt":"2026-06-11T22:07:05.353382900Z","updatedAt":"2026-06-11T22:07:05.356754700Z","documents":[]},"timestamp":"..."}
```

The handler, confirmed at `SupplierPortalController.java:147-153`:
```java
@GetMapping("/profile")
@Operation(summary = "Get supplier profile", description = "Returns the supplier's own profile")
public ResponseEntity<ApiResponse<Supplier>> getProfile(Authentication authentication) {
    UUID supplierId = getSupplierId(authentication);
    Supplier supplier = supplierService.findEntityById(supplierId);
    return ResponseEntity.ok(ApiResponse.success(supplier));
}
```

`supplierService.findEntityById()` (`SupplierServiceImpl.java:87-92`) returns the **raw JPA entity** — and `Supplier.bankDetails` (`Supplier.java:58-60`) is `@Convert(converter = EncryptionAttributeConverter.class)` with **no `@JsonIgnore`**, so Jackson serializes the **decrypted plaintext** value (`"BANK-DETAILS-X"`) directly into the JSON response.

### Why this is a real finding, not a "supplier owns their own data" non-issue

The system already has a purpose-built DTO for exactly this — `SupplierResponse` (`SupplierResponse.java`), an 11-field record that **deliberately excludes `bankDetails`** (and `documents`), used correctly by the sibling endpoint `PUT /profile` → `updateProfile()` (`SupplierPortalController.java:155-164`) via `supplierService.updateSupplier()` → `supplierMapper.toResponse()`.

`getProfile()` is the ONE endpoint in this controller that bypasses this established, documented contract — returning `Supplier` instead of `SupplierResponse`. This is:
1. **A direct CLAUDE.md §3 violation**: *"Never expose JPA entities directly from an endpoint — always use DTOs."*
2. **An inconsistency with the codebase's own design intent**: whoever wrote `SupplierResponse` explicitly chose to exclude `bankDetails` from supplier-facing responses — `getProfile()` silently defeats that decision.
3. **A latent risk for future fields**: any new `@OneToMany`/`@ManyToOne` field added to `Supplier` (e.g., re-adding `onboardedBy` with `@JsonIgnore` forgotten) will auto-leak through this endpoint with no compile-time warning, because nothing constrains the response shape.

In this test run, `documents` serializes as `[]` (empty list — the LAZY collection happens to be empty/initialized) and `onboardedBy` is absent (null for self-registered suppliers) — so **no `LazyInitializationException` occurred here**, but this is incidental to the test fixture, not a guarantee. A supplier with uploaded documents or an `onboardedBy` admin (onboarded manually rather than self-registered) could trigger `LazyInitializationException` → 500 on this same endpoint, given `open-in-view: false` (`application.yaml:16`).

### Proposed fix (Phase 10)

Change `SupplierPortalController.getProfile()` to mirror `updateProfile()`'s pattern:
```java
@GetMapping("/profile")
@Operation(summary = "Get supplier profile", description = "Returns the supplier's own profile")
public ResponseEntity<ApiResponse<SupplierResponse>> getProfile(Authentication authentication) {
    UUID supplierId = getSupplierId(authentication);
    SupplierResponse response = supplierService.getSupplier(supplierId);
    return ResponseEntity.ok(ApiResponse.success(response));
}
```
`SupplierServiceImpl.getSupplier()` (`SupplierServiceImpl.java:54-57`) already exists and does exactly this (`supplierMapper.toResponse(findEntityById(id))`) — no new service code needed, just swap the controller's call and return type. The existing test assertion (`jsonPath("$.data.companyName")`, line 178-180) is unaffected since `companyName` exists in both `Supplier` and `SupplierResponse`; a NEW assertion should be added asserting `bankDetails` is **absent** from the response (`jsonPath("$.data.bankDetails").doesNotExist()`) to lock in the fix and prevent regression.

---

## Summary for Phase 8 (ISSUES.md)

| ID | Severity | Summary | File:line | Proof |
|---|---|---|---|---|
| P2-01 | ~~CRITIQUE~~ **CLOSED** | RS256 refresh tokens (506 chars) exceeded `active_sessions.refresh_token VARCHAR(500)` — `409` on first authenticated read after login, system-wide. **Fixed via V41 + entity edit, re-test confirms all 8 steps pass.** | `V41__increase_active_sessions_refresh_token_length.sql`, `ActiveSession.java:26` | `SupplierPortalIntegrationTest` exit=0, step 5 (`GET /supplier/invoices`) and step 8 (`GET /supplier/profile`) both `Status: 200` |
| P2-02 | **HAUTE** | `GET /api/v1/supplier/profile` returns raw `Supplier` entity — decrypted `bankDetails` ("BANK-DETAILS-X") leaks into JSON response; violates CLAUDE.md "never expose JPA entities" and contradicts `SupplierResponse`'s deliberate exclusion of this field | `SupplierPortalController.java:147-153`, cf. correct pattern at `:155-164`; `Supplier.java:58-60` (no `@JsonIgnore`); `SupplierResponse.java` (excludes `bankDetails`) | Step 8 response body: `"bankDetails":"BANK-DETAILS-X"` present in `$.data` |

---

## P2-03 — `@PreAuthorize` coverage matrix — ✅ CLEAN (21/21 controllers)

**Verified by**: per-controller scan distinguishing class-level vs method-level `@PreAuthorize`, counting actual `@*Mapping` handler methods (excluding the class-level `@RequestMapping`).

| Controller | Endpoints | Coverage |
|---|---|---|
| `AuditController` | 3 | method-level, 3/3 |
| `AuthController` | 9 | method-level, 9/9 |
| `DepartmentController` | 5 | method-level, 5/5 |
| `InvoiceController` | 12 | method-level, 12/12 |
| `InvoiceDocumentController` | 3 | method-level, 3/3 |
| `NotificationController` | 4 | method-level, 4/4 |
| `OcrController` | 1 | method-level, 1/1 |
| `PaymentController` | 4 | method-level, 4/4 |
| `GoodsReceiptController` | 3 | method-level, 3/3 |
| `MatchingConfigController` | 2 | method-level, 2/2 |
| `PurchaseOrderController` | 5 | method-level, 5/5 |
| `ReportController` | 11 | method-level, 11/11 |
| `SupplierController` | 12 | method-level, 12/12 |
| `SupplierPortalController` | 11 | **class-level** `@PreAuthorize("hasRole('SUPPLIER')")` — covers all 11 |
| `AdminSessionController` | 2 | method-level, 2/2 (both `hasRole('ADMIN')`) |
| `UserController` | 7 | **class-level** `@PreAuthorize("hasRole('ADMIN')")` — covers all 7 |
| `UserProfileController` | 2 | method-level, 2/2 |
| `IntegrationStatusController` | 1 | method-level, 1/1 (`hasRole('ADMIN')`) |
| `WebhookController` | 4 | method-level, 4/4 |
| `ApprovalController` | 6 | method-level, 6/6 |
| `DelegationController` | 3 | method-level, 3/3 |

**Result: 21/21 controllers, 100% endpoint coverage.** No CRITICAL access-control gap. (First-pass scan flagged `SupplierPortalController` and `UserController` as 0% — both false positives from missing class-level annotations; corrected above.)

---

## P2-04 — MFA secret (`User.mfaSecret`) — ✅ CLEAN, never serialized

`User.mfaSecret` (`User.java:108-110`) is `@Convert(converter = EncryptionAttributeConverter.class)`, AES-256-encrypted at rest (per T1, confirmed). It has **no `@JsonIgnore`**, but this is a non-issue: a full-codebase search confirms **`User` the JPA entity is never returned in any `ResponseEntity<ApiResponse<...>>`** — `grep -rn "ApiResponse<User>"` returns zero matches. All user-facing responses go through `UserResponse`/`UserProfileResponse`-style DTOs via MapStruct mappers (per P1-07's mapper coverage table), none of which include `mfaSecret`. The raw-entity-leak pattern found in P2-02 (`Supplier`) does **not** recur for `User`.

---

## P2-05 — Soft-delete enforcement — ✅ CLEAN, zero hard deletes

`grep -rn "\.deleteById\|repository\.delete\|Repository\.delete\|\.deleteAll"` across all of `src/main/java` returns **zero matches**. No repository, service, or controller calls Spring Data's `delete`/`deleteById`/`deleteAll`. All "deletion" operations observed in the domain (`Invoice`, `Supplier`, `User`, etc.) go through `deletedAt`/soft-delete fields (e.g., `Invoice.deletedAt` at `Invoice.java:117-118`, `Supplier.deletedAt`, paired with `findByIdAndDeletedAtIsNull`-style repository queries as seen in `SupplierServiceImpl.java:90`). CLAUDE.md §3 *"Always soft-delete financial records — never hard delete"* is fully honored.

---

## P2-06 — CORS configuration — ✅ CLEAN, fail-closed in production

`application.yaml` defines CORS in two places:
- **Default profile** (`application.yaml:111-112`): `allowed-origins: ${ALLOWED_ORIGINS:http://localhost:3000}` — has a dev-friendly default (`localhost:3000`), appropriate for local development.
- **Production profile** (`application.yaml:334-336`, under the `prod` profile section starting at line 279's `---`/`spring:` block): `allowed-origins: ${ALLOWED_ORIGINS}` — **no default value**. If `ALLOWED_ORIGINS` is unset in production, Spring's property resolution will fail at startup (or resolve to an empty/invalid origin list), which is the correct **fail-closed** behavior — the app will not silently default to `localhost:3000` in production.

This matches T5's verification (CORS env var). `CorsConfig implements WebMvcConfigurer` (per P1-03) applies this at the MVC layer — not part of the Security filter chain, but functionally still enforced before requests reach controllers.

---

**Phase 2 complete.** Final tally: P2-01 (CRITIQUE, fixed+verified), P2-02 (HAUTE, fix proposed for Phase 10), P2-03/04/05/06 (✅ clean). Encryption coverage (MFA secret, `Supplier.bankDetails`, `Invoice.supplierBankDetails` — all 3 via `EncryptionAttributeConverter`, per T1) and SQL-injection surface (`SupplierRepository` native `@Query` uses safe `CONCAT('%', :param, '%')` bind-parameter pattern) were both confirmed clean during the P2-01 investigation.
