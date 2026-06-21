# M13 #3 — UI accès par département (Department Access Overview)

**Date:** 2026-06-21
**Module:** M13 — User & Access Management
**Compliance gap:** M13 UI #3 + Feature #3 (🟠 → ✅)
**Branche:** fix/a1-cashflow-sqlgrammar (lot E)

---

## 1. Contexte & objectif

Le scope d'accès par département existe déjà **fonctionnellement** côté backend :
- `User.departmentId` rattache chaque utilisateur à un département.
- `Department` porte `requiresN2`, `n1Role`, `n2Role` (niveau de validation).
- Les requêtes factures sont déjà filtrables par `departmentId` ; `DepartmentTransitionGuard` route N1→N2 selon le département.

Le gap audité (COMPLIANCE_MATRIX, M13 UI #3 / Feature #3) est précis :
**« contrôle d'accès par département fonctionnel mais sans UI dédiée (scope dérivé du rôle) »**.

**Objectif :** fournir un écran d'administration **en lecture seule** qui rend visible et
auditable le scope d'accès par département. Aucune nouvelle règle de sécurité, aucune
donnée financière. Le verdict 🟠 devient ✅ dès qu'un écran dédié expose ce scope.

**Périmètre retenu (décisions utilisateur) :**
- But : **visualisation (lecture seule)**.
- Accès : **ADMIN seul**.
- Contenu : **users + rôles par dépt**, **niveau de validation (N1 / N1→N2)**, **compteurs/résumé**.
- Hors périmètre : section « users sans département » (non retenue) ; toute (ré)affectation
  d'utilisateur ; tout nouveau modèle de permissions départementales.

---

## 2. Contraintes (projet)

- `./mvnw.cmd` (backend racine), front depuis `frontend/`.
- **Aucune migration** : pas de nouvelle table → Flyway V64 reste non consommée.
- apiClient **sans** préfixe `/api/v1` (PROB-038).
- i18n FR + EN à parité. Backend `messages_fr.properties` = ISO-8859-1 (clés ASCII pur) —
  a priori **aucun** message backend requis (lecture seule).
- Erreurs front affichées via `t(key)` (PROB-006) ; `RoleGuard fallback={null}` en nav,
  `PageRoleGuard` en page (PROB-004).
- Règle no-failures : 0 échec backend, `tsc` 0, vitest vert.

---

## 3. SoD & sécurité (PROB-065)

- `@PreAuthorize("hasRole('ADMIN')")` sur l'endpoint ; `PageRoleGuard`/`RoleGuard` ADMIN au front.
- **Aucune donnée financière exposée**, garanti *par construction* : les DTO ne portent
  **ni** `Department.budget` **ni** `User.approvalLimit` **ni** aucun montant de facture.
- Lecture seule : aucun endpoint d'écriture ; le modèle de sécurité existant est inchangé.
- Reports/finance (DAF + ASSISTANT_COMPTABLE) ne sont pas touchés.
- Test SoD explicite : un non-ADMIN (ex. DAF) reçoit **403**.

---

## 4. Backend

### 4.1 DTO (`domain/department/dto/`, records)

```java
public record DepartmentAccessDTO(
        UUID departmentId,
        String code,
        String nameFr,
        String nameEn,
        boolean requiresN2,
        String n1Role,
        String n2Role,
        int userCount,
        int activeCount,
        List<DepartmentUserDTO> users) {}

public record DepartmentUserDTO(
        UUID userId,
        String fullName,
        String username,
        boolean active,
        List<String> roles) {}
```

Volontairement **sans** `budget` ni `approvalLimit`.

### 4.2 Repository (`UserRepository`, ajout)

```java
@EntityGraph(attributePaths = {"userRoles", "userRoles.role"})
List<User> findByDepartmentIdIn(Collection<UUID> departmentIds);
```

Charge users + rôles en une passe (évite N+1). Agrégation en mémoire (échelle ~18 users).

### 4.3 Service

`DepartmentAccessService` (interface) + `DepartmentAccessServiceImpl` :

```java
/** Retourne, par département (trié par code), les utilisateurs rattachés,
 *  leurs rôles et le niveau de validation. Lecture seule ; aucune donnée financière. */
List<DepartmentAccessDTO> getDepartmentAccessOverview();
```

- Récupère tous les départements triés par `code`.
- Charge les users de ces départements via `findByDepartmentIdIn`.
- Regroupe par `departmentId`, calcule `userCount` (total) et `activeCount` (actifs).
- Mappe `userRoles → role.name` (List<String>).
- `fullName` = `firstName + " " + lastName`.
- Javadoc sur la méthode publique.

### 4.4 Controller

`DepartmentAccessController` :

```
GET /api/v1/admin/department-access
@PreAuthorize("hasRole('ADMIN')")
@Operation(...)  // Swagger
→ ResponseEntity<ApiResponse<List<DepartmentAccessDTO>>>
```

Réponse enveloppée dans `ApiResponse`. Endpoint sous `/admin/*` (administration des accès,
distinct du CRUD `/departments`).

---

## 5. Frontend

### 5.1 Service (`frontend/src/services/departmentAccessService.ts`)

- `getDepartmentAccessOverview(): Promise<DepartmentAccessDTO[]>`
  via `apiClient.get('/admin/department-access')` (sans préfixe `/api/v1`).
- Types TS miroir des DTO.

### 5.2 Page (`frontend/src/pages/admin/DepartmentAccessPage.tsx`)

- Titre + sous-titre (i18n).
- **Accordéons par département**. En-tête :
  - `code` + nom (selon langue), badge **N1** ou **N1→N2** (selon `requiresN2`),
  - compteurs « X utilisateurs · Y actifs ».
- Corps déplié : **table users × rôles** (nom complet, username, badge actif/inactif,
  rôles en chips). Rôles N1/N2 du dépt mis en évidence si présents.
- États : chargement (spinner), erreur (`t(key)`), vide (« aucun département »).
- **Aucun montant affiché.**
- Suivre les patterns de `EscalationRulesPage` / `AdminChecklistTemplatesPage`.

### 5.3 Navigation

- Entrée Sidebar dans le groupe admin (près de `/admin/users`, `/admin/permissions`),
  sous `RoleGuard` ADMIN (`fallback={null}`).
- Route `/admin/department-access` dans `AppRoutes.tsx`, protégée par `PageRoleGuard` ADMIN.

---

## 6. i18n

- Front : clés dans `fr.json` **et** `en.json` (titre, sous-titre, colonnes, badges N1 / N1→N2,
  compteurs, états vide/erreur).
- Back : a priori aucun message requis. Si besoin, clés dans `messages_fr.properties`
  (ISO-8859-1, ASCII pur) **et** `messages_en.properties`.

---

## 7. Tests

- **Service (unit, Mockito)** : agrégation correcte (regroupement par dépt, `userCount`/
  `activeCount`), dépt sans user (listes vides, compteurs 0), mapping des rôles, `fullName`.
- **Controller (intégration, MockMvc)** : 200 + forme `ApiResponse` pour ADMIN ;
  **403** pour un non-ADMIN (test SoD) ; 401 non authentifié.
- **Frontend (vitest)** : rendu (accordéons, compteurs, badge N1/N2), état vide, état erreur
  (service mocké).
- **Gate** : backend 0 échec, `tsc` 0, vitest vert.

---

## 8. Critères d'acceptation

- [ ] `GET /api/v1/admin/department-access` renvoie l'aperçu agrégé, ADMIN only (403 sinon).
- [ ] Aucun champ financier dans les DTO ni dans la réponse.
- [ ] Page `/admin/department-access` : accordéons par dépt, users×rôles, badge N1/N2, compteurs.
- [ ] Entrée Sidebar + route protégées ADMIN.
- [ ] i18n FR + EN à parité.
- [ ] Aucune migration ajoutée (V64 intacte).
- [ ] Gate vert (backend 0 échec, tsc 0, vitest vert).
- [ ] COMPLIANCE_MATRIX M13 UI #3 + Feature #3 : 🟠 → ✅.
