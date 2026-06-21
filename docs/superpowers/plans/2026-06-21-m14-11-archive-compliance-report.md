# M14 #11 — Rapport de conformité des archives — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exposer un rapport de conformité agrégé en lecture seule, spécifique aux archives documentaires, accessible à l'ADMIN uniquement, sans aucune donnée financière.

**Architecture:** Nouveau service léger `ArchiveComplianceService` dans `domain/compliance` qui calcule 4 sections (couverture d'archivage, intégrité SHA-256, état de rétention réutilisé de M10 #10, cycle de vie des dispositions/versioning) via des requêtes `count`, sans table ni migration. Un endpoint GET ajouté au `ComplianceController` existant retourne le DTO. Une page React ADMIN affiche les 4 sections.

**Tech Stack:** Spring Boot 3 (JPA, Spring Security `@PreAuthorize`), JUnit 5 + Mockito + MockMvc, React 18 + TypeScript, @tanstack/react-query, react-i18next, vitest.

**Spec:** docs/superpowers/specs/2026-06-21-m14-11-archive-compliance-report-design.md

## Global Constraints

- Backend runner = `./mvnw.cmd` (PAS `./mvnw`). Windows / PowerShell.
- DTO only, jamais d'entité JPA exposée. Réponse enveloppée dans `ApiResponse<T>`.
- `@PreAuthorize` sur chaque méthode controller. `@Operation` Swagger sur l'endpoint.
- Rapport = ADMIN uniquement, AUCUNE donnée financière (SoD / PROB-065).
- AUCUNE migration, AUCUNE table (snapshot temps réel).
- Requêtes `count` uniquement (pas de paramètre nullable non typé → pas de SQLGrammarException, PROB-038).
- i18n frontend : `frontend/src/i18n/fr.json` + `en.json` (UTF-8, parité stricte des clés). Aucune clé backend nouvelle attendue.
- Tests unit (service) + intégration (controller) + vitest (page). Javadoc sur méthodes service publiques.
- Critère de fin : `./mvnw.cmd test` 0 échec/0 erreur + `tsc --noEmit` 0 + vitest vert.
- Git : un commit = un sujet. Ne PAS toucher aux fichiers non-suivis hors feature (CLAUDE.md, docs/audit/, etc.). Push au seuil de 10 commits non poussés.

## File Structure

**Backend (créés) :**
- `src/main/java/com/oct/invoicesystem/domain/compliance/dto/ArchiveComplianceReportDTO.java` — le record agrégé + 3 sous-records (Coverage/Integrity/Lifecycle).
- `src/main/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceService.java` — calcule et assemble le rapport.
- `src/test/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceServiceTest.java` — tests unitaires (mocks repos + RetentionPolicyService).
- `src/test/java/com/oct/invoicesystem/domain/compliance/controller/ArchiveComplianceControllerIntegrationTest.java` — IT MockMvc (ADMIN 200, DAF/ASSISTANT_COMPTABLE 403).

**Backend (modifiés) :**
- `src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceRepository.java` — `countByStatus` + `@Query` count couverture.
- `src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceDocumentRepository.java` — `countByRetentionDisposition`, `countBySupersededByDocumentIdIsNotNull`, `@Query` count `withChecksum`.
- `src/main/java/com/oct/invoicesystem/domain/compliance/controller/ComplianceController.java` — endpoint `GET /archive-report`.

**Frontend (créés) :**
- `frontend/src/pages/admin/AdminArchiveCompliancePage.tsx` — page lecture seule, 4 sections.
- `frontend/src/test/pages/AdminArchiveCompliancePage.test.tsx` — vitest (load + access-denied).

**Frontend (modifiés) :**
- `frontend/src/AppRoutes.tsx` — route `/admin/archive-compliance`.
- `frontend/src/components/layout/Sidebar.tsx` — NavItem ADMIN.
- `frontend/src/i18n/fr.json` + `frontend/src/i18n/en.json` — bloc `archiveCompliance`.

**Docs (modifiés, dernière tâche) :**
- `docs/COMPLIANCE_MATRIX.md` — M9 UI #11 🟠 → ✅ + gaps + synthèse.

---

### Task 1: Méthodes repository (count) — backend agrégation

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceRepository.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceDocumentRepository.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceServiceTest.java` (créé en Task 2 ; aucun test repo dédié ici — couvert par l'IT en Task 4 qui frappe la vraie base H2)

**Interfaces:**
- Produces (InvoiceRepository) :
  - `long countByStatus(InvoiceStatus status)`
  - `long countArchivedWithDocument()` → nb factures `status = ARCHIVE` ayant ≥1 InvoiceDocument
- Produces (InvoiceDocumentRepository) :
  - `long countByRetentionDisposition(RetentionDisposition disposition)`
  - `long countBySupersededByDocumentIdIsNotNull()`
  - `long countWithChecksum()` → nb documents avec `checksum_sha256` non vide

- [ ] **Step 1: Ajouter les méthodes à InvoiceRepository**

Ouvrir `InvoiceRepository.java`. Vérifier que `InvoiceStatus` est importé (sinon ajouter `import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;`). Ajouter à l'intérieur de l'interface :

```java
    /** Number of invoices currently in the given status (M14 #11 archive coverage). */
    long countByStatus(InvoiceStatus status);

    /** Archived invoices that have at least one stored document (M14 #11 archive coverage). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(DISTINCT i.id) FROM Invoice i "
          + "WHERE i.status = com.oct.invoicesystem.domain.invoice.model.InvoiceStatus.ARCHIVE "
          + "AND EXISTS (SELECT 1 FROM InvoiceDocument d WHERE d.invoice = i)")
    long countArchivedWithDocument();
```

- [ ] **Step 2: Ajouter les méthodes à InvoiceDocumentRepository**

Ouvrir `InvoiceDocumentRepository.java`. Ajouter à l'intérieur de l'interface :

```java
    /** Documents in a given retention disposition (M14 #11 lifecycle section). */
    long countByRetentionDisposition(RetentionDisposition disposition);

    /** Documents superseded by a newer version (M14 #11 versioning proxy). */
    long countBySupersededByDocumentIdIsNotNull();

    /** Documents carrying a non-empty SHA-256 checksum (M14 #11 integrity proof). */
    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(d) FROM InvoiceDocument d "
          + "WHERE d.checksumSha256 IS NOT NULL AND d.checksumSha256 <> ''")
    long countWithChecksum();
```

- [ ] **Step 3: Compiler pour valider la syntaxe JPQL**

Run: `./mvnw.cmd -q -DskipTests compile`
Expected: BUILD SUCCESS (les requêtes dérivées et `@Query` compilent ; la validation JPQL se fait au démarrage du contexte en Task 4).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceRepository.java src/main/java/com/oct/invoicesystem/domain/invoice/repository/InvoiceDocumentRepository.java
git commit -m "feat(m14-11): count queries for archive compliance aggregation"
```

---

### Task 2: DTO + service `ArchiveComplianceService` (TDD)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/compliance/dto/ArchiveComplianceReportDTO.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceService.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceServiceTest.java`

**Interfaces:**
- Consumes:
  - `InvoiceRepository.countByStatus(InvoiceStatus)`, `countArchivedWithDocument()` (Task 1)
  - `InvoiceDocumentRepository.count()` (hérité JpaRepository), `countWithChecksum()`, `countByRetentionDisposition(...)`, `countBySupersededByDocumentIdIsNotNull()` (Task 1)
  - `RetentionPolicyService.evaluateCompliance()` → `RetentionComplianceDTO` (existant, `domain.retention.service` / `domain.retention.dto`)
  - `RetentionDisposition` enum (`domain.invoice.model`) : `PENDING`, `RETAINED`, `PURGED`
  - `InvoiceStatus.ARCHIVE` (`domain.invoice.model`)
- Produces:
  - `ArchiveComplianceReportDTO ArchiveComplianceService.generateReport()`
  - Record `ArchiveComplianceReportDTO(Instant generatedAt, CoverageSection coverage, IntegritySection integrity, RetentionComplianceDTO retention, LifecycleSection lifecycle)`
  - Nested records: `CoverageSection(long archivedInvoices, long archivedWithDocument, long archivedWithoutDocument, double coverageRate)`, `IntegritySection(long totalDocuments, long withChecksum, long missingChecksum, double integrityRate)`, `LifecycleSection(long pending, long retained, long purged, long versionedDocuments)`

- [ ] **Step 1: Écrire le DTO**

Create `ArchiveComplianceReportDTO.java`:

```java
package com.oct.invoicesystem.domain.compliance.dto;

import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;

import java.time.Instant;

/**
 * Aggregated, read-only archive-compliance report (M14 #11). ADMIN only, no financial data.
 * Computed in real time; not persisted.
 */
public record ArchiveComplianceReportDTO(
        Instant generatedAt,
        CoverageSection coverage,
        IntegritySection integrity,
        RetentionComplianceDTO retention,
        LifecycleSection lifecycle
) {
    /** Archival coverage of invoices that reached the ARCHIVE status. */
    public record CoverageSection(
            long archivedInvoices,
            long archivedWithDocument,
            long archivedWithoutDocument,
            double coverageRate) {}

    /** Integrity proof: every stored document carries a SHA-256 checksum. */
    public record IntegritySection(
            long totalDocuments,
            long withChecksum,
            long missingChecksum,
            double integrityRate) {}

    /** Document lifecycle: retention dispositions and versioning. */
    public record LifecycleSection(
            long pending,
            long retained,
            long purged,
            long versionedDocuments) {}
}
```

- [ ] **Step 2: Écrire les tests unitaires (RED)**

Create `ArchiveComplianceServiceTest.java`:

```java
package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.model.RetentionComplianceStatus;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveComplianceServiceTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock InvoiceDocumentRepository documentRepository;
    @Mock RetentionPolicyService retentionPolicyService;
    @InjectMocks ArchiveComplianceService service;

    private RetentionComplianceDTO sampleRetention() {
        return new RetentionComplianceDTO(
                RetentionComplianceStatus.CONFORME, 10, true, Instant.now(), 0, false, Instant.now());
    }

    @Test
    void generateReport_computesCoverageRate() {
        when(invoiceRepository.countByStatus(InvoiceStatus.ARCHIVE)).thenReturn(8L);
        when(invoiceRepository.countArchivedWithDocument()).thenReturn(6L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.coverage().archivedInvoices()).isEqualTo(8L);
        assertThat(report.coverage().archivedWithDocument()).isEqualTo(6L);
        assertThat(report.coverage().archivedWithoutDocument()).isEqualTo(2L);
        assertThat(report.coverage().coverageRate()).isCloseTo(0.75, within(0.0001));
    }

    @Test
    void generateReport_coverageRateZeroWhenNoArchives() {
        when(invoiceRepository.countByStatus(InvoiceStatus.ARCHIVE)).thenReturn(0L);
        when(invoiceRepository.countArchivedWithDocument()).thenReturn(0L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.coverage().coverageRate()).isEqualTo(0.0);
        assertThat(report.coverage().archivedWithoutDocument()).isEqualTo(0L);
    }

    @Test
    void generateReport_computesIntegrityRate() {
        when(documentRepository.count()).thenReturn(10L);
        when(documentRepository.countWithChecksum()).thenReturn(10L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.integrity().totalDocuments()).isEqualTo(10L);
        assertThat(report.integrity().withChecksum()).isEqualTo(10L);
        assertThat(report.integrity().missingChecksum()).isEqualTo(0L);
        assertThat(report.integrity().integrityRate()).isEqualTo(1.0);
    }

    @Test
    void generateReport_integrityRateOneWhenNoDocuments() {
        when(documentRepository.count()).thenReturn(0L);
        when(documentRepository.countWithChecksum()).thenReturn(0L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.integrity().integrityRate()).isEqualTo(1.0);
    }

    @Test
    void generateReport_computesLifecycleCounts() {
        when(documentRepository.countByRetentionDisposition(RetentionDisposition.PENDING)).thenReturn(3L);
        when(documentRepository.countByRetentionDisposition(RetentionDisposition.RETAINED)).thenReturn(2L);
        when(documentRepository.countByRetentionDisposition(RetentionDisposition.PURGED)).thenReturn(1L);
        when(documentRepository.countBySupersededByDocumentIdIsNotNull()).thenReturn(4L);
        when(retentionPolicyService.evaluateCompliance()).thenReturn(sampleRetention());

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.lifecycle().pending()).isEqualTo(3L);
        assertThat(report.lifecycle().retained()).isEqualTo(2L);
        assertThat(report.lifecycle().purged()).isEqualTo(1L);
        assertThat(report.lifecycle().versionedDocuments()).isEqualTo(4L);
    }

    @Test
    void generateReport_embedsRetentionAndStampsTime() {
        RetentionComplianceDTO retention = sampleRetention();
        when(retentionPolicyService.evaluateCompliance()).thenReturn(retention);

        ArchiveComplianceReportDTO report = service.generateReport();

        assertThat(report.retention()).isSameAs(retention);
        assertThat(report.generatedAt()).isNotNull();
    }
}
```

- [ ] **Step 3: Lancer les tests (vérifier RED)**

Run: `./mvnw.cmd -q -Dtest=ArchiveComplianceServiceTest test`
Expected: FAIL — `ArchiveComplianceService` n'existe pas encore (erreur de compilation du test).

- [ ] **Step 4: Écrire le service (GREEN)**

Create `ArchiveComplianceService.java`:

```java
package com.oct.invoicesystem.domain.compliance.service;

import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO;
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO.CoverageSection;
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO.IntegritySection;
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO.LifecycleSection;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceDocumentRepository;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Builds the read-only archive-compliance report (M14 #11): archival coverage, SHA-256 integrity,
 * retention status (reused from M10 #10), and document lifecycle (dispositions + versioning).
 * ADMIN only, no financial data; computed in real time from count queries (no table).
 */
@Service
@RequiredArgsConstructor
public class ArchiveComplianceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDocumentRepository documentRepository;
    private final RetentionPolicyService retentionPolicyService;

    /** Computes the full archive-compliance snapshot. */
    @Transactional(readOnly = true)
    public ArchiveComplianceReportDTO generateReport() {
        return new ArchiveComplianceReportDTO(
                Instant.now(),
                buildCoverage(),
                buildIntegrity(),
                retentionPolicyService.evaluateCompliance(),
                buildLifecycle());
    }

    private CoverageSection buildCoverage() {
        long archived = invoiceRepository.countByStatus(InvoiceStatus.ARCHIVE);
        long withDoc = invoiceRepository.countArchivedWithDocument();
        long withoutDoc = archived - withDoc;
        double rate = archived == 0 ? 0.0 : (double) withDoc / archived;
        return new CoverageSection(archived, withDoc, withoutDoc, rate);
    }

    private IntegritySection buildIntegrity() {
        long total = documentRepository.count();
        long withChecksum = documentRepository.countWithChecksum();
        long missing = total - withChecksum;
        double rate = total == 0 ? 1.0 : (double) withChecksum / total;
        return new IntegritySection(total, withChecksum, missing, rate);
    }

    private LifecycleSection buildLifecycle() {
        long pending = documentRepository.countByRetentionDisposition(RetentionDisposition.PENDING);
        long retained = documentRepository.countByRetentionDisposition(RetentionDisposition.RETAINED);
        long purged = documentRepository.countByRetentionDisposition(RetentionDisposition.PURGED);
        long versioned = documentRepository.countBySupersededByDocumentIdIsNotNull();
        return new LifecycleSection(pending, retained, purged, versioned);
    }
}
```

- [ ] **Step 5: Lancer les tests (vérifier GREEN)**

Run: `./mvnw.cmd -q -Dtest=ArchiveComplianceServiceTest test`
Expected: PASS — Tests run: 6, Failures: 0, Errors: 0.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/compliance/dto/ArchiveComplianceReportDTO.java src/main/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceService.java src/test/java/com/oct/invoicesystem/domain/compliance/service/ArchiveComplianceServiceTest.java
git commit -m "feat(m14-11): ArchiveComplianceService aggregates archive compliance report"
```

---

### Task 3: Endpoint controller + IT (ADMIN 200 / DAF+ASSISTANT_COMPTABLE 403)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/compliance/controller/ComplianceController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/compliance/controller/ArchiveComplianceControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `ArchiveComplianceService.generateReport()` → `ArchiveComplianceReportDTO` (Task 2)
- Produces: `GET /api/v1/compliance/archive-report` → `ApiResponse<ArchiveComplianceReportDTO>` (ADMIN only)

- [ ] **Step 1: Écrire l'IT (RED)**

Create `ArchiveComplianceControllerIntegrationTest.java`:

```java
package com.oct.invoicesystem.domain.compliance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveComplianceControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void report_asAdmin_returnsOkWithSections() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/archive-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage").exists())
                .andExpect(jsonPath("$.data.integrity").exists())
                .andExpect(jsonPath("$.data.retention").exists())
                .andExpect(jsonPath("$.data.lifecycle").exists())
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void report_asDaf_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/archive-report"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void report_asAssistantComptable_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/archive-report"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Lancer l'IT (vérifier RED)**

Run: `./mvnw.cmd -q -Dtest=ArchiveComplianceControllerIntegrationTest test`
Expected: FAIL — l'endpoint `/archive-report` n'existe pas → 404 (le test ADMIN échoue sur le status 200 attendu).

- [ ] **Step 3: Ajouter l'endpoint au ComplianceController (GREEN)**

Dans `ComplianceController.java`, ajouter l'import :

```java
import com.oct.invoicesystem.domain.compliance.dto.ArchiveComplianceReportDTO;
import com.oct.invoicesystem.domain.compliance.service.ArchiveComplianceService;
import io.swagger.v3.oas.annotations.Operation;
```

Ajouter le champ injecté à côté de `private final ComplianceService service;` :

```java
    private final ArchiveComplianceService archiveComplianceService;
```

Ajouter la méthode endpoint (avant l'accolade fermante de la classe) :

```java
    // ── Archive compliance report (ADMIN, no financial data) ──
    @GetMapping("/archive-report")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Archive compliance report",
            description = "Aggregated read-only archive compliance: coverage, SHA-256 integrity, retention, lifecycle. ADMIN only.")
    public ResponseEntity<ApiResponse<ArchiveComplianceReportDTO>> archiveReport() {
        return ResponseEntity.ok(ApiResponse.success(archiveComplianceService.generateReport()));
    }
```

- [ ] **Step 4: Lancer l'IT (vérifier GREEN)**

Run: `./mvnw.cmd -q -Dtest=ArchiveComplianceControllerIntegrationTest test`
Expected: PASS — Tests run: 3, Failures: 0, Errors: 0. (Confirme aussi que les `@Query` JPQL de Task 1 sont valides au démarrage du contexte.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/compliance/controller/ComplianceController.java src/test/java/com/oct/invoicesystem/domain/compliance/controller/ArchiveComplianceControllerIntegrationTest.java
git commit -m "feat(m14-11): GET /compliance/archive-report endpoint (ADMIN)"
```

---

### Task 4: i18n (FR + EN)

**Files:**
- Modify: `frontend/src/i18n/fr.json`
- Modify: `frontend/src/i18n/en.json`

**Interfaces:**
- Produces: bloc de clés `archiveCompliance.*` utilisé par la page en Task 5.

- [ ] **Step 1: Ajouter le bloc dans fr.json**

Dans `frontend/src/i18n/fr.json`, ajouter une clé de premier niveau `archiveCompliance` (à placer par ordre alphabétique ou à côté de `retentionPolicy` ; JSON valide, virgules correctes) :

```json
  "archiveCompliance": {
    "navTitle": "Conformité archives",
    "title": "Conformité des archives",
    "subtitle": "État de conformité du dépôt d'archives documentaires. Aucune donnée financière.",
    "generatedAt": "Généré le",
    "coverage": "Couverture d'archivage",
    "coverageRate": "Taux de couverture",
    "archivedInvoices": "Factures archivées",
    "archivedWithDocument": "Avec document",
    "archivedWithoutDocument": "Sans document",
    "integrity": "Intégrité (SHA-256)",
    "integrityRate": "Taux d'intégrité",
    "totalDocuments": "Documents totaux",
    "withChecksum": "Avec empreinte",
    "missingChecksum": "Sans empreinte",
    "retention": "Rétention",
    "lifecycle": "Cycle de vie",
    "pending": "En attente",
    "retained": "Conservés",
    "purged": "Purgés",
    "versionedDocuments": "Documents versionnés"
  },
```

- [ ] **Step 2: Ajouter le bloc équivalent dans en.json**

Dans `frontend/src/i18n/en.json`, ajouter le même bloc avec les mêmes clés (parité stricte) :

```json
  "archiveCompliance": {
    "navTitle": "Archive compliance",
    "title": "Archive compliance",
    "subtitle": "Compliance status of the document archive repository. No financial data.",
    "generatedAt": "Generated at",
    "coverage": "Archival coverage",
    "coverageRate": "Coverage rate",
    "archivedInvoices": "Archived invoices",
    "archivedWithDocument": "With document",
    "archivedWithoutDocument": "Without document",
    "integrity": "Integrity (SHA-256)",
    "integrityRate": "Integrity rate",
    "totalDocuments": "Total documents",
    "withChecksum": "With checksum",
    "missingChecksum": "Without checksum",
    "retention": "Retention",
    "lifecycle": "Lifecycle",
    "pending": "Pending",
    "retained": "Retained",
    "purged": "Purged",
    "versionedDocuments": "Versioned documents"
  },
```

- [ ] **Step 3: Vérifier la validité JSON et la parité des clés**

Run: `cd frontend && node -e "const fr=require('./src/i18n/fr.json'),en=require('./src/i18n/en.json');const a=Object.keys(fr.archiveCompliance).sort(),b=Object.keys(en.archiveCompliance).sort();if(JSON.stringify(a)!==JSON.stringify(b))throw new Error('key mismatch');console.log('OK parity',a.length,'keys')"`
Expected: `OK parity 19 keys`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -m "feat(m14-11): i18n keys for archive compliance page (fr/en)"
```

---

### Task 5: Page React ADMIN + route + sidebar + vitest

**Files:**
- Create: `frontend/src/pages/admin/AdminArchiveCompliancePage.tsx`
- Create: `frontend/src/test/pages/AdminArchiveCompliancePage.test.tsx`
- Modify: `frontend/src/AppRoutes.tsx`
- Modify: `frontend/src/components/layout/Sidebar.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/compliance/archive-report` (Task 3) ; clés `archiveCompliance.*` (Task 4).
- Produces: route `/admin/archive-compliance` montant `AdminArchiveCompliancePage`.

- [ ] **Step 1: Écrire la page**

Create `frontend/src/pages/admin/AdminArchiveCompliancePage.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { Loader2, ShieldCheck } from 'lucide-react'

interface CoverageSection {
  archivedInvoices: number
  archivedWithDocument: number
  archivedWithoutDocument: number
  coverageRate: number
}
interface IntegritySection {
  totalDocuments: number
  withChecksum: number
  missingChecksum: number
  integrityRate: number
}
interface RetentionSection {
  status: string
  retentionYears: number
  active: boolean
  sweepOverdue: boolean
}
interface LifecycleSection {
  pending: number
  retained: number
  purged: number
  versionedDocuments: number
}
interface ArchiveComplianceReport {
  generatedAt: string
  coverage: CoverageSection
  integrity: IntegritySection
  retention: RetentionSection
  lifecycle: LifecycleSection
}

const pct = (rate: number) => `${Math.round(rate * 1000) / 10}%`

function Row({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex items-center justify-between text-sm py-1">
      <span className="text-gray-500">{label}</span>
      <span className="font-medium text-gray-800">{value}</span>
    </div>
  )
}

function Card({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl border p-5 space-y-1">
      <h2 className="text-sm font-semibold text-gray-900 mb-2">{title}</h2>
      {children}
    </div>
  )
}

export default function AdminArchiveCompliancePage() {
  const { t, i18n } = useTranslation()

  const { data: report, isLoading } = useQuery({
    queryKey: ['archive-compliance'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: ArchiveComplianceReport }>('/compliance/archive-report')
      return data.data
    },
  })

  return (
    <PageRoleGuard allowedRoles={['ROLE_ADMIN']}>
      <div className="max-w-4xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('archiveCompliance.title', 'Conformité des archives')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            {t('archiveCompliance.subtitle', "État de conformité du dépôt d'archives documentaires. Aucune donnée financière.")}
          </p>
        </div>

        {isLoading || !report ? (
          <div className="flex items-center justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <>
            <p className="flex items-center gap-1.5 text-xs text-gray-400">
              <ShieldCheck className="w-3.5 h-3.5" />
              {t('archiveCompliance.generatedAt', 'Généré le')} {new Date(report.generatedAt).toLocaleString(i18n.language)}
            </p>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Card title={t('archiveCompliance.coverage', "Couverture d'archivage")}>
                <Row label={t('archiveCompliance.coverageRate', 'Taux de couverture')} value={pct(report.coverage.coverageRate)} />
                <Row label={t('archiveCompliance.archivedInvoices', 'Factures archivées')} value={report.coverage.archivedInvoices} />
                <Row label={t('archiveCompliance.archivedWithDocument', 'Avec document')} value={report.coverage.archivedWithDocument} />
                <Row label={t('archiveCompliance.archivedWithoutDocument', 'Sans document')} value={report.coverage.archivedWithoutDocument} />
              </Card>

              <Card title={t('archiveCompliance.integrity', 'Intégrité (SHA-256)')}>
                <Row label={t('archiveCompliance.integrityRate', "Taux d'intégrité")} value={pct(report.integrity.integrityRate)} />
                <Row label={t('archiveCompliance.totalDocuments', 'Documents totaux')} value={report.integrity.totalDocuments} />
                <Row label={t('archiveCompliance.withChecksum', 'Avec empreinte')} value={report.integrity.withChecksum} />
                <Row label={t('archiveCompliance.missingChecksum', 'Sans empreinte')} value={report.integrity.missingChecksum} />
              </Card>

              <Card title={t('archiveCompliance.retention', 'Rétention')}>
                <Row label={t('archiveCompliance.retention', 'Rétention')} value={report.retention.status} />
                <Row label={t('retentionPolicy.years', 'Durée de rétention (années)')} value={report.retention.retentionYears} />
              </Card>

              <Card title={t('archiveCompliance.lifecycle', 'Cycle de vie')}>
                <Row label={t('archiveCompliance.pending', 'En attente')} value={report.lifecycle.pending} />
                <Row label={t('archiveCompliance.retained', 'Conservés')} value={report.lifecycle.retained} />
                <Row label={t('archiveCompliance.purged', 'Purgés')} value={report.lifecycle.purged} />
                <Row label={t('archiveCompliance.versionedDocuments', 'Documents versionnés')} value={report.lifecycle.versionedDocuments} />
              </Card>
            </div>
          </>
        )}
      </div>
    </PageRoleGuard>
  )
}
```

- [ ] **Step 2: Brancher la route**

Dans `frontend/src/AppRoutes.tsx`, après la ligne `const AdminRetentionPolicyPage = lazy(...)` (≈ ligne 33), ajouter :

```tsx
const AdminArchiveCompliancePage = lazy(() => import('@/pages/admin/AdminArchiveCompliancePage'))
```

Après la `<Route path="/admin/retention-policy" ... />` (≈ ligne 110), ajouter :

```tsx
            <Route path="/admin/archive-compliance" element={<AdminArchiveCompliancePage />} />
```

- [ ] **Step 3: Ajouter le NavItem sidebar**

Dans `frontend/src/components/layout/Sidebar.tsx`, juste après le `NavItem` `/admin/retention-policy` (≈ ligne 191), ajouter (réutiliser une icône déjà importée ; `Clock` est déjà importé, mais préférer `ShieldCheck` si déjà présent — sinon utiliser `Clock`) :

```tsx
          <NavItem to="/admin/archive-compliance" icon={ShieldCheck} label={t('archiveCompliance.navTitle', 'Conformité archives')} />
```

Si `ShieldCheck` n'est pas déjà importé en haut du fichier depuis `lucide-react`, l'ajouter à l'import existant `lucide-react`. (Vérifier la ligne d'import en haut du fichier ; ajouter `ShieldCheck` à la liste.)

- [ ] **Step 4: Écrire le test vitest**

Create `frontend/src/test/pages/AdminArchiveCompliancePage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import AdminArchiveCompliancePage from '@/pages/admin/AdminArchiveCompliancePage'
import apiClient from '@/services/apiClient'
import authReducer from '@/store/slices/authSlice'
import notificationReducer from '@/store/slices/notificationSlice'
import type { AuthUser } from '@/store/slices/authSlice'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

const adminUser: AuthUser = { id: '1', username: 'admin', email: 'admin@oct.fr', roles: ['ROLE_ADMIN'] }

const makeStore = (user: AuthUser | null) =>
  configureStore({
    reducer: { auth: authReducer, notifications: notificationReducer },
    preloadedState: {
      auth: { user, accessToken: 'test-token', refreshToken: null, isAuthenticated: !!user },
    },
  })

function renderPage(user: AuthUser | null = adminUser) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <Provider store={makeStore(user)}>
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <I18nextProvider i18n={i18n}>
            <AdminArchiveCompliancePage />
          </I18nextProvider>
        </MemoryRouter>
      </QueryClientProvider>
    </Provider>
  )
}

const sampleReport = {
  generatedAt: '2026-06-21T10:00:00Z',
  coverage: { archivedInvoices: 8, archivedWithDocument: 6, archivedWithoutDocument: 2, coverageRate: 0.75 },
  integrity: { totalDocuments: 10, withChecksum: 10, missingChecksum: 0, integrityRate: 1.0 },
  retention: { status: 'CONFORME', retentionYears: 10, active: true, sweepOverdue: false },
  lifecycle: { pending: 3, retained: 2, purged: 1, versionedDocuments: 4 },
}

describe('AdminArchiveCompliancePage', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads and renders the report sections', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleReport } })
    renderPage()
    expect(await screen.findByText('75%')).toBeInTheDocument()
    expect(screen.getByText('CONFORME')).toBeInTheDocument()
  })

  it('denies access to non-admin users', () => {
    renderPage({ id: '2', username: 'daf', email: 'daf@oct.fr', roles: ['ROLE_DAF'] })
    expect(screen.queryByText('75%')).toBeNull()
  })
})
```

- [ ] **Step 5: Lancer vitest sur la nouvelle page**

Run: `cd frontend && npx vitest run src/test/pages/AdminArchiveCompliancePage.test.tsx`
Expected: PASS — 2 tests verts.

- [ ] **Step 6: Vérifier tsc + suite vitest complète**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: tsc 0 erreur ; vitest tout vert (52 existants + 2 nouveaux = 54).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/admin/AdminArchiveCompliancePage.tsx frontend/src/test/pages/AdminArchiveCompliancePage.test.tsx frontend/src/AppRoutes.tsx frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(m14-11): admin archive compliance page + route + sidebar"
```

---

### Task 6: Mise à jour matrice + vérification finale complète

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md` (lignes 301, 317, 518)

**Interfaces:** aucune (documentation + gate final).

- [ ] **Step 1: Passer M9 UI #11 à ✅**

Dans `docs/COMPLIANCE_MATRIX.md`, remplacer la ligne 301 :

```
| 11 | Compliance reporting for archives | 🟠 | Module conformité (M14) existe ; pas de rapport de conformité **spécifique aux archives**. |
```

par :

```
| 11 | Compliance reporting for archives | ✅ | Rapport archives dédié (M14 #11) : `GET /api/v1/compliance/archive-report` (ADMIN, sans donnée financière) → couverture d'archivage, intégrité SHA-256, état de rétention (réutilise M10 #10), cycle de vie (dispositions/versioning). Page `/admin/archive-compliance`. |
```

- [ ] **Step 2: Mettre à jour la ligne « Gaps M9 »**

Remplacer la ligne 317 :

```
**Gaps M9 :** #1 pas d'arborescence dossiers ; #8 purge non automatisée (par design) ; #11 pas de rapport conformité archives dédié. (#7 résolu en B2.)
```

par :

```
**Gaps M9 :** #1 pas d'arborescence dossiers ; #8 purge non automatisée (par design). (#7 résolu en B2 ; #11 résolu en M14 #11.)
```

- [ ] **Step 3: Mettre à jour le tableau de synthèse M9 (ligne ≈518)**

Dans la ligne de synthèse `| M9 Archiving | 13 | 4 | 0 | 0 | ... |`, décrémenter le compte de 🟠 (4 → 3) et incrémenter ✅ en conséquence. Lire la ligne exacte avant de l'éditer (les colonnes sont : Module | total | ✅ | 🟠 | ❌ ou similaire — adapter aux en-têtes réels de ce tableau) et ajuster le commentaire pour retirer la mention du rapport archives manquant.

- [ ] **Step 4: Lancer la suite backend COMPLÈTE (critère de fin)**

Run: `./mvnw.cmd test`
Expected: BUILD SUCCESS — 0 failures, 0 errors. (Le total = base 455 + 6 service + 3 IT = 464 tests, valeur indicative ; l'exigence est 0 échec/0 erreur.)

- [ ] **Step 5: Vérifier le frontend (critère de fin)**

Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: tsc 0 erreur ; vitest tout vert.

- [ ] **Step 6: Commit**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -m "docs(m14-11): mark M9 archive compliance reporting complete"
```

---

## Notes de fin de lot (après Task 6)

- Mettre à jour le ledger `.superpowers/sdd/progress.md` (nouvelle section M14 #11).
- Si bug réel rencontré pendant l'implémentation → logger dans `docs/KNOWN_ISSUES_REGISTRY.md` (sinon rien).
- Revue finale whole-branch (opus) recommandée avant `finishing-a-development-branch`.
- Push au seuil de 10 commits non poussés (ce plan ajoute ~6 commits).
