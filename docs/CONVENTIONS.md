# CONVENTIONS — Code Style, Naming & Best Practices

---

## 1. Java / Spring Boot Conventions

### Naming
```java
// Classes — PascalCase
InvoiceService, ApprovalController, InvoiceStatusHistory

// Methods — camelCase, verb-first
submitInvoice(), findByDepartmentAndStatus(), validateAndTransition()

// Constants — UPPER_SNAKE_CASE in dedicated class
RoleConstants.ROLE_ASSISTANT_COMPTABLE
StatusConstants.BROUILLON

// DTOs — suffix DTO, Request, Response
InvoiceDTO, InvoiceCreateRequest, InvoiceSummaryDTO, ApprovalDecisionRequest

// Repositories — suffix Repository
InvoiceRepository, ApprovalStepRepository

// Services — suffix Service
InvoiceService, InvoiceValidationService

// Controllers — suffix Controller
InvoiceController, ApprovalController
```

### Entity Rules
```java
@Entity
@Table(name = "invoices")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version                          // optimistic locking — always on Invoice
    private Integer version;

    @Column(nullable = false, unique = true)
    private String referenceNumber;   // FAC-2026-00041

    @Enumerated(EnumType.STRING)      // always STRING, never ORDINAL
    private InvoiceStatus status;

    @Column(name = "deleted_at")      // soft delete — never @Where
    private LocalDateTime deletedAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

### Service Method Template
```java
/**
 * Submits an invoice for review, transitioning it from BROUILLON to SOUMIS.
 *
 * @param invoiceId the invoice to submit
 * @param currentUser the authenticated user performing the action
 * @return the updated invoice DTO
 * @throws ResourceNotFoundException if invoice does not exist
 * @throws WorkflowException if invoice is not in BROUILLON state
 * @throws ValidationException if invoice fails business rule validation
 */
@Transactional
public InvoiceDTO submitInvoice(UUID invoiceId, UserDetails currentUser) {
    Invoice invoice = findInvoiceOrThrow(invoiceId);
    invoiceValidationService.validateForSubmission(invoice);
    invoiceStateMachineService.sendEvent(invoice, InvoiceEvent.SUBMIT, currentUser);
    eventPublisher.publishEvent(new InvoiceSubmittedEvent(invoice, currentUser));
    return invoiceMapper.toDTO(invoice);
}
```

### Exception Handling
```java
// Always use specific exceptions — never throw raw RuntimeException
throw new ResourceNotFoundException("invoice", invoiceId);
throw new WorkflowException("workflow.invalid_transition", fromStatus, toStatus);
throw new UnauthorizedException("error.access_denied");
throw new ValidationException("validation.document_required");

// GlobalExceptionHandler maps these to HTTP responses:
// ResourceNotFoundException   → 404
// WorkflowException           → 409 Conflict
// UnauthorizedException       → 403 Forbidden
// ValidationException         → 400 Bad Request
// MethodArgumentNotValidException → 400 (Bean Validation)
```

### Logging
```java
@Slf4j                      // always use Lombok @Slf4j
public class InvoiceService {
    
    public InvoiceDTO submitInvoice(...) {
        log.info("Submitting invoice {} by user {}", invoiceId, currentUser.getUsername());
        // ...
        log.debug("State transition: {} → {}", fromStatus, toStatus);
        // NEVER: log.info("Bank details: {}", bankDetails);  ← sensitive data
    }
}
```

---

## 2. Database Conventions

### Table Names
```sql
-- snake_case, plural
invoices, invoice_items, invoice_documents,
approval_steps, invoice_status_history,
departments, users, roles, user_roles,
payments, audit_logs, notifications
```

### Column Names
```sql
-- snake_case
id, reference_number, invoice_id (FK), department_id (FK),
status, created_at, updated_at, deleted_at,
created_by, updated_by (Spring Data Auditing)
```

### Flyway Migration Naming
```
V1__create_users_roles.sql
V2__create_departments.sql
V3__seed_roles_and_admin.sql
V4__create_invoices.sql
V5__create_invoice_items.sql
...
```

### Every Table Must Have
```sql
id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

### Financial Tables Also Need
```sql
deleted_at  TIMESTAMPTZ  -- soft delete
```

---

## 3. REST API Conventions

### URL Patterns
```
GET    /api/v1/invoices                     → list (paginated)
POST   /api/v1/invoices                     → create
GET    /api/v1/invoices/{id}                → get one
PUT    /api/v1/invoices/{id}                → update
DELETE /api/v1/invoices/{id}                → soft delete

POST   /api/v1/invoices/{id}/submit         → workflow action
POST   /api/v1/invoices/{id}/validate-n1    → workflow action
POST   /api/v1/invoices/{id}/reject         → workflow action (body: reason)

GET    /api/v1/invoices/{id}/documents      → list documents
POST   /api/v1/invoices/{id}/documents      → upload document
GET    /api/v1/invoices/{id}/documents/{docId}/download → pre-signed URL
```

### Response Envelope
```json
// Success
{
  "success": true,
  "data": { ... },
  "message": null,
  "timestamp": "2026-03-21T10:30:00"
}

// Error
{
  "success": false,
  "data": null,
  "message": "Facture introuvable",
  "errors": [
    { "field": "rejectionReason", "message": "Le motif de rejet est obligatoire" }
  ],
  "timestamp": "2026-03-21T10:30:00"
}
```

### Pagination Query Params
```
?page=0&size=20&sort=createdAt,desc
?status=SOUMIS&department=INFO&from=2026-01-01&to=2026-03-31
```

---

## 4. Testing Conventions

### Test Class Naming
```
InvoiceServiceTest          → unit test for InvoiceService
InvoiceControllerTest       → integration test for InvoiceController
InvoiceValidationServiceTest → unit test
FullLifecycleIntegrationTest → end-to-end lifecycle test
```

### Test Structure (Arrange / Act / Assert)
```java
@Test
@DisplayName("submitInvoice: should throw ValidationException when no document attached")
void submitInvoice_noDocument_throwsValidationException() {
    // Arrange
    Invoice invoice = buildDraftInvoice();
    invoice.setDocuments(Collections.emptyList());
    when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
    
    // Act & Assert
    assertThatThrownBy(() -> invoiceService.submitInvoice(invoice.getId(), mockUser))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("document_required");
}
```

### Integration Test Setup
```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional                              // roll back after each test
@ActiveProfiles("test")
@Testcontainers                             // real PostgreSQL
class InvoiceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void createInvoice_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/invoices")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.referenceNumber").value(startsWith("FAC-")));
    }
}
```

### Minimum Test Coverage
| Layer | Minimum |
|---|---|
| Service (business logic) | 90% branch coverage |
| Controller (integration) | All endpoints × all valid roles |
| State machine | Every valid transition + every invalid transition |
| Validation | Every business rule (happy + failure) |
| Security | Every role-protected endpoint tested with wrong role → 403 |

---

## 5. Frontend Conventions (React + TypeScript)

### File Naming
```
PascalCase.tsx           → React components
camelCase.ts             → hooks, utils, API files
camelCase.test.tsx       → tests
fr.json / en.json        → translations
```

### Component Structure
```tsx
// Every component: types → hooks → render only
interface InvoiceStatusBadgeProps {
  status: InvoiceStatus;
}

export function InvoiceStatusBadge({ status }: InvoiceStatusBadgeProps) {
  const { t } = useTranslation();
  const colorMap: Record<InvoiceStatus, string> = { ... };
  
  return (
    <span className={cn('rounded-full px-2 py-1 text-xs font-medium', colorMap[status])}>
      {t(`invoice.status.${status.toLowerCase()}`)}
    </span>
  );
}
```

### API Hook Pattern (React Query)
```typescript
// Always: loading + error states handled in the hook, not in the component
export function useInvoices(filters: InvoiceFilters) {
  return useQuery({
    queryKey: ['invoices', filters],
    queryFn: () => invoicesApi.list(filters),
    staleTime: 30_000,
  });
}
```

### Translation Key Convention
```json
// fr.json
{
  "invoice": {
    "status": {
      "brouillon": "Brouillon",
      "soumis": "Soumis",
      "en_validation_n1": "En validation N1",
      "en_validation_n2": "En validation N2",
      "valide": "Validé",
      "bon_a_payer": "Bon à Payer",
      "paye": "Payé",
      "archive": "Archivé",
      "rejete": "Rejeté"
    },
    "action": {
      "submit": "Soumettre",
      "validate": "Valider",
      "reject": "Rejeter",
      "bon_a_payer": "Émettre Bon à Payer"
    }
  }
}
```

---

## 6. Git Commit Convention

```
feat(invoice): add submit endpoint with validation guard
fix(workflow): correct N2 transition guard for INFRA department
test(approval): add integration test for two-level lifecycle
docs(api): update endpoint table for reject action
refactor(service): extract reference number generation to utility
chore(deps): upgrade spring-boot to 3.4.1
```

Format: `type(scope): short description`  
Types: `feat, fix, test, docs, refactor, chore, style, perf`
