# Financial Audit Business-Content Readability — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the DAF financial audit journal show real business events (actor name+role, invoice reference, amount in XAF, supplier) instead of raw HTTP telemetry.

**Architecture:** Log business audit events from the single central workflow point `InvoiceStateMachineServiceImpl.sendEvent` (covers submit/validate/BAP/reject/payment/archive); enrich `AuditLogDTO` + `toDTO` with actor display name and role; render the four columns on the frontend with pure, testable helpers and a graceful fallback for the pre-existing HTTP rows.

**Tech Stack:** Spring Boot 3.4 / Java 21, Spring State Machine, React 19 + TypeScript, vitest, JUnit 5 + Mockito.

## Global Constraints

- Currency is **XAF** (Franc CFA BEAC) — never XOF.
- `ROLE_ADMIN` has **no** financial access; `/audit-logs/financial` stays **DAF-only** (do not open access).
- No Flyway migration, no schema change (reuse `action`, `entity_id`, `new_value`).
- Do not touch user files: `docs/QA_*.md`, `docs/SOD_*.md`, `scratch/`.
- i18n FR/EN strict parity; no object/string key collision; verify with a node script before commit.
- Do NOT declare an `ObjectMapper` bean in a shared `@TestConfiguration` (it pollutes the test context and breaks JSR-310 serialization in every `@SpringBootTest`). Build services by hand in tests.
- Run backend and frontend test suites SEPARATELY (never in parallel — CPU contention causes false failures).
- Commit only this feature's files. Message style: English, end with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

---

## File Structure

- **Modify** `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java` — add central business-audit logging after a successful transition.
- **Modify** `src/main/java/com/oct/invoicesystem/domain/audit/dto/AuditLogDTO.java` — add `userDisplayName`, `userRole`.
- **Modify** `src/main/java/com/oct/invoicesystem/domain/audit/service/AuditServiceImpl.java` — populate the new DTO fields in `toDTO`.
- **Create** `frontend/src/lib/auditFormat.ts` — pure helpers `formatAuditActor`, `formatAuditEntity`, `formatAuditDetails`.
- **Modify** `frontend/src/pages/FinancialAuditPage.tsx` — use the helpers in the table; extend `AuditLog` interface.
- **Modify** `frontend/src/i18n/fr.json` + `frontend/src/i18n/en.json` — add `audit.financial.entity.invoice`.
- **Test (create)** `src/test/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineAuditLoggingTest.java`
- **Test (modify)** `src/test/java/com/oct/invoicesystem/domain/audit/service/AuditServiceTest.java`
- **Test (create)** `frontend/src/lib/auditFormat.test.ts`

---

### Task 1: Central business-audit logging in `sendEvent`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineAuditLoggingTest.java`

**Interfaces:**
- Consumes: `AuditService.logAction(UUID userId, String entityType, String entityId, String action, Object oldValue, Object newValue, String ip, String ua)` (existing, `@Async @Transactional`).
- Produces: audit rows with `entityType="INVOICE"`, `entityId=referenceNumber`, `action` in {INVOICE_SUBMIT, APPROVE, BON_A_PAYER, REJECT, PAYMENT, ARCHIVE}, `newValue` = a `Map` `{amount, currency, supplier}`.

**Design notes for the implementer:**
- `AuditService` is NOT currently a dependency of `InvoiceStateMachineServiceImpl`. Add it as a `final` constructor field (the class uses an explicit constructor at lines 51–68 — add the parameter there and assign it).
- Add a private helper `businessActionFor(InvoiceEvent event)` returning the mapped action `String`, or `null` when the event is not a financial event.
- Add a private helper `financialDetails(Invoice invoice)` returning `Map<String,Object>` of `amount` (`invoice.getAmount()`), `currency` (`invoice.getCurrency()`), `supplier` (`invoice.getSupplier() != null ? invoice.getSupplier().getName() : invoice.getSupplierName()`).
- Resolve the actor id: reuse the same logic already present for `WorkflowExtendedStateKeys.USER_ID` — read it from the state machine extended state if present, else from `SecurityContextHolder`. To keep it simple and testable, extract a private helper `resolveActorId(StateMachine sm)` that returns the `UUID` from `sm.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.USER_ID)` (cast to UUID) or null.
- Call the logging AFTER the success check (`if (!accepted || !stateChanged) throw ...`), right before or after `publishNotificationEvent(...)` at line 137. Only log when `businessActionFor(event) != null`.
- Verify `Supplier` exposes `getName()`. If it exposes a different accessor (e.g. `getSupplierName`), use that; check the entity before writing the helper.

- [ ] **Step 1: Confirm the Supplier name accessor**

Run: `grep -nE "getName|private String name|companyName" src/main/java/com/oct/invoicesystem/domain/supplier/model/Supplier.java`
Expected: identify the correct getter for the supplier's display name; use it in `financialDetails`.

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineAuditLoggingTest.java`. This is a focused unit test that drives `sendEvent` through a mocked state machine so the transition "succeeds", then asserts the audit call. Use Mockito to mock all constructor dependencies.

```java
package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceStateChangeListener;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.purchasing.service.ThreeWayMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultExtendedState;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceStateMachineAuditLoggingTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock StateMachineFactory<InvoiceStatus, InvoiceEvent> factory;
    @Mock InvoiceStateChangeListener listener;
    @Mock ApplicationEventPublisher publisher;
    @Mock ThreeWayMatchingService matchingService;
    @Mock PurchaseOrderRepository poRepo;
    @Mock GoodsReceiptNoteRepository grnRepo;
    @Mock ThreeWayMatchingResultRepository matchingResultRepo;
    @Mock AuditService auditService;

    @Mock StateMachine<InvoiceStatus, InvoiceEvent> sm;
    @Mock State<InvoiceStatus, InvoiceEvent> state;

    private InvoiceStateMachineServiceImpl service;
    private Invoice invoice;
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvoiceStateMachineServiceImpl(
                invoiceRepository, factory, listener, publisher, matchingService,
                poRepo, grnRepo, matchingResultRepo, auditService);

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setReferenceNumber("FAC-2026-0042");
        invoice.setStatus(InvoiceStatus.BON_A_PAYER); // pre-payment state so RECORD_PAYMENT is plausible
        invoice.setAmount(new BigDecimal("850000"));
        invoice.setCurrency("XAF");
        invoice.setSupplierName("SOGARA");

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        // Make the state machine "accept" and change state.
        DefaultExtendedState ext = new DefaultExtendedState();
        ext.getVariables().put(
                com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys.USER_ID, actorId);
        when(factory.getStateMachine(anyString())).thenReturn(sm);
        when(sm.getExtendedState()).thenReturn(ext);
        when(sm.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);
        when(sm.getState()).thenReturn(state);
        when(state.getId()).thenReturn(InvoiceStatus.PAYE); // different from BON_A_PAYER -> stateChanged=true
    }

    @Test
    void sendEvent_recordPayment_logsPaymentBusinessAudit() {
        service.sendEvent(invoice.getId(), InvoiceEvent.RECORD_PAYMENT, null);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entityId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> newValue = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<UUID> uid = ArgumentCaptor.forClass(UUID.class);
        verify(auditService).logAction(uid.capture(), eq("INVOICE"), entityId.capture(),
                action.capture(), any(), newValue.capture(), any(), any());

        assertEquals("PAYMENT", action.getValue());
        assertEquals("FAC-2026-0042", entityId.getValue());
        assertEquals(actorId, uid.getValue());
        String details = newValue.getValue().toString();
        assertTrue(details.contains("850000"), details);
        assertTrue(details.contains("XAF"), details);
        assertTrue(details.contains("SOGARA"), details);
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw.cmd -q test -Dtest=InvoiceStateMachineAuditLoggingTest`
Expected: FAIL — the constructor does not yet accept `AuditService` (compile error) or `auditService.logAction` is never called.

- [ ] **Step 4: Implement — add AuditService dependency + logging**

In `InvoiceStateMachineServiceImpl`:
1. Add `private final AuditService auditService;` field and a constructor parameter (append to the existing explicit constructor signature and assignment).
2. Add the two mapping/detail helpers and the actor resolver:

```java
private String businessActionFor(InvoiceEvent event) {
    return switch (event) {
        case SUBMIT, RESUBMIT -> "INVOICE_SUBMIT";
        case VALIDATE_N1, VALIDATE_N2 -> "APPROVE";
        case BON_A_PAYER -> "BON_A_PAYER";
        case REJECT -> "REJECT";
        case RECORD_PAYMENT -> "PAYMENT";
        case ARCHIVE -> "ARCHIVE";
        default -> null;
    };
}

private java.util.Map<String, Object> financialDetails(Invoice invoice) {
    java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
    d.put("amount", invoice.getAmount());
    d.put("currency", invoice.getCurrency());
    String supplier = invoice.getSupplier() != null ? invoice.getSupplier().getName() : invoice.getSupplierName();
    d.put("supplier", supplier);
    return d;
}

private UUID resolveActorId(StateMachine<InvoiceStatus, InvoiceEvent> sm) {
    Object v = sm.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.USER_ID);
    return v instanceof UUID u ? u : null;
}
```

3. After the success check and before/after `publishNotificationEvent`, add:

```java
String businessAction = businessActionFor(event);
if (businessAction != null) {
    try {
        auditService.logAction(resolveActorId(sm), "INVOICE", invoice.getReferenceNumber(),
                businessAction, null, financialDetails(invoice), null, null);
    } catch (Exception ex) {
        log.warn("Failed to write business audit for invoice {} event {}: {}",
                invoiceId, event, ex.getMessage());
    }
}
```

(If `businessActionFor`/`resolveActorId` use `Invoice`/`InvoiceEvent` accessors that differ, adjust to the real names verified in Step 1.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw.cmd -q test -Dtest=InvoiceStateMachineAuditLoggingTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java \
        src/test/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineAuditLoggingTest.java
git commit -m "feat(audit): log business financial events from the central workflow transition

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Enrich `AuditLogDTO` with actor display name and role

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/dto/AuditLogDTO.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/service/AuditServiceImpl.java` (`toDTO`, ~line 182)
- Test: `src/test/java/com/oct/invoicesystem/domain/audit/service/AuditServiceTest.java`

**Interfaces:**
- Produces: `AuditLogDTO` now has `String userDisplayName` and `String userRole` (appended at the end of the record — existing positional constructor call in `toDTO` must be updated; no other constructor call exists elsewhere — confirm with grep).

**Design notes:**
- `User` getters: `getFirstName()`, `getLastName()`, `getUsername()`, `getUserRoles()` (`Set<UserRole>`, each `getRole().getName()` → e.g. `ROLE_DAF`).
- `userDisplayName`: if `lastName`/`firstName` present → `lastName + " " + firstName` (trim); else `username`; else `null`.
- `userRole`: first role name from `getUserRoles()`, stripped of the `ROLE_` prefix (e.g. `DAF`); `null` if none.
- Add two private static helpers in `AuditServiceImpl`: `displayName(User u)` and `primaryRole(User u)`.

- [ ] **Step 1: Confirm there is no other AuditLogDTO constructor call**

Run: `grep -rn "new AuditLogDTO(" src/main src/test`
Expected: only the one in `AuditServiceImpl.toDTO`. If tests build it directly, update them too.

- [ ] **Step 2: Write the failing test**

Add to `AuditServiceTest`:

```java
@Test
void toDTO_populatesActorDisplayNameAndRole() throws Exception {
    UUID userId = UUID.randomUUID();
    com.oct.invoicesystem.domain.user.model.Role role =
            new com.oct.invoicesystem.domain.user.model.Role();
    role.setName("ROLE_DAF");
    com.oct.invoicesystem.domain.user.model.UserRole ur =
            new com.oct.invoicesystem.domain.user.model.UserRole();
    ur.setRole(role);
    com.oct.invoicesystem.domain.user.model.User user =
            com.oct.invoicesystem.domain.user.model.User.builder()
                    .id(userId).firstName("Marie").lastName("Ndong").username("daf").build();
    user.getUserRoles().add(ur);

    com.oct.invoicesystem.domain.audit.model.AuditLog log =
            com.oct.invoicesystem.domain.audit.model.AuditLog.builder()
                    .id(UUID.randomUUID()).user(user)
                    .entityType("INVOICE").entityId("FAC-2026-0042").action("BON_A_PAYER").build();

    // toDTO is private; exercise it through the public search path with a mocked repo page.
    PageRequest pr = PageRequest.of(0, 10);
    when(auditLogRepository.findAll(any(Specification.class), eq(pr)))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(log)));

    AuditLogDTO dto = auditService.searchLogsWithActionFilter(
            null, null, null, null, java.util.List.of("BON_A_PAYER"), null, null, null, pr)
            .getContent().get(0);

    assertEquals("Ndong Marie", dto.userDisplayName());
    assertEquals("DAF", dto.userRole());
}
```

(Check `Role`, `UserRole` setters and `User.builder()` field names via grep before running; adjust construction to the real API if needed — e.g. if `UserRole` requires a `user` back-reference or uses a different setter.)

- [ ] **Step 3: Run the test to verify it fails**

Run: `./mvnw.cmd -q test -Dtest=AuditServiceTest#toDTO_populatesActorDisplayNameAndRole`
Expected: FAIL — `AuditLogDTO` has no `userDisplayName()` accessor (compile error).

- [ ] **Step 4: Implement**

In `AuditLogDTO`, append two components:

```java
public record AuditLogDTO(
        UUID id, UUID userId, String entityType, String entityId, String action,
        String oldValue, String newValue, String ipAddress, String userAgent, Instant createdAt,
        String userDisplayName, String userRole
) {}
```

In `AuditServiceImpl`, update `toDTO` to pass the two new values and add helpers:

```java
private static String displayName(com.oct.invoicesystem.domain.user.model.User u) {
    if (u == null) return null;
    String ln = u.getLastName();
    String fn = u.getFirstName();
    String full = ((ln == null ? "" : ln) + " " + (fn == null ? "" : fn)).trim();
    if (!full.isBlank()) return full;
    return u.getUsername();
}

private static String primaryRole(com.oct.invoicesystem.domain.user.model.User u) {
    if (u == null || u.getUserRoles() == null) return null;
    return u.getUserRoles().stream().findFirst()
            .map(ur -> ur.getRole().getName())
            .map(n -> n.startsWith("ROLE_") ? n.substring(5) : n)
            .orElse(null);
}
```

In the `new AuditLogDTO(...)` call, append `displayName(log.getUser()), primaryRole(log.getUser())`.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw.cmd -q test -Dtest=AuditServiceTest`
Expected: PASS (all methods).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/audit/dto/AuditLogDTO.java \
        src/main/java/com/oct/invoicesystem/domain/audit/service/AuditServiceImpl.java \
        src/test/java/com/oct/invoicesystem/domain/audit/service/AuditServiceTest.java
git commit -m "feat(audit): expose actor display name and primary role in AuditLogDTO

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Frontend pure formatting helpers

**Files:**
- Create: `frontend/src/lib/auditFormat.ts`
- Test: `frontend/src/lib/auditFormat.test.ts`

**Interfaces:**
- Consumes: `formatAmount` from `@/lib/format`.
- Produces:
  - `type AuditRow = { userId?: string; userDisplayName?: string; userRole?: string; ipAddress?: string; entityType?: string; entityId?: string; details?: string; newValue?: string }`
  - `formatAuditActor(row: AuditRow, systemLabel: string): { name: string; role?: string }`
  - `formatAuditEntity(row: AuditRow, invoiceLabel: (ref: string) => string): string`
  - `formatAuditDetails(row: AuditRow): string`

**Design notes:**
- `formatAuditActor`: if `userDisplayName` → `{ name: userDisplayName, role: userRole }`; else if `userId` → `{ name: '#' + userId.slice(0,8) }`; else `{ name: systemLabel }`.
- `formatAuditEntity`: read the raw entity id from `entityId`. If it looks like a URL (starts with `/`) → return the current fallback (`entityType` + '#' + first 8 chars). If it is a reference (non-empty, does not start with `/`) → `invoiceLabel(entityId)`. If empty → `entityType ?? '—'`.
- `formatAuditDetails`: parse `newValue` (then `details`) as JSON. If it has `amount`/`currency`/`supplier` → `` `${formatAmount(amount)} ${currency} · ${supplier}` `` (omit `· supplier` if supplier missing). If it has `method`/`status` → `` `${method} · ${status}` ``. Else return the raw string or `'—'`.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/lib/auditFormat.test.ts`:

```ts
import { describe, it, expect, vi } from 'vitest'

vi.mock('@/lib/format', () => ({ formatAmount: (n: number) => new Intl.NumberFormat('fr-FR').format(n) }))

import { formatAuditActor, formatAuditEntity, formatAuditDetails } from './auditFormat'

describe('formatAuditActor', () => {
  it('uses display name and role when present', () => {
    expect(formatAuditActor({ userDisplayName: 'Ndong Marie', userRole: 'DAF' }, 'Système'))
      .toEqual({ name: 'Ndong Marie', role: 'DAF' })
  })
  it('falls back to truncated uuid', () => {
    expect(formatAuditActor({ userId: '57858018-aaaa-bbbb' }, 'Système').name).toBe('#57858018')
  })
  it('shows system label when no actor', () => {
    expect(formatAuditActor({}, 'Système')).toEqual({ name: 'Système' })
  })
})

describe('formatAuditEntity', () => {
  const inv = (r: string) => `Facture ${r}`
  it('renders invoice reference', () => {
    expect(formatAuditEntity({ entityType: 'INVOICE', entityId: 'FAC-2026-0042' }, inv)).toBe('Facture FAC-2026-0042')
  })
  it('falls back for legacy URL entity', () => {
    expect(formatAuditEntity({ entityType: 'INVOICE', entityId: '/api/v1/invoices' }, inv)).toContain('INVOICE')
  })
})

describe('formatAuditDetails', () => {
  it('renders amount, currency and supplier for business rows', () => {
    const row = { newValue: JSON.stringify({ amount: 850000, currency: 'XAF', supplier: 'SOGARA' }) }
    const out = formatAuditDetails(row)
    expect(out).toContain('XAF')
    expect(out).toContain('SOGARA')
    expect(out).toContain('850')
  })
  it('renders method and status for legacy HTTP rows', () => {
    const row = { details: JSON.stringify({ duration_ms: 83, method: 'POST', status: 200 }) }
    expect(formatAuditDetails(row)).toBe('POST · 200')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && npx vitest run src/lib/auditFormat.test.ts`
Expected: FAIL — `./auditFormat` module does not exist.

- [ ] **Step 3: Implement `auditFormat.ts`**

```ts
import { formatAmount } from '@/lib/format'

export type AuditRow = {
  userId?: string
  userDisplayName?: string
  userRole?: string
  ipAddress?: string
  entityType?: string
  entityId?: string
  details?: string
  newValue?: string
}

export function formatAuditActor(row: AuditRow, systemLabel: string): { name: string; role?: string } {
  if (row.userDisplayName) return { name: row.userDisplayName, role: row.userRole }
  if (row.userId) return { name: '#' + row.userId.slice(0, 8) }
  return { name: systemLabel }
}

export function formatAuditEntity(row: AuditRow, invoiceLabel: (ref: string) => string): string {
  const id = row.entityId
  if (id && !id.startsWith('/')) return invoiceLabel(id)
  if (id && id.startsWith('/')) return `${row.entityType ?? ''}#${id.slice(0, 8)}`
  return row.entityType ?? '—'
}

function tryParse(s?: string): any | null {
  if (!s) return null
  try { return JSON.parse(s) } catch { return null }
}

export function formatAuditDetails(row: AuditRow): string {
  const biz = tryParse(row.newValue)
  if (biz && (biz.amount != null || biz.currency || biz.supplier)) {
    const money = `${formatAmount(biz.amount)} ${biz.currency ?? ''}`.trim()
    return biz.supplier ? `${money} · ${biz.supplier}` : money
  }
  const http = tryParse(row.details) ?? tryParse(row.newValue)
  if (http && (http.method || http.status != null)) {
    return `${http.method ?? ''} · ${http.status ?? ''}`.replace(/^ · | · $/g, '').trim()
  }
  return row.details ?? row.newValue ?? '—'
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && npx vitest run src/lib/auditFormat.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/auditFormat.ts frontend/src/lib/auditFormat.test.ts
git commit -m "feat(audit-ui): pure formatters for actor, entity and financial details

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Wire the helpers into `FinancialAuditPage` + i18n

**Files:**
- Modify: `frontend/src/pages/FinancialAuditPage.tsx`
- Modify: `frontend/src/i18n/fr.json`, `frontend/src/i18n/en.json`
- Verify: node parity script

**Interfaces:**
- Consumes: `formatAuditActor`, `formatAuditEntity`, `formatAuditDetails` from `@/lib/auditFormat`; `audit.financial.entity.invoice` i18n key.

**Design notes:**
- Extend the `AuditLog` interface with `userDisplayName?: string` and `userRole?: string`.
- Actor cell: `const actor = formatAuditActor(log, t('audit.systemActor', 'Système'))` → render `actor.name`, then `actor.role` (small, when present), then `ipAddress` below.
- Action cell: keep the existing translated badge (`t('audit.financial.event.' + log.action, log.action)`) — if not already translated, apply it now.
- Entity cell: `formatAuditEntity(log, (ref) => t('audit.financial.entity.invoice', { ref, defaultValue: 'Facture {{ref}}' }))`.
- Details cell: `formatAuditDetails(log)`.

- [ ] **Step 1: Add i18n keys (FR then EN)**

In `frontend/src/i18n/fr.json`, inside `audit.financial`, add an `entity` object:

```json
"entity": { "invoice": "Facture {{ref}}" }
```

In `frontend/src/i18n/en.json`, inside `audit.financial`:

```json
"entity": { "invoice": "Invoice {{ref}}" }
```

- [ ] **Step 2: Verify i18n parity + no collision**

Run:
```bash
cd frontend && node -e "const fr=require('./src/i18n/fr.json'),en=require('./src/i18n/en.json');const f=(o,p='')=>Object.entries(o).flatMap(([k,v])=>v&&typeof v==='object'&&!Array.isArray(v)?f(v,p+k+'.'):[[p+k,v]]);const fk=f(fr).map(x=>x[0]).sort(),ek=f(en).map(x=>x[0]).sort();console.log('FR',fk.length,'EN',ek.length,'onlyFR',JSON.stringify(fk.filter(k=>!ek.includes(k))),'onlyEN',JSON.stringify(ek.filter(k=>!fk.includes(k))))"
```
Expected: equal counts, empty `onlyFR`/`onlyEN`.

- [ ] **Step 3: Wire the helpers into the table**

Import the helpers and replace the actor/action/entity/details cell renderers with the helper-driven versions described in Design notes. Extend the `AuditLog` interface with the two new optional fields.

- [ ] **Step 4: Type-check + build + run frontend tests**

Run: `cd frontend && npx tsc --noEmit && npm run build && npm run test`
Expected: tsc 0 errors, build OK, all vitest pass (311 + auditFormat tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/FinancialAuditPage.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -m "feat(audit-ui): render readable actor, invoice ref and financial details

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Full gate + deploy + runtime verification

**Files:** none (verification only).

- [ ] **Step 1: Backend full suite**

Run: `./mvnw.cmd -q test` then aggregate the Maven summary line.
Expected: `Failures: 0, Errors: 0`, total ≥ previous 752 + new tests.

- [ ] **Step 2: Frontend full gate**

Run: `cd frontend && npx tsc --noEmit && npm run build && npm run test`
Expected: 0 tsc errors, build OK, all vitest pass.

- [ ] **Step 3: Deploy backend**

```bash
./mvnw.cmd -q -DskipTests package
docker cp target/invoice-system-1.0.0-SNAPSHOT.jar oct_backend:/app/app.jar
docker restart oct_backend
```
Wait until `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health` returns 200 (~30–45s).

- [ ] **Step 4: Deploy frontend**

```bash
cd frontend && npm run build
docker cp dist/. oct_frontend:/usr/share/nginx/html/
docker exec oct_frontend nginx -s reload
```

- [ ] **Step 5: Runtime verification (Playwright)**

Log in as `daf` (`Test1234!`). Trigger a real financial event (e.g. mark an invoice `bon à payer`, or record a payment). Navigate to `/financial-audit` and confirm the new row shows: actor **name + role**, **invoice reference**, **amount in XAF + supplier** — no HTTP telemetry. Confirm the noise toggle and event-type filter still work, and old HTTP rows still render without crashing.

- [ ] **Step 6: Final commit if any doc/status update remains, then propose grouped ff-only merge of the Lot 1→9 chain to `main` (only with the user's explicit go-ahead).**

---

## Self-Review

**Spec coverage:**
- Central logging in `sendEvent` → Task 1. ✓
- DTO + `toDTO` enrichment → Task 2. ✓
- Frontend rendering of 4 columns + fallback + pure helpers → Tasks 3–4. ✓
- i18n parity → Task 4. ✓
- Tests per change (backend ×2, frontend ×1 helper suite) → Tasks 1–3. ✓
- Gate + deploy + runtime + SoD unchanged + history kept → Task 5 + Global Constraints. ✓

**Placeholder scan:** No TBD/TODO; all steps carry real code or exact commands.

**Type consistency:** `AuditLogDTO` gains `userDisplayName`/`userRole` (Task 2) consumed by the frontend `AuditLog` interface and `AuditRow` (Tasks 3–4). Helper names `formatAuditActor/Entity/Details` match between Task 3 (definition) and Task 4 (usage). Action strings {INVOICE_SUBMIT, APPROVE, BON_A_PAYER, REJECT, PAYMENT, ARCHIVE} match `FINANCIAL_ACTIONS`.

**Verification-before-writing caveats folded in:** Steps 1 of Tasks 1 & 2 verify real accessors (Supplier name, Role/UserRole API, single DTO constructor call) before writing code, so the plan's code adapts to the actual entity API.
