# M4 #4 — Routage d'approbation par seuil de montant (garde de limite)

**Date :** 2026-06-20
**Item :** COMPLIANCE_MATRIX.md → M4 UI #4 / Features #4 (🟠 → ✅ visé)
**Branche :** fix/a1-cashflow-sqlgrammar
**Option retenue :** A (garde à chaque étape). Option B (saut de niveau automatique)
délibérément écartée → consignée dans `docs/FUTURE_IDEAS.md`.

---

## 1. Problème

Le routage d'approbation actuel est piloté **uniquement par le département** :
N1 → (N2 si `department.requiresN2`) → DAF (étape 3, systématique). Le montant de la
facture (`Invoice.amount`) et la limite d'approbation de l'utilisateur
(`User.approvalLimit`, déjà présente en base, `precision 15, scale 2`, nullable) ne sont
**jamais consultés**. M4 #4 demande un routage par seuil de montant : un approbateur ne doit
pouvoir valider que dans la limite de son habilitation financière.

## 2. Règle métier

À chaque étape de validation, avant qu'un approbateur ne valide une facture, le système
vérifie que sa limite d'approbation couvre le montant de la facture.

- Si `approver.approvalLimit != null` **et** `approvalLimit < invoice.amount`
  → **validation refusée** (`WorkflowException`, message bilingue générique).
- Si `approver.approvalLimit == null` → **illimité**, validation autorisée.
- L'étape **Bon à Payer (DAF)** n'est **jamais** soumise à cette garde : le DAF est le
  rempart final, toujours autorisé quel que soit le montant ou une éventuelle limite saisie.
- La comparaison utilise `<` **strict** : `approvalLimit == amount` est **autorisé**.
- La facture **reste dans son état courant** en cas de refus ; l'escalade est
  **organisationnelle** (un approbateur habilité, in fine le DAF, agit). Aucune modification
  de la machine à états.

### Conventions verrouillées
- `approvalLimit == null` ⇒ illimité.
- DAF (`bonAPayer`) ⇒ jamais soumis à la garde, même si une limite lui est saisie par erreur.
- Montant comparé = `Invoice.amount` (champ unique du modèle ; pas de distinction HT/TTC).
- Message d'erreur **générique** (n'expose pas les montants/limites dans la réponse API).

## 3. Conception technique

**Fichier touché : un seul** —
`src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java`.

Aucune migration Flyway (le champ `approval_limit` existe déjà), aucun changement de modèle,
aucun nouvel endpoint, aucun changement front.

### Nouvelle méthode privée

```java
private void ensureWithinApprovalLimit(User approver, Invoice invoice) {
    BigDecimal limit = approver.getApprovalLimit();
    if (limit == null) return;                          // null = illimité
    if (invoice.getAmount() != null
            && limit.compareTo(invoice.getAmount()) < 0) {
        throw new WorkflowException("approval.limit.exceeded");
    }
}
```

### Points d'appel (à côté des `ensureNotSubmitter` existants)

| Méthode        | Ligne approx. | Garde appliquée ? |
|----------------|---------------|-------------------|
| `validateN1()` | ~75           | ✅ oui            |
| `validateN2()` | ~91           | ✅ oui            |
| `bonAPayer()`  | ~107          | ❌ NON (DAF rempart final) |

### Internationalisation

- Clé `approval.limit.exceeded`, **sans paramètre** (message générique).
- Chemin des fichiers i18n : `src/main/resources/i18n/messages_{fr,en}.properties`.
- `messages_fr.properties` : ajouté en **ISO-8859-1** via `iconv -f UTF-8 -t ISO-8859-1`
  (jamais d'append UTF-8 direct — corrompt les accents). Pas d'em-dash ni de guillemets courbes.
- `messages_en.properties` : ASCII.
- Levée via `WorkflowException`, traitée par `GlobalExceptionHandler` (pas de try/catch en
  contrôleur — CLAUDE.md §3). Message rendu selon `Accept-Language`.

Exemples de libellé :
- FR : « Le montant de cette facture depasse votre limite d'approbation. »
- EN : « This invoice amount exceeds your approval limit. »

## 4. Stratégie de test (TDD strict)

Chaque test est écrit et vu **échouer (rouge)** avant d'ajouter la garde, puis passé au **vert**.

### Tests unitaires — `ApprovalServiceTest.java`

1. `validateN1` avec `approvalLimit == null` → autorisé (passe la garde).
2. `validateN1` avec `approvalLimit > amount` → autorisé.
3. `validateN1` avec `approvalLimit == amount` (égalité, cas limite) → autorisé (`<` strict).
4. `validateN1` avec `approvalLimit < amount` → lève `WorkflowException`
   (`approval.limit.exceeded`) ; facture **non avancée**.
5. `validateN2` avec `approvalLimit < amount` → lève l'exception (couvre le 2ᵉ point d'appel).
6. `bonAPayer` (DAF) avec `approvalLimit < amount` → **autorisé** (garde non appliquée au
   DAF — verrou de la règle « DAF jamais soumis »).

### Test d'intégration

Si un test contrôleur d'approbation pertinent existe : `validateN1` par un approbateur à
limite insuffisante → erreur HTTP gérée par `GlobalExceptionHandler`, message bilingue selon
`Accept-Language`.

### Critère de complétion (règle « 0 échec »)

`mvnw test` + `tsc --noEmit` (depuis `frontend/`) + `vitest run` **tous verts** avant de
déclarer la tâche terminée. Le front n'est pas modifié, mais la suite complète est lancée.

## 5. Hors périmètre (consigné dans FUTURE_IDEAS.md)

- **Option B — saut de niveau automatique** : router automatiquement vers le niveau supérieur
  quand le montant dépasse la limite courante (modifie la machine à états). Écartée (risque
  de régression sur le routage, hors périmètre PFE).
- Règles montant → nombre de niveaux requis (sémantique alternative non retenue).
- Vérification de la limite de l'approbateur côté machine à états plutôt que service.

## 6. Documentation à mettre à jour à la livraison

- `docs/COMPLIANCE_MATRIX.md` : flip M4 UI #4 et Features #4 en ✅ + ligne de preuve.
- `docs/KNOWN_ISSUES_REGISTRY.md` : **uniquement** si un bug réel est découvert/corrigé en
  cours de route (pas pour une feature planifiée).
- `docs/FUTURE_IDEAS.md` : déjà fait (Option B consignée).
