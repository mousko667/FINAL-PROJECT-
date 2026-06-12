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

- [x] **P9-13** Create `V15__add_supplier_user_link.sql` — add nullable
      `supplier_id` FK to `users` table; add `mfa_enabled BOOLEAN DEFAULT FALSE`,
      `mfa_secret VARCHAR(64)`, `mfa_verified BOOLEAN DEFAULT FALSE`,
      `failed_login_attempts INT DEFAULT 0`, `locked_until TIMESTAMPTZ` columns
- [x] **P9-14** Implement supplier self-registration endpoint
      `POST /api/v1/auth/register/supplier` with email verification token flow
      (token stored in DB, expires in 24h)
- [x] **P9-15** Implement email verification endpoint
      `GET /api/v1/auth/verify-email?token={token}` — activates supplier account
- [x] **P9-16** Update `AuthService` to issue JWT with `ROLE_SUPPLIER`
      authority on supplier login; supplier JWT includes `supplierId` claim
- [x] **P9-17** Update `SecurityConfig` to permit registration + email
      verification endpoints publicly
- [x] **P9-18** Implement supplier invoice submission
      `POST /api/v1/supplier/invoices` — suppliers upload their own invoice
      (maps to same `Invoice` entity, sets `submitted_by` to the supplier user,
      status starts at `BROUILLON`)
- [x] **P9-19** Implement supplier invoice status tracking
      `GET /api/v1/supplier/invoices` — ROLE_SUPPLIER sees only their own
- [x] **P9-20** Implement supplier document upload ✅
      `POST /api/v1/supplier/documents` — tax certificates, contracts
- [x] **P9-21** Implement supplier profile self-management ✅
      `GET/PUT /api/v1/supplier/profile`
- [x] **P9-22** Implement supplier dashboard endpoint ✅
      `GET /api/v1/supplier/dashboard` — counts by status, last payment date,
      pending actions
- [x] **P9-23** Add i18n keys for supplier portal (FR + EN) ✅
- [x] **P9-24** Write integration tests: supplier registration → email verify →
      login → submit invoice → track status ✅

**Phase 9B Exit Criteria:** Supplier can register, verify email, log in,
submit an invoice, and track its status. ROLE_SUPPLIER cannot access
internal staff endpoints. All tests pass.

---

## Phase 9C — MFA / Two-Factor Authentication (Module 14)
*Goal: TOTP-based MFA mandatory for all finance and admin roles*

- [x] **P9-25** Add `dev.samstevens.totp:totp:1.7.1` dependency to `pom.xml`
- [x] **P9-26** Implement `MfaService` — generate TOTP secret, produce QR code
      URL (otpauth URI), verify OTP code against stored secret
- [x] **P9-27** Implement MFA setup flow:
      `POST /api/v1/auth/mfa/setup` — returns QR code URL (only once);
      `POST /api/v1/auth/mfa/confirm` — verifies first OTP, sets `mfa_verified=true`
- [x] **P9-28** Update `AuthService` login flow: if user has `mfa_enabled=true`,
      return `{ mfa_required: true, pre_auth_token: "..." }` instead of full JWT;
      add `POST /api/v1/auth/mfa/validate` to accept OTP + pre_auth_token,
      return full JWT on success
- [x] **P9-29** Implement login attempt tracking: increment `failed_login_attempts`
      on each failed OTP; lock account (`locked_until = NOW() + 15min`)
      after 5 failures; return 423 LOCKED when locked
- [x] **P9-30** Enforce MFA mandatory for ALL non-supplier roles: on first login
      after role assignment, if MFA not set up, return `mfa_setup_required: true`
      and restrict access to setup endpoints only.
      Roles requiring MFA: `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF`, `ROLE_ADMIN`,
      `ROLE_VALIDATEUR_N1_*`, `ROLE_VALIDATEUR_N2_*`.
      `ROLE_SUPPLIER` is the ONLY role exempt from MFA.
- [x] **P9-31** Implement admin unlock endpoint
      `POST /api/v1/users/{id}/unlock` (ROLE_ADMIN only) — resets
      `failed_login_attempts`, clears `locked_until`
- [x] **P9-32** Add MFA-related i18n keys FR + EN
- [x] **P9-33** Write unit tests: `MfaServiceTest`
      (secret generation, QR URL, valid OTP, invalid OTP, expired OTP)
- [x] **P9-34** Write integration tests: MFA setup → confirm → login with OTP;
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

- [x] **P9-48** Create `V19__create_remittance_advice.sql` — remittance advice
      table (payment_id FK UNIQUE, pdf_object_key, generated_at, generated_by FK)
- [x] **P9-49** Implement aging analysis query in `ReportService`:
      bucket overdue invoices into 0–30, 31–60, 61–90, 90+ days;
      expose via `GET /api/v1/reports/aging`
- [x] **P9-50** Implement `RemittanceAdviceService` — generate PDF per payment
      using iText (supplier name, invoice ref, amount, payment date, method,
      reference number); store in MinIO; record in `remittance_advice` table
- [x] **P9-51** Auto-generate remittance advice when payment is recorded
      (triggered in `PaymentServiceImpl.recordPayment`)
- [x] **P9-52** Implement remittance download endpoint:
      `GET /api/v1/payments/{id}/remittance` — returns pre-signed MinIO URL
- [x] **P9-53** Implement cash flow projection endpoint:
      `GET /api/v1/reports/cash-flow?days=30` — sum of pending invoices
      due within N days, grouped by week
- [x] **P9-54** Implement supplier payment history endpoint:
      `GET /api/v1/reports/supplier/{supplierId}/payments` (DAF, AUDITEUR, ADMIN)
- [x] **P9-55** Extend `DeadlineReminderJob` to also fire payment due date
      alerts (7 days before `due_date`) to ASSISTANT_COMPTABLE
- [x] **P9-56** Add payment enhancement i18n keys FR + EN
- [x] **P9-57** Write unit tests: aging bucket calculation, remittance PDF
      generation (verify PDF not null, contains expected data)
- [x] **P9-58** Write integration tests: record payment → remittance auto-generated
      → download URL returned; aging report returns correct buckets

**Phase 9E Exit Criteria:** Remittance advice auto-generated on payment.
Aging report groups invoices correctly. Cash flow projection works.
All tests pass.

---

## Phase 9F — Webhooks / ERP Integration (Module 12)
*Goal: External systems receive real-time events via signed webhook calls*

- [x] **P9-59** Create `V20__create_webhooks.sql` — webhooks table
      (id, name, url, secret_hash VARCHAR(64), events TEXT[] or VARCHAR(500),
      is_active, created_by FK, created_at, updated_at)
      + `webhook_deliveries` table (webhook_id FK, event_type, payload TEXT,
      response_status INT, attempt_count, last_attempted_at,
      success BOOLEAN, created_at) — append-only
- [x] **P9-60** Implement `Webhook`, `WebhookDelivery` entities
- [x] **P9-61** Implement `WebhookService`:
      register/update/delete (logical) webhooks;
      build signed payload (HMAC-SHA256 with stored secret);
      deliver via `RestTemplate` with 5s timeout;
      retry 3× with backoff 5s/25s/125s;
      log every attempt to `webhook_deliveries`
- [x] **P9-62** Publish webhook events by adding `WebhookEventPublisher`
      as an additional `@Async @EventListener` alongside existing listeners:
      fire on `InvoiceSubmittedEvent`, `InvoiceValidatedEvent`,
      `InvoiceRejectedEvent`, `BonAPayerEvent`
- [x] **P9-63** Implement `WebhookController`:
      `GET/POST/DELETE /api/v1/integrations/webhooks` (ROLE_ADMIN only);
      `GET /api/v1/integrations/webhooks/{id}/deliveries` (delivery log)
- [x] **P9-64** Implement integration health endpoint:
      `GET /api/v1/integrations/status` — lists all active webhooks
      with last delivery status
- [x] **P9-65** Add webhook i18n keys FR + EN
- [x] **P9-66** Write unit tests: `WebhookServiceTest`
      (payload signing, delivery success, delivery failure + retry,
      max retries exceeded → logged as failed)
- [x] **P9-67** Write integration tests: register webhook → trigger invoice
      event → verify delivery logged; failed endpoint → verify retry count = 3

**Phase 9F Exit Criteria:** Admin can register webhooks. Invoice state changes
fire signed HTTP POSTs. Failures retry and are logged. All tests pass.

---

## Phase 9G — Dashboard & Reporting Enhancements (Module 2, 11)
*Goal: Bottleneck detection, budget comparison, supplier performance analytics*

- [x] **P9-68** Add approval bottleneck detection to `ReportService`:
      `GET /api/v1/reports/bottlenecks` — average days spent per approval step
      type (N1, N2, DAF) per department; flag steps exceeding SLA (3 business days)
- [x] **P9-69** Add supplier performance report:
      `GET /api/v1/reports/supplier/{supplierId}/performance` — invoice accuracy
      rate (% matched on first submission), rejection rate, average payment time
- [x] **P9-70** Extend `GET /api/v1/reports/kpis` to include:
      `overdueByBucket` (0-30/31-60/61-90/90+ count + value),
      `averageN1ApprovalDays`, `averageN2ApprovalDays`, `averageDafApprovalDays`,
      `webhookDeliverySuccessRate` (last 7 days)
- [x] **P9-71** Add supplier dashboard enhancements (requires Phase 9B):
      `GET /api/v1/supplier/dashboard` extended with matching status breakdown
      and next expected payment date
- [x] **P9-72** Add i18n keys for new report labels FR + EN
- [x] **P9-73** Write unit tests: `ReportServiceTest` extensions
      (bottleneck calculation, supplier performance metrics, extended KPIs)
- [x] **P9-74** Write integration tests: `ReportControllerTest` extensions
      (bottleneck endpoint, supplier performance endpoint, extended KPI shape)

**Phase 9G Exit Criteria:** KPI dashboard includes bottleneck and aging data.
Supplier performance reports return correct metrics. All tests pass.
`./mvnw test` — 0 failures. `git push origin main`.

---

## Phase 10 — Critical Gap Remediation
*Goal: Close all 5 known implementation gaps from `OCT_System_Briefing.md §4.3` before final submission*

### P10-A — OCR Implementation 🔴 Critical

- [x] **P10-01** Add `net.sourceforge.tess4j:tess4j` dependency to `pom.xml`
      Also added `org.apache.pdfbox:pdfbox:3.0.3` for PDF text-layer extraction.
- [x] **P10-02** Implement `OcrService` (`domain/ocr/service/OcrService.java`):
      - Tika detects MIME type for routing
      - PDFBox extracts text layer from digital PDFs (fallback to OCR if < 50 chars)
      - Tess4J runs OCR on scanned PDFs (each page rendered at 300 DPI via PDFRenderer)
        and on image files (JPEG, PNG, TIFF)
      - `parseFields()` extracts invoice number, date, total amount, supplier ID,
        PO reference, and line items via regex patterns supporting FR+EN formats
- [x] **P10-03** Add `POST /api/v1/ocr/extract` endpoint (`domain/ocr/controller/OcrController.java`)
      — SUPPLIER, ASSISTANT_COMPTABLE, ADMIN — returns `OcrExtractionResult` for confirmation
- [x] **P10-04** OCR config added to `application.yaml`: `ocr.tessdata-path` and `ocr.language`
      — defaults `tessdata` / `fra+eng`; override via `OCR_TESSDATA_PATH` / `OCR_LANGUAGE` env vars
- [x] **P10-05** i18n keys: OCR labels use standard ApiResponse message keys; extend
      `messages_fr.properties` / `messages_en.properties` with `ocr.*` keys as needed
- [x] **P10-06** `OcrServiceTest` written with 8 unit tests covering: invoice number extraction
      (EN + FR labels), date extraction, amount parsing, PO reference, supplier ID (NIF),
      empty text, digitalPdf flag, and rawText pass-through

**P10-A Exit Criteria:** Supplier can upload an invoice file and see OCR-extracted fields pre-populated for confirmation before submission.

---

### P10-B — JWT RS256 Migration 🟠 High

- [x] **P10-07** RSA-2048 key pair loaded from environment variables `JWT_PRIVATE_KEY` (PKCS#8 Base64)
      and `JWT_PUBLIC_KEY` (X.509 Base64). Test key pair embedded in test profile. Production
      keys must be generated with openssl and stored in a secrets manager — never committed.
- [x] **P10-08** `JwtService` fully rewritten for RS256:
      - `getPrivateKey()` decodes Base64 → PKCS8EncodedKeySpec → RSA PrivateKey
      - `getPublicKey()` decodes Base64 → X509EncodedKeySpec → RSA PublicKey
      - `buildToken()` uses `.signWith(privateKey, SignatureAlgorithm.RS256)`
      - `extractAllClaims()` uses `.verifyWith(publicKey)`
      - HS256 `getSignInKey()` and `jwt.secret` property removed entirely
- [x] **P10-09** `application.yaml` updated: `jwt.secret` replaced by `jwt.private-key` / `jwt.public-key`;
      test profile has embedded test key pair; `ProdSecretConfigValidator` validates both keys
- [x] **P10-10** `JwtServiceTest` — existing tests exercise token generation and validation;
      RS256 sign+verify roundtrip covered by the integration test suite (MfaIntegrationTest)
- [x] **P10-11** Login → JWT → protected endpoint flow covered by `ApprovalControllerTest`,
      `InvoiceControllerTest`, and `MfaIntegrationTest`

**P10-B Exit Criteria:** All JWTs signed with RS256. HS256 shared secret removed. Tests pass.

---

### P10-C — GitHub Actions CI Pipeline 🟡 Medium

- [x] **P10-12** `.github/workflows/ci.yml` created with 3 parallel jobs:
      - **backend**: Java 21 + PostgreSQL 18 + MinIO service containers; `mvn compile` then
        `mvn test`; JaCoCo report uploaded as artifact
      - **frontend**: Node 20; `npm ci` → `npm test -- --run` (Vitest) → `npm run build`
      - **docker**: depends on backend+frontend; `docker compose build --no-cache`
      - Triggers: push to main, pull_request to main

**P10-C Exit Criteria:** `.github/workflows/ci.yml` exists and passes on a clean push to main.

---

### P10-D — TLS 1.3 Spring Boot Configuration 🟡 Medium

- [x] **P10-13** TLS 1.3 config added to the `prod` profile section of `application.yaml`
      under `server.ssl` (enabled, protocol TLSv1.3, PKCS12 keystore via env vars).
      `ProdSecretConfigValidator` now validates `server.ssl.key-store` and
      `server.ssl.key-store-password` on startup.
- [x] **P10-14** `SSL_KEYSTORE_PATH` and `SSL_KEYSTORE_PASSWORD` documented in `application.yaml`
      comments with keytool command example for generating a certificate.

**P10-D Exit Criteria:** `application-prod.yml` enforces TLS 1.3 at the application layer.

---

### P10-E — OWASP ZAP Security Scan 🟡 Medium

- [x] **P10-15** `.github/workflows/security-scan.yml` created as a separate workflow:
      - Triggers after CI passes on main, or manually via workflow_dispatch
      - Starts application under test profile (TLS disabled for scan)
      - Runs OWASP ZAP baseline scan via `zaproxy/action-baseline@v0.12.0`
      - Fails job if any HIGH or CRITICAL alerts found (`fail_action: true`)
      - Uploads HTML + JSON report as artifact
- [x] **P10-16** `.github/zap-rules.tsv` documents accepted-risk informational alerts
      (CSP/HSTS/X-Frame-Options — handled by HttpSecurityHeadersFilter in code)

**P10-E Exit Criteria:** ZAP scan runs automatically on CI. No HIGH/CRITICAL findings unaddressed. ✅

---

**Phase 10 Exit Criteria:** All 5 gaps from `OCT_System_Briefing.md §4.3` resolved.
OCR implemented. JWT uses RS256. CI pipeline runs. TLS 1.3 configured. OWASP ZAP scan integrated.
`./mvnw test` — 0 failures. Final system is production-ready and compliant with all project requirements.

---

## Phase 11 — Audit Correction Cycle
*Goal: Resolve all 35 actionable findings from `docs/audit/ISSUES.md` (2026-06-11/12 audit cycle).
Sequencing rationale and decisions for every sub-phase are in `docs/audit/PLAN-CORRECTIONS.md`.*

### P11-A — Audit Log Dead Code 🔴 Critical

- [x] **P11-01** Fix `AuditController`'s `/audit-logs/system` and `/audit-logs/financial`
      endpoints (REQ-17): extend `AuditLoggingFilter.classifyAction()` so it actually
      produces entries matching `SYSTEM_ACTIONS`/`FINANCIAL_ACTIONS`, so both endpoints
      return non-empty results for realistic traffic. Add a test asserting non-empty results.
      — Done 2026-06-12: see PROB-021 in `docs/KNOWN_ISSUES_REGISTRY.md`.

**P11-A Exit Criteria:** `/audit-logs/system` and `/audit-logs/financial` return real data;
`FinancialAuditPage.tsx` displays entries.

---

### P11-B — Security Fixes 🟠 High

- [x] **P11-02** Fix `SupplierPortalController` profile endpoint (P2-02): map `Supplier` →
      `SupplierResponse` instead of returning the raw entity, so `bankDetails` is never
      serialized. Add integration test asserting `bankDetails`/`bank_details` absent from JSON.
      — Done 2026-06-12: see PROB-022 in `docs/KNOWN_ISSUES_REGISTRY.md`.
- [x] **P11-03** Fix `AuditLoggingFilter.resolveUserId()` (REQ-18): extract the authenticated
      user's ID from `SecurityContext` instead of hardcoded `null`. Add test asserting new
      audit log entries from authenticated requests have non-null `user_id`.
      — Done 2026-06-12: see PROB-023 in `docs/KNOWN_ISSUES_REGISTRY.md`. Fixing this surfaced a
      regression (`audit_logs.user_id` FK now populated, breaking `ApprovalControllerTest.cleanDb()`'s
      `userRepository.deleteAll()`) — resolved via `@OnDelete(action = OnDeleteAction.SET_NULL)` on
      `AuditLog.user` + `V42__audit_logs_user_fk_on_delete_set_null.sql`; see PROB-024.

**P11-B Exit Criteria:** No JPA entity with `bankDetails` ever leaves the API; audit logs
record the acting user for authenticated requests. — MET 2026-06-12 (P11-02, P11-03). Full
`mvnw test` run: 258 tests, 25 failures + 2 errors = 27, identical to the post-P11-01 set, all
pre-existing per `docs/audit/BASELINE.md`.

---

### P11-C — Performance Fixes 🟠 High

- [x] **P11-04** Paginate `GET /api/v1/purchase-orders` without `supplierId` (P3-01): use
      `Pageable`/`Page<T>`, return `PagedResponse<PurchaseOrderResponse>`.
      Done 2026-06-12 — see [PROB-025](KNOWN_ISSUES_REGISTRY.md). New
      `PurchaseOrderRepository.findAllActive(Pageable)`, `PurchaseOrderService.listAll(Pageable)`,
      `PurchaseOrderController.listPurchaseOrders` now returns
      `ApiResponse<PagedResponse<PurchaseOrderDTO>>` for both the unfiltered and `supplierId`
      branches. New `PurchaseOrderControllerTest` (3 tests, all pass). Full suite: 261 tests,
      25 failures + 2 errors = 27, identical to the post-P11-03/PROB-024 baseline set.
- [x] **P11-05** Add Flyway migration `V43__add_invoices_supplier_id_index.sql` (P3-02):
      `CREATE INDEX idx_invoices_supplier_id ON invoices(supplier_id)`. (Renumbered from V42 to
      V43 — V42 was used by PROB-024's `audit_logs.user_id` FK fix during P11-03.)
      Done 2026-06-12 — see [PROB-026](KNOWN_ISSUES_REGISTRY.md). Not exercised by `mvnw test`
      (test profile uses `ddl-auto: create-drop`, Flyway disabled); applies to production/staging.
- [x] **P11-06** Fix `WebhookService` delivery timeouts/retry (P3-04): wire
      `deliveryTimeoutSeconds` into a `RestTemplate` `ClientHttpRequestFactory` with
      connect/read timeouts; replace blocking `Thread.sleep` retry with a non-blocking
      scheduled retry, preserving the 5s/25s/125s backoff contract from CLAUDE.md §9.
      Done 2026-06-12 — see [PROB-027](KNOWN_ISSUES_REGISTRY.md). `WebConfig` now provides a
      `ClientHttpRequestFactory` (connect/read timeout = `webhook.delivery.timeout.seconds`)
      used by `RestTemplate`. `WebhookService` injects `TaskScheduler` and schedules retries via
      `taskScheduler.schedule(..., Instant)` instead of `Thread.sleep`. 2 new tests in
      `WebhookServiceTest` (10 total, all pass).

**P11-C Exit Criteria:** Purchase orders list is paginated; `invoices.supplier_id` is
indexed; webhook delivery no longer blocks a thread/DB connection for up to 755s. MET
2026-06-12. Full `mvnw test` run: 263 tests, 25 failures + 2 errors = 27, identical failure/error
set to the post-P11-03/PROB-024 baseline (verified via diff of sorted test names — zero new
regressions across P11-04/05/06).

---

### P11-D — Controller → Service Layer Refactor 🟠 High

- [ ] **P11-07** Refactor `AdminSessionController` (P1-05): create
      `AdminSessionService`, move repository access out of the controller.
- [ ] **P11-08** Refactor `IntegrationStatusController` (P1-05): create
      `IntegrationStatusService`, move repository access out of the controller.
- [ ] **P11-09** Refactor `WebhookController` (P1-05): move direct repository access
      into the existing `WebhookService`.
- [ ] **P11-10** Refactor `DelegationController` (P1-05): move direct repository access
      into the existing `DelegationService`.
- [ ] **P11-11** Refactor `InvoiceDocumentController` (P1-05): move direct repository
      access into the existing document service.

**P11-D Exit Criteria:** Zero controllers inject a `*Repository` directly
(`grep -rn "Repository" src/main/java/**/controller/*.java` shows only DTO/service types).

---

### P11-E — Docker/Infra Cleanup 🟠 High

- [ ] **P11-12** Remove `docker-compose.yml`'s orphaned `postgres` service (lines 13-33)
      and `postgres_data` volume (P5-01, Option B — confirmed 2026-06-12).
- [ ] **P11-13** Fix `MINIO_SECRET_KEY` default mismatch (P5-02): change
      `docker-compose.yml:66`'s `${MINIO_SECRET_KEY:-dany}` to `${MINIO_SECRET_KEY:-dany1234}`.
- [ ] **P11-14** Add `docs/ARCHITECTURE.md §4.3` prerequisite documentation: host-native
      PostgreSQL 18 (port 5433, db `oct_invoice`) must be running before `docker-compose up`.

**P11-E Exit Criteria:** `docker compose config` shows no `postgres` service; a fresh clone
with `.env` unset for `MINIO_SECRET_KEY` still has `minio_init` succeed; ARCHITECTURE.md
documents the host-Postgres prerequisite.

---

### P11-F — IAM / Permission Gaps 🟠 High

- [ ] **P11-15** Add data-sensitivity classification to financial records (REQ-23 item 1):
      new enum + column via Flyway migration; surface in relevant list/detail views.
- [ ] **P11-16** Add bulk user import/export (CSV) (REQ-23 item 2): new
      `UserController` endpoint(s) + `AdminUsersPage.tsx` UI.
- [ ] **P11-17** Add self-service access-request workflow (REQ-23 item 3): new entity +
      approval-lite flow for users requesting role/department access changes.
- [ ] **P11-18** Add visual permission-matrix editor (REQ-23 item 4): frontend grid over
      the existing `PUT /{id}/roles` endpoint.

**P11-F Exit Criteria:** All 4 sub-items implemented, tested, and added to
`docs/REQUIREMENTS-MATRIX.md` as newly-implemented.

---

### P11-G — Documentation Corrections 🟡 Medium

- [ ] **P11-19** Fix package names in `docs/ARCHITECTURE.md` §2/§10 (P1-01):
      `matching`→`purchasing`, `integration`→`webhook`, `reporting`→`report`; add 6
      missing packages.
- [ ] **P11-20** Rewrite `docs/ARCHITECTURE.md` §4.1 "Known Implementation Gaps" table (P1-02):
      all 8 entries are stale/resolved.
- [ ] **P11-21** Redraw `docs/ARCHITECTURE.md` §5 security filter chain diagram (P1-03):
      add 3 missing filters, correct relative order, fix CORS placement.
- [ ] **P11-22** Add "Inter-domain Dependencies" subsection to `docs/ARCHITECTURE.md` §2
      (P1-06): document the `invoice`↔`purchasing` bidirectional dependency.
- [ ] **P11-23** Replace `ApprovalController.getApprovalSteps`'s `List<Map<String,Object>>`
      return type (P1-07) with a typed `ApprovalStepResponse` DTO.
- [ ] **P11-24** Document the Flyway V36-V38 gap (P3-03): add a note to
      `docs/ARCHITECTURE.md` migration history explaining V35→V39 is intentional.

**P11-G Exit Criteria:** `docs/ARCHITECTURE.md` accurately reflects the current codebase
for §2, §4.1, §5, §10, and migration history; `ApprovalController` returns a typed DTO.

---

### P11-H — i18n Sweep 🟡 Medium

- [ ] **P11-25** Add missing `supplier.register.*` keys (13) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-26** Add missing `supplier.verify.*` keys (5) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-27** Add missing `supplier.tracking.*` keys (8) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-28** Add missing `supplier.portal.*` keys (3) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-29** Add missing `mfa.*` keys (16) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-30** Add missing `payments.*` keys (7) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-31** Add missing `archive.*` keys (6) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-32** Add missing `auth.*` keys incl. password-strength indicator (6) to
      `en.json`/`fr.json` (P4-01).
- [ ] **P11-33** Add missing `grn.*` keys (6) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-34** Add missing `invoice.*` keys (6) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-35** Add missing `dashboard.*` keys (4) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-36** Add missing `admin.*` keys (3) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-37** Add missing `nav.*`, `register.*`, `notifications.*`, `profile.*` keys
      (7 total) to `en.json`/`fr.json` (P4-01).
- [ ] **P11-38** Add full `t()` coverage to `ForgotPasswordPage.tsx`/`ResetPasswordPage.tsx`
      (REQ-01) — currently zero `t()` calls, requires NEW keys in both locale files.
- [ ] **P11-39** Add `alt={t('mfa.qrCodeAlt', ...)}` to the MFA QR `<img>` (P4-04) — new
      95th i18n key in `en.json`/`fr.json`.

**P11-H Exit Criteria:** Node.js diff script (per `PHASE4-FRONTEND.md` methodology) shows
0 `t()` calls with keys missing from either locale file; both locale files remain in
perfect key-parity.

---

### P11-I — Frontend Correctness Fixes 🟡 Medium

- [ ] **P11-40** Wire `SecuritySettingsPage.tsx`'s security-policy form to a real backend
      (REQ-02): new `SecurityPolicyController`/`SecurityPolicy` entity+table, actually
      enforced by `MfaSetupEnforcementFilter`/`RateLimitingFilter`/password validation.
      MFA-required toggle and min-password-length wire to enforcement points; session-timeout
      and max-login-attempts read/write the existing `active_sessions` TTL and
      account-lock-after-5 settings instead of local `useState`. Remove the "simulation only"
      banner text.
- [ ] **P11-41** Fix `LoginPage.tsx` to branch on HTTP 423 (`account.locked`) (REQ-03):
      show a distinct, translated "account locked, contact admin" message instead of the
      generic invalid-credentials text.
- [ ] **P11-42** Wire validator/manager dashboard KPI tiles "Traitées ce mois" and
      "Approuvées" (REQ-04) to `reportService.getKpis`, scoped to the validator's role,
      replacing the `—` placeholders.
- [ ] **P11-43** Add the missing `onClick` handler to `ArchivePage.tsx:150-152`'s
      "Download PDF" button (REQ-15), reusing the download logic from
      `InvoiceDetailPage.tsx`.

**P11-I Exit Criteria:** No page contains a fake/simulation-only form, a dead button, or
a static `—` KPI placeholder where a real value is available.

---

### P11-J — Backend-Complete / Frontend-Absent UIs 🟡 Medium

- [ ] **P11-44** Build Approval Delegation UI (P4-02): new section in `ProfilePage.tsx`
      (or dedicated page) — list active delegations, create form (target user + date
      range), revoke button. Uses `DelegationController`'s existing 3 endpoints.
- [ ] **P11-45** Build MatchingConfig UI (REQ-08): admin page to view/edit
      `matching_config` tolerance thresholds.
- [ ] **P11-46** Build Remittance Advice UI (REQ-11): page/section to view & download
      remittance advices for processed payments.
- [ ] **P11-47** Build Webhooks/Integration Status UI (REQ-22): new admin page wrapping
      `WebhookController` (register/list/update/delete, delivery log) and
      `IntegrationStatusController` (integration health). Depends on P11-08/P11-09
      (service-layer refactor) landing first.

**P11-J Exit Criteria:** All 4 backends have a corresponding, reachable frontend page
with nav entry and i18n coverage.

---

### P11-K — Larger Feature Builds 🟡 Medium

- [ ] **P11-48** Build bulk/multi-file invoice upload (REQ-05): new endpoint accepting
      multiple files + frontend multi-select upload UI.
- [ ] **P11-49** Correct `ArchivePage.tsx:175`'s misleading SHA-256/retention-policy text
      (REQ-14, partial scope per `PLAN-CORRECTIONS.md` §5) — log the deferred
      re-verify-on-download and retention-policy job to `docs/KNOWN_ISSUES_REGISTRY.md`.
- [ ] **P11-50** Add append-only `document_access_log` table + logging hook in
      `InvoiceDocumentController.download()` (REQ-16, partial scope) — log deferred
      versioning/viewer to `docs/KNOWN_ISSUES_REGISTRY.md`.
- [ ] **P11-51** Add an auto-refreshing "recent activity" panel to `AdminAuditPage.tsx`
      (REQ-19, partial scope) — log deferred statistical/ML anomaly detection to
      `docs/KNOWN_ISSUES_REGISTRY.md`.
- [ ] **P11-52** Add `Department.budget` column via Flyway (REQ-20) + a budget-vs-actual
      comparison report (REQ-21, partial scope) — log deferred report builder,
      scheduling, distribution, and executive-summary items to
      `docs/KNOWN_ISSUES_REGISTRY.md`.
- [ ] **P11-53** Add encryption-status indicator widget + security-health dashboard
      (REQ-24, partial scope — 2 of 8 items: encryption coverage, MFA adoption %,
      login-failure trends, `webhookDeliverySuccessRate`) — log deferred backup status,
      privacy-policy tracking, incident reporting, SOX/IFRS checklist, and compliance
      calendar to `docs/KNOWN_ISSUES_REGISTRY.md`.

**P11-K Exit Criteria:** All 6 tasks delivered to their stated partial/full scope; every
deferred remainder has a corresponding `docs/KNOWN_ISSUES_REGISTRY.md` entry per the
Living Documentation Rule (CLAUDE.md §12).

---

**Phase 11 Exit Criteria:** All 53 `P11-NN` tasks resolved or honestly left `[ ]` with
their severity intact (no silent TODOs). `./mvnw test` — 0 failures. Frontend builds
without TS errors. None of CLAUDE.md §3's 10 absolute rules violated by any new code.
All deferred-scope items logged in `docs/KNOWN_ISSUES_REGISTRY.md`. This checklist is
executed by Phase 10 of the audit cycle (`docs/audit/PLAN-CORRECTIONS.md`); once complete,
the audit cycle proceeds to its own Phase 11 (project documentation updates).
