# N10 — Nettoyage branche morte assignReviewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Supprimer la branche morte `EN_VALIDATION_N1 && requiresN2 → cannot_assign_n2_in_n1` de `assignReviewer` (jamais atteinte par l'UI, non testée, message trompeur) et la clé i18n associée, sans changer le comportement observable.

**Architecture :** `assignReviewer` conserve ses branches utiles (`EN_CONTROLE_AA` self-assign N1, `SOUMIS` refus, `EN_VALIDATION_N2` self-assign N2 — testée). Le cas `EN_VALIDATION_N1` retombe dans le `else` final existant qui lève `cannot_assign_from_state`. Une exception `WorkflowException` reste levée dans les deux cas ; seul le message (trompeur → générique correct) change. Le workflow N1→N2 réel (via `validateN1` + state-machine, transition T3) n'est pas touché.

**Tech Stack :** Spring Boot 3.4, JUnit 5 + Mockito (`ApprovalServiceTest`, test unitaire `@InjectMocks`), Spring MessageSource (bundle `i18n/messages_{fr,en}.properties`).

## Global Constraints

- `messages_fr.properties` / `messages_en.properties` sous `src/main/resources/i18n/`.
- `messages_fr.properties` : UTF-8, accents en `\uXXXX`, **pas de BOM** (leçon N23 : une réécriture binaire d'un `.properties` peut réintroduire un BOM — ici on ne fait qu'une suppression de ligne, mais vérifier `head -c3 | xxd` ≠ `efbbbf` après).
- Gate backend : `./mvnw test` doit rester ≥ 626/0/0/0 (baseline post-N23).
- `docs/KNOWN_ISSUES_REGISTRY.md` contient un octet NUL → **append via heredoc bash uniquement**, jamais Edit. Prochain PROB = **PROB-129**.
- Une branche = un sujet : tout sur `fix/n10-assignreviewer-dead-branch`.
- NE PAS toucher : `validateN1`, `validateN2`, `assignAA`, la state-machine, ni la branche `EN_VALIDATION_N2` de `assignReviewer`.

---

### Task 1 : Retirer la branche morte + clé i18n + test

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java` (retrait des lignes 56-57)
- Modify: `src/main/resources/i18n/messages_en.properties` (retrait ligne `error.approval.cannot_assign_n2_in_n1`)
- Modify: `src/main/resources/i18n/messages_fr.properties` (retrait ligne `error.approval.cannot_assign_n2_in_n1`)
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceTest.java` (ajout d'un test)

**Interfaces:**
- Consumes: le `else` final existant de `assignReviewer` (`ApprovalServiceImpl:62-63`) qui lève `new WorkflowException("error.approval.cannot_assign_from_state")`.
- Produces: aucun nouveau symbole public. Comportement : `assignReviewer(invoice EN_VALIDATION_N1)` lève désormais `WorkflowException` avec message `error.approval.cannot_assign_from_state`.

---

- [ ] **Step 1 : Écrire le test qui échoue**

Ajouter cette méthode à `ApprovalServiceTest`, juste après `assignReviewer_WhenValide_ThrowsWorkflowException` (vers la ligne 138). Le setup `@BeforeEach` donne déjà un `department` avec `requiresN2=true` — on prouve donc que même un dept 2-niveaux en `EN_VALIDATION_N1` tombe dans le message générique (et non plus `cannot_assign_n2_in_n1`). Les imports utilisés (`assertThatThrownBy`, `WorkflowException`, `InvoiceStatus`, `Optional`, `when`) sont déjà présents.

```java
    @Test
    void assignReviewer_WhenEnValidationN1_TwoLevelDept_ThrowsGenericWorkflowException() {
        // N10 : la branche morte cannot_assign_n2_in_n1 a été retirée. Un assignReviewer en
        // EN_VALIDATION_N1 (même pour un dept 2-niveaux, requiresN2=true dans le setup) doit
        // retomber sur le message générique cannot_assign_from_state — le workflow N1→N2 réel
        // passe par validateN1 + state-machine, pas par assignReviewer.
        mockSecurityContext("ROLE_VALIDATEUR_N1_INFO");
        invoice.setStatus(InvoiceStatus.EN_VALIDATION_N1);
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> approvalService.assignReviewer(invoice.getId()))
                .isInstanceOf(WorkflowException.class)
                .hasMessage("error.approval.cannot_assign_from_state");
    }
```

- [ ] **Step 2 : Lancer le test pour vérifier qu'il échoue**

Run: `./mvnw test -Dtest=ApprovalServiceTest#assignReviewer_WhenEnValidationN1_TwoLevelDept_ThrowsGenericWorkflowException`

Attendu : **ÉCHOUE**. Avant le retrait, la branche `:56-57` intercepte cet état (dept requiresN2=true) et lève `error.approval.cannot_assign_n2_in_n1` → `hasMessage("error.approval.cannot_assign_from_state")` échoue (message actuel ≠ attendu).

- [ ] **Step 3 : Retirer la branche morte dans ApprovalServiceImpl**

Dans `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java`, supprimer ce `else if` (lignes 56-57) :

```java
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1 && invoice.getDepartment().isRequiresN2()) {
            throw new WorkflowException("error.approval.cannot_assign_n2_in_n1");
```

Le bloc `assignReviewer` doit alors enchaîner directement de la branche `SOUMIS` à la branche `EN_VALIDATION_N2`. Résultat attendu du corps (lignes ~50-64) :

```java
        if (invoice.getStatus() == InvoiceStatus.EN_CONTROLE_AA) {
            checkRole(currentUser, invoice.getDepartment().getN1Role());
            createOrUpdateStep(invoice, 1, currentUser, "Validation N1 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ASSIGN_REVIEWER, null);
        } else if (invoice.getStatus() == InvoiceStatus.SOUMIS) {
            throw new WorkflowException("error.approval.aa_control_required");
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N2) {
            checkRole(currentUser, invoice.getDepartment().getN2Role());
            createOrUpdateStep(invoice, 2, currentUser, "Validation N2 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            // No state machine event for this
        } else {
            throw new WorkflowException("error.approval.cannot_assign_from_state");
        }
```

- [ ] **Step 4 : Retirer la clé i18n inutilisée (EN puis FR)**

La clé `error.approval.cannot_assign_n2_in_n1` n'est plus référencée. La retirer des deux bundles avec `sed` (suppression de la ligne entière par motif, sûr car la clé est unique) :

```bash
sed -i '/^error\.approval\.cannot_assign_n2_in_n1=/d' src/main/resources/i18n/messages_en.properties
sed -i '/^error\.approval\.cannot_assign_n2_in_n1=/d' src/main/resources/i18n/messages_fr.properties
```

Vérifier qu'il ne reste **aucune** référence à la clé dans tout le projet :

```bash
grep -rn "cannot_assign_n2_in_n1" src/ frontend/src/ ; echo "exit=$? (1 = plus aucune occurrence = OK)"
```

Attendu : aucune ligne affichée, `exit=1`.

Vérifier qu'aucun BOM n'a été introduit dans messages_fr (leçon N23) :

```bash
head -c3 src/main/resources/i18n/messages_fr.properties | xxd
```

Attendu : `2320 23` ou similaire commençant par `23` (`#`), **jamais** `efbb bf`.

- [ ] **Step 5 : Lancer les tests ciblés pour vérifier qu'ils passent**

Run: `./mvnw test -Dtest=ApprovalServiceTest+ApprovalControllerTest`

Attendu : **tous** passent. En particulier :
- `assignReviewer_WhenEnValidationN1_TwoLevelDept_ThrowsGenericWorkflowException` PASSE (nouveau).
- `p3_17_twoLevelLifecycle_InfoDepartment` PASSE (self-assign N2 via `/workflow/assign` intact — preuve que la branche `EN_VALIDATION_N2` conservée fonctionne toujours).
- Les autres `assignReviewer_*` PASSENT inchangés.

- [ ] **Step 6 : Gate complet**

Run: `rm -rf target/surefire-reports && export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany && ./mvnw test`

Attendu : `Tests run: 627, Failures: 0, Errors: 0, Skipped: 0` (626 baseline post-N23 + 1 nouveau test). Si un autre test casse, investiguer (règle « no failures on task completion »).

- [ ] **Step 7 : Living documentation — PROB-129 (heredoc, jamais Edit)**

```bash
cat >> docs/KNOWN_ISSUES_REGISTRY.md <<'EOF'

## PROB-129 — Branche morte cannot_assign_n2_in_n1 dans assignReviewer (N10)

**Root cause :** `ApprovalServiceImpl.assignReviewer` contenait une branche
`EN_VALIDATION_N1 && requiresN2` levant `error.approval.cannot_assign_n2_in_n1`. Le finding
d'audit N10 la lisait comme rendant le passage N1→N2 inatteignable. En réalité le passage N1→N2
se fait via `validateN1()` + state-machine (transition T3 EN_VALIDATION_N1→EN_VALIDATION_N2,
testée : StateMachineTransitionExhaustiveTest T3, ApprovalControllerTest.p3_17). La branche
était donc du code mort : jamais appelée par l'UI (InvoiceActionPanel n'invoque ASSIGN_REVIEWER
que depuis EN_CONTROLE_AA), non testée, au message conceptuellement trompeur.

**Solution :** Retrait de la branche (ApprovalServiceImpl:56-57) — le cas EN_VALIDATION_N1
retombe dans le else final (cannot_assign_from_state), comportement observable inchangé (une
WorkflowException → 400 dans les deux cas). Retrait de la clé i18n cannot_assign_n2_in_n1
(messages_fr + messages_en) devenue inutilisée. La branche EN_VALIDATION_N2 (self-assign N2,
testée par p3_17) est CONSERVÉE.

**Preventive rule :** Avant de « corriger » un finding de workflow, tracer le VRAI chemin de
transition (state-machine + service) et vérifier la couverture de test avant de croire qu'une
branche est cassée ou inatteignable. Une branche de service non appelée par l'UI et sans test
est du code mort candidat à suppression, pas un bug à « rebrancher ».
EOF
```

- [ ] **Step 8 : Marquer N10 ✅ dans le suivi d'audit (non commité)**

Éditer `docs/QA_AUDIT_EXHAUSTIF.md` : passer N10 à ✅ (résolu 2026-07-19, PROB-129, prémisse du finding réfutée + code mort retiré). Fichier NON commité (convention).

- [ ] **Step 9 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java \
        src/main/resources/i18n/messages_en.properties \
        src/main/resources/i18n/messages_fr.properties \
        src/test/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceTest.java \
        docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(workflow): N10 — retirer la branche morte cannot_assign_n2_in_n1 (PROB-129)"
```

(Ne PAS `git add .` : `docs/QA_*`, `docs/SOD_AUDIT_REPORT.md`, `scratch/` restent non-suivis.)

---

## Self-Review

**Spec coverage :**
- Retrait branche 56-57 : Step 3 ✅
- Retrait clé i18n FR+EN : Step 4 ✅
- Test EN_VALIDATION_N1 → cannot_assign_from_state : Step 1 ✅
- p3_17 (self-assign N2) toujours vert : Step 5 ✅
- Gate ≥ 626 (cible 627) : Step 6 ✅
- PROB-129 heredoc : Step 7 ✅
- N10 ✅ non commité : Step 8 ✅
- Branche un sujet : header + Step 9 ✅

**Placeholder scan :** aucun TBD/TODO ; code du test, de la suppression, des commandes sed/grep/heredoc fournis littéralement.

**Type consistency :** le message `error.approval.cannot_assign_from_state` du test (Step 1) correspond exactement à celui du `else` final conservé (Step 3). La clé retirée `cannot_assign_n2_in_n1` est la même en Step 3 (Java), Step 4 (properties) et Step 7 (doc). `requiresN2=true` du setup `@BeforeEach` rend le test discriminant (sans le retrait, la branche 56-57 l'intercepterait).
