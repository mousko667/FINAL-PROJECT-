# TASKS â€” Development Task List

> **How to use this file:**
> - Work top-to-bottom, phase by phase
> - Never start Phase N+1 until all tasks in Phase N are âœ…
> - Mark each task âœ… only when: code written + tests pass + i18n keys added
> - Log blockers in `docs/MEMORY.md`

---

## Phase 0 â€” Project Bootstrap
*Goal: Running skeleton with DB, security, and Docker*

- [x] **P0-01** Initialize Spring Boot project via Spring Initializr with all dependencies from `docs/ARCHITECTURE.md Â§4`
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
- [x] **P0-20** Verify: `docker-compose up` â†’ app starts, `/api/v1/auth/login` returns JWT âœ…

**Phase 0 Exit Criteria:** `docker-compose up` produces a running app, login works, Swagger UI accessible at `/swagger-ui.html`

---

## Phase 1 â€” User & Department Management
*Goal: Admins can manage users and departments*

- [x] **P1-01** Implement `UserController` (CRUD + role assignment) â€” Admin only
- [x] **P1-02** Implement `UserService` with BCrypt password hashing
- [x] **P1-03** Implement `UserRepository` with `findByEmail`, `findByUsername`
- [x] **P1-04** Implement `UserDTO`, `UserCreateRequest`, `UserUpdateRequest`
- [x] **P1-05** Implement `UserMapper` (MapStruct)
- [x] **P1-06** Implement `DepartmentController` (list, get, update approval config) â€” Admin only
- [x] **P1-07** Implement `DepartmentService` + `DepartmentRepository`
- [x] **P1-08** Add i18n keys for user and department management
- [x] **P1-09** Write unit tests: `UserServiceTest` (create, update, assign role, deactivate)
- [x] **P1-10** Write unit tests: `DepartmentServiceTest`
- [x] **P1-11** Write integration tests: `UserControllerTest` (all endpoints, all roles)
- [x] **P1-12** Write integration tests: `DepartmentControllerTest`

**Phase 1 Exit Criteria:** Admin can create users and assign roles via API. All tests pass.

---

## Phase 2 â€” Invoice Core (CRUD + Documents)
*Goal: Accounting assistant can create, edit, and attach documents to invoices*

- [x] **P2-01** Create Flyway migrations: `V4__create_invoices.sql`, `V5__create_invoice_items.sql`, `V6__create_invoice_documents.sql`
- [x] **P2-02** Implement `Invoice`, `InvoiceItem`, `InvoiceDocument` entities
- [x] **P2-03** Implement `InvoiceStatus` enum (all 9 statuses FR/EN)
- [x] **P2-04** Implement `InvoiceRepository` with paginated queries + filter support
- [x] **P2-05** Implement `InvoiceService` (create, update, soft-delete, list, get-by-id)
- [x] **P2-06** Implement `InvoiceValidationService` (all 10 business rules from `docs/WORKFLOW.md Â§8`)
- [x] **P2-07** Implement `ReferenceNumberGenerator` â€” format `FAC-{YYYY}-{NNNNN}` with DB sequence
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

## Phase 3 â€” Workflow Engine (BAP State Machine)
*Goal: Full invoice lifecycle through all approval stages*

- [x] **P3-01** Create Flyway migrations: `V7__create_approval_steps.sql`, `V8__create_invoice_status_history.sql`
- [x] **P3-02** Implement `ApprovalStep` entity
- [x] **P3-03** Implement `InvoiceStatusHistory` entity
- [x] **P3-04** Implement `InvoiceEvent` enum (SUBMIT, ASSIGN_REVIEWER, VALIDATE_N1, VALIDATE_N2, BON_A_PAYER, RECORD_PAYMENT, REJECT, RESUBMIT, ARCHIVE)
- [x] **P3-05** Implement `StateMachineConfig` â€” define all states + transitions from `docs/WORKFLOW.md Â§3`
- [x] **P3-06** Implement department-aware transition guard (1-level vs 2-level based on `Department.requires_n2`)
- [x] **P3-07** Implement `InvoiceStateMachineService` (send event, persist state, write status history)
- [x] **P3-08** Implement `InvoiceStateChangeListener` (writes to `invoice_status_history` on every transition)
- [x] **P3-09** Implement `ApprovalService` (create step, record decision, check completeness, deadline tracking)
- [x] **P3-10** Implement `ApprovalController` (assign-reviewer, validate-n1, validate-n2, bon-a-payer, reject endpoints)
- [x] **P3-11** Implement `InvoiceController` additions: submit, resubmit endpoints
- [x] **P3-12** Implement all transition guards (role check, rejection reason min length, document required)
- [x] **P3-13** Add all workflow-related i18n keys (FR + EN for every state and action)
- [x] **P3-14** Write unit tests: `InvoiceStateMachineServiceTest` (every valid transition + every invalid transition)
- [x] **P3-15** Write unit tests: `ApprovalServiceTest` (assign, validate, reject, deadline check)
- [x] **P3-16** Write integration tests: `ApprovalControllerTest` â€” full lifecycle for SINGLE-level dept
- [x] **P3-17** Write integration tests: `ApprovalControllerTest` â€” full lifecycle for TWO-level dept (INFO)
- [x] **P3-18** Write integration tests: reject + resubmit flow
- [x] **P3-19** Write integration test: wrong-role rejection (N1 trying to do N2 action â†’ 403)

**Phase 3 Exit Criteria:** A full invoice lifecycle (BROUILLON â†’ ARCHIVE) works end-to-end for both 1-level and 2-level departments. All transitions, guards, and role checks verified by tests.

---

## Phase 4 â€” Notifications
*Goal: Internal email + in-app notifications on every workflow event*

- [x] **P4-01** Create Flyway migration `V9__create_notifications.sql`
- [x] **P4-02** Implement `Notification` entity + `NotificationRepository`
- [x] **P4-03** Define domain events: `InvoiceSubmittedEvent`, `InvoiceValidatedEvent`, `InvoiceRejectedEvent`, `BonAPayerEvent`, `ApprovalDeadlineEvent`
- [x] **P4-04** Implement `EmailNotificationListener` (`@Async @EventListener`) â€” sends via `EmailService`
- [x] **P4-05** Implement `EmailService` with Thymeleaf templates (FR + EN)
- [x] **P4-06** Create email templates: `invoice-submitted.html`, `invoice-rejected.html`, `invoice-approved.html`, `deadline-reminder.html`
- [x] **P4-07** Implement `PersistNotificationListener` (`@Async @EventListener`) â€” saves to DB
- [x] **P4-08** Configure `WebSocketConfig` (STOMP over SockJS, user-specific topics)
- [x] **P4-09** Implement `WebSocketNotificationListener` (`@Async @EventListener`)
- [x] **P4-10** Implement `NotificationController` (list, mark-read, mark-all-read)
- [x] **P4-11** Implement `@Scheduled` deadline reminder job (runs daily, checks overdue approval steps)
- [x] **P4-12** Write unit tests: all listener tests with mock `EmailService` and `SimpMessagingTemplate`
- [x] **P4-13** Write integration tests: event publishing verified end-to-end

**Phase 4 Exit Criteria:** Submitting an invoice triggers an in-app notification AND an email to the N1 approvers. Notifications persisted in DB. WebSocket delivers real-time alerts.

---

## Phase 5 â€” Payment & Archiving
*Goal: Record payments and archive completed invoices*

- [x] **P5-01** Create Flyway migration `V10__create_payments.sql`
- [x] **P5-02** Implement `Payment` entity + `PaymentRepository`
- [x] **P5-03** Implement `PaymentService` (record payment, trigger PAYEâ†’ARCHIVE)
- [x] **P5-04** Implement `PaymentController` (record, get, list)
- [x] **P5-05** Write unit tests: `PaymentServiceTest`
- [x] **P5-06** Write integration tests: `PaymentControllerTest`

**Phase 5 Exit Criteria:** A BON_A_PAYER invoice can be paid and automatically archives.

---

## Phase 6 â€” Audit & Reporting
*Goal: Full audit trail and management reports*

- [x] **P6-01** Create Flyway migration `V11__create_audit_logs.sql`
- [x] **P6-02** Implement `AuditLog` entity + `AuditRepository`
- [x] **P6-03** Implement `AuditService` (append-only log writes)
- [x] **P6-04** Implement `AuditController` (paginated log, filter by user/entity/action)
- [x] **P6-05** Implement `ReportService` (KPI queries: avg processing time, volume by status, rejection rate, overdue count, top suppliers)
- [x] **P6-06** Implement Excel export (Apache POI â€” filtered invoice list)
- [x] **P6-07** Implement PDF export â€” individual invoice audit report (iText 7)
- [x] **P6-08** Implement PDF export â€” compliance report for date range (iText 7)
- [x] **P6-09** Implement `ReportController` (kpi, export-xlsx, export-pdf)
- [x] **P6-10** Write unit tests: `ReportServiceTest` (KPI calculations with known dataset)
- [x] **P6-11** Write integration tests: `ReportControllerTest` + `AuditControllerTest`

**Phase 6 Exit Criteria:** DAF/Auditor can view KPI dashboard data and export reports. Audit log is complete and tamper-evident.

---

## Phase 7 â€” Frontend (React)
*Goal: Complete working UI for all roles*

- [x] **P7-01** Bootstrap React project (Vite + TypeScript + TailwindCSS + shadcn/ui)
- [x] **P7-02** Configure Axios client with JWT interceptor + refresh logic
- [x] **P7-03** Configure React Query (`QueryClientProvider`)
- [x] **P7-04** Configure Redux Toolkit (auth slice + notification slice)
- [x] **P7-05** Configure React Router with nested routes + `ProtectedRoute` + `RoleGuard`
- [x] **P7-06** Configure i18n (react-i18next with `fr.json` + `en.json`)
- [x] **P7-07** Configure STOMP/WebSocket client hook (`useWebSocket.ts`)
- [x] **P7-08** Build `LoginPage` with form validation (React Hook Form + Zod)
- [x] **P7-09** Build app shell: sidebar, header, notification bell, language switcher
- [x] **P7-10** Build `InvoiceListPage` (table, filters, pagination, status badges)
- [x] **P7-11** Build `InvoiceCreatePage` (multi-step form: details â†’ line items â†’ documents)
- [x] **P7-12** Build `InvoiceDetailPage` (full view + `InvoiceTimeline` + `InvoiceActionPanel`)
- [x] **P7-13** Build `InvoiceActionPanel` (role-aware: shows validate/reject/bon-a-payer based on user role + invoice state)
- [x] **P7-14** Build `DocumentUploader` (drag-and-drop, MIME preview, progress bar)
- [x] **P7-15** Build `DashboardPage` with role-specific content
- [x] **P7-16** Build KPI cards + Recharts charts (donut, bar, line)
- [x] **P7-17** Build `NotificationDropdown` + unread badge
- [x] **P7-18** Build `ReportsPage` with export panel
- [x] **P7-19** Build Admin pages (user management, department config, audit log)
- [x] **P7-20** Write component tests (React Testing Library): `InvoiceActionPanel`, `InvoiceTimeline`, `RoleGuard`
- [x] **P7-21** Write API hook tests: `useInvoices`, `useAuth`

**Phase 7 Exit Criteria:** All user journeys completable in the browser for all roles. FR/EN language switch works. Role guard prevents unauthorized UI actions.

---

## Phase 8 â€” Integration, Hardening & Documentation
*Goal: Production-ready, tested, documented*

- [x] **P8-01** End-to-end test: full BAP lifecycle for single-level department (Playwright or Cypress)
- [x] **P8-02** End-to-end test: full BAP lifecycle for two-level department
- [x] **P8-03** Security audit: verify all endpoints reject unauthorized roles (automated test suite)
- [x] **P8-04** Add rate limiting on auth endpoints (Spring Security + Bucket4j)
- [x] **P8-05** Add HTTP security headers (X-Frame-Options, CSP, HSTS)
- [x] **P8-06** Performance test: invoice list endpoint with 10,000 records (< 2s)
- [x] **P8-07** Add DB indexes: `invoices(status)`, `invoices(department_id)`, `invoices(created_at)`, `audit_logs(created_at)`
- [x] **P8-08** Write `README.md` with setup instructions, architecture overview, test commands
- [x] **P8-09** Generate final Swagger/OpenAPI spec export
- [x] **P8-10** Final `docker-compose up` verification — fresh machine, zero manual steps

**Phase 8 Exit Criteria:** `docker-compose up` on a fresh machine â†’ working app. All E2E tests pass. Swagger UI documents all endpoints.



---

## Phase 9A — Supplier Domain (Module 8 Foundation)
*Goal: Supplier becomes a first-class entity; invoices link via FK*

- [x] **P9-01** Create `V13__create_suppliers.sql` — suppliers table with
      company name, tax ID, bank details (AES-256 encrypted), contact info,
      status (PENDING_VERIFICATION/ACTIVE/SUSPENDED), onboarding date, soft-delete
- [x] **P9-02** Create `V14__update_invoices_supplier_fk.sql` — add nullable
      `supplier_id` FK to invoices; migrate existing flat text data into new
      suppliers rows; keep flat fields nullable for backward compatibility
- [x] **P9-03** Implement `Supplier` entity + `SupplierStatus` enum
- [x] **P9-04** Implement `SupplierDocument` entity (tax certificates, contracts)
      linked to supplier with MinIO storage
- [x] **P9-05** Implement `SupplierRepository` (search by name, tax ID, status)
- [x] **P9-06** Implement `SupplierService` (create, update, onboard, suspend,
      soft-delete, list, get performance metrics)
- [x] **P9-07** Implement `SupplierController` (full CRUD + onboard/suspend actions)
- [x] **P9-08** Implement `SupplierMapper` (MapStruct)
- [x] **P9-09** Update `InvoiceService` + `InvoiceController` to accept optional
      `supplierId` and populate `supplier_id` FK on invoice; keep flat fields
      populated from the linked Supplier for backward compatibility
- [x] **P9-10** Add supplier i18n keys FR + EN
- [x] **P9-11** Create `SupplierPerformanceTask` to periodically compute average
      processing times and auto-suspend inactive suppliers
- [x] **P9-12** Add basic end-to-end integration test `SupplierIntegrationTest`
      for creating, suspending, and retrieving supplier metrics)

**Phase 9A Exit Criteria:** Admin can create and manage supplier profiles.
New invoices can be linked to a supplier via `supplier_id`.
All tests pass. `./mvnw test` — 0 failures.

---

## Phase 9B — Supplier Authentication & Portal (Modules 1, 2, 3)
*Goal: Suppliers can self-register, log in, submit invoices, track status*

- [ ] **P9-13** Create `V15__add_supplier_user_link.sql` — add nullable
      `supplier_id` FK to `users` table; add `mfa_enabled BOOLEAN DEFAULT FALSE`,
      `mfa_secret VARCHAR(64)`, `mfa_verified BOOLEAN DEFAULT FALSE`,
      `failed_login_attempts INT DEFAULT 0`, `locked_until TIMESTAMPTZ` columns
- [ ] **P9-14** Implement supplier self-registration endpoint
      `POST /api/v1/auth/register/supplier` with email verification token flow
      (token stored in DB, expires in 24h)
- [ ] **P9-15** Implement email verification endpoint
      `GET /api/v1/auth/verify-email?token={token}` — activates supplier account
- [ ] **P9-16** Update `AuthService` to issue JWT with `ROLE_SUPPLIER`
      authority on supplier login; supplier JWT includes `supplierId` claim
- [ ] **P9-17** Update `SecurityConfig` to permit registration + email
      verification endpoints publicly
- [ ] **P9-18** Implement supplier invoice submission
      `POST /api/v1/supplier/invoices` — suppliers upload their own invoice
      (maps to same `Invoice` entity, sets `submitted_by` to the supplier user,
      status starts at `BROUILLON`)
- [ ] **P9-19** Implement supplier invoice status tracking
      `GET /api/v1/supplier/invoices` — ROLE_SUPPLIER sees only their own
- [ ] **P9-20** Implement supplier document upload
      `POST /api/v1/supplier/documents` — tax certificates, contracts
- [ ] **P9-21** Implement supplier profile self-management
      `GET/PUT /api/v1/supplier/profile`
- [ ] **P9-22** Implement supplier dashboard endpoint
      `GET /api/v1/supplier/dashboard` — counts by status, last payment date,
      pending actions
- [ ] **P9-23** Add i18n keys for supplier portal (FR + EN)
- [ ] **P9-24** Write integration tests: supplier registration → email verify →
      login → submit invoice → track status

**Phase 9B Exit Criteria:** Supplier can register, verify email, log in,
submit an invoice, and track its status. ROLE_SUPPLIER cannot access
internal staff endpoints. All tests pass.

---

## Phase 9C — MFA / Two-Factor Authentication (Module 14)
*Goal: TOTP-based MFA mandatory for all finance and admin roles*

- [ ] **P9-25** Add `dev.samstevens.totp:totp:1.7.1` dependency to `pom.xml`
- [ ] **P9-26** Implement `MfaService` — generate TOTP secret, produce QR code
      URL (otpauth URI), verify OTP code against stored secret
- [ ] **P9-27** Implement MFA setup flow:
      `POST /api/v1/auth/mfa/setup` — returns QR code URL (only once);
      `POST /api/v1/auth/mfa/confirm` — verifies first OTP, sets `mfa_verified=true`
- [ ] **P9-28** Update `AuthService` login flow: if user has `mfa_enabled=true`,
      return `{ mfa_required: true, pre_auth_token: "..." }` instead of full JWT;
      add `POST /api/v1/auth/mfa/validate` to accept OTP + pre_auth_token,
      return full JWT on success
- [ ] **P9-29** Implement login attempt tracking: increment `failed_login_attempts`
      on each failed OTP; lock account (`locked_until = NOW() + 15min`)
      after 5 failures; return 423 LOCKED when locked
- [ ] **P9-30** Enforce MFA mandatory for high-privilege roles: on first login
      after role assignment, if MFA not set up, return `mfa_setup_required: true`
      and restrict access to setup endpoints only
- [ ] **P9-31** Implement admin unlock endpoint
      `POST /api/v1/users/{id}/unlock` (ROLE_ADMIN only) — resets
      `failed_login_attempts`, clears `locked_until`
- [ ] **P9-32** Add MFA-related i18n keys FR + EN
- [ ] **P9-33** Write unit tests: `MfaServiceTest`
      (secret generation, QR URL, valid OTP, invalid OTP, expired OTP)
- [ ] **P9-34** Write integration tests: MFA setup → confirm → login with OTP;
      5 failed attempts → account locked → admin unlock

**Phase 9C Exit Criteria:** Finance/admin roles cannot access protected endpoints
without completing MFA. Account lockout works. All tests pass.

---

## Phase 9D — Three-Way Matching (Module 5)
*Goal: Automatic PO + GRN + Invoice matching with discrepancy flagging*

- [ ] **P9-35** Create `V16__create_purchase_orders.sql` — purchase orders table
      (PO number, supplier_id FK, total amount, status, created_by, dates)
      + `purchase_order_items` (line items with quantity, unit price)
- [ ] **P9-36** Create `V17__create_goods_receipt_notes.sql` — GRN table
      (GRN number, po_id FK, received_by, receipt_date)
      + `goods_receipt_items` (item_id FK to PO item, received_quantity)
- [ ] **P9-37** Create `V18__create_three_way_matching.sql` — matching results
      table (invoice_id FK, po_id FK, grn_id FK, status MATCHED/PARTIAL/MISMATCH,
      discrepancy_notes, overridden_by FK, override_reason, created_at)
      + `matching_config` table (tolerance_percentage, tolerance_amount,
      require_grn BOOLEAN, updated_by, updated_at)
- [ ] **P9-38** Implement `PurchaseOrder`, `PurchaseOrderItem`,
      `GoodsReceiptNote`, `GoodsReceiptItem`, `ThreeWayMatchingResult` entities
- [ ] **P9-39** Implement `PurchaseOrderService` (create, update, link to supplier,
      list by supplier, get with items)
- [ ] **P9-40** Implement `ThreeWayMatchingService`:
      compare invoice line items vs PO quantities vs GRN received quantities;
      apply tolerance thresholds from `matching_config`;
      flag line-level discrepancies; return `MATCHED`, `PARTIAL`, or `MISMATCH`
- [ ] **P9-41** Integrate matching into `InvoiceStateMachineServiceImpl`:
      on `SUBMIT` event, if invoice has `purchaseOrderId`, auto-trigger matching;
      if result is `MISMATCH`, block transition to `SOUMIS` and throw
      `WorkflowException` with discrepancy detail unless override exists
- [ ] **P9-42** Implement DAF/Admin override endpoint:
      `POST /api/v1/invoices/{id}/matching/override` — records override reason,
      allows invoice to proceed despite MISMATCH
- [ ] **P9-43** Implement `PurchaseOrderController` (CRUD, ROLE_ADMIN +
      ROLE_ASSISTANT_COMPTABLE)
- [ ] **P9-44** Implement `MatchingConfigController` (read/update tolerance,
      ROLE_ADMIN only)
- [ ] **P9-45** Add matching i18n keys FR + EN
- [ ] **P9-46** Write unit tests: `ThreeWayMatchingServiceTest`
      (perfect match, within tolerance, outside tolerance, missing GRN,
      override applied)
- [ ] **P9-47** Write integration tests: PO creation → GRN creation →
      invoice submission → matching auto-triggered → MISMATCH blocks workflow →
      override allows progression

**Phase 9D Exit Criteria:** Invoice with PO reference auto-triggers matching
on submit. MISMATCH blocks workflow without override. All tests pass.

---

## Phase 9E — Payment Tracking Enhancements (Module 7)
*Goal: Aging analysis, remittance advice, cash flow, payment alerts*

- [ ] **P9-48** Create `V19__create_remittance_advice.sql` — remittance advice
      table (payment_id FK UNIQUE, pdf_object_key, generated_at, generated_by FK)
- [ ] **P9-49** Implement aging analysis query in `ReportService`:
      bucket overdue invoices into 0–30, 31–60, 61–90, 90+ days;
      expose via `GET /api/v1/reports/aging`
- [ ] **P9-50** Implement `RemittanceAdviceService` — generate PDF per payment
      using iText (supplier name, invoice ref, amount, payment date, method,
      reference number); store in MinIO; record in `remittance_advice` table
- [ ] **P9-51** Auto-generate remittance advice when payment is recorded
      (triggered in `PaymentServiceImpl.recordPayment`)
- [ ] **P9-52** Implement remittance download endpoint:
      `GET /api/v1/payments/{id}/remittance` — returns pre-signed MinIO URL
- [ ] **P9-53** Implement cash flow projection endpoint:
      `GET /api/v1/reports/cash-flow?days=30` — sum of pending invoices
      due within N days, grouped by week
- [ ] **P9-54** Implement supplier payment history endpoint:
      `GET /api/v1/reports/supplier/{supplierId}/payments` (DAF, AUDITEUR, ADMIN)
- [ ] **P9-55** Extend `DeadlineReminderJob` to also fire payment due date
      alerts (7 days before `due_date`) to ASSISTANT_COMPTABLE
- [ ] **P9-56** Add payment enhancement i18n keys FR + EN
- [ ] **P9-57** Write unit tests: aging bucket calculation, remittance PDF
      generation (verify PDF not null, contains expected data)
- [ ] **P9-58** Write integration tests: record payment → remittance auto-generated
      → download URL returned; aging report returns correct buckets

**Phase 9E Exit Criteria:** Remittance advice auto-generated on payment.
Aging report groups invoices correctly. Cash flow projection works.
All tests pass.

---

## Phase 9F — Webhooks / ERP Integration (Module 12)
*Goal: External systems receive real-time events via signed webhook calls*

- [ ] **P9-59** Create `V20__create_webhooks.sql` — webhooks table
      (id, name, url, secret_hash VARCHAR(64), events TEXT[] or VARCHAR(500),
      is_active, created_by FK, created_at, updated_at)
      + `webhook_deliveries` table (webhook_id FK, event_type, payload TEXT,
      response_status INT, attempt_count, last_attempted_at,
      success BOOLEAN, created_at) — append-only
- [ ] **P9-60** Implement `Webhook`, `WebhookDelivery` entities
- [ ] **P9-61** Implement `WebhookService`:
      register/update/delete (logical) webhooks;
      build signed payload (HMAC-SHA256 with stored secret);
      deliver via `RestTemplate` with 5s timeout;
      retry 3× with backoff 5s/25s/125s;
      log every attempt to `webhook_deliveries`
- [ ] **P9-62** Publish webhook events by adding `WebhookEventPublisher`
      as an additional `@Async @EventListener` alongside existing listeners:
      fire on `InvoiceSubmittedEvent`, `InvoiceValidatedEvent`,
      `InvoiceRejectedEvent`, `BonAPayerEvent`
- [ ] **P9-63** Implement `WebhookController`:
      `GET/POST/DELETE /api/v1/integrations/webhooks` (ROLE_ADMIN only);
      `GET /api/v1/integrations/webhooks/{id}/deliveries` (delivery log)
- [ ] **P9-64** Implement integration health endpoint:
      `GET /api/v1/integrations/status` — lists all active webhooks
      with last delivery status
- [ ] **P9-65** Add webhook i18n keys FR + EN
- [ ] **P9-66** Write unit tests: `WebhookServiceTest`
      (payload signing, delivery success, delivery failure + retry,
      max retries exceeded → logged as failed)
- [ ] **P9-67** Write integration tests: register webhook → trigger invoice
      event → verify delivery logged; failed endpoint → verify retry count = 3

**Phase 9F Exit Criteria:** Admin can register webhooks. Invoice state changes
fire signed HTTP POSTs. Failures retry and are logged. All tests pass.

---

## Phase 9G — Dashboard & Reporting Enhancements (Module 2, 11)
*Goal: Bottleneck detection, budget comparison, supplier performance analytics*

- [ ] **P9-68** Add approval bottleneck detection to `ReportService`:
      `GET /api/v1/reports/bottlenecks` — average days spent per approval step
      type (N1, N2, DAF) per department; flag steps exceeding SLA (3 business days)
- [ ] **P9-69** Add supplier performance report:
      `GET /api/v1/reports/supplier/{supplierId}/performance` — invoice accuracy
      rate (% matched on first submission), rejection rate, average payment time
- [ ] **P9-70** Extend `GET /api/v1/reports/kpis` to include:
      `overdueByBucket` (0-30/31-60/61-90/90+ count + value),
      `averageN1ApprovalDays`, `averageN2ApprovalDays`, `averageDafApprovalDays`,
      `webhookDeliverySuccessRate` (last 7 days)
- [ ] **P9-71** Add supplier dashboard enhancements (requires Phase 9B):
      `GET /api/v1/supplier/dashboard` extended with matching status breakdown
      and next expected payment date
- [ ] **P9-72** Add i18n keys for new report labels FR + EN
- [ ] **P9-73** Write unit tests: `ReportServiceTest` extensions
      (bottleneck calculation, supplier performance metrics, extended KPIs)
- [ ] **P9-74** Write integration tests: `ReportControllerTest` extensions
      (bottleneck endpoint, supplier performance endpoint, extended KPI shape)

**Phase 9G Exit Criteria:** KPI dashboard includes bottleneck and aging data.
Supplier performance reports return correct metrics. All tests pass.
`./mvnw test` — 0 failures. `git push origin main`.