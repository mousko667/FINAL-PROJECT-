# PROJECT_REPORT.md — OCT Invoice System

**Project:** Digital and Secure Supplier Invoice Validation Management System — Owendo Container Terminal (OCT)
**Repository:** `invoice-system/` (git, branch `main`, HEAD `74a92c2`)
**Purpose of this document:** Technical audit of the code base against the locked Chapters 1 + 2 of the thesis, formatted as ready-to-paste source material for Chapter 3 (Design) and Chapter 4 (Implementation).
**Method:** Direct reading of source (entities, controllers, migrations, security filters, state machine, frontend routes, configuration including git-ignored files). Every assertion is backed by a `file:line` citation. "NOT IMPLEMENTED" is used literally; nothing is invented.

> **Scope note on the workflow.** The thesis describes the BAP chain as
> `BROUILLON → SOUMIS → EN_VALIDATION_N1 → (EN_VALIDATION_N2 for IT/Infra/Workshop) → VALIDE → BON_A_PAYER → PAYE → ARCHIVE` with `REJETE` at any review step. The code implements exactly this (see §7).

---

## 1. EXECUTIVE STATUS

The project was originally developed against a phase plan (`P0…P11`, ~190 commits) and from Phase 11 onward tracked by module IDs (`M1…M14`) against a compliance matrix. Both histories are preserved in git. The forward-looking roadmap is `docs/TASKS.md`. Because the question is framed in phases (Phase 0–9G), the table below maps each historical phase to the **code that proves it exists today** rather than to a ✅ in a planning file.

| Phase | Theme | Proof in code (today) |
|-------|-------|------------------------|
| **P0** | Project scaffold, build, Docker | `pom.xml` (Spring Boot 3.4.1 / Java 21), `docker-compose.yml`, `Dockerfile`, `mvnw` |
| **P1** | Schema baseline + Flyway | `src/main/resources/db/migration/V1__create_departments.sql` … `V34__seed_test_users.sql` (34 migrations, consolidated baseline) |
| **P2** | Auth, JWT, RBAC | `config/SecurityConfig.java:51`, `domain/auth/service/JwtService.java`, `domain/auth/filter/JwtAuthenticationFilter.java` |
| **P3** | Users, roles, departments | `domain/user/model/User.java`, `Role.java`, `UserRole.java`, `domain/department/model/Department.java`, `V1`/`V3`/`V4`/`V5` |
| **P4** | Invoice reception + OCR | `domain/invoice/model/Invoice.java`, `domain/ocr/service/OcrService.java` (PDFBox + Tess4J), `OcrController.java` |
| **P5** | Validation workflow + state machine | `config/StateMachineConfig.java`, `domain/invoice/statemachine/InvoiceEvent.java`, `domain/workflow/guard/*` |
| **P6** | Approval workflow (N1/N2), delegation, escalation | `domain/workflow/controller/ApprovalController.java`, `DelegationController.java`, `EscalationRuleController.java` |
| **P7** | Payments, aging, remittance | `domain/payment/model/Payment.java`, `RemittanceAdvice.java`, `PaymentController.java`, `report/.../aging` |
| **P8** | Audit trail (append-only) | `domain/audit/model/AuditLog.java`, `shared/filter/AuditLoggingFilter.java`, `V32__enforce_append_only_logs.sql` |
| **P9 / 9A–9G** | Supplier domain, MFA TOTP, three-way matching, integration/webhooks | `domain/supplier/*`, `domain/mfa/service/MfaService`, `domain/purchasing/*` (3-way matching), `domain/webhook/*` (HMAC webhooks) |
| **P10** | Reporting & analytics | `domain/report/controller/ReportController.java` (23 endpoints), `ReportDefinition.java`, scheduled reports |
| **P11 / M1–M14** | IAM gaps, security policy, retention, compliance, checklists, dept access | `domain/auth/model/SecurityPolicy.java`, `domain/retention/*`, `domain/compliance/*`, `domain/checklist/*`, `domain/department/controller/DepartmentAccessController.java` |

**Overall progress (against the 14-module Project Requirements, per `docs/TASKS.md` SYNTHÈSE).**
≈ **262 requirement bullets** total; ≈ **236 ✅ fully implemented**, ≈ **40 🟠 partial**, **0 ❌ absent**, **0 🔴 broken**. The partials are intentional scope decisions (responsive web instead of a native mobile app; a configurable connector *framework* instead of a live SAP/Oracle/bank sync — see §2.B of TASKS / §3(o) here) or low-urgency polish.

**What the system does end-to-end today.** A supplier self-registers at `/register/supplier`, logs in, and submits an invoice (PDF / image / XML) through the portal; OCR (PDFBox text layer, Tesseract fallback) pre-fills the fields and a duplicate check runs at submission. An Assistant Comptable can also key invoices in (or bulk-import CSV/XML). On submission the invoice enters the BAP state machine: `SOUMIS → EN_VALIDATION_N1`, then either straight to `VALIDE` (single-level departments) or `EN_VALIDATION_N2 → VALIDE` for IT / Infrastructure / Workshop. If a Purchase Order is linked, automated three-way matching (PO/GRN/Invoice) runs with DB-configured tolerance and blocks `MISMATCH` invoices until a DAF/Admin override with justification. The DAF marks `BON_A_PAYER`; the Assistant Comptable records the payment (`PAYE`) producing a remittance-advice PDF; the invoice is then archived (`ARCHIVE`). Throughout, every action is written to an append-only audit trail, notifications fire by email + WebSocket, dashboards are role-specific, reports/exports (PDF/Excel/CSV) are available to finance roles, and admins manage users/roles, retention, compliance, and integration connectors. MFA (TOTP) is mandatory for all staff roles, supplier excluded; bank details are AES-256-GCM encrypted at rest.

---

## 2. STACK & VERSIONS

### Backend (`pom.xml`)
| Component | Version |
|-----------|---------|
| Spring Boot (parent) | **3.4.1** |
| Java | **21** (`maven.compiler.source/target = 21`) |
| Spring Boot starters | web, data-jpa, validation, actuator, security, mail, thymeleaf, websocket, devtools, test (managed by parent 3.4.1) |
| JJWT (`io.jsonwebtoken` api/impl/jackson) | **0.12.6** |
| Spring Statemachine (core + data-jpa) | **4.0.0** |
| bucket4j-core (auth rate limiting) | **7.6.0** |
| dev.samstevens.totp (MFA TOTP) | **1.7.1** |
| PostgreSQL JDBC driver | runtime (managed by parent) |
| Flyway (core + database-postgresql) | managed by parent |
| MinIO SDK | **8.5.13** |
| Apache Tika core (MIME detection) | **2.9.2** |
| Tess4J (Tesseract OCR JNA wrapper) | **5.11.0** |
| Apache PDFBox | **3.0.3** |
| Apache POI (poi-ooxml, Excel export) | **5.3.0** |
| iText core (PDF export) | **8.0.5** |
| Lombok | **1.18.36** |
| MapStruct | **1.6.3** (componentModel=spring) |
| springdoc-openapi (Swagger UI) | **2.7.0** |
| Testcontainers (junit-jupiter + postgresql) | **1.20.4** (test) |
| H2 | test scope (fast unit tests) |
| spring-security-test | test scope |

**Build/test tooling.** Maven (wrapper `mvnw`); JUnit 5 (Jupiter) via spring-boot-starter-test; Testcontainers (real PostgreSQL for integration tests); **JaCoCo 0.8.12** with an enforced gate (`pom.xml:309-358`): LINE ≥ 0.80, BRANCH ≥ 0.75, excluding `dto/`, `model/`, `config/`, `*Application`. Surefire forces `spring.profiles.active=test`.

### Frontend (`frontend/package.json`)
| Component | Version |
|-----------|---------|
| React / React-DOM | **19.2.4** |
| TypeScript | **~6.0.2** |
| Vite | **8.0.4** |
| Redux Toolkit / react-redux | **2.11.2** / **9.2.0** |
| React Router DOM | **7.14.0** |
| @tanstack/react-query | **5.96.2** |
| axios | **1.14.0** |
| react-hook-form / @hookform/resolvers / zod | **7.72.1** / **5.2.2** / **4.3.6** |
| i18next / react-i18next / browser-languagedetector | **26.0.3** / **17.0.2** / **8.2.1** |
| @stomp/stompjs / sockjs-client | **7.3.0** / **1.6.1** (WebSocket) |
| react-pdf | **9.2.1** (document viewer) |
| recharts | **3.8.1** (charts) |
| lucide-react | **1.7.0** (icons) |
| tailwindcss | **3.4.19** (+ tailwind-merge 3.5.0, class-variance-authority 0.7.1) |
| **Test:** Vitest | **4.1.2** |
| @playwright/test (e2e) | **1.59.1** |
| @testing-library/react / jest-dom / user-event | **16.3.2** / **6.9.1** / **14.6.1** |
| ESLint | **9.39.4** (typescript-eslint 8.58.0) |

### Infrastructure (`docker-compose.yml`)
| Service | Image / Tag | Notes |
|---------|-------------|-------|
| MinIO | `minio/minio:latest` | object storage, ports 9000 / 9001; bucket `oct-invoices` |
| MinIO init | `minio/mc:latest` | one-shot bucket creation |
| MailHog | `mailhog/mailhog:latest` | dev SMTP catcher (1025 / 8025) |
| Backend | built from `Dockerfile` (`target: runtime`) | port 8080, profile from `SPRING_PROFILES_ACTIVE` |
| Frontend | built from `frontend/Dockerfile` | nginx, port 3000→80 |
| **PostgreSQL** | **NOT in compose** | A host-native **PostgreSQL 18** on port **5433** (db `oct_invoice`) is required; compose reaches it via `host.docker.internal`. CI uses `postgres:18-alpine`. |

CI runners (`.github/workflows/ci.yml`): `postgres:18-alpine`, Java 21, Node 20.

---

## 3. CHAPTER 1 + 2 CLAIM VERIFICATION

| # | Claim | Verdict | Proof |
|---|-------|---------|-------|
| (a) | OCR-assisted extraction (PDF/XML/image) | **VERIFIED** | `domain/ocr/service/OcrService.java:60` (`extract`), PDFBox text-layer + Tess4J OCR fallback (`OcrService.java:6-12, 75, 110`); XML via `InvoiceXmlParser.java` (XXE-safe); endpoint `OcrController.java:28` `POST /api/v1/ocr/extract` |
| (b) | Duplicate detection at submission | **VERIFIED** | `domain/invoice/service/InvoiceStateMachineServiceImpl.java:87` (`performDuplicateCheck` on SUBMIT), repository `countDuplicatesBySupplierAndDescription` (`:143`); blocks with explicit error (`:148`) |
| (c) | Three-way matching (Invoice/PO/GRN) + configurable tolerance + override w/ justification | **VERIFIED** | Entities `purchasing/model/ThreeWayMatchingResult.java`, `MatchingConfig.java` (tolerance %/amount, requireGrn); `MatchingConfigController.java` (`GET/POST /matching-config`); override `InvoiceController.java:259` `POST /invoices/{id}/matching/override` (DAF/ADMIN, reason required → status `OVERRIDDEN`); line comparison `MatchingQueryController.java:43` `GET /matching/{id}/lines` |
| (d) | Configurable multi-level routing + departmental matrix; sequential N1 then N2 for IT/Infra/Workshop | **VERIFIED** | `Department.requiresN2 / n1Role / n2Role` (`Department.java:798-806`); state-machine guard `StateMachineConfig.java:66` (`requiresN2`) vs `:72` (`isSingleLevel`); seeded matrix `V1__create_departments.sql`; admin UI route `/admin/approval-matrix` |
| (e) | MFA mandatory for finance/approval roles | **VERIFIED** | `config/security/MfaSetupEnforcementFilter.java:79-86` (deny-list: mandatory for every role except `ROLE_SUPPLIER`); two-step login `AuthController.java:101` `POST /auth/mfa/validate`; policy toggle `SecurityPolicy.mfaRequired` |
| (f) | RBAC enforced at every endpoint | **VERIFIED** | `@EnableMethodSecurity` (`SecurityConfig.java:28`); `@PreAuthorize` present on every controller method audited in §6 (e.g. class-level `UserController.java:35`, method-level throughout); `.anyRequest().authenticated()` (`SecurityConfig.java:71`) |
| (g) | AES-256 at rest for supplier bank details | **VERIFIED** | `shared/util/EncryptionUtil.java:18` `AES/GCM/NoPadding`, 32-byte key (`:19,33`), 128-bit tag; applied via `EncryptionAttributeConverter` on `Supplier.bankDetails` (`Supplier.java:58-60`), `Invoice.supplierBankDetails` (`Invoice.java:986-988`), and `User.mfaSecret` (`User.java:108-111`) |
| (h) | TLS in transit | **PARTIAL** | Config exists: `application.yaml:332-338` (prod `server.ssl` TLSv1.3, PKCS12 keystore from `SSL_KEYSTORE_PATH`). **No keystore is shipped** (`docs/TASKS.md` G2). Evidence of config present; runtime TLS proof not yet produced → roadmap **R2** |
| (i) | Immutable, exportable audit trail | **VERIFIED** | Append-only enforced at DB by triggers `V32__enforce_append_only_logs.sql` (audit_logs, webhook_deliveries, document_access_log — UPDATE/DELETE raise exception); export `AuditController.java:125` `GET /audit-logs/export` (CSV/Excel/PDF) |
| (j) | Real-time tracking via dashboards for every actor | **VERIFIED** | `frontend/src/pages/DashboardPage.tsx` (role-routed) + `supplier/SupplierDashboardPage.tsx`; live updates via WebSocket (`useWebSocket` hook in `AppRoutes.tsx:7,67`); KPIs from `ReportController.java:43` `/reports/kpis` |
| (k) | Payment tracking with aging analysis | **VERIFIED (aging basic)** | `PaymentController.java` (record/batch/history/export); aging `ReportController.java:112` `GET /reports/aging` (`AgingReportDTO`). Bucketed aging widget (0-30/31-60/61-90/90+) not yet a dashboard widget → roadmap **R3** |
| (l) | Digital archiving with metadata search + retention policy | **VERIFIED** | Archive `InvoiceController.java:272` `GET /invoices/archive` (date/dept/search filters); retention `RetentionPolicy.java` + `RetentionPolicyController.java`; disposition `RetentionDispositionController.java`; document viewer `/archive` (react-pdf zoom/rotate) |
| (m) | Email + WebSocket notifications | **VERIFIED** | `Notification.java` entity; listeners `EmailNotificationListener`, `WebSocketNotificationListener`, `PersistNotificationListener` (tests present); STOMP config; `NotificationController.java` (`/notifications`, `/unread-count`, read/read-all) |
| (n) | Reporting & analytics with export (PDF/Excel) | **VERIFIED** | `ReportController.java` 23 endpoints (kpis, aging, cash-flow, bottlenecks, budget-vs-actual, volume-trend, executive-summary, builder, scheduled); export via `shared/export/TabularExportService` (CSV/Excel/PDF), `report/export/excel` + `report/export/pdf/*` |
| (o) | Documented RESTful API for ERP/banking integration (webhooks count) | **PARTIAL** | REST API documented via springdoc/Swagger (`application.yaml:144`, `/swagger-ui.html`). Webhooks fully implemented (`WebhookController.java`, HMAC-SHA256, 3-retry backoff, append-only delivery log). ERP/banking connectors are a **configurable framework** (`IntegrationConnector` type ERP/ACCOUNTING/BANKING/DMS/MOCK) **with no live external sync** — declared out-of-scope in `docs/TASKS.md §B`. Documented + webhooks = real; live ERP/bank sync = NOT IMPLEMENTED (by scope) |
| (p) | Supplier self-service portal (registration, submit, track) | **VERIFIED** | `SupplierPortalController.java` (`/api/v1/supplier/*`, class-level `@PreAuthorize hasRole('SUPPLIER')`): register via `AuthController.java:52` `/auth/register/supplier`; submit `:71` `POST /supplier/invoices`; track `:87` `GET /supplier/invoices`; dashboard `:166`; frontend routes `/supplier/*` (`AppRoutes.tsx:130-139`) |

**Summary:** 13 VERIFIED, 2 PARTIAL ((h) TLS keystore, (o) live ERP/bank sync), 0 fully NOT IMPLEMENTED. The two partials map to roadmap items **R2** and (for (o)) an explicit out-of-scope decision.

---

## 4. DOMAIN MODEL (Chapter 3 class diagram)

46 JPA `@Entity` classes. All use Lombok (`@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`) and UUID PKs (`@GeneratedValue(strategy = UUID)`) unless noted. Auditing via `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate/@LastModifiedDate`, or Hibernate `@CreationTimestamp/@UpdateTimestamp`. Soft-delete = nullable `deleted_at`.

### Package `domain.user`
**User** (`users`) — implements `UserDetails`. Fields: `id:UUID @Id`; `username:String @Column(unique,nf,100)`; `email:String @Column(unique,nf,255)`; `password:String @Column(name=password_hash,nf,255)`; `firstName/lastName:String`; `preferredLang:String(2)`; `employeeId:String`; `departmentId:UUID`; `approvalLimit:BigDecimal(15,2)`; `active:boolean`; `mfaEnabled:boolean`; `mfaSecret:String @Convert(EncryptionAttributeConverter)`; `mfaVerified:boolean`; `failedLoginAttempts:int`; `lockedUntil:Instant`; `emailVerificationToken(+expiry)`; `passwordResetToken(+expiry)`; `createdAt/updatedAt/deletedAt`. **Relations:** `@OneToMany(mappedBy=user) Set<UserRole> roles` (cascade ALL, orphanRemoval); `@ManyToOne Supplier supplier`. Auditing: yes. Soft-delete: yes.
**Role** (`roles`): `id`; `name:String(unique,nf,100)`; `description:String(255)`; `createdAt`. Auditing: createdAt.
**UserRole** (`user_roles`): `@EmbeddedId UserRoleId id`; `@ManyToOne User user`; `@ManyToOne Role role`; `@ManyToOne User assignedBy`; `assignedAt`. (Join entity for User↔Role many-to-many.)

### Package `domain.department`
**Department** (`departments`): `id`; `code:String(unique,nf,20)`; `nameFr/nameEn:String(nf,255)`; `requiresN2:boolean`; `n1Role:String(nf,100)`; `n2Role:String(100)`; `isActive:boolean`; `budget:BigDecimal(15,2)`; `createdAt/updatedAt` (`@PrePersist/@PreUpdate`).

### Package `domain.supplier`
**Supplier** (`suppliers`): `id`; `companyName:String(nf,255)`; `taxId:String(unique,nf,100)`; `contactEmail:String(nf,255)`; `contactPhone:String(50)`; `bankDetails:String @Convert(Encryption…) TEXT`; `address:TEXT`; `status:SupplierStatus @Enumerated(STRING)`; `category:SupplierCategory @Enumerated(STRING)`; `@ManyToOne User onboardedBy`; `onboardedAt`; `createdAt/updatedAt/deletedAt`; `@OneToMany(mappedBy=supplier) documents` (cascade ALL, orphanRemoval). Soft-delete: yes.
**SupplierContract** (`supplier_contracts`): `id`; `supplierId:UUID`; `reference(nf,100)`; `title(nf,255)`; `startDate/endDate:LocalDate`; `status:String(20)`; `notes(2000)`; `createdBy:UUID`; `createdAt @CreationTimestamp`.
**SupplierCommunication** (`supplier_communications`): `id`; `supplierId:UUID`; `type:String(20)`; `subject(nf,255)`; `body(2000)`; `loggedBy:UUID`; `loggedAt @CreationTimestamp`.
**SupplierDocument** (`supplier_documents`): `id`; `@ManyToOne Supplier supplier`; `documentType:SupplierDocumentType @Enumerated`; `originalFilename`; `minioObjectKey(unique,500)`; `fileSizeBytes:Long`; `checksumSha256(64)`; `@ManyToOne User uploadedBy`; `uploadedAt @CreatedDate`; `expiresAt`.

### Package `domain.invoice`
**Invoice** (`invoices`): `id`; `referenceNumber:String(unique,nf,20)`; `@ManyToOne(opt=false) Department department`; `@ManyToOne(opt=false) User submittedBy`; `@ManyToOne Supplier supplier`; flat legacy `supplierName/supplierEmail/supplierTaxId`; `supplierBankDetails:String @Convert(Encryption…) TEXT`; `amount:BigDecimal(15,2)`; `currency:String(3)='XAF'`; `issueDate/dueDate:LocalDate`; `description:TEXT`; `status:InvoiceStatus @Enumerated(STRING)`; `dataSensitivity:DataSensitivity @Enumerated`; `@Version Integer version`; `createdAt/updatedAt/deletedAt`; `purchaseOrderId:UUID`; `matchingStatus:String(20)`. **Relations:** `@OneToMany(mappedBy=invoice) List<InvoiceItem> items`; `@OneToMany(mappedBy=invoice) List<InvoiceDocument> documents` (both cascade ALL, orphanRemoval). Optimistic locking (`@Version`); soft-delete; auditing.
**InvoiceItem** (`invoice_items`): `id`; `@ManyToOne(opt=false) Invoice invoice`; `lineNumber:Integer`; `description:TEXT`; `quantity:BigDecimal(10,3)`; `unitPrice:BigDecimal(15,2)`; `totalPrice:BigDecimal(15,2)`; `createdAt`.
**InvoiceDocument** (`invoice_documents`): `id`; `@ManyToOne(opt=false) Invoice invoice`; `originalFilename(nf,255)`; `minioObjectKey(unique,nf,500)`; `fileType(nf,100)`; `fileSizeBytes:Long`; `checksumSha256(nf,64)`; `@ManyToOne(opt=false) User uploadedBy`; `uploadedAt`; `version:int=1`; `supersededByDocumentId:UUID`; `retentionDisposition:RetentionDisposition @Enumerated`; `retentionDispositionAt`; `@ManyToOne User retentionDispositionBy`. (Versioning + retention disposition.)
**DocumentAccessLog** (`document_access_log`): `id`; `@ManyToOne InvoiceDocument document @OnDelete(CASCADE)`; `invoiceId:UUID`; `@ManyToOne User accessedBy @OnDelete(SET_NULL)`; `action:String(50)='DOWNLOAD'`; `ipAddress/userAgent`; `accessedAt @CreationTimestamp`. **Append-only** (V32 triggers).

### Package `domain.purchasing`
**PurchaseOrder** (`purchase_orders`): `id`; `poNumber(unique,nf,50)`; `@ManyToOne(opt=false) Supplier supplier`; `totalAmount:BigDecimal(15,2)`; `status:PurchaseOrderStatus @Enumerated`; `@ManyToOne(opt=false) User createdBy`; `createdAt/updatedAt/deletedAt`; `@OneToMany(mappedBy=purchaseOrder) items`. Soft-delete.
**PurchaseOrderItem** (`purchase_order_items`): `id`; `@ManyToOne(opt=false) PurchaseOrder purchaseOrder`; `itemDescription(nf,255)`; `quantity:BigDecimal(10,2)`; `unitPrice:BigDecimal(15,2)`; `lineTotal:BigDecimal(15,2)`; `createdAt`.
**GoodsReceiptNote** (`goods_receipt_notes`): `id`; `grnNumber(unique,nf,50)`; `@ManyToOne(opt=false) PurchaseOrder purchaseOrder`; `@ManyToOne(opt=false) User receivedBy`; `receiptDate:LocalDate`; `createdAt/updatedAt/deletedAt`; `@OneToMany(mappedBy=goodsReceiptNote) items`. Soft-delete.
**GoodsReceiptItem** (`goods_receipt_items`): `id`; `@ManyToOne(opt=false) GoodsReceiptNote goodsReceiptNote`; `@ManyToOne(opt=false) PurchaseOrderItem purchaseOrderItem`; `receivedQuantity:BigDecimal(10,2)`; `createdAt`.
**MatchingConfig** (`matching_config`): `id`; `tolerancePercentage:BigDecimal(5,2)`; `toleranceAmount:BigDecimal(15,2)`; `requireGrn:Boolean`; `isActive:Boolean`; `@ManyToOne(opt=false) User updatedBy`; `updatedAt @LastModifiedDate`.
**ThreeWayMatchingResult** (`three_way_matching_results`): `id`; `@ManyToOne(opt=false) Invoice invoice`; `@ManyToOne(opt=false) PurchaseOrder purchaseOrder`; `@ManyToOne GoodsReceiptNote goodsReceiptNote`; `status:MatchingStatus @Enumerated`; `discrepancyNotes:TEXT`; `@ManyToOne User overriddenBy`; `overrideReason(500)`; `createdAt/updatedAt`. **Append-only by design.**

### Package `domain.workflow`
**ApprovalStep** (`approval_steps`): `id`; `@ManyToOne(opt=false) Invoice invoice`; `stepOrder:Integer`; `stepNameFr/stepNameEn:String(255)`; `@ManyToOne User approver`; `departmentCode(nf,20)`; `status:@Enumerated(STRING,20)`; `comments:TEXT`; `rejectionReason:TEXT`; `deadline:Instant`; `actionAt`; `createdAt`.
**InvoiceStatusHistory** (`invoice_status_history`): `id`; `@ManyToOne(opt=false) Invoice invoice`; `fromStatus/toStatus:String(30)`; `@ManyToOne(opt=false) User changedBy`; `changeReason:TEXT`; `changedAt`.
**ApprovalDelegation** (`approval_delegations`): `id`; `@ManyToOne(opt=false) User delegator`; `@ManyToOne(opt=false) User delegatee`; `departmentCode(nf,20)`; `fromDate/toDate:LocalDate`; `reason:TEXT`; `@ManyToOne(opt=false) User createdBy`; `createdAt`; `revoked:boolean`; `revokedAt`.
**EscalationRule** (`escalation_rules`): `id`; `hoursAfterDeadline:int`; `label(255)`; `active:boolean`; `@ManyToOne User createdBy`; `createdAt/updatedAt`.

### Package `domain.payment`
**Payment** (`payments`): `id`; `@OneToOne(opt) Invoice invoice (unique)`; `amountPaid:BigDecimal(15,2)`; `paymentDate:Instant`; `paymentMethod:PaymentMethod @Enumerated(STRING,50)`; `reference(100)`; `@ManyToOne User recordedBy`; `deleted:boolean` (soft-delete flag); `createdAt/updatedAt`.
**RemittanceAdvice** (`remittance_advice`): `id`; `@OneToOne(opt=false) Payment payment (unique)`; `pdfObjectKey(nf,255)`; `generatedAt:Instant`; `@ManyToOne(opt=false) User generatedBy`; `createdAt/updatedAt`.
**PaymentAlertRule** (`payment_alert_rules`): `id`; `daysBeforeDue:int`; `label(255)`; `active:boolean`; `@ManyToOne User createdBy`; `createdAt/updatedAt`.

### Package `domain.notification`
**Notification** (`notifications`): `id`; `@ManyToOne User user`; `@ManyToOne Invoice invoice`; `titleFr/titleEn:String(255)`; `messageFr/messageEn:TEXT`; `type:NotificationType @Enumerated(STRING,50)`; `isRead:boolean`; `readAt`; `createdAt @CreatedDate`.

### Package `domain.audit`
**AuditLog** (`audit_logs`): `id`; `@ManyToOne User user @OnDelete(SET_NULL)`; `entityType(nf,50)`; `entityId(nf,100)`; `action(nf,100)`; `oldValue/newValue:String @JdbcTypeCode(JSON) jsonb`; `ipAddress(50)`; `userAgent`; `createdAt @CreationTimestamp`. **Append-only** (V32).

### Package `domain.auth`
**ActiveSession** (`active_sessions`): `id`; `@ManyToOne(opt=false) User user`; `refreshToken(unique,nf,1024)`; `ipAddress(50)`; `userAgent:TEXT`; `createdAt @CreatedDate`; `expiresAt`; `revoked:boolean`; `revokedAt`.
**SecurityPolicy** (`security_policy`): `id`; `mfaRequired:Boolean=true`; `sessionTimeoutMinutes:Integer=60`; `maxLoginAttempts:Integer=5`; `minPasswordLength:Integer=8`; `isActive:Boolean=true`; `@ManyToOne User updatedBy`; `updatedAt @LastModifiedDate`. (Singleton, versioned.)

### Package `domain.access`
**AccessRequest** (`access_requests`): `id`; `@ManyToOne(opt) User requester`; `requestedRole(nf,100)`; `reason(nf,1000)`; `status:AccessRequestStatus @Enumerated(STRING,20)`; `@ManyToOne User reviewedBy`; `reviewComment(1000)`; `createdAt @CreatedDate`; `reviewedAt` (`@PrePersist`).

### Package `domain.announcement`
**Announcement** (`announcements`): `id`; `title(nf,200)`; `body(nf,2000)`; `severity:String(20)='INFO'`; `active:boolean`; `@ManyToOne User createdBy @OnDelete(SET_NULL)`; `createdAt @CreationTimestamp`; `expiresAt`.

### Package `domain.checklist`
**ChecklistTemplate** (`checklist_templates`): `id`; `name(nf,255)`; `departmentId:UUID` (null=global); `active:boolean`; `@ManyToOne User createdBy`; `@OneToMany(mappedBy=template) items @OrderBy(displayOrder)`; `createdAt/updatedAt`.
**ChecklistTemplateItem** (`checklist_template_items`): `id`; `@ManyToOne(opt=false) ChecklistTemplate template`; `label(nf,500)`; `required:boolean`; `displayOrder:int`.
**ChecklistResponse** (`checklist_responses`): `id`; `invoiceId:UUID`; `@ManyToOne(opt=false) ChecklistTemplate template`; `@ManyToOne User respondedBy`; `@OneToMany(mappedBy=response) items` (cascade ALL, orphanRemoval); `respondedAt @CreatedDate`.
**ChecklistResponseItem** (`checklist_response_items`): `id`; `@ManyToOne(opt=false) ChecklistResponse response`; `templateItemId:UUID`; `checked:boolean`; `note(1000)`.

### Package `domain.compliance`
**SecurityIncident** (`security_incidents`): `id`; `title(nf,255)`; `description(4000)`; `severity:String(20)='MEDIUM'`; `status:String(20)='OPEN'`; `reportedBy:UUID`; `reportedAt @CreationTimestamp`; `resolvedAt`.
**ComplianceChecklistItem** (`compliance_checklist_items`): `id`; `framework:String(30)` (SOX/IFRS/LOCAL); `label(nf,500)`; `completed:boolean`; `notes(2000)`; `updatedAt @UpdateTimestamp`.
**ComplianceCalendarEntry** (`compliance_calendar`): `id`; `title(nf,255)`; `dueDate:LocalDate`; `description(2000)`; `completed:boolean`; `createdAt @CreationTimestamp`.
**BackupStatus** (`backup_status`): `id:Integer=1` (singleton, no UUID); `lastBackupAt:Instant`; `status:String(20)='UNKNOWN'`; `detail(1000)`.
**PrivacyPolicyAcceptance** (`privacy_policy_acceptances`): `id`; `userId:UUID`; `policyVersion(nf,40)`; `acceptedAt @CreationTimestamp`.

### Package `domain.retention`
**RetentionPolicy** (`retention_policy`): `id`; `retentionYears:int`; `active:boolean`; `lastSweepAt:Instant`; `lastFlaggedCount:Integer`; `@ManyToOne User updatedBy`; `createdAt/updatedAt`. (Singleton.)

### Package `domain.report`
**ReportDefinition** (`report_definitions`): `id`; `name(nf,150)`; `dataset:String(40)` (INVOICES/SUPPLIERS/AUDIT/BUDGET); `format:String(10)`; `frequency:String(20)`; `recipients(2000)` (comma-separated); `active:boolean`; `createdBy:UUID`; `createdAt @CreationTimestamp`; `lastRunAt`.

### Package `domain.webhook`
**Webhook** (`webhooks`): `id`; `name(nf,100)`; `url(nf,1000)`; `secretHash(nf,64)`; `events(nf,500)`; `isActive:Boolean`; `@ManyToOne User createdBy`; `createdAt/updatedAt`.
**WebhookDelivery** (`webhook_deliveries`): `id`; `@ManyToOne Webhook webhook`; `eventType(nf,100)`; `payload:TEXT`; `responseStatus:Integer`; `attemptCount:int`; `lastAttemptedAt`; `success:Boolean`; `createdAt`. **Append-only** (V32).
**IntegrationConnector** (`integration_connectors`): `id`; `name(nf,150)`; `type:String(30)` (ERP/ACCOUNTING/BANKING/DMS/MOCK); `endpoint(500)`; `config(4000)`; `enabled:boolean`; `lastStatus(20)`; `lastCheckedAt`; `lastMessage(1000)`; `syncIntervalMinutes:Integer`; `lastSyncAt`; `lastSyncStatus(20)`; `lastSyncMessage(1000)`; `createdBy:UUID`; `createdAt @CreationTimestamp`.

---

## 5. DATABASE (Chapter 3 ERD + data dictionary)

### 5.1 Flyway migration log (V1 → V34)
`ddl-auto: validate` (Hibernate validates only; Flyway owns the schema — `application.yaml:14`). Migrations are a **consolidated baseline** (commit `e12d6c6` folded ~60 churned migrations into V1..V34).

| Version | File | Creates / Alters |
|---------|------|------------------|
| V1 | create_departments | `departments` + seed of the OCT departmental matrix (codes, n1/n2 roles, requires_n2) |
| V2 | create_suppliers | `suppliers` (+`category`, `idx_suppliers_category`) |
| V3 | create_users | `users` (identity + MFA + login tracking + tokens + staff profile); deferred FK `suppliers.onboarded_by → users` |
| V4 | create_roles | `roles`, `user_roles` (join) |
| V5 | seed_roles_and_admin | seed 14 roles + admin user + role grant |
| V6 | create_purchase_orders | `purchase_orders`, `purchase_order_items` |
| V7 | create_goods_receipts | `goods_receipt_notes`, `goods_receipt_items` |
| V8 | create_invoices | `invoices` (+ supplier_id FK, purchase_order_id, matching_status, data_sensitivity) |
| V9 | create_matching | `three_way_matching_results`, `matching_config` |
| V10 | create_invoice_items | `invoice_items` |
| V11 | create_invoice_documents | `invoice_documents` (checksum, version, retention disposition) |
| V12 | create_invoice_status_history | `invoice_status_history` |
| V13 | create_notifications | `notifications` |
| V14 | create_payments | `payments`, `remittance_advice` |
| V15 | create_audit_logs | `audit_logs` |
| V16 | create_webhooks | `webhooks`, `webhook_deliveries` |
| V17 | create_approval_steps | `approval_steps` |
| V18 | create_active_sessions | `active_sessions` |
| V19 | create_approval_delegations | `approval_delegations` |
| V20 | create_security_policy | `security_policy` |
| V21 | create_access_requests | `access_requests` |
| V22 | create_document_access_log | `document_access_log` |
| V23 | create_announcements | `announcements` |
| V24 | create_supplier_relationship | `supplier_contracts`, `supplier_communications`, `supplier_documents` |
| V25 | create_report_definitions | `report_definitions` |
| V26 | create_integration_connectors | `integration_connectors` |
| V27 | create_payment_alert_rules | `payment_alert_rules` |
| V28 | create_escalation_rules | `escalation_rules` |
| V29 | create_retention_policy | `retention_policy` |
| V30 | create_compliance | `security_incidents`, `compliance_checklist_items`, `compliance_calendar`, `backup_status`, `privacy_policy_acceptances` |
| V31 | create_validation_checklists | `checklist_templates`, `checklist_template_items`, `checklist_responses`, `checklist_response_items` |
| V32 | enforce_append_only_logs | trigger fn `prevent_append_only_mutation()` + BEFORE UPDATE/DELETE triggers on `audit_logs`, `webhook_deliveries`, `document_access_log` |
| V33 | enforce_financial_retention | financial-record retention enforcement (DB-level guards on financial tables) |
| V34 | seed_test_users | seed test users for the 7 actors (password `Test1234!`) |

### 5.2 Data-dictionary tables (paste-ready) — core tables

**`invoices`** (`V8`)
| Column | SQL type | Null | Default | Key |
|--------|----------|------|---------|-----|
| id | UUID | NO | gen_random_uuid() | PK |
| reference_number | VARCHAR(20) | NO | — | UNIQUE |
| department_id | UUID | NO | — | FK→departments(id) |
| submitted_by | UUID | NO | — | FK→users(id) |
| supplier_name | VARCHAR(255) | YES | — | (legacy) |
| supplier_email | VARCHAR(255) | YES | — | (legacy) |
| supplier_tax_id | VARCHAR(100) | YES | — | (legacy) |
| supplier_bank_details | TEXT | YES | — | AES-GCM ciphertext |
| amount | NUMERIC(15,2) | NO | — | |
| currency | VARCHAR(3) | NO | 'XAF' | |
| issue_date | DATE | NO | — | |
| due_date | DATE | NO | — | |
| description | TEXT | YES | — | |
| status | VARCHAR(30) | NO | 'BROUILLON' | idx |
| version | INTEGER | NO | 0 | optimistic lock |
| created_at | TIMESTAMPTZ | NO | NOW() | idx |
| updated_at | TIMESTAMPTZ | NO | NOW() | |
| deleted_at | TIMESTAMPTZ | YES | — | soft-delete |
| supplier_id | UUID | YES | — | FK→suppliers(id) |
| purchase_order_id | UUID | YES | — | FK→purchase_orders(id) |
| matching_status | VARCHAR(20) | YES | — | idx |
| data_sensitivity | VARCHAR(20) | NO | 'INTERNAL' | |

*Indexes:* status; department_id; created_at; (status, created_at DESC); supplier_id; purchase_order_id; matching_status.

**`suppliers`** (`V2`)
| Column | SQL type | Null | Default | Key |
|--------|----------|------|---------|-----|
| id | UUID | NO | gen_random_uuid() | PK |
| company_name | VARCHAR(255) | NO | — | |
| tax_id | VARCHAR(100) | NO | — | UNIQUE |
| contact_email | VARCHAR(255) | NO | — | |
| contact_phone | VARCHAR(50) | YES | — | |
| bank_details | TEXT | YES | — | AES-GCM ciphertext |
| address | TEXT | YES | — | |
| status | VARCHAR(30) | NO | 'PENDING_VERIFICATION' | |
| onboarded_by | UUID | YES | — | FK→users(id) |
| onboarded_at | TIMESTAMPTZ | YES | — | |
| created_at | TIMESTAMPTZ | NO | NOW() | |
| updated_at | TIMESTAMPTZ | NO | NOW() | |
| deleted_at | TIMESTAMPTZ | YES | — | soft-delete |
| category | VARCHAR(30) | YES | — | idx_suppliers_category |

**`users`** (`V3`)
| Column | SQL type | Null | Default | Key |
|--------|----------|------|---------|-----|
| id | UUID | NO | gen_random_uuid() | PK |
| username | VARCHAR(100) | NO | — | UNIQUE |
| email | VARCHAR(255) | NO | — | UNIQUE |
| password_hash | VARCHAR(255) | NO | — | BCrypt(12) |
| first_name | VARCHAR(100) | NO | — | |
| last_name | VARCHAR(100) | NO | — | |
| preferred_lang | VARCHAR(2) | YES | 'fr' | |
| is_active | BOOLEAN | NO | TRUE | |
| created_at / updated_at | TIMESTAMPTZ | NO | NOW() | |
| deleted_at | TIMESTAMPTZ | YES | — | soft-delete |
| supplier_id | UUID | YES | — | FK→suppliers(id) |
| mfa_enabled | BOOLEAN | YES | FALSE | |
| mfa_secret | VARCHAR(255) | YES | — | AES-GCM ciphertext |
| mfa_verified | BOOLEAN | YES | FALSE | |
| failed_login_attempts | INTEGER | YES | 0 | |
| locked_until | TIMESTAMPTZ | YES | — | |
| email_verification_token (+_expiry) | VARCHAR(255)/TIMESTAMPTZ | YES | — | idx |
| password_reset_token (+_expiry) | VARCHAR(255)/TIMESTAMPTZ | YES | — | idx |
| employee_id | VARCHAR(100) | YES | — | idx |
| department_id | UUID | YES | — | FK→departments(id) |
| approval_limit | NUMERIC(15,2) | YES | — | |

> The remaining 43 tables follow the field shapes documented per-entity in §4 (column names = snake_case of the field; types map BigDecimal→NUMERIC, Instant→TIMESTAMPTZ, LocalDate→DATE, UUID→UUID, String(n)→VARCHAR(n)/TEXT, boolean→BOOLEAN, enum→VARCHAR). For Chapter 3 the three tables above plus `departments`, `three_way_matching_results`, `payments`, `approval_steps`, and `audit_logs` are the diagram-worthy core; the others are reference/config/log tables.

---

## 6. API SURFACE (Chapter 3 use cases / Chapter 4 listings)

All paths are prefixed `/api/v1`. All responses are wrapped in `ApiResponse<T>` (CLAUDE.md §3). Roles below are the literal `@PreAuthorize` expressions. `SC` = `ASSISTANT_COMPTABLE`. `!SUPPLIER && !ADMIN` reflects the separation-of-duties rule (Admin has no financial/invoice data access).

### Auth (`/auth`) — `AuthController`
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | /auth/login | permitAll | Login step 1 (returns JWT or `mfa_required`+pre-auth token) |
| POST | /auth/refresh | permitAll | Refresh access token |
| POST | /auth/register/supplier | permitAll | Supplier self-registration |
| GET | /auth/verify-email | permitAll | Email verification |
| POST | /auth/forgot-password | permitAll | Request reset link |
| POST | /auth/reset-password | permitAll | Confirm new password |
| POST | /auth/mfa/setup | isAuthenticated | Begin MFA enrolment (QR/secret) |
| POST | /auth/mfa/confirm | isAuthenticated | Confirm MFA enrolment with OTP |
| POST | /auth/mfa/validate | permitAll | Login step 2 (OTP → full JWT) |

### Security policy & sessions
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET/PUT | /admin/security-policy | ADMIN | Read/update singleton security policy |
| GET | /admin/security-health | ADMIN | Encryption/MFA/lockouts/webhooks health |
| GET | /admin/sessions | ADMIN | List active sessions |
| DELETE | /admin/sessions/user/{userId} | ADMIN | Revoke a user's sessions |

### Users / roles / profile / access
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET/POST/PUT | /users, /users/{id} | ADMIN (class-level) | CRUD users |
| PATCH | /users/{id}/activate | ADMIN | Toggle active |
| PUT | /users/{id}/roles | ADMIN | Assign roles |
| POST | /users/{id}/unlock, /users/{id}/mfa/reset | ADMIN | Unlock / reset MFA |
| GET | /users/export, /export/csv | ADMIN | Export (CSV/Excel/PDF) |
| POST | /users/import/csv | ADMIN | Bulk import |
| GET | /roles | ADMIN | List roles |
| GET/PUT | /profile | isAuthenticated | Self profile |
| POST | /access-requests | auth && !SUPPLIER | Request a role |
| GET | /access-requests/mine | auth && !SUPPLIER | My requests |
| GET | /access-requests | ADMIN | All requests |
| PATCH | /access-requests/{id} | ADMIN | Approve/reject |

### Departments & department access
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | /departments, /departments/{id} | isAuthenticated | List/detail |
| POST/PUT | /departments, /departments/{id} | ADMIN | CRUD |
| PATCH | /departments/{id}/activate | ADMIN | Toggle |
| GET | /admin/department-access | ADMIN | Read-only users×roles×N1/N2 per dept |

### Invoices (`/invoices`) — `InvoiceController`, `InvoiceDocumentController`
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | /invoices | auth && !SUPPLIER && !ADMIN | List (filters) |
| GET | /invoices/export | same | Export list |
| GET | /invoices/{id} | same | Detail |
| GET | /invoices/pending-validation | ADMIN/DAF/SC (+validators) | Validation queue |
| GET | /invoices/{id}/matching | same as detail | Matching result |
| GET | /invoices/{id}/matching/export | same | Export matching report |
| GET | /invoices/{id}/history | same | Status history |
| POST | /invoices | SC | Create |
| POST | /invoices/import | SC | Bulk CSV/XML import |
| PUT | /invoices/{id} | SC | Update |
| PATCH | /invoices/{id}/sensitivity | DAF/SC | Set data sensitivity |
| DELETE | /invoices/{id} | SC | Soft-delete |
| POST | /invoices/{id}/submit | SC | BROUILLON→SOUMIS |
| POST | /invoices/{id}/resubmit | SC | REJETE→SOUMIS |
| POST | /invoices/{id}/matching/override | DAF/ADMIN | Override MISMATCH (reason) |
| GET | /invoices/archive | ADMIN/DAF/SC | Archive search |
| GET | /invoices/{id}/export/pdf | auth && !SUPPLIER | PDF export |
| POST | /invoices/{invoiceId}/documents | SC | Upload doc |
| POST | /invoices/{invoiceId}/documents/bulk | SC | Bulk docs |
| GET | /invoices/{invoiceId}/documents | isAuthenticated | List docs |
| GET | /invoices/{invoiceId}/documents/{docId}/download | isAuthenticated | Presigned download (logged) |

### OCR
| POST | /ocr/extract | SUPPLIER/SC/ADMIN | OCR/XML field extraction |

### Workflow / approval (`/invoices/{invoiceId}/workflow`) — `ApprovalController`
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | /workflow/rejection-reasons | auth && !SUPPLIER | i18n rejection-reason codes |
| GET | /workflow/steps | auth && !SUPPLIER | Approval timeline |
| POST | /workflow/assign | SC/DAF/ADMIN (+validators) | SOUMIS→EN_VALIDATION_N1 |
| POST | /workflow/validate-n1 | DAF (+N1 validators) | N1 decision |
| POST | /workflow/validate-n2 | N2 validators (INFO/INFRA/TECH) | N2 decision |
| POST | /workflow/bon-a-payer | DAF/ADMIN | VALIDE→BON_A_PAYER |
| POST | /workflow/reject | DAF/ADMIN (+validators) | →REJETE (reason required) |

### Delegations & escalation & validator stats
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST/GET/DELETE | /approvals/delegations(+/{id}) | ADMIN | Admin-managed delegations |
| POST/GET/DELETE | /approvals/delegations/mine(+/{id}) | approver roles | Self delegations |
| GET | /approvals/delegations/eligible-delegatees | approver roles | Pick delegatee |
| GET/POST/PUT/DELETE | /escalation-rules(+/{id}) | ADMIN/DAF | Escalation config |
| GET | /workflow/my-stats | auth && !SUPPLIER | Approver KPIs |

### Three-way matching (`/matching`, `/matching-config`, `/goods-receipts`, `/purchase-orders`)
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| GET | /matching | auth && !SUPPLIER && !ADMIN | Matching list (latest per invoice) |
| GET | /matching/{invoiceId}/lines | same | Line-by-line PO/GRN/Invoice comparison |
| GET | /matching-config | ADMIN/SC | Read tolerance config |
| POST | /matching-config | ADMIN | Update tolerance config |
| POST/GET | /goods-receipts(+/{id}) | ADMIN/SC (read +DAF) | GRN create/list/detail |
| POST/GET/PUT/DELETE | /purchase-orders(+/{id}) | ADMIN/SC (read +DAF) | PO CRUD |

### Payments (`/payments`, `/payment-alert-rules`)
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | /payments/invoice/{invoiceId} | SC | Record payment |
| POST | /payments/batch | SC | Batch payments |
| GET | /payments/invoice/{invoiceId} | SC/DAF/ADMIN | Payment by invoice |
| GET | /payments | SC/DAF/ADMIN | History (dept filter) |
| GET | /payments/{paymentId}/remittance | SC/DAF/ADMIN | Remittance advice PDF |
| GET | /payments/export | SC/DAF/ADMIN | Export (CSV/Excel/PDF) |
| GET/POST/PUT/DELETE | /payment-alert-rules(+/{id}) | DAF/SC | Alert-rule config |

### Suppliers (`/suppliers`, `/supplier` portal)
| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST/PUT/GET | /suppliers(+/{id}) | ADMIN/SC (read +DAF) | Supplier CRUD |
| GET | /suppliers/export | ADMIN/SC/DAF | Export directory |
| POST/PATCH | /suppliers/{id}/activate, /suspend | ADMIN/SC | Status lifecycle |
| DELETE | /suppliers/{id} | ADMIN | Soft-delete |
| GET | /suppliers/{id}/performance | ADMIN/SC/DAF | Performance metrics |
| GET/POST | /suppliers/{id}/documents | ADMIN/SC/DAF (read) | Docs |
| GET/POST/DELETE | /suppliers/{id}/contracts | ADMIN/SC (read +DAF) | Contracts |
| GET/POST | /suppliers/{id}/communications | ADMIN/SC (read +DAF) | Comms log |
| POST/GET | /supplier/invoices(+/submit,/resubmit) | SUPPLIER | Portal submit/track |
| POST | /supplier/invoices/{id}/documents | SUPPLIER | Portal doc upload |
| GET/PUT | /supplier/profile | SUPPLIER | Portal profile |
| GET | /supplier/dashboard | SUPPLIER | Portal dashboard |
| POST/GET | /supplier/documents | SUPPLIER | Portal documents |

### Reports (`/reports`) — `ReportController` (DAF/SC only — Admin excluded)
kpis · summary · activity · export/excel · export/pdf/audit/{id} · export/pdf/compliance · aging · cash-flow · supplier/{id}/payments · bottlenecks · supplier/{id}/performance · budget-vs-actual · budget-alerts · definitions (GET/POST/DELETE) · definitions/{id}/run · definitions/{id}/preview · volume-trend · executive-summary. (All `hasAnyRole('DAF','ASSISTANT_COMPTABLE')`.)

### Audit (`/audit-logs`)
| GET | /audit-logs/system | ADMIN | System audit |
| GET | /audit-logs/financial | DAF | Financial audit (SoD) |
| GET | /audit-logs | ADMIN/DAF | Combined list |
| GET | /audit-logs/anomalies | ADMIN | Anomaly detection |
| GET | /audit-logs/export | ADMIN/DAF | Export |
| GET | /audit-logs/summary/{system,financial,export} | ADMIN / DAF / ADMIN+DAF | Aggregated summaries |

### Compliance / retention / archive
| GET/POST/PATCH/DELETE | /compliance/incidents,/checklist,/calendar | ADMIN (incident create: any non-supplier) | Compliance management |
| GET/POST | /compliance/backup-status | ADMIN | Backup status |
| GET/POST | /compliance/privacy-acceptance | isAuthenticated | Privacy acceptance |
| GET | /compliance/archive-report | ADMIN | Archive compliance report (no financial data) |
| GET/PUT | /retention-policy(+/compliance) | ADMIN | Retention policy + compliance status |
| GET | /retention/pending-documents | ADMIN | Documents past horizon |
| PUT | /retention/documents/{id}/disposition | ADMIN | RETAIN/PURGE |

### Notifications / announcements / checklists
| GET | /notifications, /unread-count | isAuthenticated | Inbox |
| PATCH | /notifications/{id}/read, /read-all | isAuthenticated | Mark read |
| GET/POST/PUT/PATCH/DELETE | /announcements (+/all,/{id}/active) | isAuthenticated (read) / ADMIN (write) | Announcements |
| GET/POST/PUT/DELETE | /checklist-templates(+/{id}) | ADMIN | Template CRUD |
| GET/POST | /invoices/{invoiceId}/checklist | auth && !SUPPLIER | Validator checklist responses |

### Integration (`/integrations`)
| GET/POST/PUT/PATCH/DELETE | /integrations/connectors(+/{id}/test,/sync-schedule,/sync,/enabled) | ADMIN | Connector framework |
| GET | /integrations/status | ADMIN | Connector health |
| POST/GET/DELETE | /integrations/webhooks(+/{id}/deliveries) | ADMIN | Webhooks + delivery log |

---

## 7. WORKFLOW STATE MACHINE (Chapter 3 activity + sequence diagrams)

Source: `config/StateMachineConfig.java` (Spring Statemachine 4.0.0). Initial state `BROUILLON`; end state `ARCHIVE`. Guards: `DocumentRequiredGuard`, `RoleMatchGuard`, `DepartmentTransitionGuard` (`requiresN2` / `isSingleLevel`), `RejectionReasonGuard`.

| From | Event | To | Guard | Role (from `@PreAuthorize`) |
|------|-------|-----|-------|------------------------------|
| BROUILLON | SUBMIT | SOUMIS | DocumentRequiredGuard (also triggers duplicate check + matching) | ASSISTANT_COMPTABLE |
| SOUMIS | ASSIGN_REVIEWER | EN_VALIDATION_N1 | RoleMatchGuard | SC / DAF / ADMIN (+ validators) |
| EN_VALIDATION_N1 | VALIDATE_N1 | EN_VALIDATION_N2 | `requiresN2(ctx) && RoleMatch` | DAF (+ N1 validators) |
| EN_VALIDATION_N1 | VALIDATE_N1 | VALIDE | `isSingleLevel(ctx) && RoleMatch` | DAF (+ N1 validators) |
| EN_VALIDATION_N2 | VALIDATE_N2 | VALIDE | RoleMatchGuard | N2 validators (INFO/INFRA/TECH) |
| VALIDE | BON_A_PAYER | BON_A_PAYER | RoleMatchGuard | DAF / ADMIN |
| BON_A_PAYER | RECORD_PAYMENT | PAYE | — | ASSISTANT_COMPTABLE |
| PAYE | ARCHIVE | ARCHIVE | — | (system on payment) |
| EN_VALIDATION_N1 | REJECT | REJETE | `RejectionReason && RoleMatch` | DAF / ADMIN (+ validators) |
| EN_VALIDATION_N2 | REJECT | REJETE | `RejectionReason && RoleMatch` | DAF / ADMIN (+ validators) |
| VALIDE | REJECT | REJETE | `RejectionReason && RoleMatch` | DAF / ADMIN (+ validators) |
| REJETE | RESUBMIT | SOUMIS | — | ASSISTANT_COMPTABLE |

Two-level departments (`requiresN2=true`, seeded in V1): **Informatique** (RSI→DSI), **Infrastructure** (Resp. INFRA→Directeur INFRA), **Atelier/Direction Technique** (Resp. Atelier→Directeur Technique). All others are single-level. This matches the locked Chapter 1+2 BAP definition exactly.

---

## 8. SECURITY IMPLEMENTATION (Chapter 4)

| Mechanism | File:line | Summary (algorithm) |
|-----------|-----------|---------------------|
| JWT issuance/refresh | `domain/auth/service/JwtService.java:99-107` | RS256 (RSA-2048) asymmetric signing; private key signs (`getPrivateKey :121`), public key verifies (`getPublicKey :131`); access 24 h, refresh 7 d, pre-auth 5 min (`:45-52`). |
| MFA TOTP | `domain/mfa/service/MfaService` + `AuthController.java:84-102` + `MfaSetupEnforcementFilter.java:79-86` | `dev.samstevens.totp` time-based OTP; setup→confirm→validate; secret AES-encrypted (`User.mfaSecret`). Deny-list filter forces MFA setup for every non-supplier role before any protected call. |
| Password hashing | `config/SecurityConfig.java:90-92` | `BCryptPasswordEncoder(strength=12)`. |
| AES-256 encryption util | `shared/util/EncryptionUtil.java:18-55` | AES/GCM/NoPadding, 32-byte key, random 12-byte IV per value, 128-bit auth tag, `GCM:` prefix + Base64; applied via `EncryptionAttributeConverter` JPA converter. |
| RBAC enforcement | `config/SecurityConfig.java:28,55-71` + `@PreAuthorize` on every controller | Method security enabled; permit-list for auth/swagger/actuator/ws, everything else authenticated; per-method role checks (§6); SoD: Admin excluded from financial/invoice data. |
| Input validation | `spring-boot-starter-validation` + DTO `@Valid` (controllers) + `GlobalExceptionHandler` | Bean Validation on request DTOs; centralized error translation to i18n messages. |
| Audit logging filter | `shared/filter/AuditLoggingFilter.java:23-57` | `OncePerRequestFilter` after auth; classifies entity/action from URI+method+status and writes an `AuditLog` (user, IP, UA) via `AuditService.logAction`. |
| File MIME validation | `domain/invoice/service/InvoiceDocumentService.java:267-270` | Apache Tika `tika.detect(content)` against `ALLOWED_MIME_TYPES` allow-list (PDF/JPEG/PNG/TIFF/XML); rejects disguised files. SHA-256 checksum computed on upload (`:91,283`). |
| Account lockout | `domain/auth/service/AuthService.java:389-405` | Increment `failedLoginAttempts`; at `>= maxLoginAttempts` (policy, default 5) set `lockedUntil = now + 15 min` and throw `AccountLockedException`; reset on success. |
| Rate limiting (auth) | `config/security/RateLimitingFilter.java:4-30` | bucket4j token-bucket per client on `/auth/login` + `/auth/refresh`. |
| HTTP security headers | `config/security/HttpSecurityHeadersFilter.java` | Adds security response headers (CSP/HSTS/X-Frame-Options family) ahead of the auth filters. |
| TLS in transit | `application.yaml:332-338` (prod) | `server.ssl` TLSv1.3, PKCS12 keystore from env. **Keystore not shipped** → roadmap **R2**. |

---

## 9. FRONTEND PAGE INVENTORY (Chapter 4 screenshots)

Routes from `frontend/src/AppRoutes.tsx`. Guards: `ProtectedRoute` (staff, wraps `AppShell`), `SupplierRoute` (suppliers, wraps `SupplierLayout`); fine-grained per-role visibility via `RoleGuard`/`PageRoleGuard` inside pages and nav.

### Public
| Route | Component | Allowed | Screenshot |
|-------|-----------|---------|-----------|
| /login | LoginPage | all | Login + (step 2) OTP screen |
| /register | RegisterPage | all | Staff/registration form |
| /register/supplier | SupplierRegisterPage | all | Supplier self-registration (company, tax ID, bank) |
| /forgot-password | ForgotPasswordPage | all | Request reset link |
| /reset-password | ResetPasswordPage | all | New password + strength bar |
| /verify-email | EmailVerificationPage | all | Email verification result |

### Staff (ProtectedRoute → AppShell)
| Route | Component | Allowed | Screenshot |
|-------|-----------|---------|-----------|
| /dashboard | DashboardPage | all staff (role-routed) | Role-specific KPI dashboard |
| /profile | ProfilePage | all staff | Profile + MFA setup block + role assignments |
| /access-requests | MyAccessRequestsPage | non-supplier | My role requests |
| /my-delegations | MyDelegationsPage | approvers | Self delegation list |
| /invoices | InvoiceListPage | SC/DAF | Invoice list + filters + import modal |
| /invoices/new | InvoiceCreatePage | SC | 3-step create wizard (OCR, PO link, docs) |
| /invoices/:id | InvoiceDetailPage | SC/DAF | Detail + InvoiceActionPanel + timeline + matching panel |
| /approvals | ApprovalQueuePage | validators/DAF | Approval queue + SLA colour coding |
| /financial-audit | FinancialAuditPage | DAF | Financial audit + summary tab |
| /purchase-orders | PurchaseOrdersPage | SC/ADMIN | PO list/create |
| /goods-receipts | GoodsReceiptsPage | SC/ADMIN | GRN create + list |
| /matching | MatchingListPage | SC/DAF | Matching list (filters, badges) |
| /matching/:invoiceId | MatchingDetailPage | SC/DAF | Line-by-line PO/GRN/Invoice comparison |
| /reports | ReportsPage | DAF/SC | Analytics dashboard + charts (volume trend, status) |
| /reports/builder | ReportBuilderPage | DAF/SC | Custom report builder + preview |
| /payments | PaymentsPage | SC/DAF | Payments + batch + remittance |
| /payments/alert-rules | PaymentAlertRulesPage | DAF/SC | Alert-rule config |
| /notifications | NotificationsPage | all staff | Notification center |
| /archive | ArchivePage | ADMIN/DAF/SC | Archive search + DocumentViewerModal |
| /admin/users · /admin/users/new | AdminUsersPage / AdminUserFormPage | ADMIN | User console + create form (employee ID, approval limit) |
| /admin/permissions | AdminPermissionMatrixPage | ADMIN | user×role matrix editor |
| /admin/access-requests | AdminAccessRequestsPage | ADMIN | Approve/reject access requests |
| /admin/announcements | AdminAnnouncementsPage | ADMIN | Publish announcements |
| /admin/compliance | AdminCompliancePage | ADMIN | Incidents/checklist/calendar/backup |
| /admin/departments · /new | AdminDepartmentsPage / Form | ADMIN | Department CRUD |
| /admin/audit | AdminAuditPage | ADMIN | Audit viewer + anomalies + retention card + summary tab |
| /admin/approval-matrix | ApprovalMatrixPage | ADMIN | Departmental N1/N2 routing matrix |
| /admin/delegations | AdminDelegationsPage | ADMIN | Admin delegations |
| /admin/matching-config | AdminMatchingConfigPage | ADMIN | Tolerance config |
| /admin/checklist-templates | AdminChecklistTemplatesPage | ADMIN | Checklist template CRUD |
| /admin/escalation-rules | EscalationRulesPage | ADMIN/DAF | Escalation config |
| /admin/retention-policy | AdminRetentionPolicyPage | ADMIN | Retention policy |
| /admin/archive-compliance | AdminArchiveCompliancePage | ADMIN | Archive compliance report |
| /admin/retention-disposition | AdminRetentionDispositionPage | ADMIN | Retain/purge expired docs |
| /admin/security | SecuritySettingsPage | ADMIN | Security health + sessions + policy |
| /admin/integrations | IntegrationsPage | ADMIN | Connectors + webhooks + status |
| /admin/department-access | DepartmentAccessPage | ADMIN | Read-only dept access overview |
| /admin/suppliers (+/new,/:id,/:id/edit) | SuppliersPage / Detail / Form | ADMIN/SC | Supplier directory + detail tabs |

### Supplier (SupplierRoute → SupplierLayout)
| /supplier/dashboard | SupplierDashboardPage | SUPPLIER | Supplier KPIs + required actions |
| /supplier/invoices | SupplierInvoicesPage | SUPPLIER | Submission history + progress |
| /supplier/invoices/new | SupplierInvoiceSubmitPage | SUPPLIER | Submit invoice (OCR preview) |
| /supplier/profile | SupplierProfilePage | SUPPLIER | Profile (tax + bank) |
| /supplier/documents | SupplierDocumentsPage | SUPPLIER | Document repository |

### Sub-page components worth capturing
- **NotificationDropdown** (header bell, unread count, real-time) — `components/.../Notification*`
- **DocumentUploader / BulkDocumentUpload** (drag-drop + MIME feedback)
- **InvoiceTimeline** (vertical 6-step approval journey)
- **InvoiceActionPanel** (validate-n1/n2, bon-a-payer, reject with reason dropdown)
- **MFA setup** (QR + OTP confirm, in ProfilePage)
- **Language switcher** (FR/EN, i18next)
- **MatchingBadge**, **DocumentViewerModal** (zoom/rotate, react-pdf), **AuditSummary**, **VolumeTrendSection**, **RetentionComplianceCard**, **BudgetAlerts** (all have vitest specs).

---

## 10. TESTS INVENTORY (Chapter 4 testing section)

- **Backend `@Test` methods:** **492** across **89 test classes** under `src/test` (unit + integration). Integration tests extend `support/AbstractPostgresIntegrationTest` (Testcontainers PostgreSQL).
- **Frontend unit (Vitest):** **67** test cases across 11 spec files (`frontend/src/**/*.test.tsx`).
- **Playwright e2e:** **3 spec files / 30 test cases** — `e2e/bap-single-level.spec.ts`, `e2e/bap-two-level.spec.ts`, `e2e/security-audit.spec.ts`.

**Most important integration tests:**
| Test | Covers |
|------|--------|
| `ThreeWayMatchingIntegrationTest` | PO/GRN/Invoice matching, tolerance, MISMATCH blocking |
| `MatchingQueryControllerIntegrationTest` | `/matching` + line comparison, SoD |
| `StateMachineTransitionExhaustiveTest` | every BAP transition + guard |
| `InvoiceStateMachineServiceTest` | submit, duplicate check, matching trigger |
| `MfaIntegrationTest` | two-step login, OTP, deny-list enforcement |
| `SecurityPolicyIntegrationTest` | policy CRUD, lockout thresholds |
| `BatchPaymentIntegrationTest` / `PaymentIntegrationTest` | batch + single payment, PAYE→ARCHIVE |
| `CashFlowProjectionIntegrationTest` | cash-flow 200 on real PostgreSQL (PROB-054 regression) |
| `SupplierPortalIntegrationTest` / `SupplierIntegrationTest` | portal submit/track, category filter |
| `ArchiveComplianceControllerIntegrationTest` | archive compliance report (no financial data) |
| `RetentionDispositionControllerIntegrationTest` / `RetentionPolicyControllerIntegrationTest` | retention RETAIN/PURGE, compliance status |
| `InvoiceImportIntegrationTest` | CSV/XML bulk import |
| `AuditSummaryControllerTest` / `AuditLogSummaryRepositoryTest` | aggregated audit summaries, SoD |
| `EscalationRuleServiceTest` / `ApprovalServiceTest` | escalation + approval-limit guard |

**Coverage (existing JaCoCo report `target/site/jacoco/index.html`).** Overall **LINE 66 %** (10,948 of 32,979 missed → 22,031 covered) and **BRANCH 52 %** (1,010 of 2,140 missed). NB: this aggregate report **includes** the `dto/model/config` packages; the enforced JaCoCo gate (`pom.xml`) **excludes** those and requires LINE ≥ 80 % / BRANCH ≥ 75 % on the business code, which is why the gate can pass while the all-packages aggregate reads 66 %. A fresh `./mvnw jacoco:report` was not re-run for this audit (it requires the host PostgreSQL on 5433 for the Testcontainers/integration suite); the committed report is the current evidence. For an apples-to-apples thesis number, re-run with the gate exclusions applied → roadmap **R5**.

---

## 11. NON-VERSIONED CONFIG (secrets redacted)

> Per the task, every secret value below is shown as `***REDACTED***` while key names remain visible. `.env` and `target/` are git-ignored (`.gitignore`).

### `invoice-system/.env` (git-ignored)
```
APP_PORT=8080
FRONTEND_PORT=3000
SPRING_PROFILES_ACTIVE=dev
DB_HOST=host.docker.internal
DB_PORT=5433
DB_NAME=oct_invoice
DB_USER=postgres
DB_PASSWORD=***REDACTED***
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=***REDACTED***
MINIO_SECRET_KEY=***REDACTED***
MINIO_BUCKET=oct-invoices
MINIO_CONSOLE_PORT=9001
JWT_PRIVATE_KEY=***REDACTED***          # Base64 PKCS#8 RSA-2048 private key
JWT_PUBLIC_KEY=***REDACTED***           # Base64 X.509 RSA-2048 public key
JWT_EXPIRATION_MS=86400000
JWT_REFRESH_EXPIRATION_MS=604800000
ENCRYPTION_KEY=***REDACTED***           # 32-char AES-256 key
MAIL_HOST=mailhog
MAIL_PORT=1025
MAIL_USERNAME=***REDACTED***
MAIL_PASSWORD=***REDACTED***
MAIL_FROM=***REDACTED***
MAIL_FROM_NAME=OCT Invoice System
MAILHOG_SMTP_PORT=1025
MAILHOG_UI_PORT=8025
VITE_API_BASE_URL=http://localhost:8080/api/v1
VITE_WS_URL=http://localhost:8080/ws
```
> **Security note for the thesis:** the committed `.env` and `application.yaml` **test profile** currently embed a real RSA-2048 keypair and `ENCRYPTION_KEY`/`DB_PASSWORD`/`MINIO` credentials in plaintext. They are dev/test values, but should be rotated and never reused in production (the prod profile already reads them from env). See roadmap **R6**.

### `src/main/resources/application.yaml` (versioned, but profile-specific keys)
Profiles `dev` / `test` / `prod`. Keys (values from env in prod): `spring.datasource.{url,username,password}`, `jwt.{private-key,public-key,expiration-ms,refresh-expiration-ms,pre-auth-expiration-ms}`, `encryption.key`, `minio.{endpoint,access-key,secret-key,bucket,presigned-url-expiry-minutes}`, `ocr.{tessdata-path,language}`, `app.cors.allowed-origins`, `app.mail.{from,from-name}`, `app.security.mfa.enforce-secret-check`, `server.ssl.{enabled,protocol,key-store,key-store-password,key-store-type}` (prod), `management.health.mail.enabled=false`. **Test-profile JWT keys + `encryption.key=***REDACTED***`** are hardcoded for tests (lines 256-266) — flagged in R6.

### `src/test/resources/application-test.yml` (versioned)
Test datasource + Flyway clean settings (PostgreSQL `localhost:5432`). No new secrets beyond the test keys above.

### `CLAUDE.md` (versioned, AI directive)
Primary agent ruleset: project identity, mandatory reading order, architecture/security constraints, BAP workflow rules, MFA mandate, three-way matching rules, webhook rules, git discipline, bug-prevention rules. No secrets.

### `docs/MEMORY.md` (versioned, AI memory)
Session checkpoints + accumulated context. Note: its header still reads "Current Phase: Phase 9D" (stale — the project is well past Phase 9). Informational only; not a build input.

### `.cursor/rules`, `invoice-system/.cursor/rules` (IDE, git-tracked dir)
Cursor IDE rule files mirroring CLAUDE.md guidance. No secrets.

### `.github/workflows/` (versioned) — **discrepancy with TASKS.md**
- `ci.yml` — backend job (Java 21 + `postgres:18-alpine` + MinIO + `mvnw -B verify`), frontend job (Node 20 + Vitest), docker build check. **Present** (created 2026-06-07).
- `security-scan.yml` — OWASP ZAP baseline scan triggered after CI on main / manual. **Present**, with `zap-rules.tsv`.
> `docs/TASKS.md §A` marks **G1 (CI)** and **G3 (ZAP)** as ❌ absent. The files exist in the repo. TASKS.md is stale on these two — see roadmap **R1**.

### `.superpowers/sdd`, `.mvn/wrapper`, `.idea/`, `.vscode/`, `.antigravity/`
- `.mvn/wrapper` — Maven wrapper (`maven-wrapper.properties`), versioned. No secrets.
- `.idea/`, `.vscode/` (root) — present but empty / ignored by `.gitignore` (except whitelisted `extensions.json`/`settings.json`). No content of note.
- `.superpowers/sdd` — skill scaffolding; not a build input.

---

## 12. COMPLETION ROADMAP — what remains to do

The system covers Chapters 1+2 in code; what remains is **submission-truthfulness polish**: two thesis claims are PARTIAL (§3 (h), (o)), the planning file is stale on CI/ZAP, and a few `🟠` items would strengthen credibility. Ordered by priority then dependency.

| ID | Source | What is missing / partial | Why needed | Suggested implementation | Effort | Priority | Deps |
|----|--------|---------------------------|-----------|--------------------------|--------|----------|------|
| **R1** | TASKS.md §A G1/G3 vs repo | `docs/TASKS.md §A` marks CI (G1) and ZAP (G3) as ❌, but `.github/workflows/ci.yml` and `security-scan.yml` **exist**. Doc contradicts code. | A thesis must not claim work absent that is present (and vice-versa). | Edit `docs/TASKS.md §A`: mark G1 ✅ (cite `ci.yml`), G3 🟠/✅ (cite `security-scan.yml`+`zap-rules.tsv`); verify the workflows actually run green on a push and capture a screenshot/log for Chapter 4. No code change. | S | **P0** | — |
| **R2** | Thesis claim (h); TASKS.md G2 | TLS config exists (prod `server.ssl`) but **no PKCS12 keystore** is shipped, so TLS-in-transit is unproven. | Chapter 1+2 claims TLS in transit; needs runtime evidence. | Generate `keystore.p12` via the `keytool` command already in `application.yaml:329-331`; document `SSL_KEYSTORE_PATH`/`SSL_KEYSTORE_PASSWORD` in `.env` + README; OR terminate TLS at nginx and document that. Capture a `https://` handshake screenshot. Files: `.env`, `README.md`, optionally `frontend/nginx.conf`. | S | **P0** | — |
| **R3** | TASKS.md G5 / M2 #3 / M7 #2 | Aging analysis is a single `/reports/aging` list; no bucketed widget (0-30/31-60/61-90/90+) on the finance dashboard, and no per-supplier rollup. | Claim (k) reads stronger with explicit aging buckets; minor UI gap. | Backend: add `bucketedAging()` to `ReportService` returning 4 buckets + per-supplier rollup; new endpoint `GET /reports/aging/buckets` (DAF/SC). Frontend: add an `AgingBucketsWidget` to `DashboardPage` (finance) using recharts. Tests: extend `ReportServiceTest` + a vitest spec. No migration. | M | P1 | — |
| **R4** | TASKS.md G4 / PRD Module 4 | SHA-256 is computed on **upload** (`InvoiceDocumentService.java:91`) but integrity is **not re-verified on download**. | PRD Module 4 claims document integrity verification. | In `InvoiceDocumentController` download path / `InvoiceDocumentService`, recompute SHA-256 of the fetched object and compare to `checksum_sha256`; log/raise on mismatch. Test: `InvoiceDocumentServiceTest#download_verifiesChecksum`. No migration (column exists). | S | P1 | — |
| **R5** | TASKS.md / TESTING | Coverage number for the thesis is ambiguous (aggregate 66 % vs gated ≥80 %). | Chapter 4 needs one defensible coverage figure. | Run `./mvnw -P… jacoco:report` with the host PostgreSQL up; report the **gated** business-code figure (exclusions per `pom.xml`) and paste the JaCoCo summary screenshot. No code change unless coverage gate fails. | S | P1 | R1 |
| **R6** | Audit finding (this report §11) | Real RSA keypair + `ENCRYPTION_KEY` + DB/MinIO creds embedded in committed `.env` and the test profile of `application.yaml`. | Hard-coded secrets are a security-review red flag in a thesis on a *secure* system. | Rotate dev/test secrets; keep only placeholders in committed files; confirm prod reads all from env (already true). Document the secret-management story in Chapter 4 / README. Files: `.env`, `application.yaml` (test block), README. | S | P1 | — |
| **R7** | TASKS.md G6 | No root `README.md` for the actual project repo. | Submission polish; graders expect run instructions. | Write `invoice-system/README.md`: stack, `docker-compose up` + host-PostgreSQL note, profiles, test commands, default credentials (`Test1234!`). | S | P2 | R2 |
| **R8** | TASKS.md G7 | WCAG 2.1 AA accessibility unverified. | NFR (PRD §7); credibility for a quality FYP. | Run axe/Lighthouse on login, dashboard, invoice detail, approvals; fix top issues; record results in Chapter 4. Frontend only. | M | P2 | — |
| **R9** | TASKS.md M5 #9/#10, M9 #1 | 🟠 polish: matching-history viewer (only latest shown), line-by-line resolution workflow (only override), archive folder tree (metadata search only). | Nice-to-have; current behaviour is defensible by design. | Optional. If pursued: add `GET /matching/{invoiceId}/history` listing all `ThreeWayMatchingResult` rows + a viewer; everything else is documented scope. | M | P2 | **DÉCISION (2026-06-26) : écarté pour le PFE, documenté comme choix de périmètre — `docs/FUTURE_IDEAS.md` § R9** (données append-only déjà conservées ; aucune perte). |

**Suggested execution order:** **R1 → R2 → R6 → R4 → R3 → R5 → R7 → R8 → R9.** Do R1 first (pure doc-vs-code reconciliation, unblocks an honest status), then close the two PARTIAL thesis claims (R2 TLS proof, then R6 secret hygiene which pairs naturally with the security chapter, then R4 integrity-on-download). R3 (aging buckets) and R5 (clean coverage number) strengthen Chapters 3/4; R5 depends on R1's green pipeline. R7/R8/R9 are P2 polish. None of R2–R9 require a new Flyway migration (all needed columns/entities already exist).

---

## 13. CONSOLIDATED GAP TABLE

| Thesis claim | Code reality | Action |
|--------------|--------------|--------|
| (a) OCR PDF/XML/image | Implemented (PDFBox+Tess4J+XML parser, `OcrService`) | none — covered |
| (b) Duplicate detection | Implemented at SUBMIT (`InvoiceStateMachineServiceImpl:87`) | none — covered |
| (c) 3-way matching + tolerance + override | Implemented (`MatchingConfig`, override DAF/ADMIN) | none — covered |
| (d) Multi-level routing + dept matrix, N1→N2 for IT/Infra/Workshop | Implemented (state-machine guards, seeded matrix) | none — covered |
| (e) MFA mandatory for finance/approval | Implemented (deny-list filter, TOTP) | none — covered |
| (f) RBAC at every endpoint | Implemented (`@PreAuthorize` everywhere, method security) | none — covered |
| (g) AES-256 at rest for bank details | Implemented (AES-GCM converter) | none — covered |
| (h) TLS in transit | Config present (prod `server.ssl`); **no keystore shipped** | **R2** |
| (i) Immutable, exportable audit trail | Implemented (DB triggers V32 + export) | none — covered |
| (j) Real-time dashboards per actor | Implemented (role dashboards + WebSocket) | none — covered |
| (k) Payment tracking + aging | Implemented; aging is basic (no buckets/rollup) | **R3** |
| (l) Archiving + metadata search + retention | Implemented (archive search, retention policy + disposition) | none — covered |
| (m) Email + WebSocket notifications | Implemented (3 listeners + STOMP) | none — covered |
| (n) Reporting/analytics + PDF/Excel export | Implemented (23 report endpoints + TabularExportService) | none — covered |
| (o) Documented REST API for ERP/banking (webhooks) | API documented (Swagger) + webhooks (HMAC, retries, log); **ERP/bank live sync = framework only (out of scope)** | none for webhooks/docs; live sync is an explicit scope exclusion (TASKS §B) |
| (p) Supplier self-service portal | Implemented (`/supplier/*`, register/submit/track) | none — covered |
| Document integrity (PRD Module 4) | SHA-256 on upload only; not re-verified on download | **R4** |
| CI pipeline / ZAP scan (Briefing) | **Exist** in `.github/workflows`; TASKS.md still says absent | **R1** |
| Secret management (security NFR) | Real secrets committed in `.env` / test profile | **R6** |
| Coverage evidence (TESTING) | Aggregate 66 % vs gated ≥80 %; ambiguous | **R5** |
| WCAG 2.1 AA (PRD §7) | Unverified | **R8** |
| README / run docs (submission) | Absent at repo root | **R7** |

---

*End of report. All citations refer to files under `invoice-system/` at commit `74a92c2`.*
