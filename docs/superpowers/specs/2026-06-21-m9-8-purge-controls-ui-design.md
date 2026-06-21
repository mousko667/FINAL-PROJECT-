# M9 #8 — Contrôles de purge en UI (ADMIN) — Design

**Date:** 2026-06-21
**Branche:** fix/a1-cashflow-sqlgrammar
**Item ciblé:** COMPLIANCE_MATRIX.md ligne 298 (M9 UI #8 « Archive and purge controls ») — verdict 🟠 → ✅
**Lot:** E

## 1. Vue d'ensemble & périmètre

Nouvelle page admin **purement frontend** exposant les deux endpoints de disposition
déjà livrés (lot « retention disposition », PENDING/RETAINED/PURGED). Elle permet à un
ADMIN de voir les documents de facture ayant dépassé l'horizon de rétention et encore
en `PENDING`, et de leur appliquer une disposition : **Conserver** (`RETAINED`) ou
**Purger** (`PURGED`).

`PURGED` reste un **marquage de conformité** — aucune suppression physique du fichier
MinIO (purge volontairement non destructive, fidèle au design existant du backend).

**Contraintes de périmètre :**
- **Zéro nouveau backend** : les endpoints, DTO, audit et clés i18n backend existent déjà.
- **Zéro Flyway** : V64 non consommée par cette tâche.
- Ferme la 🟠 ligne 298 de `COMPLIANCE_MATRIX.md`.

### Backend existant réutilisé (ne pas modifier)

- `GET /api/v1/retention/pending-documents` — `@PreAuthorize hasRole('ADMIN')`
  → `ApiResponse<List<RetentionPendingDocumentDTO>>`
- `PUT /api/v1/retention/documents/{id}/disposition` — `@PreAuthorize hasRole('ADMIN')`
  → body `{ disposition: 'RETAINED' | 'PURGED' }`, renvoie le DTO mis à jour,
  message i18n `retention.disposition.updated`. Cible `PENDING` rejetée
  (`retention.disposition.invalid_target`, 400).

`RetentionPendingDocumentDTO` : `{ id: UUID, invoiceId: UUID, originalFilename: string,
uploadedAt: Instant, retentionDisposition: RetentionDisposition }`.

Le listing ne renvoie **que** les documents périmés encore `PENDING` ; appliquer une
disposition les retire donc de la liste au prochain re-fetch.

## 2. Composants

- **`frontend/src/pages/admin/AdminRetentionDispositionPage.tsx`** — la page :
  titre + sous-titre + note non-destructif, puis tableau ou état vide.
- **Route** `/admin/retention-disposition` (lazy, comme les pages sœurs),
  sous `PageRoleGuard allowedRoles={['ROLE_ADMIN']}`.
- **NavItem** sidebar section ADMIN, icône lucide (`Trash2`), libellé i18n.
- **Modale de confirmation** pour Purger uniquement (réutilise un composant modale
  existant s'il y en a un dans le projet ; sinon modale locale légère).

Pattern de référence : `AdminRetentionPolicyPage.tsx` et `AdminArchiveCompliancePage.tsx`
(PageRoleGuard, react-query, i18n avec fallback FR, `apiClient` sans double-préfixe,
icônes lucide).

## 3. Flux de données

- `useQuery(['retention-pending-documents'])` → `GET /retention/pending-documents`
  → liste de `RetentionPendingDocumentDTO`.
- Action ligne → `useMutation` → `PUT /retention/documents/{id}/disposition`
  avec `{ disposition: 'RETAINED' | 'PURGED' }` → `onSuccess` invalide la query
  `['retention-pending-documents']` (la ligne disparaît, le backend ne liste que les PENDING).
- `apiClient` : baseURL déjà `/api/v1`, **pas de double-préfixe** (cf. PROB-038).

## 4. UI & états

- **Chargement** : spinner centré (`Loader2 animate-spin`), comme les pages sœurs.
- **État vide (cas nominal)** : encart rassurant — icône `CheckCircle` (vert/neutre) +
  message « Aucun document ne dépasse la durée de rétention. Rien à traiter. ».
- **Tableau** (docs périmés présents) — colonnes :
  - **Fichier** : `originalFilename`
  - **Facture** : lien vers `/invoices/{invoiceId}` si `invoiceId` présent, sinon « — »
  - **Uploadé le** : `uploadedAt` via `toLocaleString(i18n.language)`
  - **Actions** : `[Conserver]` (neutre) + `[Purger]` (rouge/destructif visuellement)
- **Conserver** → mutation directe `RETAINED` + feedback succès.
- **Purger** → ouvre la modale de confirmation (rappel marquage non destructif) ;
  `[Annuler]` / `[Confirmer]`. Confirmer → mutation `PURGED`.
- Boutons d'une ligne désactivés (`disabled` + spinner) pendant sa mutation en cours.

## 5. Gestion des erreurs

- Erreur de chargement liste → encart d'erreur rouge avec message i18n.
- Erreur de mutation → message i18n passé par `t(key)` (le backend peut renvoyer une
  clé i18n comme message — cf. PROB-006).
- L'UI n'expose jamais `PENDING` comme cible → le garde-fou backend
  `invalid_target` ne devrait pas être atteint.
- Accès non-ADMIN → `PageRoleGuard` affiche l'UI d'erreur d'accès
  (défense en profondeur ; backend renvoie déjà 403).

## 6. i18n (FR + EN, parité stricte)

Nouveau bloc `retentionDisposition` dans `fr.json` et `en.json` (frontend) :

`title`, `subtitle`, `note`, `colFile`, `colInvoice`, `colUploaded`, `colActions`,
`retain`, `purge`, `empty`, `confirmPurgeTitle`, `confirmPurgeBody`, `confirm`,
`cancel`, `loadError`, `actionError`, `retained`, `purged`, et le libellé sidebar.

- Parité stricte des clés FR/EN (vérification programmatique).
- Accents FR vérifiés.
- **Pas de modification de `messages_fr.properties`/`messages_en.properties` backend** :
  la tâche est frontend ; les clés backend (`retention.disposition.*`) existent déjà.

## 7. Tests (vitest)

`frontend/src/test/pages/AdminRetentionDispositionPage.test.tsx`, pattern des pages
sœurs (QueryClientProvider + mock `apiClient` + fixtures plates) :

1. **Liste peuplée** → rend les lignes (fichier + actions).
2. **État vide** → rend le message « rien à traiter », pas de tableau.
3. **Conserver** → `PUT` avec `disposition: 'RETAINED'`, query invalidée.
4. **Purger** → ouvre la modale ; Confirmer envoie `PUT disposition: 'PURGED'` ;
   assert que sans confirmer, aucun `PUT` n'est émis.
5. **Accès refusé** (non-ADMIN) → rendu négatif via `PageRoleGuard`.

## 8. Critère de fin

- `tsc --noEmit` : 0 erreur.
- `vitest` : tout vert (54 actuels + nouveaux tests).
- Backend inchangé : `mvnw.cmd test` toujours 464/0/0.
- `COMPLIANCE_MATRIX.md` L298 🟠 → ✅ + ligne Gaps M9 + synthèse M9 mises à jour.
- Aucun nouveau PROB attendu (pattern suivi). Si un bug réel survient → KNOWN_ISSUES_REGISTRY.

## Hors périmètre (YAGNI)

- Historique des dispositions (RETAINED/PURGED passés) → nécessiterait un endpoint backend.
- Purge en lot (sélection multiple) → nécessiterait un endpoint bulk + risque SoD/ergonomie.
- Suppression physique MinIO → contraire au design non destructif.
- Arborescence de dossiers (M9 UI #1) → item 🟠 distinct, autre tâche.
