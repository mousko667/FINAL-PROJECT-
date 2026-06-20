# B2 — Configuration de la politique de rétention (M9 #7 / M14 #6)

**Date :** 2026-06-20
**Branche :** fix/a1-cashflow-sqlgrammar
**Lot :** Section C, familles B+C — tâche B2
**Statut :** Design approuvé → implémentation

---

## 1. Problème

`DocumentRetentionJob` lit aujourd'hui le seuil de rétention via
`@Value("${app.retention.years:10}")` — codé en config statique, non modifiable
sans éditer un fichier et redémarrer le serveur. M9 #7 / M14 #6 exigent que cette
politique soit **configurable depuis l'application**, auditée, sans redémarrage.

Même nature de gap que PROB-061 (alertes de paiement codées en dur) résolu en B4.

## 2. Décision de conception

**Modèle singleton (Option A)** — une seule politique globale, pas une liste de règles.
Justification : le job actuel raisonne sur un seuil unique global ; la conformité
comptable impose une durée d'archivage unique ; YAGNI. L'Option B (liste multi-règles
par type/département) est consignée dans `docs/FUTURE_IDEAS.md`.

**Contrôle d'accès :** ADMIN uniquement. La config ne contient aucune donnée financière
(ni montant, ni fournisseur) → pas de conflit avec la règle de séparation des devoirs.

## 3. Architecture

Nouveau domaine `domain/retention/`, symétrique à `domain/workflow/` (EscalationRule).

```
AdminRetentionPolicyPage.tsx
   GET /api/v1/retention-policy   (lecture)
   PUT /api/v1/retention-policy   (maj : retentionYears + active)   ADMIN only
        ▼
RetentionPolicyController → RetentionPolicyService → RetentionPolicyRepository
                                   │ garantit le singleton (get-or-seed)
                                   ▼
                       DocumentRetentionJob lit la config DB
                       (fallback app.retention.years si table vide)
                       écrit lastSweepAt + lastFlaggedCount après balayage
```

## 4. Modèle de données

Table `retention_policy` (migration **V62**), une seule ligne (singleton seedé).

| Colonne | Type | Éditable UI | Note |
|---|---|---|---|
| id | UUID PK | — | |
| retention_years | int NOT NULL | ✅ | seuil principal (seed = 10) |
| active | boolean NOT NULL | ✅ | interrupteur du balayage (seed = true) |
| last_sweep_at | timestamptz NULL | lecture seule | écrit par le job |
| last_flagged_count | int NULL | lecture seule | écrit par le job |
| updated_by | UUID FK users NULL | — | audit |
| updated_at | timestamptz NOT NULL | — | audit (@LastModifiedDate) |
| created_at | timestamptz NOT NULL | — | @CreatedDate |

Booléen nommé `active` (PAS `isActive`) — piège Lombok PROB-003.
Le cron du balayage reste dans `app.retention.cron` (config technique, non exposé).

V62 seed la ligne initiale : `retention_years = 10`, `active = true`.

## 5. Composants backend

- `RetentionPolicy` (entity, @EntityListeners AuditingEntityListener)
- `RetentionPolicyRepository extends JpaRepository<RetentionPolicy, UUID>` — `findFirstByOrderByCreatedAtAsc()`
- `RetentionPolicyDTO` (record) — retentionYears, active, lastSweepAt, lastFlaggedCount, updatedAt
- `RetentionPolicyRequest` (record) — `@Min(1) @Max(100) int retentionYears`, `boolean active`
- `RetentionPolicyService` :
  - `get()` — get-or-seed : retourne la ligne, la crée (10/true) si absente
  - `update(request, currentUser)` — met à jour retentionYears + active, set updatedBy
  - `recordSweep(Instant when, int flaggedCount)` — appelé par le job
- `RetentionPolicyController` `@RequestMapping("/api/v1/retention-policy")` :
  - `GET` `@PreAuthorize("hasRole('ADMIN')")` → `ApiResponse<RetentionPolicyDTO>`
  - `PUT` `@PreAuthorize("hasRole('ADMIN')")` `@Valid @RequestBody` → `ApiResponse<RetentionPolicyDTO>`, message `retention_policy.updated`

## 6. Modification du job

`DocumentRetentionJob.flagDocumentsPastRetention()` :
- lit `retentionYears` + `active` via `RetentionPolicyService.get()` (fallback `@Value` si la table est vide — robustesse au démarrage initial)
- si `active == false` → skip (log debug, pas de balayage)
- après le balayage, appelle `recordSweep(now, flaggedCount)`

## 7. Frontend

`AdminRetentionPolicyPage.tsx` (pattern React Query, baseURL `/api/v1` → appeler `/retention-policy`) :
- `useQuery(['retention-policy'])` → GET
- formulaire : champ « durée de rétention (années) » + interrupteur « balayage actif »
- bloc lecture seule : dernier balayage (date) + nombre de documents flaggés
- `useMutation` PUT → invalide `['retention-policy']`
- Route dans AppRoutes.tsx sous garde ADMIN (PageRoleGuard) + entrée Sidebar (RoleGuard fallback null), section admin

## 8. i18n

- `messages_fr.properties` (ISO-8859-1, via iconv) + `messages_en.properties` (ASCII) :
  clés `retention_policy.updated`
- `frontend/src/i18n/{fr,en}.json` (UTF-8) : libellés page (titre, champs, aide, dernier balayage)

## 9. Tests (TDD RED→GREEN)

- **Service (unit)** : get-or-seed crée la ligne si absente ; get retourne l'existante sans dupliquer ; update modifie retentionYears+active et set updatedBy ; recordSweep écrit lastSweepAt+count.
- **Controller (integration)** : GET/PUT en ADMIN = 200 ; DAF et autres rôles = 403 ; validation (retentionYears < 1) = 400.
- **Job** : lit la valeur DB (pas le @Value) ; skip si active=false ; appelle recordSweep avec le bon count ; fallback config si table vide.
- **Frontend (vitest)** : la page rend le formulaire avec la valeur chargée ; soumet le PUT avec les bonnes données (apiClient mocké, QueryClientProvider).

## 10. Conventions respectées

ApiResponse<T> ; @PreAuthorize sur chaque méthode ; DTO records, jamais l'entité ;
Flyway V62 (jamais modifier une migration appliquée) ; booléen `active` ; baseURL
frontend sans préfixe ; i18n FR Latin-1 via iconv. KNOWN_ISSUES si bug rencontré.
COMPLIANCE_MATRIX : flip M9 #7 / M14 #6 en ✅.
