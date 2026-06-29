# Phase 1 — Architecture Audit

> Verified by direct code inspection (file:line). Cross-referenced against `docs/ARCHITECTURE.md` (423 lines, read in full).

---

## P1-01 — Documented package names do not match actual packages (Phase 9 modules)

**Severity: MOYENNE** (documentation drift, no functional impact, but misleads new contributors and contradicts CLAUDE.md §12 Living Documentation Rule)

`docs/ARCHITECTURE.md` §2 (main package tree) and §10 ("New Domain Modules — Phase 9") describe packages that do not exist under those names.

**Proof — actual top-level domain packages** (`find src/main/java/com/oct/invoicesystem/domain -maxdepth 1 -type d`):
```
domain/audit
domain/auth
domain/department
domain/invoice
domain/mfa
domain/notification
domain/ocr
domain/payment
domain/purchasing
domain/report
domain/storage
domain/supplier
domain/user
domain/webhook
domain/workflow
```

| Documented (ARCHITECTURE.md) | Actual on disk | Status |
|---|---|---|
| §2: `domain/reporting/` | `domain/report/` | ❌ wrong name |
| §10: `domain/matching/` (PurchaseOrder, GoodsReceiptNote, ThreeWayMatchingResult, MatchingConfig, ThreeWayMatchingService, PurchaseOrderController, MatchingConfigController) | `domain/purchasing/` | ❌ wrong name — confirmed via imports in `InvoiceStateMachineServiceImpl.java:12-17` (`com.oct.invoicesystem.domain.purchasing.model.MatchingStatus`, `...purchasing.service.ThreeWayMatchingService`, etc.) |
| §10: `domain/integration/` (Webhook, WebhookDelivery, WebhookService, WebhookEventPublisher, WebhookController) | `domain/webhook/` | ❌ wrong name — `domain/integration/` does not exist at all; confirmed via `find src/main/java -path "*Webhook*"` → all 12 files live under `domain/webhook/{controller,dto,event/listener,mapper,model,repository,service}` |
| §2 main tree: omits entirely | `domain/mfa/`, `domain/ocr/`, `domain/supplier/`, `domain/webhook/`, `domain/purchasing/`, `domain/department/`, `config/security/` | ❌ undocumented in §2 (some appear only in §10's "Phase 9" addendum, 2 of those under the wrong name per above) |

**Root cause**: §10 was written as a "Phase 9 addendum" without renaming to match the names actually chosen during implementation (`matching` → `purchasing`, `integration` → `webhook`), and §2's main tree was never updated to fold in the Phase 9 modules at all.

**Proposed fix (Phase 11)**: Rewrite ARCHITECTURE.md §2 to show the complete, current package tree (all 15 domain packages + `config/security`) as a single source of truth, and remove/merge §10 into §2 rather than keeping it as a separate "addendum" with stale names.

---

## P1-02 — ARCHITECTURE.md §4.1 "Known Implementation Gaps" table is almost entirely stale

**Severity: HAUTE** (actively misleading — claims features are unimplemented when they exist, tested, and are in production code)

§4.1 (last updated "2026-06-06") lists 8 gaps. Cross-referencing against this audit's Phase 0 baseline and T1-T7 verification (BASELINE.md §1-14):

| GAP | Documented status | Actual status (this audit) | Verdict |
|---|---|---|---|
| GAP 1 — OCR | ❌ Not implemented | Tess4J 5.11.0 + PDFBox 3.0.3 wired in `pom.xml`; `OcrServiceTest` 9/9 passed in Phase 0 baseline (BASELINE.md §3) | ❌ STALE — implemented |
| GAP 2 — JWT HS256 | "must migrate to RS256" | `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` env vars present in docker-compose (RS256 already in place per T-cycle) | ❌ STALE — implemented |
| GAP 3 — CI missing | ❌ Absent | `.github/workflows/ci.yml` (backend/frontend/docker jobs) exists and is structurally valid (BASELINE.md §10, T4 ✅) | ❌ STALE — implemented |
| GAP 4 — TLS 1.3 | ❌ Not configured | `application.yaml:326-332` configures TLS — **not yet independently re-verified this session**, carried from earlier note | ⚠️ UNVERIFIED (likely stale, needs 1-line grep confirmation) |
| GAP 5 — OWASP ZAP | ❌ Absent | `.github/workflows/security-scan.yml` + `zap-rules.tsv` exist (BASELINE.md §10, T4 ✅) | ❌ STALE — implemented |
| GAP 6 — Approval Delegation | "no entity/service/endpoint/UI" | V40 migration + `ApprovalDelegation` entity + `ApprovalDelegationRepository` + `DelegationService`/`DelegationController` all exist and tested (BASELINE.md §12, T6); **frontend UI is genuinely still missing** (REGRESSION-T6-02) | ⚠️ PARTIALLY STALE — backend done, UI gap is real |
| GAP 7 — Audit sub-typing | ❌ Absent | Implemented per m-08 (BASELINE.md §13, T7 ✅) | ❌ STALE — implemented |
| GAP 8 — Archive search | ❌ Absent | Implemented per m-04 (BASELINE.md §13, T7 ✅) | ❌ STALE — implemented |

**Root cause**: The T1-T7 correction cycle (2026-06-07, see memory `post-audit-corrections-2026-06-07`) implemented fixes for GAP1/2/3/5/6(partial)/7/8 but never updated §4.1 to reflect the new state — a direct violation of CLAUDE.md §12 Living Documentation Rule ("If the problem was caused by an INCORRECT rule in these docs, CORRECT the rule").

**Proposed fix (Phase 11)**: Rewrite §4.1 entirely. Only GAP 6 (Approval Delegation frontend) survives as a real gap — re-scope it to "frontend UI only, backend complete" and link to REGRESSION-T6-02. GAP 4 (TLS) needs a 1-line verification (`grep -n "ssl\|tls" application.yaml`) before being closed or kept.

---

## P1-03 — Security filter chain: ARCHITECTURE.md §5 diagram does not match `SecurityConfig.java`

**Severity: HAUTE** (security documentation must accurately reflect the actual chain — an incorrect diagram could lead a future developer to add a new filter in the wrong relative position, e.g. before rate limiting or after audit logging)

**Documented (§5)**:
```
CorsFilter → JwtAuthenticationFilter → AuditLoggingFilter → SecurityFilterChain
  → @PreAuthorize → service-layer ownership check → Business logic
```

**Actual** (`SecurityConfig.java:71-75`):
```java
.addFilterBefore(httpSecurityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(rateLimitingFilter,        UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter,             UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(mfaSetupEnforcementFilter,  UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(auditLoggingFilter,         UsernamePasswordAuthenticationFilter.class)
```

Effective order relative to `UsernamePasswordAuthenticationFilter` (UPAF):
```
HttpSecurityHeadersFilter → RateLimitingFilter → JwtAuthenticationFilter
  → [UsernamePasswordAuthenticationFilter] → MfaSetupEnforcementFilter → AuditLoggingFilter
  → SecurityFilterChain (@PreAuthorize) → service-layer ownership check → Business logic
```

(Note: when multiple filters are registered with `addFilterBefore`/`addFilterAfter` against the *same* anchor class, Spring Security preserves the **registration order** among them as a stable tie-break, so the three `addFilterBefore` calls above run in the listed order, and the two `addFilterAfter` calls likewise.)

**Discrepancies**:
1. **3 filters entirely undocumented**: `HttpSecurityHeadersFilter`, `RateLimitingFilter`, `MfaSetupEnforcementFilter` do not appear in §5's diagram at all.
2. **CORS is not a security filter chain entry at all.** `CorsConfig.java:9` is `class CorsConfig implements WebMvcConfigurer` (confirmed via grep — `allowedOrigins`/`.allowedOrigins(origins)` inside an `addCorsMappings`-style method), registering CORS via Spring MVC's `WebMvcConfigurer`, separate from the `SecurityFilterChain` filter list entirely. §5 places `CorsFilter` as the *first* element of the security chain — this is architecturally incorrect, not just mis-ordered.
3. **AuditLoggingFilter position**: documented as running *before* `SecurityFilterChain`/`@PreAuthorize`; actual position is `addFilterAfter(..., UsernamePasswordAuthenticationFilter.class)`, i.e., after authentication but still as part of the filter chain that runs before the dispatcher reaches `@PreAuthorize`-guarded controller methods. The relative claim ("before SecurityFilterChain") is misleading because the filter chain *is* part of what §5 calls "SecurityFilterChain" — the diagram conflates "the filter chain" with "the authorization decision point."
4. **JwtAuthenticationFilter relative position**: documented as first (after CORS); actually third among the `addFilterBefore` group (after `HttpSecurityHeadersFilter` and `RateLimitingFilter`).

**Root cause**: §5's diagram was written when only `JwtAuthenticationFilter` existed; later additions (`HttpSecurityHeadersFilter`, `RateLimitingFilter`, `MfaSetupEnforcementFilter` — the latter added during the T-cycle for MFA enforcement) were wired into `SecurityConfig.java` without updating §5.

**Proposed fix (Phase 11)**: Replace §5's diagram with the verified chain above, and explicitly note that CORS is configured via `WebMvcConfigurer` (MVC layer), separate from the Spring Security filter chain.

---

## P1-04 — Three-Way Matching integration point: implementation matches (and exceeds) the documented design

**Severity: N/A — POSITIVE FINDING** (no issue; documenting for completeness per the audit's "verify, don't assume" mandate)

ARCHITECTURE.md (§"Three-Way Matching Integration Point") documents:
> `InvoiceStateMachineServiceImpl.sendEvent(SUBMIT)` → if `invoice.purchaseOrderId` present → `ThreeWayMatchingService.match(invoice, po, grn)` → MATCHED→proceed to SOUMIS; PARTIAL→proceed with `matching_status=PARTIAL`; MISMATCH→throw `WorkflowException`, invoice stays BROUILLON, until DAF/ADMIN override→proceed with `matching_status=OVERRIDDEN`.

**Verified** (`InvoiceStateMachineServiceImpl.java:84-93, 166-205`):
- `sendEvent()` checks `event.equals(InvoiceEvent.SUBMIT)` (line 84) ✅
- Gated on `invoice.getPurchaseOrderId() != null` (line 90) before calling `performMatchingCheck()` ✅
- `performMatchingCheck()` (lines 166-205):
  - Checks for an existing `OVERRIDDEN` result first and short-circuits (lines 169-173) ✅ matches documented override flow
  - Fetches PO via `purchaseOrderRepository.findByIdActive(...)`, throws `WorkflowException` if not found (lines 176-177)
  - Fetches GRN (optional, `.orElse(null)`) (line 180)
  - Calls `threeWayMatchingService.match(invoice, po, grn)` (line 183) ✅ exact signature match
  - Persists `invoice.setMatchingStatus(result.getStatus().name())` (lines 186-187) — covers MATCHED/PARTIAL/OVERRIDDEN status persistence as documented
  - On `MatchingStatus.MISMATCH`, throws `WorkflowException` with discrepancy notes (lines 190-194) — since this throw happens **before** `sm.sendEvent(message)` (line 114, which is reached only after line 93), the invoice never transitions and **stays at BROUILLON** ✅ exactly as documented

**Bonus — undocumented but correct addition**: `performDuplicateCheck()` (lines 85-87, 137-156) also runs on every `SUBMIT`, independent of `purchaseOrderId`. This implements part of Module 3's "Détection de doublons (alerte)" requirement (REQUIREMENTS-MATRIX.md Module 3) — **not mentioned anywhere in ARCHITECTURE.md**, but is a real, tested feature (`countDuplicatesBySupplierAndDescription`, 365-day window, excludes rejected/archived). This should be added to ARCHITECTURE.md's Three-Way Matching Integration Point section as a sibling pre-submit check, and credited toward Module 3 coverage in Phase 6.

**No fix required** for the matching logic itself — only a documentation addition (Phase 11) to mention the duplicate-check sibling behavior.

---

## P1-02b — GAP 4 (TLS 1.3) verified resolved — confirms ALL 8 entries in §4.1 are stale

**Severity**: folded into P1-02 (no separate issue)

`application.yaml:320-331`:
```yaml
  # TLS 1.3 — enforced at the application layer (not only at infrastructure/reverse-proxy level)
  ssl:
    ...
    protocol: TLSv1.3
    enabled-protocols: TLSv1.3
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
```

GAP 4 is implemented (TLSv1.3 enforced via `application.yaml` ssl config). Combined with P1-02's table, **all 8 rows of §4.1 are stale**: 7 fully resolved (GAP 1,2,3,4,5,7,8), 1 partially resolved (GAP 6 — backend done, frontend UI still missing per REGRESSION-T6-02).

---

## P1-05 — 5 controllers inject repositories directly, bypassing the service layer (CLAUDE.md §3 violation)

**Severity: HAUTE** (direct violation of an "absolute rule"; 2 of these are full business-logic-in-controller cases with zero service layer; this is a **regression** within domains T3/T6 already marked "fixed"/"verified")

CLAUDE.md §3 states unambiguously: *"**Never** bypass the service layer from a controller"* and *"**Never** put business logic in a controller or repository"*. The T3 correction (commit `0cde8a3`, "layer rules: repositories out of controllers, SecurityHelper created") addressed 5 controllers at the time — but a scan of **all 21 controllers** for `import ...repository...` / `private final ...Repository` fields finds **5 controllers still doing this today**:

| Controller | Repository injected | Usage | Severity |
|---|---|---|---|
| `domain/invoice/controller/InvoiceDocumentController.java:7,38,76` | `UserRepository` | `getActorId()` — single `findByUsername(username).map(User::getId)` for current-user resolution, read-only | LOW — same pattern `SecurityHelper` was built to replace, but functionally harmless (1 line, no business logic) |
| `domain/workflow/controller/DelegationController.java:4,32,41,43` | `UserRepository` | `findById()` x2 to resolve `delegator`/`delegatee` from raw `Map<String,Object>` request body, then passes `User` entities into `delegationService.createDelegation(...)`. Also returns raw `Map<String,Object>` (not a DTO) for both `createDelegation` and `listDelegations`, with field-by-field entity access (`d.getDelegator().getUsername()`, etc.) | **MEDIUM** — business logic (lookup + entity assembly) in controller; no DTO/mapper used at all |
| `domain/user/controller/AdminSessionController.java:4,25,31,51` | `ActiveSessionRepository` | **No service class exists for this controller at all.** `listActiveSessions()` calls `sessionRepository.findAllActive(...)` directly, then manually builds `List<Map<String,Object>>` via `LinkedHashMap` with raw entity field access (`s.getUser().getId()`, `s.getUser().getUsername()`, `s.getIpAddress()`, etc., lines 35-40). `revokeUserSessions()` calls `sessionRepository.revokeAllForUser(...)` directly. | **HIGH** — 100% controller-as-service; was inside T6's "ActiveSession ✅ end-to-end" verdict (BASELINE.md §12), which checked functional completeness but not layering |
| `domain/webhook/controller/WebhookController.java:10-11,40-41,70,86,104,107` | `WebhookRepository`, `WebhookDeliveryRepository` | `listWebhooks()` calls `webhookRepository.findByIsActiveTrue()` directly (bypassing `webhookService` entirely for this endpoint, even though the same controller correctly uses `webhookService.registerWebhook(...)` and `webhookService.deactivateWebhook(...)` elsewhere — **inconsistent layering within the same file**). `getDeliveryLog()` fetches `Webhook` via `webhookRepository.findById(...)`, then `deliveryRepository.findByWebhookOrderByCreatedAtDesc(webhook, pageable)`, then manually builds `WebhookDeliveryResponse` via `.builder()` with raw entity field access — this DTO assembly belongs in `WebhookMapper` (which already exists and is used for `toResponseWithoutSecret` in the same file) | **HIGH** — inconsistent layering, partial mapper bypass |
| `domain/webhook/controller/IntegrationStatusController.java:6-7,27-28,38,57` | `WebhookRepository`, `WebhookDeliveryRepository` | **No service field, no service class used at all.** `getIntegrationStatus()` calls `webhookRepository.findByIsActiveTrue()` directly, then `.map(this::mapWebhookToStatus)` — a **private controller method** (lines 47-65) that manually builds `WebhookStatusResponse` via `.builder()` with raw entity access (`webhook.getId()`, `webhook.getName()`, `webhook.getEvents().split(",")`, etc.) AND performs a second repository call per webhook (`deliveryRepository.findLatestDeliveryByWebhook(webhook)`) inside the mapping function — a classic N+1 pattern (flagged again in Phase 3) | **HIGH** — 100% controller-as-service, business logic + N+1 query in a private controller method |

**Root cause**: T3 (2026-06-07) fixed the controllers it identified as violations at the time, but `AdminSessionController` and `IntegrationStatusController` were created/finalized as part of T6/T7 (same session, later commits `41d4369`/`1995fb6`) — i.e., **new violations were introduced in the same correction cycle that fixed the old ones**, and were never caught because T6/T7's verification criteria checked functional completeness ("does ActiveSession work end-to-end?") rather than re-running the T3 layering check against new code.

**Proposed fix (Phase 10)**:
1. `AdminSessionController` → extract `AdminSessionService` (or add methods to existing `AuthService`/session-related service) with a proper `ActiveSessionResponse` DTO + `ActiveSessionMapper`.
2. `IntegrationStatusController` → extract logic into `WebhookService` (add `getIntegrationStatus()` method using a single query with a join/fetch to avoid N+1, returning `List<WebhookStatusResponse>` directly).
3. `WebhookController.listWebhooks()` → route through `webhookService` (add `listActiveWebhooks()` if missing). `getDeliveryLog()` → move `WebhookDeliveryResponse` assembly into `WebhookMapper` as `toDeliveryResponse(WebhookDelivery)`.
4. `DelegationController` → move `UserRepository` lookups into `DelegationService.createDelegation(UUID delegatorId, UUID delegateeId, ...)` (resolve entities inside the service); replace raw `Map<String,Object>` returns with proper `DelegationResponse`/`DelegationCreateRequest` DTOs + `DelegationMapper`.
5. `InvoiceDocumentController.getActorId()` → replace with `securityHelper.currentUser(authentication).getId()` (², `SecurityHelper` already exists and is used elsewhere — this is a 1-line fix).

This finding should be cross-referenced as a **regression** in Phase 7/8 even though it falls outside the original T1-T7 m-01..m-11 checklist, because it directly contradicts the stated outcome of commit `0cde8a3` (T3).

---

## P1-06 — Domain coupling graph

**Severity: BASSE** (informational — no genuine circular Spring-bean dependency found, but the bidirectional `invoice`↔`purchasing` package relationship is worth documenting since ARCHITECTURE.md doesn't describe inter-domain dependencies at all)

**Method**: `grep -rhoE "^import com\.oct\.invoicesystem\.domain\.[a-z]+"` per domain, deduplicated, self-references removed.

```
audit      -> user
auth       -> mfa, notification, supplier, user
department -> (none)
invoice    -> department, notification, purchasing, storage, supplier, user, workflow
mfa        -> (none)
notification -> department, invoice, supplier, user, workflow
ocr        -> (none)
payment    -> invoice, notification, storage, user
purchasing -> invoice, supplier, user
report     -> invoice, payment, webhook, workflow
storage    -> (none)
supplier   -> department, invoice, report, storage, user
user       -> audit, auth, supplier
webhook    -> invoice, notification, user
workflow   -> department, invoice, user
```

**Notable findings**:

1. **`invoice` ↔ `purchasing` bidirectional reference** (verified, not a false positive):
   - `purchasing` imports `invoice.model.Invoice` and `invoice.model.InvoiceItem` (3 import sites) — used by `ThreeWayMatchingService` to compare PO/GRN line items against the submitted `Invoice`'s items.
   - `invoice` imports `purchasing.service.ThreeWayMatchingService`, `purchasing.repository.{PurchaseOrderRepository,GoodsReceiptNoteRepository,ThreeWayMatchingResultRepository}`, `purchasing.model.{MatchingStatus,ThreeWayMatchingResult}` (used by `InvoiceStateMachineServiceImpl`, see P1-04).

   This is **not a circular Spring bean dependency** (no constructor cycle: `ThreeWayMatchingService` does not depend on `InvoiceStateMachineServiceImpl` or any invoice-domain bean) — it is a **package-level bidirectional reference**, which is common and acceptable for a "reconciliation" domain that both consumes and is consumed by the domain it reconciles. However, ARCHITECTURE.md presents `domain/*` as if each package were a clean one-directional layer (§1's diagram shows "Domain Modules" as a single undifferentiated box with no inter-module arrows) — this should be documented explicitly as an intentional exception, e.g., in §2 add a short note: *"`purchasing` and `invoice` have a bidirectional package dependency by design: `purchasing` reads `Invoice`/`InvoiceItem` for matching; `invoice`'s state machine calls `purchasing`'s `ThreeWayMatchingService` on submission."*

2. **`user` → `audit`, `user` → `auth`**: `user` domain importing from `audit` and `auth` is slightly unusual naming-wise (one might expect `auth`/`audit` to depend on `user`, not vice versa) — likely `UserService` triggers audit-log writes (`AuditLogService`) and reads `ActiveSession`/account-lockout state (`auth` domain, per T1's `isAccountNonLocked`/`failed_login_attempts` work). Not a violation, just worth noting the directionality is "supporting domains depend upward into `user`" in some cases and "`user` reaches into supporting domains" in others — inconsistent direction convention, but each individual edge is independently justifiable. **No action needed**, informational only.

3. **`supplier` → `report`**: `SupplierController`/`SupplierService` imports `report.dto.SupplierPerformanceDTO` and `report.service.ReportService` — almost certainly the "Métriques de performance fournisseur" requirement (Module 8). Legitimate cross-domain read.

**No corrective action required for P1-06** — recorded for Phase 11 documentation enrichment only (add a short "Inter-domain Dependencies" subsection to ARCHITECTURE.md §2).

---

## P1-07 — MapStruct mapper coverage is inconsistent across domains

**Severity: BASSE** (no entity-leak found — CLAUDE.md's "never expose JPA entities directly" rule holds — but mapping strategy is inconsistent: some domains use MapStruct `@Mapper` interfaces, others use manual/inline DTO construction, and `workflow`'s `DelegationController`/`ApprovalController.getApprovalSteps` use raw `Map<String,Object>` instead of typed DTOs entirely)

**Mapper inventory** (8 MapStruct mappers exist):
```
department/mapper/DepartmentMapper.java
invoice/mapper/InvoiceItemMapper.java
invoice/mapper/InvoiceMapper.java
purchasing/mapper/MatchingConfigMapper.java
purchasing/mapper/PurchaseOrderMapper.java
supplier/mapper/SupplierMapper.java
user/mapper/UserMapper.java
webhook/mapper/WebhookMapper.java
```

**Domains with entity models but NO MapStruct mapper**:

| Domain | Entities | Controller return type | Verdict |
|---|---|---|---|
| `audit` | `AuditLog` | `ApiResponse<PagedResponse<...>>` (returns directly, not via `ResponseEntity`) — uses `PagedResponse.of(result)` (line 70/91/111 of `AuditController.java`); needs verification of what `result`'s element type is, but no raw `AuditLog` import found in controller's `ResponseEntity`/`ApiResponse` generic params | Likely OK — `PagedResponse.of()` probably wraps a DTO from the service layer; **not independently verified this session**, low priority |
| `auth` | `ActiveSession` | `AdminSessionController` returns `List<Map<String,Object>>` built from manual `LinkedHashMap` with raw `s.getUser().getId()`/`s.getUser().getUsername()` field access | **Already flagged in P1-05** — fix (extract service + DTO + mapper) covers this too |
| `notification` | `Notification`, `NotificationType` | `ResponseEntity<ApiResponse<PagedResponse<NotificationDTO>>>` — returns `NotificationDTO`, not raw entity | OK — DTO exists, just built manually (no MapStruct `@Mapper`); acceptable per CONVENTIONS (MapStruct not mandated, just common) |
| `payment` | `Payment`, `PaymentMethod`, `RemittanceAdvice` | `ResponseEntity<ApiResponse<PaymentDTO>>` / `PagedResponse<PaymentDTO>` — DTO used | OK — same as `notification`, manual mapping not MapStruct |
| `workflow` | `ApprovalDelegation`, `ApprovalStep`, `ApprovalStepStatus`, `InvoiceStatusHistory` | `DelegationController` returns `Map<String,Object>` (raw, manual field access `d.getDelegator().getUsername()`); `ApprovalController.getApprovalSteps` returns `List<Map<String,Object>>` | **`DelegationController` already flagged in P1-05.** `ApprovalController.getApprovalSteps` returning `Map<String,Object>` instead of a typed `ApprovalStepResponse` DTO is a smaller, separate style issue — not a security/entity-leak issue (no raw entity reference escapes), but inconsistent with the rest of the codebase's DTO discipline |
| `report`, `storage`, `mfa`, `ocr` | (no entity models in `model`/`entity` dirs — these domains are service-only, e.g., `report` produces DTOs/PDFs on the fly, `storage` wraps MinIO, `mfa`/`ocr` are stateless services) | N/A | OK — no entities to map |

**Conclusion**: No CLAUDE.md violation found here beyond what's already captured in P1-05 (raw `Map<String,Object>` in `DelegationController`/`AdminSessionController`/`ApprovalController.getApprovalSteps` is a DTO-discipline gap, not an entity leak — `Map<String,Object>` doesn't expose JPA proxies/lazy-loading artifacts the way returning `ApprovalDelegation` directly would, but it is untyped and bypasses i18n/serialization consistency). Recommend (Phase 10, low priority, can be bundled with P1-05's `ApprovalController`/`DelegationController` fixes): replace `ApprovalController.getApprovalSteps`'s `List<Map<String,Object>>` with a proper `ApprovalStepResponse` DTO + mapper, for consistency — but this is BASSE severity and can be deferred if time-constrained.

---

## Phase 1 — Closing Summary

All planned Phase 1 scope items are now complete:
- ✅ Actual vs documented package structure (P1-01)
- ✅ §4.1 Gap table staleness, all 8 entries incl. GAP4/TLS (P1-02, P1-02b)
- ✅ Security filter chain diagram vs actual (P1-03)
- ✅ Three-Way Matching integration point vs actual (P1-04)
- ✅ Layering audit — ALL 21 controllers scanned for repository injection (P1-05) — **most significant finding of Phase 1**, a real regression
- ✅ Domain coupling graph (P1-06)
- ✅ DTO/Mapper consistency (P1-07)

**Net new issues for Phase 8**: P1-01 (MOYENNE), P1-02 (HAUTE), P1-03 (HAUTE), P1-05 (HAUTE). P1-06/P1-07 are informational/BASSE, foldable into Phase 11 doc updates without separate ISSUES.md entries (or listed as BASSE for completeness).

---

## Summary for Phase 8 (ISSUES.md)

| ID | Severity | Summary | File:line |
|---|---|---|---|
| P1-01 | MOYENNE | Package names in ARCHITECTURE.md §2/§10 don't match actual (`matching`→`purchasing`, `integration`→`webhook`, `reporting`→`report`); §2 omits 6 packages | `docs/ARCHITECTURE.md` §2, §10 |
| P1-02 | HAUTE | §4.1 Gap table stale: ALL 8 gaps stale — 7 fully resolved (incl. GAP4/TLS), GAP6 partially (backend done, frontend missing) | `docs/ARCHITECTURE.md` §4.1 |
| P1-03 | HAUTE | §5 security filter chain diagram omits 3 filters, misplaces CORS (not actually in the security chain), wrong relative order | `docs/ARCHITECTURE.md` §5 vs `SecurityConfig.java:71-75` |
| P1-04 | — (positive) | Three-Way Matching wiring verified correct + exceeds doc (undocumented duplicate-check) | `InvoiceStateMachineServiceImpl.java:84-93,137-205` |
| P1-05 | HAUTE | 5 controllers inject repositories directly (CLAUDE.md §3 violation); `AdminSessionController` + `IntegrationStatusController` have ZERO service layer — **new regressions introduced within the same T3/T6/T7 cycle that claimed to fix this class of bug** | `AdminSessionController.java`, `IntegrationStatusController.java`, `WebhookController.java`, `DelegationController.java`, `InvoiceDocumentController.java` |
| P1-06 | BASSE | `invoice`↔`purchasing` bidirectional package dependency undocumented (not a Spring bean cycle, by-design reconciliation pattern) | `docs/ARCHITECTURE.md` §2 (missing "Inter-domain Dependencies" subsection) |
| P1-07 | BASSE | `ApprovalController.getApprovalSteps` returns `List<Map<String,Object>>` instead of typed DTO (style/consistency only, no entity leak) | `ApprovalController.java:36` |
