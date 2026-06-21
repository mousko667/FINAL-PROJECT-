# M11 #7 — Tendances temporelles volume/valeur — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un rapport « Tendances volume/valeur » — nombre de factures et montant total par mois sur les 12 derniers mois glissants — exposé en endpoint backend et affiché dans `/reports`, fermant M11 UI #7 + feature #6.

**Architecture:** Backend = un DTO record + une méthode service (`getVolumeTrend(int months)`) qui réutilise `invoiceRepository.findAllWithFilters(...)` (aucune nouvelle requête) et groupe par mois en Java, + un endpoint `GET /reports/volume-trend`. Frontend = un composant `VolumeTrendSection` (recharts ComposedChart) monté dans `ReportsPage`. Aucun changement de schéma (zéro Flyway).

**Tech Stack:** Spring Boot 3 (records, JPQL existant, Mockito + @SpringBootTest/MockMvc), React 18 + TypeScript, @tanstack/react-query, recharts (déjà dépendance), react-i18next, vitest + @testing-library/react.

## Global Constraints

- **Zéro Flyway, zéro nouvelle requête** : réutiliser `invoiceRepository.findAllWithFilters(status, departmentId, fromDate, toDate, reference, supplierId, Pageable)` qui filtre déjà sur `issueDate` (params from/to) et exclut `deletedAt IS NULL`.
- **Date pilote = `issueDate`** (champ `issue_date`, `LocalDate`, NOT NULL sur l'entité `Invoice`). Montant = `getAmount()` (`BigDecimal`, NOT NULL).
- **Accès = DAF + ASSISTANT_COMPTABLE uniquement** : `@PreAuthorize("hasAnyRole('DAF','ASSISTANT_COMPTABLE')")`. ADMIN doit recevoir 403 (séparation des pouvoirs, PROB-065). Pas de ROLE_AUDITEUR dans ce système.
- **Toutes réponses API** dans `ApiResponse<T>` ; `@Operation` Swagger sur l'endpoint.
- **Fenêtre** : 12 mois glissants par défaut, `?months` configurable, borné `[1, 60]` ; hors bornes → `ValidationException("reports.trends.invalid_months")` (→ 400 via GlobalExceptionHandler).
- **Mois vides pré-remplis à zéro** (série continue, exactement N points).
- **monthLabel** = `YYYY-MM` (ex. `2026-01`), produit côté backend, affiché tel quel sur l'axe X.
- **NE PAS filtrer par statut workflow** (compter toutes les factures émises non soft-deleted).
- i18n FR + EN à parité ; FR avec accents corrects. Clés frontend dans `frontend/src/i18n/fr.json` + `en.json` (PAS les `messages_*.properties` backend, sauf la clé d'erreur — voir Task 2).
- `apiClient` baseURL = `/api/v1` → appeler `/reports/volume-trend` SANS préfixe (PROB-038).
- Critère de fin global : `./mvnw.cmd test` 0 échec ; `tsc --noEmit` 0 ; `vitest` vert. Commits atomiques par tâche. Commandes backend depuis la racine repo ; commandes frontend depuis `frontend/`.

## File Structure

- **Create** `src/main/java/com/oct/invoicesystem/domain/report/dto/VolumeTrendDTO.java` — record DTO (+ record imbriqué `MonthlyTrendPoint`).
- **Modify** `src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java` — déclarer `getVolumeTrend(int months)`.
- **Modify** `src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java` — implémenter l'agrégation.
- **Modify** `src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java` — tests service (Mockito).
- **Modify** `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java` — endpoint `GET /volume-trend`.
- **Modify** `src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java` — tests endpoint (rôles).
- **Create** `frontend/src/components/reports/VolumeTrendSection.tsx` — section recharts.
- **Create** `frontend/src/test/components/VolumeTrendSection.test.tsx` — test vitest.
- **Modify** `frontend/src/pages/ReportsPage.tsx` — monter `<VolumeTrendSection />`.
- **Modify** `frontend/src/i18n/fr.json` + `frontend/src/i18n/en.json` — clés `reports.trends.*`.
- **Modify** `docs/COMPLIANCE_MATRIX.md` — passer M11 UI #7 + feature #6 à ✅, synthèse + compteur.

---

### Task 1 : DTO `VolumeTrendDTO`

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/report/dto/VolumeTrendDTO.java`

**Interfaces:**
- Consumes: rien.
- Produces: `VolumeTrendDTO(LocalDate fromDate, LocalDate toDate, List<MonthlyTrendPoint> points)` et le record imbriqué `VolumeTrendDTO.MonthlyTrendPoint(String monthLabel, int year, int month, long invoiceCount, BigDecimal totalAmount)` — utilisés par Task 2 (service) et Task 3 (controller test).

- [ ] **Step 1 : Créer le record DTO**

Créer `src/main/java/com/oct/invoicesystem/domain/report/dto/VolumeTrendDTO.java` :

```java
package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tendance temporelle volume (nb de factures) et valeur (montant total) par mois,
 * agrégée sur la date de facture (issueDate). Voir M11 #7 / feature #6.
 */
public record VolumeTrendDTO(
        LocalDate fromDate,
        LocalDate toDate,
        List<MonthlyTrendPoint> points
) {
    /** Un point mensuel : label YYYY-MM, année/mois, nombre de factures et montant total. */
    public record MonthlyTrendPoint(
            String monthLabel,
            int year,
            int month,
            long invoiceCount,
            BigDecimal totalAmount
    ) {}
}
```

- [ ] **Step 2 : Compiler**

Run (racine repo) : `./mvnw.cmd -q compile`
Expected : BUILD SUCCESS (le record compile seul).

- [ ] **Step 3 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/dto/VolumeTrendDTO.java
git commit -m "feat(m11-7): VolumeTrendDTO record (monthly volume/value points)"
```

---

### Task 2 : Service `getVolumeTrend` (agrégation par mois) + tests

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java`
- Modify (i18n erreur): `src/main/resources/i18n/messages_fr.properties` + `messages_en.properties`

**Interfaces:**
- Consumes: `VolumeTrendDTO` + `VolumeTrendDTO.MonthlyTrendPoint` (Task 1). `invoiceRepository.findAllWithFilters(InvoiceStatus, UUID, LocalDate, LocalDate, String, UUID, Pageable)` renvoyant `Page<Invoice>` ; `Invoice.getIssueDate()` (`LocalDate`), `Invoice.getAmount()` (`BigDecimal`). `ValidationException(String messageKey)` (déjà utilisée ailleurs dans le projet, ex. RetentionDispositionService).
- Produces: `ReportService.getVolumeTrend(int months)` → `VolumeTrendDTO` — consommé par Task 3 (controller).

- [ ] **Step 1 : Écrire les tests service (échec attendu)**

Ajouter dans `ReportServiceTest.java`. Imports nécessaires en tête du fichier s'ils manquent : `import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;`, `import java.time.YearMonth;`. `Invoice`, `InvoiceStatus`, `BigDecimal`, `LocalDate`, `PageImpl`, `List`, `Collections`, `UUID` sont déjà importés.

Helper local + 4 tests (les ajouter à la fin de la classe, avant l'accolade fermante) :

```java
    private Invoice invoiceOn(LocalDate issueDate, String amount) {
        return Invoice.builder()
                .id(UUID.randomUUID())
                .issueDate(issueDate)
                .amount(new BigDecimal(amount))
                .status(InvoiceStatus.SOUMIS)
                .build();
    }

    @Test
    void getVolumeTrend_AggregatesCountAndAmountPerMonth() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(10);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        List<Invoice> invoices = List.of(
                invoiceOn(thisMonth, "100.00"),
                invoiceOn(thisMonth, "50.00"),
                invoiceOn(lastMonth, "200.00")
        );
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(invoices));

        VolumeTrendDTO result = reportService.getVolumeTrend(12);

        assertEquals(12, result.points().size()); // série continue de 12 mois
        // points triés chronologiquement : le dernier est le mois courant
        VolumeTrendDTO.MonthlyTrendPoint current = result.points().get(11);
        assertEquals(YearMonth.from(thisMonth).toString(), current.monthLabel()); // "YYYY-MM"
        assertEquals(2L, current.invoiceCount());
        assertEquals(new BigDecimal("150.00"), current.totalAmount());
        VolumeTrendDTO.MonthlyTrendPoint previous = result.points().get(10);
        assertEquals(1L, previous.invoiceCount());
        assertEquals(new BigDecimal("200.00"), previous.totalAmount());
    }

    @Test
    void getVolumeTrend_FillsEmptyMonthsWithZero() {
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        VolumeTrendDTO result = reportService.getVolumeTrend(6);

        assertEquals(6, result.points().size());
        assertTrue(result.points().stream().allMatch(p -> p.invoiceCount() == 0L));
        assertTrue(result.points().stream().allMatch(p -> p.totalAmount().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void getVolumeTrend_RejectsMonthsBelowOne() {
        assertThrows(
                com.oct.invoicesystem.shared.exception.ValidationException.class,
                () -> reportService.getVolumeTrend(0));
    }

    @Test
    void getVolumeTrend_RejectsMonthsAboveSixty() {
        assertThrows(
                com.oct.invoicesystem.shared.exception.ValidationException.class,
                () -> reportService.getVolumeTrend(61));
    }
```

- [ ] **Step 2 : Lancer les tests → échec attendu**

Run (racine repo) : `./mvnw.cmd -q -Dtest=ReportServiceTest test`
Expected : FAIL — `getVolumeTrend` n'existe pas (erreur de compilation des tests).

- [ ] **Step 3 : Déclarer la méthode dans l'interface**

Dans `src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java`, ajouter l'import et la déclaration (à côté des autres) :

```java
import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;
```
```java
    VolumeTrendDTO getVolumeTrend(int months);
```

- [ ] **Step 4 : Implémenter dans ReportServiceImpl**

Dans `src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java`, ajouter les imports manquants en tête (`java.time.YearMonth`, `java.util.LinkedHashMap`, et `VolumeTrendDTO` ; `BigDecimal`, `LocalDate`, `Pageable`, `Collectors`, `List`, `Map` sont déjà présents) puis la méthode :

```java
    @Override
    @Transactional(readOnly = true)
    public VolumeTrendDTO getVolumeTrend(int months) {
        if (months < 1 || months > 60) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException("reports.trends.invalid_months");
        }
        log.info("Calculating volume/value trend over {} months", months);

        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        LocalDate fromDate = firstMonth.atDay(1);
        LocalDate toDate = LocalDate.now();

        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, fromDate, toDate, null, null, Pageable.unpaged()).getContent();

        // Buckets par mois calendaire (issueDate)
        Map<YearMonth, List<Invoice>> byMonth = invoices.stream()
                .collect(Collectors.groupingBy(inv -> YearMonth.from(inv.getIssueDate())));

        // Série continue : un point par mois de la fenêtre, vide = 0
        List<VolumeTrendDTO.MonthlyTrendPoint> points = new java.util.ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = firstMonth.plusMonths(i);
            List<Invoice> monthInvoices = byMonth.getOrDefault(ym, java.util.Collections.emptyList());
            BigDecimal total = monthInvoices.stream()
                    .map(Invoice::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new VolumeTrendDTO.MonthlyTrendPoint(
                    ym.toString(),           // "YYYY-MM"
                    ym.getYear(),
                    ym.getMonthValue(),
                    monthInvoices.size(),
                    total));
        }

        return new VolumeTrendDTO(fromDate, toDate, points);
    }
```

- [ ] **Step 5 : Ajouter la clé d'erreur i18n backend**

`reports.trends.invalid_months` doit être résolvable. `messages_fr.properties` est **ISO-8859-1** : écris la valeur FR en ASCII pur (pas d'accent, pas de tiret cadratin) pour éviter toute corruption. Ajouter dans `src/main/resources/i18n/messages_fr.properties` :
```
reports.trends.invalid_months=Le nombre de mois doit etre compris entre 1 et 60.
```
Et dans `src/main/resources/i18n/messages_en.properties` :
```
reports.trends.invalid_months=The number of months must be between 1 and 60.
```

- [ ] **Step 6 : Lancer les tests → vert**

Run (racine repo) : `./mvnw.cmd -q -Dtest=ReportServiceTest test`
Expected : PASS (tous les tests de ReportServiceTest, dont les 4 nouveaux).

- [ ] **Step 7 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java src/main/java/com/oct/invoicesystem/domain/report/service/ReportServiceImpl.java src/test/java/com/oct/invoicesystem/domain/report/service/ReportServiceTest.java src/main/resources/i18n/messages_fr.properties src/main/resources/i18n/messages_en.properties
git commit -m "feat(m11-7): getVolumeTrend service — monthly aggregation + validation"
```

---

### Task 3 : Endpoint `GET /reports/volume-trend` + tests rôles

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java`

**Interfaces:**
- Consumes: `reportService.getVolumeTrend(int)` → `VolumeTrendDTO` (Task 2).
- Produces: `GET /api/v1/reports/volume-trend?months=12` → `ApiResponse<VolumeTrendDTO>`.

- [ ] **Step 1 : Écrire les tests endpoint (échec attendu)**

Ajouter dans `ReportControllerTest.java` (avant l'accolade finale). Imports : `java.util.List`, `LocalDate`, `Collections` sont déjà là ; ajouter si manquant `import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;`.

```java
    // ─── Volume/value trend (M11 #7) ───────────────────────────────────────

    private VolumeTrendDTO sampleTrend() {
        return new VolumeTrendDTO(
                LocalDate.now().minusMonths(11).withDayOfMonth(1),
                LocalDate.now(),
                List.of(new VolumeTrendDTO.MonthlyTrendPoint("2026-01", 2026, 1, 3L, new java.math.BigDecimal("1500.00"))));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getVolumeTrend_WithDaf_ReturnsSuccess() throws Exception {
        when(reportService.getVolumeTrend(anyInt())).thenReturn(sampleTrend());

        mockMvc.perform(get("/api/v1/reports/volume-trend").param("months", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.points[0].monthLabel").value("2026-01"))
                .andExpect(jsonPath("$.data.points[0].invoiceCount").value(3));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getVolumeTrend_WithAssistantComptable_ReturnsSuccess() throws Exception {
        when(reportService.getVolumeTrend(anyInt())).thenReturn(sampleTrend());

        mockMvc.perform(get("/api/v1/reports/volume-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getVolumeTrend_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/volume-trend"))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2 : Lancer les tests → échec attendu**

Run (racine repo) : `./mvnw.cmd -q -Dtest=ReportControllerTest test`
Expected : FAIL — l'endpoint n'existe pas (404 au lieu de 200/403, ou échec de compilation si VolumeTrendDTO non importé).

- [ ] **Step 3 : Ajouter l'endpoint**

Dans `ReportController.java`, après la méthode `getCashFlowProjection` (ou à côté des autres GET reporting), ajouter :

```java
    @GetMapping("/volume-trend")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Volume/Value Trend",
            description = "Monthly invoice count and total amount over the last N rolling months (M11 #7), aggregated on issue date")
    public ApiResponse<com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO> getVolumeTrend(
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.success(reportService.getVolumeTrend(months));
    }
```

- [ ] **Step 4 : Lancer les tests → vert**

Run (racine repo) : `./mvnw.cmd -q -Dtest=ReportControllerTest test`
Expected : PASS (toute la classe, dont les 3 nouveaux).

- [ ] **Step 5 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java src/test/java/com/oct/invoicesystem/domain/report/controller/ReportControllerTest.java
git commit -m "feat(m11-7): GET /reports/volume-trend endpoint (DAF + ASSISTANT_COMPTABLE)"
```

---

### Task 4 : i18n frontend `reports.trends.*` (FR + EN)

**Files:**
- Modify: `frontend/src/i18n/fr.json`
- Modify: `frontend/src/i18n/en.json`

**Interfaces:**
- Consumes: rien.
- Produces: clés `reports.trends.title`, `reports.trends.desc`, `reports.trends.amount`, `reports.trends.count` (consommées par Task 5). `reports.noData` existe déjà (réutilisée).

- [ ] **Step 1 : Repérer le bloc `reports` et son emplacement**

Read `frontend/src/i18n/fr.json` et `en.json` autour de la clé `"reports"`. Repérer une sous-clé existante (ex. `"cashFlowTitle"`, `"noData"`) pour insérer le sous-objet `trends` DANS l'objet `reports`, en respectant les virgules JSON.

- [ ] **Step 2 : Ajouter le sous-bloc FR**

Dans l'objet `"reports"` de `frontend/src/i18n/fr.json`, ajouter (clé adjacente aux autres clés `reports`, virgule correcte) :

```json
    "trends": {
      "title": "Tendances volume / valeur",
      "desc": "Nombre de factures et montant total par mois sur les 12 derniers mois (par date de facture).",
      "amount": "Montant (XAF)",
      "count": "Nb de factures"
    }
```

- [ ] **Step 3 : Ajouter le sous-bloc EN (parité)**

Dans l'objet `"reports"` de `frontend/src/i18n/en.json` :

```json
    "trends": {
      "title": "Volume / value trends",
      "desc": "Invoice count and total amount per month over the last 12 months (by invoice date).",
      "amount": "Amount (XAF)",
      "count": "Invoice count"
    }
```

- [ ] **Step 4 : Vérifier parité + JSON valide**

Run (depuis `frontend/`) :
```bash
node -e "const f=require('./src/i18n/fr.json').reports.trends,e=require('./src/i18n/en.json').reports.trends;const a=Object.keys(f).sort(),b=Object.keys(e).sort();if(JSON.stringify(a)!==JSON.stringify(b)){console.error('PARITE KO',a,b);process.exit(1)}console.log('OK',a.length,'cles')"
```
Expected : `OK 4 cles`.

- [ ] **Step 5 : Commit**

```bash
git add frontend/src/i18n/fr.json frontend/src/i18n/en.json
git commit -m "feat(m11-7): i18n keys reports.trends (FR+EN parity)"
```

---

### Task 5 : Composant `VolumeTrendSection` (recharts) + test + montage dans ReportsPage

**Files:**
- Create: `frontend/src/components/reports/VolumeTrendSection.tsx`
- Test: `frontend/src/test/components/VolumeTrendSection.test.tsx`
- Modify: `frontend/src/pages/ReportsPage.tsx`

**Interfaces:**
- Consumes: endpoint `GET /reports/volume-trend` → `{ data: { data: VolumeTrendDTO } }` où un point = `{ monthLabel: string, year: number, month: number, invoiceCount: number, totalAmount: number }`. Clés i18n `reports.trends.*` (Task 4) + `reports.noData` (existante).
- Produces: composant `default export VolumeTrendSection` monté dans `ReportsPage`.

- [ ] **Step 1 : Écrire le test (échec attendu)**

Créer `frontend/src/test/components/VolumeTrendSection.test.tsx`. Pattern : QueryClientProvider + I18nextProvider + mock apiClient (recharts se rend en jsdom ; on assert sur le titre et le label d'axe textuels, pas sur le SVG).

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from '@/i18n'
import VolumeTrendSection from '@/components/reports/VolumeTrendSection'
import apiClient from '@/services/apiClient'

vi.mock('@/services/apiClient', () => ({
  default: { get: vi.fn() },
}))

function renderSection() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <I18nextProvider i18n={i18n}>
        <VolumeTrendSection />
      </I18nextProvider>
    </QueryClientProvider>
  )
}

const sampleTrend = {
  fromDate: '2025-07-01',
  toDate: '2026-06-21',
  points: [
    { monthLabel: '2026-05', year: 2026, month: 5, invoiceCount: 4, totalAmount: 4000 },
    { monthLabel: '2026-06', year: 2026, month: 6, invoiceCount: 2, totalAmount: 1500 },
  ],
}

describe('VolumeTrendSection', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders the trend section title once loaded', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: sampleTrend } })
    renderSection()
    expect(await screen.findByText('Tendances volume / valeur')).toBeInTheDocument()
  })

  it('shows the empty state when all months are zero', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({
      data: { data: { fromDate: '2025-07-01', toDate: '2026-06-21', points: [
        { monthLabel: '2026-06', year: 2026, month: 6, invoiceCount: 0, totalAmount: 0 },
      ] } },
    })
    renderSection()
    // titre toujours rendu ; le graphe est remplacé par l'état vide
    expect(await screen.findByText('Tendances volume / valeur')).toBeInTheDocument()
    expect(screen.getByTestId('volume-trend-empty')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2 : Lancer le test → échec attendu**

Run (depuis `frontend/`) : `npx vitest run src/test/components/VolumeTrendSection.test.tsx`
Expected : FAIL — module `@/components/reports/VolumeTrendSection` introuvable.

- [ ] **Step 3 : Créer le composant**

Créer `frontend/src/components/reports/VolumeTrendSection.tsx`. La carte/section reproduit le style des sections de `ReportsPage` (bordure, titre). Graphe = `ComposedChart` (barres montant axe gauche + ligne volume axe droit). État vide = quand chaque point a `invoiceCount === 0` ET `totalAmount === 0`.

```tsx
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import apiClient from '@/services/apiClient'
import { Loader2 } from 'lucide-react'
import {
  ComposedChart, Bar, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'

interface TrendPoint {
  monthLabel: string
  year: number
  month: number
  invoiceCount: number
  totalAmount: number
}
interface VolumeTrend {
  fromDate: string
  toDate: string
  points: TrendPoint[]
}

export default function VolumeTrendSection() {
  const { t } = useTranslation()

  const { data: trend, isLoading } = useQuery({
    queryKey: ['volume-trend'],
    queryFn: async () => {
      const { data } = await apiClient.get<{ data: VolumeTrend }>('/reports/volume-trend')
      return data.data
    },
    retry: false,
  })

  const isEmpty = !trend?.points?.length
    || trend.points.every(p => p.invoiceCount === 0 && p.totalAmount === 0)

  return (
    <div className="bg-white rounded-xl border overflow-hidden">
      <div className="px-5 py-4 font-semibold text-gray-900">{t('reports.trends.title', 'Tendances volume / valeur')}</div>
      <div className="border-t px-5 py-4">
        <p className="text-sm text-gray-500 mb-4">{t('reports.trends.desc', 'Nombre de factures et montant total par mois sur les 12 derniers mois (par date de facture).')}</p>
        {isLoading ? (
          <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-muted-foreground" /></div>
        ) : isEmpty ? (
          <p data-testid="volume-trend-empty" className="text-sm text-center text-gray-400 py-4">{t('reports.noData', 'Aucune donnée')}</p>
        ) : (
          <ResponsiveContainer width="100%" height={260}>
            <ComposedChart data={trend!.points}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="monthLabel" tick={{ fontSize: 11 }} />
              <YAxis yAxisId="left" tick={{ fontSize: 11 }} tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
              <YAxis yAxisId="right" orientation="right" tick={{ fontSize: 11 }} allowDecimals={false} />
              <Tooltip formatter={(value, name) => name === t('reports.trends.amount', 'Montant (XAF)')
                ? [`${Number(value).toLocaleString()} XAF`, name]
                : [value, name]} />
              <Legend />
              <Bar yAxisId="left" dataKey="totalAmount" name={t('reports.trends.amount', 'Montant (XAF)')} fill="#6366f1" radius={[4, 4, 0, 0]} />
              <Line yAxisId="right" dataKey="invoiceCount" name={t('reports.trends.count', 'Nb de factures')} stroke="#10b981" strokeWidth={2} dot={{ r: 3 }} />
            </ComposedChart>
          </ResponsiveContainer>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 4 : Lancer le test → vert**

Run (depuis `frontend/`) : `npx vitest run src/test/components/VolumeTrendSection.test.tsx`
Expected : PASS (2 tests).

- [ ] **Step 5 : Monter le composant dans ReportsPage**

Dans `frontend/src/pages/ReportsPage.tsx` :
- Ajouter l'import en tête : `import VolumeTrendSection from '@/components/reports/VolumeTrendSection'`
- Monter `<VolumeTrendSection />` juste après la `</Section>` du bloc Cash flow (repère : le commentaire `{/* Cash flow */}` ... `</Section>`, insérer après). Comme la section gère sa propre carte/titre, l'insérer directement dans le flux des sections (pas besoin de l'envelopper dans `<Section>`).

- [ ] **Step 6 : Vérifier tsc + suite vitest**

Run (depuis `frontend/`) :
```bash
npx tsc --noEmit
npx vitest run
```
Expected : `tsc` 0 erreur ; `vitest` tout vert (suite existante + 2 nouveaux).

- [ ] **Step 7 : Commit**

```bash
git add frontend/src/components/reports/VolumeTrendSection.tsx frontend/src/test/components/VolumeTrendSection.test.tsx frontend/src/pages/ReportsPage.tsx
git commit -m "feat(m11-7): VolumeTrendSection (recharts ComposedChart) in /reports"
```

---

### Task 6 : Vérification finale + mise à jour COMPLIANCE_MATRIX

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md`

**Interfaces:**
- Consumes: feature complète (Tasks 1-5).
- Produces: matrice à jour (preuve).

- [ ] **Step 1 : Gate complet backend + frontend**

Run (racine repo) : `./mvnw.cmd test`
Expected : BUILD SUCCESS, 0 failures / 0 errors. Si un test échoue → corriger la cause AVANT de continuer (règle no-failures).

Run (depuis `frontend/`) : `npx tsc --noEmit && npx vitest run`
Expected : tsc 0 ; vitest tout vert.

- [ ] **Step 2 : Passer M11 UI #7 et feature #6 à ✅**

Dans `docs/COMPLIANCE_MATRIX.md`, remplacer la ligne (~368) :
```markdown
| 7 | Volume and value trends | ✅ | Tendance temporelle volume/valeur par mois (12 mois glissants, `?months`) via `GET /api/v1/reports/volume-trend` (DAF + ASSISTANT_COMPTABLE) ; section ComposedChart (barres montant + ligne nb factures) dans `/reports`. Agrégée sur la date de facture. |
```
Et la ligne feature (~384) :
```markdown
| 6 | Volume & value trend analysis | ✅ | Tendance temporelle mensuelle (volume + valeur) — voir UI #7. |
```

- [ ] **Step 3 : Mettre à jour la synthèse M11 et le compteur global**

Dans la table de synthèse (~520), remplacer la ligne M11 :
```markdown
| M11 Reporting | 20 | 3 | 0 | 0 | Bon (cash-flow corrigé PROB-054 ; tendances temporelles M11 #7) |
```
(18→20 ✅, 5→3 🟠.)

Dans la phrase de comptage global (~525), passer `~**229 ✅**` → `~**231 ✅**` et `~**47 🟠**` → `~**45 🟠**` (deux items de plus conformes, deux partiels de moins). Laisser intacte la phrase « ~49 partiels » de la ligne ~531 (approximative, hors comptage).

- [ ] **Step 4 : Commit**

```bash
git add docs/COMPLIANCE_MATRIX.md
git commit -m "docs(m11-7): mark volume/value trends compliant (M11 UI #7 + feature #6)"
```

---

## Notes de revue finale (après Task 6)

- Lancer la revue finale whole-branch (opus) puis `superpowers:finishing-a-development-branch`.
- Si un bug réel est rencontré → le logger dans `docs/KNOWN_ISSUES_REGISTRY.md` (PROB-NNN) avant le fix (règle Living Documentation §12).
- Push : géré hors plan (seuil 10 commits / fin de lot).
- Vérifier au runtime si possible (lancer backend + front, ouvrir `/reports`, dérouler la section, vérifier le 200 réseau — PROB-038).
