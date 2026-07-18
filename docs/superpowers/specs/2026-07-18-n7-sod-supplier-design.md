# Spec — Fix N7 : SoD accès référentiel fournisseur (DAF)

**Date :** 2026-07-18
**Branche :** `fix/n7-sod-supplier`
**Finding :** N7 (🟠 SoD) — `docs/QA_AUDIT_EXHAUSTIF.md`
**Périmètre :** backend uniquement (le frontend est déjà conforme).

---

## 1. Problème

Le backend autorise encore `ROLE_DAF` en **lecture** sur 6 endpoints d'administration du
référentiel fournisseur. Or :

- La règle de séparation des tâches (SoD) veut que le DAF **valide et paie** les factures ; il
  **n'administre pas** le référentiel fournisseur (administré par `ADMIN` + `ASSISTANT_COMPTABLE`).
- Le briefing (`docs/REQUIREMENTS-MATRIX.md`, Module 8 « Supplier Management ») ne rattache pas le
  référentiel fournisseur au rôle DAF.
- Le **frontend est déjà conforme** : les 4 pages fournisseur (`SuppliersPage`,
  `SupplierDetailPage`, `SupplierFormPage`, `SupplierOnboardingPage`) sont gardées
  `PageRoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE']}`, et l'entrée de menu « Fournisseurs »
  de la Sidebar est réservée `ROLE_ASSISTANT_COMPTABLE` seul.

C'est donc une **désynchronisation back/front** : le garde-fou réel (backend `@PreAuthorize`) est
plus permissif que l'UI. Un DAF ne peut atteindre ces pages via l'UI, mais **l'API répond encore
à un appel direct** avec un JWT DAF.

## 2. Décision métier (tranchée avec l'utilisateur, 2026-07-18)

- Retirer `'DAF'` des **6 endpoints d'administration** du référentiel (détail, liste/recherche,
  export, documents, contrats, communications).
- **GARDER** `GET /suppliers/{id}/performance` accessible au DAF : c'est de l'**analytics financier**
  (Module 11), légitimement du ressort du DAF, pas de l'administration du référentiel.

## 3. Changements — retirer `'DAF'` de 6 `@PreAuthorize`

### `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java`

| Ligne (avant) | Endpoint | Avant | Après |
|---|---|---|---|
| 75 | `GET /suppliers/{id}` (détail) | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` |
| 81 | `GET /suppliers` (liste/recherche) | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` |
| 95 | `GET /suppliers/export` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` |
| 171 | `GET /suppliers/{id}/documents` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` |

### `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierRelationshipController.java`

| Ligne (avant) | Endpoint | Avant | Après |
|---|---|---|---|
| 32 | `GET /api/v1/suppliers/{supplierId}/contracts` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` |
| 57 | `GET /api/v1/suppliers/{supplierId}/communications` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')` | `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` |

> Note : `SupplierRelationshipController` a `@RequestMapping("/api/v1/suppliers/{supplierId}")`,
> donc contrats/comms sont sous `/{supplierId}/contracts` et `/{supplierId}/communications`
> (pas `/suppliers/contracts`). `SupplierController` a `@RequestMapping("/api/v1/suppliers")`.

## 4. Explicitement INCHANGÉ

- `GET /suppliers/{id}/performance` (`SupplierController` ~ligne 164, `hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')`)
  → **conservé** (analytics financier légitime pour le DAF).
- Toute l'**écriture** (create/update/activate/suspend/soft-delete/upload documents, POST contracts,
  POST communications) : déjà réservée `ADMIN` + `ASSISTANT_COMPTABLE`, le DAF ne l'a pas → rien à faire.
- **Frontend** : déjà conforme (4 pages `PageRoleGuard` AA-only + menu AA-only). **Aucune modification.**

## 5. Tests

Cible : `src/test/java/com/oct/invoicesystem/domain/supplier/controller/SupplierIntegrationTest.java`
(tests d'intégration MockMvc des deux contrôleurs ; le référentiel/contrats/comms y sont testés).

- Un utilisateur `ROLE_DAF` doit recevoir **403** sur les **6** endpoints retirés :
  `GET /api/v1/suppliers/{id}`, `GET /api/v1/suppliers`, `GET /api/v1/suppliers/export`,
  `GET /api/v1/suppliers/{id}/documents`, `GET /api/v1/suppliers/{supplierId}/contracts`,
  `GET /api/v1/suppliers/{supplierId}/communications`.
- Un utilisateur `ROLE_DAF` doit continuer à recevoir **200** sur `GET /suppliers/{id}/performance`.
- Vérifier que `ROLE_ASSISTANT_COMPTABLE` (et `ROLE_ADMIN` là où il était déjà autorisé) gardent
  **200** sur les endpoints modifiés (non-régression).
- Si des tests existants asseyaient un accès DAF 200 sur ces 6 endpoints, les **corriger** pour
  refléter la nouvelle règle (403).

## 6. Living-doc & vérification

- `docs/KNOWN_ISSUES_REGISTRY.md` : ajouter **PROB-123** (cause racine : `@PreAuthorize` backend
  désynchronisé du frontend / SoD ; solution : retrait de `'DAF'` sur 6 lectures ; règle préventive :
  toute surface référentiel fournisseur exclut le DAF côté back ET front — le DAF ne garde que
  l'analytics `/performance`). **Append via heredoc bash** (le fichier contient un octet NUL — jamais Edit).
- `docs/QA_AUDIT_EXHAUSTIF.md` : marquer **N7 ✅** (fichier de suivi, NON commité).
- **Gate backend** : `rm -rf target/surefire-reports && export DB_NAME=oct_invoice DB_USER=postgres
  DB_PASSWORD=dany && ./mvnw test` → baseline **620+/0/0/0**.
- **Vérif runtime SoD** : redéployer le backend, login `daf` (mdp `Test1234!`) via
  `POST /api/v1/auth/login` (rate-limit 5/min/IP → réutiliser le JWT), puis :
  - `GET /api/v1/suppliers` avec JWT DAF → attendu **403**.
  - `GET /api/v1/suppliers/{id}/performance` avec JWT DAF → attendu **200**.
- Branche `fix/n7-sod-supplier`, un sujet. **Aucun merge/push sans le feu vert explicite** de l'utilisateur.

## 7. Hors périmètre

- N8 (câblage UI), N10 (assignation N2), N23 (i18n Bean-Validation) : findings distincts, non traités ici.
- N13 : déjà corrigé + marqué ✅ (vérifié 2026-07-18).
