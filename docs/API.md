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
| GET | `/payments` | DAF, AUDITEUR, ADMIN | List all payments (paginated) |

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

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/audit-logs` | AUDITEUR, ADMIN | Paginated audit log |
| GET | `/audit-logs?userId={id}` | AUDITEUR, ADMIN | Filter by user |
| GET | `/audit-logs?entityId={invoiceId}` | AUDITEUR, ADMIN | All actions on one invoice |
| GET | `/audit-logs?action=APPROVE` | AUDITEUR, ADMIN | Filter by action type |

---

## Reports

| Method | Path | Roles | Description |
|---|---|---|---|
| GET | `/reports/kpi` | DAF, AUDITEUR, ADMIN | KPI dashboard data |
| GET | `/reports/export?format=xlsx` | DAF, AUDITEUR, ADMIN | Export filtered invoices to Excel |
| GET | `/reports/export?format=pdf` | DAF, AUDITEUR, ADMIN | Export compliance report PDF |
| GET | `/invoices/{id}/export?format=pdf` | DAF, AUDITEUR, ADMIN | Export single invoice audit PDF |

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
