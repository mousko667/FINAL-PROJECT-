# TESTING — Strategy & Coverage Requirements

---

## 1. Testing Philosophy

> "A feature is not done until its tests pass."

Every piece of logic must have a corresponding test. Tests are not optional and are not added after the fact. Write tests alongside the code, ideally before (TDD where practical).

**The testing pyramid for this project:**
```
         [E2E Tests]          ← Playwright: full user journeys (Phase 8)
       [Integration Tests]    ← MockMvc + Testcontainers: controller + DB
     [Unit Tests]             ← JUnit 5 + Mockito: service + validation logic
```

---

## 2. Test Stack

| Tool | Purpose |
|---|---|
| JUnit 5 | Test runner |
| Mockito | Mocking dependencies in unit tests |
| AssertJ | Fluent assertions (`assertThat(...).isEqualTo(...)`) |
| MockMvc | HTTP integration tests without a running server |
| Testcontainers | Real PostgreSQL container for integration tests |
| Spring Security Test | `@WithMockUser`, security slice tests |
| MailHog (Docker) | Capture outgoing emails in dev/test |
| React Testing Library | Frontend component tests |
| Vitest | Frontend unit test runner |
| Playwright | End-to-end browser tests |

---

## 3. Unit Test Requirements

### InvoiceValidationService — must test ALL rules
```java
// From docs/WORKFLOW.md §8 — every rule needs a test
✅ Rule 1: Invoice cannot be submitted without at least one document
✅ Rule 2: Rejection reason is mandatory (min 10 chars)
✅ Rule 3: Approver cannot approve their own submitted invoice
✅ Rule 4: Resubmission requires version > version at rejection
✅ Rule 5: DAF approves all invoices (role check)
✅ Rule 6: Only ASSISTANT_COMPTABLE can create and submit
✅ Rule 7: Archive is automatic (no manual trigger allowed from outside)
✅ Rule 8: No hard delete — soft delete enforced
✅ Rule 9: Amounts stored as BigDecimal, no floating point
✅ Rule 10: Reference format validates FAC-{YYYY}-{NNNNN}
```

### InvoiceStateMachineService — must test ALL transitions
```java
// Valid transitions
✅ BROUILLON → SOUMIS (submit)
✅ SOUMIS → EN_VALIDATION_N1 (assign_reviewer)
✅ EN_VALIDATION_N1 → EN_VALIDATION_N2 (validate_n1, 2-level dept)
✅ EN_VALIDATION_N1 → VALIDE (validate_n1, 1-level dept)
✅ EN_VALIDATION_N2 → VALIDE (validate_n2)
✅ VALIDE → BON_A_PAYER (bon_a_payer)
✅ BON_A_PAYER → PAYE (record_payment)
✅ PAYE → ARCHIVE (automatic)
✅ EN_VALIDATION_N1 → REJETE (reject)
✅ EN_VALIDATION_N2 → REJETE (reject)
✅ VALIDE → REJETE (reject by DAF)
✅ REJETE → SOUMIS (resubmit after modification)

// Invalid transitions (must throw WorkflowException)
✅ BROUILLON → EN_VALIDATION_N1 (skipped submit)
✅ SOUMIS → VALIDE (skipped review)
✅ ARCHIVE → SOUMIS (can't un-archive)
✅ PAYE → REJETE (can't reject a paid invoice)
✅ REJETE → BON_A_PAYER (skipped resubmission)

// Guard failures (must throw appropriate exception)
✅ N1 validator from wrong department → 403
✅ N2 validator trying to validate single-level dept → 403
✅ reject without reason → ValidationException
✅ resubmit without modification → WorkflowException
```

### ApprovalService — must test
```java
✅ createApprovalStep: creates step with correct order and deadline
✅ recordDecision: updates step status + timestamps
✅ isAllStepsComplete: returns false if any step pending
✅ getDepartmentApprovalChain: returns correct chain for each of 9 departments
✅ checkOverdueSteps: identifies steps past deadline correctly
```

---

## 4. Integration Test Requirements

### Every controller endpoint needs:
1. ✅ **Happy path** — correct role, valid data → expected status + response shape
2. ✅ **Wrong role** — unauthorized role → 403
3. ✅ **Unauthenticated** — no token → 401
4. ✅ **Not found** — invalid ID → 404
5. ✅ **Invalid input** — bad request body → 400 with field errors

### Example coverage for InvoiceController:
```
POST   /api/v1/invoices              → 201 (ASSISTANT_COMPTABLE)
POST   /api/v1/invoices              → 403 (AUDITEUR)
POST   /api/v1/invoices              → 401 (no token)
POST   /api/v1/invoices              → 400 (missing required fields)
GET    /api/v1/invoices              → 200 paginated (all authenticated roles)
GET    /api/v1/invoices/{id}         → 200 (role with access)
GET    /api/v1/invoices/{id}         → 404 (unknown ID)
PUT    /api/v1/invoices/{id}         → 200 (ASSISTANT_COMPTABLE, BROUILLON state)
PUT    /api/v1/invoices/{id}         → 409 (invoice not in BROUILLON — wrong state)
DELETE /api/v1/invoices/{id}         → 200 (soft delete, ASSISTANT_COMPTABLE)
POST   /api/v1/invoices/{id}/submit  → 200 (ASSISTANT_COMPTABLE, has document)
POST   /api/v1/invoices/{id}/submit  → 400 (no document attached)
```

---

## 5. Lifecycle Integration Tests (Critical)

These are the most important tests — they verify the entire BAP process end-to-end.

### Test: Single-Level Approval Lifecycle
```
1. Admin creates ASSISTANT_COMPTABLE user + VALIDATEUR_N1_DRH user + DAF user
2. ASSISTANT_COMPTABLE creates invoice for DRH department
3. ASSISTANT_COMPTABLE uploads document
4. ASSISTANT_COMPTABLE submits invoice → status: SOUMIS
5. VALIDATEUR_N1_DRH assigns self → status: EN_VALIDATION_N1
6. VALIDATEUR_N1_DRH validates → status: VALIDE (DRH is single-level)
7. DAF issues BON_A_PAYER → status: BON_A_PAYER
8. ASSISTANT_COMPTABLE records payment → status: PAYE
9. Assert: status is ARCHIVE (automatic)
10. Assert: invoice_status_history has 7 entries
11. Assert: audit_log has entries for every action
12. Assert: notifications were persisted for each transition
```

### Test: Two-Level Approval Lifecycle (Informatique)
```
1. Create users: ASSISTANT_COMPTABLE, VALIDATEUR_N1_INFO (RSI), VALIDATEUR_N2_INFO (DSI), DAF
2. ASSISTANT_COMPTABLE creates + submits invoice for INFO department
3. VALIDATEUR_N1_INFO assigns self → EN_VALIDATION_N1
4. VALIDATEUR_N1_INFO validates → EN_VALIDATION_N2 (not VALIDE — 2-level!)
5. VALIDATEUR_N2_INFO validates → VALIDE
6. DAF issues BON_A_PAYER → BON_A_PAYER
7. ASSISTANT_COMPTABLE records payment → PAYE → ARCHIVE
8. Assert: approval_steps has 3 records (N1 + N2 + DAF)
```

### Test: Rejection + Resubmission Flow
```
1. ASSISTANT_COMPTABLE creates + submits invoice
2. VALIDATEUR_N1 assigns + rejects with reason "Montant incorrect, veuillez corriger"
3. Assert: status is REJETE
4. Assert: ASSISTANT_COMPTABLE received notification
5. ASSISTANT_COMPTABLE updates invoice (amount corrected → version increments)
6. ASSISTANT_COMPTABLE resubmits → status: SOUMIS
7. Approval continues to completion
```

---

## 6. Security Tests (Required for Every Protected Endpoint)

Run the full role matrix test — every endpoint × every role:

```java
@ParameterizedTest
@MethodSource("unauthorizedRolesForSubmit")
void submitInvoice_unauthorizedRole_returns403(String role) throws Exception {
    mockMvc.perform(post("/api/v1/invoices/{id}/submit", invoiceId)
            .with(user("test").roles(role)))
        .andExpect(status().isForbidden());
}

static Stream<String> unauthorizedRolesForSubmit() {
    return Stream.of("VALIDATEUR_N1_DRH", "VALIDATEUR_N2_INFO", "DAF", "AUDITEUR");
}
```

---

## 7. Frontend Tests

### Component Tests (React Testing Library)
```
✅ InvoiceStatusBadge: renders correct color and label for each status
✅ InvoiceActionPanel: shows correct actions for ASSISTANT_COMPTABLE in SOUMIS state
✅ InvoiceActionPanel: shows validate/reject for VALIDATEUR_N1 in EN_VALIDATION_N1 state
✅ InvoiceActionPanel: shows bon-a-payer only for DAF in VALIDE state
✅ RoleGuard: hides children when user lacks required role
✅ ProtectedRoute: redirects to login when unauthenticated
✅ InvoiceTimeline: renders all status history entries in order
```

### API Hook Tests (Vitest + Mock Service Worker)
```
✅ useInvoices: returns paginated data, handles loading/error states
✅ useAuth: handles login, stores token, handles 401 refresh
✅ useWebSocket: connects to STOMP, receives notification, updates store
```

---

## 8. Test Data Factories

Create a `TestDataFactory` class (backend) and `fixtures/` folder (frontend) with reusable test objects:

```java
// TestDataFactory.java
public class TestDataFactory {
    
    public static Invoice buildDraftInvoice(Department dept) {
        return Invoice.builder()
            .id(UUID.randomUUID())
            .referenceNumber("FAC-2026-00001")
            .status(InvoiceStatus.BROUILLON)
            .department(dept)
            .amount(new BigDecimal("450000.00"))
            .currency("XAF")
            .issueDate(LocalDate.now())
            .dueDate(LocalDate.now().plusDays(30))
            .version(0)
            .build();
    }
    
    public static Department buildDrhDepartment() { ... }
    public static Department buildInfoDepartment() { ... }  // requires_n2 = true
    public static User buildAssistantComptable() { ... }
    public static User buildValidateurN1(Department dept) { ... }
}
```

---

## 9. CI Test Command

```bash
# Run all tests (must pass before any PR merge or phase completion)
./mvnw test

# Run only unit tests
./mvnw test -Dgroups="unit"

# Run only integration tests (requires Docker for Testcontainers)
./mvnw test -Dgroups="integration"

# Coverage report
./mvnw test jacoco:report
# Report at: target/site/jacoco/index.html

# Frontend tests
cd frontend && npm test
```

---

## 10. Coverage Thresholds (enforced by JaCoCo)

```xml
<!-- pom.xml JaCoCo config -->
<configuration>
    <rules>
        <rule>
            <limits>
                <limit>
                    <counter>LINE</counter>
                    <value>COVEREDRATIO</value>
                    <minimum>0.80</minimum>  <!-- 80% line coverage minimum -->
                </limit>
                <limit>
                    <counter>BRANCH</counter>
                    <value>COVEREDRATIO</value>
                    <minimum>0.75</minimum>  <!-- 75% branch coverage minimum -->
                </limit>
            </limits>
        </rule>
    </rules>
    <excludes>
        <exclude>**/dto/**</exclude>     <!-- DTOs don't need coverage -->
        <exclude>**/config/**</exclude>  <!-- Config classes excluded -->
        <exclude>**/model/**</exclude>   <!-- Entities excluded -->
    </excludes>
</configuration>
```
