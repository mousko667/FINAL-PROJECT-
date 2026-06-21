# M5 #1 + #4 — Page de rapprochement dédiée — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter une page `/matching` dédiée (liste filtrable des rapprochements 3-voies) + une vue détail avec comparaison ligne-à-ligne PO/GRN/facture, en lecture recalculée, sans migration.

**Architecture:** Backend lecture-seule (`MatchingComparator` extrait de `ThreeWayMatchingService` + `MatchingQueryService` + `MatchingQueryController`), recompose la comparaison à la volée depuis les entités déjà liées et le dernier `ThreeWayMatchingResult`. Front React (liste + détail) consommant 2 nouveaux endpoints. Append-only respecté, aucune table créée.

**Tech Stack:** Spring Boot 3.4, JPA/Hibernate, PostgreSQL, JUnit5+MockMvc+Mockito ; React 18 + TS + Vite, TanStack Query, react-i18next, vitest.

## Global Constraints

- TDD strict : test d'abord, rouge, puis implémentation. Réf : `superpowers:test-driven-development`.
- `./mvnw.cmd test` et `npm run build` + `vitest` doivent rester **VERTS (0 échec)** avant chaque commit (règle no-failures).
- **Aucune migration Flyway** (V64 reste libre). Lecture seule, append-only respecté.
- Autorisation sur chaque endpoint : `@PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")` (SoD : ADMIN & SUPPLIER exclus).
- Réponses backend wrappées en `ApiResponse<T>` ; pagination via `PagedResponse.of(Page)`.
- Bug Postgres connu (PROB-038/054) : tout paramètre nullable dans une requête → `CAST(:param AS ...)`.
- `apiClient` front : base **SANS** `/api/v1` (PROB-038) → chemins `/matching`, `/matching/{id}/lines`.
- i18n : clés `matching.*` ajoutées dans `frontend/src/i18n/fr.json` ET `en.json` (parité, aucune collision). Messages d'erreur backend via `MessageSource` (`messages_fr.properties` = **ISO-8859-1**, clé ASCII).
- Javadoc sur méthodes service publiques ; `@Operation` Swagger sur endpoints.
- Noms réels vérifiés : `Invoice.referenceNumber`, `Invoice.supplierName`, `Invoice.supplier` (nullable, `getCompanyName()`), `Invoice.purchaseOrderId` (UUID), `Invoice.getItems()` → `InvoiceItem(description, quantity, unitPrice)`. `PurchaseOrder.poNumber`, `getItems()` → `PurchaseOrderItem(itemDescription, quantity, unitPrice)`. `GoodsReceiptItem(purchaseOrderItem, receivedQuantity)`. `GoodsReceiptNoteRepository.findByPurchaseOrderId(UUID)`. `MatchingConfigRepository.findByIsActiveTrue()`. `MatchingConfig(tolerancePercentage, toleranceAmount)`.

---

## File Structure

- Create `src/main/java/com/oct/invoicesystem/domain/purchasing/service/MatchingComparator.java` — tolérance + verdict par ligne (extrait de `ThreeWayMatchingService`).
- Modify `src/main/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingService.java` — délègue à `MatchingComparator` (refactor sans changement de comportement).
- Create `src/main/java/com/oct/invoicesystem/domain/purchasing/dto/MatchingSummaryDTO.java`
- Create `src/main/java/com/oct/invoicesystem/domain/purchasing/dto/MatchingDetailDTO.java` (+ `LineComparison`, `LineVerdict`).
- Modify `src/main/java/com/oct/invoicesystem/domain/purchasing/repository/ThreeWayMatchingResultRepository.java` — requête liste (dernier par facture).
- Create `src/main/java/com/oct/invoicesystem/domain/purchasing/service/MatchingQueryService.java`
- Create `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingQueryController.java`
- Tests : `MatchingComparatorTest`, `MatchingQueryServiceTest`, `MatchingQueryControllerIntegrationTest`.
- Front : `frontend/src/services/matchingService.ts`, `frontend/src/pages/matching/MatchingListPage.tsx`, `frontend/src/pages/matching/MatchingDetailPage.tsx`, modif `AppRoutes.tsx`, `Sidebar.tsx`, `i18n/fr.json`, `i18n/en.json`. Tests `MatchingListPage.test.tsx`, `MatchingDetailPage.test.tsx`.

---

## Task 1: `MatchingComparator` — verdict par ligne (extraction logique tolérance)

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/purchasing/service/MatchingComparator.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/purchasing/service/MatchingComparatorTest.java`

**Interfaces:**
- Produces:
  - `enum LineVerdict { MATCHED, WITHIN_TOLERANCE, MISMATCH, MISSING_IN_PO }` (fichier `dto/MatchingDetailDTO.java`, Task 3 ; pour Task 1 le placer temporairement dans `MatchingComparator` n'est PAS souhaité → créer l'enum dès Task 1 dans son propre fichier `dto/LineVerdict.java`).
  - `boolean MatchingComparator.isWithinTolerance(BigDecimal invQty, BigDecimal poQty, BigDecimal invPrice, BigDecimal poPrice, MatchingConfig config)` — logique identique à l'actuelle `ThreeWayMatchingService.isWithinTolerance`.
  - `LineVerdict MatchingComparator.verdictForLine(BigDecimal invQty, BigDecimal invPrice, BigDecimal poQty, BigDecimal poPrice, MatchingConfig config)` — MATCHED si qté&prix exacts ; sinon WITHIN_TOLERANCE si `isWithinTolerance`, sinon MISMATCH. (MISSING_IN_PO est décidé par l'appelant quand pas de ligne PO.)

- [ ] **Step 1: Créer l'enum `LineVerdict`**

Create `src/main/java/com/oct/invoicesystem/domain/purchasing/dto/LineVerdict.java` :
```java
package com.oct.invoicesystem.domain.purchasing.dto;

/** Verdict de rapprochement d'une ligne facture vs PO. */
public enum LineVerdict { MATCHED, WITHIN_TOLERANCE, MISMATCH, MISSING_IN_PO }
```

- [ ] **Step 2: Écrire le test qui échoue**

Create `MatchingComparatorTest.java` :
```java
package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingComparatorTest {

    private final MatchingComparator comparator = new MatchingComparator();

    private MatchingConfig config(String pct, String amount) {
        return MatchingConfig.builder()
                .tolerancePercentage(new BigDecimal(pct))
                .toleranceAmount(new BigDecimal(amount))
                .build();
    }

    @Test
    void exactMatch_isMatched() {
        LineVerdict v = comparator.verdictForLine(
                new BigDecimal("10"), new BigDecimal("5.00"),
                new BigDecimal("10"), new BigDecimal("5.00"), config("2.00", "0"));
        assertThat(v).isEqualTo(LineVerdict.MATCHED);
    }

    @Test
    void smallDiffWithinTolerance_isWithinTolerance() {
        // qté 10 vs 10.1 → 1% < 2% → within
        LineVerdict v = comparator.verdictForLine(
                new BigDecimal("10.1"), new BigDecimal("5.00"),
                new BigDecimal("10"), new BigDecimal("5.00"), config("2.00", "0"));
        assertThat(v).isEqualTo(LineVerdict.WITHIN_TOLERANCE);
    }

    @Test
    void largeDiffOutsideTolerance_isMismatch() {
        LineVerdict v = comparator.verdictForLine(
                new BigDecimal("20"), new BigDecimal("5.00"),
                new BigDecimal("10"), new BigDecimal("5.00"), config("2.00", "0"));
        assertThat(v).isEqualTo(LineVerdict.MISMATCH);
    }
}
```

- [ ] **Step 3: Lancer le test → échec compilation (classe absente)**

Run: `./mvnw.cmd -q test -Dtest=MatchingComparatorTest`
Expected: FAIL (cannot find symbol MatchingComparator).

- [ ] **Step 4: Implémenter `MatchingComparator`**

Create `MatchingComparator.java` (copie fidèle de la logique `isWithinTolerance` actuelle, lignes 183-207 de `ThreeWayMatchingService`) :
```java
package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Logique de tolérance de rapprochement, réutilisée par le matching d'écriture et la lecture ligne-à-ligne. */
@Component
public class MatchingComparator {

    /** True si l'écart qté/prix d'une ligne reste dans la tolérance (% OU montant) de la config. */
    public boolean isWithinTolerance(BigDecimal invoiceQty, BigDecimal poQty,
                                     BigDecimal invoicePrice, BigDecimal poPrice, MatchingConfig config) {
        BigDecimal percTolerance = config.getTolerancePercentage();
        BigDecimal amtTolerance = config.getToleranceAmount();

        BigDecimal qtyVariance = invoiceQty.subtract(poQty).abs();
        BigDecimal priceVariance = invoicePrice.subtract(poPrice).abs();

        BigDecimal qtyThreshold = poQty.multiply(percTolerance).divide(new BigDecimal("100"));
        BigDecimal qtyVarianceAmount = qtyVariance.multiply(poPrice);
        if (qtyVariance.compareTo(qtyThreshold) > 0 && qtyVarianceAmount.compareTo(amtTolerance) > 0) {
            return false;
        }

        BigDecimal priceThreshold = poPrice.multiply(percTolerance).divide(new BigDecimal("100"));
        BigDecimal priceVarianceAmount = priceVariance.multiply(poQty);
        if (priceVariance.compareTo(priceThreshold) > 0 && priceVarianceAmount.compareTo(amtTolerance) > 0) {
            return false;
        }
        return true;
    }

    /** Verdict d'une ligne dont l'équivalent PO existe. */
    public LineVerdict verdictForLine(BigDecimal invoiceQty, BigDecimal invoicePrice,
                                      BigDecimal poQty, BigDecimal poPrice, MatchingConfig config) {
        if (invoiceQty.compareTo(poQty) == 0 && invoicePrice.compareTo(poPrice) == 0) {
            return LineVerdict.MATCHED;
        }
        return isWithinTolerance(invoiceQty, poQty, invoicePrice, poPrice, config)
                ? LineVerdict.WITHIN_TOLERANCE : LineVerdict.MISMATCH;
    }
}
```

- [ ] **Step 5: Lancer le test → vert**

Run: `./mvnw.cmd -q test -Dtest=MatchingComparatorTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/purchasing/dto/LineVerdict.java \
        src/main/java/com/oct/invoicesystem/domain/purchasing/service/MatchingComparator.java \
        src/test/java/com/oct/invoicesystem/domain/purchasing/service/MatchingComparatorTest.java
git commit -m "feat(M5): MatchingComparator — verdict de tolérance par ligne (#4)"
```

---

## Task 2: Refactor `ThreeWayMatchingService` pour déléguer à `MatchingComparator`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingService.java:36-207`
- Test (non-régression) : suite matching existante (`ThreeWayMatchingIntegrationTest` + `ThreeWayMatchingServiceTest` s'il existe).

**Interfaces:**
- Consumes: `MatchingComparator.isWithinTolerance(...)` (Task 1).
- Produces: comportement de `match(...)` inchangé.

- [ ] **Step 1: Injecter le comparator**

Ajouter le champ après `matchingConfigRepository` (ligne ~37) :
```java
    private final MatchingComparator matchingComparator;
```

- [ ] **Step 2: Remplacer l'appel interne**

Dans `performMatching` (ligne ~137), remplacer l'appel à la méthode privée par :
```java
                if (matchingComparator.isWithinTolerance(invoiceQty, poQty, invoicePrice, poPrice, config)) {
```
Et l'appel GRN (ligne ~153) :
```java
                    if (!matchingComparator.isWithinTolerance(receivedQty, invoiceQty, BigDecimal.ONE, BigDecimal.ONE, config)) {
```
Puis **supprimer** la méthode privée `isWithinTolerance` (lignes ~183-207) désormais inutilisée.

- [ ] **Step 3: Lancer la suite matching → vert (non-régression)**

Run: `./mvnw.cmd -q test -Dtest=ThreeWayMatching*`
Expected: PASS (aucune régression — comportement identique).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingService.java
git commit -m "refactor(M5): ThreeWayMatchingService délègue la tolérance à MatchingComparator"
```

---

## Task 3: DTOs `MatchingSummaryDTO` + `MatchingDetailDTO`/`LineComparison`

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/purchasing/dto/MatchingSummaryDTO.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/purchasing/dto/MatchingDetailDTO.java`

**Interfaces:**
- Consumes: `MatchingStatus`, `LineVerdict` (Task 1).
- Produces: les deux records ci-dessous (utilisés par Task 5/6/7/front).

- [ ] **Step 1: Créer `MatchingSummaryDTO`**

```java
package com.oct.invoicesystem.domain.purchasing.dto;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;

import java.time.Instant;
import java.util.UUID;

/** Ligne de la liste de rapprochement (dernier résultat par facture). Aucun total financier exposé. */
public record MatchingSummaryDTO(
        UUID invoiceId,
        String invoiceNumber,
        String supplierName,
        UUID purchaseOrderId,
        String purchaseOrderNumber,
        boolean grnPresent,
        MatchingStatus status,
        int lineCount,
        int discrepancyLineCount,
        Instant matchedAt) {
}
```

- [ ] **Step 2: Créer `MatchingDetailDTO` + `LineComparison`**

```java
package com.oct.invoicesystem.domain.purchasing.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Détail d'un rapprochement avec comparaison ligne-à-ligne PO/GRN/facture. Lecture seule. */
public record MatchingDetailDTO(
        MatchingSummaryDTO summary,
        String discrepancyNotes,
        UUID overriddenBy,
        String overrideReason,
        List<LineComparison> lines) {

    /** Comparaison d'une ligne entre PO, GRN (qté reçue) et facture. */
    public record LineComparison(
            String description,
            BigDecimal poQuantity,
            BigDecimal poUnitPrice,
            BigDecimal receivedQuantity,   // null si pas de GRN
            BigDecimal invoiceQuantity,
            BigDecimal invoiceUnitPrice,
            BigDecimal qtyVariancePct,
            BigDecimal priceVariancePct,
            LineVerdict verdict) {
    }
}
```

- [ ] **Step 3: Compiler**

Run: `./mvnw.cmd -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/purchasing/dto/MatchingSummaryDTO.java \
        src/main/java/com/oct/invoicesystem/domain/purchasing/dto/MatchingDetailDTO.java
git commit -m "feat(M5): DTOs liste + comparaison ligne-à-ligne du rapprochement"
```

---

## Task 4: Requête de liste (dernier résultat par facture) sur le repository

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/repository/ThreeWayMatchingResultRepository.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/purchasing/repository/ThreeWayMatchingResultRepositoryTest.java`

**Interfaces:**
- Produces: `Page<ThreeWayMatchingResult> findLatestPerInvoice(MatchingStatus status, String search, Pageable pageable)` — ne retient que le dernier `created_at` par `invoice_id` ; `status` et `search` nullables (filtre ignoré si null).

- [ ] **Step 1: Écrire le test repository (@DataJpaTest)**

```java
package com.oct.invoicesystem.domain.purchasing.repository;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ThreeWayMatchingResultRepositoryTest {

    @Autowired ThreeWayMatchingResultRepository repository;

    @Test
    void findLatestPerInvoice_noFilter_returnsPage() {
        Page<?> page = repository.findLatestPerInvoice(null, null, PageRequest.of(0, 20));
        assertThat(page).isNotNull();
        assertThat(page.getContent()).isNotNull();
    }

    @Test
    void findLatestPerInvoice_filterByStatus_returnsPage() {
        Page<?> page = repository.findLatestPerInvoice(MatchingStatus.MATCHED, null, PageRequest.of(0, 20));
        assertThat(page).isNotNull();
    }
}
```
*(Note implémenteur : ces tests valident surtout que la requête JPQL compile et s'exécute sans `SQLGrammarException`. Le seeding de données n'est pas requis ici — la couverture comportementale fine est dans `MatchingQueryServiceTest`, Task 5.)*

- [ ] **Step 2: Lancer → échec (méthode absente)**

Run: `./mvnw.cmd -q test -Dtest=ThreeWayMatchingResultRepositoryTest`
Expected: FAIL (cannot find symbol findLatestPerInvoice).

- [ ] **Step 3: Ajouter la requête**

Dans `ThreeWayMatchingResultRepository`, ajouter (imports `Page`, `Pageable`, `Param`, `Query`, `MatchingStatus`) :
```java
    @Query("""
            SELECT r FROM ThreeWayMatchingResult r
            WHERE r.createdAt = (
                SELECT MAX(r2.createdAt) FROM ThreeWayMatchingResult r2
                WHERE r2.invoice.id = r.invoice.id)
              AND (CAST(:status AS string) IS NULL OR r.status = :status)
              AND (:search IS NULL
                   OR LOWER(r.invoice.referenceNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.invoice.supplierName)    LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(r.purchaseOrder.poNumber)  LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<ThreeWayMatchingResult> findLatestPerInvoice(@Param("status") MatchingStatus status,
                                                      @Param("search") String search,
                                                      Pageable pageable);
```
*(Le `CAST(:status AS string)` neutralise le bug Postgres de type inféré sur enum nullable — PROB-038/054.)*

- [ ] **Step 4: Lancer → vert**

Run: `./mvnw.cmd -q test -Dtest=ThreeWayMatchingResultRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/purchasing/repository/ThreeWayMatchingResultRepository.java \
        src/test/java/com/oct/invoicesystem/domain/purchasing/repository/ThreeWayMatchingResultRepositoryTest.java
git commit -m "feat(M5): requête liste rapprochement (dernier résultat par facture, filtres typés)"
```

---

## Task 5: `MatchingQueryService` — liste + recomposition ligne-à-ligne

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/purchasing/service/MatchingQueryService.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/purchasing/service/MatchingQueryServiceTest.java`

**Interfaces:**
- Consumes: `ThreeWayMatchingResultRepository.findLatestPerInvoice(...)` (Task 4), `MatchingComparator.verdictForLine(...)` (Task 1), `GoodsReceiptNoteRepository.findByPurchaseOrderId(UUID)`, `MatchingConfigRepository.findByIsActiveTrue()`, `InvoiceRepository.findById(UUID)`, `PurchaseOrderRepository.findById(UUID)`.
- Produces:
  - `Page<MatchingSummaryDTO> list(MatchingStatus status, String search, Pageable pageable)`
  - `MatchingDetailDTO getLines(UUID invoiceId)` — lève `ResourceNotFoundException` si facture absente ou sans PO lié.

- [ ] **Step 1: Écrire le test (Mockito)**

```java
package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO;
import com.oct.invoicesystem.domain.purchasing.model.*;
import com.oct.invoicesystem.domain.purchasing.repository.*;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingQueryServiceTest {

    @Mock ThreeWayMatchingResultRepository matchingRepo;
    @Mock InvoiceRepository invoiceRepo;
    @Mock PurchaseOrderRepository poRepo;
    @Mock GoodsReceiptNoteRepository grnRepo;
    @Mock MatchingConfigRepository configRepo;
    @InjectMocks MatchingQueryService service;

    @Test
    void getLines_invoiceMissing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(invoiceRepo.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getLines(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getLines_exactLine_verdictMatched() {
        UUID invId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();

        Invoice inv = new Invoice();
        inv.setReferenceNumber("INV-1");
        inv.setSupplierName("ACME");
        inv.setPurchaseOrderId(poId);
        InvoiceItem ii = new InvoiceItem();
        ii.setDescription("Widget"); ii.setQuantity(new BigDecimal("10")); ii.setUnitPrice(new BigDecimal("5.00"));
        inv.setItems(List.of(ii));

        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-1").build();
        PurchaseOrderItem pi = new PurchaseOrderItem();
        pi.setItemDescription("Widget"); pi.setQuantity(new BigDecimal("10")); pi.setUnitPrice(new BigDecimal("5.00"));
        po.setItems(List.of(pi));

        MatchingConfig config = MatchingConfig.builder()
                .tolerancePercentage(new BigDecimal("2.00")).toleranceAmount(BigDecimal.ZERO).build();

        when(invoiceRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(poRepo.findById(poId)).thenReturn(Optional.of(po));
        when(grnRepo.findByPurchaseOrderId(poId)).thenReturn(List.of());
        when(configRepo.findByIsActiveTrue()).thenReturn(Optional.of(config));
        when(matchingRepo.findByInvoiceId(invId)).thenReturn(Optional.empty());

        MatchingDetailDTO dto = service.getLines(invId);
        assertThat(dto.lines()).hasSize(1);
        assertThat(dto.lines().get(0).verdict()).isEqualTo(LineVerdict.MATCHED);
    }

    @Test
    void getLines_invoiceLineWithoutPo_verdictMissingInPo() {
        UUID invId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();

        Invoice inv = new Invoice();
        inv.setReferenceNumber("INV-2"); inv.setSupplierName("ACME"); inv.setPurchaseOrderId(poId);
        InvoiceItem ii = new InvoiceItem();
        ii.setDescription("Ghost"); ii.setQuantity(new BigDecimal("1")); ii.setUnitPrice(new BigDecimal("9.00"));
        inv.setItems(List.of(ii));

        PurchaseOrder po = PurchaseOrder.builder().poNumber("PO-2").build();
        po.setItems(List.of());
        MatchingConfig config = MatchingConfig.builder()
                .tolerancePercentage(new BigDecimal("2.00")).toleranceAmount(BigDecimal.ZERO).build();

        when(invoiceRepo.findById(invId)).thenReturn(Optional.of(inv));
        when(poRepo.findById(poId)).thenReturn(Optional.of(po));
        when(grnRepo.findByPurchaseOrderId(poId)).thenReturn(List.of());
        when(configRepo.findByIsActiveTrue()).thenReturn(Optional.of(config));
        when(matchingRepo.findByInvoiceId(invId)).thenReturn(Optional.empty());

        MatchingDetailDTO dto = service.getLines(invId);
        assertThat(dto.lines().get(0).verdict()).isEqualTo(LineVerdict.MISSING_IN_PO);
    }
}
```

- [ ] **Step 2: Lancer → échec (classe absente)**

Run: `./mvnw.cmd -q test -Dtest=MatchingQueryServiceTest`
Expected: FAIL (cannot find symbol MatchingQueryService).

- [ ] **Step 3: Implémenter le service**

```java
package com.oct.invoicesystem.domain.purchasing.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.purchasing.dto.LineVerdict;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO.LineComparison;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingSummaryDTO;
import com.oct.invoicesystem.domain.purchasing.model.*;
import com.oct.invoicesystem.domain.purchasing.repository.*;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Lecture seule : liste des rapprochements + recomposition ligne-à-ligne (M5 #1/#4). Aucune écriture. */
@Service
@RequiredArgsConstructor
public class MatchingQueryService {

    private final ThreeWayMatchingResultRepository matchingRepository;
    private final InvoiceRepository invoiceRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    private final MatchingConfigRepository matchingConfigRepository;
    private final MatchingComparator matchingComparator;

    /** Liste paginée du dernier résultat de rapprochement par facture. */
    @Transactional(readOnly = true)
    public Page<MatchingSummaryDTO> list(MatchingStatus status, String search, Pageable pageable) {
        return matchingRepository.findLatestPerInvoice(status, blankToNull(search), pageable)
                .map(this::toSummary);
    }

    /** Détail ligne-à-ligne d'un rapprochement, recalculé à la volée. */
    @Transactional(readOnly = true)
    public MatchingDetailDTO getLines(UUID invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("matching.invoice.notfound"));
        if (invoice.getPurchaseOrderId() == null) {
            throw new ResourceNotFoundException("matching.po.notfound");
        }
        PurchaseOrder po = purchaseOrderRepository.findById(invoice.getPurchaseOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("matching.po.notfound"));
        List<GoodsReceiptNote> grns = goodsReceiptNoteRepository.findByPurchaseOrderId(po.getId());
        MatchingConfig config = matchingConfigRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("matching.config.notfound"));

        Map<String, BigDecimal> receivedByDesc = new HashMap<>();
        for (GoodsReceiptNote grn : grns) {
            for (GoodsReceiptItem gri : grn.getItems()) {
                String desc = gri.getPurchaseOrderItem().getItemDescription();
                receivedByDesc.merge(desc, gri.getReceivedQuantity(), BigDecimal::add);
            }
        }

        List<LineComparison> lines = new ArrayList<>();
        for (InvoiceItem inv : invoice.getItems()) {
            PurchaseOrderItem poItem = po.getItems().stream()
                    .filter(p -> p.getItemDescription().equalsIgnoreCase(inv.getDescription()))
                    .findFirst().orElse(null);
            if (poItem == null) {
                lines.add(new LineComparison(inv.getDescription(), null, null,
                        receivedByDesc.get(inv.getDescription()),
                        inv.getQuantity(), inv.getUnitPrice(), null, null, LineVerdict.MISSING_IN_PO));
                continue;
            }
            LineVerdict verdict = matchingComparator.verdictForLine(
                    inv.getQuantity(), inv.getUnitPrice(), poItem.getQuantity(), poItem.getUnitPrice(), config);
            lines.add(new LineComparison(
                    inv.getDescription(), poItem.getQuantity(), poItem.getUnitPrice(),
                    receivedByDesc.get(poItem.getItemDescription()),
                    inv.getQuantity(), inv.getUnitPrice(),
                    variancePct(inv.getQuantity(), poItem.getQuantity()),
                    variancePct(inv.getUnitPrice(), poItem.getUnitPrice()),
                    verdict));
        }

        Optional<ThreeWayMatchingResult> last = matchingRepository.findByInvoiceId(invoiceId);
        MatchingSummaryDTO summary = toSummaryFrom(invoice, po, grns, lines, last.orElse(null));
        return new MatchingDetailDTO(
                summary,
                last.map(ThreeWayMatchingResult::getDiscrepancyNotes).orElse(null),
                last.map(r -> r.getOverriddenBy() != null ? r.getOverriddenBy().getId() : null).orElse(null),
                last.map(ThreeWayMatchingResult::getOverrideReason).orElse(null),
                lines);
    }

    private MatchingSummaryDTO toSummary(ThreeWayMatchingResult r) {
        Invoice inv = r.getInvoice();
        PurchaseOrder po = r.getPurchaseOrder();
        int lineCount = inv.getItems() == null ? 0 : inv.getItems().size();
        return new MatchingSummaryDTO(
                inv.getId(), inv.getReferenceNumber(), inv.getSupplierName(),
                po != null ? po.getId() : null, po != null ? po.getPoNumber() : null,
                r.getGoodsReceiptNote() != null,
                r.getStatus(), lineCount, 0, r.getCreatedAt());
    }

    private MatchingSummaryDTO toSummaryFrom(Invoice inv, PurchaseOrder po, List<GoodsReceiptNote> grns,
                                             List<LineComparison> lines, ThreeWayMatchingResult last) {
        int discrepancies = (int) lines.stream()
                .filter(l -> l.verdict() == LineVerdict.MISMATCH || l.verdict() == LineVerdict.MISSING_IN_PO)
                .count();
        return new MatchingSummaryDTO(
                inv.getId(), inv.getReferenceNumber(), inv.getSupplierName(),
                po.getId(), po.getPoNumber(), !grns.isEmpty(),
                last != null ? last.getStatus() : null,
                inv.getItems().size(), discrepancies,
                last != null ? last.getCreatedAt() : null);
    }

    private BigDecimal variancePct(BigDecimal actual, BigDecimal base) {
        if (base == null || base.compareTo(BigDecimal.ZERO) == 0) return null;
        return actual.subtract(base).abs()
                .multiply(new BigDecimal("100"))
                .divide(base, 2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
```
*(Note : le `discrepancyLineCount` de la liste reste 0 — il n'est calculé que dans le détail, pour éviter de recharger toutes les lignes de chaque facture sur la liste. Documenté tel quel dans le DTO.)*

- [ ] **Step 4: Lancer → vert**

Run: `./mvnw.cmd -q test -Dtest=MatchingQueryServiceTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/purchasing/service/MatchingQueryService.java \
        src/test/java/com/oct/invoicesystem/domain/purchasing/service/MatchingQueryServiceTest.java
git commit -m "feat(M5): MatchingQueryService — liste + comparaison ligne-à-ligne en lecture"
```

---

## Task 6: `MatchingQueryController` + clés i18n erreur backend

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingQueryController.java`
- Modify: `src/main/resources/messages_fr.properties` (ISO-8859-1), `src/main/resources/messages_en.properties`
- Test: `src/test/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingQueryControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `MatchingQueryService.list(...)`, `MatchingQueryService.getLines(...)` (Task 5).
- Produces: `GET /api/v1/matching`, `GET /api/v1/matching/{invoiceId}/lines`.

- [ ] **Step 1: Écrire le test d'intégration**

```java
package com.oct.invoicesystem.domain.purchasing.controller;

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
class MatchingQueryControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void list_asStaff_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SUPPLIER")
    void list_asSupplier_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isForbidden());
    }

    @Test
    void list_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/matching")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void lines_unknownInvoice_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/matching/00000000-0000-0000-0000-000000000000/lines"))
                .andExpect(status().isNotFound());
    }
}
```
*(Si la convention du projet impose `@WithMockUser(authorities = "ROLE_...")`, suivre la convention présente dans `ArchiveComplianceControllerIntegrationTest`.)*

- [ ] **Step 2: Lancer → échec (endpoint absent → 401/404 inattendus)**

Run: `./mvnw.cmd -q test -Dtest=MatchingQueryControllerIntegrationTest`
Expected: FAIL.

- [ ] **Step 3: Implémenter le controller**

```java
package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.MatchingDetailDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingSummaryDTO;
import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.service.MatchingQueryService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Page de rapprochement dédiée (M5 #1/#4) — lecture seule. Staff hors SUPPLIER/ADMIN (SoD). */
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
@Tag(name = "Matching", description = "Liste et comparaison ligne-à-ligne des rapprochements 3-voies")
public class MatchingQueryController {

    private final MatchingQueryService service;

    @GetMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Liste des rapprochements", description = "Dernier résultat de rapprochement par facture, filtrable par statut/recherche.")
    public ResponseEntity<ApiResponse<PagedResponse<MatchingSummaryDTO>>> list(
            @RequestParam(required = false) MatchingStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<MatchingSummaryDTO> result = service.list(status, search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(result)));
    }

    @GetMapping("/{invoiceId}/lines")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Comparaison ligne-à-ligne", description = "Recompose la comparaison PO/GRN/facture par ligne pour une facture.")
    public ResponseEntity<ApiResponse<MatchingDetailDTO>> lines(@PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success(service.getLines(invoiceId)));
    }
}
```

- [ ] **Step 4: Ajouter les clés i18n erreur backend**

Dans `messages_en.properties` :
```
matching.invoice.notfound=Invoice not found
matching.po.notfound=No purchase order linked to this invoice
matching.config.notfound=No active matching configuration found
```
Dans `messages_fr.properties` (**ISO-8859-1, sans accent dans la clé**, valeurs FR — utiliser `iconv` si ajout d'accents, cf. règle projet) :
```
matching.invoice.notfound=Facture introuvable
matching.po.notfound=Aucun bon de commande lie a cette facture
matching.config.notfound=Aucune configuration de rapprochement active
```
*(Note implémenteur : si tu veux les accents FR, écris le fichier en ISO-8859-1 via iconv ; sinon garde l'ASCII ci-dessus pour ne pas corrompre l'encodage.)*

- [ ] **Step 5: Lancer → vert**

Run: `./mvnw.cmd -q test -Dtest=MatchingQueryControllerIntegrationTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Suite backend complète + commit**

Run: `./mvnw.cmd -q test`
Expected: PASS (0 échec).
```bash
git add src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingQueryController.java \
        src/test/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingQueryControllerIntegrationTest.java \
        src/main/resources/messages_fr.properties src/main/resources/messages_en.properties
git commit -m "feat(M5): endpoints liste + lignes de rapprochement (ADMIN/SUPPLIER exclus)"
```

---

## Task 7: Front — service + page liste

**Files:**
- Create: `frontend/src/services/matchingService.ts`
- Create: `frontend/src/pages/matching/MatchingListPage.tsx`
- Modify: `frontend/src/i18n/fr.json`, `frontend/src/i18n/en.json`
- Modify: `frontend/src/AppRoutes.tsx`, `frontend/src/components/layout/Sidebar.tsx`
- Test: `frontend/src/test/pages/MatchingListPage.test.tsx`

**Interfaces:**
- Consumes: `GET /matching` (Task 6).
- Produces: route `/matching`, fonctions `listMatching`, `getMatchingLines`.

- [ ] **Step 1: Service**

```ts
import apiClient from '@/services/apiClient'

export interface MatchingSummary {
  invoiceId: string
  invoiceNumber: string
  supplierName: string
  purchaseOrderId: string | null
  purchaseOrderNumber: string | null
  grnPresent: boolean
  status: 'MATCHED' | 'PARTIAL' | 'MISMATCH' | 'OVERRIDDEN'
  lineCount: number
  discrepancyLineCount: number
  matchedAt: string | null
}

export interface LineComparison {
  description: string
  poQuantity: number | null
  poUnitPrice: number | null
  receivedQuantity: number | null
  invoiceQuantity: number
  invoiceUnitPrice: number
  qtyVariancePct: number | null
  priceVariancePct: number | null
  verdict: 'MATCHED' | 'WITHIN_TOLERANCE' | 'MISMATCH' | 'MISSING_IN_PO'
}

export interface MatchingDetail {
  summary: MatchingSummary
  discrepancyNotes: string | null
  overriddenBy: string | null
  overrideReason: string | null
  lines: LineComparison[]
}

export async function listMatching(params: { status?: string; search?: string; page?: number; size?: number }) {
  const { data } = await apiClient.get('/matching', { params })
  return data.data as { content: MatchingSummary[]; totalPages: number; page: number }
}

export async function getMatchingLines(invoiceId: string) {
  const { data } = await apiClient.get(`/matching/${invoiceId}/lines`)
  return data.data as MatchingDetail
}
```

- [ ] **Step 2: Ajouter les clés i18n (FR + EN, parité)**

Dans `fr.json`, ajouter l'objet `"matching"` :
```json
"matching": {
  "title": "Rapprochement",
  "subtitle": "Rapprochement à trois voies (facture, bon de commande, réception).",
  "search": "Rechercher (facture, fournisseur, BC)",
  "status": "Statut",
  "all": "Tous",
  "invoiceNumber": "N° facture",
  "supplier": "Fournisseur",
  "poNumber": "N° BC",
  "grn": "Réception",
  "discrepancies": "Lignes en écart",
  "date": "Date",
  "empty": "Aucun rapprochement",
  "error": "Erreur de chargement",
  "back": "Retour à la liste",
  "description": "Désignation",
  "poQty": "Qté BC", "poPrice": "PU BC", "received": "Qté reçue",
  "invQty": "Qté facture", "invPrice": "PU facture",
  "qtyVariance": "Écart qté", "priceVariance": "Écart prix",
  "verdict": "Verdict", "export": "Exporter",
  "verdicts": { "MATCHED": "Conforme", "WITHIN_TOLERANCE": "Dans tolérance", "MISMATCH": "Écart", "MISSING_IN_PO": "Absent du BC" },
  "statuses": { "MATCHED": "Conforme", "PARTIAL": "Partiel", "MISMATCH": "Écart", "OVERRIDDEN": "Forcé" }
}
```
Dans `en.json`, ajouter l'objet `"matching"` avec **les mêmes clés** :
```json
"matching": {
  "title": "Matching",
  "subtitle": "Three-way matching (invoice, purchase order, goods receipt).",
  "search": "Search (invoice, supplier, PO)",
  "status": "Status",
  "all": "All",
  "invoiceNumber": "Invoice no.",
  "supplier": "Supplier",
  "poNumber": "PO no.",
  "grn": "Receipt",
  "discrepancies": "Discrepant lines",
  "date": "Date",
  "empty": "No matching records",
  "error": "Failed to load",
  "back": "Back to list",
  "description": "Description",
  "poQty": "PO qty", "poPrice": "PO price", "received": "Received qty",
  "invQty": "Invoice qty", "invPrice": "Invoice price",
  "qtyVariance": "Qty variance", "priceVariance": "Price variance",
  "verdict": "Verdict", "export": "Export",
  "verdicts": { "MATCHED": "Matched", "WITHIN_TOLERANCE": "Within tolerance", "MISMATCH": "Mismatch", "MISSING_IN_PO": "Missing in PO" },
  "statuses": { "MATCHED": "Matched", "PARTIAL": "Partial", "MISMATCH": "Mismatch", "OVERRIDDEN": "Overridden" }
}
```

- [ ] **Step 3: Écrire le test page liste (vitest)**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import MatchingListPage from '@/pages/matching/MatchingListPage'

vi.mock('@/services/matchingService', () => ({
  listMatching: vi.fn().mockResolvedValue({
    content: [{
      invoiceId: 'i1', invoiceNumber: 'INV-1', supplierName: 'ACME',
      purchaseOrderId: 'p1', purchaseOrderNumber: 'PO-1', grnPresent: true,
      status: 'MATCHED', lineCount: 2, discrepancyLineCount: 0, matchedAt: '2026-06-21T10:00:00Z',
    }],
    totalPages: 1, page: 0,
  }),
}))
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter><MatchingListPage /></MemoryRouter>
    </QueryClientProvider>
  )
}

describe('MatchingListPage', () => {
  it('affiche une ligne de rapprochement', async () => {
    renderPage()
    expect(await screen.findByText('INV-1')).toBeInTheDocument()
    expect(screen.getByText('ACME')).toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Lancer → échec (page absente)**

Run (dans `frontend/`): `npx vitest run src/test/pages/MatchingListPage.test.tsx`
Expected: FAIL.

- [ ] **Step 5: Implémenter `MatchingListPage`**

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import { listMatching, type MatchingSummary } from '@/services/matchingService'

const STAFF_ROLES = ['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE',
  'ROLE_VALIDATEUR_N1_INFORMATIQUE', 'ROLE_VALIDATEUR_N2_INFORMATIQUE',
  'ROLE_VALIDATEUR_N1_INFRASTRUCTURE', 'ROLE_VALIDATEUR_N2_INFRASTRUCTURE',
  'ROLE_VALIDATEUR_N1_ATELIER', 'ROLE_VALIDATEUR_N2_ATELIER',
  'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG', 'ROLE_VALIDATEUR_N1_FINANCE',
  'ROLE_VALIDATEUR_N1_TERMINAL', 'ROLE_VALIDATEUR_N1_COMMUNICATION', 'ROLE_VALIDATEUR_N1_QHSSE']

export default function MatchingListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [status, setStatus] = useState('')
  const [search, setSearch] = useState('')

  const { data, isLoading, isError } = useQuery({
    queryKey: ['matching-list', status, search],
    queryFn: () => listMatching({ status: status || undefined, search: search || undefined }),
  })

  return (
    <PageRoleGuard allowedRoles={STAFF_ROLES}>
      <div className="max-w-5xl mx-auto space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('matching.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">{t('matching.subtitle')}</p>
        </div>

        <div className="flex gap-3">
          <input className="border rounded-lg px-3 py-1.5 text-sm flex-1"
            placeholder={t('matching.search')} value={search}
            onChange={(e) => setSearch(e.target.value)} />
          <select className="border rounded-lg px-3 py-1.5 text-sm" value={status}
            onChange={(e) => setStatus(e.target.value)}>
            <option value="">{t('matching.all')}</option>
            {['MATCHED', 'PARTIAL', 'MISMATCH', 'OVERRIDDEN'].map((s) => (
              <option key={s} value={s}>{t(`matching.statuses.${s}`)}</option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : isError ? (
          <p className="text-sm text-red-500">{t('matching.error')}</p>
        ) : !data || data.content.length === 0 ? (
          <p className="text-sm text-gray-400">{t('matching.empty')}</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="py-2">{t('matching.invoiceNumber')}</th>
                <th>{t('matching.supplier')}</th>
                <th>{t('matching.poNumber')}</th>
                <th>{t('matching.status')}</th>
                <th>{t('matching.discrepancies')}</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((m: MatchingSummary) => (
                <tr key={m.invoiceId} className="border-b hover:bg-gray-50 cursor-pointer"
                  onClick={() => navigate(`/matching/${m.invoiceId}`)}>
                  <td className="py-2 font-medium">{m.invoiceNumber}</td>
                  <td>{m.supplierName}</td>
                  <td>{m.purchaseOrderNumber ?? '—'}</td>
                  <td>{m.status ? t(`matching.statuses.${m.status}`) : '—'}</td>
                  <td>{m.discrepancyLineCount}/{m.lineCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </PageRoleGuard>
  )
}
```
*(Note implémenteur : vérifier le nom exact des rôles dans le projet — si une constante de rôles staff existe déjà (cherchée via grep `allowedRoles` sur les pages matching/invoice), la réutiliser au lieu de redéclarer `STAFF_ROLES`.)*

- [ ] **Step 6: Route + sidebar**

Dans `AppRoutes.tsx`, ajouter sous le guard staff (suivre le pattern des routes existantes) :
```tsx
<Route path="/matching" element={<MatchingListPage />} />
<Route path="/matching/:invoiceId" element={<MatchingDetailPage />} />
```
(importer les deux pages ; `MatchingDetailPage` créée en Task 8 — l'import peut être ajouté maintenant si le fichier existe, sinon ajouter la 2e route en Task 8.)
Dans `Sidebar.tsx`, ajouter une entrée « matching.title » avec `RoleGuard fallback={null}` pour `STAFF_ROLES` (suivre le pattern des entrées existantes).

- [ ] **Step 7: Lancer → vert**

Run (dans `frontend/`): `npx vitest run src/test/pages/MatchingListPage.test.tsx`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/services/matchingService.ts frontend/src/pages/matching/MatchingListPage.tsx \
        frontend/src/test/pages/MatchingListPage.test.tsx frontend/src/i18n/fr.json frontend/src/i18n/en.json \
        frontend/src/AppRoutes.tsx frontend/src/components/layout/Sidebar.tsx
git commit -m "feat(M5): page liste de rapprochement + service + i18n + nav"
```

---

## Task 8: Front — page détail (comparaison ligne-à-ligne + export)

**Files:**
- Create: `frontend/src/pages/matching/MatchingDetailPage.tsx`
- Modify: `frontend/src/AppRoutes.tsx` (route détail si pas déjà ajoutée en Task 7)
- Test: `frontend/src/test/pages/MatchingDetailPage.test.tsx`

**Interfaces:**
- Consumes: `getMatchingLines(invoiceId)` (Task 7), endpoint export existant `/invoices/{id}/matching/export`.

- [ ] **Step 1: Écrire le test (vitest)**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import MatchingDetailPage from '@/pages/matching/MatchingDetailPage'

vi.mock('@/services/matchingService', () => ({
  getMatchingLines: vi.fn().mockResolvedValue({
    summary: { invoiceId: 'i1', invoiceNumber: 'INV-1', supplierName: 'ACME',
      purchaseOrderId: 'p1', purchaseOrderNumber: 'PO-1', grnPresent: true,
      status: 'MISMATCH', lineCount: 1, discrepancyLineCount: 1, matchedAt: null },
    discrepancyNotes: null, overriddenBy: null, overrideReason: null,
    lines: [{ description: 'Widget', poQuantity: 10, poUnitPrice: 5, receivedQuantity: 10,
      invoiceQuantity: 20, invoiceUnitPrice: 5, qtyVariancePct: 100, priceVariancePct: 0, verdict: 'MISMATCH' }],
  }),
}))
vi.mock('@/components/auth/RoleGuard', () => ({
  PageRoleGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/matching/i1']}>
        <Routes><Route path="/matching/:invoiceId" element={<MatchingDetailPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('MatchingDetailPage', () => {
  it('affiche la comparaison ligne-à-ligne', async () => {
    renderPage()
    expect(await screen.findByText('Widget')).toBeInTheDocument()
    expect(screen.getByText('100%')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Lancer → échec (page absente)**

Run (dans `frontend/`): `npx vitest run src/test/pages/MatchingDetailPage.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implémenter `MatchingDetailPage`**

```tsx
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useParams, Link } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import { PageRoleGuard } from '@/components/auth/RoleGuard'
import apiClient from '@/services/apiClient'
import { getMatchingLines, type LineComparison } from '@/services/matchingService'

const STAFF_ROLES = ['ROLE_DAF', 'ROLE_ASSISTANT_COMPTABLE',
  'ROLE_VALIDATEUR_N1_INFORMATIQUE', 'ROLE_VALIDATEUR_N2_INFORMATIQUE',
  'ROLE_VALIDATEUR_N1_INFRASTRUCTURE', 'ROLE_VALIDATEUR_N2_INFRASTRUCTURE',
  'ROLE_VALIDATEUR_N1_ATELIER', 'ROLE_VALIDATEUR_N2_ATELIER',
  'ROLE_VALIDATEUR_N1_DRH', 'ROLE_VALIDATEUR_N1_DG', 'ROLE_VALIDATEUR_N1_FINANCE',
  'ROLE_VALIDATEUR_N1_TERMINAL', 'ROLE_VALIDATEUR_N1_COMMUNICATION', 'ROLE_VALIDATEUR_N1_QHSSE']

const pct = (v: number | null) => (v == null ? '—' : `${v}%`)
const num = (v: number | null) => (v == null ? '—' : String(v))

const rowClass = (verdict: string) =>
  verdict === 'MISMATCH' || verdict === 'MISSING_IN_PO' ? 'bg-red-50' : ''

export default function MatchingDetailPage() {
  const { t } = useTranslation()
  const { invoiceId } = useParams<{ invoiceId: string }>()

  const { data, isLoading } = useQuery({
    queryKey: ['matching-lines', invoiceId],
    queryFn: () => getMatchingLines(invoiceId!),
    enabled: !!invoiceId,
  })

  const exportReport = async (format: string) => {
    const res = await apiClient.get(`/invoices/${invoiceId}/matching/export`, {
      params: { format }, responseType: 'blob',
    })
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url; a.download = `matching_${invoiceId}.${format === 'excel' ? 'xlsx' : format}`
    a.click(); URL.revokeObjectURL(url)
  }

  return (
    <PageRoleGuard allowedRoles={STAFF_ROLES}>
      <div className="max-w-5xl mx-auto space-y-6">
        <Link to="/matching" className="text-sm text-blue-600">{t('matching.back')}</Link>

        {isLoading || !data ? (
          <div className="flex justify-center py-20"><Loader2 className="w-6 h-6 animate-spin text-muted-foreground" /></div>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <h1 className="text-2xl font-bold text-gray-900">
                {data.summary.invoiceNumber} · {data.summary.supplierName}
              </h1>
              <div className="flex gap-2">
                {['csv', 'excel', 'pdf'].map((f) => (
                  <button key={f} onClick={() => exportReport(f)}
                    className="text-sm border rounded-lg px-3 py-1.5">{t('matching.export')} {f.toUpperCase()}</button>
                ))}
              </div>
            </div>

            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="py-2">{t('matching.description')}</th>
                  <th>{t('matching.poQty')}</th><th>{t('matching.poPrice')}</th>
                  <th>{t('matching.received')}</th>
                  <th>{t('matching.invQty')}</th><th>{t('matching.invPrice')}</th>
                  <th>{t('matching.qtyVariance')}</th><th>{t('matching.priceVariance')}</th>
                  <th>{t('matching.verdict')}</th>
                </tr>
              </thead>
              <tbody>
                {data.lines.map((l: LineComparison, i: number) => (
                  <tr key={i} className={`border-b ${rowClass(l.verdict)}`}>
                    <td className="py-2 font-medium">{l.description}</td>
                    <td>{num(l.poQuantity)}</td><td>{num(l.poUnitPrice)}</td>
                    <td>{num(l.receivedQuantity)}</td>
                    <td>{num(l.invoiceQuantity)}</td><td>{num(l.invoiceUnitPrice)}</td>
                    <td>{pct(l.qtyVariancePct)}</td><td>{pct(l.priceVariancePct)}</td>
                    <td>{t(`matching.verdicts.${l.verdict}`)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
    </PageRoleGuard>
  )
}
```

- [ ] **Step 4: S'assurer que la route détail existe dans `AppRoutes.tsx`** (ajoutée en Task 7 ; sinon l'ajouter ici).

- [ ] **Step 5: Lancer → vert**

Run (dans `frontend/`): `npx vitest run src/test/pages/MatchingDetailPage.test.tsx`
Expected: PASS.

- [ ] **Step 6: Gate front complet + commit**

Run (dans `frontend/`): `npm run build && npx vitest run`
Expected: tsc OK + tous les tests verts.
```bash
git add frontend/src/pages/matching/MatchingDetailPage.tsx \
        frontend/src/test/pages/MatchingDetailPage.test.tsx frontend/src/AppRoutes.tsx
git commit -m "feat(M5): page détail rapprochement ligne-à-ligne + export"
```

---

## Task 9: Vérification runtime + clôture matrice de conformité

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md` (M5 #1 et #4 → ✅)
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md` (si un bug a été rencontré)

- [ ] **Step 1: Gate complet backend + front**

Run: `./mvnw.cmd test` → 0 échec.
Run (dans `frontend/`): `npm run build && npx vitest run` → verts.

- [ ] **Step 2: Vérif runtime (pas seulement DOM — PROB-038)**

Démarrer backend + frontend (cf. REMEDIATION_PROMPT §1), se connecter en `daf`/`aa`,
ouvrir `/matching` → vérifier **200** sur `GET /matching` (onglet réseau), cliquer une ligne →
vérifier **200** sur `GET /matching/{id}/lines` et l'affichage du tableau ligne-à-ligne.
Tester l'export CSV depuis le détail. Vérifier qu'un compte `admin` ou `supplier` n'a PAS
l'entrée de menu et reçoit **403** sur l'API.

- [ ] **Step 3: Mettre à jour `COMPLIANCE_MATRIX.md`**

Passer les lignes M5 #1 (UI three-way matching interface) et M5 #4 (line item comparison) de 🟠 à ✅ avec preuve (« page dédiée `/matching` + comparaison ligne-à-ligne, endpoints `/matching` & `/matching/{id}/lines`, ADMIN/SUPPLIER exclus — 2026-06-21 »). Ajuster les compteurs de la section synthèse.

- [ ] **Step 4: Commit final**

```bash
git add docs/COMPLIANCE_MATRIX.md docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "docs(M5): clôture #1 + #4 (page rapprochement dédiée + ligne-à-ligne) dans la matrice"
```

---

## Self-Review (rempli par l'auteur du plan)

- **Couverture spec** : §1 principe → Tasks 1/5 (lecture recalculée) ; §2 découpage → Tasks 1-6 ; §3 DTOs → Task 3 ; §4 endpoints/autorisations/erreurs → Tasks 4/6 ; §5 front → Tasks 7/8 ; §6 tests & gate → présents dans chaque task + Task 9. ✅ aucun trou.
- **Placeholders** : aucun TODO/TBD ; chaque step de code montre le code complet. ✅
- **Cohérence des types** : `LineVerdict`/`MatchingStatus`/`MatchingSummaryDTO`/`MatchingDetailDTO`/`LineComparison` nommés identiquement entre back (Tasks 1/3/5/6) et front (Task 7 service). `findLatestPerInvoice`, `verdictForLine`, `isWithinTolerance`, `list`, `getLines` cohérents entre tasks. ✅
- **Réserves implémenteur** : noms de rôles staff front (réutiliser une constante existante si présente) ; convention `@WithMockUser` (aligner sur `ArchiveComplianceControllerIntegrationTest`) ; encodage ISO-8859-1 des messages FR. Signalés en notes dans les tasks concernées.
