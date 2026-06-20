# Disposition de rétention (PENDING/RETAINED/PURGED) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire en sorte qu'un document périmé n'alimente l'ATTENTION de la carte de conformité que tant qu'il est en attente de disposition ; une fois traité (RETAINED/PURGED) il ne compte plus → la carte repasse CONFORME.

**Architecture:** Nouveau champ `retention_disposition` (enum PENDING/RETAINED/PURGED) sur `invoice_documents` + traçabilité (at/by). `DocumentRetentionJob` ne compte que les périmés `PENDING`. Nouveaux endpoints ADMIN (`RetentionDispositionController` + `RetentionDispositionService`) pour lister les périmés en attente et acter une disposition. La règle `evaluateCompliance()` reste inchangée (on corrige la source du compteur, pas la règle).

**Tech Stack:** Spring Boot 3, JPA/Flyway, JUnit 5 + Mockito + MockMvc, React 18 + TS (i18n only), react-i18next.

## Global Constraints

- Backend command runner : `./mvnw.cmd` (PAS `./mvnw`).
- SoD (PROB-065) : rétention = paramètre système → **ADMIN uniquement** sur chaque endpoint ; jamais DAF/financier.
- Toujours `@PreAuthorize` sur chaque méthode de contrôleur ; DTO only (jamais d'entité JPA exposée) ; réponses dans `ApiResponse<T>`.
- Flyway immuable (PROB-009) : nouvelle migration `V63`, jamais d'édition d'une migration appliquée.
- Lombok boolean (PROB-003) : N/A ici (pas de nouveau booléen).
- `messages_fr.properties` est en ISO-8859-1 → ajout via iconv, ASCII-safe (pas d'em-dash/quotes courbes). `messages_en.properties` = ASCII.
- i18n frontend : `frontend/src/i18n/{fr,en}.json`, chemin `admin.audit.retention.*`.
- `evaluateCompliance()` : NE PAS modifier (la règle prend le bon sens dès que le job alimente le compteur avec les seuls PENDING).
- `AuditService.logAction` signature : `logAction(UUID userId, String entityType, String entityId, String action, Object oldValue, Object newValue, String ipAddress, String userAgent)`.
- Exceptions : `new ValidationException(String)` (→ 400), `new ResourceNotFoundException(String)` (→ 404), toutes deux dans `com.oct.invoicesystem.shared.exception`.
- Critère de fin : `./mvnw.cmd test` 0 échec + `tsc` 0 + `vitest` vert.

---

## File Structure

- `src/main/java/com/oct/invoicesystem/domain/invoice/model/RetentionDisposition.java` — **créer** : enum.
- `src/main/java/com/oct/invoicesystem/domain/invoice/model/InvoiceDocument.java` — **modifier** : 3 champs.
- `src/main/resources/db/migration/V63__add_retention_disposition.sql` — **créer** : migration.
- `src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceDocumentRepository.java` — **modifier** : requête `...AndRetentionDisposition`.
- `src/main/java/com/oct/invoicesystem/domain/invoice/scheduler/DocumentRetentionJob.java` — **modifier** : ne ramasse que PENDING.
- `src/test/java/com/oct/invoicesystem/domain/invoice/scheduler/DocumentRetentionJobTest.java` — **modifier** : stubs adaptés.
- `src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionPendingDocumentDTO.java` — **créer** : DTO.
- `src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionDispositionRequest.java` — **créer** : body PUT.
- `src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionService.java` — **créer** : service.
- `src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionServiceTest.java` — **créer** : tests unitaires.
- `src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionController.java` — **créer** : contrôleur.
- `src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionControllerIntegrationTest.java` — **créer** : tests intégration.
- `src/main/resources/messages_fr.properties` + `messages_en.properties` — **modifier** : clé `retention.disposition.updated`.
- `frontend/src/i18n/fr.json` + `en.json` — **modifier** : libellé `admin.audit.retention.flagged`.
- `docs/COMPLIANCE_MATRIX.md` — **modifier** : note ligne 335.

---

## Task 1 : Enum + champs entité + migration Flyway

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/invoice/model/RetentionDisposition.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/model/InvoiceDocument.java`
- Create: `src/main/resources/db/migration/V63__add_retention_disposition.sql`

**Interfaces:**
- Produces : enum `RetentionDisposition { PENDING, RETAINED, PURGED }` ; champs `InvoiceDocument.getRetentionDisposition()/setRetentionDisposition(...)`, `getRetentionDispositionAt()/setRetentionDispositionAt(Instant)`, `getRetentionDispositionBy()/setRetentionDispositionBy(User)`.

- [ ] **Step 1 : Créer l'enum**

`src/main/java/com/oct/invoicesystem/domain/invoice/model/RetentionDisposition.java` :
```java
package com.oct.invoicesystem.domain.invoice.model;

/** Disposition decision for a document past its retention horizon (M10 #10 refinement). */
public enum RetentionDisposition {
    /** Past horizon (or not), no decision taken yet. Default. Counted by the retention sweep. */
    PENDING,
    /** Deliberately kept (legal value / legal hold). No longer counted. */
    RETAINED,
    /** Purge / cold-archive decision recorded (marking only — no physical delete here). */
    PURGED
}
```

- [ ] **Step 2 : Ajouter les 3 champs à `InvoiceDocument`**

Dans `InvoiceDocument.java`, ajouter les imports manquants si absents (`EnumType`, `Enumerated` depuis `jakarta.persistence`) puis, après le champ `supersededByDocumentId` (autour de la ligne 69), insérer :
```java
    // M10 #10 refinement: disposition of a document past its retention horizon.
    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "retention_disposition", nullable = false, length = 20)
    @Builder.Default
    private RetentionDisposition retentionDisposition = RetentionDisposition.PENDING;

    @Column(name = "retention_disposition_at")
    private Instant retentionDispositionAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retention_disposition_by")
    private User retentionDispositionBy;
```
(`User`, `ManyToOne`, `FetchType`, `JoinColumn`, `Column`, `Instant`, `Builder` sont déjà importés dans ce fichier.)

- [ ] **Step 3 : Créer la migration V63**

`src/main/resources/db/migration/V63__add_retention_disposition.sql` :
```sql
-- M10 #10 refinement: track disposition of documents past their retention horizon
-- so the compliance card only flags those still PENDING (not the whole expired backlog forever).
ALTER TABLE invoice_documents
    ADD COLUMN retention_disposition    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN retention_disposition_at TIMESTAMP,
    ADD COLUMN retention_disposition_by UUID REFERENCES users(id);
```

- [ ] **Step 4 : Compiler pour vérifier que l'entité + l'enum compilent**

Run : `./mvnw.cmd -q -DskipTests compile`
Expected : BUILD SUCCESS (pas d'erreur de compilation).

- [ ] **Step 5 : Commit**
```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/model/RetentionDisposition.java \
        src/main/java/com/oct/invoicesystem/domain/invoice/model/InvoiceDocument.java \
        src/main/resources/db/migration/V63__add_retention_disposition.sql
git commit -m "feat(retention): RetentionDisposition enum + fields on InvoiceDocument + V63

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2 : Repository + job ne compte que les PENDING

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceDocumentRepository.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/scheduler/DocumentRetentionJob.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/invoice/scheduler/DocumentRetentionJobTest.java`

**Interfaces:**
- Consumes : `RetentionDisposition` (Task 1).
- Produces : `InvoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(Instant, RetentionDisposition)`.

- [ ] **Step 1 : Adapter les tests du job (RED)**

Dans `DocumentRetentionJobTest.java` :
- ajouter l'import : `import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;`
- remplacer, dans `inactivePolicy_skipsSweepEntirely`, la vérification :
  `verify(invoiceDocumentRepository, never()).findByUploadedAtBefore(any());`
  par :
  `verify(invoiceDocumentRepository, never()).findByUploadedAtBeforeAndRetentionDisposition(any(), any());`
- dans `activePolicy_flagsExpiredAndRecordsSweep`, remplacer le stub :
  `when(invoiceDocumentRepository.findByUploadedAtBefore(any())).thenReturn(List.of(doc));`
  par :
  `when(invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(any(), eq(RetentionDisposition.PENDING))).thenReturn(List.of(doc));`
- dans `activePolicy_noExpiredDocs_recordsZeroSweep`, remplacer le stub :
  `when(invoiceDocumentRepository.findByUploadedAtBefore(any())).thenReturn(List.of());`
  par :
  `when(invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(any(), eq(RetentionDisposition.PENDING))).thenReturn(List.of());`
(`eq` est déjà importé statiquement dans ce fichier.)

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run : `./mvnw.cmd test -Dtest=DocumentRetentionJobTest`
Expected : FAIL — compilation error (`findByUploadedAtBeforeAndRetentionDisposition` n'existe pas encore).

- [ ] **Step 3 : Ajouter la requête au repository**

Dans `InvoiceDocumentRepository.java`, ajouter l'import `import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;` puis, après `findByUploadedAtBefore` :
```java
    // M10 #10 refinement: expired documents still awaiting a disposition decision (retention sweep).
    List<InvoiceDocument> findByUploadedAtBeforeAndRetentionDisposition(
            java.time.Instant cutoff, RetentionDisposition disposition);
```

- [ ] **Step 4 : Brancher le job sur la requête PENDING**

Dans `DocumentRetentionJob.java` :
- ajouter l'import `import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;`
- remplacer la ligne :
  `List<InvoiceDocument> expired = invoiceDocumentRepository.findByUploadedAtBefore(cutoff);`
  par :
  `List<InvoiceDocument> expired = invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(cutoff, RetentionDisposition.PENDING);`
(Le reste — log RETENTION_FLAG, recordSweep — reste inchangé.)

- [ ] **Step 5 : Lancer pour vérifier le succès**

Run : `./mvnw.cmd test -Dtest=DocumentRetentionJobTest`
Expected : PASS (3 tests).

- [ ] **Step 6 : Commit**
```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceDocumentRepository.java \
        src/main/java/com/oct/invoicesystem/domain/invoice/scheduler/DocumentRetentionJob.java \
        src/test/java/com/oct/invoicesystem/domain/invoice/scheduler/DocumentRetentionJobTest.java
git commit -m "feat(retention): sweep counts only PENDING expired documents

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3 : DTO + RetentionDispositionService

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionPendingDocumentDTO.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionDispositionRequest.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionService.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionServiceTest.java`

**Interfaces:**
- Consumes : `InvoiceDocumentRepository` (Task 2), `RetentionPolicyService.getEntity()` (existant, donne `getRetentionYears()`), `AuditService.logAction(...)`, `RetentionDisposition`, `User`.
- Produces :
  - record `RetentionPendingDocumentDTO(UUID id, UUID invoiceId, String originalFilename, Instant uploadedAt, RetentionDisposition retentionDisposition)`
  - record `RetentionDispositionRequest(@NotNull RetentionDisposition disposition)`
  - `RetentionDispositionService.listPendingExpired() : List<RetentionPendingDocumentDTO>`
  - `RetentionDispositionService.setDisposition(UUID docId, RetentionDisposition target, User actor) : RetentionPendingDocumentDTO`

- [ ] **Step 1 : Créer le DTO**

`src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionPendingDocumentDTO.java` :
```java
package com.oct.invoicesystem.domain.retention.dto;

import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;

import java.time.Instant;
import java.util.UUID;

/** A document past its retention horizon, for ADMIN disposition (M10 #10 refinement). */
public record RetentionPendingDocumentDTO(
        UUID id,
        UUID invoiceId,
        String originalFilename,
        Instant uploadedAt,
        RetentionDisposition retentionDisposition
) {}
```

- [ ] **Step 2 : Créer le record de requête**

`src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionDispositionRequest.java` :
```java
package com.oct.invoicesystem.domain.retention.dto;

import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import jakarta.validation.constraints.NotNull;

/** Body for setting a document's retention disposition. */
public record RetentionDispositionRequest(
        @NotNull RetentionDisposition disposition
) {}
```

- [ ] **Step 3 : Écrire les tests unitaires du service (RED)**

`src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionServiceTest.java` :
```java
package com.oct.invoicesystem.domain.retention.service;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionDispositionServiceTest {

    @Mock InvoiceDocumentRepository invoiceDocumentRepository;
    @Mock com.oct.invoicesystem.domain.retention.service.RetentionPolicyService retentionPolicyService;
    @Mock AuditService auditService;
    @InjectMocks RetentionDispositionService service;

    private InvoiceDocument doc(UUID id, RetentionDisposition disp) {
        Invoice inv = new Invoice();
        inv.setId(UUID.randomUUID());
        InvoiceDocument d = new InvoiceDocument();
        d.setId(id);
        d.setInvoice(inv);
        d.setOriginalFilename("old.pdf");
        d.setUploadedAt(Instant.parse("2000-01-01T00:00:00Z"));
        d.setRetentionDisposition(disp);
        return d;
    }

    @Test
    void listPendingExpired_returnsOnlyExpiredPendingMappedToDto() {
        when(retentionPolicyService.getEntity())
                .thenReturn(RetentionPolicy.builder().retentionYears(10).active(true).build());
        InvoiceDocument d = doc(UUID.randomUUID(), RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(any(), eq(RetentionDisposition.PENDING)))
                .thenReturn(List.of(d));

        List<RetentionPendingDocumentDTO> result = service.listPendingExpired();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(d.getId());
        assertThat(result.get(0).originalFilename()).isEqualTo("old.pdf");
        assertThat(result.get(0).retentionDisposition()).isEqualTo(RetentionDisposition.PENDING);
    }

    @Test
    void setDisposition_retained_setsFieldsAndAudits() {
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        User actor = new User();
        actor.setId(UUID.randomUUID());

        RetentionPendingDocumentDTO dto = service.setDisposition(id, RetentionDisposition.RETAINED, actor);

        assertThat(dto.retentionDisposition()).isEqualTo(RetentionDisposition.RETAINED);
        assertThat(d.getRetentionDispositionAt()).isNotNull();
        assertThat(d.getRetentionDispositionBy()).isEqualTo(actor);
        verify(auditService).logAction(eq(actor.getId()), eq("INVOICE_DOCUMENT"), eq(id.toString()),
                eq("RETENTION_DISPOSITION"), any(), any(), any(), any());
    }

    @Test
    void setDisposition_purged_setsField() {
        UUID id = UUID.randomUUID();
        InvoiceDocument d = doc(id, RetentionDisposition.PENDING);
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.of(d));
        when(invoiceDocumentRepository.save(any(InvoiceDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        User actor = new User();
        actor.setId(UUID.randomUUID());

        RetentionPendingDocumentDTO dto = service.setDisposition(id, RetentionDisposition.PURGED, actor);

        assertThat(dto.retentionDisposition()).isEqualTo(RetentionDisposition.PURGED);
    }

    @Test
    void setDisposition_toPending_isRejected() {
        UUID id = UUID.randomUUID();
        User actor = new User();
        actor.setId(UUID.randomUUID());

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.PENDING, actor))
                .isInstanceOf(ValidationException.class);
        verify(invoiceDocumentRepository, never()).save(any());
    }

    @Test
    void setDisposition_unknownDoc_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceDocumentRepository.findById(id)).thenReturn(Optional.empty());
        User actor = new User();
        actor.setId(UUID.randomUUID());

        assertThatThrownBy(() -> service.setDisposition(id, RetentionDisposition.RETAINED, actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 4 : Lancer pour vérifier l'échec**

Run : `./mvnw.cmd test -Dtest=RetentionDispositionServiceTest`
Expected : FAIL — `RetentionDispositionService` n'existe pas (compilation error).

- [ ] **Step 5 : Implémenter le service**

`src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionService.java` :
```java
package com.oct.invoicesystem.domain.retention.service;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionPolicy;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * ADMIN disposition of documents past their retention horizon (M10 #10 refinement).
 * Listing surfaces only expired documents still PENDING; setting a disposition (RETAINED/PURGED)
 * removes them from the count so the compliance card stops flagging ATTENTION once handled.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RetentionDispositionService {

    private final InvoiceDocumentRepository invoiceDocumentRepository;
    private final RetentionPolicyService retentionPolicyService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<RetentionPendingDocumentDTO> listPendingExpired() {
        RetentionPolicy policy = retentionPolicyService.getEntity();
        Instant cutoff = Instant.now().minus(policy.getRetentionYears() * 365L, ChronoUnit.DAYS);
        return invoiceDocumentRepository
                .findByUploadedAtBeforeAndRetentionDisposition(cutoff, RetentionDisposition.PENDING)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public RetentionPendingDocumentDTO setDisposition(UUID docId, RetentionDisposition target, User actor) {
        if (target == RetentionDisposition.PENDING) {
            throw new ValidationException("retention.disposition.invalid_target");
        }
        InvoiceDocument doc = invoiceDocumentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id " + docId));

        RetentionDisposition previous = doc.getRetentionDisposition();
        doc.setRetentionDisposition(target);
        doc.setRetentionDispositionAt(Instant.now());
        doc.setRetentionDispositionBy(actor);
        InvoiceDocument saved = invoiceDocumentRepository.save(doc);

        auditService.logAction(actor != null ? actor.getId() : null, "INVOICE_DOCUMENT",
                docId.toString(), "RETENTION_DISPOSITION",
                previous != null ? previous.name() : null, target.name(), null, null);

        return toDto(saved);
    }

    private RetentionPendingDocumentDTO toDto(InvoiceDocument d) {
        return new RetentionPendingDocumentDTO(
                d.getId(),
                d.getInvoice() != null ? d.getInvoice().getId() : null,
                d.getOriginalFilename(),
                d.getUploadedAt(),
                d.getRetentionDisposition());
    }
}
```

- [ ] **Step 6 : Lancer pour vérifier le succès**

Run : `./mvnw.cmd test -Dtest=RetentionDispositionServiceTest`
Expected : PASS (5 tests).

- [ ] **Step 7 : Commit**
```bash
git add src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionPendingDocumentDTO.java \
        src/main/java/com/oct/invoicesystem/domain/retention/dto/RetentionDispositionRequest.java \
        src/main/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionService.java \
        src/test/java/com/oct/invoicesystem/domain/retention/service/RetentionDispositionServiceTest.java
git commit -m "feat(retention): RetentionDispositionService (list pending + set disposition)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4 : Contrôleur + i18n backend

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionControllerIntegrationTest.java`
- Modify: `src/main/resources/messages_fr.properties`, `src/main/resources/messages_en.properties`

**Interfaces:**
- Consumes : `RetentionDispositionService` (Task 3), `RetentionDispositionRequest`, `RetentionPendingDocumentDTO`, `ApiResponse.success(T)` / `ApiResponse.success(T, String)`, `@AuthenticationPrincipal User`.
- Produces : `GET /api/v1/retention/pending-documents`, `PUT /api/v1/retention/documents/{id}/disposition`.

- [ ] **Step 1 : Écrire les tests d'intégration (RED)**

`src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionControllerIntegrationTest.java` :
```java
package com.oct.invoicesystem.domain.retention.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.retention.dto.RetentionDispositionRequest;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.service.RetentionDispositionService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetentionDispositionControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RetentionDispositionService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void pending_asAdmin_returnsOk() throws Exception {
        when(service.listPendingExpired()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/retention/pending-documents"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void pending_asDaf_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/retention/pending-documents"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setDisposition_asAdmin_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setDisposition(eq(id), eq(RetentionDisposition.RETAINED), any()))
                .thenReturn(new RetentionPendingDocumentDTO(id, UUID.randomUUID(), "old.pdf", null, RetentionDisposition.RETAINED));
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.RETAINED))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void setDisposition_asDaf_returnsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.RETAINED))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setDisposition_unknownDoc_returnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setDisposition(eq(id), eq(RetentionDisposition.PURGED), any()))
                .thenThrow(new ResourceNotFoundException("Document not found with id " + id));
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.PURGED))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setDisposition_nullDisposition_returnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2 : Lancer pour vérifier l'échec**

Run : `./mvnw.cmd test -Dtest=RetentionDispositionControllerIntegrationTest`
Expected : FAIL — endpoints 404 (contrôleur inexistant) / compilation.

- [ ] **Step 3 : Implémenter le contrôleur**

`src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionController.java` :
```java
package com.oct.invoicesystem.domain.retention.controller;

import com.oct.invoicesystem.domain.retention.dto.RetentionDispositionRequest;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.service.RetentionDispositionService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN disposition of documents past their retention horizon (M10 #10 refinement).
 * Technical/compliance setting with no financial data — ADMIN only (SoD, PROB-065).
 */
@RestController
@RequestMapping("/api/v1/retention")
@RequiredArgsConstructor
@Tag(name = "Retention Disposition", description = "Disposition of documents past retention horizon")
public class RetentionDispositionController {

    private final RetentionDispositionService service;

    @GetMapping("/pending-documents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List documents past retention horizon still awaiting disposition")
    public ApiResponse<List<RetentionPendingDocumentDTO>> pending() {
        return ApiResponse.success(service.listPendingExpired());
    }

    @PutMapping("/documents/{id}/disposition")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set a document's retention disposition (RETAINED or PURGED)")
    public ApiResponse<RetentionPendingDocumentDTO> setDisposition(
            @PathVariable UUID id,
            @Valid @RequestBody RetentionDispositionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(
                service.setDisposition(id, request.disposition(), currentUser),
                "retention.disposition.updated");
    }
}
```

- [ ] **Step 4 : Lancer pour vérifier le succès**

Run : `./mvnw.cmd test -Dtest=RetentionDispositionControllerIntegrationTest`
Expected : PASS (6 tests).

- [ ] **Step 5 : Ajouter la clé i18n backend (ISO-8859-1 safe, ASCII)**

Vérifier d'abord si la clé existe : `grep -n "retention.disposition.updated" src/main/resources/messages_fr.properties` (attendu : rien).
Ajouter la même ligne ASCII (sans accent → sûr en ISO-8859-1) à la fin des deux fichiers :
- `src/main/resources/messages_fr.properties` :
  `retention.disposition.updated=Disposition de retention mise a jour`
- `src/main/resources/messages_en.properties` :
  `retention.disposition.updated=Retention disposition updated`
(Note : valeur FR volontairement sans accent pour rester ASCII-safe en Latin-1 ; si des accents sont souhaités, les ajouter via `iconv -f UTF-8 -t ISO-8859-1`.)

Vérifier l'encodage non corrompu : `file src/main/resources/messages_fr.properties` (doit rester ISO-8859 / Latin-1, pas UTF-8 avec BOM).

- [ ] **Step 6 : Commit**
```bash
git add src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionController.java \
        src/test/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionControllerIntegrationTest.java \
        src/main/resources/messages_fr.properties src/main/resources/messages_en.properties
git commit -m "feat(retention): RetentionDispositionController (ADMIN) + i18n key

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5 : Libellé carte (i18n frontend) + matrice + vérification finale

**Files:**
- Modify: `frontend/src/i18n/fr.json`, `frontend/src/i18n/en.json`
- Modify: `docs/COMPLIANCE_MATRIX.md`

**Interfaces:** aucune nouvelle (raffinement de libellé).

- [ ] **Step 1 : Affiner le libellé `flagged` (fr/en)**

Dans `frontend/src/i18n/fr.json`, sous `admin.audit.retention`, remplacer la valeur de la clé `flagged` :
- de : `"flagged": "Documents marqués",`
- à : `"flagged": "Documents périmés en attente",`

Dans `frontend/src/i18n/en.json`, sous `admin.audit.retention`, remplacer :
- de : `"flagged": "Flagged documents",`
- à : `"flagged": "Expired documents pending",`

- [ ] **Step 2 : Valider le JSON + types + tests front**

Run : `cd frontend && node -e "JSON.parse(require('fs').readFileSync('src/i18n/fr.json','utf8')); JSON.parse(require('fs').readFileSync('src/i18n/en.json','utf8')); console.log('JSON OK')"`
Expected : `JSON OK`.
Run : `cd frontend && npx tsc --noEmit && npx vitest run`
Expected : tsc 0 erreur ; vitest tous verts (le test du composant n'asserte pas sur ce libellé précis, il reste vert).

- [ ] **Step 3 : Note dans COMPLIANCE_MATRIX**

Dans `docs/COMPLIANCE_MATRIX.md`, ligne 335 (M10 #10, déjà ✅), compléter la note en ajoutant à la fin de la cellule de droite :
`Le compteur « périmés » reflète les documents EN ATTENTE de disposition (PENDING) ; le statut s'éteint après traitement via PUT /retention/documents/{id}/disposition (RETAINED/PURGED), ADMIN.`

- [ ] **Step 4 : Vérification backend complète**

Run : `./mvnw.cmd test`
Expected : BUILD SUCCESS, 0 failure / 0 error (tests existants + DocumentRetentionJobTest 3 + RetentionDispositionServiceTest 5 + RetentionDispositionControllerIntegrationTest 6).

- [ ] **Step 5 : Commit**
```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json docs/COMPLIANCE_MATRIX.md
git commit -m "feat(retention): card label reflects pending disposition + matrix note

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**1. Couverture du spec :**
- §3.1 enum → Task 1 step 1. ✅
- §3.2 champs entité → Task 1 step 2. ✅
- §3.3 migration V63 → Task 1 step 3 (V62 confirmé comme dernière). ✅
- §4.1 requête repository → Task 2 step 3. ✅
- §4.2 job ne compte que PENDING → Task 2 step 4 (+ tests adaptés step 1). ✅
- §4.3 evaluateCompliance inchangé → respecté (aucune tâche ne le touche). ✅
- §5.1 DTO → Task 3 step 1. ✅
- §5.2 service (list + setDisposition, refus PENDING, 404, audit) → Task 3 steps 3/5 + tests. ✅
- §5.3 contrôleur (2 endpoints ADMIN) → Task 4. ✅ ; §5.3 request record → Task 3 step 2. ✅
- §5.4 i18n backend → Task 4 step 5. ✅
- §5.5 tests service + contrôleur → Task 3 + Task 4. ✅
- §6.1 libellé carte → Task 5 step 1. ✅
- §6.2 matrice → Task 5 step 3. ✅
- §7 critère de fin → Task 5 steps 2/4. ✅

**2. Placeholders :** aucun TBD ; tout le code montré en entier.

**3. Cohérence des types :** `RetentionDisposition{PENDING,RETAINED,PURGED}`, `findByUploadedAtBeforeAndRetentionDisposition(Instant,RetentionDisposition)`, `RetentionPendingDocumentDTO(id,invoiceId,originalFilename,uploadedAt,retentionDisposition)`, `RetentionDispositionRequest(disposition)`, `listPendingExpired()`, `setDisposition(UUID,RetentionDisposition,User)`, endpoints `/api/v1/retention/pending-documents` & `/documents/{id}/disposition`, action audit `RETENTION_DISPOSITION` / entité `INVOICE_DOCUMENT` — identiques entre toutes les tâches. ✅
