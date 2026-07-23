# API — Complete Endpoint Contract

**Base URL:** `/api/v1`  
**Auth header:** `Authorization: Bearer {jwt_token}`  
**Language header:** `Accept-Language: fr` or `Accept-Language: en`  

---

## Authentication

| Method | Path | Body | Roles | Response |
|---|---|---|---|---|
| POST | `/auth/login` | `{username, password}` | Public | `{accessToken, refreshToken, expiresIn, user}` |
| POST | `/auth/refresh` | `{refreshToken}` | Public | `{accessToken, expiresIn}` |
| POST | `/auth/logout` | `{refreshToken}` | Authenticated | 200 |
| GET | `/auth/me` | — | Authenticated | `UserDTO` |

---

## Users (Admin only)

| Method | Path | Description |
|---|---|---|
| GET | `/users` | List all users (paginated) |
| POST | `/users` | Create user |
| GET | `/users/{id}` | Get user detail |
| PUT | `/users/{id}` | Update user |
| PATCH | `/users/{id}/activate` | Activate/deactivate user |
| PUT | `/users/{id}/roles` | Assign roles to user |

---

## Departments (Admin only for write)

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/departments` | All authenticated | List all OCT departments |
| GET | `/departments/{id}` | All authenticated | Get department detail + approval chain |
| PUT | `/departments/{id}` | ADMIN | Update approval config |

---

## Invoices

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/invoices` | All | List (paginated, filtered) |
| POST | `/invoices` | ASSISTANT_COMPTABLE, ADMIN | Create draft invoice |
| GET | `/invoices/{id}` | All | Full invoice detail |
| PUT | `/invoices/{id}` | ASSISTANT_COMPTABLE, ADMIN | Update (BROUILLON or REJETE only) |
| DELETE | `/invoices/{id}` | ASSISTANT_COMPTABLE, ADMIN | Soft delete (BROUILLON only) |
| GET | `/invoices/{id}/history` | All except supplier | Status transition history |

### Invoice Filters (GET /invoices)
```
?status=SOUMIS
?department=INFO
?from=2026-01-01&to=2026-03-31
?reference=FAC-2026
?page=0&size=20&sort=createdAt,desc
```

### InvoiceCreateRequest body
```json
{
  "supplierName": "ACME Gabon",
  "supplierEmail": "factures@acme-gabon.com",
  "supplierTaxId": "GA-12345",
  "departmentId": "uuid-of-department",
  "amount": 450000.00,
  "currency": "XAF",
  "issueDate": "2026-03-15",
  "dueDate": "2026-04-15",
  "description": "Fournitures informatiques - Mars 2026",
  "items": [
    {
      "description": "Écran 27 pouces",
      "quantity": 2,
      "unitPrice": 175000.00
    },
    {
      "description": "Clavier + Souris",
      "quantity": 5,
      "unitPrice": 20000.00
    }
  ]
}
```

---

## Workflow Actions

| Method | Path | Actor Role | Guard | Description |
|---|---|---|---|---|
| POST | `/invoices/{id}/submit` | ASSISTANT_COMPTABLE | Has document + all fields | BROUILLON → SOUMIS |
| POST | `/invoices/{id}/assign-reviewer` | VALIDATEUR_N1_{DEPT} | Dept matches | SOUMIS → EN_VALIDATION_N1 |
| POST | `/invoices/{id}/validate-n1` | VALIDATEUR_N1_{DEPT} | Assigned as reviewer | EN_VALIDATION_N1 → EN_VALIDATION_N2 or VALIDE |
| POST | `/invoices/{id}/assign-reviewer-n2` | VALIDATEUR_N2_{DEPT} | 2-level dept | EN_VALIDATION_N2 assignment |
| POST | `/invoices/{id}/validate-n2` | VALIDATEUR_N2_{DEPT} | Assigned + 2-level dept | EN_VALIDATION_N2 → VALIDE |
| POST | `/invoices/{id}/bon-a-payer` | DAF, ADMIN | Status = VALIDE | VALIDE → BON_A_PAYER |
| POST | `/invoices/{id}/reject` | N1, N2, DAF, ADMIN | Reason required | Any review state → REJETE |
| POST | `/invoices/{id}/resubmit` | ASSISTANT_COMPTABLE | Modified since rejection | REJETE → SOUMIS |
| POST | `/invoices/{id}/workflow/archive` | ASSISTANT_COMPTABLE, DAF | Status = PAYE | PAYE → ARCHIVE |

> Archiving is an **explicit** action since AUDIT-030: recording a payment leaves the invoice in
> `PAYE`. ADMIN is deliberately excluded (no financial access).

### Reject body (reason is mandatory)
```json
{
  "reason": "Le montant ne correspond pas au bon de commande. Veuillez corriger."
}
```

### Validate body (comment optional)
```json
{
  "comment": "Facture conforme, approuvée."
}
```

---

## Documents

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/invoices/{id}/documents` | ASSISTANT_COMPTABLE, ADMIN | Upload file (multipart, max 10MB) |
| GET | `/invoices/{id}/documents` | All | List attached documents |
| GET | `/invoices/{id}/documents/{docId}/download` | All | Get pre-signed MinIO URL (15 min expiry) |
| DELETE | `/invoices/{id}/documents/{docId}` | ASSISTANT_COMPTABLE, ADMIN | Remove document (BROUILLON only) |

### Accepted MIME types
`application/pdf`, `image/png`, `image/jpeg`, `image/tiff`

---

## Payments

| Method | Path | Roles | Description |
|---|---|---|---|
| POST | `/invoices/{id}/payment` | ASSISTANT_COMPTABLE, ADMIN | Record payment (BON_A_PAYER only) |
| GET | `/invoices/{id}/payment` | All except N1/N2 | Get payment details |
| GET | `/payments` | DAF, ADMIN, ASSISTANT_COMPTABLE | List all payments (paginated) |

### PaymentRequest body
```json
{
  "amount": 450000.00,
  "currency": "XAF",
  "paymentDate": "2026-03-21",
  "paymentMethod": "VIREMENT",
  "paymentReference": "VIR-2026-03-0041",
  "notes": "Virement effectué via BGFI Bank"
}
```

---

## Notifications

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/notifications` | Authenticated | List my notifications (paginated) |
| PATCH | `/notifications/{id}/read` | Authenticated | Mark one as read |
| PATCH | `/notifications/read-all` | Authenticated | Mark all as read |
| GET | `/notifications/unread-count` | Authenticated | Count of unread |

### WebSocket topic (STOMP)
```
Subscribe: /user/{userId}/notifications
Message format: { "id": "...", "title": "...", "message": "...", "type": "...", "invoiceId": "..." }
```

---

## Audit

The audit log is split by access level (see `OCT_System_Briefing.md §8 Module 10`):
- **Financial audit trail** (invoices, approvals, payments) → CFO (DAF) only
- **System/security audit trail** (logins, role changes, integrations) → Administrator only

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/audit-logs` | DAF, ADMIN | Paginated audit log (DAF sees financial events; ADMIN sees system/security events) |
| GET | `/audit-logs?userId={id}` | DAF, ADMIN | Filter by user |
| GET | `/audit-logs?entityId={invoiceId}` | DAF, ADMIN | All actions on one invoice |
| GET | `/audit-logs?action=APPROVE` | DAF, ADMIN | Filter by action type |

---

## Reports

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/reports/kpi` | DAF, ADMIN, ASSISTANT_COMPTABLE | KPI dashboard data |
| GET | `/reports/export?format=xlsx` | DAF, ADMIN, ASSISTANT_COMPTABLE | Export filtered invoices to Excel |
| GET | `/reports/export?format=pdf` | DAF, ADMIN | Export compliance report PDF |
| GET | `/invoices/{id}/export?format=pdf` | DAF, ADMIN | Export single invoice audit PDF |

### KPI Response shape
```json
{
  "totalInvoicesThisMonth": 148,
  "totalValueProcessed": 42000000,
  "currency": "XAF",
  "avgProcessingDays": 3.2,
  "pendingApproval": 17,
  "rejectionRate": 0.081,
  "overdueCount": 4,
  "volumeByStatus": {
    "BROUILLON": 5,
    "SOUMIS": 8,
    "EN_VALIDATION_N1": 7,
    "EN_VALIDATION_N2": 2,
    "VALIDE": 3,
    "BON_A_PAYER": 4,
    "PAYE": 112,
    "ARCHIVE": 876,
    "REJETE": 12
  },
  "topDepartmentsByVolume": [
    { "department": "Terminal", "count": 45, "totalValue": 15000000 }
  ]
}
```

---

## Standard Error Codes

| HTTP | Code | Message key |
|---|---|---|
| 400 | VALIDATION_ERROR | `error.validation` |
| 400 | DOCUMENT_REQUIRED | `error.invoice.document_required` |
| 400 | REJECTION_REASON_REQUIRED | `error.workflow.rejection_reason_required` |
| 401 | UNAUTHORIZED | `error.auth.unauthorized` |
| 403 | FORBIDDEN | `error.auth.forbidden` |
| 404 | INVOICE_NOT_FOUND | `error.invoice.not_found` |
| 404 | USER_NOT_FOUND | `error.user.not_found` |
| 409 | INVALID_TRANSITION | `error.workflow.invalid_transition` |
| 409 | DUPLICATE_REFERENCE | `error.invoice.duplicate_reference` |
| 409 | VERSION_CONFLICT | `error.invoice.version_conflict` |
| 413 | FILE_TOO_LARGE | `error.document.file_too_large` |
| 415 | UNSUPPORTED_MIME | `error.document.unsupported_mime` |
| 500 | INTERNAL_ERROR | `error.internal` |


---

## Supplier Management

The Accounting Assistant manages the full supplier lifecycle (onboarding, updates, activation, deactivation) per `OCT_System_Briefing.md §5.2`.

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/suppliers` | All authenticated | List suppliers (paginated, filterable by name, taxId, status) |
| POST | `/suppliers` | ASSISTANT_COMPTABLE, ADMIN | Create supplier profile (AA manages supplier onboarding) |
| GET | `/suppliers/{id}` | All authenticated | Get supplier detail |
| PUT | `/suppliers/{id}` | ASSISTANT_COMPTABLE, ADMIN | Update supplier info |
| PATCH | `/suppliers/{id}/activate` | ASSISTANT_COMPTABLE, ADMIN | Activate supplier (PENDING_VERIFICATION → ACTIVE) |
| PATCH | `/suppliers/{id}/suspend` | ASSISTANT_COMPTABLE, ADMIN | Suspend supplier (ACTIVE → SUSPENDED) |
| DELETE | `/suppliers/{id}` | ASSISTANT_COMPTABLE, ADMIN | Soft delete supplier |
| POST | `/suppliers/{id}/documents` | ASSISTANT_COMPTABLE, ADMIN | Upload supplier document (tax certificate, contract) |
| GET | `/suppliers/{id}/documents` | ASSISTANT_COMPTABLE, ADMIN, DAF | List supplier documents |

---

## Supplier Portal (ROLE_SUPPLIER only)

| Method | Path | Description |
|---|---|---|
| GET | `/supplier/dashboard` | Counts by status, last payment date, pending actions |
| GET | `/supplier/invoices` | Own invoices only (paginated) |
| POST | `/supplier/invoices` | Submit new invoice |
| GET | `/supplier/profile` | Own supplier profile |
| PUT | `/supplier/profile` | Update own profile |
| POST | `/supplier/documents` | Upload own tax certificate or contract |

---

## Authentication Additions

| Method | Path | Body | Roles | Description |
|---|---|---|---|---|
| POST | `/auth/register/supplier` | `{companyName, taxId, email, password}` | Public | Supplier self-registration |
| GET | `/auth/verify-email` | `?token=` | Public | Email verification |
| POST | `/auth/mfa/setup` | — | Authenticated | Generate TOTP secret + QR code URL |
| POST | `/auth/mfa/confirm` | `{otp}` | Authenticated | Confirm MFA setup with first valid OTP |
| POST | `/auth/mfa/validate` | `{preAuthToken, otp}` | Public | Complete MFA login, returns full JWT |
| POST | `/users/{id}/unlock` | — | ADMIN | Reset failed attempts and clear account lock |

### MFA setup response shape
```json
{
  "qrCodeUrl": "otpauth://totp/OCT:username?secret=BASE32SECRET&issuer=OCT",
  "secret": "BASE32SECRET"
}
```

### Login response when MFA required
```json
{
  "mfa_required": true,
  "pre_auth_token": "eyJ..."
}
```

### Login response when MFA setup required
```json
{
  "mfa_setup_required": true
}
```

---

## Purchase Orders

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/purchase-orders` | ADMIN, ASSISTANT_COMPTABLE, DAF | List POs (paginated) |
| POST | `/purchase-orders` | ADMIN, ASSISTANT_COMPTABLE | Create PO with line items |
| GET | `/purchase-orders/{id}` | All authenticated | PO detail with line items |
| PUT | `/purchase-orders/{id}` | ADMIN, ASSISTANT_COMPTABLE | Update PO (OPEN status only) |

### PurchaseOrderCreateRequest body
```json
{
  "poNumber": "PO-2026-0041",
  "supplierId": "uuid",
  "departmentId": "uuid",
  "currency": "XAF",
  "issueDate": "2026-04-01",
  "expiryDate": "2026-12-31",
  "items": [
    {
      "description": "Serveur Dell PowerEdge",
      "quantity": 2,
      "unitPrice": 850000.00
    }
  ]
}
```

---

## Three-Way Matching

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/invoices/{id}/matching` | All authenticated | Get matching result for invoice |
| POST | `/invoices/{id}/matching/override` | ASSISTANT_COMPTABLE, DAF, ADMIN | Override MISMATCH with mandatory written justification (recorded permanently in audit trail) |
| GET | `/matching-config` | ADMIN | Get current tolerance configuration |
| PUT | `/matching-config` | ADMIN | Update tolerance thresholds |

### Override body
```json
{
  "reason": "Variance approved by procurement director per memo 2026-04-12"
}
```

### MatchingConfig body
```json
{
  "tolerancePercent": 2.00,
  "toleranceAmount": 5000.00,
  "requireGrn": true
}
```

---

## Webhooks / Integration

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/integrations/webhooks` | ADMIN | List all webhooks |
| POST | `/integrations/webhooks` | ADMIN | Register new webhook |
| DELETE | `/integrations/webhooks/{id}` | ADMIN | Deactivate webhook (soft) |
| GET | `/integrations/webhooks/{id}/deliveries` | ADMIN | Delivery log (paginated) |
| GET | `/integrations/status` | ADMIN | All webhook health with last delivery status |

### WebhookCreateRequest body
```json
{
  "name": "SAP Integration",
  "url": "https://erp.oct.ga/invoices/webhook",
  "events": ["INVOICE_SUBMITTED", "INVOICE_APPROVED", "INVOICE_REJECTED", "INVOICE_PAID"]
}
```

### WebhookCreateResponse (secret shown once only)
```json
{
  "id": "uuid",
  "name": "SAP Integration",
  "url": "https://erp.oct.ga/invoices/webhook",
  "secret": "raw-secret-shown-once",
  "events": ["INVOICE_SUBMITTED", "INVOICE_APPROVED", "INVOICE_REJECTED", "INVOICE_PAID"],
  "isActive": true
}
```

### Webhook payload delivered to registered URL
```json
{
  "event": "INVOICE_SUBMITTED",
  "timestamp": "2026-04-13T10:30:00Z",
  "invoiceId": "uuid",
  "referenceNumber": "FAC-2026-00041",
  "supplierId": "uuid",
  "amount": 450000.00,
  "currency": "XAF",
  "status": "SOUMIS"
}
```

---

## Payment Enhancements

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/payments/{id}/remittance` | ASSISTANT_COMPTABLE, DAF, ADMIN, SUPPLIER (own only) | Pre-signed URL for remittance advice PDF |
| GET | `/reports/aging` | DAF, ADMIN, ASSISTANT_COMPTABLE | Aging analysis by overdue bucket |
| GET | `/reports/cash-flow` | DAF, ADMIN, ASSISTANT_COMPTABLE | Cash flow projection (`?days=30`) |
| GET | `/reports/supplier/{supplierId}/payments` | DAF, ADMIN, ASSISTANT_COMPTABLE | Full payment history per supplier |
| GET | `/reports/supplier/{supplierId}/performance` | DAF, ADMIN, ASSISTANT_COMPTABLE | Invoice accuracy + rejection rate |
| GET | `/reports/bottlenecks` | DAF, ADMIN, ASSISTANT_COMPTABLE | Average approval duration per step type and department |

### Aging response shape
```json
{
  "asOf": "2026-04-13",
  "buckets": {
    "0_30":   { "count": 5,  "totalValue": 2500000 },
    "31_60":  { "count": 3,  "totalValue": 1800000 },
    "61_90":  { "count": 1,  "totalValue": 450000  },
    "90_plus":{ "count": 2,  "totalValue": 3200000 }
  },
  "currency": "XAF"
}
```

### Cash flow response shape
```json
{
  "projectionDays": 30,
  "currency": "XAF",
  "weeklyProjection": [
    { "weekStart": "2026-04-14", "weekEnd": "2026-04-20", "totalDue": 1500000 },
    { "weekStart": "2026-04-21", "weekEnd": "2026-04-27", "totalDue": 3200000 }
  ]
}
```

### Bottleneck response shape
```json
{
  "steps": [
    {
      "departmentCode": "INFO",
      "stepOrder": 1,
      "stepType": "N1",
      "averageDays": 4.2,
      "slaThresholdDays": 3,
      "isBottleneck": true,
      "sampleSize": 14
    }
  ]
}
```

---

## Standard Error Codes (additions)

| HTTP | Code | Message key |
|---|---|---|
| 409 | MATCHING_MISMATCH | `error.matching.mismatch` |
| 409 | MATCHING_OVERRIDE_REQUIRED | `error.matching.override_required` |
| 423 | ACCOUNT_LOCKED | `error.auth.account_locked` |
| 401 | MFA_REQUIRED | `error.auth.mfa_required` |
| 400 | MFA_SETUP_REQUIRED | `error.auth.mfa_setup_required` |
| 400 | INVALID_OTP | `error.auth.invalid_otp` |
| 400 | EMAIL_NOT_VERIFIED | `error.auth.email_not_verified` |
| 404 | SUPPLIER_NOT_FOUND | `error.supplier.not_found` |
| 409 | SUPPLIER_INACTIVE | `error.supplier.inactive` |
| 404 | PURCHASE_ORDER_NOT_FOUND | `error.po.not_found` |
| 409 | PO_NOT_OPEN | `error.po.not_open` |