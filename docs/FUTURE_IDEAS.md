# Idées gardées pour des évolutions futures

> Ce fichier consigne des options de conception délibérément écartées pour rester
> dans le périmètre (YAGNI), mais qui pourraient être implémentées plus tard si le
> besoin métier se confirme. Ne PAS implémenter sans une nouvelle décision explicite.

---

## B2 — Politique de rétention : passer du singleton à une liste de règles (Option B)

**Contexte :** B2 (2026-06-20) a implémenté la config de rétention en **singleton global**
(Option A) : une seule durée de rétention (années) + activation, lue par
`DocumentRetentionJob`. Voir `docs/superpowers/specs/2026-06-20-b2-retention-policy-design.md`.

**Option B écartée (à reconsidérer si le besoin apparaît) :** remplacer le singleton par
un **ensemble de règles** (CRUD, comme `EscalationRule` / `PaymentAlertRule`), avec une
durée de rétention **différente par type de document ou par département**.
Exemple : contrats = 10 ans, justificatifs = 7 ans, bons de commande = 5 ans.

**Ce que cela impliquerait :**
- Refonte de `DocumentRetentionJob` pour parcourir N règles au lieu d'un seuil unique.
- **Catégorisation des documents** : il faut un champ « type/catégorie » exploitable sur
  `InvoiceDocument` pour rattacher un document à une règle (n'existe pas aujourd'hui).
- Gestion des conflits / règle par défaut (document ne correspondant à aucune règle).
- UI : passer du formulaire d'édition à un tableau CRUD (réutiliser le pattern
  `EscalationRulesPage.tsx`).

**Pourquoi écarté pour l'instant :** le job actuel raisonne sur un seuil unique global ;
la conformité comptable (OHADA) impose généralement UNE durée d'archivage unique (10 ans).
Aucun besoin actuel de durées différenciées → décision prématurée (YAGNI).

**Chemin de migration :** la table singleton `retention_policy` pourrait devenir la « règle
par défaut » d'un futur modèle multi-règles, sans perte de données.
