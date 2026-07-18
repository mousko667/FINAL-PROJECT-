# Spec — Correctif N5 + N6 : routage des notifications rejet / BAP

**Date :** 2026-07-18
**Branche :** `fix/notif-n5-n6`
**Findings :** N5, N6 (`docs/QA_AUDIT_EXHAUSTIF.md`)
**PROB :** PROB-119 (N5), PROB-120 (N6)

## 1. Problème (état réel du code, confirmé)

Deux canaux de notification traitent le rejet et le Bon à Payer :
`EmailNotificationListener` (email) et `PersistNotificationListener` (in-app persistée).

- **N5 — mauvais destinataire interne.** `onInvoiceRejected` et `onBonAPayer` ciblent
  `invoice.getSubmittedBy()` en supposant que c'est l'Assistant Comptable. Pour une facture
  soumise via le **portail fournisseur**, `submittedBy = le compte fournisseur`
  (`SupplierPortalController.toInvoice` fait `.submittedBy(actor)` avec `actor.id = user.getId()`
  du compte fournisseur connecté). Conséquence sur rejet d'une facture portail : le fournisseur
  reçoit le mail interne « submitter » (mauvais template) et **aucun ASSISTANT_COMPTABLE** n'est
  prévenu de reprendre le dossier.

- **N6 — fournisseur non notifié sur BAP.** `onBonAPayer` (email ET in-app) ne fait **aucun**
  `notifySupplier(...)`, contrairement à `onInvoiceRejected` et `onInvoicePayed`. Le fournisseur
  n'est jamais informé de l'approbation de sa facture. Viole la matrice §7 (« Invoice approved
  (BON_A_PAYER) → Supplier »).

Aucune migration, aucun changement de state-machine.

## 2. Comportement cible

### Règle de résolution du destinataire « Assistant Comptable » (commune aux deux listeners)

```
resolveAccountingRecipients(invoice):
    submitter = invoice.getSubmittedBy()
    si submitter != null ET submitter porte ROLE_ASSISTANT_COMPTABLE:
        return [submitter]                       # lui seul (il suit son dossier)
    sinon:                                        # facture portail : submitter = fournisseur
        return userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")
```

Le test « porte ROLE_ASSISTANT_COMPTABLE » réutilise le parcours de rôles déjà présent sur `User`
(via `getAuthorities()` / `userRoles` → `getRole().getName()`).

### Matrice des notifications après correctif

| Événement | Interne (AA résolus) | Fournisseur |
|---|---|---|
| **Rejet** | email `invoice-rejected` + in-app | email `supplier-invoice-rejected` (existe) + in-app (**nouveau**) |
| **BAP** | email `invoice-approved` + in-app | email `supplier-invoice-approved` (**nouveau template**) + in-app (**nouveau**) |

Le versant fournisseur in-app reprend exactement le pattern `onInvoicePayed`
(`findActiveUsersBySupplierId`, canal in-app).

## 3. Changements par fichier

### `EmailNotificationListener.java`
- Ajouter un helper privé `resolveAccountingRecipients(Invoice)` (règle §2).
- `onInvoiceRejected` : remplacer la cible `submittedBy` par les **AA résolus** (template
  `invoice-rejected`). Mail fournisseur `supplier-invoice-rejected` : **inchangé**.
- `onBonAPayer` : cibler les **AA résolus** (template `invoice-approved`) + **ajouter**
  `notifySupplier(invoice, sujet, "supplier-invoice-approved", vars)`.

### `PersistNotificationListener.java`
- Ajouter le même helper `resolveAccountingRecipients(Invoice)`.
- `onInvoiceRejected` : notifier les **AA résolus** (au lieu de `submittedBy` seul) +
  **ajouter** la notif in-app fournisseur (via `findActiveUsersBySupplierId`,
  type `NotificationType.REJECTION`).
- `onBonAPayer` : notifier les **AA résolus** + **ajouter** la notif in-app fournisseur
  (type `NotificationType.APPROVAL`).

### `templates/email/supplier-invoice-approved.html` (nouveau)
Calqué sur `supplier-invoice-rejected.html` : bilingue FR/EN, header « OCT — Portail Fournisseurs »,
variables `${reference}`, `${amount}`, `${frontendUrl}`, bouton vers `/supplier/invoices`.
Message : « Votre facture a été approuvée pour paiement / Your invoice has been approved for payment ».

## 4. Tests (TDD — rouge avant vert)

Fichiers existants à étendre : `EmailNotificationListenerTest`, `PersistNotificationListenerTest`.

1. **Rejet, facture portail** (`submittedBy` = fournisseur, non-AA) → AA actifs notifiés
   (email + in-app), le fournisseur reçoit son mail + in-app dédiés ; aucun mail interne au
   fournisseur. *(N5)*
2. **Rejet, facture interne** (`submittedBy` = AA) → cet AA **seul** notifié en interne (pas les
   autres AA) + fournisseur.
3. **BAP, facture portail** → AA actifs notifiés en interne **+ fournisseur email + in-app**.
   *(N5 + N6)*
4. **BAP, facture interne** (`submittedBy` = AA) → cet AA seul + fournisseur.
5. **`resolveAccountingRecipients`** : retourne `[submitter]` si submitter est AA ; sinon la liste
   `findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")`.

Mocks déjà en place : `UserRepository`, `SupplierRepository`, `EmailService` / `NotificationRepository`.

**Gate :** `./mvnw test` depuis `target/surefire-reports` vide → baseline 614 + nouveaux tests,
0 failure / 0 error / 0 skipped.

## 5. Vérification runtime (MailHog)

Facture portail (compte `supplier`) → rejet puis BAP via le workflow (aa → contrôle AA →
validateur → daf). Dans MailHog (`http://localhost:8025`) :
- sur rejet : un mail aux AA + un mail fournisseur ;
- sur BAP : un mail aux AA + un mail fournisseur (nouveau template).

## 6. i18n

Sujets de mail et titres in-app restent bilingues **en dur** (chaînes FR/EN concaténées), dans le
style existant de ces listeners. **Aucune nouvelle clé** `messages_*.properties` → pas de
manipulation ISO-8859-1.

## 7. Living-doc

- `docs/KNOWN_ISSUES_REGISTRY.md` : PROB-119 (N5) + PROB-120 (N6) via heredoc bash (octet NUL).
- `docs/TASKS.md` : marquer N5 + N6 corrigés.

## 8. Hors périmètre

- Pas de refonte des templates existants (`invoice-rejected`, `invoice-approved`, autres
  `supplier-*`).
- Pas de changement de state-machine ni de migration.
- N7/N8/N4/N21/N10 : findings séparés, autres sessions.
