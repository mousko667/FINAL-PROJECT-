# OCT Invoice System — Thesis System Dossier

> Single source of truth for an academic final-year thesis about this system.
> All facts below were extracted by **reading** the repository (source, config, migrations,
> docs, and existing test artifacts) — **nothing was executed or re-run**. Quantitative
> claims (test counts, coverage, versions) are taken verbatim from the cited files.
> Where a fact is not recorded in the repository, it is marked **"Not documented in repo"**.

---

## Table of Contents

1. [Project Identity](#1-project-identity)
2. [Technology Stack](#2-technology-stack)
3. [High-Level Architecture](#3-high-level-architecture)
4. [Domain Model](#4-domain-model)
5. [Data Dictionary](#5-data-dictionary)
6. [Roles & Authorization (Endpoint Inventory)](#6-roles--authorization-endpoint-inventory)
7. [Workflow / State Machine](#7-workflow--state-machine)
8. [Departments & Approval Matrix](#8-departments--approval-matrix)
9. [Three-Way Matching](#9-three-way-matching)
10. [Security](#10-security)
11. [Database & Migrations](#11-database--migrations)
12. [Frontend Page Inventory](#12-frontend-page-inventory)
13. [Testing & Quality](#13-testing--quality)
14. [Problems, Known Issues, Limitations, Future Work](#14-problems-known-issues-limitations-future-work)
15. [Deployment & Operations](#15-deployment--operations)
16. [Non-Functional Facts](#16-non-functional-facts)
17. [Reporting & Analytics](#17-reporting--analytics)
18. [Candidate Academic References](#18-candidate-academic-references)
19. [Glossary / Abbreviations](#19-glossary--abbreviations)

---

## 1. Project Identity

| Field | Value | Source |
|---|---|---|
| Name | OCT Invoice System — *Système de Gestion des Factures Fournisseurs* | `pom.xml:21-22` |
| Client | Owendo Container Terminal (OCT), Libreville, Gabon | `CLAUDE.md:11`, `README.md:125` |
| Type | Final-year Bachelor project (enterprise-grade quality target) | `CLAUDE.md:12` |
| GroupId / ArtifactId / Version | `com.oct.invoicesystem` / `invoice-system` / `1.0.0-SNAPSHOT` | `pom.xml:18-20` |
| Current commit hash | `ca4eb3b2d9584334faa3d5d66cad19b072aeb7b2` | `git rev-parse HEAD` (working tree) |
| Commit date | 2026-06-29 17:54:42 +0200 | `git log -1` |
| Dossier date | 2026-06-29 | session context |

**One-paragraph summary.** The OCT Invoice System is a web-based, secure, bilingual (French/English)
platform that digitizes Owendo Container Terminal's supplier-invoice lifecycle — the *Bon à Payer*
(BAP) process — across nine departments. Suppliers self-register and submit invoices through a
dedicated portal (or send them by email for manual entry by the Accounting Assistant); invoices then
flow through a department-specific, multi-level approval workflow (Level 1, optionally Level 2),
final CFO payment authorisation, payment recording, automatic archiving, and an immutable audit
trail (`docs/PRD.md:9-23`). The backend is a modular-monolith Spring Boot 3.4.1 application on
Java 21 with PostgreSQL 18 (Flyway-managed), MinIO object storage, RS256-JWT authentication, TOTP
MFA, AES-256-GCM field encryption, three-way PO/GRN matching, OCR field extraction, webhooks, and
reporting/export; the frontend is a React 19 + TypeScript + Vite single-page application
(`README.md:5-14`, `docs/ARCHITECTURE.md:1-26`).

---

## 2. Technology Stack

### 2.1 Backend (exact versions from `pom.xml`)

| Component | Version | `pom.xml` ref |
|---|---|---|
| Java | 21 | `:27` |
| Spring Boot (parent) | 3.4.1 | `:13` |
| spring-boot-starter-web / data-jpa / validation / actuator / security / mail / thymeleaf / websocket | managed by Boot 3.4.1 | `:49-189` |
| JJWT (jjwt-api/impl/jackson) | 0.12.6 | `:33`, `:82-100` |
| bucket4j-core (rate limiting) | 7.6.0 | `:103-107` |
| dev.samstevens.totp (MFA/TOTP) | 1.7.1 | `:109-113` |
| PostgreSQL JDBC driver | runtime (Boot-managed) | `:116-120` |
| Flyway (core + flyway-database-postgresql) | Boot-managed | `:122-130` |
| Spring State Machine (core + data-jpa) | 4.0.0 | `:132-143` |
| MinIO client | 8.5.13 | `:36`, `:145-150` |
| Apache Tika (tika-core) | 2.9.2 | `:38`, `:152-157` |
| Tess4J (Tesseract OCR wrapper) | 5.11.0 | `:39`, `:159-164` |
| Apache PDFBox | 3.0.3 | `:166-171` |
| Apache POI (poi-ooxml, Excel export) | 5.3.0 | `:41`, `:193-197` |
| iText (itext-core, PDF export) | 8.0.5 | `:40`, `:199-205` |
| Lombok | 1.18.36 | `:35`, `:208-213` |
| MapStruct | 1.6.3 | `:34`, `:215-219` |
| SpringDoc OpenAPI (Swagger UI) | 2.7.0 | `:37`, `:221-226` |
| Testcontainers (junit-jupiter, postgresql) | 1.20.4 | `:42`, `:241-254` |
| H2 (unit-test DB) | test (Boot-managed) | `:256-261` |
| JaCoCo Maven plugin | 0.8.12 | `:311-313` |

### 2.2 Frontend (exact versions from `frontend/package.json`)

| Component | Version | Component | Version |
|---|---|---|---|
| react / react-dom | ^19.2.4 | @reduxjs/toolkit | ^2.11.2 |
| react-router-dom | ^7.14.0 | react-redux | ^9.2.0 |
| typescript | ~6.0.2 | @tanstack/react-query | ^5.96.2 |
| vite | ^8.0.4 | axios | ^1.14.0 |
| vitest | ^4.1.2 | @stomp/stompjs + sockjs-client | ^7.3.0 / ^1.6.1 |
| @playwright/test | ^1.59.1 | i18next / react-i18next | ^26.0.3 / ^17.0.2 |
| @testing-library/react | ^16.3.2 | react-hook-form + zod | ^7.72.1 / ^4.3.6 |
| tailwindcss | ^3.4.19 | recharts | ^3.8.1 |
| react-pdf | ^9.2.1 | lucide-react | ^1.7.0 |
| @vitest/coverage-v8 | ^4.1.2 | pa11y-ci (a11y) | ^4.1.1 |

*Source: `frontend/package.json:16-72`.*

### 2.3 Infrastructure

| Concern | Choice | Source |
|---|---|---|
| Backend build image | `maven:3.9.6-eclipse-temurin-21` (build) → `eclipse-temurin:21-jre-alpine` (runtime), non-root user `octapp` | `Dockerfile:8,21-34` |
| Object storage | `minio/minio:latest` + `minio/mc:latest` init | `docker-compose.yml:16-52` |
| Dev mail catcher | `mailhog/mailhog:latest` (SMTP 1025, UI 8025) | `docker-compose.yml:54-63` |
| Database | **host-native** PostgreSQL 18, port 5433, db `oct_invoice` (NOT a compose service) | `docker-compose.yml:5-7`, `docs/ARCHITECTURE.md:237-250` |
| Frontend container | nginx serving Vite build, port 3000→80 | `docker-compose.yml:114-129` |

---

## 3. High-Level Architecture

Modular monolith. React SPA ↔ REST/JSON over HTTPS ↔ Spring Boot ↔ PostgreSQL (JPA) + MinIO (S3 API)
(`docs/ARCHITECTURE.md:1-26`).

### 3.1 Layering rules (strict)
- **Controller** — routing, request binding, response wrapping only; one service call per endpoint; `@PreAuthorize` on every method; returns `ResponseEntity<ApiResponse<T>>`; never touches repositories; no business `if/else` (`docs/ARCHITECTURE.md:158-165`).
- **Service** — all business logic; validates business rules; `@Transactional` where needed; Javadoc on public methods; maps entities→DTOs via MapStruct (`:166-173`).
- **Repository** — Spring Data JPA; `@Query` JPQL/native for complex queries; no business logic (`:174-179`).
- **Entity** — JPA with constraints; `@Version` optimistic locking on `Invoice`; `deleted_at` soft delete; sensitive fields via `@Convert` `EncryptionAttributeConverter` (`:180-185`).

### 3.2 Backend package map (`com.oct.invoicesystem`)
`config/` (+ `config/security/`) and `shared/` (exception, response, util, i18n, export, filter), plus
**`domain/`** feature modules: `access`, `announcement`, `audit`, `auth`, `checklist`, `compliance`,
`department`, `invoice`, `mfa`, `notification`, `ocr`, `payment`, `purchasing`, `report`, `retention`,
`security`, `storage`, `supplier`, `user`, `webhook`, `workflow` (verified against the source tree;
`docs/ARCHITECTURE.md:30-128`). Most modules depend only on `shared/`; one intentional **bidirectional
`invoice ↔ purchasing` dependency** exists for three-way matching (`:130-152`).

### 3.3 Request lifecycle (security filter chain)
`HttpSecurityHeadersFilter → RateLimitingFilter → JwtAuthenticationFilter →` **[UsernamePasswordAuthenticationFilter anchor]** `→ MfaSetupEnforcementFilter → AuditLoggingFilter →`
RBAC authorization → `@PreAuthorize` → service-layer ownership check → business logic
(`SecurityConfig.java:75-79`, `docs/ARCHITECTURE.md:311-333`). CORS is configured separately via
`CorsConfig implements WebMvcConfigurer` + `.cors(cors -> {})` (`SecurityConfig.java:54`, `:300-310`).

Key technology decisions: Spring State Machine for workflow; DB-table department config;
async `@ApplicationEvent` + `@Async` for notifications/webhooks; MapStruct (compile-time) for
DTO mapping; Flyway migrations; MinIO storage; SpringDoc OpenAPI; Spring `MessageSource` i18n
(`docs/ARCHITECTURE.md:188-201`).

---

## 4. Domain Model

> Field types/constraints below are taken from the JPA entities and the migration DDL. The
> `Invoice` entity (`Invoice.java`) was read in full; the remaining entities are described from
> their migration DDL in `docs/DATABASE.md` and the migration files, which the docs state match
> the entities (Hibernate runs with `ddl-auto: validate`, `application.yaml:14`).

### 4.1 Core entities and relationships

**Invoice** (`invoices`) — `Invoice.java:41-164`
- `id: UUID` (PK, `GenerationType.UUID`); `referenceNumber: String(20)` (NOT NULL, UNIQUE; format `FAC-{YYYY}-{NNNNN}`)
- `department: Department` `@ManyToOne(optional=false)` FK `department_id`
- `submittedBy: User` `@ManyToOne(optional=false)` FK `submitted_by`
- `supplier: Supplier` `@ManyToOne` (nullable) FK `supplier_id`
- `folder: ArchiveFolder` `@ManyToOne` (nullable) FK `folder_id`
- `supplierName: String(255)` NOT NULL; `supplierEmail: String(255)` NOT NULL; `supplierTaxId: String(100)`
- `supplierBankDetails: TEXT` — **AES-256-GCM encrypted** via `@Convert(EncryptionAttributeConverter)`
- `amount: BigDecimal(15,2)` NOT NULL; `currency: String(3)` default `XAF`
- `issueDate: LocalDate` NOT NULL; `dueDate: LocalDate` NOT NULL; `description: TEXT`
- `status: InvoiceStatus` (enum STRING, NOT NULL, default `BROUILLON`)
- `dataSensitivity: DataSensitivity` (enum STRING, NOT NULL, default `INTERNAL`)
- `version: Integer` `@Version` (optimistic locking, default 0)
- `createdAt/updatedAt: Instant`; `deletedAt: Instant` (soft delete)
- `purchaseOrderId: UUID` (nullable, for 3-way matching); `matchingStatus: String(20)`
- `items: List<InvoiceItem>` `@OneToMany(cascade ALL, orphanRemoval)`; `documents: List<InvoiceDocument>` `@OneToMany(cascade ALL, orphanRemoval)`

**InvoiceItem** (`invoice_items`) — `id`, `invoice_id` FK (ON DELETE CASCADE), `line_number`, `description`, `quantity NUMERIC(10,3)`, `unit_price NUMERIC(15,2)`, `total_price NUMERIC(15,2)` (= qty × unit_price, computed in service) (`docs/DATABASE.md:141-151`).

**InvoiceDocument** (`invoice_documents`) — `id`, `invoice_id` FK (CASCADE), `original_filename`, `minio_object_key` (UNIQUE), `file_type` (MIME), `file_size_bytes BIGINT`, `checksum_sha256 VARCHAR(64)`, `uploaded_by` FK, `uploaded_at` (`docs/DATABASE.md:153-164`).

**User** (`users`) — `id`, `username(100)` UNIQUE, `email(255)` UNIQUE, `password_hash(255)` (**BCrypt strength 12**), `first_name`, `last_name`, `preferred_lang(2)` default `fr`, `is_active`, `created_at/updated_at/deleted_at`; MFA/lockout columns added later: `mfa_enabled`, `mfa_secret(64)` (encrypted), `mfa_verified`, `failed_login_attempts`, `locked_until`, `supplier_id` FK (NULL for staff) (`docs/DATABASE.md:59-72`, `:381-390`).

**Role** (`roles`) — `id`, `name(100)` UNIQUE (e.g. `ROLE_ASSISTANT_COMPTABLE`), `description`, `created_at`. **UserRole** (`user_roles`) — composite PK `(user_id, role_id)`, `assigned_at`, `assigned_by` (M:N User↔Role) (`docs/DATABASE.md:74-89`).

**Department** (`departments`) — `id`, `code(20)` UNIQUE, `name_fr`, `name_en`, `requires_n2 BOOLEAN`, `n1_role(100)`, `n2_role(100)` (NULL if no N2), `is_active` (`docs/DATABASE.md:91-103`).

**ApprovalStep** (`approval_steps`) — `id`, `invoice_id` FK, `step_order` (1=N1, 2=N2, 3=DAF), `step_name_fr/en`, `approver_id` FK (NULL until assigned), `department_code`, `status` (PENDING|APPROVED|REJECTED), `comments`, `rejection_reason`, `deadline`, `action_at`, `created_at`; UNIQUE `(invoice_id, step_order)` (`docs/DATABASE.md:166-182`).

**InvoiceStatusHistory** (`invoice_status_history`) — `id`, `invoice_id` FK, `from_status`, `to_status`, `changed_by` FK, `change_reason`, `changed_at` (`docs/DATABASE.md:184-193`).

**Payment** (`payments`) — `id`, `invoice_id` FK **UNIQUE** (1:1), `amount`, `currency`, `payment_date`, `payment_method` (VIREMENT|CHEQUE|ESPECES), `payment_reference`, `notes`, `processed_by` FK (`docs/DATABASE.md:210-222`). **RemittanceAdvice** (`remittance_advice`) — `id`, `payment_id` FK UNIQUE, `pdf_object_key`, `generated_by`, `generated_at` (`:419-426`).

**AuditLog** (`audit_logs`) — `id`, `user_id` FK (NULL for system), `entity_type`, `entity_id`, `action`, `old_value JSONB`, `new_value JSONB`, `ip_address`, `user_agent`, `created_at` — **append-only, no `updated_at`** (`docs/DATABASE.md:224-237`).

**Notification** (`notifications`) — `id`, `user_id` FK, `invoice_id` FK (nullable), `title_fr/en`, `message_fr/en`, `type` (SUBMISSION|VALIDATION|REJECTION|APPROVAL|PAYMENT|DEADLINE), `is_read`, `read_at`, `created_at` (`docs/DATABASE.md:195-208`).

**Supplier** (`suppliers`) — `id`, `company_name`, `tax_id` UNIQUE, `contact_email`, `contact_phone`, `bank_details TEXT` (**AES-256 encrypted**), `address`, `status` (PENDING_VERIFICATION|ACTIVE|SUSPENDED), `onboarded_by/at`, `created_at/updated_at/deleted_at` (soft delete) (`docs/DATABASE.md:276-292`). **SupplierDocument** (`supplier_documents`) — `id`, `supplier_id` FK, `document_type` (TAX_CERTIFICATE|CONTRACT|OTHER), filename, `minio_object_key` UNIQUE, size, `checksum_sha256`, `uploaded_by`, `expires_at` (`:294-306`).

**PurchaseOrder** (`purchase_orders`) — `id`, `po_number` UNIQUE, `supplier_id` FK, `department_id` FK, `total_amount`, `currency`, `status` (OPEN|CLOSED|CANCELLED), `created_by`, `issue_date`, `expiry_date` (`docs/DATABASE.md:308-322`). **PurchaseOrderItem** — `id`, `po_id` FK (CASCADE), `line_number`, `description`, `quantity`, `unit_price`, `total_price`, UNIQUE `(po_id, line_number)` (`:324-335`).

**GoodsReceiptNote** (`goods_receipt_notes`) — `id`, `grn_number` UNIQUE, `po_id` FK, `received_by`, `receipt_date`, `notes` (`docs/DATABASE.md:337-346`). **GoodsReceiptItem** — `id`, `grn_id` FK (CASCADE), `po_item_id` FK, `received_quantity` (`:348-355`).

**ThreeWayMatchingResult** (`three_way_matching_results`) — `id`, `invoice_id` FK, `po_id` FK, `grn_id` FK, `status` (MATCHED|PARTIAL|MISMATCH|OVERRIDDEN), `discrepancy_notes`, `overridden_by`, `override_reason`, `created_at` — **append-only** (`docs/DATABASE.md:357-369`). **MatchingConfig** (`matching_config`) — `id`, `tolerance_percent NUMERIC(5,2)` default 2.00, `tolerance_amount NUMERIC(15,2)` default 5000.00, `require_grn BOOLEAN` default TRUE, `updated_by`, `updated_at` (`:371-379`).

**Webhook** (`webhooks`) — `id`, `name`, `url(1000)`, `secret_hash(64)` (SHA-256 of secret), `events(500)` (comma-separated), `is_active`, `created_by`, timestamps (`docs/DATABASE.md:392-403`). **WebhookDelivery** (`webhook_deliveries`) — `id`, `webhook_id` FK, `event_type`, `payload`, `response_status`, `attempt_count`, `last_attempted_at`, `success` — **append-only** (`:405-417`).

**ActiveSession** (`active_sessions`, V18) — `id`, `user_id` FK, `refresh_token(500)` UNIQUE, `ip_address`, `user_agent`, `created_at`, `expires_at`, `revoked`, `revoked_at` (`docs/DATABASE.md:452-467`).

**ApprovalDelegation** (`approval_delegations`, V19) — `id`, `delegator_id` FK, `delegatee_id` FK, `department_code`, `from_date`, `to_date`, `reason`, `created_by`, `created_at`, `revoked`, `revoked_at`; DB CHECKs `to_date >= from_date` and `delegator_id <> delegatee_id` (`docs/DATABASE.md:469-490`).

### 4.2 Multiplicities (for the ERD)
- `User` 1—N `Invoice` (submittedBy); `Department` 1—N `Invoice`; `Supplier` 0..1—N `Invoice`.
- `Invoice` 1—N `InvoiceItem`, 1—N `InvoiceDocument`, 1—N `ApprovalStep`, 1—N `InvoiceStatusHistory`, 0..1—1 `Payment`.
- `Payment` 1—0..1 `RemittanceAdvice`.
- `User` N—M `Role` (via `user_roles`).
- `Supplier` 1—N `SupplierDocument`, 1—N `PurchaseOrder`.
- `PurchaseOrder` 1—N `PurchaseOrderItem`, 1—N `GoodsReceiptNote`; `GoodsReceiptNote` 1—N `GoodsReceiptItem`.
- `Invoice` 1—N `ThreeWayMatchingResult` (append-only); `Webhook` 1—N `WebhookDelivery`.

---

## 5. Data Dictionary

The repository documents **46 tables / 400 columns / 68 indexes / 72 FKs / 11 triggers / 5 check
constraints** in the consolidated baseline (`docs/ARCHITECTURE.md:286`, `docs/DATABASE.md:12`). The
canonical per-table DDL lives in `docs/DATABASE.md` and the `V1`–`V42` migration files. The principal
tables (column → type → key constraints) are summarized below; see §4 for the full column lists.

| Table | Key columns | Notable constraints | Source |
|---|---|---|---|
| `users` | `id UUID` PK, `username` UNIQUE, `email` UNIQUE, `password_hash` (BCrypt-12), `mfa_*`, `locked_until` | soft delete `deleted_at` | `docs/DATABASE.md:59-72,381-390` |
| `roles` / `user_roles` | `name` UNIQUE / PK `(user_id, role_id)` | M:N join | `:74-89` |
| `departments` | `code` UNIQUE, `requires_n2`, `n1_role`, `n2_role` | 9 seeded rows (V1) | `:91-117` |
| `invoices` | `reference_number` UNIQUE, `amount NUMERIC(15,2)`, `status`, `version` | `@Version`, soft delete | `:119-139` |
| `invoice_items` | FK `invoice_id` CASCADE | `total_price = qty×unit_price` | `:141-151` |
| `invoice_documents` | `minio_object_key` UNIQUE, `checksum_sha256` | integrity check | `:153-164` |
| `approval_steps` | `step_order` 1/2/3 | UNIQUE `(invoice_id, step_order)` | `:166-182` |
| `invoice_status_history` | `from_status`, `to_status` | history | `:184-193` |
| `payments` | `invoice_id` UNIQUE (1:1) | `payment_method` enum | `:210-222` |
| `audit_logs` | `old_value/new_value JSONB`, `ip_address` | **append-only** (no UPDATE/DELETE) | `:224-237` |
| `notifications` | `type`, `is_read` | bilingual title/message | `:195-208` |
| `suppliers` | `tax_id` UNIQUE, `bank_details` (AES) | status lifecycle, soft delete | `:276-292` |
| `purchase_orders` / `_items` | `po_number` UNIQUE | UNIQUE `(po_id, line_number)` | `:308-335` |
| `goods_receipt_notes` / `_items` | `grn_number` UNIQUE | links PO items | `:337-355` |
| `three_way_matching_results` | `status`, `override_reason` | **append-only** | `:357-369` |
| `matching_config` | `tolerance_percent/amount`, `require_grn` | admin-configurable | `:371-379` |
| `webhooks` / `webhook_deliveries` | `secret_hash`, `events` / `attempt_count` | deliveries **append-only** | `:392-417` |
| `active_sessions` | `refresh_token` UNIQUE, `revoked` | never deleted | `:452-467` |
| `approval_delegations` | `from_date`/`to_date`, `revoked` | 2 DB CHECK constraints | `:469-490` |

**Append-only enforcement** is implemented by DB triggers in `V32__enforce_append_only_logs.sql`
(`audit_logs`, `webhook_deliveries`, `document_access_log`) and
`V41/V42` for `matching_line_resolutions`; **10-year financial retention** triggers in
`V33__enforce_financial_retention.sql` (`docs/DATABASE.md:47-48`, migration filenames).

**Performance indexes** (V-numbered) include partial indexes on `invoices(status/department_id)`
WHERE `deleted_at IS NULL`, `approval_steps(approver/deadline)` WHERE `status='PENDING'`,
`notifications(user_id)` WHERE `is_read=FALSE`, and `audit_logs(entity_type, entity_id)`
(`docs/DATABASE.md:243-258`).

---

## 6. Roles & Authorization (Endpoint Inventory)

### 6.1 Canonical role list (exactly six)

| Spring role code | Description | MFA | Source |
|---|---|---|---|
| `ROLE_SUPPLIER` | External supplier; submits own invoices, tracks status; sees only own data | **Not required** (only exempt role) | `docs/PRD.md:35`, `docs/ARCHITECTURE.md:363-365` |
| `ROLE_ASSISTANT_COMPTABLE` | Creates/submits invoices, validates, manages suppliers, records payments | Mandatory | `docs/PRD.md:36` |
| `ROLE_VALIDATEUR_N1_{DEPT}` | Level-1 (department head) approver | Mandatory | `docs/PRD.md:37` |
| `ROLE_VALIDATEUR_N2_{DEPT}` | Level-2 (director) approver — IT, Infrastructure, Workshop only | Mandatory | `docs/PRD.md:38` |
| `ROLE_DAF` | CFO: L1 approver for Finance + final *Bon à Payer* for ALL depts; **financial** audit only | Mandatory | `docs/PRD.md:39` |
| `ROLE_ADMIN` | Manages users/roles/departments/config + **system/security** audit only; **zero financial access** | Mandatory | `docs/PRD.md:40` |

> **Separation of duties (verified design rule):** `ADMIN` accesses system/security audit only;
> `DAF` accesses financial audit only. There is **no Auditor role**
> (`README.md:197`, `docs/TESTING.md:187-189`, `AuditController.java:60-185`).
> The concrete `@PreAuthorize` split: `GET /audit-logs/system` → `ADMIN`; `GET /audit-logs/financial`
> → `DAF`; `GET /audit-logs` → `ADMIN or DAF` (`AuditController.java:60-102`).

### 6.2 Endpoint inventory (method · path · controller:line · required role)

> Extracted directly from the `@RequestMapping`/`@*Mapping`/`@PreAuthorize` annotations in
> `src/main/java/**/controller/*.java`. `isAuthenticated()` means any logged-in user.

**Auth — `/api/v1/auth`** (`AuthController.java`)
| Method · Path | Line | Access |
|---|---|---|
| POST `/login` | 38 | permitAll |
| POST `/refresh` | 45 | permitAll |
| POST `/register/supplier` | 52 | permitAll |
| GET `/verify-email` | 60 | permitAll |
| POST `/forgot-password` | 68 | permitAll |
| POST `/reset-password` | 76 | permitAll |
| POST `/mfa/setup` | 84 | isAuthenticated |
| POST `/mfa/confirm` | 91 | isAuthenticated |
| POST `/mfa/validate` | 101 | permitAll |

**Invoices — `/api/v1/invoices`** (`InvoiceController.java`)
| Method · Path | Line | Access |
|---|---|---|
| GET `/` | 76 | auth, not SUPPLIER, not ADMIN |
| GET `/export` | 95 | auth, not SUPPLIER, not ADMIN |
| GET `/{id}` | 127 | auth, not SUPPLIER, not ADMIN |
| GET `/duplicate-check` | 134 | ASSISTANT_COMPTABLE, SUPPLIER |
| GET `/pending-validation` | 153 | ADMIN, DAF, ASSISTANT_COMPTABLE (+validators) |
| GET `/{id}/matching` | 178 | auth, not SUPPLIER, not ADMIN |
| GET `/{id}/matching/export` | 186 | auth, not SUPPLIER, not ADMIN |
| GET `/{id}/history` | 207 | auth, not SUPPLIER, not ADMIN |
| POST `/` | 215 | ASSISTANT_COMPTABLE |
| POST `/import` (multipart) | 227 | ASSISTANT_COMPTABLE |
| PUT `/{id}` | 242 | ASSISTANT_COMPTABLE |
| PATCH `/{id}/sensitivity` | 254 | DAF, ASSISTANT_COMPTABLE |
| DELETE `/{id}` (soft) | 265 | ASSISTANT_COMPTABLE |
| POST `/{id}/submit` | 274 | ASSISTANT_COMPTABLE |
| POST `/{id}/resubmit` | 284 | ASSISTANT_COMPTABLE |
| POST `/{id}/matching/override` | 294 | DAF, ADMIN |
| GET `/archive` | 307 | ADMIN, DAF, ASSISTANT_COMPTABLE |
| GET `/{id}/export/pdf` | 323 | auth, not SUPPLIER |

**Invoice documents — `/api/v1/invoices/{invoiceId}/documents`** (`InvoiceDocumentController.java`): POST `/` (40) ASSISTANT_COMPTABLE; POST `/bulk` (51) ASSISTANT_COMPTABLE; GET `/` (65) isAuthenticated; GET `/{docId}/download` (76) isAuthenticated.

**Invoice checklist — `/api/v1/invoices/{invoiceId}/checklist`** (`InvoiceChecklistController.java`): GET (38) / POST (45) auth, not SUPPLIER.

**Workflow — `/api/v1/invoices/{invoiceId}/workflow`** (`ApprovalController.java`)
| Method · Path | Line | Access |
|---|---|---|
| GET `/rejection-reasons` | 36 | auth, not SUPPLIER |
| GET `/steps` | 49 | auth, not SUPPLIER |
| POST `/assign` | 57 | ASSISTANT_COMPTABLE, DAF, ADMIN (+validators) |
| POST `/validate-n1` | 72 | DAF (+ N1 validators) |
| POST `/validate-n2` | 88 | N2 validators (INFO/INFRA/TECH) |
| POST `/bon-a-payer` | 100 | DAF, ADMIN |
| POST `/reject` | 111 | DAF, ADMIN (+validators) |

**Approvals / delegations / escalation / stats** (workflow module):
`/api/v1/approvals/delegations` (`DelegationController.java`): POST (32) ADMIN; GET (51) ADMIN; POST `/mine` (72), GET `/mine` (89), GET `/eligible-delegatees` (98), DELETE `/mine/{id}` (106) — approver roles; DELETE `/{id}` (127) ADMIN.
`/api/v1/escalation-rules` (`EscalationRuleController.java`): GET/POST/PUT/DELETE — `ADMIN, DAF`.
`/api/v1/workflow/my-stats` (`ValidatorStatsController.java`): GET (33) auth, not SUPPLIER.

**Payments — `/api/v1/payments`** (`PaymentController.java`): POST `/invoice/{invoiceId}` (39) ASSISTANT_COMPTABLE; POST `/batch` (51) ASSISTANT_COMPTABLE; POST `/{paymentId}/process` (61) ASSISTANT_COMPTABLE; GET `/invoice/{invoiceId}` (71), GET `/` (79), GET `/{paymentId}/remittance` (90), GET `/export` (98) — ASSISTANT_COMPTABLE, DAF, ADMIN.
**Payment alert rules — `/api/v1/payment-alert-rules`** (`PaymentAlertRuleController.java`): GET/POST/PUT/DELETE — DAF, ASSISTANT_COMPTABLE.

**Purchasing**: `/api/v1/purchase-orders` (`PurchaseOrderController.java`): POST/PUT/DELETE ADMIN+ASSISTANT_COMPTABLE; GET `/` (94) ADMIN+ASSISTANT_COMPTABLE+DAF; GET `/{id}` (85) ADMIN+ASSISTANT_COMPTABLE. `/api/v1/goods-receipts` (`GoodsReceiptController.java`): POST (35) ADMIN+ASSISTANT_COMPTABLE; GET ADMIN+ASSISTANT_COMPTABLE+DAF. `/api/v1/matching` (`MatchingQueryController.java`): GET `/` (35), GET `/{invoiceId}/lines` (48) auth, not SUPPLIER, not ADMIN; POST `/{invoiceId}/lines/{poLineId}/resolve` (55) ADMIN or DAF. `/api/v1/matching-config` (`MatchingConfigController.java`): GET (36) ADMIN+ASSISTANT_COMPTABLE; POST (45) ADMIN.

**Suppliers — `/api/v1/suppliers`** (`SupplierController.java`): POST (60), PUT `/{id}` (67) ADMIN+ASSISTANT_COMPTABLE; GET `/{id}` (75), GET `/` (81), GET `/export` (93), GET `/{id}/performance` (146), GET `/{id}/documents` (167) ADMIN+ASSISTANT_COMPTABLE+DAF; PATCH `/{id}/activate` (123), PATCH `/{id}/suspend` (131), POST `/{id}/documents` (188) ADMIN+ASSISTANT_COMPTABLE; DELETE `/{id}` (139) ADMIN. **Supplier relationship — `/api/v1/suppliers/{supplierId}`** (`SupplierRelationshipController.java`): contracts/communications GET (ADMIN+ASSISTANT_COMPTABLE+DAF), POST/DELETE (ADMIN+ASSISTANT_COMPTABLE).

**Supplier portal — `/api/v1/supplier`** (`SupplierPortalController.java`, class-level `@PreAuthorize("hasRole('SUPPLIER')")` line 53): POST `/invoices` (73); GET `/invoices` (89); POST `/invoices/{id}/submit` (109); POST `/invoices/{id}/resubmit` (122); POST `/invoices/{id}/documents` (135); GET/PUT `/profile` (149/157); GET `/dashboard` (168); POST/GET `/documents` (209/242).

**Users & admin**: `/api/v1/users` (`UserController.java`, class-level `ADMIN` line 35): GET, GET `/{id}`, POST, PUT `/{id}`, PATCH `/{id}/activate`, PUT `/{id}/roles`, POST `/{id}/unlock`, POST `/{id}/mfa/reset`, GET `/export/csv`, GET `/export`, POST `/import/csv`. `/api/v1/roles` (`RoleController.java`, ADMIN). `/api/v1/profile` (`UserProfileController.java`): GET (35) / PUT (43) isAuthenticated. `/api/v1/admin/sessions` (`AdminSessionController.java`, ADMIN). `/api/v1/departments` (`DepartmentController.java`): GET isAuthenticated; POST/PUT/PATCH ADMIN. `/api/v1/admin/department-access` (`DepartmentAccessController.java`, ADMIN).

**Audit & reporting**: `/api/v1/audit-logs` (`AuditController.java`, see §6.1 SoD split); `/api/v1/reports` (`ReportController.java`) — **all 20+ endpoints `hasAnyRole('DAF','ASSISTANT_COMPTABLE')`** (kpis, summary, activity, export/excel, export/pdf/audit, export/pdf/compliance, aging, aging/buckets, cash-flow, payment-cycle, supplier payments/performance, bottlenecks, budget-vs-actual, budget-alerts, definitions CRUD, run/preview, volume-trend, executive-summary).

**Compliance / governance (mostly ADMIN)**: `/api/v1/compliance` (`ComplianceController.java`) incidents/checklist/calendar/backup-status/archive-report (ADMIN; incident create = auth not SUPPLIER; privacy-acceptance = isAuthenticated); `/api/v1/backups` (`BackupController.java`, ADMIN); `/api/v1/retention-policy` & `/api/v1/retention` (ADMIN); `/api/v1/checklist-templates` (ADMIN); `/api/v1/announcements` (GET isAuthenticated; manage = ADMIN); `/api/v1/access-requests` (create/mine = auth not SUPPLIER; list/patch = ADMIN); `/api/v1/admin/security-policy` & `/api/v1/admin/security-health` (ADMIN).

**Integrations**: `/api/v1/integrations/connectors`, `/api/v1/integrations/webhooks`, `/api/v1/integrations/status` (all `WebhookController`/`IntegrationConnectorController`/`IntegrationStatusController`, **ADMIN**).

**OCR — `/api/v1/ocr`** (`OcrController.java`): POST `/extract` (multipart, 28) — SUPPLIER, ASSISTANT_COMPTABLE, ADMIN.

**Notifications — `/api/v1/notifications`** (`NotificationController.java`): GET, GET `/unread-count`, PATCH `/{id}/read`, PATCH `/read-all` — isAuthenticated.

**Archive folders — `/api/v1/archive`** (`ArchiveFolderController.java`): GET `/folders` (43) ADMIN+DAF+ASSISTANT_COMPTABLE; POST/PUT/DELETE `/folders` ADMIN; PATCH `/invoices/{invoiceId}/folder` (82) DAF+ASSISTANT_COMPTABLE.

---

## 7. Workflow / State Machine

Implemented with **Spring State Machine 4.0.0** (`StateMachineConfig.java`, enum-based,
`autoStartup=false`, initial `BROUILLON`, end `ARCHIVE`).

### 7.1 States (`InvoiceStatus.java`, with FR/EN labels)
`BROUILLON` (Draft) · `SOUMIS` (Submitted) · `EN_VALIDATION_N1` (Under review L1) ·
`EN_VALIDATION_N2` (Under review L2) · `VALIDE` (Validated) · `BON_A_PAYER` (Approved for payment) ·
`PAYE` (Paid) · `ARCHIVE` (Archived) · `REJETE` (Rejected).

### 7.2 Transitions, events and guards (`StateMachineConfig.java:48-117`)

| From | Event | To | Guard(s) | Actor (WORKFLOW.md) |
|---|---|---|---|---|
| BROUILLON | SUBMIT | SOUMIS | `documentRequiredGuard` | ASSISTANT_COMPTABLE |
| SOUMIS | ASSIGN_REVIEWER | EN_VALIDATION_N1 | `roleMatchGuard` | VALIDATEUR_N1 (self-assign) |
| EN_VALIDATION_N1 | VALIDATE_N1 | EN_VALIDATION_N2 | `requiresN2 && roleMatch` | VALIDATEUR_N1 (2-level dept) |
| EN_VALIDATION_N1 | VALIDATE_N1 | VALIDE | `isSingleLevel && roleMatch` | VALIDATEUR_N1 (1-level dept) |
| EN_VALIDATION_N2 | VALIDATE_N2 | VALIDE | `roleMatchGuard` | VALIDATEUR_N2 |
| VALIDE | BON_A_PAYER | BON_A_PAYER | `roleMatchGuard` | DAF |
| BON_A_PAYER | RECORD_PAYMENT | PAYE | — | ASSISTANT_COMPTABLE |
| PAYE | ARCHIVE | ARCHIVE | — | System (automatic) |
| EN_VALIDATION_N1 | REJECT | REJETE | `rejectionReason && roleMatch` | VALIDATEUR_N1 |
| EN_VALIDATION_N2 | REJECT | REJETE | `rejectionReason && roleMatch` | VALIDATEUR_N2 |
| VALIDE | REJECT | REJETE | `rejectionReason && roleMatch` | DAF |
| REJETE | RESUBMIT | SOUMIS | — | ASSISTANT_COMPTABLE |

Guards (classes in `domain/workflow/guard/`): `DocumentRequiredGuard`, `RoleMatchGuard`,
`DepartmentTransitionGuard` (`requiresN2` / `isSingleLevel`), `RejectionReasonGuard`.

### 7.3 Business rules (`docs/WORKFLOW.md:208-219`, `:68-131`)
1. Cannot submit without ≥1 attached document. 2. Rejection reason mandatory, **≥10 characters**.
3. An approver cannot approve their own submitted invoice. 4. Resubmission requires the invoice to
have been modified (version incremented). 5. DAF approves all invoices (final gate). 6. Only
ASSISTANT_COMPTABLE creates/submits. 7. Archiving is automatic (no manual action). 8. Financial
records are never hard-deleted (soft delete). 9. Amounts in XAF by default, `BigDecimal`.
10. Reference format `FAC-{YYYY}-{NNNNN}`, resets each year. Deadline timer = **3 business days**
on N1 assignment (`docs/WORKFLOW.md:82-83`).

---

## 8. Departments & Approval Matrix

Department→chain mapping is **stored in the `departments` table** (admin-configurable), seeded in
`V1` (`docs/DATABASE.md:105-117`, `docs/WORKFLOW.md:160-176`).

### 8.1 Single-level (Assistant Comptable → N1 → DAF)
| Dept code | Name (FR) | N1 role |
|---|---|---|
| DRH | Direction des Ressources Humaines | `ROLE_VALIDATEUR_N1_DRH` |
| DG | Direction Générale | `ROLE_VALIDATEUR_N1_DG` |
| FIN | Finance | `ROLE_VALIDATEUR_N1_FIN` (the DAF) |
| TERM | Terminal | `ROLE_VALIDATEUR_N1_TERM` |
| COM | Communication & RSE | `ROLE_VALIDATEUR_N1_COM` |
| QHSSE | QHSSE | `ROLE_VALIDATEUR_N1_QHSSE` |

### 8.2 Two-level (Assistant Comptable → N1 → N2 → DAF)
| Dept code | Name | N1 role | N2 role |
|---|---|---|---|
| INFO | Informatique | `ROLE_VALIDATEUR_N1_INFO` (RSI) | `ROLE_VALIDATEUR_N2_INFO` (DSI) |
| INFRA | Infrastructure | `ROLE_VALIDATEUR_N1_INFRA` | `ROLE_VALIDATEUR_N2_INFRA` |
| TECH | Atelier / Direction Technique | `ROLE_VALIDATEUR_N1_TECH` | `ROLE_VALIDATEUR_N2_TECH` |

The DAF issues the final *Bon à Payer* for **all** departments regardless of level
(`docs/WORKFLOW.md:17`, `:98-103`).

---

## 9. Three-Way Matching

- **Trigger:** automatically when an invoice carrying a `purchaseOrderId` is submitted
  (BROUILLON→SOUMIS), via `InvoiceStateMachineServiceImpl` calling `ThreeWayMatchingService`
  (`docs/ARCHITECTURE.md:481-494`, `CLAUDE.md:355-357`).
- **Tolerances (stored in `matching_config`, admin-configurable):** default **2% or 5,000 XAF,
  whichever is greater**; `require_grn=true` ⇒ no match without a GRN linked to the PO
  (`docs/WORKFLOW.md:242-248`, `docs/DATABASE.md:371-379`, seeded by `V40`).
- **Result statuses:** `MATCHED` → proceed; `PARTIAL` → proceed with `matching_status=PARTIAL`;
  `MISMATCH` → `WorkflowException`, invoice stays BROUILLON until override
  (`docs/ARCHITECTURE.md:487-494`).
- **Override:** only `ROLE_DAF` or `ROLE_ADMIN` (`POST /invoices/{id}/matching/override`,
  `InvoiceController.java:294-295`); override reason mandatory and audit-logged;
  `three_way_matching_results` is **append-only** (`CLAUDE.md:358-360`, `docs/WORKFLOW.md:248`).
- **Line-level resolution:** `MatchingQueryController` exposes per-line matching and
  `POST /matching/{invoiceId}/lines/{poLineId}/resolve` (ADMIN/DAF), backed by the append-only
  `matching_line_resolutions` table (`V38`, `V41`, `V42`).

---

## 10. Security

| Control | Concrete implementation | Source |
|---|---|---|
| **Authentication** | Stateless JWT bearer tokens; `SessionCreationPolicy.STATELESS`; permitAll endpoints = login, refresh, register/supplier, verify-email, forgot/reset-password, mfa/validate, swagger, actuator, `/ws/**` | `SecurityConfig.java:55-74` |
| **JWT signing** | **RS256 (RSA-2048)** asymmetric; private key (PKCS#8) signs, public key (X.509) verifies; keys loaded Base64-DER from `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` | `JwtService.java:21-35,99-139` |
| **Token lifetimes** | access `JWT_EXPIRATION_MS` default **86 400 000 ms (24 h)**; refresh default **604 800 000 ms (7 days)**; pre-auth (MFA) **300 000 ms (5 min)** | `application.yaml:85-87`, `JwtService.java:45-52` |
| **Password hashing** | `BCryptPasswordEncoder(12)` — **BCrypt strength 12** | `SecurityConfig.java:90-92`, `docs/DATABASE.md:64` |
| **MFA** | TOTP (`dev.samstevens.totp` 1.7.1, RFC 6238); mandatory for all roles except `ROLE_SUPPLIER`; two-step login (`mfa_required` + 5-min `pre_auth_token` → `/auth/mfa/validate`); raw TOTP secret never returned; `mfa_secret` encrypted at rest; account locks after **5 failed attempts** | `docs/WORKFLOW.md:251-258`, `docs/ARCHITECTURE.md:456-478`, `CLAUDE.md:345-352` |
| **Encryption at rest** | **AES-256-GCM** (`AES/GCM/NoPadding`), 32-byte key, **12-byte random IV**, **128-bit auth tag**, `GCM:` prefix + Base64(iv):Base64(ct); applied to supplier/invoice bank details via `@Convert(EncryptionAttributeConverter)` | `EncryptionUtil.java:14-77`, `Invoice.java:83-85` |
| **Encryption in transit** | **TLS 1.3** in `prod` profile: `server.ssl.enabled=true`, `protocol/enabled-protocols=TLSv1.3`, PKCS12 keystore | `application.yaml:324-330`, `README.md:110-121` |
| **RBAC** | `@EnableMethodSecurity` + `@PreAuthorize` on every controller method; `anyRequest().authenticated()` | `SecurityConfig.java:28,71`; §6 |
| **Segregation of duties** | ADMIN = system/security audit only; DAF = financial audit only; ADMIN has zero financial access | `AuditController.java:60-102`, `docs/PRD.md:72-77` |
| **File validation** | Apache Tika **content-based MIME detection** before storage; allow-list = `application/pdf`, `image/png`, `image/jpeg`, `image/tiff`, `application/xml`, `text/xml`; reject otherwise | `InvoiceDocumentService.java:37-45,273-281` |
| **File integrity** | SHA-256 computed on upload and **re-verified on download** before issuing the presigned URL (PROB-042 fix) | `KNOWN_ISSUES_REGISTRY.md` PROB-042 |
| **Audit logging** | `AuditLoggingFilter` logs POST/PUT/PATCH/DELETE actions; `audit_logs` append-only (DB triggers, V32) | `SecurityConfig.java:79`, `docs/DATABASE.md:224-237` |
| **Rate limiting** | bucket4j on `/api/v1/auth/login` & `/refresh`: **5 requests/minute/IP**, HTTP 429 on excess | `RateLimitingFilter.java:24-63` |
| **Security headers** | `HttpSecurityHeadersFilter` (X-Content-Type-Options: nosniff, etc.); `X-Powered-By` emptied (PROB-068) | `HttpSecurityHeadersFilter.java`, `KNOWN_ISSUES_REGISTRY.md` PROB-068 |
| **WebSocket auth** | JWT enforced on the STOMP CONNECT frame by `WebSocketAuthChannelInterceptor` (handshake permitAll at HTTP layer) | `SecurityConfig.java:67-70` |
| **Webhook signing** | HMAC-SHA256(payload, secret) in `X-OCT-Signature`; 3 retries backoff 5s/25s/125s; never blocks the invoice transaction | `docs/ARCHITECTURE.md:498-511`, `CLAUDE.md:362-368` |
| **Secret management** | prod secrets from env only; `ProdSecretConfigValidator` fails fast if missing; test keys isolated in `application-test.yml`; `.env`/`*.p12`/`certs/` gitignored | `README.md:78-90` |
| **Error hygiene** | `server.error.include-message/stacktrace/exception=never` | `application.yaml:69-72,316-317` |

---

## 11. Database & Migrations

PostgreSQL 18, Flyway-managed (`ddl-auto: validate`, `application.yaml:14,26-30`). The history was
**consolidated on 2026-06-22** from 60 churned files into a contiguous **V1–V34 baseline**, with
**V35–V42** added afterward; verified by `pg_dump` diff (46 tables / 400 columns / 68 indexes /
72 FKs / 11 triggers / 5 checks) and a full green suite (`docs/DATABASE.md:7-13`, `docs/ARCHITECTURE.md:275-296`).
**Rule:** never modify an applied migration (checksum locked — PROB-009).

| V | Purpose | V | Purpose |
|---|---|---|---|
| V1 | departments (+budget) + seed (9 depts) | V22 | document_access_log |
| V2 | suppliers (+category) | V23 | announcements |
| V3 | users (identity + MFA + tokens + profile) | V24 | supplier relationship (contracts/comms/docs) |
| V4 | roles + user_roles | V25 | report_definitions |
| V5 | seed roles + bootstrap admin | V26 | integration_connectors (+sync schedule) |
| V6 | purchase_orders (+items) | V27 | payment_alert_rules |
| V7 | goods_receipt_notes (+items) | V28 | escalation_rules |
| V8 | invoices (final shape) | V29 | retention_policy |
| V9 | three_way_matching_results + matching_config | V30 | compliance (incidents/checklist/calendar/backup/privacy) |
| V10 | invoice_items | V31 | validation_checklists (templates + responses) |
| V11 | invoice_documents (versioning + retention disposition) | V32 | enforce append-only logs (triggers) |
| V12 | invoice_status_history (+ indexes) | V33 | enforce financial 10-year retention (triggers) |
| V13 | notifications | V34 | seed test users (dev/test only) |
| V14 | payments + remittance_advice | V35 | add payment_status |
| V15 | audit_logs | V36 | create archive_folders |
| V16 | webhooks + webhook_deliveries | V37 | add invoice folder_id |
| V17 | approval_steps | V38 | matching_line_resolutions |
| V18 | active_sessions | V39 | backup_audit_logs |
| V19 | approval_delegations | V40 | seed default matching_config |
| V20 | security_policy | V41 | matching_line_resolutions append-only |
| V21 | access_requests | V42 | matching_line_resolutions block truncate |

*Source: `src/main/resources/db/migration/` filenames + `docs/DATABASE.md:15-50`.*

---

## 12. Frontend Page Inventory

React 19 + Vite SPA, lazy-loaded routes (`frontend/src/AppRoutes.tsx`). Two route guards:
`ProtectedRoute` (staff, wraps `AppShell`) and `SupplierRoute` (suppliers, wraps `SupplierLayout`).
Page-level role gating uses `PageRoleGuard`; nav uses `RoleGuard` (fallback null) — PROB-002/004.
Each row is a **candidate screenshot** for a thesis figure.

### 12.1 Public / auth (unauthenticated)
| Route | Page | Screenshot |
|---|---|---|
| `/login` | Login (+ MFA OTP second step) | ✅ |
| `/register`, `/register/supplier` | Staff / supplier registration | ✅ |
| `/forgot-password`, `/reset-password` | Password recovery | ✅ |
| `/verify-email` | Email verification | ✅ |

### 12.2 Staff (ProtectedRoute → AppShell)
| Route | Page (what it shows) | Primary role |
|---|---|---|
| `/dashboard` | KPI/overview dashboard | all staff |
| `/profile` | User profile + MFA management | all staff |
| `/access-requests` | My access requests | staff (not SUPPLIER) |
| `/my-delegations` | My approval delegations | approvers |
| `/invoices` | Invoice list (dept-scoped default) | staff (not SUPPLIER/ADMIN) |
| `/invoices/new` | Create invoice (with OCR) | ASSISTANT_COMPTABLE |
| `/invoices/:id` | Invoice detail (timeline, actions, docs viewer) | staff |
| `/approvals` | Approval queue (pending validation) | approvers/DAF |
| `/financial-audit` | Financial audit trail | DAF |
| `/purchase-orders` | Purchase orders list | ADMIN/ASSISTANT_COMPTABLE/DAF |
| `/goods-receipts` | Goods receipt notes | ADMIN/ASSISTANT_COMPTABLE |
| `/matching`, `/matching/:invoiceId` | Three-way matching list + line detail | staff |
| `/reports`, `/reports/builder` | Reports dashboard + custom report builder | DAF/ASSISTANT_COMPTABLE |
| `/payments`, `/payments/alert-rules` | Payments + alert rules | ASSISTANT_COMPTABLE/DAF |
| `/notifications` | In-app notifications | all staff |
| `/archive` | Digital archive (searchable) | ADMIN/DAF/ASSISTANT_COMPTABLE |
| `/admin/users`, `/admin/users/new` | User management / form | ADMIN |
| `/admin/permissions` | Permission matrix | ADMIN |
| `/admin/access-requests` | Access request approvals | ADMIN |
| `/admin/announcements` | Announcements | ADMIN |
| `/admin/compliance` | Compliance (incidents/checklist/calendar) | ADMIN |
| `/admin/departments`, `/admin/departments/new` | Departments + form | ADMIN |
| `/admin/audit` | System/security audit + recent activity | ADMIN |
| `/admin/approval-matrix` | Approval matrix view | ADMIN |
| `/admin/delegations` | Delegations management | ADMIN |
| `/admin/matching-config` | Matching tolerance config | ADMIN |
| `/admin/checklist-templates` | Validation checklist templates | ADMIN |
| `/admin/escalation-rules` | Escalation rules | ADMIN/DAF |
| `/admin/retention-policy`, `/admin/archive-compliance`, `/admin/retention-disposition` | Retention & archive compliance | ADMIN |
| `/admin/backups` | Backup management | ADMIN |
| `/admin/security`, `/admin/department-access` | Security settings / department access | ADMIN |
| `/admin/integrations` | Webhooks & connectors | ADMIN |
| `/admin/suppliers`, `/admin/suppliers/new`, `/admin/suppliers/:id`, `/.../edit` | Supplier list/onboarding/detail/edit | ADMIN/ASSISTANT_COMPTABLE |

### 12.3 Supplier portal (SupplierRoute → SupplierLayout)
| Route | Page | Role |
|---|---|---|
| `/supplier/dashboard` | Supplier dashboard | SUPPLIER |
| `/supplier/invoices`, `/supplier/invoices/new` | Own invoices + submit (OCR) | SUPPLIER |
| `/supplier/profile` | Supplier profile | SUPPLIER |
| `/supplier/documents` | Supplier documents | SUPPLIER |
| `*` | NotFound (404) | any |

*Source: `frontend/src/AppRoutes.tsx:74-145`. Existing captured screenshots in repo root include
`audit-01-login.png`, `audit-dashboard-aa.png`, `audit-invoices-aa.png`, `audit-invoice-create.png`,
`audit-matching.png`, `audit-payments.png`, `audit-reports.png`, `audit-suppliers.png`,
`audit-archive.png`, `audit-supplier-dashboard.png`, `audit-supplier-submit-ocr.png`.*

---

## 13. Testing & Quality

### 13.1 Real numbers (from existing artifacts — not re-run)

| Metric | Value | Source |
|---|---|---|
| Backend tests (aggregate) | **538 run, 0 failures, 0 errors, 0 skipped** | `target/surefire-reports/*.txt` (92 test classes, summed) |
| Backend test classes | 92 `*.txt` surefire reports | `target/surefire-reports/` |
| JaCoCo — instruction coverage (aggregate) | **66.31 %** (23 411 / 35 308) | `target/site/jacoco/jacoco.csv` |
| JaCoCo — line coverage (aggregate) | **68.85 %** (4 609 / 6 694) | `target/site/jacoco/jacoco.csv` |
| JaCoCo — branch coverage (aggregate) | **51.98 %** (1 210 / 2 328) | `target/site/jacoco/jacoco.csv` |
| Frontend (Vitest) test files | 23 | `frontend/src/**/*.test.ts(x)` |
| Frontend (Vitest) test cases | 80 `it()`/`test()` | `frontend/src/**/*.test.ts(x)` |
| E2E specs (Playwright) | 3 (`bap-single-level`, `bap-two-level`, `security-audit`) | `frontend/e2e/*.spec.ts` |

> **Coverage gate (`pom.xml:309-358`).** The JaCoCo `check` goal enforces **LINE ≥ 0.65** and
> **BRANCH ≥ 0.50** over business code (excluding `dto/`, `model/`, `config/`, `*Application`).
> A comment (R5, 2026-06-26) records that the thresholds were lowered from the original 80 %/75 %
> to the "real, defensible gated figure measured against host PostgreSQL — **68 % line / 53 % branch
> over 144 business classes**", tracked as test-coverage debt (`pom.xml:336-356`, PROB-070).
> `docs/TESTING.md:308-334` still shows the **original** 0.80/0.75 thresholds; the **enforced**
> values are the `pom.xml` ones (0.65/0.50). The measured aggregate from the JaCoCo artifact is
> 68.85 % line / 51.98 % branch (above), consistent with the R5 figure.

### 13.2 Strategy — test pyramid (`docs/TESTING.md:11-34`)
- **Unit** (JUnit 5 + Mockito + AssertJ) — service & validation logic, all 10 workflow business rules, all state-machine transitions (valid + invalid + guard failures), `ApprovalService` chains for all 9 departments (`docs/TESTING.md:38-91`).
- **Integration** (MockMvc + Testcontainers PostgreSQL + Spring Security Test) — every controller endpoint × {happy path, wrong role 403, unauthenticated 401, not-found 404, invalid-input 400} (`:95-118`); full-role-matrix parameterized tests (`:172-189`).
- **Lifecycle integration tests** — single-level BAP, two-level (Informatique) BAP, rejection+resubmission, asserting status history, audit log, notifications, approval-step counts, remittance generation (`:122-168`).
- **E2E** (Playwright) — BAP single-level, BAP two-level, security audit journeys.
- **Frontend component / hook tests** (React Testing Library + Vitest) — status badges, action panels, role guards, timeline, `useInvoices`/`useAuth`/`useWebSocket` (`:194-213`).
- Unit DB = in-memory H2; integration DB = real PostgreSQL via Testcontainers; Surefire forces `spring.profiles.active=test` (`pom.xml:372-378`, `docs/TESTING.md:25-34`).

### 13.3 Documented coverage gaps (`docs/TESTING.md:295-304`)
`AuthRehydrator`, `SupplierRoute` guard, `StatusBadge` new variants, `InvoiceActionPanel` i18n
fallback, and WebSocket-reconnection-on-401 were flagged as lacking tests at audit time.

---

## 14. Problems, Known Issues, Limitations, Future Work

> All items below are backed by `docs/KNOWN_ISSUES_REGISTRY.md` (the living "PROB-NNN" registry).
> The registry's mandatory format records root cause, fix, and a preventive rule for each item.

### 14.1 Resolved during development (representative, by severity)
| ID | Category | Issue → resolution |
|---|---|---|
| PROB-001 | Frontend 🔴 | Session lost on refresh → `AuthRehydrator` calls `GET /profile` at startup |
| PROB-002 | Frontend 🔴 | Supplier routes blocked → dedicated `SupplierRoute` guard |
| PROB-003 | Backend 🔴 | Lombok `boolean isActive` → `isIsActive()` mapping bug → rename to `active` |
| PROB-004 | Frontend 🟠 | RoleGuard error UI in navbar → split `RoleGuard`(null) / `PageRoleGuard` |
| PROB-005 | Frontend 🟠 | Audit page empty → wrong endpoint `/audit-logs/system`→`/audit-logs` |
| PROB-009 | Infra 🔴 | Flyway checksum mismatch → never modify applied migrations |
| PROB-011 | Infra 🟠 | nginx served stale frontend → `docker cp` + `nginx -s reload` |
| PROB-012/013 | Backend 🟠/🟡 | iText 8 API (`SolidBorder`) / `BigDecimal.valueOf` misuse |
| PROB-042 | Security 🟡 | SHA-256 not re-verified on download → now verified before presign |
| PROB-068 | Security | `X-Powered-By` header leaked → emptied (ZAP finding) |

### 14.2 Remaining limitations / future work (open or partial in the registry)
| ID | Status | Limitation → recommendation |
|---|---|---|
| PROB-049 | ⏳ Acknowledged (by design) | Validators can list/export invoices of **all** departments; dept filter is a UX default, not an authorization boundary. *Recommendation:* if a hard boundary is wanted, scope `departmentId` server-side on `GET /invoices` and `/invoices/export` + add a cross-department integration test. |
| PROB-014 | ⏳ Historic note | JWT originally HS256; **now RS256/RSA-2048** (resolved per `ARCHITECTURE.md:214`, `JwtService.java`). |
| PROB-016 | ✅ Resolved later | Approval delegation (now V19 entity + `DelegationController` + `AdminDelegationsPage`, GAP 6). |
| PROB-043 | ❌ Partial | 10-year retention is a documented *policy*; automated lifecycle/purge/legal-hold job not fully implemented. *Recommendation:* `@Scheduled` retention sweep with legal-hold + audited transition. |
| PROB-044 | ❌ Not implemented | Document **versioning** (re-upload overwrites; no revision history). *Recommendation:* `document_versions` table + distinct MinIO keys. |
| PROB-045 | ✅ Resolved later | In-app document viewer (PDF zoom/rotate via `react-pdf@9.2.1`). |
| PROB-046 | ❌ Not implemented | Statistical/ML audit **anomaly detection** (only auto-refresh activity feed). *Recommendation:* per-user baselines (mean+σ), flag >Nσ. |
| PROB-047 | ❌ Partial | Advanced report generator (REQ-21) only partially built. |
| PROB-015 | ⏳ Workaround | Playwright `fullPage` screenshot truncates nested `overflow-y-auto`. |

> The registry is large (1048 lines); the above is the representative set. For the thesis
> "Recommendations" chapter, each open item is already phrased neutrally with a proposed solution.
> A final QA pass (`docs/FINAL_QA_AUDIT_REPORT.md`, and memory note "final-qa-audit-v38-blocker")
> records that a `V38`-era audit found a migration referencing a missing table that blocked boot,
> which was corrected; treat any "100% PASS" claim cautiously and cite the measured figures here.

---

## 15. Deployment & Operations

### 15.1 Build & run
- **Backend image:** multi-stage Docker (`maven:3.9.6-eclipse-temurin-21` → `eclipse-temurin:21-jre-alpine`, non-root `octapp`, G1GC, `MaxRAMPercentage=75`) (`Dockerfile`).
- **Compose services:** `minio` (9000/9001), `minio_init` (one-shot bucket create), `mailhog` (1025/8025), `backend` (8080), `frontend` (3000→80) (`docker-compose.yml`).
- **PostgreSQL is NOT in compose** — a host-native PostgreSQL 18 on port **5433**, db `oct_invoice`, must run first; backend reaches it via `host.docker.internal:5433` (`docker-compose.yml:5-7,73-83`, `README.md:21-24`).
- **Start:** `docker compose up --build`; Flyway applies migrations on a fresh DB (`README.md:91-108`).
- **Service URLs:** frontend `:3000`, API `:8080/api/v1`, Swagger `:8080/swagger-ui.html`, MinIO `:9000`/console `:9001`, MailHog `:8025` (`README.md:99-106`).
- **No-rebuild deploy:** frontend `npm run build` → `docker cp dist/. oct_frontend:/usr/share/nginx/html/` → `nginx -s reload`; backend `mvnw -DskipTests package` → `docker cp jar` → `docker restart oct_backend` (`docs/ARCHITECTURE.md:252-265`).

### 15.2 Required services & env variables
DB (`DB_HOST/PORT/NAME/USER/PASSWORD`), MinIO (`MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET`),
JWT (`JWT_PRIVATE_KEY/PUBLIC_KEY/EXPIRATION_MS/REFRESH_EXPIRATION_MS`), `ENCRYPTION_KEY` (≥32 chars),
TLS (`SSL_KEYSTORE_PATH/PASSWORD`, prod), mail (`MAIL_HOST/PORT/USERNAME/PASSWORD/FROM/FROM_NAME`),
frontend (`VITE_API_BASE_URL`, `VITE_WS_URL`), `ALLOWED_ORIGINS` (CORS), `OCR_TESSDATA_PATH`/`OCR_LANGUAGE`
(`README.md:30-67`, `application.yaml`). `ProdSecretConfigValidator` fails fast if a prod secret is missing.

### 15.3 Profiles, backup, requirements
- Profiles: `dev` (local, MailHog, MFA secret-check off), `test` (Testcontainers PostgreSQL), `prod` (TLS 1.3, real SMTP, secrets from env) (`application.yaml:171-346`).
- **Backup:** scheduled cron `BACKUP_CRON` default `0 0 2 * * *`, retention `BACKUP_RETENTION_COUNT` default 7; backup audit log table (V39); `BackupController` (ADMIN) + restore (`application.yaml:116-120`).
- **Rotating `ENCRYPTION_KEY` invalidates existing encrypted bank details** — only rotate on a fresh DB (`README.md:88`).
- **Client/server requirements:** Java 21 JDK, Node 20+, Docker Desktop, host PostgreSQL 18 on 5433 (`README.md:16-24`). Upload limits: max file 10 MB / request 15 MB (`application.yaml:33-37`).

---

## 16. Non-Functional Facts

- **i18n (bilingual FR/EN).** Backend `MessageSource` (`messages_fr.properties` default + `messages_en.properties`; FR file is ISO-8859-1/Latin-1 per memory note), language via `Accept-Language`; frontend i18next (`fr.json`/`en.json`), default UI language French. Status enums carry FR+EN labels (`InvoiceStatus.java`). (`docs/ARCHITECTURE.md:370-388`, `application.yaml:48-52`).
- **Real-time.** WebSocket/STOMP (SockJS) for in-app notifications; JWT enforced on STOMP CONNECT; client uses `@stomp/stompjs` + `sockjs-client` with a silent error handler (PROB-007) (`SecurityConfig.java:67-70`, `frontend/package.json`).
- **Async / decoupling.** Spring `@ApplicationEvent` + `@Async` task pool (core 5, max 20, queue 100; scheduling pool 3) so a failed email/webhook never blocks a transaction (`application.yaml:55-64`).
- **Performance.** HikariCP pools (dev max 10 / prod max 20); partial DB indexes on hot query paths; presigned MinIO URLs (15-min expiry); `open-in-view: false`. The repo also records a supplier `/reports` performance fix (memory "palier1-t1-t2-t3"). (`application.yaml:107,182-190,286-292`, `docs/DATABASE.md:243-258`).
- **Scalability note.** Modular monolith with stateless JWT auth (horizontally scalable behind a load balancer); object storage and DB are external. **Not documented in repo:** measured load/throughput benchmarks.

---

## 17. Reporting & Analytics

All report endpoints are `DAF`/`ASSISTANT_COMPTABLE` only (SoD: ADMIN excluded from financials)
(`ReportController.java:46-245`). Available KPIs/reports and exports:

- **KPIs & dashboards:** `/reports/kpis`, `/reports/summary`, `/reports/activity`, `/reports/executive-summary`, `/reports/volume-trend`, `/reports/bottlenecks` (workflow bottlenecks).
- **Financial analytics:** `/reports/aging` & `/reports/aging/buckets` (overdue aging), `/reports/cash-flow`, `/reports/payment-cycle` (cycle time), `/reports/budget-vs-actual`, `/reports/budget-alerts`.
- **Supplier analytics:** `/reports/supplier/{id}/payments`, `/reports/supplier/{id}/performance`.
- **Custom report builder:** `/reports/definitions` (CRUD), `/definitions/{id}/run`, `/definitions/{id}/preview` (stored `report_definitions`, V25).
- **Exports:** Excel via Apache POI (`/reports/export/excel`); PDF via iText 8 (`/reports/export/pdf/audit/{id}`, `/reports/export/pdf/compliance`); audit-log export (`/audit-logs/export`, `/audit-logs/summary/export`); payments export, suppliers export, invoices export, per-invoice PDF, matching export.
- **Alerts:** payment alert rules (`/payment-alert-rules`), overdue invoice notifications to DAF+Admin, deadline-approaching (24h) emails (`docs/WORKFLOW.md:193-194`).
- **Module 7 (PRD):** KPI dashboard (processing times, volumes, rejection rates), Excel/PDF export, overdue alerts (`docs/PRD.md:85-89`).

---

## 18. Candidate Academic References

Standards and libraries actually used (cite alongside the implementation):

- **JWT** — RFC 7519 (JSON Web Token); RS256 = RSASSA-PKCS1-v1_5 with SHA-256 (RFC 7518). Implemented with JJWT 0.12.6, RSA-2048 (`JwtService.java`).
- **TOTP / MFA** — RFC 6238 (TOTP), RFC 4226 (HOTP). Implemented with `dev.samstevens.totp` 1.7.1.
- **BCrypt** — Provos & Mazières, "A Future-Adaptable Password Scheme" (1999); strength factor 12 (`SecurityConfig.java`).
- **AES-GCM** — NIST FIPS 197 (AES) + NIST SP 800-38D (GCM mode); 256-bit key, 96-bit IV, 128-bit tag (`EncryptionUtil.java`).
- **TLS 1.3** — RFC 8446 (prod profile, `application.yaml`).
- **HMAC-SHA256** — RFC 2104 (HMAC) + FIPS 180-4 (SHA-256) for webhook signatures.
- **OWASP** — ASVS / Top 10; an OWASP ZAP scan is configured (`.github/workflows/security-scan.yml`, `docs/audit/zap-report.*`), PROB-068 derived from it.
- **ISO 27001 / NIST 800-53** — relevant to the audit-trail, RBAC, separation-of-duties, retention, and incident/compliance modules (the system implements append-only audit logs, RBAC, SoD, and a compliance module; *the repo does not claim formal certification* — **Not documented in repo** as certified).
- **Frameworks/specs to cite:** Spring Boot 3.4.1, Spring Security, Spring State Machine 4.0.0 (UML state-machine model), Spring Data JPA / Hibernate, Flyway (evolutionary database design), React 19, MinIO (S3 API), Apache Tika (content type detection), Tesseract/Tess4J (OCR), iText 8 & Apache POI (document generation), WCAG (a11y audit at `docs/audit/wcag-a11y-audit.md`, pa11y-ci).

---

## 19. Glossary / Abbreviations

| Term | Meaning |
|---|---|
| OCT | Owendo Container Terminal (client) |
| BAP | *Bon à Payer* — "good for payment"; OCT's invoice approval/payment authorisation process |
| AA | Assistant Comptable (Accounting Assistant) — workflow initiator |
| DAF | *Directeur Administratif et Financier* (CFO) — final BAP gate, financial audit |
| DG | *Direction Générale* (General Management) |
| DRH | *Direction des Ressources Humaines* (HR) |
| RSI / DSI | IT manager (N1 INFO) / IT director (N2 INFO) |
| INFRA / TECH / TERM / COM / QHSSE / FIN | Department codes (Infrastructure / Workshop-Technical / Terminal / Communication & CSR / Quality-Health-Safety-Security-Environment / Finance) |
| N1 / N2 | Level-1 / Level-2 departmental approver |
| GRN | Goods Receipt Note (three-way matching) |
| PO | Purchase Order |
| MFA / TOTP | Multi-Factor Authentication / Time-based One-Time Password |
| RBAC / SoD | Role-Based Access Control / Separation of Duties |
| XAF | Central African CFA franc (default currency) |
| BROUILLON / SOUMIS / VALIDE / REJETE / PAYE / ARCHIVE | Invoice states: Draft / Submitted / Validated / Rejected / Paid / Archived |
| `ApiResponse<T>` | Standard response wrapper `{success, data, message, timestamp}` |
| OCR | Optical Character Recognition (Tess4J + PDFBox invoice field extraction) |

---

*End of dossier. All quantitative claims (538 backend tests / 0 failures; 66.31% instruction,
68.85% line, 51.98% branch coverage; 23 frontend test files / 80 cases; 3 e2e specs; library
versions; migration list V1–V42) were verified against the cited repository files without executing
anything. Items not recorded anywhere in the repository are marked "Not documented in repo".*
