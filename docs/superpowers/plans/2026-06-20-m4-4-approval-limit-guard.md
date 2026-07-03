# M4 #4 — Approval-Limit Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Block an approver from validating an invoice whose amount exceeds the approver's `approvalLimit`, at the N1 and N2 steps; the DAF (Bon à Payer) step is never blocked.

**Architecture:** Add one private guard method `ensureWithinApprovalLimit(User, Invoice)` to `ApprovalServiceImpl`, called from `validateN1` and `validateN2` (alongside the existing `ensureNotSubmitter`). No state-machine change, no migration, no new endpoint, no frontend change. Escalation is organisational (the guard refuses an under-limit approver; a higher approver — ultimately the unlimited DAF — acts).

**Tech Stack:** Spring Boot 3, JUnit 5, Mockito, Java BigDecimal.

## Global Constraints

- `approvalLimit == null` ⇒ unlimited (guard passes). Copied verbatim from spec §2.
- Comparison is strict `<`: `approvalLimit == amount` is **allowed**.
- Amount compared = `Invoice.amount` (single amount field on the model; no HT/TTC split).
- DAF step (`bonAPayer`) is **never** subject to the guard.
- Error message is **generic** (no amounts exposed), thrown as `WorkflowException("approval.limit.exceeded")`.
- i18n files: `src/main/resources/i18n/messages_fr.properties` (ISO-8859-1, add via `iconv -f UTF-8 -t ISO-8859-1`, no em-dash/curly quotes) and `messages_en.properties` (ASCII).
- Boolean/Lombok, no JPA entity exposure, `@PreAuthorize`, `GlobalExceptionHandler` — existing project rules (CLAUDE.md §3) already satisfied; this change touches only a service method.
- Completion gate (“0 échec”): `./mvnw test` + (from `frontend/`) `npx tsc --noEmit` + `npx vitest run` all green before declaring done.

---

### Task 1: Approval-limit guard at N1/N2, DAF exempt

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java`
- Modify: `src/main/resources/i18n/messages_fr.properties`
- Modify: `src/main/resources/i18n/messages_en.properties`
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceTest.java`

**Interfaces:**
- Consumes: existing `ApprovalServiceImpl.validateN1(UUID, String)`, `validateN2(UUID, String)`, `bonAPayer(UUID, String)`; `User.getApprovalLimit() : BigDecimal`; `Invoice.getAmount() : BigDecimal`; `WorkflowException(String)`.
- Produces: private `void ensureWithinApprovalLimit(User approver, Invoice invoice)` (internal; no external caller relies on it).

- [ ] **Step 1: Write the failing tests**

Add these six tests to `ApprovalServiceTest.java`. They follow the existing setup style (`mockSecurityContext`, `invoiceRepository.findByIdAndDeletedAtIsNull`, `approvalStepRepository` mocks). Each sets `invoice.setAmount(...)` and `currentUser.setApprovalLimit(...)`. `submittedBy` stays null so `ensureNotSubmitter` never trips first.

```java
@Test
void validateN1_NullLimit_IsUnlimited_Success() {
    invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
    invoice.setAmount(new java.math.BigDecimal("5000000"));
    mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
    currentUser.setApprovalLimit(null); // unlimited
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
    ApprovalStep step = new ApprovalStep();
    step.setStatus(ApprovalStepStatus.PENDING);
    when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 1)).thenReturn(Optional.of(step));
    when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

    approvalService.validateN1(invoice.getId(), "ok");

    assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
    verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.VALIDATE_N1), anyMap());
}

@Test
void validateN1_LimitAboveAmount_Success() {
    invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
    invoice.setAmount(new java.math.BigDecimal("1000000"));
    mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
    currentUser.setApprovalLimit(new java.math.BigDecimal("2000000"));
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
    ApprovalStep step = new ApprovalStep();
    when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 1)).thenReturn(Optional.of(step));
    when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

    approvalService.validateN1(invoice.getId(), "ok");

    assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
}

@Test
void validateN1_LimitEqualsAmount_Success() {
    invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
    invoice.setAmount(new java.math.BigDecimal("1000000"));
    mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
    currentUser.setApprovalLimit(new java.math.BigDecimal("1000000")); // equal => allowed (strict <)
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
    ApprovalStep step = new ApprovalStep();
    when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 1)).thenReturn(Optional.of(step));
    when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

    approvalService.validateN1(invoice.getId(), "ok");

    assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
}

@Test
void validateN1_LimitBelowAmount_ThrowsAndDoesNotAdvance() {
    invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
    invoice.setAmount(new java.math.BigDecimal("5000000"));
    mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
    currentUser.setApprovalLimit(new java.math.BigDecimal("1000000"));
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

    WorkflowException ex = assertThrows(WorkflowException.class,
            () -> approvalService.validateN1(invoice.getId(), "ok"));
    assertEquals("approval.limit.exceeded", ex.getMessage());
    verify(invoiceStateMachineService, never()).sendEvent(any(), eq(InvoiceEvent.VALIDATE_N1), anyMap());
}

@Test
void validateN2_LimitBelowAmount_Throws() {
    invoice.setStatus(InvoiceStatus.EN_VALIDATION_N2);
    invoice.setAmount(new java.math.BigDecimal("5000000"));
    mockSecurityContext("ROLE_VALIDATEUR_N2_INFO");
    currentUser.setApprovalLimit(new java.math.BigDecimal("1000000"));
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

    WorkflowException ex = assertThrows(WorkflowException.class,
            () -> approvalService.validateN2(invoice.getId(), "ok"));
    assertEquals("approval.limit.exceeded", ex.getMessage());
}

@Test
void bonAPayer_DafLimitBelowAmount_StillSucceeds() {
    invoice.setStatus(InvoiceStatus.VALIDE);
    invoice.setAmount(new java.math.BigDecimal("5000000"));
    mockSecurityContext("ROLE_DAF");
    currentUser.setApprovalLimit(new java.math.BigDecimal("1000000")); // below amount, but DAF is exempt
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
    ApprovalStep step = new ApprovalStep();
    when(approvalStepRepository.findByInvoiceIdAndStepOrder(invoice.getId(), 3)).thenReturn(Optional.of(step));
    when(approvalStepRepository.save(any(ApprovalStep.class))).thenAnswer(i -> i.getArguments()[0]);

    approvalService.bonAPayer(invoice.getId(), "BAP OK");

    assertEquals(ApprovalStepStatus.APPROVED, step.getStatus());
    verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.BON_A_PAYER), anyMap());
}
```

Add the static import if not already present: `import static org.mockito.Mockito.never;` (it is covered by the existing wildcard `import static org.mockito.Mockito.*;` — verify before adding).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw -q -Dtest=ApprovalServiceTest test`
Expected: the 4 "success" tests likely PASS already (no guard yet), the two below-limit tests (`validateN1_LimitBelowAmount_...`, `validateN2_LimitBelowAmount_...`) FAIL — no exception thrown / wrong message. This confirms the guard is not yet present.

- [ ] **Step 3: Add the guard method and wire it into N1/N2**

In `ApprovalServiceImpl.java`, add the private method (place it next to `ensureNotSubmitter`):

```java
private void ensureWithinApprovalLimit(User approver, Invoice invoice) {
    java.math.BigDecimal limit = approver.getApprovalLimit();
    if (limit == null) {
        return; // null = unlimited
    }
    if (invoice.getAmount() != null && limit.compareTo(invoice.getAmount()) < 0) {
        throw new WorkflowException("approval.limit.exceeded");
    }
}
```

In `validateN1(...)`, after the existing `ensureNotSubmitter(invoice, currentUser);` line, add:

```java
ensureWithinApprovalLimit(currentUser, invoice);
```

In `validateN2(...)`, after its `ensureNotSubmitter(invoice, currentUser);` line, add:

```java
ensureWithinApprovalLimit(currentUser, invoice);
```

Do **NOT** add the call in `bonAPayer(...)` — the DAF is exempt by design.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw -q -Dtest=ApprovalServiceTest test`
Expected: all six new tests + all pre-existing `ApprovalServiceTest` tests PASS.

- [ ] **Step 5: Add the bilingual i18n key**

EN (`src/main/resources/i18n/messages_en.properties`) — append a plain ASCII line:

```
approval.limit.exceeded=This invoice amount exceeds your approval limit.
```

FR (`src/main/resources/i18n/messages_fr.properties`) — append via iconv (ISO-8859-1), no em-dash/curly quotes:

```bash
printf "approval.limit.exceeded=Le montant de cette facture depasse votre limite d'approbation.\n" \
  | iconv -f UTF-8 -t ISO-8859-1 >> src/main/resources/i18n/messages_fr.properties
```

(The EN line can be appended with the Edit tool directly. For FR, use the iconv command above so accents are not corrupted — though this exact string is ASCII-only, keep the iconv habit per the project rule.)

- [ ] **Step 6: Run the full backend suite + frontend checks (completion gate)**

Run: `./mvnw test`
Expected: BUILD SUCCESS, 0 failures.

Run (from `frontend/`): `npx tsc --noEmit`
Expected: 0 errors.

Run (from `frontend/`): `npx vitest run`
Expected: all green (46/46 baseline; no front change so unchanged).

- [ ] **Step 7: Flip the compliance matrix**

In `docs/COMPLIANCE_MATRIX.md`, change M4 UI #4 and M4 Features #4 from 🟠 to ✅ with an evidence line, e.g.:

> | 4 | Threshold-based approval routing | ✅ | Garde de limite d'approbation : `validateN1`/`validateN2` refusent (`approval.limit.exceeded`) si `approvalLimit < amount` ; DAF exempt ; `null`=illimité. Tests `ApprovalServiceTest`. |

Mirror the same evidence for Features #4.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java \
        src/test/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceTest.java \
        src/main/resources/i18n/messages_en.properties \
        src/main/resources/i18n/messages_fr.properties \
        docs/COMPLIANCE_MATRIX.md
git commit -m "feat(m4-4): amount-threshold approval-limit guard at N1/N2 (DAF exempt)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

Stage **only** these files — do not stage CLAUDE.md, docs/REMEDIATION_PROMPT.md, docs/REQUIREMENTS-MATRIX.md, docs/SCOPE.md, or docs/audit/ (user's prior-session work).

---

## Notes for the implementer

- `WorkflowException` messages are returned **raw** by `GlobalExceptionHandler` (no `MessageSource` resolution server-side). The frontend resolves the key via `t(key)` (project pattern, CLAUDE.md §13 / PROB-006). That is why the thrown message is the i18n **key** `approval.limit.exceeded`, matching the surrounding code's habit and the spec.
- No integration test is added: there is no existing service-level integration test for approval that exercises a real `MessageSource`/HTTP round-trip for these methods, and the unit tests fully cover the guard logic. If an approval controller integration test exists and is trivially extendable, an optional case may be added, but it is not required by this plan.
- This is a single-task plan; no cross-task interface drift to reconcile.
