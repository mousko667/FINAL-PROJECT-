# Assainissement de l'encodage de `messages_fr.properties`

**Date :** 2026-07-11
**Type :** Correctif d'encodage / dette technique i18n
**Fichier cible unique :** `src/main/resources/i18n/messages_fr.properties`
**Prérequis de :** travaux UI/UX à venir (design system) — un i18n FR sain est la base.

---

## 1. Contexte & problème

Le fichier i18n français est dans un état d'encodage **mixte et partiellement
corrompu**, découvert lors d'une inspection octet-par-octet (le simple `file`
le voyait « ASCII » car la majorité l'est). Trois défauts distincts coexistent :

1. **Bloc MFA en UTF-16LE incrusté** (région octets ~4507–7187) : 15 clés MFA
   stockées en UTF-16 little-endian au milieu d'un fichier ASCII. Sous la
   lecture UTF-8 par défaut de Spring, ce bloc s'affiche en mojibake
   (caractères espacés `m f a`, accents en `�`). **1287 octets NUL** au total.

2. **31 clés aux accents définitivement détruits** : accents remplacés par
   `U+FFFD` (`�`, séquence `ef bf bd`) ou mojibake `\xc3?` — irrécupérables
   depuis le fichier lui-même. Ex : `webhook.delivery.failed=�chec...`
   (devrait être « Échec »). Réparties dans les sections paiement, reporting,
   webhooks, escalade, rétention.

3. **Défauts cosmétiques** : 6× `﻿` parasite (BOM inséré au milieu du
   texte, motif `…déjà﻿ : {0}`) + 1 mojibake en commentaire ligne 1
   (`\xe2?"` au lieu d'un tiret cadratin).

**Découverte clé — doublons MFA :** les 15 clés MFA existent **en double**.
Le bloc UTF-16 (défaut #1) est un **doublon mort** ; une seconde copie plus bas
dans le fichier (≈ lignes 262/280-294) est **déjà saine** et porte la
traduction canonique (« double authentification »). En `.properties` Java, la
**dernière occurrence gagne** — donc au runtime c'est déjà le bon bloc qui
s'applique. Le bloc UTF-16 ne fait que polluer le fichier.

### État de l'encodage — vérité runtime

- Aucune config d'encodage explicite (`application.yml` sans `messages.encoding`,
  pas de bean `MessageSource` custom) → Spring Boot lit en **UTF-8 par défaut**.
- Le fichier EN (`messages_en.properties`) est **sain** (0 `U+FFFD`) et sert de
  **référence 1:1** pour chaque clé (sens + existence).
- Choix d'encodage cible : **ASCII pur avec échappement `\uXXXX`**. C'est le
  format `.properties` historique, **indépendant de l'encodage de lecture**
  (fonctionne que Spring lise en UTF-8, ISO-8859-1, peu importe) → le plus sûr.

## 2. Périmètre

### Dans le périmètre (fichier FR uniquement)

- **A. Supprimer le doublon MFA** : retirer le bloc UTF-16 (15 clés + son
  commentaire), conserver uniquement le bloc MFA canonique sain déjà présent.
- **B. Réparer les 31 clés détruites** : rétablir les accents français corrects,
  chaque valeur revérifiée contre la clé EN correspondante (sens) et le texte FR
  résiduel (formulation), réécrites en `\uXXXX`.
- **C. Nettoyer les cosmétiques** : 6× `﻿`, mojibake ligne 1.
- **D. Garantir l'intégrité de sortie** : fichier final = **ASCII pur**
  (0 octet > 127), **0 octet NUL**, **fins de ligne CRLF** préservées.

### Hors périmètre (ne pas toucher)

- `messages_en.properties` — sain ; contient des modifs non commitées
  préexistantes (travail antérieur du dev solo), à laisser telles quelles.
- Les ~240 clés FR déjà correctes.
- La configuration Spring `MessageSource` (UTF-8 par défaut convient).
- Toute traduction de contenu (on répare l'encodage, on ne re-traduit pas le
  sens ; les 31 valeurs gardent leur formulation d'origine, accents rétablis).

## 3. Contrainte technique critique

**L'outil d'édition texte (Edit) normalise l'encodage** de ce fichier :
lors d'un essai, il a converti CRLF→LF sur tout le fichier et re-décodé les
octets, produisant une régression (350→319 lignes, statut « binaire »).

→ **Toute écriture DOIT passer par un script Python** manipulant le fichier en
**mode binaire** (`open(path, "rb"/"wb")`), qui :
- garde les portions saines **octet pour octet** (aucune re-décodage),
- ne remplace que les régions ciblées,
- écrit un résultat validé ASCII + CRLF.

Jamais d'`Edit`/`Write` texte direct sur `messages_fr.properties`.

## 4. Approche

Script de reconstruction unique, idempotent, en 4 passes sur les octets bruts :

1. **Parse défensif** : découper le fichier en lignes sur `\r\n` (bytes),
   décoder chaque ligne en `latin-1` (préserve tous les octets 1:1 pour
   inspection), sans jamais décoder tout le fichier d'un bloc.
2. **Passe A — dédoublonnage MFA** : identifier le bloc UTF-16 par sa signature
   (présence d'octets NUL) et le supprimer intégralement (lignes + commentaire).
   Vérifier ensuite qu'il reste **exactement une** occurrence de chaque clé MFA.
3. **Passe B — réparation** : pour chacune des 31 clés détruites, remplacer la
   ligne par `clé=<valeur FR corrigée échappée \uXXXX>`. La table de
   correspondance (clé → FR correct) est figée dans le script, chaque entrée
   annotée de sa référence EN.
4. **Passe C — cosmétique** : retirer `﻿`, corriger le mojibake ligne 1.
5. **Écriture + validation** : réassembler en CRLF, encoder ASCII strict
   (échoue si un octet > 127 subsiste), écrire en binaire.

### Validation post-écriture (bloquante — la tâche n'est PAS finie sans)

- `assert` octets NUL == 0
- `assert` octets > 127 == 0 (ASCII pur)
- `assert` toutes fins de ligne == CRLF, nombre de lignes cohérent
- **Aucune clé en double** (recompter avec `Counter`)
- **Aucune clé perdue** : l'ensemble des clés FR ⊇ clés FR d'origine
  (moins les 15 doublons MFA supprimés), et parité clés FR ↔ clés EN vérifiée
- Parse Java-properties simulé : chaque `\uXXXX` se dé-échappe en accent correct
- `./mvnw -q compile` puis démarrage backend OK, et **vérification runtime** :
  au moins une clé réparée (ex. `webhook.delivery.failed`) et une clé MFA
  s'affichent correctement via l'API en `Accept-Language: fr`.

## 5. Table de réparation (31 clés — complète)

Formulation FR conservée, accents rétablis, chaque valeur validée contre la clé
EN correspondante. Cette table est la source de vérité, recopiée telle quelle
dans le script.

| # | Clé | FR corrigé | Réf. EN |
|---|---|---|---|
| 1 | `payment.recorded.success` | Paiement enregistré avec succès | Payment recorded successfully |
| 2 | `remittance.invoice.ref` | Référence de Facture | Invoice Reference |
| 3 | `remittance.amount` | Montant Payé | Amount Paid |
| 4 | `remittance.payment_method` | Méthode de Paiement | Payment Method |
| 5 | `remittance.reference` | Numéro de Référence | Reference Number |
| 6 | `remittance.download.url.generated` | URL de téléchargement de l'avis de paiement générée avec succès | Remittance download URL generated successfully |
| 7 | `report.aging.title` | Rapport d'Analyse de l'Âge d'Impayé | Aging Analysis Report |
| 8 | `report.aging.total_overdue` | Total Impayé | Total Overdue |
| 9 | `report.cashflow.title` | Projection de Flux de Trésorerie | Cash Flow Projection |
| 10 | `report.cashflow.projected_amount` | Montant Projeté | Projected Amount |
| 11 | `report.cashflow.total_projected` | Montant Total Projeté | Total Projected Amount |
| 12 | `report.supplier.payment_method` | Méthode de Paiement | Payment Method |
| 13 | `webhook.delivery.failed` | Échec de la livraison du webhook | Webhook delivery failed |
| 14 | `integration.status.title` | État de Santé de l'Intégration | Integration Health Status |
| 15 | `report.bottleneck.title` | Goulets d'étranglement d'approbation | Approval Bottlenecks |
| 16 | `report.bottleneck.department` | Département | Department |
| 17 | `report.bottleneck.step_order` | Ordre d'étape | Step Order |
| 18 | `report.bottleneck.step_name` | Nom d'étape | Step Name |
| 19 | `report.bottleneck.step_count` | Nombre d'étapes | Step Count |
| 20 | `report.supplier.performance.accuracy_rate` | Taux de précision des factures | Invoice Accuracy Rate |
| 21 | `report.kpi.webhook_delivery_success_rate` | Taux de succès de livraison Webhook | Webhook Delivery Success Rate |
| 22 | `supplier.dashboard.matching_status_breakdown` | Répartition du statut de concordance | Matching Status Breakdown |
| 23 | `supplier.dashboard.next_expected_payment_date` | Prochaine date de paiement prévue | Next Expected Payment Date |
| 24 | `invoice.reject.code.PIECE_MANQUANTE` | Pièce justificative manquante | Missing supporting document |
| 25 | `invoice.reject.code.HORS_BUDGET` | Dépense hors budget | Out-of-budget expense |
| 26 | `error.reject.detail.required.for.other` | Un détail d'au moins 10 caractères est requis lorsque le motif est "Autre". | A detail of at least 10 characters is required when the reason is "Other". |
| 27 | `error.escalation_rule.not_found` | Règle d'escalade introuvable | Escalation rule not found |
| 28 | `escalation_rule.created` | Règle d'escalade créée | Escalation rule created |
| 29 | `escalation_rule.updated` | Règle d'escalade mise à jour | Escalation rule updated |
| 30 | `escalation_rule.deleted` | Règle d'escalade supprimée | Escalation rule deleted |
| 31 | `retention_policy.updated` | Politique de rétention mise à jour | Retention policy updated |

> Note : le bloc MFA « canonique » à conserver (passe A) est celui **sans octet
> NUL** (traduction « double authentification », déjà correcte) ; le bloc à
> supprimer est le bloc UTF-16 (avec octets NUL).

## 6. Tests / vérification

Pas de test unitaire nouveau (fichier de ressources, pas de code). La
vérification est le bloc §4 « Validation post-écriture », exécuté par le script
lui-même + un contrôle runtime manuel via l'API. La règle projet
[No failures on task completion] s'applique : `./mvnw test` doit rester à 0
échec après le changement (le fichier i18n n'a pas de test dédié mais ne doit
casser aucun test existant chargeant les messages).

## 7. Livrables

- `messages_fr.properties` assaini (ASCII/`\uXXXX`/CRLF, 0 NUL, 0 doublon).
- Entrée `docs/KNOWN_ISSUES_REGISTRY.md` (PROB-NNN) : cause racine
  (copier-coller UTF-16 + double bloc MFA + accents U+FFFD), solution, règle
  préventive (« i18n FR toujours en `\uXXXX` ASCII ; jamais éditer via un outil
  qui normalise l'encodage ; vérifier `count(NUL)==0` avant commit »).
- `docs/TASKS.md` mis à jour si une entrée §A Open Gaps couvrait l'i18n.
- Le script de reconstruction reste dans le scratchpad (outil jetable, pas un
  livrable projet — conforme aux règles de création de fichiers §4 du CLAUDE.md).

## 8. Risques & garde-fous

- **Risque :** casser une des 240 clés saines. **Garde-fou :** on ne réécrit que
  les lignes ciblées ; les portions saines sont copiées octet-pour-octet ;
  la validation §4 vérifie la parité des clés FR↔EN.
- **Risque :** régression d'encodage par un outil texte. **Garde-fou :** §3,
  script binaire only.
- **Risque :** perte de sens en réparant un accent. **Garde-fou :** double
  contrôle EN + formulation FR d'origine conservée.
