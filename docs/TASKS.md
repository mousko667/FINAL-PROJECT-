# TASKS — Development Task List

> **How to use this file:**
> - Work top-to-bottom, phase by phase
> - Never start Phase N+1 until all tasks in Phase N are ✅
> - Mark each task ✅ only when: code written + tests pass + i18n keys added
> - Log blockers in `docs/MEMORY.md`

---

## Phase 0 — Project Bootstrap
*Goal: Running skeleton with DB, security, and Docker*

- [x] **P0-01** Initialize Spring Boot project via Spring Initializr with all dependencies from `docs/ARCHITECTURE.md §4`
- [x] **P0-02** Create `docker-compose.yml` with postgres, minio, backend, frontend, mailhog services
- [x] **P0-03** Create `Dockerfile` for backend (multi-stage: build + runtime)
- [x] **P0-04** Create Flyway migration `V1__create_users_roles.sql`
- [x] **P0-05** Create Flyway migration `V2__create_departments.sql` with all 9 OCT departments seeded
- [x] **P0-06** Implement `User`, `Role`, `UserRole` entities
- [x] **P0-07** Implement `Department` entity with `requires_n2`, `n1_role`, `n2_role` columns
- [x] **P0-08** Configure `SecurityConfig.java` with JWT filter chain and RBAC rules
- [x] **P0-09** Implement `JwtService` (generate + validate tokens)
- [x] **P0-10** Implement `JwtAuthenticationFilter`
- [x] **P0-11** Implement `AuthController` (login + refresh endpoints)
- [x] **P0-12** Implement `GlobalExceptionHandler` with structured error responses
- [x] **P0-13** Implement `ApiResponse<T>` and `PagedResponse<T>` wrappers
- [x] **P0-14** Configure `MessageSource` for FR/EN with `messages_fr.properties` and `messages_en.properties`
- [x] **P0-15** Implement `EncryptionUtil` (AES-256 encrypt/decrypt)
- [x] **P0-16** Implement `AuditLoggingFilter`
- [x] **P0-17** Configure `AsyncConfig` (`@EnableAsync` + thread pool)
- [x] **P0-18** Configure OpenAPI / Swagger (`OpenApiConfig.java`)
- [x] **P0-19** Seed admin user + all roles via `V3__seed_roles_and_admin.sql`
- [x] **P0-20** Verify: `docker-compose up` → app starts, `/api/v1/auth/login` returns JWT ✅

**Phase 0 Exit Criteria:** `docker-compose up` produces a running app, login works, Swagger UI accessible at `/swagger-ui.html`

---

## Phase 1 — User & Department Management
*Goal: Admins can manage users and departments*

- [x] **P1-01** Implement `UserController` (CRUD + role assignment) — Admin only
- [x] **P1-02** Implement `UserService` with BCrypt password hashing
- [x] **P1-03** Implement `UserRepository` with `findByEmail`, `findByUsername`
- [x] **P1-04** Implement `UserDTO`, `UserCreateRequest`, `UserUpdateRequest`
- [x] **P1-05** Implement `UserMapper` (MapStruct)
- [x] **P1-06** Implement `DepartmentController` (list, get, update approval config) — Admin only
- [x] **P1-07** Implement `DepartmentService` + `DepartmentRepository`
- [x] **P1-08** Add i18n keys for user and department management
- [x] **P1-09** Write unit tests: `UserServiceTest` (create, update, assign role, deactivate)
- [x] **P1-10** Write unit tests: `DepartmentServiceTest`
- [x] **P1-11** Write integration tests: `UserControllerTest` (all endpoints, all roles)
- [x] **P1-12** Write integration tests: `DepartmentControllerTest`

**Phase 1 Exit Criteria:** Admin can create users and assign roles via API. All tests pass.

---

## Phase 2 — Invoice Core (CRUD + Documents)
*Goal: Accounting assistant can create, edit, and attach documents to invoices*

- [x] **P2-01** Create Flyway migrations: `V4__create_invoices.sql`, `V5__create_invoice_items.sql`, `V6__create_invoice_documents.sql`
- [x] **P2-02** Implement `Invoice`, `InvoiceItem`, `InvoiceDocument` entities
- [x] **P2-03** Implement `InvoiceStatus` enum (all 9 statuses FR/EN)
- [x] **P2-04** Implement `InvoiceRepository` with paginated queries + filter support
- [x] **P2-05** Implement `InvoiceService` (create, update, soft-delete, list, get-by-id)
- [x] **P2-06** Implement `InvoiceValidationService` (all 10 business rules from `docs/WORKFLOW.md §8`)
- [x] **P2-07** Implement `ReferenceNumberGenerator` — format `FAC-{YYYY}-{NNNNN}` with DB sequence
- [x] **P2-08** Implement `InvoiceController` (CRUD endpoints)
- [x] **P2-09** Implement `MinioStorageService` (upload, download pre-signed URL, delete)
- [x] **P2-10** Implement `InvoiceDocumentService` (validate MIME via Tika, compute SHA-256, store in MinIO)
- [x] **P2-11** Implement `InvoiceDocumentController` (upload, list, download)
- [x] **P2-12** Implement `InvoiceMapper`, `InvoiceItemMapper` (MapStruct)
- [x] **P2-13** Add all invoice-related i18n keys
- [x] **P2-14** Write unit tests: `InvoiceServiceTest` (create, update, soft-delete, ownership)
- [x] **P2-15** Write unit tests: `InvoiceValidationServiceTest` (all 10 rules, happy + edge cases)
- [x] **P2-16** Write unit tests: `InvoiceDocumentServiceTest` (MIME rejection, checksum, size limit)
- [x] **P2-17** Write integration tests: `InvoiceControllerTest` (all endpoints, ASSISTANT_COMPTABLE + ADMIN roles)
- [x] **P2-18** Write integration tests: `InvoiceDocumentControllerTest`

**Phase 2 Exit Criteria:** Accounting assistant can create a draft invoice with line items, attach a PDF, and list their invoices. All tests pass.

---

## Phase 3 — Workflow Engine (BAP State Machine)
*Goal: Full invoice lifecycle through all approval stages*

- [x] **P3-01** Create Flyway migrations: `V7__create_approval_steps.sql`, `V8__create_invoice_status_history.sql`
- [x] **P3-02** Implement `ApprovalStep` entity
- [ ] **P3-03** Implement `InvoiceStatusHistory` entity
- [ ] **P3-04** Implement `InvoiceEvent` enum (SUBMIT, ASSIGN_REVIEWER, VALIDATE_N1, VALIDATE_N2, BON_A_PAYER, RECORD_PAYMENT, REJECT, RESUBMIT, ARCHIVE)
- [ ] **P3-05** Implement `StateMachineConfig` — define all states + transitions from `docs/WORKFLOW.md §3`
- [ ] **P3-06** Implement department-aware transition guard (1-level vs 2-level based on `Department.requires_n2`)
- [ ] **P3-07** Implement `InvoiceStateMachineService` (send event, persist state, write status history)
- [ ] **P3-08** Implement `InvoiceStateChangeListener` (writes to `invoice_status_history` on every transition)
- [ ] **P3-09** Implement `ApprovalService` (create step, record decision, check completeness, deadline tracking)
- [ ] **P3-10** Implement `ApprovalController` (assign-reviewer, validate-n1, validate-n2, bon-a-payer, reject endpoints)
- [ ] **P3-11** Implement `InvoiceController` additions: submit, resubmit endpoints
- [ ] **P3-12** Implement all transition guards (role check, rejection reason min length, document required)
- [ ] **P3-13** Add all workflow-related i18n keys (FR + EN for every state and action)
- [ ] **P3-14** Write unit tests: `InvoiceStateMachineServiceTest` (every valid transition + every invalid transition)
- [ ] **P3-15** Write unit tests: `ApprovalServiceTest` (assign, validate, reject, deadline check)
- [ ] **P3-16** Write integration tests: `ApprovalControllerTest` — full lifecycle for SINGLE-level dept
- [ ] **P3-17** Write integration tests: `ApprovalControllerTest` — full lifecycle for TWO-level dept (INFO)
- [ ] **P3-18** Write integration tests: reject + resubmit flow
- [ ] **P3-19** Write integration test: wrong-role rejection (N1 trying to do N2 action → 403)

**Phase 3 Exit Criteria:** A full invoice lifecycle (BROUILLON → ARCHIVE) works end-to-end for both 1-level and 2-level departments. All transitions, guards, and role checks verified by tests.

---

## Phase 4 — Notifications
*Goal: Internal email + in-app notifications on every workflow event*

- [ ] **P4-01** Create Flyway migration `V9__create_notifications.sql`
- [ ] **P4-02** Implement `Notification` entity + `NotificationRepository`
- [ ] **P4-03** Define domain events: `InvoiceSubmittedEvent`, `InvoiceValidatedEvent`, `InvoiceRejectedEvent`, `BonAPayerEvent`, `ApprovalDeadlineEvent`
- [ ] **P4-04** Implement `EmailNotificationListener` (`@Async @EventListener`) — sends via `EmailService`
- [ ] **P4-05** Implement `EmailService` with Thymeleaf templates (FR + EN)
- [ ] **P4-06** Create email templates: `invoice-submitted.html`, `invoice-rejected.html`, `invoice-approved.html`, `deadline-reminder.html`
- [ ] **P4-07** Implement `PersistNotificationListener` (`@Async @EventListener`) — saves to DB
- [ ] **P4-08** Configure `WebSocketConfig` (STOMP over SockJS, user-specific topics)
- [ ] **P4-09** Implement `WebSocketNotificationListener` (`@Async @EventListener`)
- [ ] **P4-10** Implement `NotificationController` (list, mark-read, mark-all-read)
- [ ] **P4-11** Implement `@Scheduled` deadline reminder job (runs daily, checks overdue approval steps)
- [ ] **P4-12** Write unit tests: all listener tests with mock `EmailService` and `SimpMessagingTemplate`
- [ ] **P4-13** Write integration tests: event publishing verified end-to-end

**Phase 4 Exit Criteria:** Submitting an invoice triggers an in-app notification AND an email to the N1 approvers. Notifications persisted in DB. WebSocket delivers real-time alerts.

---

## Phase 5 — Payment & Archiving
*Goal: Record payments and archive completed invoices*

- [ ] **P5-01** Create Flyway migration `V10__create_payments.sql`
- [ ] **P5-02** Implement `Payment` entity + `PaymentRepository`
- [ ] **P5-03** Implement `PaymentService` (record payment, trigger PAYE→ARCHIVE)
- [ ] **P5-04** Implement `PaymentController` (record, get, list)
- [ ] **P5-05** Write unit tests: `PaymentServiceTest`
- [ ] **P5-06** Write integration tests: `PaymentControllerTest`

**Phase 5 Exit Criteria:** A BON_A_PAYER invoice can be paid and automatically archives.

---

## Phase 6 — Audit & Reporting
*Goal: Full audit trail and management reports*

- [ ] **P6-01** Create Flyway migration `V11__create_audit_logs.sql`
- [ ] **P6-02** Implement `AuditLog` entity + `AuditRepository`
- [ ] **P6-03** Implement `AuditService` (append-only log writes)
- [ ] **P6-04** Implement `AuditController` (paginated log, filter by user/entity/action)
- [ ] **P6-05** Implement `ReportService` (KPI queries: avg processing time, volume by status, rejection rate, overdue count, top suppliers)
- [ ] **P6-06** Implement Excel export (Apache POI — filtered invoice list)
- [ ] **P6-07** Implement PDF export — individual invoice audit report (iText 7)
- [ ] **P6-08** Implement PDF export — compliance report for date range (iText 7)
- [ ] **P6-09** Implement `ReportController` (kpi, export-xlsx, export-pdf)
- [ ] **P6-10** Write unit tests: `ReportServiceTest` (KPI calculations with known dataset)
- [ ] **P6-11** Write integration tests: `ReportControllerTest` + `AuditControllerTest`

**Phase 6 Exit Criteria:** DAF/Auditor can view KPI dashboard data and export reports. Audit log is complete and tamper-evident.

---

## Phase 7 — Frontend (React)
*Goal: Complete working UI for all roles*

- [ ] **P7-01** Bootstrap React project (Vite + TypeScript + TailwindCSS + shadcn/ui)
- [ ] **P7-02** Configure Axios client with JWT interceptor + refresh logic
- [ ] **P7-03** Configure React Query (`QueryClientProvider`)
- [ ] **P7-04** Configure Redux Toolkit (auth slice + notification slice)
- [ ] **P7-05** Configure React Router with nested routes + `ProtectedRoute` + `RoleGuard`
- [ ] **P7-06** Configure i18n (react-i18next with `fr.json` + `en.json`)
- [ ] **P7-07** Configure STOMP/WebSocket client hook (`useWebSocket.ts`)
- [ ] **P7-08** Build `LoginPage` with form validation (React Hook Form + Zod)
- [ ] **P7-09** Build app shell: sidebar, header, notification bell, language switcher
- [ ] **P7-10** Build `InvoiceListPage` (table, filters, pagination, status badges)
- [ ] **P7-11** Build `InvoiceCreatePage` (multi-step form: details → line items → documents)
- [ ] **P7-12** Build `InvoiceDetailPage` (full view + `InvoiceTimeline` + `InvoiceActionPanel`)
- [ ] **P7-13** Build `InvoiceActionPanel` (role-aware: shows validate/reject/bon-a-payer based on user role + invoice state)
- [ ] **P7-14** Build `DocumentUploader` (drag-and-drop, MIME preview, progress bar)
- [ ] **P7-15** Build `DashboardPage` with role-specific content
- [ ] **P7-16** Build KPI cards + Recharts charts (donut, bar, line)
- [ ] **P7-17** Build `NotificationDropdown` + unread badge
- [ ] **P7-18** Build `ReportsPage` with export panel
- [ ] **P7-19** Build Admin pages (user management, department config, audit log)
- [ ] **P7-20** Write component tests (React Testing Library): `InvoiceActionPanel`, `InvoiceTimeline`, `RoleGuard`
- [ ] **P7-21** Write API hook tests: `useInvoices`, `useAuth`

**Phase 7 Exit Criteria:** All user journeys completable in the browser for all roles. FR/EN language switch works. Role guard prevents unauthorized UI actions.

---

## Phase 8 — Integration, Hardening & Documentation
*Goal: Production-ready, tested, documented*

- [ ] **P8-01** End-to-end test: full BAP lifecycle for single-level department (Playwright or Cypress)
- [ ] **P8-02** End-to-end test: full BAP lifecycle for two-level department
- [ ] **P8-03** Security audit: verify all endpoints reject unauthorized roles (automated test suite)
- [ ] **P8-04** Add rate limiting on auth endpoints (Spring Security + Bucket4j)
- [ ] **P8-05** Add HTTP security headers (X-Frame-Options, CSP, HSTS)
- [ ] **P8-06** Performance test: invoice list endpoint with 10,000 records (< 2s)
- [ ] **P8-07** Add DB indexes: `invoices(status)`, `invoices(department_id)`, `invoices(created_at)`, `audit_logs(created_at)`
- [ ] **P8-08** Write `README.md` with setup instructions, architecture overview, test commands
- [ ] **P8-09** Generate final Swagger/OpenAPI spec export
- [ ] **P8-10** Final `docker-compose up` verification — fresh machine, zero manual steps

**Phase 8 Exit Criteria:** `docker-compose up` on a fresh machine → working app. All E2E tests pass. Swagger UI documents all endpoints.
