# ARCHITECTURE — Technical Decisions & Layer Rules

---

## 1. High-Level Architecture

```
React Frontend (Vite + TypeScript)
        ↕ REST/JSON over HTTPS
Spring Boot Backend (Modular Monolith)
    ├── Security Layer (JWT filter chain)
    ├── Domain Modules (by feature)
    │   ├── auth
    │   ├── user
    │   ├── department
    │   ├── invoice          ← core
    │   ├── workflow         ← core
    │   ├── payment
    │   ├── audit
    │   ├── notification
    │   ├── reporting
    │   └── storage
    └── Shared (exceptions, response wrappers, utils)
        ↕ JPA/JDBC              ↕ S3 API
    PostgreSQL 18           MinIO (file storage)
```

---

## 2. Package Structure (Backend)

```
com.oct.invoicesystem/
├── config/
│   ├── SecurityConfig.java
│   ├── CorsConfig.java
│   ├── WebConfig.java
│   ├── WebSocketConfig.java
│   ├── StateMachineConfig.java
│   ├── OpenApiConfig.java
│   ├── AsyncConfig.java          ← @EnableAsync for notification listeners
│   ├── ProdSecretConfigValidator.java
│   └── security/                 ← extra security filters (registered in the chain)
│       ├── HttpSecurityHeadersFilter.java
│       ├── MfaSetupEnforcementFilter.java
│       └── RateLimitingFilter.java
│
├── domain/
│   ├── auth/
│   │   ├── controller/AuthController.java
│   │   ├── service/{AuthService, JwtService}.java
│   │   ├── dto/{LoginRequest, LoginResponse, RefreshTokenRequest}.java
│   │   └── filter/{JwtAuthenticationFilter, AuditLoggingFilter}.java
│   │
│   ├── user/
│   │   └── {controller, service, repository, model, dto}/
│   │       → User.java, Role.java, UserRole.java
│   │
│   ├── department/              ← NEW — stores OCT departments + approval config
│   │   └── {controller, service, repository, model, dto}/
│   │       → Department.java, DepartmentService.java
│   │
│   ├── invoice/
│   │   ├── controller/{InvoiceController, InvoiceDocumentController}.java
│   │   ├── service/{InvoiceService, InvoiceValidationService, InvoiceDocumentService}.java
│   │   ├── repository/{InvoiceRepository, InvoiceItemRepository, InvoiceDocumentRepository}.java
│   │   ├── model/{Invoice, InvoiceItem, InvoiceDocument, InvoiceStatus}.java
│   │   ├── dto/{InvoiceDTO, InvoiceCreateRequest, InvoiceSummaryDTO, ...}.java
│   │   └── statemachine/{InvoiceStateMachineService, InvoiceEvent, InvoiceStateChangeListener}.java
│   │
│   ├── workflow/
│   │   └── {controller, service, repository, model, dto}/
│   │       → ApprovalStep.java, ApprovalService.java, ApprovalController.java
│   │
│   ├── payment/
│   │   └── {controller, service, repository, model, dto}/
│   │
│   ├── audit/
│   │   └── {service, repository, model, dto}/
│   │       → AuditLog.java, InvoiceStatusHistory.java, AuditService.java
│   │
│   ├── notification/
│   │   └── {service, model, repository, event, listener}/
│   │       → NotificationService.java, EmailService.java
│   │       → InvoiceSubmittedEvent.java, InvoiceRejectedEvent.java, ...
│   │       → EmailNotificationListener.java, WebSocketNotificationListener.java
│   │       → PersistNotificationListener.java
│   │
│   ├── report/                   ← was documented as "reporting/" (wrong)
│   │   └── {controller, service, dto}/
│   │       → ReportController.java, ReportService.java
│   │
│   ├── supplier/                 ← Phase 9 — supplier portal + self-registration
│   │   └── {controller, service, repository, model, dto, mapper}/
│   │       → Supplier.java, SupplierStatus.java, SupplierDocument.java
│   │
│   ├── mfa/                      ← Phase 9 — TOTP multi-factor auth
│   │   └── {service, dto}/
│   │       → MfaService.java
│   │
│   ├── purchasing/               ← Phase 9 — was documented as "matching/" (wrong)
│   │   └── {controller, service, repository, model, dto}/
│   │       → PurchaseOrder.java, GoodsReceiptNote.java,
│   │         ThreeWayMatchingResult.java, MatchingConfig.java
│   │       → ThreeWayMatchingService.java, PurchaseOrderController.java
│   │
│   ├── webhook/                  ← Phase 9 — was documented as "integration/" (wrong)
│   │   └── {controller, service, repository, model, dto, event, mapper}/
│   │       → Webhook.java, WebhookDelivery.java
│   │       → WebhookService.java, WebhookEventPublisher.java
│   │
│   ├── ocr/                      ← invoice field extraction (Tess4J + PDFBox)
│   │   └── {controller, service, dto}/
│   │       → OcrService.java, OcrController.java, OcrExtractionResult.java
│   │
│   └── storage/
│       └── service/{StorageService (interface), MinioStorageService}.java
│
└── shared/
    ├── exception/{GlobalExceptionHandler, ResourceNotFoundException,
    │             UnauthorizedException, WorkflowException, ValidationException}.java
    ├── response/{ApiResponse, PagedResponse}.java
    ├── util/{EncryptionUtil, FileUtil, DateUtil, ReferenceNumberGenerator}.java
    ├── constants/{RoleConstants, StatusConstants}.java
    ├── annotation/Auditable.java
    ├── i18n/MessageSourceConfig.java
    └── mapper/{InvoiceMapper, UserMapper, DepartmentMapper}.java  ← MapStruct
```

### 2.1 Inter-domain Dependencies (P1-06)

Most `domain/` packages depend only on `shared/`. The one notable exception is a
**bidirectional dependency between `invoice` and `purchasing`** (three-way matching),
verified against imports on 2026-06-13:

```
invoice  ──────────────▶  purchasing
  InvoiceController, InvoiceService, InvoiceStateMachineServiceImpl import
  purchasing's ThreeWayMatchingService, PurchaseOrderRepository,
  GoodsReceiptNoteRepository, ThreeWayMatchingResult(Repository),
  MatchingStatus, MatchingResultDTO, MatchingOverrideRequest

purchasing  ───────────▶  invoice
  ThreeWayMatchingResult (entity) references invoice.model.Invoice (FK);
  ThreeWayMatchingService matches against invoice.model.Invoice / InvoiceItem
```

This cycle is intentional and reflects the domain reality (an invoice is matched against
its PO/GRN at submission, and a matching result belongs to an invoice). It is the reason
`InvoiceStateMachineServiceImpl` lives in `invoice` but calls into `purchasing` on the
`SUBMIT` transition. No other domain-to-domain cycles exist; new cross-domain calls should
go service→service (never repository→repository) to keep the boundary explicit.

---

## 3. Layer Rules (Strict)

### Controller layer
- Only routing, request binding, and response wrapping
- Calls ONE service method per endpoint
- Uses `@PreAuthorize` on every method
- Returns `ResponseEntity<ApiResponse<T>>`
- Never accesses repositories directly
- Never contains if/else business logic

### Service layer
- All business logic lives here
- Validates business rules (not just DTO validation)
- Calls repositories, other services, and publishes events
- Annotated with `@Transactional` where needed
- All public methods have Javadoc
- Never returns raw entities — always maps to DTOs via MapStruct

### Repository layer
- Spring Data JPA only
- Complex queries use `@Query` with JPQL or native SQL (named clearly)
- No business logic
- No direct entity modification outside of entity methods

### Entity / Model layer
- JPA entities with proper constraints
- Use `@Version` for optimistic locking on Invoice
- Use `deleted_at` for soft delete — never `@Where` filters (explicit in queries)
- Sensitive fields annotated with `@Convert` using `EncryptionAttributeConverter`

---

## 4. Key Technology Decisions

| Concern | Choice | Rationale |
|---|---|---|
| Workflow engine | Spring State Machine | Lightweight, Spring-native, educational value |
| Department config | Database table | Admin-configurable without code changes |
| Async events | Spring `@ApplicationEvent` + `@Async` | Decoupled, no failed email blocks a transaction |
| Entity→DTO | MapStruct | Compile-time, no reflection, fast |
| DB migrations | Flyway | Versioned, reproducible, production-safe |
| File storage | MinIO | S3-compatible, self-hosted, free |
| API docs | SpringDoc OpenAPI 3 | Auto-generated Swagger UI |
| i18n | Spring MessageSource | Standard, property-file based |
| Testing | JUnit 5 + Mockito + MockMvc + Testcontainers | Full stack coverage |

---

## 4.1 Known Implementation Gaps

> Last updated: 2026-06-13 (P11-20 / audit P1-02). The original 8-gap table (dated
> 2026-06-06) was entirely stale: 7 of 8 gaps had since been implemented (T1–T7 cycle,
> 2026-06-07) but never closed here. Re-verified against the codebase this session — only
> GAP 6's **frontend** remains open. Full history in `docs/KNOWN_ISSUES_REGISTRY.md`.

| # | Gap | Status (re-verified 2026-06-13) |
|---|---|---|
| GAP 1 | OCR field extraction | ✅ **Resolved** — Tess4J 5.11.0 + PDFBox 3.0.3 (`pom.xml`), `domain/ocr/OcrService`, `OcrServiceTest` present |
| GAP 2 | JWT HS256 → RS256 | ✅ **Resolved** — RS256 in place: `JwtService` loads an RSA key pair from `jwt.private-key`/`jwt.public-key` (`application.yaml`) |
| GAP 3 | GitHub Actions CI pipeline | ✅ **Resolved** — `.github/workflows/ci.yml` exists |
| GAP 4 | TLS 1.3 in Spring Boot | ✅ **Resolved** — `application.yaml` `server.ssl` sets `protocol/enabled-protocols: TLSv1.3` + PKCS12 keystore |
| GAP 5 | OWASP ZAP security scan | ✅ **Resolved** — `.github/workflows/security-scan.yml` + `.github/zap-rules.tsv` |
| GAP 6 | Approval Delegation | ✅ **Resolved** (2026-06-13, P11-44) — backend was already complete (V40 migration, `ApprovalDelegation` entity, `DelegationService`/`DelegationController` + tests); the missing frontend is now built: `AdminDelegationsPage` (admin-only, route `/admin/delegations`, sidebar entry) lists active delegations per department with revoke, and creates delegations (delegator/delegatee/date-range/reason) over the 3 existing endpoints |
| GAP 7 | Financial audit sub-typing | ✅ **Resolved** — implemented (T7) |
| GAP 8 | Archive full-text search | ✅ **Resolved** — implemented (T7) |

## 4.2 Resolved Architecture Issues (from KNOWN_ISSUES_REGISTRY.md)

| # | Issue | Resolution |
|---|---|---|
| PROB-001 | User lost on page refresh (Redux state not rehydrated) | AuthRehydrator component calls GET /profile on startup |
| PROB-002 | Supplier routes blocked by wrong guard | Added SupplierRoute guard, separate from ProtectedRoute |
| PROB-003 | boolean isActive → isIsActive() Lombok double-prefix bug | Renamed field to `active`, Lombok generates `isActive()` |
| PROB-004 | RoleGuard showing error UI in sidebar | Split into RoleGuard (null) + PageRoleGuard (error UI) |
| PROB-005 | Audit log page always empty | Fixed endpoint /audit-logs/system → /audit-logs |
| PROB-009 | Flyway checksum violation on startup | Never modify applied migrations — added to absolute rules |
| PROB-011 | Nginx serving old frontend after deploy | docker cp + nginx -s reload procedure documented |
| PROB-012 | iText 8 Cell.setBorderColor() not found | Use SolidBorder instead — iText 8 API change |

## 4.3 Deployment Procedures (verified 2026-06-06)

### Prerequisite: host-native PostgreSQL (P5-01, Option B — confirmed 2026-06-12)

`docker-compose.yml` does **not** manage PostgreSQL. A host-native PostgreSQL 18
instance must already be running before `docker-compose up`, listening on
**port 5433** with database **`oct_invoice`** (user `postgres`). The `backend`
container connects to it via `host.docker.internal:5433` (configured through
`extra_hosts: host.docker.internal:host-gateway` and the `DB_HOST`/`DB_PORT`
environment variables, both overridable via `.env`).

This applies to local dev and to any fresh clone — `docker-compose up -d` alone
will not provision a database. Start/verify the host Postgres instance first
(e.g. as a native Windows service or a separately-managed container), then run
`./mvnw flyway:migrate` (or let the app's Flyway integration migrate on startup)
against `oct_invoice` before starting `docker-compose`.

### Frontend deploy (no image rebuild)
```bash
npm run build
docker cp dist/. oct_frontend:/usr/share/nginx/html/
docker exec oct_frontend nginx -s reload
```

### Backend deploy (no image rebuild)
```powershell
.\mvnw.cmd -DskipTests package
docker cp target\invoice-system-1.0.0-SNAPSHOT.jar oct_backend:/app/app.jar
docker restart oct_backend
# Wait ~30s for startup. Check: docker logs oct_backend --tail=20
```

### Verify all services healthy
```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
# Expected: oct_backend, oct_frontend, oct_minio, oct_minio_init, oct_mailhog — all Up
# (PostgreSQL is host-native, not a docker-compose service — verify separately,
# e.g. `pg_isready -h localhost -p 5433`)
```

## 4.4 Flyway Migration History — consolidated baseline (2026-06-22)

The original migration history grew to **60 files with churn** (create-then-revert pairs such
as the webhook `secret_encrypted` column added in V26 and dropped in V30; repeated role/user
seed fixes across V3/V16/V23/V31/V33; a `V36–V38` numbering gap; and data-migrations that only
made sense against a legacy DB). During the 2026-06-22 sanitization pass this was **rewritten
into a single contiguous, no-churn baseline of 34 migrations (`V1`…`V34`)** — each table created
in its final shape, seeds consolidated, the numbering gap closed.

**Verification:** a fresh `flyway:migrate` of the consolidated set produces a schema
structurally identical to the previous one — 46 tables, 400 columns, 68 indexes, 72 FKs, 11
triggers, 5 check constraints (pg_dump diff empty) — and the full backend suite passes (491
tests, 0 failures, with `ddl-auto: validate`). See `docs/DATABASE.md` for the ordered list.

**Important:** this baseline targets a **freshly-created database**. The removed
data-migration steps (back-filling `suppliers` from flat invoice fields; nulling plaintext
bank details) were no-ops on a clean DB and are intentionally gone — the consolidated set does
not migrate a pre-existing legacy database.

**Rule unchanged:** add new schema changes as the next contiguous version (`V35`, …). Never
modify a migration that has already been applied — its checksum is locked in
`flyway_schema_history` (see PROB-009).

---

## 5. Security Architecture

> Verified against `SecurityConfig.java:71-75` on 2026-06-13 (P11-21 / audit P1-03). The
> chain is anchored on `UsernamePasswordAuthenticationFilter` (UPAF): three filters are
> registered `addFilterBefore` it and two `addFilterAfter`. When multiple filters target the
> same anchor, Spring Security preserves registration order as a stable tie-break.
>
> **CORS is NOT part of this filter chain.** It is configured via `CorsConfig implements
> WebMvcConfigurer` (Spring MVC layer) plus `.cors(cors -> {})` in `SecurityConfig` — separate
> from the security filter list.

```
Request
  ↓
HttpSecurityHeadersFilter   (security response headers)        ┐
  ↓                                                            │ addFilterBefore(UPAF)
RateLimitingFilter          (throttle / brute-force guard)     │ (registration order)
  ↓                                                            │
JwtAuthenticationFilter     (extract + validate Bearer token)  ┘
  ↓
[ UsernamePasswordAuthenticationFilter ]  (anchor)
  ↓
MfaSetupEnforcementFilter   (force MFA setup where mandatory)   ┐ addFilterAfter(UPAF)
  ↓                                                            │ (registration order)
AuditLoggingFilter          (log POST/PUT/PATCH/DELETE actions) ┘
  ↓
SecurityFilterChain authorization (RBAC rules from SecurityConfig)
  ↓
@PreAuthorize on controller method (method-level guard)
  ↓
Service layer ownership check (e.g. dept-level access)
  ↓
Business logic
```

### Role Hierarchy

There are exactly **six roles** in this system. No Auditor role exists.

```
ROLE_ADMIN
  → Manages all user accounts, system config, approval matrix
  → Access to system/security audit trail ONLY (not financial data)
  → MFA mandatory

ROLE_DAF (CFO — Directeur Administratif et Financier)
  → Level 1 approver for Finance department invoices
  → Issues Bon à Payer (final payment authorisation) for ALL departments
  → Access to financial audit trail ONLY (not system/security logs)
  → MFA mandatory

ROLE_VALIDATEUR_N2_{DEPT}
  → Second-level approval for their department (IT, Infrastructure, Workshop only)
  → MFA mandatory

ROLE_VALIDATEUR_N1_{DEPT}
  → First-level approval for their department
  → MFA mandatory

ROLE_ASSISTANT_COMPTABLE
  → Creates/submits invoices, validates, manages suppliers, records payments
  → MFA mandatory

ROLE_SUPPLIER
  → Submits own invoices via supplier portal, tracks status
  → MFA NOT required (only role exempt from MFA)
```

---

## 6. Internationalization (i18n) Rules

- All user-facing strings (labels, messages, errors) use message keys
- Never hardcode French or English text in Java code or React components
- Backend: `messages_fr.properties` (default) + `messages_en.properties`
- Frontend: `fr.json` + `en.json` in `src/i18n/`
- Language detected from `Accept-Language` header (backend) and user preference (frontend)
- Status names (BROUILLON, SOUMIS, etc.) are always stored as enum constants — translated for display only

### Key naming convention (backend)
```
invoice.status.brouillon = Brouillon
invoice.status.soumis = Soumis
workflow.action.submit = Soumettre
error.invoice.not_found = Facture introuvable
error.workflow.invalid_transition = Transition non autorisée : {0} → {1}
validation.rejection_reason.required = Le motif de rejet est obligatoire
```

---

## 7. API Design Rules

- All endpoints prefixed: `/api/v1/`
- HTTP methods: GET (read), POST (create/action), PUT (full update), PATCH (partial), DELETE (soft)
- Paginated lists: `?page=0&size=20&sort=createdAt,desc`
- Filter parameters on GET list endpoints: `?status=SOUMIS&department=INFO&from=2026-01-01`
- All responses wrapped: `{"success": true, "data": {...}, "message": null, "timestamp": "..."}`
- Error responses: `{"success": false, "data": null, "message": "...", "errors": [...]}`
- `Accept-Language: fr` or `Accept-Language: en` controls response message language

---

## 8. Environment Profiles

| Profile | Purpose | DB | Notes |
|---|---|---|---|
| `dev` | Local development | `oct_invoice_dev` | H2 in-memory for unit tests |
| `test` | CI/CD integration tests | Testcontainers PostgreSQL | Real DB, real MinIO |
| `prod` | Production | `${DATABASE_URL}` | All secrets from env vars |

---

## 9. Docker Compose Services

```yaml
services:
  # PostgreSQL is host-native (port 5433, db oct_invoice) — NOT a compose service (see §4.3)
  minio:       port 9000/9001 — MinIO object storage
  minio_init:  one-shot — creates the MinIO bucket, then exits
  backend:     port 8080 — Spring Boot app (connects to host Postgres via host.docker.internal:5433)
  frontend:    port 3000 — React app (nginx in prod)
  mailhog:     port 1025/8025 — SMTP test server (dev only)
```

## 10. Phase 9+ Domain Modules — folded into §2

The Phase 9 modules (`supplier`, `mfa`, `purchasing`, `webhook`) and the later `ocr`
module are now part of the single package tree in **§2** above. This section previously
listed them separately under names that never matched the code; corrected here as a lookup
for anyone searching the old names (P1-01):

| Documented name (pre-P11) | Actual package on disk |
|---|---|
| `domain/matching/` | `domain/purchasing/` — PurchaseOrder, GoodsReceiptNote, ThreeWayMatchingResult, MatchingConfig, ThreeWayMatchingService, PurchaseOrderController |
| `domain/integration/` | `domain/webhook/` — Webhook, WebhookDelivery, WebhookService, WebhookEventPublisher, WebhookController |
| `domain/reporting/` (§2) | `domain/report/` — ReportController, ReportService |

§2 is the authoritative, complete package tree (all 15 `domain/` packages + `config/security/`).

---

## New Role (Phase 9)

ROLE_SUPPLIER
→ Submit own invoices via /api/v1/supplier/invoices
→ View own invoice status only
→ Manage own profile via /api/v1/supplier/profile
→ Download own remittance advice
→ Cannot access /api/v1/invoices/** (staff endpoints)
→ Cannot access any other supplier's data
→ Cannot perform workflow actions (N1, N2, DAF, assign, reject)
→ supplierId embedded in JWT claims at login

---

## Security Architecture Addition (MFA)

Request → POST /auth/login
↓
Credentials validated
├── FAIL → increment failed_login_attempts; lock if ≥5 → HTTP 423
└── PASS
↓
mfa_enabled = true AND mfa_verified = true?
├── NO  → return full JWT (existing flow)
└── YES → return { mfa_required: true, pre_auth_token (5min TTL) }
↓
POST /auth/mfa/validate { preAuthToken, otp }
↓
OTP valid?
├── NO → increment failed_login_attempts; lock if ≥5
└── YES → return full JWT
MFA mandatory enforcement:
If user has ROLE_ASSISTANT_COMPTABLE | ROLE_DAF | ROLE_ADMIN | ROLE_VALIDATEUR_N1_* | ROLE_VALIDATEUR_N2_*
and mfa_verified = false:
→ login returns { mfa_setup_required: true }
→ only /auth/mfa/setup and /auth/mfa/confirm are accessible
Note: ROLE_SUPPLIER is the ONLY role exempt from MFA.


## Three-Way Matching Integration Point

InvoiceStateMachineServiceImpl.sendEvent(SUBMIT)
↓
invoice.purchaseOrderId present?
├── NO  → skip matching, proceed to SOUMIS normally
└── YES → ThreeWayMatchingService.match(invoice, po, grn)
↓
Result?
├── MATCHED  → proceed to SOUMIS
├── PARTIAL  → proceed to SOUMIS with matching_status = PARTIAL
└── MISMATCH → throw WorkflowException; invoice stays BROUILLON
↓ [DAF/ADMIN records override]
→ proceed to SOUMIS with matching_status = OVERRIDDEN

---

## Webhook Architecture

Domain Event Published (InvoiceSubmittedEvent, etc.)
↓
WebhookEventPublisher (@Async @EventListener)
↓
For each active Webhook subscribed to this event:
build JSON payload
sign with HMAC-SHA256(payload, rawSecret)
POST to webhook.url with X-OCT-Signature header (timeout: 5s)
├── SUCCESS → log delivery (success=true, response_status, attempt_count=1)
└── FAIL    → retry with backoff 5s, 25s, 125s (max 3 attempts)
→ log final delivery (success=false, attempt_count=3)
→ DO NOT propagate exception (never blocks invoice transaction)

---

## Key Technology Additions (Phase 9)

| Concern | Choice | Rationale |
|---|---|---|
| TOTP / MFA | dev.samstevens.totp:totp:1.7.1 | Standard TOTP RFC 6238, Spring-compatible |
| Webhook delivery | RestTemplate with timeout | Already available, sufficient for outbound calls |
| Remittance PDF | iText (already in pom.xml) | Same library used for audit PDF exports |
| Supplier portal isolation | ROLE_SUPPLIER + supplierId JWT claim | JWT claim used server-side; body supplierId ignored for suppliers |