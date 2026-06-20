# Design — M10 #12 : Rapport de synthèse audit agrégé

**Date :** 2026-06-20
**Item de conformité :** Module 10 (Audit Trail), UI #12 — « Audit summary reports » (🟠 → ✅)
**Branche :** fix/a1-cashflow-sqlgrammar
**Source backlog :** docs/COMPLIANCE_MATRIX.md ligne 337 ; docs/REMEDIATION_PROMPT.md §C M10.

---

## 1. Problème

L'écran audit (`/admin/audit`, `/audit/financial`) offre aujourd'hui des **vues filtrées + export
des logs bruts**, mais aucun **rapport de synthèse agrégé** (totaux groupés). M10 #12 reste 🟠.

Objectif : produire un rapport de synthèse qui agrège `audit_logs` sur une plage de dates, selon
4 dimensions, en respectant la séparation de rôles déjà en place (ADMIN = système, DAF = financier).

## 2. Périmètre

**Inclus :**
- Agrégation par **action**, **utilisateur** (top 10), **type d'entité**, **jour** (tendance).
- Plage de dates `from`/`to` (défaut : 30 derniers jours → aujourd'hui).
- Deux endpoints scopés (système ADMIN / financier DAF) + un endpoint d'export.
- Onglet « Synthèse » sur les pages audit existantes (composant partagé).
- Export csv/excel/pdf du rapport.

**Hors périmètre (autres items E, commits séparés) :** M10 #10 (indicateur de rétention sur
l'écran audit), M14 #11 (rapport de conformité archives). Pas de nouvelle table, pas de chart lib.

## 3. Architecture

Aucune nouvelle table : agrégation à la volée via requêtes `GROUP BY` sur `audit_logs`
(même approche que la détection d'anomalies M10, cf. `AuditLogRepository.countByUserSince`).

### Backend

**`AuditLogRepository`** — 4 requêtes `GROUP BY`, chacune bornée par `from`/`to` et filtrée par une
liste d'actions autorisées (`a.action IN :allowedActions`). C'est cette clause `IN` qui applique le
scoping système/financier au niveau SQL, cohérent avec `searchLogsWithActionFilter`.

```java
@Query("""
    SELECT a.action, COUNT(a) FROM AuditLog a
    WHERE a.createdAt >= :from AND a.createdAt < :to AND a.action IN :allowedActions
    GROUP BY a.action ORDER BY COUNT(a) DESC
""")
List<Object[]> summaryByAction(Instant from, Instant to, List<String> allowedActions);

// summaryByEntityType : GROUP BY a.entityType, même bornes + filtre.
// summaryByDay        : GROUP BY CAST(a.createdAt AS date), ORDER BY date ASC.
// summaryByUser       : LEFT JOIN sur a.user, GROUP BY username (null -> label fallback),
//                       ORDER BY COUNT DESC ; top 10 appliqué côté service (limit liste).
```

Note `byDay` : grouper par jour via `cast(a.createdAt as date)` (JPQL `CAST ... AS date` /
fonction Hibernate). Si le dialecte pose souci, repli sur une requête native bornée — décision au
moment du TDD repository.

Note `byUser` : `username` obtenu via la relation `a.user` (LEFT JOIN, car `user` est nullable —
actions système). Les logs sans utilisateur regroupés sous une clé i18n fallback (`—`).

**`AuditService`** — nouvelle méthode :
```java
AuditSummaryDTO summarize(LocalDate from, LocalDate to, List<String> allowedActions);
```
Implémentation : convertit `from`/`to` en `Instant` (début de `from` ... début de `to`+1 jour,
borne haute exclusive), appelle les 4 requêtes, mappe `Object[]` → `CountEntry`, tronque `byUser`
au top 10, calcule `totalEvents` (somme de `byAction`).

**DTOs** (records, dans `domain/audit/dto`) :
```java
public record AuditSummaryDTO(LocalDate from, LocalDate to, long totalEvents,
                              List<CountEntry> byAction, List<CountEntry> byUser,
                              List<CountEntry> byEntityType, List<CountEntry> byDay) {}
public record CountEntry(String label, long count) {}
```

**`AuditController`** — réutilise les constantes `SYSTEM_ACTIONS` / `FINANCIAL_ACTIONS` existantes.

## 4. Contrat des endpoints

```
GET /api/v1/audit-logs/summary/system      @PreAuthorize hasRole('ADMIN')   -> SYSTEM_ACTIONS
GET /api/v1/audit-logs/summary/financial   @PreAuthorize hasRole('DAF')     -> FINANCIAL_ACTIONS
   params: from (ISO-8601 date, defaut J-30), to (ISO-8601 date, defaut aujourd'hui)
   reponse: ApiResponse<AuditSummaryDTO>, message i18n audit.summary.retrieved

GET /api/v1/audit-logs/summary/export      @PreAuthorize hasRole('ADMIN') or hasRole('DAF')
   params: format=csv|excel|pdf (defaut csv), scope=system|financial, from, to
   garde SoD : un DAF demandant scope=system -> 403 ; un ADMIN demandant scope=financial -> 403.
   reponse: fichier, Content-Disposition attachment; filename=audit_summary.<ext>
```

L'export aplatit les 4 dimensions en lignes via `TabularExportService.export(format, title, headers, rows)` :
une section par dimension, colonnes `Dimension | Libellé | Nombre`.

## 5. Frontend

Onglets **Journal / Synthèse** ajoutés à `AdminAuditPage` (ADMIN) et `FinancialAuditPage` (DAF).
L'onglet Synthèse rend un composant **partagé** `<AuditSummary scope="system" | "financial" />`
(seul `scope` diffère — pas de duplication).

`<AuditSummary>` :
- En-tête : 2 champs date (du / au, defaut J-30) + `ExportMenu endpoint="/audit-logs/summary/export"`
  params `{ scope, from, to }`, filename `audit_summary`.
- Carte KPI : `totalEvents`.
- 4 panneaux (style `bg-white rounded-xl border p-4`, comme `AnomalyPanel`) :
  par action, top utilisateurs, par type d'entité, par jour (barres horizontales largeur ∝ count/max).
- `useQuery(['audit-summary', scope, from, to])` → `apiClient.get('/audit-logs/summary/' + scope, { params:{from,to} })`.
- Loading : `Loader2`. Vide : `t('app.noData')`.

## 6. i18n

Nouvelles clés sous `admin.audit.summary.*` (titre, onglets journal/synthese, du, au, total,
dimensions action/user/entity/day, label utilisateur inconnu) en **FR et EN** (parité).

⚠ `messages_fr.properties` est en ISO-8859-1 (cf. memoire messages-fr-iso-8859-1) : ajouter les
clés FR via `iconv`, sans em-dash ni guillemets courbes, pour ne pas corrompre les accents.

## 7. Gestion d'erreurs

- `from > to` ou date invalide → 400 via `GlobalExceptionHandler` (clé i18n `audit.summary.invalidRange`).
- Export avec `scope` non autorisé pour le rôle courant → 403.
- Rapport en lecture seule : aucune écriture en base (cohérent avec `audit_logs` append-only).

## 8. Tests (TDD — test d'abord)

- **Repository** (`@DataJpaTest`) : comptages corrects par dimension ; filtre `allowedActions`
  exclut le hors-périmètre ; bornes `from`/`to` respectées ; `byDay` groupe par jour ;
  user null → présent sous fallback.
- **Service** (`AuditServiceTest`) : DTO assemblé (total = somme byAction, top 10 byUser, fallback user null).
- **Controller** : `/summary/system` 200 ADMIN / 403 DAF ; `/summary/financial` inverse ;
  `/summary/export?scope=system` 403 pour DAF (garde SoD) ; défauts de dates.
- **Frontend** (vitest) : `<AuditSummary>` rend les panneaux depuis un mock ; état vide ;
  changement de dates → re-fetch.

## 9. Critère de fin (règle no-failures-on-task-completion)

- `mvnw.cmd test` : 0 échec. `tsc` : 0 erreur. `vitest` : tout vert.
- `docs/COMPLIANCE_MATRIX.md` M10 #12 (et Features correspondante) passé 🟠 → ✅ avec preuve.
- `docs/KNOWN_ISSUES_REGISTRY.md` mis à jour si un bug est rencontré.
- Commit atomique `feat(m10-12): ...`. Pas de push tant que le compte < 10 (regle push-every-10-commits).
