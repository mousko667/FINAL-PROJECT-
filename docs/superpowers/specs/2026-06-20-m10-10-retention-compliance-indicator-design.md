# M10 #10 — Indicateur de conformité de rétention sur l'écran audit

**Date :** 2026-06-20
**Tâche :** M10 #10 (COMPLIANCE_MATRIX ligne 335, 🟠)
**Branche :** fix/a1-cashflow-sqlgrammar
**Statut backlog avant :** 🟠 « Rétention gérée en M9/M14 ; pas d'indicateur de conformité de rétention sur l'écran audit »

---

## 1. Objectif

Afficher un **indicateur de conformité de rétention** sur l'écran audit administrateur
(`/admin/audit`, ROLE_ADMIN), comblant le gap M10 #10. La politique de rétention est déjà
gérée (B2 / M9 #7 / M14 #6) : entité singleton DB-backed, job quotidien non-destructif
(`DocumentRetentionJob`) qui marque les documents périmés. Il manque uniquement une
**visualisation de l'état de conformité** sur l'écran audit.

### Hors périmètre (YAGNI)
- Aucune modification du CRUD de la politique (`RetentionPolicyController` GET/PUT).
- Aucune modification du `DocumentRetentionJob`.
- Pas d'affichage côté DAF / audit financier (séparation des devoirs PROB-065 : la rétention
  est un paramètre technique/conformité système, géré par ADMIN ; pas de croisement).
- Pas d'écran dédié : un simple panneau sur l'écran audit existant.

---

## 2. Architecture & flux

```
DocumentRetentionJob (quotidien) ──recordSweep──> RetentionPolicy (singleton DB)
                                                          │
RetentionPolicyService.evaluateCompliance() ───lit──────┘
   │ calcule status (CONFORME / ATTENTION / NON_CONFORME)
   ▼
GET /api/v1/retention-policy/compliance  (@PreAuthorize hasRole('ADMIN'))
   │ ApiResponse<RetentionComplianceDTO>
   ▼
AdminAuditPage › onglet Journal › <RetentionComplianceCard/> (en haut)
```

Principe d'isolation : le calcul de conformité est une nouvelle méthode dédiée dans
`RetentionPolicyService`, séparée du CRUD, testable indépendamment. Nouveau endpoint dédié
`/compliance` et nouveau DTO `RetentionComplianceDTO` : la conformité (lecture/calcul) ne se
mélange pas avec la configuration (lecture/écriture).

---

## 3. Backend

### 3.1 Règle de calcul du statut

À partir de l'entité `RetentionPolicy` (`active`, `lastSweepAt`, `lastFlaggedCount`) :

| Statut          | Condition |
|-----------------|-----------|
| `NON_CONFORME`  | politique **inactive** (`active == false`) |
| `ATTENTION`     | active, ET (`lastSweepAt == null` ou dernier balayage > seuil) **OU** `lastFlaggedCount > 0` |
| `CONFORME`      | active ET dernier balayage ≤ seuil ET `lastFlaggedCount == 0` (null traité comme 0) |

Seuil de fraîcheur du balayage : configurable via `application.yml`
`app.retention.compliance.sweep-max-age-hours:48` (pas de magic number).
`sweepOverdue` (booléen dérivé) = `lastSweepAt == null` ou plus ancien que le seuil.

### 3.2 Enum

`RetentionComplianceStatus { CONFORME, ATTENTION, NON_CONFORME }`
(package `domain.retention.model`).

### 3.3 DTO

`RetentionComplianceDTO` (record, package `domain.retention.dto`) :

```
status            : RetentionComplianceStatus
retentionYears    : int
active            : boolean
lastSweepAt       : Instant (nullable)
lastFlaggedCount  : Integer (nullable)
sweepOverdue      : boolean
updatedAt         : Instant (nullable)
```

### 3.4 Service

Méthode `evaluateCompliance()` dans `RetentionPolicyService` :
- `@Transactional(readOnly = true)`
- réutilise `getEntity()` (seeding garanti)
- lit le seuil via `@Value("${app.retention.compliance.sweep-max-age-hours:48}")`
- applique la règle 3.1 et retourne `RetentionComplianceDTO`.

### 3.5 Endpoint

Dans `RetentionPolicyController` :

```java
@GetMapping("/compliance")
@PreAuthorize("hasRole('ADMIN')")
@Operation(summary = "Get retention compliance status")
public ApiResponse<RetentionComplianceDTO> compliance() {
    return ApiResponse.success(service.evaluateCompliance());
}
```

### 3.6 Tests backend

- **Unitaires** (`RetentionPolicyServiceTest`) : `evaluateCompliance()` pour les 3 statuts +
  cas limites : `lastSweepAt == null`, balayage juste sous/au-dessus du seuil,
  `lastFlaggedCount == null` vs `> 0`, politique inactive prioritaire sur le reste.
- **Intégration** (`RetentionPolicyControllerIntegrationTest`) : `GET /compliance` → 200 pour
  ADMIN, 403 pour un rôle non-ADMIN.

---

## 4. Frontend

### 4.1 Composant `RetentionComplianceCard.tsx`

Emplacement : `frontend/src/components/audit/RetentionComplianceCard.tsx`.

- `useQuery(['retention-compliance'])` → `GET /retention-policy/compliance`, `retry: false`.
- Carte au style des panneaux existants (`bg-white rounded-xl border p-4`) :
  - Titre + icône `ShieldCheck` (lucide-react).
  - **Badge de statut** coloré : CONFORME = vert, ATTENTION = ambre, NON_CONFORME = rouge.
  - Détails : période de rétention (`X ans`), politique active (oui/non), dernier balayage
    (date), nombre de documents marqués.
  - Message contextuel selon le statut (ex. `sweepOverdue` → message « balayage en retard »).
- Erreur / 403 → `return null` (masquage silencieux, comme `AnomalyPanel`).

### 4.2 Intégration

Dans `AdminAuditPage`, onglet **Journal**, tout en haut (au-dessus de `<AnomalyPanel/>`).

### 4.3 i18n frontend

Clés sous `admin.audit.retention.*` dans `frontend/src/i18n/{fr,en}.json` (clé `translation`) :
titre, libellés des champs (période, active oui/non, dernier balayage, docs marqués),
3 libellés de statut, messages contextuels (dont balayage en retard).

### 4.4 Test frontend

`frontend/src/test/components/RetentionComplianceCard.test.tsx` :
- rend les 3 statuts (badge + classe couleur attendue) ;
- masquage (`null`) quand la requête échoue.

---

## 5. i18n backend & documentation

- `messages_fr.properties` / `messages_en.properties` : aucune clé nécessaire a priori (endpoint
  en lecture seule, pas de message de succès). Si une clé est ajoutée : ⚠ `messages_fr` est en
  ISO-8859-1 → ajout via iconv, ASCII-safe (pas d'em-dash ni de guillemets courbes).
- `docs/COMPLIANCE_MATRIX.md` ligne 335 : 🟠 → ✅ avec note décrivant l'indicateur.

---

## 6. Critère de fin

- `./mvnw.cmd test` : 0 échec backend.
- `tsc` : 0 erreur.
- `vitest` : tous les tests verts.
- COMPLIANCE_MATRIX ligne 335 = ✅.
