# M8 #10 - Assistant d'onboarding fournisseur multi-etapes

**Date :** 2026-06-28  
**Branche :** chore/sanitize-docs-migrations  
**Module :** M8 #10 (Supplier onboarding workflow)  
**TASKS.md :** ligne 347

## Contexte verifie dans le code

Le domaine fournisseur existe deja et couvre le cycle de statut attendu :
`SupplierStatus` propose `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED` ;
`Supplier` porte deja `onboardedBy` et `onboardedAt` ; `SupplierServiceImpl`
cree les fournisseurs en `PENDING_VERIFICATION`, puis expose `activateSupplier`
et `suspendSupplier`.

Le front admin existe aussi, mais il est encore "formulaire + detail" :
`/admin/suppliers/new` et `/admin/suppliers/:id/edit` utilisent un formulaire
classique, et la page detail affiche seulement les actions activable/suspend.
Il n'existe pas de wizard, stepper ou parcours guide pour l'onboarding.

## Decision de cadrage

1. L'assistant sera un parcours admin multi-etapes, pas une nouvelle logique
   de création separée du domaine existant.
2. Le wizard orchestrera les donnees deja supportees par l'API actuelle :
   identite, contact, adresse, categorie, banque et documents.
3. Une garde backend legere sera ajoutee avant activation : un fournisseur ne
   pourra pas passer a `ACTIVE` tant que le dossier minimal d'onboarding n'est
   pas complet.
4. On conserve le workflow de statut existant et les endpoints actuels ;
   pas de nouveau modele de domaine, pas de nouvelle table.

## Architecture

### Frontend

Nouveau parcours admin dedie, base sur un stepper en 3 etapes :

1. Identite fournisseur
2. Coordonnees et paiement
3. Verification finale et documents

Le wizard reutilise les hooks API existants pour creer et mettre a jour un
fournisseur, puis affiche un resume d'onboarding avec les actions de suivi.
L'UI reste dans le style des pages admin existantes, sans ecran marketing.

### Backend

`SupplierServiceImpl.activateSupplier` verifiera que le fournisseur a bien :

- une fiche non supprimee,
- des documents d'onboarding minimaux presentes,
- des champs d'identite et de contact non vides.

En cas d'incomplet, le service levera une `ValidationException` i18nisee.
La suspension reste inchangee.

### i18n

Ajouter les cles FR/EN pour :

- titre et etapes du wizard,
- actions de navigation,
- etat "onboarding incomplet",
- message de blocage a l'activation.

## Tests

### Backend

- `SupplierServiceTest` : activation happy path, dossier incomplet, fournisseur
  introuvable.
- `SupplierControllerTest` ou integration test equivalent : activation 200
  pour ADMIN/ASSISTANT_COMPTABLE, 403 pour role interdit.

### Frontend

- test du wizard : navigation entre etapes, resume final, create/save appele
  avec la bonne charge utile.
- test de la page detail : etat "onboarding incomplet" visible, bouton
  d'activation desactive ou message de blocage selon le retour API.

## Hors-scope

- OCR ou extraction automatique de documents fournisseur.
- Portail self-service fournisseur pour l'onboarding.
- Nouvelle table de progression d'onboarding.
- Workflow d'approbation fournisseur multi-acteurs.
