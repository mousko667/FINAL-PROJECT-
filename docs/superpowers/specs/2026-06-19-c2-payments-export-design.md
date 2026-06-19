# C2 — Export dédié page Paiements (M7 #11)

**Date :** 2026-06-19
**Item :** Section C, famille C — C2
**Gap couvert :** M7 #11 « payment-specific export » (dernier gap M7 ouvert)
**Branche :** fix/a1-cashflow-sqlgrammar

---

## Objectif

Ajouter un export dédié (CSV / Excel / PDF) de l'historique des paiements sur la
page Paiements. Aujourd'hui la page n'offre **aucun** export ; la seule sortie
fichier est l'avis de versement par paiement (`/payments/{id}/remittance`).

## Décision de périmètre

**Option retenue : historique des paiements seul.**

- C'est exactement le « payment-specific export » de M7 #11.
- Pas d'export des factures en attente (doublon avec `/invoices/export?status=BON_A_PAYER`).
- Pas de nouveaux filtres date/méthode sur la page (hors scope, autre tâche).
- Le filtre `departmentCode` existant côté backend est respecté gratuitement.

## Architecture

Reproduction du pattern d'export standard du projet, déjà appliqué à `users`,
`invoices`, `suppliers`, `audit-logs` :

- endpoint backend `GET /<resource>/export?format=csv|excel|pdf`
- service partagé `TabularExportService.export(fmt, title, headers, rows)`
- composant frontend `<ExportMenu endpoint=... filename=... />`

Aucun nouveau mécanisme introduit.

## Backend

### `PaymentService` / `PaymentServiceImpl`

Nouvelle méthode :

```java
byte[] exportPayments(String departmentCode, TabularExportService.Format format);
```

- Récupère **tous** les paiements (pas de pagination — cf. `UserController` qui
  prend `size 10000`), en respectant le filtre `departmentCode` optionnel (même
  logique de branchement que `listPayments`).
- Construit les lignes depuis l'entité `Payment` (accès à `getInvoice()` →
  référence facture, fournisseur), ce qui permet un export plus riche que le
  `PaymentDTO` (qui ne porte ni la référence facture ni le fournisseur).
- Délègue le rendu à `tabularExportService.export(fmt, "Payments", headers, rows)`.

Pour récupérer toutes les lignes : ajouter au `PaymentRepository` une variante
non paginée du filtre département (ou réutiliser `findAll()` /
`findByInvoiceDepartmentCode` avec `Pageable.unpaged()`).

### Colonnes exportées (validées)

| # | En-tête             | Source                                       |
|---|---------------------|----------------------------------------------|
| 1 | Référence facture   | `payment.getInvoice().getReferenceNumber()`  |
| 2 | Fournisseur         | `payment.getInvoice().getSupplierName()`     |
| 3 | Mode de paiement    | `payment.getPaymentMethod()`                 |
| 4 | Référence paiement  | `payment.getReference()`                     |
| 5 | Montant payé        | `payment.getAmountPaid()`                    |
| 6 | Devise              | `payment.getInvoice().getCurrency()`         |
| 7 | Date de paiement    | `payment.getPaymentDate()`                   |
| 8 | Enregistré par      | `payment.getRecordedBy()` (username)         |

Valeurs nulles → chaîne vide (helper `nz` comme `UserController`).

### `PaymentController`

```java
@GetMapping("/export")
@PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")
ResponseEntity<byte[]> exportPayments(
        @RequestParam(required = false) String departmentCode,
        @RequestParam(defaultValue = "csv") String format)
```

- `Content-Disposition: attachment; filename=payments.<ext>` + `Content-Type`
  via le helper `fileResponse` identique à `UserController`.
- Rôles : alignés sur `listPayments` (ASSISTANT_COMPTABLE, DAF, ADMIN). ADMIN y
  voit la même donnée que la liste paiements déjà accessible — ce n'est pas un
  accès « rapports/financier » au sens de la règle de séparation des pouvoirs.

## Frontend

Dans `PaymentsPage.tsx`, ajouter
`<ExportMenu endpoint="/payments/export" filename="payments" />` dans l'en-tête
de la carte « Historique des paiements » (à droite, près du compteur
`totalElements`). Aucun nouveau composant ; `ExportMenu` gère déjà le
téléchargement blob authentifié et propose CSV/Excel/PDF.

## i18n

Le bouton réutilise `app.export` (déjà présent FR + EN). Aucune nouvelle clé a
priori. Si une clé est ajoutée : double ajout FR (`messages_fr.properties` en
ISO-8859-1 via `iconv`, sans em-dash/guillemets courbes) + EN.

## Tests (TDD RED→GREEN)

### Backend
- Intégration `PaymentControllerIntegrationTest` :
  - `GET /payments/export?format=csv` → 200, `Content-Type text/csv`, en-tête CSV
    attendu, une ligne par paiement.
  - filtre `departmentCode` respecté.
  - rôle interdit (ex. ROLE_SUPPLIER) → 403.
- Unitaire service : construction correcte des lignes (colonnes, ordre, nulls).

### Frontend
- `ExportMenu` a déjà ses tests. Vérifier que `PaymentsPage` rend le menu.
- Garde-fous : `npx tsc --noEmit` + `npm run build` (vite) + `vitest` de la page
  si un fichier de test existe (cf. PROB-064 : lancer vitest, ne pas se fier à
  tsc/build).

## Hors scope (YAGNI)
- Filtres date/méthode sur la page (option 3).
- Export des factures en attente (option 2 — déjà couvert par l'export factures).

## Définition de terminé
- Backend + front verts (suite back, tsc, vite build, vitest concerné).
- Endpoint conforme au contrat d'export standard du projet.
- M7 #11 basculé ✅ dans `COMPLIANCE_MATRIX.md`.
- Bug éventuel logué dans `KNOWN_ISSUES_REGISTRY.md` avant commit.
- Commit (1 commit = 1 sujet) + bloc de reprise.
