# Audit d'accessibilité WCAG 2.1 AA (R8)

**Date :** 2026-06-26
**Outil :** axe-core 4.10.2 (règles `wcag2a`, `wcag2aa`, `wcag21a`, `wcag21aa`)
**Méthode :** application réelle en cours d'exécution (frontend Vite `:3000` + backend Spring Boot `:8080`, profil `dev`, PostgreSQL hôte `5433/oct_invoice`), navigation pilotée par Playwright, scan axe injecté dans chaque page après chargement.
**Compte de test :** `aa` / `Test1234!` (ASSISTANT_COMPTABLE — pas de MFA, accès dashboard finance / factures / rapports / paiements).

## Résumé

| Page | Violations avant | Violations après | Règles concernées |
| --- | --- | --- | --- |
| Login (`/login`) | 0 | 0 | — |
| Vérification MFA (OTP) | 0 | 0 | — |
| Tableau de bord (`/dashboard`) | 1 (3 nœuds) | **0** | `color-contrast` |
| Liste des factures (`/invoices`) | 2 (3 nœuds) | **0** | `label` (critique), `color-contrast` |
| Rapports (`/reports`) | 2 (4 nœuds) | **0** | `label` (critique), `color-contrast` |
| Paiements (`/payments`) | 1 (1 nœud) | **0** | `color-contrast` |
| Profil (`/profile`) | 0 | 0 | — |

**Résultat final : 0 violation WCAG 2.1 AA sur l'ensemble des pages auditées.**

## Détail des problèmes corrigés

### 1. `label` — champs de formulaire sans étiquette accessible (critique, WCAG 1.3.1 / 4.1.2)
- **Liste des factures** : les filtres de date `#filter-from-date` et `#filter-to-date` (et le `<select>` de statut) n'avaient ni `<label>` associé ni `aria-label`.
  - *Correction :* ajout de `aria-label` via `MessageSource` (clés `invoice.filterFromDate`, `invoice.filterToDate`, `invoice.filterStatus`, FR + EN).
- **Rapports** : les deux `<input type="date">` avaient un `<label>` visuel non associé.
  - *Correction :* association explicite `label[htmlFor]` ↔ `input[id]` (`reports-from-date`, `reports-to-date`).

### 2. `color-contrast` — contraste de texte insuffisant (sérieux, WCAG 1.4.3, seuil 4.5:1)
- **Sidebar** (`text-slate-500` #64748b sur fond #0f2540, ratio 3.24) : labels de section et version `v1.0.0 · OCT`.
  - *Correction :* `text-slate-500` → `text-slate-400` (#94a3b8, ratio ≈ 4.7:1 sur le fond sombre).
- **Widget d'ancienneté (AgingBucketsWidget, R3)** état vide (`text-gray-400` #9ca3af sur blanc, ratio 2.53).
  - *Correction :* `text-gray-400` → `text-gray-500` (#6b7280, ratio ≈ 4.6:1).
- **Liste des factures** message d'erreur (`text-red-500` #ef4444 sur blanc, ratio 3.76).
  - *Correction :* `text-red-500` → `text-red-600` (#dc2626, ratio ≈ 4.8:1).
- **Rapports / Paiements** états vides et sous-titres (`text-gray-400`, ratio 2.53).
  - *Correction :* `text-gray-400` → `text-gray-500` ; libellé en dur extrait vers la clé `reports.dateRangeHint` (FR + EN).

## Non-vérifié dans cette passe

- **File d'approbations / validation N1-N2** : pages réservées aux rôles VALIDATEUR, pour lesquels la MFA (TOTP) est obligatoire (cf. CLAUDE.md §Phase 9). Le second facteur ne peut être franchi sans le secret TOTP du compte ; ces écrans n'ont donc pas été scannés en runtime. Ils réutilisent les mêmes composants de formulaire/table que les pages auditées (mêmes correctifs de label/contraste applicables). À auditer lors d'une passe ultérieure avec un compte validateur dont le secret TOTP est connu.

## Reproduction

1. Démarrer PostgreSQL hôte (`5433`), MinIO et MailHog, puis le backend (`SPRING_PROFILES_ACTIVE=dev mvnw spring-boot:run`) et le frontend (`cd frontend && npm run dev`).
2. Se connecter avec `aa` / `Test1234!`.
3. Sur chaque page, injecter axe-core et exécuter :
   `axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','wcag21a','wcag21aa'] } })`.
