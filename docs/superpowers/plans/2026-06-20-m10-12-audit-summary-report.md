# Rapport de synthèse audit agrégé (M10 #12) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un rapport de synthèse audit agrégé (totaux par action / utilisateur / type d'entité / jour) sur une plage de dates, scopé par rôle (ADMIN=système, DAF=financier), affiché dans un onglet « Synthèse » et exportable.

**Architecture:** Agrégation à la volée via requêtes `GROUP BY` sur `audit_logs` (aucune nouvelle table). Service assemble un `AuditSummaryDTO`. Controller expose deux endpoints scopés + un export réutilisant `TabularExportService`. Frontend : composant partagé `<AuditSummary scope=...>` dans un onglet des pages audit.

**Tech Stack:** Spring Boot 3 / JPA / JUnit5 + Mockito ; React 18 + TS / react-query / vitest ; iText+POI via `TabularExportService`.

## Global Constraints

- `@PreAuthorize` sur chaque méthode controller ; réponses en `ApiResponse<T>` ; DTO (records), jamais d'entité exposée.
- Séparation de rôles : ADMIN → `SYSTEM_ACTIONS`, DAF → `FINANCIAL_ACTIONS` (constantes existantes dans `AuditController`). SoD (PROB-065) : pas de croisement.
- `messages_fr.properties` est en ISO-8859-1 — ajouter les clés FR via `iconv`, sans em-dash ni guillemets courbes (cf. mémoire messages-fr-iso-8859-1). Parité FR/EN.
- `audit_logs` est append-only — rapport en lecture seule, aucune écriture.
- Critère de fin : `mvnw.cmd test` 0 échec, `tsc` 0, `vitest` vert (règle no-failures-on-task-completion).
- Commits atomiques. Pas de push tant que unpushed < 10 (règle push-every-10-commits ; actuellement 3 non poussés).

---

### Task 1: DTOs de synthèse

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/audit/dto/CountEntry.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/audit/dto/AuditSummaryDTO.java`

**Interfaces:**
- Produces: `record CountEntry(String label, long count)` ; `record AuditSummaryDTO(LocalDate from, LocalDate to, long totalEvents, List<CountEntry> byAction, List<CountEntry> byUser, List<CountEntry> byEntityType, List<CountEntry> byDay)`.

- [ ] **Step 1: Créer CountEntry**

```java
package com.oct.invoicesystem.domain.audit.dto;

/** One aggregated bucket of the audit summary report: a label and its event count. */
public record CountEntry(String label, long count) {}
```

- [ ] **Step 2: Créer AuditSummaryDTO**

```java
package com.oct.invoicesystem.domain.audit.dto;

import java.time.LocalDate;
import java.util.List;

/** Aggregated audit summary over [from, to], grouped along four dimensions (M10 #12). */
public record AuditSummaryDTO(
        LocalDate from,
        LocalDate to,
        long totalEvents,
        List<CountEntry> byAction,
        List<CountEntry> byUser,
        List<CountEntry> byEntityType,
        List<CountEntry> byDay) {}
```

- [ ] **Step 3: Compiler**

Run: `./mvnw.cmd -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/audit/dto/CountEntry.java src/main/java/com/oct/invoicesystem/domain/audit/dto/AuditSummaryDTO.java
git commit -m "feat(m10-12): audit summary DTOs (CountEntry, AuditSummaryDTO)"
```

---

### Task 2: Requêtes d'agrégation (repository)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/repository/AuditLogRepository.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/audit/repository/AuditLogSummaryRepositoryTest.java` (create)

**Interfaces:**
- Produces (all return `List<Object[]>` of `[label, count]`):
  - `summaryByAction(Instant from, Instant to, List<String> allowedActions)`
  - `summaryByEntityType(Instant from, Instant to, List<String> allowedActions)`
  - `summaryByUser(Instant from, Instant to, List<String> allowedActions)` — label = username or null
  - `summaryByDay(Instant from, Instant to, List<String> allowedActions)` — label = `java.sql.Date` / date, count

- [ ] **Step 1: Écrire le test repository (échoue)**

```java
package com.oct.invoicesystem.domain.audit.repository;

import com.oct.invoicesystem.domain.audit.model.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AuditLogSummaryRepositoryTest {

    @Autowired private AuditLogRepository repo;

    private final Instant now = Instant.now();

    private AuditLog log(String action, String entityType, Instant at) {
        return AuditLog.builder().action(action).entityType(entityType)
                .entityId("E1").createdAt(at).build();
    }

    @BeforeEach
    void seed() {
        repo.save(log("LOGIN", "User", now));
        repo.save(log("LOGIN", "User", now.minus(1, ChronoUnit.DAYS)));
        repo.save(log("USER_CREATE", "User", now));
        repo.save(log("INVOICE_CREATE", "Invoice", now)); // hors SYSTEM scope
        repo.save(log("LOGIN", "User", now.minus(40, ChronoUnit.DAYS))); // hors fenêtre
    }

    private final List<String> systemActions = List.of("LOGIN", "USER_CREATE");

    @Test
    void summaryByAction_respectsWindowAndAllowedActions() {
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        List<Object[]> rows = repo.summaryByAction(from, to, systemActions);
        // LOGIN=2 (le 3e est hors fenêtre), USER_CREATE=1, INVOICE_CREATE exclu (hors scope)
        assertThat(rows).extracting(r -> (String) r[0]).containsExactlyInAnyOrder("LOGIN", "USER_CREATE");
        long loginCount = rows.stream().filter(r -> r[0].equals("LOGIN")).mapToLong(r -> (long) r[1]).sum();
        assertThat(loginCount).isEqualTo(2);
    }

    @Test
    void summaryByEntityType_groupsByEntity() {
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        List<Object[]> rows = repo.summaryByEntityType(from, to, systemActions);
        assertThat(rows).extracting(r -> (String) r[0]).containsExactly("User"); // Invoice exclu (scope)
    }

    @Test
    void summaryByDay_groupsByCalendarDay() {
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        List<Object[]> rows = repo.summaryByDay(from, to, systemActions);
        // au moins 2 jours distincts (aujourd'hui + hier)
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);
    }
}
```

- [ ] **Step 2: Lancer le test (échoue à la compilation : méthodes absentes)**

Run: `./mvnw.cmd -q -o test -Dtest=AuditLogSummaryRepositoryTest`
Expected: échec compilation / méthodes introuvables.

- [ ] **Step 3: Ajouter les requêtes au repository**

Ajouter dans `AuditLogRepository` (après `countByUserSinceAndAction`) :

```java
    // M10 #12 — aggregated summary report queries (role-scoped via allowedActions IN-clause).
    @org.springframework.data.jpa.repository.Query("""
        SELECT a.action, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY a.action ORDER BY COUNT(a) DESC
    """)
    java.util.List<Object[]> summaryByAction(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);

    @org.springframework.data.jpa.repository.Query("""
        SELECT a.entityType, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY a.entityType ORDER BY COUNT(a) DESC
    """)
    java.util.List<Object[]> summaryByEntityType(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);

    @org.springframework.data.jpa.repository.Query("""
        SELECT a.user.username, COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY a.user.username ORDER BY COUNT(a) DESC
    """)
    java.util.List<Object[]> summaryByUser(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);

    @org.springframework.data.jpa.repository.Query("""
        SELECT CAST(a.createdAt AS date), COUNT(a) FROM AuditLog a
        WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
        GROUP BY CAST(a.createdAt AS date) ORDER BY CAST(a.createdAt AS date) ASC
    """)
    java.util.List<Object[]> summaryByDay(java.time.Instant from, java.time.Instant to, java.util.List<String> allowedActions);
```

Note : `a.user.username` produit un LEFT JOIN implicite ; les logs sans user donnent label `null` (géré au service). Si `CAST(a.createdAt AS date)` échoue selon le dialecte H2/PG au test, remplacer `summaryByDay` par une `@Query(nativeQuery = true)` : `SELECT CAST(created_at AS date) AS d, COUNT(*) FROM audit_logs WHERE created_at >= :from AND created_at < :to AND action IN (:allowedActions) GROUP BY d ORDER BY d`.

- [ ] **Step 4: Lancer le test (passe)**

Run: `./mvnw.cmd -q -o test -Dtest=AuditLogSummaryRepositoryTest`
Expected: PASS (3 tests). Si `summaryByDay` casse → appliquer le repli natif ci-dessus, relancer.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/audit/repository/AuditLogRepository.java src/test/java/com/oct/invoicesystem/domain/audit/repository/AuditLogSummaryRepositoryTest.java
git commit -m "feat(m10-12): role-scoped GROUP BY summary queries on audit_logs"
```

---

### Task 3: Méthode service `summarize`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/service/AuditService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/service/AuditServiceImpl.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/audit/service/AuditServiceTest.java` (modify — ajouter cas)

**Interfaces:**
- Consumes: les 4 méthodes repo de Task 2 ; DTOs de Task 1.
- Produces: `AuditSummaryDTO summarize(LocalDate from, LocalDate to, List<String> allowedActions)` sur `AuditService`.

- [ ] **Step 1: Écrire le test service (échoue)**

Ajouter dans `AuditServiceTest` (les imports `java.time.*`, `java.util.List`, `org.mockito.Mockito.when` sont nécessaires) :

```java
    @Test
    void summarize_assemblesDtoAndComputesTotal() {
        java.time.LocalDate from = java.time.LocalDate.now().minusDays(30);
        java.time.LocalDate to = java.time.LocalDate.now();
        java.util.List<String> actions = java.util.List.of("LOGIN", "USER_CREATE");

        when(auditLogRepository.summaryByAction(any(), any(), eq(actions)))
                .thenReturn(java.util.List.of(new Object[]{"LOGIN", 5L}, new Object[]{"USER_CREATE", 2L}));
        when(auditLogRepository.summaryByUser(any(), any(), eq(actions)))
                .thenReturn(java.util.List.of(new Object[]{"alice", 4L}, new Object[]{null, 3L}));
        when(auditLogRepository.summaryByEntityType(any(), any(), eq(actions)))
                .thenReturn(java.util.List.of(new Object[]{"User", 7L}));
        when(auditLogRepository.summaryByDay(any(), any(), eq(actions)))
                .thenReturn(java.util.List.of(new Object[]{java.sql.Date.valueOf(to), 7L}));

        com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO dto =
                auditService.summarize(from, to, actions);

        assertEquals(7L, dto.totalEvents()); // 5 + 2
        assertEquals(2, dto.byAction().size());
        assertEquals("alice", dto.byUser().get(0).label());
        // user null -> label fallback "—"
        assertEquals("—", dto.byUser().get(1).label());
    }
```

- [ ] **Step 2: Lancer le test (échoue : méthode absente)**

Run: `./mvnw.cmd -q -o test -Dtest=AuditServiceTest#summarize_assemblesDtoAndComputesTotal`
Expected: échec compilation (`summarize` introuvable).

- [ ] **Step 3: Déclarer la méthode dans l'interface**

Dans `AuditService.java`, ajouter l'import `java.time.LocalDate` et :

```java
    /** Aggregated audit summary over [from, to], restricted to allowedActions (M10 #12). */
    com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO summarize(
            java.time.LocalDate from, java.time.LocalDate to, java.util.List<String> allowedActions);
```

- [ ] **Step 4: Implémenter dans AuditServiceImpl**

Ajouter (avec un constant `USER_FALLBACK_LABEL = "—"` en tête de classe) :

```java
    private static final String USER_FALLBACK_LABEL = "—";
    private static final int TOP_USERS = 10;

    @Override
    public com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO summarize(
            java.time.LocalDate from, java.time.LocalDate to, java.util.List<String> allowedActions) {
        java.time.Instant fromI = from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        java.time.Instant toI = to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(); // borne haute exclusive

        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byAction =
                toEntries(auditLogRepository.summaryByAction(fromI, toI, allowedActions), false);
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byUser =
                toEntries(auditLogRepository.summaryByUser(fromI, toI, allowedActions), true);
        if (byUser.size() > TOP_USERS) byUser = byUser.subList(0, TOP_USERS);
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byEntityType =
                toEntries(auditLogRepository.summaryByEntityType(fromI, toI, allowedActions), false);
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> byDay =
                toEntries(auditLogRepository.summaryByDay(fromI, toI, allowedActions), false);

        long total = byAction.stream().mapToLong(com.oct.invoicesystem.domain.audit.dto.CountEntry::count).sum();
        return new com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO(
                from, to, total, byAction, byUser, byEntityType, byDay);
    }

    private java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> toEntries(
            java.util.List<Object[]> rows, boolean fallbackNull) {
        java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> out = new java.util.ArrayList<>();
        for (Object[] r : rows) {
            String label = r[0] == null ? (fallbackNull ? USER_FALLBACK_LABEL : "") : String.valueOf(r[0]);
            long count = ((Number) r[1]).longValue();
            out.add(new com.oct.invoicesystem.domain.audit.dto.CountEntry(label, count));
        }
        return out;
    }
```

- [ ] **Step 5: Lancer le test (passe)**

Run: `./mvnw.cmd -q -o test -Dtest=AuditServiceTest`
Expected: PASS (tous les tests de la classe).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/audit/service/AuditService.java src/main/java/com/oct/invoicesystem/domain/audit/service/AuditServiceImpl.java src/test/java/com/oct/invoicesystem/domain/audit/service/AuditServiceTest.java
git commit -m "feat(m10-12): AuditService.summarize assembles role-scoped summary DTO"
```

---

### Task 4: Endpoints controller (system / financial / export)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/audit/controller/AuditController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/audit/controller/AuditSummaryControllerTest.java` (create)

**Interfaces:**
- Consumes: `auditService.summarize(...)` ; `SYSTEM_ACTIONS` / `FINANCIAL_ACTIONS` (déjà dans la classe) ; `tabularExportService`.
- Produces: endpoints `GET /summary/system`, `/summary/financial`, `/summary/export`.

- [ ] **Step 1: Écrire le test controller (échoue)**

Le projet a déjà des tests d'intégration controller : calquer sur un test existant (`@SpringBootTest @AutoConfigureMockMvc` + `@WithMockUser`). Vérifier le pattern dans un test controller voisin avant d'écrire. Test à créer :

```java
package com.oct.invoicesystem.domain.audit.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditSummaryControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test @WithMockUser(roles = "ADMIN")
    void systemSummary_allowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/system")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "DAF")
    void systemSummary_forbiddenForDaf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/system")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "DAF")
    void financialSummary_allowedForDaf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/financial")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "DAF")
    void export_systemScope_forbiddenForDaf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/export").param("scope", "system"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Lancer le test (échoue : 404)**

Run: `./mvnw.cmd -q -o test -Dtest=AuditSummaryControllerTest`
Expected: échec (endpoints absents → 404, pas les statuts attendus).

- [ ] **Step 3: Ajouter les endpoints dans AuditController**

Ajouter avant la dernière accolade :

```java
    private java.util.List<String> actionsForScope(String scope) {
        return "financial".equalsIgnoreCase(scope) ? FINANCIAL_ACTIONS : SYSTEM_ACTIONS;
    }

    private java.time.LocalDate orDefault(java.time.LocalDate v, java.time.LocalDate fallback) {
        return v == null ? fallback : v;
    }

    @GetMapping("/summary/system")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rapport de synthèse audit système", description = "Totaux agrégés des événements système (ADMIN)")
    public ApiResponse<com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO> systemSummary(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        java.time.LocalDate t = orDefault(to, java.time.LocalDate.now());
        java.time.LocalDate f = orDefault(from, t.minusDays(30));
        return ApiResponse.success(auditService.summarize(f, t, SYSTEM_ACTIONS), "audit.summary.retrieved");
    }

    @GetMapping("/summary/financial")
    @PreAuthorize("hasRole('DAF')")
    @Operation(summary = "Rapport de synthèse audit financier", description = "Totaux agrégés des événements financiers (DAF)")
    public ApiResponse<com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO> financialSummary(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        java.time.LocalDate t = orDefault(to, java.time.LocalDate.now());
        java.time.LocalDate f = orDefault(from, t.minusDays(30));
        return ApiResponse.success(auditService.summarize(f, t, FINANCIAL_ACTIONS), "audit.summary.retrieved");
    }

    @GetMapping("/summary/export")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")
    @Operation(summary = "Export du rapport de synthèse audit (csv|excel|pdf)")
    public org.springframework.http.ResponseEntity<byte[]> exportSummary(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "system") String scope,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            org.springframework.security.core.Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isDaf = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DAF"));
        boolean financial = "financial".equalsIgnoreCase(scope);
        // SoD guard: ADMIN may only export system, DAF may only export financial.
        if ((financial && !isDaf) || (!financial && !isAdmin)) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        java.time.LocalDate t = orDefault(to, java.time.LocalDate.now());
        java.time.LocalDate f = orDefault(from, t.minusDays(30));
        var summary = auditService.summarize(f, t, actionsForScope(scope));
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        java.util.List<String> headers = java.util.List.of("Dimension", "Libelle", "Nombre");
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        appendDim(rows, "Action", summary.byAction());
        appendDim(rows, "Utilisateur", summary.byUser());
        appendDim(rows, "Entite", summary.byEntityType());
        appendDim(rows, "Jour", summary.byDay());
        byte[] body = tabularExportService.export(fmt, "Audit Summary", headers, rows);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=audit_summary." + fmt.extension)
                .contentType(org.springframework.http.MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

    private void appendDim(java.util.List<java.util.List<String>> rows, String dim,
                           java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> entries) {
        for (var e : entries) {
            rows.add(java.util.List.of(dim, e.label() == null ? "" : e.label(), String.valueOf(e.count())));
        }
    }
```

- [ ] **Step 4: Lancer le test (passe)**

Run: `./mvnw.cmd -q -o test -Dtest=AuditSummaryControllerTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/audit/controller/AuditController.java src/test/java/com/oct/invoicesystem/domain/audit/controller/AuditSummaryControllerTest.java
git commit -m "feat(m10-12): summary endpoints (system/financial/export) with SoD guard"
```

---

### Task 5: Suite backend complète + i18n

**Files:**
- Modify: `src/main/resources/messages_en.properties`
- Modify: `src/main/resources/messages_fr.properties` (via iconv)

- [ ] **Step 1: Ajouter les clés EN**

Append à `messages_en.properties` :
```
audit.summary.retrieved=Audit summary retrieved
```

- [ ] **Step 2: Ajouter la clé FR (ISO-8859-1, via iconv)**

```bash
printf 'audit.summary.retrieved=Synthese d audit recuperee\n' | iconv -f UTF-8 -t ISO-8859-1 >> src/main/resources/messages_fr.properties
```
(Pas d'accent problématique ici ; rester ASCII pour sécurité.)

- [ ] **Step 3: Lancer toute la suite backend**

Run: `./mvnw.cmd -q -o test`
Expected: 0 failure, 0 error.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/messages_en.properties src/main/resources/messages_fr.properties
git commit -m "feat(m10-12): i18n key for audit summary (FR/EN)"
```

---

### Task 6: Composant frontend `<AuditSummary>`

**Files:**
- Create: `frontend/src/components/audit/AuditSummary.tsx`
- Test: `frontend/src/components/audit/AuditSummary.test.tsx` (create)

**Interfaces:**
- Consumes: `GET /audit-logs/summary/{scope}` → `ApiResponse<AuditSummaryDTO>` ; `ExportMenu`.
- Produces: `export default function AuditSummary({ scope }: { scope: 'system' | 'financial' })`.

- [ ] **Step 1: Écrire le test vitest (échoue)**

Calquer sur un test de composant existant pour le wrapper QueryClient/i18n (vérifier `frontend/src/test/` ou un `*.test.tsx` voisin avant d'écrire les imports exacts). Squelette :

```tsx
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { vi, describe, it, expect, beforeEach } from 'vitest'
import AuditSummary from './AuditSummary'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient')

const mockSummary = {
  from: '2026-05-21', to: '2026-06-20', totalEvents: 7,
  byAction: [{ label: 'LOGIN', count: 5 }, { label: 'USER_CREATE', count: 2 }],
  byUser: [{ label: 'alice', count: 4 }],
  byEntityType: [{ label: 'User', count: 7 }],
  byDay: [{ label: '2026-06-20', count: 7 }],
}

function renderWithProviders() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><AuditSummary scope="system" /></QueryClientProvider>)
}

describe('AuditSummary', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders aggregated panels from the API', async () => {
    ;(apiClient.get as any).mockResolvedValue({ data: { data: mockSummary } })
    renderWithProviders()
    await waitFor(() => expect(screen.getByText('LOGIN')).toBeInTheDocument())
    expect(screen.getByText('alice')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Lancer le test (échoue : composant absent)**

Run: `cd frontend && npx vitest run src/components/audit/AuditSummary.test.tsx`
Expected: échec (module introuvable).

- [ ] **Step 3: Implémenter AuditSummary.tsx**

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'
import { Loader2, BarChart3 } from 'lucide-react'
import { ExportMenu } from '@/components/ui/ExportMenu'

interface CountEntry { label: string; count: number }
interface SummaryDTO {
  from: string; to: string; totalEvents: number
  byAction: CountEntry[]; byUser: CountEntry[]; byEntityType: CountEntry[]; byDay: CountEntry[]
}

function isoDaysAgo(n: number): string {
  const d = new Date(); d.setDate(d.getDate() - n); return d.toISOString().slice(0, 10)
}

function CountPanel({ title, entries }: { title: string; entries: CountEntry[] }) {
  return (
    <div className="bg-white rounded-xl border p-4">
      <h3 className="font-semibold text-gray-800 mb-3">{title}</h3>
      {entries.length === 0 ? (
        <p className="text-sm text-gray-400">—</p>
      ) : (
        <ul className="divide-y">
          {entries.map((e, i) => (
            <li key={i} className="flex items-center justify-between py-1.5 text-sm">
              <span className="text-gray-700 truncate">{e.label || '—'}</span>
              <span className="text-xs font-mono bg-amber-50 text-amber-700 px-2 py-0.5 rounded border border-amber-100">{e.count}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function DayBars({ title, entries }: { title: string; entries: CountEntry[] }) {
  const max = Math.max(1, ...entries.map((e) => e.count))
  return (
    <div className="bg-white rounded-xl border p-4">
      <h3 className="font-semibold text-gray-800 mb-3">{title}</h3>
      {entries.length === 0 ? <p className="text-sm text-gray-400">—</p> : (
        <ul className="space-y-1.5">
          {entries.map((e, i) => (
            <li key={i} className="flex items-center gap-2 text-xs">
              <span className="w-24 shrink-0 text-gray-500">{e.label}</span>
              <span className="h-3 bg-primary/70 rounded" style={{ width: `${(e.count / max) * 100}%` }} />
              <span className="text-gray-600">{e.count}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default function AuditSummary({ scope }: { scope: 'system' | 'financial' }) {
  const { t } = useTranslation()
  const [from, setFrom] = useState(isoDaysAgo(30))
  const [to, setTo] = useState(isoDaysAgo(0))

  const { data, isLoading } = useQuery({
    queryKey: ['audit-summary', scope, from, to],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: SummaryDTO }>(`/audit-logs/summary/${scope}`, { params: { from, to } })
      return data.data
    },
  })

  return (
    <div className="space-y-6">
      <div className="bg-white rounded-xl border p-4 flex flex-wrap items-end gap-3">
        <label className="text-sm text-gray-600">{t('admin.audit.summary.from')}
          <input type="date" value={from} onChange={(e) => setFrom(e.target.value)}
            className="block border rounded-lg px-3 py-1.5 mt-1 text-sm" />
        </label>
        <label className="text-sm text-gray-600">{t('admin.audit.summary.to')}
          <input type="date" value={to} onChange={(e) => setTo(e.target.value)}
            className="block border rounded-lg px-3 py-1.5 mt-1 text-sm" />
        </label>
        <div className="ml-auto">
          <ExportMenu endpoint="/audit-logs/summary/export" filename="audit_summary"
            params={{ scope, from, to }} />
        </div>
      </div>

      {isLoading || !data ? (
        <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
      ) : (
        <>
          <div className="bg-white rounded-xl border p-4 flex items-center gap-3">
            <BarChart3 className="w-5 h-5 text-primary" />
            <span className="text-sm text-gray-600">{t('admin.audit.summary.total')}</span>
            <span className="text-2xl font-bold text-gray-900">{data.totalEvents}</span>
          </div>
          <div className="grid gap-6 md:grid-cols-2">
            <CountPanel title={t('admin.audit.summary.byAction')} entries={data.byAction} />
            <CountPanel title={t('admin.audit.summary.byUser')} entries={data.byUser} />
            <CountPanel title={t('admin.audit.summary.byEntity')} entries={data.byEntityType} />
            <DayBars title={t('admin.audit.summary.byDay')} entries={data.byDay} />
          </div>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Lancer le test (passe)**

Run: `cd frontend && npx vitest run src/components/audit/AuditSummary.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/audit/AuditSummary.tsx frontend/src/components/audit/AuditSummary.test.tsx
git commit -m "feat(m10-12): shared AuditSummary panel component (frontend)"
```

---

### Task 7: Onglets sur les pages audit + i18n frontend

**Files:**
- Modify: `frontend/src/pages/admin/AdminAuditPage.tsx`
- Modify: `frontend/src/pages/FinancialAuditPage.tsx`
- Modify: `frontend/src/i18n/fr.json` et `frontend/src/i18n/en.json` (vérifier chemin exact des fichiers i18n frontend avant d'éditer)

**Interfaces:**
- Consumes: `<AuditSummary scope=...>` (Task 6).

- [ ] **Step 1: Repérer les fichiers i18n frontend**

Run: `ls frontend/src/i18n 2>/dev/null || find frontend/src -name "*.json" -path "*i18n*"`
Ajouter sous la clé `admin.audit.summary` (FR et EN, parité) :
- `tabJournal` / `tabSummary` / `title`
- `from` / `to` / `total`
- `byAction` / `byUser` / `byEntity` / `byDay`

FR (exemples) : `Journal`, `Synthese`, `Du`, `Au`, `Total des evenements`, `Par action`, `Par utilisateur`, `Par type d'entite`, `Par jour`.
EN : `Log`, `Summary`, `From`, `To`, `Total events`, `By action`, `By user`, `By entity type`, `By day`.

- [ ] **Step 2: Ajouter l'état d'onglet + rendu dans AdminAuditPage**

Dans `AdminAuditPage`, importer `AuditSummary` et un `useState<'journal'|'summary'>('journal')`. Sous le `<h1>` / barre de titre, ajouter une barre d'onglets :

```tsx
import AuditSummary from '@/components/audit/AuditSummary'
// ... dans le composant :
const [tab, setTab] = useState<'journal' | 'summary'>('journal')
```

Barre d'onglets (insérée juste après le bloc titre/Export, avant `<AnomalyPanel/>`) :

```tsx
<div className="flex gap-2 border-b">
  <button onClick={() => setTab('journal')}
    className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === 'journal' ? 'border-primary text-primary' : 'border-transparent text-gray-500'}`}>
    {t('admin.audit.summary.tabJournal')}
  </button>
  <button onClick={() => setTab('summary')}
    className={`px-4 py-2 text-sm font-medium border-b-2 ${tab === 'summary' ? 'border-primary text-primary' : 'border-transparent text-gray-500'}`}>
    {t('admin.audit.summary.tabSummary')}
  </button>
</div>
```

Envelopper le contenu journal existant (anomalies + recent + filtres + table) dans `{tab === 'journal' && ( ... )}` et ajouter `{tab === 'summary' && <AuditSummary scope="system" />}`. L'`ExportMenu` du titre (export brut) ne s'affiche que dans l'onglet journal.

- [ ] **Step 3: Faire de même dans FinancialAuditPage**

Identique mais `<AuditSummary scope="financial" />`. Lire d'abord `FinancialAuditPage.tsx` pour respecter sa structure (peut différer d'AdminAuditPage).

- [ ] **Step 4: Compiler le frontend**

Run: `cd frontend && npx tsc --noEmit`
Expected: 0 erreur.

- [ ] **Step 5: Lancer toute la suite vitest**

Run: `cd frontend && npx vitest run`
Expected: tout vert.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/admin/AdminAuditPage.tsx frontend/src/pages/FinancialAuditPage.tsx frontend/src/i18n
git commit -m "feat(m10-12): Summary tab on audit pages (ADMIN + DAF) + i18n"
```

---

### Task 8: Vérification finale + matrice de conformité

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md` (ligne 337, M10 #12 ; et Features M10 si applicable)

- [ ] **Step 1: Suite backend complète**

Run: `./mvnw.cmd -q -o test`
Expected: 0 failure, 0 error.

- [ ] **Step 2: Build/typecheck + vitest frontend**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: 0 erreur, tout vert.

- [ ] **Step 3: Passer M10 #12 à ✅ dans la matrice**

Remplacer la ligne 337 :
`| 12 | Audit summary reports | ✅ | Rapport de synthese agrege (totaux par action/utilisateur/entite/jour, plage de dates) en onglet "Synthese" sur /admin/audit (ADMIN, systeme) et /audit/financial (DAF, financier) ; export csv/excel/pdf ; endpoints /audit-logs/summary/{system,financial,export} avec garde SoD. |`
Ajuster aussi le compteur de synthèse (§ bas de fichier) : un 🟠 de moins.

- [ ] **Step 4: Commit final**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -m "docs(m10-12): mark M10 #12 audit summary report as compliant"
```

- [ ] **Step 5: Vérifier le compte d'unpushed (règle push-every-10-commits)**

Run: `git rev-list --count @{u}..HEAD`
Si ≥ 10 → `git push`. Sinon, ne pas pousser ; émettre un prompt de reprise pour la session suivante.

---

## Self-Review (effectuée)

- **Couverture spec** : DTOs (T1), 4 requêtes scopées (T2), summarize+top10+fallback (T3), 3 endpoints+garde SoD (T4), i18n backend (T5), composant+4 panneaux+barres jour (T6), onglets+i18n front (T7), tests/matrice (T8). Toutes les sections de la spec sont couvertes.
- **Placeholders** : aucun TODO/TBD ; code complet à chaque étape.
- **Cohérence des types** : `CountEntry(label,count)` / `AuditSummaryDTO(...)` identiques partout ; `summarize(LocalDate,LocalDate,List<String>)` cohérent service↔controller↔test ; endpoints `/summary/{system,financial,export}` cohérents back↔front.
- **Risque connu** : `CAST(a.createdAt AS date)` (byDay) — repli natif documenté dans T2 Step 3/4.
