# B1 — UI config règles d'escalade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre configurable, via un écran admin, le délai d'escalade SLA, avec escalade hiérarchique contextuelle (N1 en retard → N2 du même département, sinon DAF seul) et notification email + in-app.

**Architecture:** Calque le pattern B4 (`PaymentAlertRule`) : entité `EscalationRule` (domaine `workflow`) → repo → service → DTO/Request → controller REST `/api/v1/escalation-rules`. Le job `DeadlineReminderJob` lit les règles actives (seuil = min des `hoursAfterDeadline`), résout le destinataire depuis les `ApprovalStep` de la facture, envoie email + publie un `ApprovalEscalationEvent` persisté en notification in-app. Frontend : page admin calquée sur `PaymentAlertRulesPage`.

**Tech Stack:** Spring Boot 3.x, JPA/Hibernate, Flyway, JUnit, React 18 + TS, React Query, react-i18next.

## Global Constraints

- Réponses API toujours enveloppées dans `ApiResponse<T>`.
- `@PreAuthorize` sur chaque méthode de controller.
- Jamais d'entité JPA exposée directement → DTO.
- Flyway pour tout changement de schéma ; **prochaine version libre = V61** ; ne JAMAIS modifier une migration appliquée (PROB-009).
- Booléen nommé `active` (pas `isActive`) — piège Lombok double-préfixe (PROB-003).
- Strings user-facing via `MessageSource` (backend) / i18n (frontend), bilingues FR+EN.
- `messages_fr.properties` = **ISO-8859-1** → ajouts via `iconv`, pas d'em-dash ni guillemets courbes. `frontend/src/i18n/{fr,en}.json` = UTF-8.
- `apiClient` baseURL = `/api/v1` → endpoints frontend SANS préfixe `/api/v1`.
- Accès écran : `ROLE_ADMIN` + `ROLE_DAF` (config workflow, non financière). L'Admin n'est JAMAIS destinataire d'escalade (séparation des devoirs).
- Build frontend : `npm run build` = vite seul → lancer `npx tsc --noEmit` ET `npx vitest run` séparément. 4 échecs vitest pré-existants (InvoiceTimeline / useAuth / e2e) = hors scope.
- Commit via `git commit -F -` (heredoc). 1 commit = 1 sujet. NE PAS pousser (demander d'abord).

---

## File Structure

**Backend (domaine `com.oct.invoicesystem.domain.workflow`)**
- Create `model/EscalationRule.java` — entité (id, hoursAfterDeadline, label, active, createdBy, audit).
- Create `repository/EscalationRuleRepository.java` — `findByActiveTrue`, `findAllByOrderByHoursAfterDeadlineAsc`.
- Create `dto/EscalationRuleDTO.java` — record vue.
- Create `dto/EscalationRuleRequest.java` — record create/update validé.
- Create `service/EscalationRuleService.java` — CRUD `@Transactional` + Javadoc.
- Create `controller/EscalationRuleController.java` — REST `/api/v1/escalation-rules`.

**Backend (domaine `notification`)**
- Create `event/ApprovalEscalationEvent.java` — événement escalade (invoiceId, stepId, recipientUserId).
- Modify `event/listener/PersistNotificationListener.java` — listener `onApprovalEscalation` (notif in-app type `DEADLINE`).

**Backend (job)**
- Modify `domain/notification/scheduler/DeadlineReminderJob.java` — brancher règles actives + résolution destinataire + publier `ApprovalEscalationEvent`.

**Backend (migration)**
- Create `src/main/resources/db/migration/V61__create_escalation_rules.sql`.

**Backend (i18n)**
- Modify `messages_fr.properties` + `messages_en.properties` — clés `error.escalation_rule.*`, `escalation_rule.{created,updated,deleted}`.

**Frontend**
- Create `frontend/src/pages/admin/EscalationRulesPage.tsx` — page CRUD (calque `PaymentAlertRulesPage`).
- Modify `frontend/src/AppRoutes.tsx` — lazy import + route `/admin/escalation-rules`.
- Modify `frontend/src/components/layout/Sidebar.tsx` — entrée nav (RoleGuard ADMIN+DAF).
- Modify `frontend/src/i18n/fr.json` + `en.json` — bloc `escalationRules.*`.

**Tests**
- Create `src/test/java/.../workflow/service/EscalationRuleServiceTest.java`.
- Create `src/test/java/.../workflow/controller/EscalationRuleControllerIntegrationTest.java`.

---

## Task 1: Entité + migration `EscalationRule`

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/model/EscalationRule.java`
- Create: `src/main/resources/db/migration/V61__create_escalation_rules.sql`

**Interfaces:**
- Produces: `EscalationRule` entity with getters `getId():UUID`, `getHoursAfterDeadline():int`, `getLabel():String`, `isActive():boolean`, `getCreatedAt():Instant`, `getUpdatedAt():Instant`; builder `EscalationRule.builder().hoursAfterDeadline(int).label(String).active(boolean).createdBy(User).build()`.

- [ ] **Step 1: Create the entity** (calque exact de `PaymentAlertRule`)

```java
package com.oct.invoicesystem.domain.workflow.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A configurable SLA escalation rule (B1, M4 #11 / M6 #6). The {@link
 * com.oct.invoicesystem.domain.notification.scheduler.DeadlineReminderJob} escalates an overdue
 * approval step once it has been overdue for at least {@code hoursAfterDeadline} hours, for the
 * smallest active threshold. The recipient is derived from the approval chain (next tier in the
 * same department, otherwise the DAF) — it is NOT stored on the rule.
 */
@Entity
@Table(name = "escalation_rules")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hours_after_deadline", nullable = false)
    private int hoursAfterDeadline;

    @Column(length = 255)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
```

- [ ] **Step 2: Create the Flyway migration**

```sql
-- V61: Configurable SLA escalation rules (B1, M4 #11 / M6 #6)
CREATE TABLE escalation_rules (
    id                   UUID PRIMARY KEY,
    hours_after_deadline INTEGER NOT NULL,
    label                VARCHAR(255),
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_by           UUID REFERENCES users(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_escalation_rules_active ON escalation_rules(active);
```

> Vérifier le nom réel de la table users (`users`) et le type de PK avant d'appliquer ; aligner sur les migrations existantes (ex. `payment_alert_rules` dans une migration antérieure).

- [ ] **Step 3: Compile**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/model/EscalationRule.java src/main/resources/db/migration/V61__create_escalation_rules.sql
git commit -F - <<'EOF'
feat(b1): EscalationRule entity + V61 migration

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 2: Repository + DTO + Request

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/repository/EscalationRuleRepository.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/dto/EscalationRuleDTO.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/dto/EscalationRuleRequest.java`

**Interfaces:**
- Consumes: `EscalationRule` (Task 1).
- Produces:
  - `EscalationRuleRepository extends JpaRepository<EscalationRule, UUID>` with `List<EscalationRule> findByActiveTrue()`, `List<EscalationRule> findAllByOrderByHoursAfterDeadlineAsc()`.
  - `EscalationRuleDTO(UUID id, int hoursAfterDeadline, String label, boolean active, Instant createdAt, Instant updatedAt)`.
  - `EscalationRuleRequest(int hoursAfterDeadline, String label, boolean active)`.

- [ ] **Step 1: Create the repository**

```java
package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, UUID> {

    List<EscalationRule> findByActiveTrue();

    List<EscalationRule> findAllByOrderByHoursAfterDeadlineAsc();
}
```

- [ ] **Step 2: Create the DTO**

```java
package com.oct.invoicesystem.domain.workflow.dto;

import java.time.Instant;
import java.util.UUID;

/** View of an escalation rule (B1). */
public record EscalationRuleDTO(
        UUID id,
        int hoursAfterDeadline,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
```

- [ ] **Step 3: Create the Request**

```java
package com.oct.invoicesystem.domain.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Create/update payload for an escalation rule (B1). */
public record EscalationRuleRequest(
        @PositiveOrZero @Max(720) int hoursAfterDeadline,
        @Size(max = 255) String label,
        boolean active
) {}
```

- [ ] **Step 4: Compile**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/repository/EscalationRuleRepository.java src/main/java/com/oct/invoicesystem/domain/workflow/dto/EscalationRuleDTO.java src/main/java/com/oct/invoicesystem/domain/workflow/dto/EscalationRuleRequest.java
git commit -F - <<'EOF'
feat(b1): EscalationRule repository + DTO + request

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 3: Service (TDD)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/service/EscalationRuleService.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/service/EscalationRuleServiceTest.java`

**Interfaces:**
- Consumes: `EscalationRuleRepository`, `EscalationRuleDTO`, `EscalationRuleRequest` (Task 2), `User`.
- Produces: `EscalationRuleService` with `List<EscalationRuleDTO> list()`, `EscalationRuleDTO create(EscalationRuleRequest, User)`, `EscalationRuleDTO update(UUID, EscalationRuleRequest)`, `void delete(UUID)`. Throws `ResourceNotFoundException("error.escalation_rule.not_found")` on missing id.

- [ ] **Step 1: Write the failing test**

```java
package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleDTO;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import com.oct.invoicesystem.domain.workflow.repository.EscalationRuleRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscalationRuleServiceTest {

    @Mock EscalationRuleRepository repository;
    @InjectMocks EscalationRuleService service;

    @Test
    void create_persistsRuleAndReturnsDto() {
        EscalationRuleRequest req = new EscalationRuleRequest(24, "After 1 day", true);
        when(repository.save(any(EscalationRule.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        EscalationRuleDTO dto = service.create(req, null);

        assertThat(dto.hoursAfterDeadline()).isEqualTo(24);
        assertThat(dto.label()).isEqualTo("After 1 day");
        assertThat(dto.active()).isTrue();
    }

    @Test
    void list_returnsRulesOrderedByThreshold() {
        when(repository.findAllByOrderByHoursAfterDeadlineAsc()).thenReturn(List.of(
                EscalationRule.builder().hoursAfterDeadline(0).active(true).build(),
                EscalationRule.builder().hoursAfterDeadline(48).active(false).build()));

        List<EscalationRuleDTO> result = service.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).hoursAfterDeadline()).isEqualTo(0);
    }

    @Test
    void update_missingId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new EscalationRuleRequest(12, null, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=EscalationRuleServiceTest test`
Expected: FAIL (compilation — `EscalationRuleService` not defined)

- [ ] **Step 3: Write the service** (calque `PaymentAlertRuleService`, sans la règle d'unicité)

```java
package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleDTO;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import com.oct.invoicesystem.domain.workflow.repository.EscalationRuleRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CRUD for configurable SLA escalation rules (B1). The
 * {@link com.oct.invoicesystem.domain.notification.scheduler.DeadlineReminderJob} reads the active
 * rules to decide how long after the deadline an overdue approval step is escalated.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EscalationRuleService {

    private final EscalationRuleRepository repository;

    @Transactional(readOnly = true)
    public List<EscalationRuleDTO> list() {
        return repository.findAllByOrderByHoursAfterDeadlineAsc().stream().map(this::toDto).toList();
    }

    public EscalationRuleDTO create(EscalationRuleRequest request, User actor) {
        EscalationRule rule = EscalationRule.builder()
                .hoursAfterDeadline(request.hoursAfterDeadline())
                .label(request.label())
                .active(request.active())
                .createdBy(actor)
                .build();
        return toDto(repository.save(rule));
    }

    public EscalationRuleDTO update(UUID id, EscalationRuleRequest request) {
        EscalationRule rule = require(id);
        rule.setHoursAfterDeadline(request.hoursAfterDeadline());
        rule.setLabel(request.label());
        rule.setActive(request.active());
        return toDto(repository.save(rule));
    }

    public void delete(UUID id) {
        repository.delete(require(id));
    }

    private EscalationRule require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("error.escalation_rule.not_found"));
    }

    private EscalationRuleDTO toDto(EscalationRule r) {
        return new EscalationRuleDTO(r.getId(), r.getHoursAfterDeadline(), r.getLabel(),
                r.isActive(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=EscalationRuleServiceTest test`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/service/EscalationRuleService.java src/test/java/com/oct/invoicesystem/domain/workflow/service/EscalationRuleServiceTest.java
git commit -F - <<'EOF'
feat(b1): EscalationRuleService CRUD + unit tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 4: Controller (TDD integration)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/controller/EscalationRuleController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/controller/EscalationRuleControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `EscalationRuleService` (Task 3).
- Produces: REST endpoints under `/api/v1/escalation-rules` — `GET` (list), `POST` (create, 201), `PUT /{id}`, `DELETE /{id}`. All `@PreAuthorize("hasAnyRole('ADMIN','DAF')")`. Bodies wrapped in `ApiResponse<T>`.

- [ ] **Step 1: Write the failing integration test**

> Calquer la structure exacte sur un test d'intégration de controller existant (ex. `PaymentAlertRuleControllerIntegrationTest` s'il existe, sinon un autre `*ControllerIntegrationTest` du projet) : mêmes annotations (`@SpringBootTest` + `@AutoConfigureMockMvc` ou `@WebMvcTest`), même façon de simuler les rôles (`@WithMockUser(roles = ...)`), même setup MockMvc. Vérifier d'abord le pattern réel avant d'écrire.

```java
package com.oct.invoicesystem.domain.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EscalationRuleControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "DAF")
    void list_asDaf_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/escalation-rules"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returnsCreated() throws Exception {
        EscalationRuleRequest req = new EscalationRuleRequest(24, "After 1 day", true);
        mockMvc.perform(post("/api/v1/escalation-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void list_asAssistantComptable_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/escalation-rules"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw -q -Dtest=EscalationRuleControllerIntegrationTest test`
Expected: FAIL (404/no mapping — controller not defined)

- [ ] **Step 3: Write the controller** (calque `PaymentAlertRuleController`)

```java
package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleDTO;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import com.oct.invoicesystem.domain.workflow.service.EscalationRuleService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Configuration of SLA escalation rules (B1, M4 #11 / M6 #6). Workflow setting (non-financial):
 * accessible to ADMIN and DAF. The escalation recipient is derived from the approval chain, never
 * the Admin (separation of duties).
 */
@RestController
@RequestMapping("/api/v1/escalation-rules")
@RequiredArgsConstructor
@Tag(name = "Escalation Rules", description = "Configurable SLA escalation thresholds")
public class EscalationRuleController {

    private final EscalationRuleService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "List escalation rules")
    public ApiResponse<List<EscalationRuleDTO>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "Create an escalation rule")
    public ApiResponse<EscalationRuleDTO> create(
            @Valid @RequestBody EscalationRuleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(service.create(request, currentUser), "escalation_rule.created");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "Update an escalation rule")
    public ApiResponse<EscalationRuleDTO> update(
            @PathVariable UUID id, @Valid @RequestBody EscalationRuleRequest request) {
        return ApiResponse.success(service.update(id, request), "escalation_rule.updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "Delete an escalation rule")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null, "escalation_rule.deleted");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw -q -Dtest=EscalationRuleControllerIntegrationTest test`
Expected: PASS (3 tests). Si le `ApiResponse.success(data, key)` signature diffère, l'aligner sur l'usage réel vu dans `PaymentAlertRuleController`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/controller/EscalationRuleController.java src/test/java/com/oct/invoicesystem/domain/workflow/controller/EscalationRuleControllerIntegrationTest.java
git commit -F - <<'EOF'
feat(b1): EscalationRule REST controller + integration tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 5: Événement + notification in-app à l'escalade

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/notification/event/ApprovalEscalationEvent.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListener.java`

**Interfaces:**
- Consumes: `Notification`, `NotificationType.DEADLINE`, `InvoiceRepository`, `UserRepository`.
- Produces: `ApprovalEscalationEvent(Object source, UUID invoiceId, UUID recipientUserId)` with getters `getInvoiceId():UUID`, `getRecipientUserId():UUID`; listener `onApprovalEscalation(ApprovalEscalationEvent)` persisting one `Notification` of type `DEADLINE` to the recipient user.

> Avant d'écrire l'event : ouvrir un event existant (ex. `ApprovalDeadlineEvent.java`) pour copier la forme exacte (extends `ApplicationEvent` ? champs `final` ? constructeur). Aligner dessus.

- [ ] **Step 1: Create the event** (forme alignée sur `ApprovalDeadlineEvent`)

```java
package com.oct.invoicesystem.domain.notification.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/** Published when an overdue approval step is escalated (B1). Carries the resolved recipient. */
public class ApprovalEscalationEvent extends ApplicationEvent {
    private final UUID invoiceId;
    private final UUID recipientUserId;

    public ApprovalEscalationEvent(Object source, UUID invoiceId, UUID recipientUserId) {
        super(source);
        this.invoiceId = invoiceId;
        this.recipientUserId = recipientUserId;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getRecipientUserId() { return recipientUserId; }
}
```

- [ ] **Step 2: Add the listener method to `PersistNotificationListener`**

Add a new `@Async @EventListener` method (after `onApprovalDeadline`). It looks up the invoice and the recipient user, then persists a `DEADLINE` notification via the existing private `save(...)` helper:

```java
    /**
     * Persist an in-app escalation notification for the resolved recipient (B1).
     */
    @Async
    @EventListener
    public void onApprovalEscalation(ApprovalEscalationEvent event) {
        log.info("Persisting escalation notification for invoice {} → user {}",
                event.getInvoiceId(), event.getRecipientUserId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice ->
            userRepository.findById(event.getRecipientUserId()).ifPresent(user ->
                save(user, invoice,
                        "URGENT — Escalade SLA",
                        "URGENT — SLA escalation",
                        "La facture " + invoice.getReferenceNumber() + " a dépassé son délai d'approbation et vous est escaladée. Action requise.",
                        "Invoice " + invoice.getReferenceNumber() + " has exceeded its approval deadline and has been escalated to you. Action required.",
                        NotificationType.DEADLINE)));
    }
```

> Confirmer que `userRepository.findById(UUID)` existe (JpaRepository → oui) et que l'import `import com.oct.invoicesystem.domain.notification.event.*;` couvre déjà le nouvel event (le listener importe le package event en wildcard — vérifié à la ligne 6 du fichier).

- [ ] **Step 3: Compile**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/notification/event/ApprovalEscalationEvent.java src/main/java/com/oct/invoicesystem/domain/notification/event/listener/PersistNotificationListener.java
git commit -F - <<'EOF'
feat(b1): in-app escalation notification (ApprovalEscalationEvent + listener)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 6: Brancher le job `DeadlineReminderJob`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/notification/scheduler/DeadlineReminderJob.java`

**Interfaces:**
- Consumes: `EscalationRuleRepository` (Task 2), `ApprovalEscalationEvent` (Task 5), existing `ApprovalStepRepository.findByInvoiceIdAndStepOrder(UUID, Integer)`, `userRepository.findActiveUsersByRoleName(String)`.
- Produces: modified escalation behaviour (threshold-gated, contextual recipient, email + in-app, DAF-only last resort).

- [ ] **Step 1: Inject `EscalationRuleRepository`**

Add field after `paymentAlertRuleRepository`:

```java
    private final com.oct.invoicesystem.domain.workflow.repository.EscalationRuleRepository escalationRuleRepository;
```

- [ ] **Step 2: Compute the effective threshold and gate escalation**

Replace the start of the `// ── SLA Escalations ──` block. Compute the smallest active `hoursAfterDeadline` (default 0 when no active rule), and only escalate a step whose `hoursOverdue >= threshold`:

```java
        // Effective escalation threshold = smallest active rule (0 = immediate, historical default)
        int escalationThresholdHours = escalationRuleRepository.findByActiveTrue().stream()
                .filter(EscalationRule::isActive)
                .mapToInt(EscalationRule::getHoursAfterDeadline)
                .min()
                .orElse(0);
```

(Import `com.oct.invoicesystem.domain.workflow.model.EscalationRule`.)

Then inside the `for (ApprovalStep step : overdue)` loop, after computing `hoursOverdue`, skip steps not yet past the threshold:

```java
                    if (hoursOverdue < escalationThresholdHours) continue;
```

- [ ] **Step 3: Replace DAF+Admin recipients with contextual resolution**

Replace the manager-notification block (the `for (User manager : concat(dafUsers, adminUsers))` loop) with contextual resolution. Remove the now-unused `adminUsers` and `dafUsers` precomputation if no longer needed elsewhere; resolve per step:

```java
                    // Contextual escalation recipient (B1):
                    // N1 overdue in a 2-tier dept → the N2 approver; otherwise → DAF only.
                    User recipient = null;
                    if (step.getStepOrder() != null && step.getStepOrder() == 1) {
                        recipient = approvalStepRepository
                                .findByInvoiceIdAndStepOrder(invoice.getId(), 2)
                                .map(ApprovalStep::getApprover)
                                .orElse(null);
                    }

                    java.util.List<User> recipients = (recipient != null)
                            ? java.util.List.of(recipient)
                            : userRepository.findActiveUsersByRoleName("ROLE_DAF");

                    for (User mgr : recipients) {
                        emailService.sendEmail(
                                mgr.getEmail(),
                                "🚨 Escalade SLA — Facture bloquée / SLA Escalation",
                                "sla-escalation-manager",
                                vars
                        );
                        // In-app notification (B1)
                        eventPublisher.publishEvent(
                                new com.oct.invoicesystem.domain.notification.event.ApprovalEscalationEvent(
                                        this, invoice.getId(), mgr.getId()));
                    }
```

> Supprimer la déclaration `List<User> adminUsers = ...` devenue inutile (l'Admin n'est plus destinataire). Garder `dafUsers` seulement s'il sert encore ; sinon le retirer aussi. Vérifier qu'aucune autre ligne ne référence `adminUsers`/`concat` après modification ; retirer `concat` si plus utilisé.

- [ ] **Step 4: Update the Javadoc of `sendDeadlineReminders`**

Change the escalation line to reflect the new behaviour:

```java
     * Escalation → next approval tier in the same department (contextual), else DAF only,
     *              once overdue past the smallest active EscalationRule threshold (B1).
     *              Sends email + persists an in-app notification. Admin is never a recipient.
```

- [ ] **Step 5: Compile + run the existing notification/workflow tests**

Run: `./mvnw -q -DskipTests compile`
Expected: BUILD SUCCESS

Run: `./mvnw -q -Dtest=EscalationRuleServiceTest,EscalationRuleControllerIntegrationTest test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/notification/scheduler/DeadlineReminderJob.java
git commit -F - <<'EOF'
feat(b1): wire escalation rules into DeadlineReminderJob

Threshold-gated escalation (min active rule), contextual recipient
(N2 same dept, else DAF only), email + in-app notification. Admin no
longer receives escalation emails (separation of duties).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 7: i18n backend (messages_fr / messages_en)

**Files:**
- Modify: `src/main/resources/messages_fr.properties` (ISO-8859-1)
- Modify: `src/main/resources/messages_en.properties` (UTF-8/ASCII)

**Interfaces:**
- Produces keys consumed by Tasks 3/4 services & controller: `error.escalation_rule.not_found`, `escalation_rule.created`, `escalation_rule.updated`, `escalation_rule.deleted`.

- [ ] **Step 1: Append the English keys**

Add to `messages_en.properties`:

```properties
error.escalation_rule.not_found=Escalation rule not found
escalation_rule.created=Escalation rule created
escalation_rule.updated=Escalation rule updated
escalation_rule.deleted=Escalation rule deleted
```

- [ ] **Step 2: Append the French keys via iconv** (ISO-8859-1 — no curly quotes/em-dash)

Create a temp UTF-8 snippet then convert and append:

```bash
cat > /tmp/b1_fr.txt <<'EOF'
error.escalation_rule.not_found=Regle d'escalade introuvable
escalation_rule.created=Regle d'escalade creee
escalation_rule.updated=Regle d'escalade mise a jour
escalation_rule.deleted=Regle d'escalade supprimee
EOF
iconv -f UTF-8 -t ISO-8859-1 /tmp/b1_fr.txt >> src/main/resources/messages_fr.properties
```

> Préférer des accents corrects en convertissant proprement : si tu veux « Règle/créée/supprimée », écris-les en UTF-8 dans le snippet — `iconv -f UTF-8 -t ISO-8859-1` les encode correctement en Latin-1. N'utilise PAS d'append UTF-8 direct (corromprait les accents). Pas d'em-dash, pas de guillemets courbes.

- [ ] **Step 3: Verify encoding integrity**

Run: `file src/main/resources/messages_fr.properties && tail -4 src/main/resources/messages_fr.properties`
Expected: ISO-8859 text; the 4 new lines readable.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages_fr.properties src/main/resources/messages_en.properties
git commit -F - <<'EOF'
feat(b1): i18n keys for escalation rules (fr ISO-8859-1 + en)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 8: Frontend — page, route, nav, i18n

**Files:**
- Create: `frontend/src/pages/admin/EscalationRulesPage.tsx`
- Modify: `frontend/src/AppRoutes.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx:186-189`
- Modify: `frontend/src/i18n/fr.json`, `frontend/src/i18n/en.json`

**Interfaces:**
- Consumes: backend `/escalation-rules` (Task 4) via `apiClient` (baseURL `/api/v1`, so path is `/escalation-rules`).
- Produces: route `/admin/escalation-rules`; nav entry visible to ADMIN+DAF.

- [ ] **Step 1: Create the page** (calque `PaymentAlertRulesPage`, champ `hoursAfterDeadline`, pas de champ rôles, note destinataire auto)

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, Plus, Trash2, Save, X, AlarmClock } from 'lucide-react'

interface EscalationRule {
  id: string
  hoursAfterDeadline: number
  label?: string | null
  active: boolean
}

interface EditorState {
  id?: string
  hoursAfterDeadline: number
  label: string
  active: boolean
}

const emptyEditor = (): EditorState => ({ hoursAfterDeadline: 24, label: '', active: true })

export default function EscalationRulesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [editor, setEditor] = useState<EditorState | null>(null)

  const { data: rules, isLoading } = useQuery({
    queryKey: ['escalation-rules'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: EscalationRule[] }>('/escalation-rules')
      return data.data ?? []
    },
  })

  const saveMutation = useMutation({
    mutationFn: (state: EditorState) => {
      const body = { hoursAfterDeadline: state.hoursAfterDeadline, label: state.label || null, active: state.active }
      return state.id
        ? apiClient.put(`/escalation-rules/${state.id}`, body)
        : apiClient.post('/escalation-rules', body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['escalation-rules'] })
      setEditor(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/escalation-rules/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['escalation-rules'] }),
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']}>
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{t('escalationRules.title', 'Escalation Rules')}</h1>
            <p className="text-sm text-gray-500 mt-0.5">{t('escalationRules.subtitle', 'Configure how long after a missed deadline an approval is escalated.')}</p>
          </div>
          {!editor && (
            <button onClick={() => setEditor(emptyEditor())}
              className="flex items-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-lg hover:bg-primary/90 text-sm font-medium">
              <Plus className="w-4 h-4" />{t('escalationRules.new', 'New rule')}
            </button>
          )}
        </div>

        <p className="text-xs text-gray-500 bg-gray-50 border rounded-lg p-3">
          {t('escalationRules.recipientNote', 'The recipient is determined automatically: the next approval tier in the same department, otherwise the DAF.')}
        </p>

        {editor ? (
          <div className="bg-white rounded-xl border p-6 space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('escalationRules.hoursAfter', 'Hours after deadline')} *</label>
                <input type="number" min={0} max={720} value={editor.hoursAfterDeadline}
                  onChange={e => setEditor({ ...editor, hoursAfterDeadline: Number(e.target.value) })}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
                <p className="text-xs text-gray-400 mt-1">{t('escalationRules.hoursHint', 'Escalation is sent once an approval is overdue by this many hours (0 = immediately).')}</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('escalationRules.label', 'Label')}</label>
                <input value={editor.label} onChange={e => setEditor({ ...editor, label: e.target.value })}
                  placeholder={t('escalationRules.labelPlaceholder', 'e.g. Escalate after 1 day')}
                  className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary/30" />
              </div>
            </div>

            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={editor.active} onChange={e => setEditor({ ...editor, active: e.target.checked })} />
              {t('escalationRules.active', 'Active')}
            </label>

            {saveMutation.isError && (
              <p className="text-sm text-red-600 bg-red-50 p-3 rounded-md border border-red-200">
                {t('escalationRules.saveError', 'Failed to save the rule.')}
              </p>
            )}

            <div className="flex items-center justify-end gap-3 pt-2 border-t">
              <button onClick={() => setEditor(null)} className="flex items-center gap-2 px-4 py-2 border rounded-lg text-sm hover:bg-gray-50">
                <X className="w-4 h-4" /> {t('app.cancel', 'Cancel')}
              </button>
              <button onClick={() => saveMutation.mutate(editor)} disabled={saveMutation.isPending}
                className="flex items-center gap-2 px-5 py-2 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-60">
                {saveMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                {t('app.save', 'Save')}
              </button>
            </div>
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <div className="bg-white rounded-xl border overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('escalationRules.hoursAfter', 'Hours after deadline')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('escalationRules.label', 'Label')}</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">{t('escalationRules.active', 'Active')}</th>
                  <th className="text-right px-4 py-3 font-medium text-gray-600">{t('app.actions', 'Actions')}</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {(!rules || rules.length === 0) ? (
                  <tr><td colSpan={4} className="text-center py-16 text-muted-foreground">
                    <AlarmClock className="w-8 h-8 mx-auto mb-2 text-gray-300" />
                    {t('escalationRules.empty', 'No rules — escalation is immediate to the DAF by default.')}
                  </td></tr>
                ) : rules.map(rule => (
                  <tr key={rule.id} className="hover:bg-gray-50 group">
                    <td className="px-4 py-3 font-medium">+{rule.hoursAfterDeadline}h</td>
                    <td className="px-4 py-3 text-gray-500">{rule.label || '—'}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-0.5 rounded ${rule.active ? 'bg-green-50 text-green-700' : 'bg-gray-100 text-gray-500'}`}>
                        {rule.active ? t('escalationRules.active', 'Active') : t('escalationRules.inactive', 'Inactive')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button onClick={() => setEditor({ id: rule.id, hoursAfterDeadline: rule.hoursAfterDeadline, label: rule.label ?? '', active: rule.active })}
                          className="text-sm text-primary hover:underline">{t('app.edit', 'Edit')}</button>
                        <button onClick={() => { if (confirm(t('escalationRules.deleteConfirm', 'Delete this rule?'))) deleteMutation.mutate(rule.id) }}
                          className="p-1 text-gray-400 hover:text-red-600" title={t('app.delete', 'Delete')}>
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </PageRoleGuard>
  )
}
```

- [ ] **Step 2: Register the route in `AppRoutes.tsx`**

Add the lazy import near the other admin imports (~line 31):

```tsx
const EscalationRulesPage = lazy(() => import('@/pages/admin/EscalationRulesPage'))
```

Add the route alongside the other `/admin/*` routes (~line 106):

```tsx
            <Route path="/admin/escalation-rules" element={<EscalationRulesPage />} />
```

- [ ] **Step 3: Add the nav entry in `Sidebar.tsx`**

The admin block (line 177) is `RoleGuard allowedRoles={['ROLE_ADMIN']}`, which would hide the link from the DAF. Wrap ONLY the new entry in its own guard so the DAF sees it too. Insert after the checklist-templates entry (line 189), still inside the admin section:

```tsx
          <RoleGuard allowedRoles={['ROLE_ADMIN', 'ROLE_DAF']} fallback={null}>
            <NavItem to="/admin/escalation-rules" icon={AlarmClock} label={t('escalationRules.navTitle', 'Escalades')} />
          </RoleGuard>
```

Add `AlarmClock` to the `lucide-react` import at the top of `Sidebar.tsx`.

> Note : si placer un `RoleGuard` DAF imbriqué dans le bloc Admin pose un souci de section visible pour le DAF (le `SectionLabel` "Admin" resterait caché pour lui), c'est acceptable pour B1 — l'entrée reste atteignable. Ne pas sur-concevoir.

- [ ] **Step 4: Add i18n blocks**

In `frontend/src/i18n/en.json`, add an `escalationRules` object:

```json
  "escalationRules": {
    "title": "Escalation Rules",
    "navTitle": "Escalations",
    "subtitle": "Configure how long after a missed deadline an approval is escalated.",
    "new": "New rule",
    "hoursAfter": "Hours after deadline",
    "hoursHint": "Escalation is sent once an approval is overdue by this many hours (0 = immediately).",
    "label": "Label",
    "labelPlaceholder": "e.g. Escalate after 1 day",
    "active": "Active",
    "inactive": "Inactive",
    "recipientNote": "The recipient is determined automatically: the next approval tier in the same department, otherwise the DAF.",
    "empty": "No rules — escalation is immediate to the DAF by default.",
    "saveError": "Failed to save the rule.",
    "deleteConfirm": "Delete this rule?"
  }
```

In `frontend/src/i18n/fr.json`, add the French counterpart (UTF-8, accents OK):

```json
  "escalationRules": {
    "title": "Règles d'escalade",
    "navTitle": "Escalades",
    "subtitle": "Configurez le délai après dépassement avant qu'une approbation soit escaladée.",
    "new": "Nouvelle règle",
    "hoursAfter": "Heures après échéance",
    "hoursHint": "L'escalade est envoyée une fois l'approbation en retard de ce nombre d'heures (0 = immédiatement).",
    "label": "Libellé",
    "labelPlaceholder": "ex. Escalader après 1 jour",
    "active": "Active",
    "inactive": "Inactive",
    "recipientNote": "Le destinataire est déterminé automatiquement : le validateur de niveau supérieur du même département, sinon le DAF.",
    "empty": "Aucune règle — l'escalade est immédiate vers le DAF par défaut.",
    "saveError": "Échec de l'enregistrement de la règle.",
    "deleteConfirm": "Supprimer cette règle ?"
  }
```

> Insérer chaque bloc à un emplacement valide JSON (ajouter la virgule sur l'objet précédent). Respecter le style d'indentation existant des fichiers i18n.

- [ ] **Step 5: Verify frontend builds & types**

Run (from `frontend/`): `npx tsc --noEmit`
Expected: no new errors.

Run: `npx vitest run`
Expected: only the 4 pre-existing failures (InvoiceTimeline / useAuth / e2e), no new failures.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/admin/EscalationRulesPage.tsx frontend/src/AppRoutes.tsx frontend/src/components/layout/Sidebar.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -F - <<'EOF'
feat(b1): escalation rules admin page + route + nav + i18n

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

## Task 9: Documentation & clôture

**Files:**
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md`
- Modify: `docs/COMPLIANCE_MATRIX.md`

- [ ] **Step 1: Log the separation-of-duties fix in KNOWN_ISSUES_REGISTRY.md**

Add a PROB entry: root cause (the job notified DAF + Admin with invoice amounts, violating `admin-no-financial-access`), solution (contextual recipient resolution, Admin removed, DAF-only last resort, Task 6), preventive rule (escalation/notification recipients must respect separation of duties — never send financial content to ROLE_ADMIN). Follow the existing PROB-NNN numbering and format.

- [ ] **Step 2: Flip the compliance rows to ✅**

In `docs/COMPLIANCE_MATRIX.md`, update:
- M4 row #11 (`Escalation rules for delayed approvals`) → ✅ with note: «UI de config `/admin/escalation-rules` (B1) : délai configurable, escalade hiérarchique contextuelle (N2 même dépt sinon DAF), email + notif in-app. Admin retiré (séparation des devoirs).»
- M6 row #6 (`Escalation rules configuration`) → ✅ (idem M4 #11).
- Update the "Gaps M4" and "Gaps M6" summary lines to remove the `#11` / `#6` escalation gap.

- [ ] **Step 3: Full backend test run**

Run: `./mvnw -q -Dtest='com.oct.invoicesystem.domain.workflow.**' test`
Expected: PASS (incl. EscalationRule service + controller tests). Note any pre-existing unrelated failures separately.

- [ ] **Step 4: Commit**

```bash
git add docs/KNOWN_ISSUES_REGISTRY.md docs/COMPLIANCE_MATRIX.md
git commit -F - <<'EOF'
docs(b1): log SoD fix + flip M4 #11 / M6 #6 to done

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

- [ ] **Step 5: Emit the resume block** for the next session (next item = B2 — UI config politique de rétention, M9 #7 / M14 #6), paste-ready, per the per-task handoff cadence.

---

## Self-Review Notes

- **Spec coverage:** entité/repo/service/controller (§2,§3 → T1-T4), résolution contextuelle (§2bis → T6), fallback historique (§4 → T6 threshold 0 + DAF), notif in-app (§4bis → T5+T6), accès ADMIN+DAF (§3 → T4 `@PreAuthorize` + T8 guards), Admin retiré + SoD (§2 → T6 + T9 log), frontend (§5 → T8), i18n (§5 → T7+T8), tests (§6 → T3,T4,T5), migration V61 (§2 → T1), clôture (§7 → T9). ✅
- **Type consistency:** `EscalationRule` getters/builder cohérents T1↔T2↔T3 ; `EscalationRuleRequest(hoursAfterDeadline, label, active)` identique T2↔T3↔T4↔T8 ; `ApprovalEscalationEvent(source, invoiceId, recipientUserId)` cohérent T5↔T6 ; endpoint `/escalation-rules` (front) vs `/api/v1/escalation-rules` (back) cohérent via baseURL. ✅
- **Known unknowns à vérifier en exécution (signalés inline):** signature exacte `ApiResponse.success(data, key)`, pattern réel des tests d'intégration controller, forme exacte de `ApplicationEvent` dans les events existants, nom/PK table `users` dans la migration. Chacun a une instruction de vérification dans sa tâche.
