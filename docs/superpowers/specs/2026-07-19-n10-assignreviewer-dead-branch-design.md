# N10 — Nettoyage de la branche morte `cannot_assign_n2_in_n1` dans assignReviewer

**Date :** 2026-07-19
**Finding audit :** N10 (🟡 Workflow)
**Branche :** `fix/n10-assignreviewer-dead-branch`
**PROB :** PROB-129

## Investigation (systematic-debugging, cause racine)

Le finding N10 affirmait : « la branche N1→N2 des départements 2-niveaux serait inatteignable via
`assignReviewer` ». **Cette prémisse est erronée.** L'investigation a établi :

1. **Le passage N1→N2 ne transite PAS par `assignReviewer`.** Il se fait via `validateN1()`
   (`ApprovalServiceImpl:90`) → state-machine. La transition `EN_VALIDATION_N1 → EN_VALIDATION_N2`
   sur l'événement `VALIDATE_N1` avec la garde `departmentTransitionGuard.requiresN2(ctx)` existe
   (`StateMachineConfig.java:77-80`) et est **testée et passante** :
   `StateMachineTransitionExhaustiveTest` T3 (ligne 215) + cycle de vie complet
   `ApprovalControllerTest.p3_17_twoLevelLifecycle_InfoDepartment` (ligne 154).

2. **La branche `EN_VALIDATION_N2` de `assignReviewer` (`:58-60`, self-assign N2) est VIVANTE et
   testée** : `p3_17` fait un `POST /workflow/assign` avec le validateur N2 en état
   `EN_VALIDATION_N2` et attend 200 (« N2 self-assigns », ligne 176). **À conserver.**

3. **Seule la branche `EN_VALIDATION_N1 && requiresN2` (`:56-57`) est du code mort :**
   - Jamais atteinte par l'UI : le frontend (`InvoiceActionPanel.tsx`) n'appelle `ASSIGN_REVIEWER`
     que depuis l'état `EN_CONTROLE_AA` (lignes 107-112) ; en `EN_VALIDATION_N1` il propose
     `VALIDATE_N1`, pas `ASSIGN_REVIEWER`.
   - Aucun test ne l'exerce ni n'assert son message `error.approval.cannot_assign_n2_in_n1`
     (grep = 0 dans `src/test`).
   - Le message est conceptuellement trompeur (« Impossible d'assigner N2 tant que N1 n'est pas
     terminée ») : il ne correspond à aucun geste utilisateur réel.

**Conclusion :** N10 n'est pas un bug de workflow. Le workflow N1→N2 fonctionne. Le reliquat est une
branche défensive morte, non testée, au message trompeur.

## Solution

### 1. Retirer la branche morte

Dans `ApprovalServiceImpl.assignReviewer` (`:56-57`), supprimer le `else if` :

```java
} else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1 && invoice.getDepartment().isRequiresN2()) {
    throw new WorkflowException("error.approval.cannot_assign_n2_in_n1");
}
```

Après suppression, un appel `assignReviewer` sur une facture en `EN_VALIDATION_N1` tombe dans le
`else` final (`:62-63`) et lève `error.approval.cannot_assign_from_state`. **Comportement observable
inchangé** : une exception `WorkflowException` (→ HTTP 400) est levée dans les deux cas ; seul le
message change (d'un message trompeur vers un message générique correct).

La branche `EN_VALIDATION_N2` (self-assign N2) reste **intacte**.

### 2. Retirer la clé i18n devenue inutilisée

`error.approval.cannot_assign_n2_in_n1` n'est plus référencée après (1). La retirer de :
- `src/main/resources/i18n/messages_en.properties:355`
- `src/main/resources/i18n/messages_fr.properties:349`

### 3. Test

Ajouter à `ApprovalServiceTest` un test prouvant qu'un `assignReviewer` en `EN_VALIDATION_N1` est
refusé proprement avec le message générique `error.approval.cannot_assign_from_state` (et non plus
`cannot_assign_n2_in_n1`). Ceci verrouille le nouveau comportement et documente l'intention.

Le test `p3_17` (self-assign N2) doit continuer à passer sans modification — c'est la preuve que la
branche N2 conservée fonctionne toujours.

## Contraintes / hors périmètre

- **Aucune migration Flyway.**
- Ne PAS toucher `validateN1`, `validateN2`, `assignAA`, ni la state-machine (tous corrects).
- Ne PAS toucher la branche `EN_VALIDATION_N2` de `assignReviewer` (vivante, testée).
- `messages_fr.properties` : UTF-8, accents en `\uXXXX`, pas de BOM (cf. leçon N23).

## Definition of Done

- [ ] Branche `EN_VALIDATION_N1 && requiresN2` retirée de `assignReviewer`.
- [ ] Clé `error.approval.cannot_assign_n2_in_n1` retirée de messages_fr ET messages_en.
- [ ] Test : `assignReviewer` en `EN_VALIDATION_N1` → `WorkflowException("error.approval.cannot_assign_from_state")`.
- [ ] `p3_17_twoLevelLifecycle_InfoDepartment` passe toujours (self-assign N2 intact).
- [ ] Gate backend `./mvnw test` ≥ 626/0/0/0 (baseline post-N23).
- [ ] `docs/KNOWN_ISSUES_REGISTRY.md` : PROB-129 (heredoc, jamais Edit).
- [ ] `docs/QA_AUDIT_EXHAUSTIF.md` : N10 marqué ✅ (non commité).
