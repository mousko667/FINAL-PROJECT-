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
    PostgreSQL 15           MinIO (file storage)
```

---

## 2. Package Structure (Backend)

```
com.oct.invoicesystem/
├── config/
│   ├── SecurityConfig.java
│   ├── JwtConfig.java
│   ├── CorsConfig.java
│   ├── MinioConfig.java
│   ├── MailConfig.java
│   ├── WebSocketConfig.java
│   ├── StateMachineConfig.java
│   ├── OpenApiConfig.java
│   └── AsyncConfig.java          ← @EnableAsync for notification listeners
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
│   ├── reporting/
│   │   └── {controller, service, dto}/
│   │       → ReportController.java, ReportService.java
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

## 5. Security Architecture

```
Request
  ↓
CorsFilter (allowed origins whitelist)
  ↓
JwtAuthenticationFilter (extract + validate Bearer token)
  ↓
AuditLoggingFilter (log POST/PUT/PATCH/DELETE actions)
  ↓
SecurityFilterChain (RBAC rules from SecurityConfig)
  ↓
@PreAuthorize on controller method (method-level guard)
  ↓
Service layer ownership check (e.g. dept-level access)
  ↓
Business logic
```

### Role Hierarchy
```
ROLE_ADMIN
  → can do everything

ROLE_DAF (Directeur Administratif et Financier)
  → BON_A_PAYER authorization, payment validation, all reports

ROLE_VALIDATEUR_N2_{DEPT}
  → Second-level approval for their department

ROLE_VALIDATEUR_N1_{DEPT}
  → First-level approval for their department

ROLE_ASSISTANT_COMPTABLE
  → Create/submit invoices, record payments

ROLE_AUDITEUR
  → Read-only: all invoices, audit logs, reports
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
  postgres:    port 5432 — PostgreSQL 15
  minio:       port 9000/9001 — MinIO object storage
  backend:     port 8080 — Spring Boot app
  frontend:    port 3000 — React app (nginx in prod)
  mailhog:     port 8025 — SMTP test server (dev only)
```

## 10.New Domain Modules (Phase 9)

domain/
├── supplier/
│   └── {controller, service, repository, model, dto, mapper}/
│       → Supplier.java, SupplierStatus.java, SupplierDocument.java
│       → SupplierService.java, SupplierController.java, SupplierMapper.java
│
├── mfa/
│   └── service/MfaService.java
│   └── dto/{MfaSetupResponse.java, MfaValidateRequest.java, MfaConfirmRequest.java}
│
├── matching/
│   └── {controller, service, repository, model, dto}/
│       → PurchaseOrder.java, PurchaseOrderItem.java
│       → GoodsReceiptNote.java, GoodsReceiptItem.java
│       → ThreeWayMatchingResult.java, MatchingConfig.java
│       → ThreeWayMatchingService.java
│       → PurchaseOrderController.java, MatchingConfigController.java
│
└── integration/
└── {controller, service, repository, model, dto}/
→ Webhook.java, WebhookDelivery.java
→ WebhookService.java, WebhookEventPublisher.java
→ WebhookController.java

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
If user has ROLE_DAF | ROLE_ADMIN | ROLE_VALIDATEUR_N1_* | ROLE_VALIDATEUR_N2_*
and mfa_verified = false:
→ login returns { mfa_setup_required: true }
→ only /auth/mfa/setup and /auth/mfa/confirm are accessible


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