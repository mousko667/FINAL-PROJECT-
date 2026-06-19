# C1 — Motifs de rejet prédéfinis (M4 #8)

> Lot section C (familles B+C). Item C1. Date : 2026-06-19.
> Référence COMPLIANCE_MATRIX : M4 UI #8 « Rejection reason selection » (était 🟠 → cible ✅).

## Intention

Le rejet d'une facture impose aujourd'hui un **texte libre** (`rejectionReason`, `@NotBlank` + `@Size min 10`).
L'exigence #8 demande une **sélection de motif prédéfini**. On ajoute donc :

- un **dropdown de motifs prédéfinis OBLIGATOIRE** ;
- un **détail libre OPTIONNEL** (sauf motif `AUTRE` → détail obligatoire).

Sans casser l'existant ni migrer le schéma.

## Approche retenue — encoder `[CODE] détail` dans le `rejectionReason` existant

On **ne crée pas** de colonne `reason_code`. Le backend compose la chaîne stockée :

- Avec détail : `"[MONTANT_INCORRECT] le HT ne correspond pas au BDC"`.
- Sans détail : libellé/code seul → `"[DOUBLON]"`.
- Rétrocompatible : les anciens rejets en texte libre restent lisibles tels quels.

**Pourquoi pas une nouvelle colonne typée :** zéro migration Flyway, zéro impact sur
`ApprovalStep` / l'historique / les exports qui lisent déjà `rejectionReason`. Niveau adapté
à un item « famille C, effort faible ». (Une vraie colonne `reason_code` reste une évolution
possible si on veut des statistiques par motif — hors périmètre C1.)

## Backend

1. **Enum** `RejectionReasonCode` (package `domain.workflow.model` ou `dto`) :
   `MONTANT_INCORRECT`, `PIECE_MANQUANTE`, `DOUBLON`, `INFOS_FOURNISSEUR_INCORRECTES`,
   `HORS_BUDGET`, `AUTRE`.
2. **`RejectRequest`** :
   - ajout `@NotNull RejectionReasonCode reasonCode` ;
   - `rejectionReason` devient le **détail optionnel** : on retire `@NotBlank`/`@Size min 10` ;
   - contrainte « détail ≥ 10 car. obligatoire » conservée **uniquement si** `reasonCode == AUTRE`,
     vérifiée **dans le controller** avant composition (lève `ValidationException` → 400 via `GlobalExceptionHandler`).
3. **Controller `/{invoiceId}/workflow/reject`** : compose la chaîne `"[CODE] détail"`
   (ou `"[CODE]"` si pas de détail) et appelle `approvalService.reject(invoiceId, composed)`
   — **service, guard et historique inchangés**.
4. **Endpoint lecture** `GET /workflow/rejection-reasons` (authentifié) :
   renvoie `List<{ code, label }>` traduit via `MessageSource` selon `Accept-Language`,
   pour peupler le dropdown front sans dupliquer l'enum côté React.

## Frontend

- `InvoiceActionPanel` (modale de rejet) :
  - un `<select>` de motifs (chargés via `GET /workflow/rejection-reasons`) **au-dessus** du textarea ;
  - le textarea devient « Détail (optionnel) » — **obligatoire (≥10 car.) si motif = `AUTRE`** ;
  - bouton « Confirmer » désactivé tant qu'aucun motif n'est choisi (et tant que détail manquant si `AUTRE`) ;
  - payload envoyé : `{ reasonCode, rejectionReason }`.

## i18n (parité FR/EN obligatoire)

- Libellés motifs : `invoice.rejectReason.code.MONTANT_INCORRECT` … `.AUTRE` (FR + EN).
- Erreurs : `error.rejection.code.required` ; ajustement du message « détail requis si AUTRE ».

## Tests (TDD RED → GREEN)

**Backend**
- `reject` avec `reasonCode` valide + détail → chaîne `"[CODE] détail"` persistée sur l'`ApprovalStep`.
- `reasonCode == AUTRE` sans détail (ou < 10 car.) → 400.
- `reasonCode` null → 400.
- `GET /workflow/rejection-reasons` → liste non vide, libellés traduits (FR par défaut, EN via header).

**Frontend**
- la modale affiche le `<select>` peuplé ;
- « Confirmer » désactivé sans motif choisi ;
- motif `AUTRE` → détail devient requis.

## Hors périmètre (YAGNI)

- Table configurable + UI admin des motifs (surdimensionné — proche famille B).
- Colonne `reason_code` typée + statistiques par motif.
- Modification du guard `RejectionReasonGuard` (la chaîne composée fait toujours ≥ 10 car. via le préfixe `[CODE]`).
