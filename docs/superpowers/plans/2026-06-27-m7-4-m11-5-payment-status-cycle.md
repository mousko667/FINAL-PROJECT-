# M7 #4 + M11 #5 — Statut paiement SCHEDULED/PROCESSED + rapport cycle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduire un statut de paiement SCHEDULED/PROCESSED (paiement planifié vs exécuté) sans toucher la state machine BAP, plus un rapport « cycle de paiement » mesurant les délais par étape.

**Architecture:** Statut additif sur l'entité `Payment` (enum `PaymentStatus`). Un paiement naît PROCESSED par défaut (rétro-compatible) ; opt-in `scheduled=true` le crée en SCHEDULED sans finaliser. La finalisation (remittance + event + transitions PAYE/ARCHIVE) est extraite dans `finalizePayment(...)`, appelée à la création PROCESSED directe et via le nouvel endpoint `POST /payments/{id}/process`. Le rapport réutilise `InvoiceStatusHistory` (même mécanisme que processing-time/bottlenecks).

**Tech Stack:** Spring Boot 3.4 · Java 21 · PostgreSQL · Flyway · JPA · React 19 + TS · TanStack Query · i18next · JUnit/Mockito · vitest.

## Global Constraints

- Réponses API toujours dans `ApiResponse<T>` ; `@PreAuthorize` sur chaque méthode controller.
- SoD : ADMIN **sans** accès financier → rapport `payment-cycle` réservé `DAF` + `ASSISTANT_COMPTABLE`.
- DTO uniquement aux endpoints (jamais l'entité JPA).
- Bilingue FR/EN ; `messages_fr.properties` = **ISO-8859-1** (ajouter via iconv, sans em-dash ni quotes courbes) ; `messages_en.properties` = UTF-8 ; parité fr.json/en.json côté front.
- Javadoc sur méthodes service publiques ; tests happy + ≥2 edge + endpoint avec rôle ; `@Operation` Swagger.
- Flyway : nouvelle migration **V35** (next contigu) ; ne jamais modifier une migration appliquée (PROB-009).
- Lombok boolean : pas de `boolean isXxx` (PROB-003) — ici on utilise un enum, pas de booléen entité.
- Gate avant chaque commit : `./mvnw test` 0 échec + `npm run test` (vitest) 0 échec + `tsc` 0 erreur.
- 1 tâche = 1 commit atomique. Push dès 10 commits non poussés.
- Branche : `chore/sanitize-docs-migrations`.

---

### Task 1: Enum PaymentStatus + champs entité + migration V35

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/payment/model/PaymentStatus.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/model/Payment.java`
- Create: `src/main/resources/db/migration/V35__add_payment_status.sql`

**Interfaces:**
- Consumes: rien.
- Produces: enum `PaymentStatus { SCHEDULED, PROCESSED }` ; `Payment.getStatus()/setStatus(PaymentStatus)` ; `Payment.getProcessedDate()/setProcessedDate(Instant)` ; champ builder `status`, `processedDate`.

- [ ] **Step 1: Créer l'enum**

```java
package com.oct.invoicesystem.domain.payment.model;

/**
 * Cycle de vie d'un paiement.
 * SCHEDULED  : paiement planifié, pas encore exécuté (la facture reste BON_A_PAYER).
 * PROCESSED  : paiement exécuté (déclenche la finalisation : remittance + PAYE + ARCHIVE).
 */
public enum PaymentStatus {
    SCHEDULED,
    PROCESSED
}
```

- [ ] **Step 2: Ajouter les champs à `Payment`**

Dans `Payment.java`, après le champ `reference` (autour de la ligne 47), ajouter :

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "processed_date")
    private Instant processedDate;
```

(`Instant` et `EnumType` sont déjà importés.)

- [ ] **Step 3: Écrire la migration V35**

```sql
-- M7 #4 : statut de paiement (SCHEDULED/PROCESSED) + date d'execution reelle.
-- DEFAULT 'PROCESSED' rend l'historique existant coherent (paiements deja executes).
ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED';
ALTER TABLE payments ADD COLUMN processed_date TIMESTAMP;
UPDATE payments SET processed_date = payment_date WHERE processed_date IS NULL;
```

- [ ] **Step 4: Compiler**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/payment/model/PaymentStatus.java \
        src/main/java/com/oct/invoicesystem/domain/payment/model/Payment.java \
        src/main/resources/db/migration/V35__add_payment_status.sql
git commit -m "feat(payment): M7 #4 — PaymentStatus enum + champs entite + migration V35"
```

---

### Task 2: DTO + PaymentRequest + service (scheduled + processPayment + refactor finalize)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentDTO.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentRequest.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceImpl.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceTest.java` (créer si absent)

**Interfaces:**
- Consumes: `PaymentStatus`, `Payment.setStatus/setProcessedDate` (Task 1).
- Produces: `PaymentService.processPayment(UUID paymentId, UUID userId) : PaymentDTO` ; `PaymentDTO` avec `PaymentStatus status` + `Instant processedDate` ; `PaymentRequest.scheduled() : Boolean`.

- [ ] **Step 1: Étendre `PaymentDTO`**

```java
package com.oct.invoicesystem.domain.payment.dto;

import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDTO(
        UUID id,
        UUID invoiceId,
        BigDecimal amountPaid,
        PaymentMethod paymentMethod,
        Instant paymentDate,
        String reference,
        UUID recordedBy,
        Instant createdAt,
        PaymentStatus status,
        Instant processedDate
) {}
```

- [ ] **Step 2: Étendre `PaymentRequest`**

Ajouter le champ optionnel `scheduled` à la fin du record (après `reference`) :

```java
        String reference,

        /** true = paiement planifie (SCHEDULED) ; absent/false = execute immediatement (PROCESSED). */
        Boolean scheduled
) {}
```

- [ ] **Step 3: Déclarer `processPayment` dans l'interface**

Dans `PaymentService.java`, ajouter :

```java
    /**
     * Marque un paiement planifie (SCHEDULED) comme execute (PROCESSED) et finalise :
     * generation de l'avis de paiement, publication de l'evenement, transitions PAYE puis ARCHIVE.
     *
     * @throws com.oct.invoicesystem.shared.exception.ResourceNotFoundException si le paiement est introuvable
     * @throws com.oct.invoicesystem.shared.exception.WorkflowException si le paiement n'est pas SCHEDULED
     */
    PaymentDTO processPayment(UUID paymentId, UUID userId);
```

- [ ] **Step 4: Écrire les tests service (TDD — avant l'impl)**

Dans `PaymentServiceTest.java`. Le test mocke `paymentRepository`, `invoiceRepository`, `userRepository`, `invoiceStateMachineService`, `remittanceAdviceService`, `eventPublisher`, `selfProvider`, `tabularExportService`. Pattern : invoice en BON_A_PAYER, `existsByInvoiceId` false, `save` renvoie le paiement avec un id.

```java
@Test
void recordPayment_processedByDefault_finalizes() {
    // scheduled absent -> PROCESSED + finalisation
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoice.getStatus()).thenReturn(InvoiceStatus.BON_A_PAYER);
    when(paymentRepository.existsByInvoiceId(invoiceId)).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(paymentRepository.save(any())).thenAnswer(a -> a.getArgument(0));

    PaymentRequest req = new PaymentRequest(TEN, PaymentMethod.VIREMENT, Instant.now(), "PAY-1", null);
    PaymentDTO dto = service.recordPayment(invoiceId, req, userId);

    assertThat(dto.status()).isEqualTo(PaymentStatus.PROCESSED);
    assertThat(dto.processedDate()).isNotNull();
    verify(remittanceAdviceService).generateRemittanceAdvice(any(), eq(userId));
    verify(invoiceStateMachineService).sendEvent(eq(invoiceId), eq(InvoiceEvent.RECORD_PAYMENT), anyMap());
    verify(invoiceStateMachineService).sendEvent(eq(invoiceId), eq(InvoiceEvent.ARCHIVE), anyMap());
}

@Test
void recordPayment_scheduled_doesNotFinalize() {
    when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));
    when(invoice.getStatus()).thenReturn(InvoiceStatus.BON_A_PAYER);
    when(paymentRepository.existsByInvoiceId(invoiceId)).thenReturn(false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(paymentRepository.save(any())).thenAnswer(a -> a.getArgument(0));

    PaymentRequest req = new PaymentRequest(TEN, PaymentMethod.VIREMENT, Instant.now(), "PAY-1", true);
    PaymentDTO dto = service.recordPayment(invoiceId, req, userId);

    assertThat(dto.status()).isEqualTo(PaymentStatus.SCHEDULED);
    assertThat(dto.processedDate()).isNull();
    verifyNoInteractions(remittanceAdviceService);
    verify(invoiceStateMachineService, never()).sendEvent(any(), any(), anyMap());
}

@Test
void processPayment_happy_finalizes() {
    Payment scheduled = Payment.builder().id(paymentId).invoice(invoice)
            .status(PaymentStatus.SCHEDULED).recordedBy(user).build();
    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(scheduled));
    when(invoice.getId()).thenReturn(invoiceId);
    when(paymentRepository.save(any())).thenAnswer(a -> a.getArgument(0));

    PaymentDTO dto = service.processPayment(paymentId, userId);

    assertThat(dto.status()).isEqualTo(PaymentStatus.PROCESSED);
    assertThat(dto.processedDate()).isNotNull();
    verify(remittanceAdviceService).generateRemittanceAdvice(eq(paymentId), eq(userId));
    verify(invoiceStateMachineService).sendEvent(eq(invoiceId), eq(InvoiceEvent.ARCHIVE), anyMap());
}

@Test
void processPayment_alreadyProcessed_throws() {
    Payment processed = Payment.builder().id(paymentId).invoice(invoice)
            .status(PaymentStatus.PROCESSED).build();
    when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(processed));

    assertThatThrownBy(() -> service.processPayment(paymentId, userId))
            .isInstanceOf(WorkflowException.class);
}

@Test
void processPayment_notFound_throws() {
    when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.processPayment(paymentId, userId))
            .isInstanceOf(ResourceNotFoundException.class);
}
```

> Note d'impl : si `PaymentServiceTest` existe déjà, ajouter ces tests et réutiliser le setup `@BeforeEach` existant ; sinon créer la classe avec `@ExtendWith(MockitoExtension.class)`, les `@Mock` ci-dessus, `@InjectMocks PaymentServiceImpl service`, et un `@BeforeEach` initialisant les UUID + un `@Mock Invoice invoice`, `@Mock User user`. `TEN = BigDecimal.TEN`.

- [ ] **Step 2b: Lancer les tests → échec attendu**

Run: `./mvnw -q -Dtest=PaymentServiceTest test`
Expected: FAIL (méthodes/champs absents : `processPayment`, `status`, `scheduled`).

- [ ] **Step 5: Implémenter `PaymentServiceImpl`**

a) Extraire la finalisation. Remplacer dans `recordPayment` le bloc des lignes 100-121
(remittance + event + 2 `sendEvent`) par un appel `finalizePayment(payment, userId)`, et
construire le paiement avec le statut :

```java
        boolean isScheduled = Boolean.TRUE.equals(request.scheduled());

        Payment payment = Payment.builder()
                .invoice(invoice)
                .amountPaid(request.amountPaid())
                .paymentDate(request.paymentDate())
                .paymentMethod(request.paymentMethod())
                .reference(request.reference())
                .recordedBy(recordedBy)
                .status(isScheduled ? PaymentStatus.SCHEDULED : PaymentStatus.PROCESSED)
                .processedDate(isScheduled ? null : Instant.now())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Recorded payment {} for invoice {} (status={})", payment.getId(), invoiceId, payment.getStatus());

        if (!isScheduled) {
            finalizePayment(payment, userId);
        }

        return toDTO(payment);
```

b) Ajouter la méthode privée `finalizePayment` (corps = ancien bloc 100-121, paramétré) :

```java
    /**
     * Effets de bord d'un paiement execute : avis de paiement, evenement metier, transitions
     * BON_A_PAYER -> PAYE puis PAYE -> ARCHIVE. Partage par la creation directe et processPayment.
     */
    private void finalizePayment(Payment payment, UUID userId) {
        UUID invoiceId = payment.getInvoice().getId();

        remittanceAdviceService.generateRemittanceAdvice(payment.getId(), userId);
        log.info("Auto-generated remittance advice for payment {}", payment.getId());

        try {
            eventPublisher.publishEvent(new InvoicePayedEvent(this, invoiceId, payment.getId()));
        } catch (Exception e) {
            log.error("Failed to publish InvoicePayedEvent for invoice {}: {}", invoiceId, e.getMessage());
        }

        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.RECORD_PAYMENT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, userId));

        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ARCHIVE,
                Map.of(
                        WorkflowExtendedStateKeys.USER_ID, userId,
                        WorkflowExtendedStateKeys.AUTO_ARCHIVE, true
                ));
    }

    @Override
    @Transactional
    public PaymentDTO processPayment(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found id: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SCHEDULED) {
            throw new WorkflowException("payment.already.processed");
        }

        payment.setStatus(PaymentStatus.PROCESSED);
        payment.setProcessedDate(Instant.now());
        payment = paymentRepository.save(payment);
        log.info("Processed scheduled payment {}", paymentId);

        finalizePayment(payment, userId);
        return toDTO(payment);
    }
```

c) Mettre à jour `toDTO` pour inclure les 2 nouveaux champs :

```java
    private PaymentDTO toDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getAmountPaid(),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getReference(),
                payment.getRecordedBy() != null ? payment.getRecordedBy().getId() : null,
                payment.getCreatedAt(),
                payment.getStatus(),
                payment.getProcessedDate()
        );
    }
```

d) Ajouter l'import `import com.oct.invoicesystem.domain.payment.model.PaymentStatus;`.

> Note : `recordBatchPayment` construit `new PaymentRequest(..., reference)` — il faut
> ajouter le 5e argument `null` (scheduled) à cet appel (ligne ~144). Le batch reste PROCESSED.

- [ ] **Step 6: Lancer les tests → succès**

Run: `./mvnw -q -Dtest=PaymentServiceTest test`
Expected: PASS (5 tests verts).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentDTO.java \
        src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentRequest.java \
        src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentService.java \
        src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceImpl.java \
        src/test/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceTest.java
git commit -m "feat(payment): M7 #4 — recordPayment scheduled + processPayment + refactor finalize"
```

---

### Task 3: Endpoint POST /payments/{id}/process + test controller + i18n backend

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/controller/PaymentController.java`
- Modify: `src/main/resources/messages_fr.properties` (ISO-8859-1)
- Modify: `src/main/resources/messages_en.properties` (UTF-8)
- Test: `src/test/java/com/oct/invoicesystem/domain/payment/controller/PaymentControllerTest.java`

**Interfaces:**
- Consumes: `PaymentService.processPayment` (Task 2).
- Produces: `POST /api/v1/payments/{paymentId}/process`.

- [ ] **Step 1: Écrire le test controller (TDD)**

Aligner sur le style de `PaymentControllerTest` existant (MockMvc + `@WithMockUser`). Si la
classe n'existe pas, la créer avec `@WebMvcTest(PaymentController.class)` + `@MockBean PaymentService` + `@MockBean RemittanceAdviceService`.

```java
@Test
@WithMockUser(roles = "ASSISTANT_COMPTABLE")
void process_asAssistant_returns200() throws Exception {
    UUID pid = UUID.randomUUID();
    when(paymentService.processPayment(eq(pid), any()))
            .thenReturn(mock(PaymentDTO.class));
    mockMvc.perform(post("/api/v1/payments/{id}/process", pid).with(csrf()))
            .andExpect(status().isOk());
}

@Test
@WithMockUser(roles = "DAF")
void process_asDaf_returns403() throws Exception {
    mockMvc.perform(post("/api/v1/payments/{id}/process", UUID.randomUUID()).with(csrf()))
            .andExpect(status().isForbidden());
}
```

> Si le `@AuthenticationPrincipal User` empêche le mock simple, suivre exactement le pattern
> déjà utilisé par les autres tests `post` de ce fichier (resolver d'argument / `@WithUserDetails`).

- [ ] **Step 2: Lancer → échec**

Run: `./mvnw -q -Dtest=PaymentControllerTest test`
Expected: FAIL (endpoint 404 / méthode absente).

- [ ] **Step 3: Ajouter l'endpoint**

Dans `PaymentController.java`, après `recordBatchPayment` (ligne ~59) :

```java
    @PostMapping("/{paymentId}/process")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Mark a scheduled payment as processed (triggers remittance + archive)")
    public ResponseEntity<ApiResponse<PaymentDTO>> processPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal User currentUser) {
        PaymentDTO dto = paymentService.processPayment(paymentId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(dto, "payment.processed.success"));
    }
```

- [ ] **Step 4: Ajouter les clés i18n EN** (`messages_en.properties`)

```properties
payment.processed.success=Payment marked as processed
payment.scheduled.success=Payment scheduled
payment.already.processed=Payment has already been processed
```

- [ ] **Step 5: Ajouter les clés i18n FR** (ISO-8859-1 — via iconv, sans accent problématique)

```bash
printf 'payment.processed.success=Paiement marqu\xe9 comme ex\xe9cut\xe9\npayment.scheduled.success=Paiement planifi\xe9\npayment.already.processed=Le paiement a d\xe9j\xe0 \xe9t\xe9 ex\xe9cut\xe9\n' >> src/main/resources/messages_fr.properties
```

(Vérifier ensuite à l'œil que les accents `é`/`à` s'affichent correctement dans l'éditeur Latin-1.)

- [ ] **Step 6: Lancer → succès**

Run: `./mvnw -q -Dtest=PaymentControllerTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/payment/controller/PaymentController.java \
        src/test/java/com/oct/invoicesystem/domain/payment/controller/PaymentControllerTest.java \
        src/main/resources/messages_fr.properties src/main/resources/messages_en.properties
git commit -m "feat(payment): M7 #4 — endpoint POST /payments/{id}/process + i18n"
```

---

### Task 4: Rapport cycle de paiement — DTO + service + endpoint + tests (M11 #5)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/report/dto/PaymentCycleReportDTO.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java`

**Interfaces:**
- Consumes: `PaymentRepository`, `InvoiceStatusHistoryRepository` (déjà injecté dans `ReportServiceImpl` comme `historyRepository`), `Payment.getStatus/getPaymentDate/getProcessedDate` (Task 1).
- Produces: `ReportService.getPaymentCycleReport(Instant from, Instant to) : PaymentCycleReportDTO` ; `GET /api/v1/reports/payment-cycle`.

- [ ] **Step 1: Créer le DTO**

```java
package com.oct.invoicesystem.domain.report.dto;

/**
 * Analyse du cycle de paiement sur une periode (basee sur la date d'execution reelle).
 * Les delais moyens sont en jours ; null si l'echantillon correspondant est vide.
 */
public record PaymentCycleReportDTO(
        long invoicesPaidCount,
        Double avgSubmissionToBapDays,
        Double avgBapToPaymentDays,
        Double avgScheduledToProcessedDays,
        Double avgTotalCycleDays
) {}
```

- [ ] **Step 2: Déclarer dans l'interface `ReportService`**

```java
    /**
     * Analyse le cycle de paiement (delais soumission -> validation -> BAP -> paiement) pour les
     * factures dont le paiement a ete execute (PROCESSED) entre {@code from} et {@code to}.
     */
    PaymentCycleReportDTO getPaymentCycleReport(java.time.Instant from, java.time.Instant to);
```

- [ ] **Step 3: Écrire les tests service (TDD)**

Dans `ReportServiceTest.java`. Mocker `paymentRepository` (l'ajouter aux mocks si absent) et
`historyRepository`. Construire 1 facture payée : paiement PROCESSED, `paymentDate` = J,
`processedDate` = J+2 ; historique SOUMIS à J-10, BON_A_PAYER à J-3.

```java
@Test
void paymentCycle_happy_computesAverages() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to   = Instant.parse("2026-12-31T00:00:00Z");
    UUID invId = UUID.randomUUID();
    Payment p = Payment.builder()
            .status(PaymentStatus.PROCESSED)
            .paymentDate(Instant.parse("2026-06-10T00:00:00Z"))
            .processedDate(Instant.parse("2026-06-12T00:00:00Z"))
            .build();
    // p.getInvoice().getId() == invId  -> stub via mock invoice
    when(paymentRepository.findProcessedBetween(from, to)).thenReturn(List.of(p));
    when(historyRepository.findRelevantHistoryForProcessingTime()).thenReturn(List.of(
            history(invId, "SOUMIS",      "2026-06-01T00:00:00Z"),
            history(invId, "BON_A_PAYER", "2026-06-08T00:00:00Z")));

    PaymentCycleReportDTO dto = service.getPaymentCycleReport(from, to);

    assertThat(dto.invoicesPaidCount()).isEqualTo(1);
    assertThat(dto.avgSubmissionToBapDays()).isEqualTo(7.0);   // 01 -> 08
    assertThat(dto.avgBapToPaymentDays()).isEqualTo(4.0);      // 08 -> 12 (processedDate)
}

@Test
void paymentCycle_emptyPeriod_returnsZeroAndNulls() {
    when(paymentRepository.findProcessedBetween(any(), any())).thenReturn(List.of());

    PaymentCycleReportDTO dto = service.getPaymentCycleReport(Instant.now(), Instant.now());

    assertThat(dto.invoicesPaidCount()).isZero();
    assertThat(dto.avgBapToPaymentDays()).isNull();
}

@Test
void paymentCycle_scheduledPayment_feedsScheduledToProcessed() {
    Instant from = Instant.parse("2026-01-01T00:00:00Z");
    Instant to   = Instant.parse("2026-12-31T00:00:00Z");
    Payment p = Payment.builder()
            .status(PaymentStatus.PROCESSED)
            .paymentDate(Instant.parse("2026-06-10T00:00:00Z"))   // prevu
            .processedDate(Instant.parse("2026-06-13T00:00:00Z")) // reel (+3j)
            .build();
    when(paymentRepository.findProcessedBetween(from, to)).thenReturn(List.of(p));
    when(historyRepository.findRelevantHistoryForProcessingTime()).thenReturn(List.of());

    PaymentCycleReportDTO dto = service.getPaymentCycleReport(from, to);

    assertThat(dto.avgScheduledToProcessedDays()).isEqualTo(3.0);
}
```

> Helper `history(...)` : crée un `InvoiceStatusHistory` avec un mock `Invoice` dont `getId()` renvoie l'UUID, `toStatus`, `changedAt`. `avgScheduledToProcessedDays` = moyenne de `(processedDate - paymentDate)` sur **tous** les paiements PROCESSED de la période (paymentDate = date prévue ; pour un paiement non planifié processedDate≈paymentDate ⇒ ~0, ce qui reste correct).

- [ ] **Step 4: Ajouter la requête repository**

Dans `PaymentRepository.java` :

```java
    @Query("SELECT p FROM Payment p WHERE p.status = com.oct.invoicesystem.domain.payment.model.PaymentStatus.PROCESSED " +
           "AND p.processedDate >= :from AND p.processedDate <= :to")
    java.util.List<Payment> findProcessedBetween(@org.springframework.data.repository.query.Param("from") java.time.Instant from,
                                                 @org.springframework.data.repository.query.Param("to") java.time.Instant to);
```

- [ ] **Step 5: Lancer les tests → échec**

Run: `./mvnw -q -Dtest=ReportServiceTest test`
Expected: FAIL (méthode `getPaymentCycleReport` absente).

- [ ] **Step 6: Implémenter dans `ReportServiceImpl`**

S'assurer que `PaymentRepository paymentRepository` est injecté (sinon l'ajouter au
constructeur — il est probablement déjà présent pour `getSupplierPaymentHistory`). Ajouter :

```java
    @Override
    @Transactional(readOnly = true)
    public PaymentCycleReportDTO getPaymentCycleReport(Instant from, Instant to) {
        List<Payment> paid = paymentRepository.findProcessedBetween(from, to);
        if (paid.isEmpty()) {
            return new PaymentCycleReportDTO(0, null, null, null, null);
        }

        // Historique soumission/BAP indexe par facture (meme source que processing-time).
        Map<UUID, List<InvoiceStatusHistory>> historyByInvoice =
                historyRepository.findRelevantHistoryForProcessingTime().stream()
                        .collect(Collectors.groupingBy(h -> h.getInvoice().getId()));

        List<Double> subToBap = new ArrayList<>();
        List<Double> bapToPay = new ArrayList<>();
        List<Double> schedToProc = new ArrayList<>();
        List<Double> total = new ArrayList<>();

        for (Payment p : paid) {
            UUID invId = p.getInvoice().getId();
            List<InvoiceStatusHistory> hist = historyByInvoice.getOrDefault(invId, List.of());
            Instant submitted = firstAt(hist, "SOUMIS");
            Instant bap = firstAt(hist, "BON_A_PAYER");
            Instant processed = p.getProcessedDate();

            if (submitted != null && bap != null) subToBap.add(days(submitted, bap));
            if (bap != null && processed != null) bapToPay.add(days(bap, processed));
            if (submitted != null && processed != null) total.add(days(submitted, processed));
            if (p.getPaymentDate() != null && processed != null) schedToProc.add(days(p.getPaymentDate(), processed));
        }

        return new PaymentCycleReportDTO(
                paid.size(),
                average(subToBap),
                average(bapToPay),
                average(schedToProc),
                average(total));
    }

    private static Instant firstAt(List<InvoiceStatusHistory> hist, String toStatus) {
        return hist.stream().filter(h -> toStatus.equals(h.getToStatus()))
                .map(InvoiceStatusHistory::getChangedAt)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private static double days(Instant a, Instant b) {
        return java.time.Duration.between(a, b).toHours() / 24.0;
    }

    private static Double average(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
```

Ajouter les imports nécessaires (`Collectors`, `ArrayList`, `Comparator`, `Duration` si absents) et `import com.oct.invoicesystem.domain.report.dto.PaymentCycleReportDTO;`, `import com.oct.invoicesystem.domain.payment.model.Payment;`.

- [ ] **Step 7: Lancer → succès**

Run: `./mvnw -q -Dtest=ReportServiceTest test`
Expected: PASS.

- [ ] **Step 8: Ajouter l'endpoint + test controller**

Dans `ReportController.java`, après `/cash-flow` (ligne ~134) :

```java
    @GetMapping("/payment-cycle")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Payment Cycle Analysis",
            description = "Average step delays (submission -> validation -> BAP -> payment) for paid invoices in a period")
    public ApiResponse<PaymentCycleReportDTO> getPaymentCycle(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.success(reportService.getPaymentCycleReport(from, to));
    }
```

Dans `ReportControllerTest.java` (suivre le pattern des tests `/cash-flow` existants) :

```java
@Test
@WithMockUser(roles = "DAF")
void paymentCycle_asDaf_returns200() throws Exception {
    when(reportService.getPaymentCycleReport(any(), any()))
            .thenReturn(new PaymentCycleReportDTO(0, null, null, null, null));
    mockMvc.perform(get("/api/v1/reports/payment-cycle")
                    .param("from", "2026-01-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
            .andExpect(status().isOk());
}

@Test
@WithMockUser(roles = "ADMIN")
void paymentCycle_asAdmin_returns403() throws Exception {
    mockMvc.perform(get("/api/v1/reports/payment-cycle")
                    .param("from", "2026-01-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
            .andExpect(status().isForbidden());
}
```

- [ ] **Step 9: Lancer toute la suite back**

Run: `./mvnw -q test`
Expected: 0 échec.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/ \
        src/main/java/com/oct/invoicesystem/domain/payment/repository/PaymentRepository.java \
        src/test/java/com/oct/invoicesystem/domain/report/
git commit -m "feat(report): M11 #5 — rapport cycle de paiement (delais par etape)"
```

---

### Task 5: Front — case « planifier » + colonne statut + bouton « marquer exécuté » + i18n + test

**Files:**
- Modify: `frontend/src/pages/PaymentsPage.tsx`
- Modify: `frontend/src/i18n/locales/fr.json` (chemin exact à confirmer — voir Step 0)
- Modify: `frontend/src/i18n/locales/en.json`
- Test: `frontend/src/pages/__tests__/PaymentsPage.test.tsx` (créer ; ou emplacement test existant du projet)

**Interfaces:**
- Consumes: champ DTO `status`/`processedDate` (Task 2) ; endpoint `POST /payments/{id}/process` (Task 3).
- Produces: rien (feuille terminale).

- [ ] **Step 0: Confirmer les chemins réels (règle d'or)**

Run: `ls frontend/src/i18n/locales/ 2>/dev/null; ls frontend/src/locales/ 2>/dev/null; find frontend/src -name 'fr.json'`
Localiser fr.json/en.json + repérer où vivent les tests vitest existants (ex. `*.test.tsx`).

- [ ] **Step 1: Étendre l'interface `Payment` (front)**

Dans `PaymentsPage.tsx`, ajouter au `interface Payment` :

```typescript
  status: 'SCHEDULED' | 'PROCESSED'
  processedDate?: string
```

- [ ] **Step 2: Case « planifier » dans `RecordPaymentModal`**

Ajouter `scheduled: false` au state `form`. Avant le bloc Notes, ajouter :

```tsx
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" checked={form.scheduled}
              onChange={e => setForm(p => ({ ...p, scheduled: e.target.checked }))} />
            {t('payments.scheduleThis', 'Planifier ce paiement (à exécuter plus tard)')}
          </label>
```

Inclure `scheduled: form.scheduled || undefined` dans le body POST. Adapter le libellé du
bouton : `form.scheduled ? t('payments.scheduleBtn','Planifier le paiement') : t('invoice.markPaid','Enregistrer le paiement')`.

- [ ] **Step 3: Mutation « marquer exécuté » + colonne statut**

Dans `PaymentsPage`, ajouter une mutation :

```tsx
  const processMutation = useMutation({
    mutationFn: (paymentId: string) => apiClient.post(`/payments/${paymentId}/process`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payments'] })
      queryClient.invalidateQueries({ queryKey: ['invoices-bon-a-payer'] })
    },
  })
```

Dans le `<thead>` de l'historique, ajouter une `<th>` Statut après la date :
`<th className="text-left px-4 py-3 font-medium text-gray-600">{t('payments.colStatus','Statut')}</th>`

Dans chaque `<tr>`, ajouter une cellule statut + conditionner remittance à PROCESSED :

```tsx
                      <td className="px-4 py-3">
                        {p.status === 'SCHEDULED' ? (
                          <button onClick={() => processMutation.mutate(p.id)}
                            disabled={processMutation.isPending}
                            className="flex items-center gap-1 text-xs bg-amber-100 text-amber-800 border border-amber-200 px-2 py-1 rounded hover:bg-amber-200 disabled:opacity-50">
                            <CheckCircle className="w-3.5 h-3.5" />
                            {t('payments.markProcessed', 'Marquer exécuté')}
                          </button>
                        ) : (
                          <span className="text-xs bg-green-50 text-green-700 px-2 py-0.5 rounded border border-green-100">
                            {t('payments.status.processed', 'Exécuté')}
                          </span>
                        )}
                      </td>
```

(La cellule remittance existante : l'envelopper dans `{p.status === 'PROCESSED' && ( ... )}`.)

- [ ] **Step 4: Clés i18n (fr + en, parité)**

fr.json (sous `payments`) :
```json
"scheduleThis": "Planifier ce paiement (à exécuter plus tard)",
"scheduleBtn": "Planifier le paiement",
"colStatus": "Statut",
"markProcessed": "Marquer exécuté",
"status": { "scheduled": "Planifié", "processed": "Exécuté" }
```
en.json (mêmes clés) :
```json
"scheduleThis": "Schedule this payment (process later)",
"scheduleBtn": "Schedule payment",
"colStatus": "Status",
"markProcessed": "Mark processed",
"status": { "scheduled": "Scheduled", "processed": "Processed" }
```

- [ ] **Step 5: Test vitest**

```tsx
// rendre PaymentsPage avec un QueryClient + un mock apiClient renvoyant
// un paiement SCHEDULED et un PROCESSED, puis :
it('affiche le bouton Marquer exécuté seulement pour SCHEDULED', async () => {
  // ... render ...
  expect(await screen.findByText('Marquer exécuté')).toBeInTheDocument()
  expect(screen.getByText('Exécuté')).toBeInTheDocument()
})
```

(Adapter au harnais de test/mocking du projet repéré au Step 0.)

- [ ] **Step 6: Gate front**

Run: `cd frontend && npm run test && npx tsc --noEmit`
Expected: vitest 0 échec, tsc 0 erreur.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/PaymentsPage.tsx frontend/src/**/fr.json frontend/src/**/en.json \
        frontend/src/pages/__tests__/PaymentsPage.test.tsx
git commit -m "feat(payment): M7 #4 — front statut paiement (planifier + marquer execute)"
```

---

### Task 6: Mise à jour TASKS.md + gate complet final

**Files:**
- Modify: `docs/TASKS.md`

- [ ] **Step 1: Marquer M7 #4 et M11 #5 comme faits**

Ligne 298 (M7 #4) : passer 🟠 → ✅ avec note « Statut SCHEDULED/PROCESSED + endpoint /process (2026-06-27) ».
Ligne 441 (M11 #5) : passer 🟠 → ✅ avec note « Rapport /reports/payment-cycle (delais par etape) ».
Mettre à jour les lignes de gaps récap (322 pour M7, 465 pour M11) en retirant ces items.

- [ ] **Step 2: Gate complet**

Run: `./mvnw -q test` (0 échec) puis `cd frontend && npm run test && npx tsc --noEmit` (0 échec, 0 erreur).

- [ ] **Step 3: Commit**

```bash
git add docs/TASKS.md
git commit -m "docs(tasks): M7 #4 + M11 #5 marques comme faits"
```

- [ ] **Step 4: Vérifier le compteur de commits non poussés**

Run: `git log --oneline origin/chore/sanitize-docs-migrations..HEAD | wc -l`
Si ≥ 10 → `git push origin chore/sanitize-docs-migrations` (règle « push every 10 commits »).

---

## Notes d'exécution

- **Règle d'or** : chaque tâche commence par grep/lire le fichier réel avant d'éditer ; les
  signatures exactes des tests existants priment sur les squelettes ci-dessus.
- **Pas de migration en double** : V35 confirmé comme prochain numéro (max actuel = V34).
- **ISO-8859-1** : ne jamais append en UTF-8 dans `messages_fr.properties` (corrompt les accents).
- **SoD** : le test `paymentCycle_asAdmin_returns403` est non négociable.
