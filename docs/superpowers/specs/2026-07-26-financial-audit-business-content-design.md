# Journal d'audit financier — contenu métier lisible (Lot 9, extension)

**Date :** 2026-07-26
**Branche :** `feat/financial-audit-readability`
**Statut :** design validé, à implémenter

## Contexte et problème

La page `/financial-audit` (réservée au DAF) affiche des lignes dérivées uniquement du
filtre HTTP `AuditLoggingFilter` :

- **Acteur** = `#57858018` (fragment d'UUID technique) + IP Docker.
- **Entité** = `INVOICE#/api/v1/` (bout d'URL tronqué).
- **Détails** = `{"duration_ms":83,"method":"POST","status":200}` (télémétrie HTTP brute).

Aucun montant, aucun n° de facture, aucun fournisseur : ce n'est pas un journal financier
exploitable. Le premier passage du Lot 9 (granularité `classifyAction`, exclusion serveur
`excludeAction`, filtre par type + masquage du bruit `ACCESS_DENIED`) a amélioré le VOLUME
et la navigation, mais pas le CONTENU des lignes. Ce document conçoit l'enrichissement du
contenu métier.

Décision utilisateur = **Approche A** : journaliser de vrais événements financiers depuis
le point central du workflow, et enrichir le DTO/affichage pour montrer acteur lisible,
n° de facture, montant XAF + fournisseur.

## Objectifs

Pour chaque nouvel événement financier, la ligne du journal doit afficher :

1. **Acteur** : `NOM Prénom` + rôle (ex. « Ndong Marie (DAF) »), + IP conservée.
2. **Événement** : libellé métier traduit (« Bon à payer », « Soumission », « Paiement »…).
3. **Entité** : n° de facture lisible (`FAC-2026-0042`) au lieu de l'URL.
4. **Détails** : montant + devise + fournisseur (« 850 000 XAF · SOGARA »).
5. **Date** : conservée (déjà lisible).

## Non-objectifs

- Pas de migration ni de rétro-remplissage des ~1802 lignes HTTP existantes : elles restent
  telles quelles (le front les rend proprement mais sans montant, la donnée n'existe pas).
- Pas de changement d'autorisation / SoD : `/audit-logs/financial` reste **DAF uniquement** ;
  `ROLE_ADMIN` n'a **aucun** accès financier (règle projet « on ferme, jamais on n'ouvre »).
- Pas de refonte de l'export CSV/PDF (conserve ses colonnes actuelles).
- Pas d'instrumentation hors du cycle de vie de la facture.
- On conserve le travail déjà fait au 1er passage (granularité, `excludeAction`, filtre + masquage).

## Architecture

### 1. Journalisation métier centralisée (backend)

**Fichier :** `InvoiceStateMachineServiceImpl.sendEvent(...)`, après la transition réussie
(à côté de `publishNotificationEvent`, ligne ~137).

Tous les événements financiers passent par ce point unique : `SUBMIT`, `RESUBMIT`,
`VALIDATE_N1`, `VALIDATE_N2`, `BON_A_PAYER`, `REJECT`, `RECORD_PAYMENT`, `ARCHIVE`
(le paiement passe par `sendEvent(RECORD_PAYMENT)` — vérifié dans `PaymentServiceImpl:142`).

Après transition acceptée, appeler :

```
auditService.logAction(
    actorId,                       // WorkflowExtendedStateKeys.USER_ID, sinon SecurityContext
    "INVOICE",                     // entityType
    invoice.getReferenceNumber(),  // entityId = n° facture lisible
    businessAction,                // dérivé de l'InvoiceEvent (mapping ci-dessous)
    null,                          // oldValue
    financialDetails,              // newValue = {amount, currency, supplier}
    null, null);                   // ip / userAgent : non disponibles hors requête HTTP ici
```

**Mapping InvoiceEvent → action** (toutes déjà dans `FINANCIAL_ACTIONS`) :

| InvoiceEvent            | action métier    |
|-------------------------|------------------|
| SUBMIT, RESUBMIT        | INVOICE_SUBMIT   |
| VALIDATE_N1, VALIDATE_N2| APPROVE          |
| BON_A_PAYER             | BON_A_PAYER      |
| REJECT                  | REJECT           |
| RECORD_PAYMENT          | PAYMENT          |
| ARCHIVE                 | ARCHIVE          |
| (autre)                 | *(non journalisé)* |

**`financialDetails`** = objet sérialisé par l'`ObjectMapper` déjà injecté dans
`AuditService.logAction`, forme :

```json
{ "amount": 850000, "currency": "XAF", "supplier": "SOGARA" }
```

- `amount` = `invoice.getAmount()` (BigDecimal) ; `currency` = `invoice.getCurrency()` (XAF par défaut).
- `supplier` = `invoice.getSupplier().getName()` avec repli sur `invoice.getSupplierName()`
  (champ plat legacy) si le FK est nul.

**Garanties :** `logAction` est `@Async @Transactional` — un échec de journalisation ne casse
jamais la transition (déjà en place). Aucun schéma modifié (réutilise `action`, `entity_id`,
`new_value`). Un event non mappé n'écrit rien (pas de bruit).

### 2. DTO enrichi + résolution de l'acteur (backend)

**`AuditLogDTO`** — ajouter deux champs :

```
String userDisplayName,  // "NOM Prénom", repli username, null si pas d'acteur
String userRole          // rôle principal, ex. "DAF"
```

**`AuditServiceImpl.toDTO(...)`** — renseigner depuis `log.getUser()` :

- `userDisplayName` : `lastName + " " + firstName` si présents ; sinon `username` ; sinon `null`.
- `userRole` : le **rôle principal** de l'utilisateur, formaté court (sans préfixe `ROLE_`).

**Rétrocompatibilité :** on AJOUTE des champs, on n'en retire aucun. Les 4 endpoints qui
renvoient ce DTO (system, financial, legacy, export) continuent de fonctionner. L'export
CSV/PDF n'utilise pas les nouveaux champs → aucun impact.

**SoD :** `userDisplayName`/`userRole` sont exposés dans les deux journaux, mais chaque rôle
ne voit que son propre journal via les allow-lists existantes (inchangé).

**Perf :** le mapping est déjà `@Transactional(readOnly=true)` ; page = 20 lignes ; le
lazy-loading éventuel de `getUser()` par ligne est acceptable et noté (pas de sur-ingénierie).

### 3. Affichage frontend

**`FinancialAuditPage.tsx`** — interface `AuditLog` : ajouter `userDisplayName?`, `userRole?`.

Rendu robuste, distinguant ligne **métier** (nouvelle) vs **HTTP** (ancienne) par le CONTENU :

| Colonne | Ligne métier (nouvelle) | Ligne HTTP (ancienne, repli) |
|---|---|---|
| Date | `formatDateTime` (inchangé) | idem |
| Acteur | `userDisplayName` + `userRole` + IP ; repli `#uuid` ; « Système » si nul | `#uuid` + IP |
| Action | libellé `audit.financial.event.*` | idem (déjà traduit) |
| Entité | `Facture {referenceNumber}` si `entityId` = n° réf | affichage actuel (URL) |
| Détails | `{amount} {currency} · {supplier}` depuis `newValue` JSON | `méthode · statut` (au lieu du JSON brut) |

**Détection du type de ligne :**
- `newValue` parse en `{amount,currency,supplier}` → rendu financier (montant via `Intl`/helper
  `formatMoney` déjà utilisé, format XAF).
- sinon (`details`/`newValue` = `{duration_ms,method,status}`) → repli `méthode · statut`.
- entité : commence par une lettre (n° réf) → « Facture … » ; commence par `/api` → URL.

**Helpers purs testables** (extraits pour test unitaire, pas de test de page lourd) :
`formatAuditActor(log)`, `formatAuditEntity(log, t)`, `formatAuditDetails(log, t, formatMoney)`.

**i18n :** libellés d'événements déjà présents (1er passage). Ajouter au besoin `audit.financial.entity.invoice`
(« Facture {{ref}} » / « Invoice {{ref}} ») avec parité FR/EN stricte, sans collision objet/string.

## Tests (TDD, un test par changement)

**Backend :**
1. `InvoiceStateMachineServiceImpl` : `sendEvent` appelle `logAction` avec la bonne action,
   `entityId = referenceNumber`, `newValue` contenant montant+devise+fournisseur. Cas :
   au moins `SUBMIT→INVOICE_SUBMIT` et `BON_A_PAYER→BON_A_PAYER`, + un event non financier
   qui ne journalise pas. Mock `AuditService` + ArgumentCaptor.
2. `AuditServiceImpl.toDTO` : `userDisplayName` = « NOM Prénom », `userRole` renseigné, replis
   (username / null). Unitaire (transformation pure).

**Frontend :**
3. Helpers purs `formatAuditActor/Entity/Details` : ligne métier → « 850 000 XAF · SOGARA » +
   « Ndong Marie » + « Facture FAC-2026-0042 » ; ligne HTTP → repli sans planter.

**Gate :** backend `./mvnw.cmd test` (suites séparées, ≥ 752/0/0 + nouveaux tests) ; frontend
`tsc` (0) + `build` + `vitest` (311+/…) ; parité i18n FR/EN vérifiée par script node.

## Risques / vigilance

- **N+1** sur `log.getUser()` : acceptable (page 20), noté.
- **Contexte de test pollué** (leçon 1er passage) : NE PAS déclarer de bean `ObjectMapper`
  dans une `@TestConfiguration` partagée ; construire les services de test à la main.
- **Encodage i18n FR** : parité stricte, pas de collision objet/string, vérif script node avant commit.
- **Déploiement Docker** : `docker cp` + restart/reload explicites, puis vérif runtime en
  déclenchant un vrai événement (soumission ou BAP) pour voir une ligne métier lisible.

## Critère de succès

Connecté en `daf` sur `/financial-audit`, après avoir déclenché une action financière, la
ligne correspondante affiche **nom d'acteur + rôle, n° de facture, montant XAF + fournisseur** —
plus aucune trace HTTP technique sur les événements métier.
