# API — Endpoint Contract

> **Generated from the source code** (the `@*Mapping` + `@PreAuthorize` annotations of the 40
> controllers) — the code is the single source of truth (P6 decision **D6**, finding
> **AUDIT-041**). Do **not** hand-edit the tables below to match an intention: fix the controller
> and regenerate. The generator lives in the audit tooling; a CI-style consistency test
> (`ApiDocConsistencyTest`) fails if a path documented here no longer exists in the code.

**Base URL:** `/api/v1` &nbsp;·&nbsp; **Auth header:** `Authorization: Bearer {jwt}` &nbsp;·&nbsp; **Language:** `Accept-Language: fr|en`

All responses are wrapped in `ApiResponse<T>` (`{success, message, data, timestamp}`); error
messages are localized via `Accept-Language`.

## Roles & separation of duties (SoD)

The **Roles** column is the authorization actually enforced by `@PreAuthorize`. Read it literally:

- `Public` — no authentication. `Authenticated` — any logged-in user.
- `ROLE_X` — that role is granted access.
- _not ADMIN_, _not SUPPLIER_ — the endpoint is open to authenticated users **except** that role
  (e.g. `Authenticated, not SUPPLIER, not ADMIN` = every internal staff role).

> ⚠ **`ROLE_ADMIN` has NO financial access.** This is an audit criterion, not an oversight: the
> ADMIN is a *technical* administrator. Any endpoint carrying invoice, payment, matching, report or
> audit data excludes ADMIN (`not ADMIN`). A previous revision of this document wrongly granted
> ADMIN 15 financial surfaces — **the document was wrong, the code was right**. Never "align the
> code on the doc" by opening ADMIN access: that is a SoD regression (AUDIT-041, AUDIT-017).

---

## Authentication

| Method | Path | Roles |
|---|---|---|
| POST | `/api/v1/auth/forgot-password` | Public |
| POST | `/api/v1/auth/login` | Public |
| POST | `/api/v1/auth/mfa/confirm` | Authenticated |
| POST | `/api/v1/auth/mfa/setup` | Authenticated |
| POST | `/api/v1/auth/mfa/validate` | Public |
| POST | `/api/v1/auth/refresh` | Public |
| POST | `/api/v1/auth/register/supplier` | Public |
| POST | `/api/v1/auth/reset-password` | Public |
| GET | `/api/v1/auth/verify-email` | Public |

---

## User profile (self-service)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/profile` | Authenticated |
| PUT | `/api/v1/profile` | Authenticated |

---

## Users (administration)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/admin/sessions` | `ROLE_ADMIN` |
| DELETE | `/api/v1/admin/sessions/user/{userId}` | `ROLE_ADMIN` |
| GET | `/api/v1/roles` | `ROLE_ADMIN` |
| GET | `/api/v1/users` | `ROLE_ADMIN` |
| POST | `/api/v1/users` | `ROLE_ADMIN` |
| GET | `/api/v1/users/export` | `ROLE_ADMIN` |
| GET | `/api/v1/users/export/csv` | `ROLE_ADMIN` |
| POST | `/api/v1/users/import/csv` | `ROLE_ADMIN` |
| GET | `/api/v1/users/{id}` | `ROLE_ADMIN` |
| PUT | `/api/v1/users/{id}` | `ROLE_ADMIN` |
| PATCH | `/api/v1/users/{id}/activate` | `ROLE_ADMIN` |
| POST | `/api/v1/users/{id}/mfa/reset` | `ROLE_ADMIN` |
| PUT | `/api/v1/users/{id}/roles` | `ROLE_ADMIN` |
| POST | `/api/v1/users/{id}/unlock` | `ROLE_ADMIN` |

---

## Departments

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/admin/department-access` | `ROLE_ADMIN` |
| GET | `/api/v1/departments` | Authenticated |
| POST | `/api/v1/departments` | `ROLE_ADMIN` |
| GET | `/api/v1/departments/{id}` | Authenticated |
| PUT | `/api/v1/departments/{id}` | `ROLE_ADMIN` |
| PATCH | `/api/v1/departments/{id}/activate` | `ROLE_ADMIN` |

---

## Invoices

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/invoices` | Authenticated, _not SUPPLIER, not ADMIN_ |
| POST | `/api/v1/invoices` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/invoices/archive` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/invoices/duplicate-check` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_SUPPLIER` |
| GET | `/api/v1/invoices/export` | Authenticated, _not SUPPLIER, not ADMIN_ |
| POST | `/api/v1/invoices/import` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/invoices/pending-validation` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF`, `ROLE_VALIDATEUR_N1_COM`, `ROLE_VALIDATEUR_N1_DG`, `ROLE_VALIDATEUR_N1_DRH`, `ROLE_VALIDATEUR_N1_INFO`, `ROLE_VALIDATEUR_N1_INFRA`, `ROLE_VALIDATEUR_N1_QHSSE`, `ROLE_VALIDATEUR_N1_TECH`, `ROLE_VALIDATEUR_N1_TERM`, `ROLE_VALIDATEUR_N2_INFO`, `ROLE_VALIDATEUR_N2_INFRA`, `ROLE_VALIDATEUR_N2_TECH` |
| GET | `/api/v1/invoices/{id}` | Authenticated, _not SUPPLIER, not ADMIN_ |
| PUT | `/api/v1/invoices/{id}` | `ROLE_ASSISTANT_COMPTABLE` |
| DELETE | `/api/v1/invoices/{id}` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/invoices/{id}/export/pdf` | Authenticated, _not SUPPLIER, not ADMIN_ |
| GET | `/api/v1/invoices/{id}/history` | Authenticated, _not SUPPLIER, not ADMIN_ |
| GET | `/api/v1/invoices/{id}/matching` | Authenticated, _not SUPPLIER, not ADMIN_ |
| GET | `/api/v1/invoices/{id}/matching/export` | Authenticated, _not SUPPLIER, not ADMIN_ |
| POST | `/api/v1/invoices/{id}/matching/override` | `ROLE_DAF` |
| POST | `/api/v1/invoices/{id}/resubmit` | `ROLE_ASSISTANT_COMPTABLE` |
| PATCH | `/api/v1/invoices/{id}/sensitivity` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/invoices/{id}/submit` | `ROLE_ASSISTANT_COMPTABLE` |

---

## Invoice workflow (approval circuit)

| Method | Path | Roles |
|---|---|---|
| POST | `/api/v1/invoices/{invoiceId}/workflow/archive` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/invoices/{invoiceId}/workflow/assign` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF`, `ROLE_VALIDATEUR_N1_COM`, `ROLE_VALIDATEUR_N1_DG`, `ROLE_VALIDATEUR_N1_DRH`, `ROLE_VALIDATEUR_N1_INFO`, `ROLE_VALIDATEUR_N1_INFRA`, `ROLE_VALIDATEUR_N1_QHSSE`, `ROLE_VALIDATEUR_N1_TECH`, `ROLE_VALIDATEUR_N1_TERM`, `ROLE_VALIDATEUR_N2_INFO`, `ROLE_VALIDATEUR_N2_INFRA`, `ROLE_VALIDATEUR_N2_TECH` |
| POST | `/api/v1/invoices/{invoiceId}/workflow/assign-aa` | `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/invoices/{invoiceId}/workflow/bon-a-payer` | `ROLE_DAF` |
| POST | `/api/v1/invoices/{invoiceId}/workflow/reject` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF`, `ROLE_VALIDATEUR_N1_COM`, `ROLE_VALIDATEUR_N1_DG`, `ROLE_VALIDATEUR_N1_DRH`, `ROLE_VALIDATEUR_N1_INFO`, `ROLE_VALIDATEUR_N1_INFRA`, `ROLE_VALIDATEUR_N1_QHSSE`, `ROLE_VALIDATEUR_N1_TECH`, `ROLE_VALIDATEUR_N1_TERM`, `ROLE_VALIDATEUR_N2_INFO`, `ROLE_VALIDATEUR_N2_INFRA`, `ROLE_VALIDATEUR_N2_TECH` |
| GET | `/api/v1/invoices/{invoiceId}/workflow/rejection-reasons` | Authenticated, _not SUPPLIER_ |
| GET | `/api/v1/invoices/{invoiceId}/workflow/steps` | Authenticated, _not SUPPLIER, not ADMIN_ |
| POST | `/api/v1/invoices/{invoiceId}/workflow/validate-n1` | `ROLE_DAF`, `ROLE_VALIDATEUR_N1_COM`, `ROLE_VALIDATEUR_N1_DG`, `ROLE_VALIDATEUR_N1_DRH`, `ROLE_VALIDATEUR_N1_INFO`, `ROLE_VALIDATEUR_N1_INFRA`, `ROLE_VALIDATEUR_N1_QHSSE`, `ROLE_VALIDATEUR_N1_TECH`, `ROLE_VALIDATEUR_N1_TERM` |
| POST | `/api/v1/invoices/{invoiceId}/workflow/validate-n2` | `ROLE_VALIDATEUR_N2_INFO`, `ROLE_VALIDATEUR_N2_INFRA`, `ROLE_VALIDATEUR_N2_TECH` |
| GET | `/api/v1/workflow/my-stats` | Authenticated, _not SUPPLIER_ |

---

## Invoice documents

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/invoices/{invoiceId}/documents` | Authenticated, _not SUPPLIER, not ADMIN_ |
| POST | `/api/v1/invoices/{invoiceId}/documents` | `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/invoices/{invoiceId}/documents/bulk` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/invoices/{invoiceId}/documents/{docId}/download` | Authenticated, _not SUPPLIER, not ADMIN_ |

---

## Invoice checklist

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/checklist-templates` | `ROLE_ADMIN` |
| POST | `/api/v1/checklist-templates` | `ROLE_ADMIN` |
| GET | `/api/v1/checklist-templates/{id}` | `ROLE_ADMIN` |
| PUT | `/api/v1/checklist-templates/{id}` | `ROLE_ADMIN` |
| DELETE | `/api/v1/checklist-templates/{id}` | `ROLE_ADMIN` |
| GET | `/api/v1/invoices/{invoiceId}/checklist` | Authenticated, _not SUPPLIER, not ADMIN_ |
| POST | `/api/v1/invoices/{invoiceId}/checklist` | Authenticated, _not SUPPLIER, not ADMIN_ |

---

## Purchasing & three-way matching

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/goods-receipts` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/goods-receipts` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/goods-receipts/{id}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/matching` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/matching-config` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/matching-config` | `ROLE_DAF` |
| GET | `/api/v1/matching/{invoiceId}/lines` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/matching/{invoiceId}/lines/{poLineId}/resolve` | `ROLE_DAF` |
| GET | `/api/v1/purchase-orders` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/purchase-orders` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/purchase-orders/{id}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| PUT | `/api/v1/purchase-orders/{id}` | `ROLE_ASSISTANT_COMPTABLE` |
| DELETE | `/api/v1/purchase-orders/{id}` | `ROLE_ASSISTANT_COMPTABLE` |

---

## Payments

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/payment-alert-rules` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/payment-alert-rules` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| PUT | `/api/v1/payment-alert-rules/{id}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| DELETE | `/api/v1/payment-alert-rules/{id}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/payments` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/payments/batch` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/payments/export` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/payments/invoice/{invoiceId}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/payments/invoice/{invoiceId}` | `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/payments/{paymentId}/process` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/payments/{paymentId}/remittance` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |

---

## Reports

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/reports/activity` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/aging` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/aging/buckets` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/bottlenecks` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/budget-alerts` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/budget-vs-actual` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/cash-flow` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/definitions` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/reports/definitions` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| DELETE | `/api/v1/reports/definitions/{id}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/definitions/{id}/preview` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/definitions/{id}/run` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/executive-summary` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/export/excel` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/export/pdf/audit/{id}` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/export/pdf/compliance` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/kpis` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/payment-cycle` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/supplier/{supplierId}/payments` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/supplier/{supplierId}/performance` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/reports/volume-trend` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |

---

## Suppliers (internal referential)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/suppliers` | `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/suppliers` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/suppliers/export` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/suppliers/{id}` | `ROLE_ASSISTANT_COMPTABLE` |
| PUT | `/api/v1/suppliers/{id}` | `ROLE_ASSISTANT_COMPTABLE` |
| DELETE | `/api/v1/suppliers/{id}` | `ROLE_ASSISTANT_COMPTABLE` |
| PATCH | `/api/v1/suppliers/{id}/activate` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/suppliers/{id}/documents` | `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/suppliers/{id}/documents` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/suppliers/{id}/performance` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| PATCH | `/api/v1/suppliers/{id}/suspend` | `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/suppliers/{supplierId}/communications` | `ROLE_ADMIN`, `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/suppliers/{supplierId}/communications` | `ROLE_ADMIN`, `ROLE_ASSISTANT_COMPTABLE` |
| GET | `/api/v1/suppliers/{supplierId}/contracts` | `ROLE_ADMIN`, `ROLE_ASSISTANT_COMPTABLE` |
| POST | `/api/v1/suppliers/{supplierId}/contracts` | `ROLE_ADMIN`, `ROLE_ASSISTANT_COMPTABLE` |
| DELETE | `/api/v1/suppliers/{supplierId}/contracts/{contractId}` | `ROLE_ADMIN`, `ROLE_ASSISTANT_COMPTABLE` |

---

## Supplier portal (/supplier/*, ROLE_SUPPLIER)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/supplier/dashboard` | `ROLE_SUPPLIER` |
| GET | `/api/v1/supplier/documents` | `ROLE_SUPPLIER` |
| POST | `/api/v1/supplier/documents` | `ROLE_SUPPLIER` |
| GET | `/api/v1/supplier/invoices` | `ROLE_SUPPLIER` |
| POST | `/api/v1/supplier/invoices` | `ROLE_SUPPLIER` |
| POST | `/api/v1/supplier/invoices/{invoiceId}/documents` | `ROLE_SUPPLIER` |
| POST | `/api/v1/supplier/invoices/{invoiceId}/resubmit` | `ROLE_SUPPLIER` |
| POST | `/api/v1/supplier/invoices/{invoiceId}/submit` | `ROLE_SUPPLIER` |
| GET | `/api/v1/supplier/profile` | `ROLE_SUPPLIER` |
| PUT | `/api/v1/supplier/profile` | `ROLE_SUPPLIER` |
| GET | `/api/v1/supplier/purchase-orders` | `ROLE_SUPPLIER` |

---

## Delegations & escalation

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/approvals/delegations` | `ROLE_ADMIN` |
| POST | `/api/v1/approvals/delegations` | `ROLE_ADMIN` |
| GET | `/api/v1/approvals/delegations/eligible-delegatees` | `ROLE_APPROVER_ROLES` |
| GET | `/api/v1/approvals/delegations/mine` | `ROLE_APPROVER_ROLES` |
| POST | `/api/v1/approvals/delegations/mine` | `ROLE_APPROVER_ROLES` |
| DELETE | `/api/v1/approvals/delegations/mine/{id}` | `ROLE_APPROVER_ROLES` |
| DELETE | `/api/v1/approvals/delegations/{id}` | `ROLE_ADMIN` |
| GET | `/api/v1/escalation-rules` | `ROLE_ADMIN`, `ROLE_DAF` |
| POST | `/api/v1/escalation-rules` | `ROLE_ADMIN`, `ROLE_DAF` |
| PUT | `/api/v1/escalation-rules/{id}` | `ROLE_ADMIN`, `ROLE_DAF` |
| DELETE | `/api/v1/escalation-rules/{id}` | `ROLE_ADMIN`, `ROLE_DAF` |

---

## Access requests

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/access-requests` | `ROLE_ADMIN` |
| POST | `/api/v1/access-requests` | Authenticated, _not SUPPLIER_ |
| GET | `/api/v1/access-requests/mine` | Authenticated, _not SUPPLIER_ |
| PATCH | `/api/v1/access-requests/{id}` | `ROLE_ADMIN` |

---

## Announcements

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/announcements` | Authenticated |
| POST | `/api/v1/announcements` | `ROLE_ADMIN` |
| GET | `/api/v1/announcements/all` | `ROLE_ADMIN` |
| PUT | `/api/v1/announcements/{id}` | `ROLE_ADMIN` |
| DELETE | `/api/v1/announcements/{id}` | `ROLE_ADMIN` |
| PATCH | `/api/v1/announcements/{id}/active` | `ROLE_ADMIN` |

---

## Notifications

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/notifications` | Authenticated |
| PATCH | `/api/v1/notifications/read-all` | Authenticated |
| GET | `/api/v1/notifications/unread-count` | Authenticated |
| PATCH | `/api/v1/notifications/{id}/read` | Authenticated |

---

## Archiving & retention

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/archive/folders` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| POST | `/api/v1/archive/folders` | `ROLE_ADMIN` |
| PUT | `/api/v1/archive/folders/{id}` | `ROLE_ADMIN` |
| DELETE | `/api/v1/archive/folders/{id}` | `ROLE_ADMIN` |
| PATCH | `/api/v1/archive/invoices/{invoiceId}/folder` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_DAF` |
| GET | `/api/v1/retention-policy` | `ROLE_ADMIN` |
| PUT | `/api/v1/retention-policy` | `ROLE_ADMIN` |
| GET | `/api/v1/retention-policy/compliance` | `ROLE_ADMIN` |
| PUT | `/api/v1/retention/documents/{id}/disposition` | `ROLE_ADMIN`, `ROLE_DAF` |
| GET | `/api/v1/retention/pending-documents` | `ROLE_ADMIN`, `ROLE_DAF` |

---

## Compliance & backups

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/backups` | `ROLE_ADMIN` |
| POST | `/api/v1/backups` | `ROLE_ADMIN` |
| GET | `/api/v1/backups/audit-logs` | `ROLE_ADMIN` |
| POST | `/api/v1/backups/{filename}/restore` | `ROLE_ADMIN` |
| GET | `/api/v1/compliance/archive-report` | `ROLE_ADMIN` |
| GET | `/api/v1/compliance/backup-status` | `ROLE_ADMIN` |
| POST | `/api/v1/compliance/backup-status` | `ROLE_ADMIN` |
| GET | `/api/v1/compliance/calendar` | `ROLE_ADMIN` |
| POST | `/api/v1/compliance/calendar` | `ROLE_ADMIN` |
| PATCH | `/api/v1/compliance/calendar/{id}` | `ROLE_ADMIN` |
| DELETE | `/api/v1/compliance/calendar/{id}` | `ROLE_ADMIN` |
| GET | `/api/v1/compliance/checklist` | `ROLE_ADMIN` |
| POST | `/api/v1/compliance/checklist` | `ROLE_ADMIN` |
| PATCH | `/api/v1/compliance/checklist/{id}` | `ROLE_ADMIN` |
| DELETE | `/api/v1/compliance/checklist/{id}` | `ROLE_ADMIN` |
| GET | `/api/v1/compliance/incidents` | `ROLE_ADMIN` |
| POST | `/api/v1/compliance/incidents` | Authenticated, _not SUPPLIER_ |
| PATCH | `/api/v1/compliance/incidents/{id}/status` | `ROLE_ADMIN` |
| GET | `/api/v1/compliance/privacy-acceptance` | Authenticated |
| POST | `/api/v1/compliance/privacy-acceptance` | Authenticated |

---

## Audit log

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/audit-logs` | `ROLE_ADMIN`, `ROLE_DAF` |
| GET | `/api/v1/audit-logs/anomalies` | `ROLE_ADMIN` |
| GET | `/api/v1/audit-logs/export` | `ROLE_ADMIN`, `ROLE_DAF` |
| GET | `/api/v1/audit-logs/financial` | `ROLE_DAF` |
| GET | `/api/v1/audit-logs/summary/export` | `ROLE_ADMIN`, `ROLE_DAF` |
| GET | `/api/v1/audit-logs/summary/financial` | `ROLE_DAF` |
| GET | `/api/v1/audit-logs/summary/system` | `ROLE_ADMIN` |
| GET | `/api/v1/audit-logs/system` | `ROLE_ADMIN` |

---

## Integrations & webhooks

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/integrations/connectors` | `ROLE_ADMIN` |
| POST | `/api/v1/integrations/connectors` | `ROLE_ADMIN` |
| DELETE | `/api/v1/integrations/connectors/{id}` | `ROLE_ADMIN` |
| PATCH | `/api/v1/integrations/connectors/{id}/enabled` | `ROLE_ADMIN` |
| POST | `/api/v1/integrations/connectors/{id}/sync` | `ROLE_ADMIN` |
| PUT | `/api/v1/integrations/connectors/{id}/sync-schedule` | `ROLE_ADMIN` |
| POST | `/api/v1/integrations/connectors/{id}/test` | `ROLE_ADMIN` |
| GET | `/api/v1/integrations/status` | `ROLE_ADMIN` |
| GET | `/api/v1/integrations/webhooks` | `ROLE_ADMIN` |
| POST | `/api/v1/integrations/webhooks` | `ROLE_ADMIN` |
| DELETE | `/api/v1/integrations/webhooks/{id}` | `ROLE_ADMIN` |
| GET | `/api/v1/integrations/webhooks/{id}/deliveries` | `ROLE_ADMIN` |

---

## Security (administration)

| Method | Path | Roles |
|---|---|---|
| GET | `/api/v1/admin/security-health` | `ROLE_ADMIN` |
| GET | `/api/v1/admin/security-policy` | `ROLE_ADMIN` |
| PUT | `/api/v1/admin/security-policy` | `ROLE_ADMIN` |

---

## OCR

| Method | Path | Roles |
|---|---|---|
| POST | `/api/v1/ocr/extract` | `ROLE_ASSISTANT_COMPTABLE`, `ROLE_SUPPLIER` |

---

_Total: **221** endpoints across 40 controllers. Regenerate after any controller change._
