# M11 #7 — Tendances temporelles volume/valeur — Design

**Date:** 2026-06-21
**Branche:** fix/a1-cashflow-sqlgrammar
**Items ciblés:** COMPLIANCE_MATRIX.md M11 UI #7 (ligne 368) + M11 feature #6 (ligne 384) — verdict 🟠 → ✅
**Lot:** E

## 1. Vue d'ensemble & périmètre

Nouveau rapport analytique « Tendances volume/valeur » : pour chacun des **12 derniers
mois glissants** (configurable via `?months`), le **nombre de factures** émises et leur
**montant total** (XAF), agrégés sur la **date de facture** (`issueDate`). Affiché comme
nouvelle `<Section>` dans `/reports`, en `ComposedChart` recharts (barres montant +
ligne volume, double axe Y).

Ferme **deux 🟠** : M11 UI #7 « Volume and value trends » et M11 feature #6
« Volume & value trend analysis ».

**Périmètre / coût :**
- Backend léger : 1 endpoint + 1 méthode service + 1 DTO. **Réutilise**
  `invoiceRepository.findAllWithFilters(...)` (filtre déjà par `issueDate` et exclut
  `deletedAt IS NULL`). **Aucune nouvelle requête.**
- Frontend : 1 nouvelle section dans `ReportsPage.tsx` (lib `recharts` déjà présente).
- **Zéro Flyway.**
- Accès **DAF + ASSISTANT_COMPTABLE** (PROB-065 ; ADMIN n'accède pas aux rapports
  financiers — séparation des pouvoirs).

## 2. Composants

### Backend
- **`VolumeTrendDTO`** (record) :
  - `LocalDate fromDate`
  - `LocalDate toDate`
  - `List<MonthlyTrendPoint> points`
  - record imbriqué `MonthlyTrendPoint(String monthLabel, int year, int month, long invoiceCount, BigDecimal totalAmount)`
- **`ReportService.getVolumeTrend(int months)`** + impl `ReportServiceImpl` :
  - Valide `months` ∈ `[1, 60]`, sinon `ValidationException("reports.trends.invalid_months")`.
  - Fenêtre : `toDate = LocalDate.now()` ; `fromDate = YearMonth.from(now).minusMonths(months - 1).atDay(1)`.
  - `invoiceRepository.findAllWithFilters(null, null, fromDate, toDate, null, null, Pageable.unpaged()).getContent()`.
  - Groupe par `YearMonth.from(inv.getIssueDate())` : count + somme de `getAmount()`.
  - **Pré-remplit les N mois** de la fenêtre à zéro (série continue, aucun trou), fusionne avec les buckets calculés, trie chronologiquement.
  - `monthLabel` = `YYYY-MM` (ex. `2026-01`), stable et locale-agnostique.
- **`ReportController`** : `GET /api/v1/reports/volume-trend?months=12`
  (défaut 12), `@PreAuthorize("hasAnyRole('DAF','ASSISTANT_COMPTABLE')")`,
  `ApiResponse<VolumeTrendDTO>`, `@Operation`.

### Frontend
- Nouvelle `<Section>` « Tendances volume/valeur » dans `frontend/src/pages/ReportsPage.tsx` :
  - `useQuery(['volume-trend'])` → `GET /reports/volume-trend`.
  - `ComposedChart` recharts : `Bar dataKey="totalAmount"` (axe Y gauche, XAF) +
    `Line dataKey="invoiceCount"` (axe Y droit), `XAxis dataKey="monthLabel"`,
    deux `YAxis` (yAxisId gauche/droit), `Tooltip`, `CartesianGrid`, `ResponsiveContainer`.
  - États `isLoading` (loader) et vide (`reports.noData`) calqués sur les sections sœurs.
- Clés i18n `reports.trends.*` (FR + EN, parité).

## 3. Flux de données

`useQuery(['volume-trend'])` → `GET /reports/volume-trend` → `VolumeTrendDTO`
→ `ComposedChart data={trend.points}`.

Backend : `findAllWithFilters` (exclut déjà soft-deleted) → `stream().collect(groupingBy(YearMonth))`
→ fusion avec les N mois pré-générés → tri chronologique → DTO.

## 4. Décisions & règles métier

- **Date pilote** : `issueDate` (champ `issue_date`, NOT NULL sur l'entité Invoice → aucun null à gérer).
- **Statuts comptés** : *toutes* les factures non soft-deleted (filtre `deletedAt IS NULL`
  déjà dans `findAllWithFilters`). On NE filtre PAS par statut workflow : une tendance de
  volume/valeur « reçue/émise » compte toutes les factures émises sur le mois,
  indépendamment de leur avancement (contrairement au cash-flow, prospectif, qui exclut
  PAYE/ARCHIVE/REJETE).
- **Fenêtre** : 12 mois glissants par défaut, `?months` configurable, borné `[1, 60]`
  (garde-fou contre une requête démesurée).
- **Mois vides** : pré-remplis à `invoiceCount=0, totalAmount=0` → série continue de N points.
- **monthLabel** : `YYYY-MM` côté backend ; affiché tel quel sur l'axe X (cohérent avec
  `weekLabel` du cash-flow).
- **Montant** : `BigDecimal`, somme de `Invoice::getAmount`.

## 5. Gestion des erreurs

- Backend : `months` hors `[1, 60]` → `ValidationException("reports.trends.invalid_months")`
  → 400 via `GlobalExceptionHandler`. Aucune facture sur la période → DTO avec N points à
  zéro (cas normal, pas une erreur).
- Frontend : erreur de chargement → message i18n ; tous points à zéro / vide →
  `t('reports.noData')` (pattern des sections sœurs).
- Accès : `@PreAuthorize` (DAF/ASSISTANT_COMPTABLE) → 403 sinon. `/reports` déjà sous
  garde de rôle existante côté front.

## 6. Tests

### Backend — service (TDD)
1. Factures réparties sur plusieurs mois → points corrects (count + somme par mois), ordre chronologique.
2. Mois sans facture dans la fenêtre → bucket présent à zéro (série continue, N points).
3. `months` invalide (0 et 61) → `ValidationException`.
4. Factures soft-deleted exclues (verrouille le comportement du repo).

### Backend — IT (`ReportControllerIntegrationTest`)
- `GET /reports/volume-trend` → 200 DAF, 200 ASSISTANT_COMPTABLE, 403 rôle non autorisé.

### Frontend (vitest, ReportsPage)
- Section tendances rend le graphe quand des points existent ; état vide quand tout à zéro.
- Pattern de test recharts : suivre le pattern déjà en place pour la section cash-flow
  (mock `/reports/volume-trend` ; assertion sur le titre/labels de section, recharts rendu
  en jsdom comme les sections existantes).

## 7. Critère de fin

- `./mvnw.cmd test` vert, **0 échec** (tests service + IT inclus ; règle no-failures).
- `tsc --noEmit` 0 erreur ; `vitest` vert.
- `COMPLIANCE_MATRIX.md` : M11 UI #7 + feature #6 🟠 → ✅ ; ligne synthèse M11
  (`18 | 5` → `20 | 3`) ; compteur global (+2 ✅, −2 🟠).
- i18n FR + EN à parité, accents FR OK.
- Flyway non touché (prochaine V64 non consommée).

## Hors périmètre (YAGNI)

Consigné dans `docs/FUTURE_IDEAS.md` (section « M11 #7 — Tendances temporelles ») :
- Filtre par département / fournisseur sur la tendance (couvert par le report builder M11 #9).
- Export dédié du graphe (l'export global existe).
- Granularité hebdo / trimestrielle (mensuel + `?months` suffit).
- Plage de dates libre from/to (décision = fenêtre glissante).
