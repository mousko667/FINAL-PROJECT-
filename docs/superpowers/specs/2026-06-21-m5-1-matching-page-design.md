# M5 #1 + #4 — Page de rapprochement dédiée (comparaison ligne-à-ligne)

**Date :** 2026-06-21
**Lot :** E — finition des 🟠 partiels
**Items couverts :** M5 #1 (interface de rapprochement dédiée) + M5 #4 (comparaison ligne-à-ligne PO/GRN/facture)
**Items voisins laissés 🟠 (hors périmètre, YAGNI) :** #9 viewer d'historique, #10 workflow de résolution ligne-par-ligne.

---

## 1. Objectif & principe directeur

Fournir une page `/matching` autonome (liste filtrable de tous les rapprochements 3-voies)
et une vue détail avec comparaison **ligne-à-ligne** PO / GRN / facture, **sans toucher au
chemin d'écriture du matching** ni consommer de migration.

**Principe :** tout est **calculé en lecture** à partir des entités déjà liées
(Invoice ↔ PurchaseOrder ↔ GoodsReceiptNote) et du dernier `ThreeWayMatchingResult`.
Append-only respecté, **V64 reste libre**, cohérent avec le pattern « rapport calculé en
temps réel » déjà adopté (`ArchiveComplianceService`).

**Ce qui existe déjà (vérifié dans le code) :**
- Matching 3-voies calculé à la soumission ; `ThreeWayMatchingResult` append-only
  (statut MATCHED/PARTIAL/MISMATCH/OVERRIDDEN, `discrepancyNotes` texte).
- `GET /{id}/matching` (dernier résultat), `GET /{id}/matching/export` (csv/excel/pdf),
  `POST /{id}/matching/override` (DAF/ADMIN).
- Front : matching affiché **dans le détail facture** ; `AdminMatchingConfigPage` (config tolérance).

**Ce qui manque (le 🟠) :** une **page dédiée** (pas un encart) + la comparaison **ligne-à-ligne**.

---

## 2. Découpage (unités à responsabilité unique)

- **`MatchingComparator`** (nouveau, `domain/purchasing`) — logique de tolérance par ligne,
  **extraite** de `ThreeWayMatchingService.isWithinTolerance/performMatching` pour réutilisation
  sans duplication. `ThreeWayMatchingService` l'appelle ensuite (refactor **sans changement de
  comportement**).
- **`MatchingQueryService`** (nouveau) — lecture seule : liste paginée + recomposition
  ligne-à-ligne. Dépend uniquement des repositories + `MatchingComparator`.
- **`MatchingQueryController`** (nouveau) — `GET /api/v1/matching`, `GET /api/v1/matching/{invoiceId}/lines`.
- **Front** — `MatchingListPage.tsx`, `MatchingDetailPage.tsx`, `matchingService.ts`, route + sidebar.

---

## 3. DTOs (lecture seule, aucun changement de schéma)

### Liste — `MatchingSummaryDTO` (un par facture, dernier résultat)
```
record MatchingSummaryDTO(
    UUID invoiceId, String invoiceNumber, String supplierName,
    UUID purchaseOrderId, String purchaseOrderNumber, boolean grnPresent,
    MatchingStatus status,        // MATCHED | PARTIAL | MISMATCH | OVERRIDDEN
    int lineCount, int discrepancyLineCount,
    Instant matchedAt)
```

### Détail — `MatchingDetailDTO` + `LineComparison`
```
record MatchingDetailDTO(
    MatchingSummaryDTO summary, String discrepancyNotes,
    UUID overriddenBy, String overrideReason, List<LineComparison> lines)

record LineComparison(
    String description,
    BigDecimal poQuantity, BigDecimal poUnitPrice,
    BigDecimal receivedQuantity,        // GRN, null si pas de GRN
    BigDecimal invoiceQuantity, BigDecimal invoiceUnitPrice,
    BigDecimal qtyVariancePct, BigDecimal priceVariancePct,
    LineVerdict verdict)

enum LineVerdict { MATCHED, WITHIN_TOLERANCE, MISMATCH, MISSING_IN_PO }
```

**Appariement des lignes :** par `description` (insensible casse), comme `performMatching`
le fait déjà. Ligne facture sans équivalent PO → `MISSING_IN_PO`. Le verdict réutilise
`MatchingComparator` (tolérance % / montant de la `MatchingConfig` active).

---

## 4. Endpoints, autorisations, erreurs

Controller `MatchingQueryController` — `@RequestMapping("/api/v1/matching")`, `@Tag("Matching")`.
Tous les endpoints :
`@PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")`
→ exactement le périmètre des endpoints matching existants (DAF, ASSISTANT_COMPTABLE,
validateurs ; **ADMIN exclu = SoD**).

| Méthode | Endpoint | Réponse |
|---|---|---|
| GET | `/api/v1/matching?status=&search=&page=&size=` | `ApiResponse<PagedResponse<MatchingSummaryDTO>>` |
| GET | `/api/v1/matching/{invoiceId}/lines` | `ApiResponse<MatchingDetailDTO>` |

**Liste :** `status` optionnel (filtre `MatchingStatus`) ; `search` optionnel (n° facture /
fournisseur / n° PO, insensible casse) ; pagination `PagedResponse` ; tri `matchedAt` desc.
Source = requête repo paginée sur `ThreeWayMatchingResult` retenant le **dernier résultat par
facture** (created_at = max). **Bug Postgres connu (PROB-038/054) :** tout paramètre nullable
sera typé via `CAST(:param AS ...)`.

**Lignes :** recomposées à la volée. `404` (`ResourceNotFoundException` → `GlobalExceptionHandler`)
si facture inexistante ou sans PO lié. Message via `MessageSource` (clé i18n FR+EN, fichier
backend `messages_fr.properties` en **ISO-8859-1**, clé en ASCII).

**Export :** pas de nouvel endpoint — réutilise `GET /invoices/{id}/matching/export` existant.

---

## 5. Frontend

- **`matchingService.ts`** : `listMatching(params)`, `getMatchingLines(invoiceId)`.
  Via `apiClient` (base **sans** `/api/v1` — PROB-038) → chemins `/matching`, `/matching/{id}/lines`.
- **`MatchingListPage.tsx`** (`/matching`) : `PageRoleGuard` (staff hors SUPPLIER/ADMIN).
  Tableau (n° facture, fournisseur, PO, GRN oui/non, badge statut, lignes en écart, date),
  filtres (statut + recherche debounced), pagination (`useQuery`), états loading/vide/erreur,
  clic → `/matching/{invoiceId}`.
- **`MatchingDetailPage.tsx`** (`/matching/:invoiceId`) : récap + notes divergence + bloc
  override ; **tableau ligne-à-ligne** (Description | PO qté/PU | Reçu GRN qté | Facture qté/PU |
  Écart qté % | Écart prix % | badge verdict), lignes MISMATCH/MISSING_IN_PO surlignées ;
  bouton **Exporter** (csv/excel/pdf → endpoint existant) ; liens retour facture + liste.
- **Routing/nav** : routes sous guard staff dans `AppRoutes.tsx` ; entrée Sidebar
  « Rapprochement » via `RoleGuard fallback={null}` (masquée SUPPLIER/ADMIN).
- **i18n** : clés `matching.*` dans `fr.json` **et** `en.json` (parité, sans collision).

---

## 6. Tests & critères de complétion

### Backend (TDD)
- `MatchingComparatorTest` : MATCHED exact, WITHIN_TOLERANCE, MISMATCH, MISSING_IN_PO ;
  équivalence de comportement avec l'ancienne logique inline (non-régression refactor).
- `MatchingQueryServiceTest` (repos mockés) : liste = dernier résultat/facture ; filtre statut ;
  recherche ; `getMatchingLines` recompose qté/prix/écarts/verdict ; 404 facture sans PO.
- `MatchingQueryControllerIntegrationTest` (MockMvc) : `GET /matching` → 200 paginé pour
  DAF/ASSISTANT_COMPTABLE/validateur, **403** SUPPLIER + ADMIN, 401 anonyme ; filtres status/search ;
  `GET /matching/{id}/lines` → 200 + lignes, 404 inexistant/sans PO.
- Non-régression : suite matching existante reste verte (refactor via `MatchingComparator`).

### Frontend (vitest)
- `MatchingListPage.test.tsx` : rendu liste, filtre statut, état vide, navigation au clic.
- `MatchingDetailPage.test.tsx` : tableau ligne-à-ligne, badges verdict, bouton export, surlignage écarts.

### Gate (0 échec — règle no-failures)
- [ ] `./mvnw.cmd test` vert.
- [ ] `npm run build` (tsc) + `vitest` verts.
- [ ] Parité i18n FR/EN (`matching.*`, aucune collision).
- [ ] Aucune migration consommée (V64 libre).
- [ ] `@PreAuthorize` + `@Operation` Swagger + Javadoc service.
- [ ] SoD : ADMIN et SUPPLIER exclus.
- [ ] Vérif runtime (vrai 200 liste + chargement lignes, pas seulement DOM — PROB-038).
- [ ] `docs/COMPLIANCE_MATRIX.md` : M5 #1 et #4 → ✅ avec preuve ; `KNOWN_ISSUES_REGISTRY.md` si bug.

### Hors périmètre (YAGNI)
Viewer d'historique (#9), workflow de résolution ligne-par-ligne (#10), persistance des lignes
(table) — restent 🟠.
