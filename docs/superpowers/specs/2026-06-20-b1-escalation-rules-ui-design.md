# B1 — UI de configuration des règles d'escalade — Design

**Date :** 2026-06-20
**Gap visé :** M4 #11 / M6 #6 — *Escalation rules configuration* (🟠 → ✅)
**Branche :** `fix/a1-cashflow-sqlgrammar`

> ⚠ **Collision de nommage à noter.** Dans `docs/COMPLIANCE_MATRIX.md`, le libellé « B1 »
> désigne historiquement les *templates de checklist* (M4 #3, déjà ✅ le 2026-06-18). Le « B1 »
> de la liste du lot en cours (prompt de reprise) désigne **le gap M4 #11 / M6 #6 : l'UI de
> configuration des règles d'escalade**, encore ouvert. C'est ce dernier qui est traité ici.

---

## 1. Objectif et périmètre

L'escalade SLA fonctionne déjà : `DeadlineReminderJob` (quotidien, 07:00) détecte les
`ApprovalStep` en attente dont la deadline est dépassée et envoie un email d'escalade au **DAF
et à l'Admin**, en plus d'un avertissement à l'approbateur lui-même. Tout ce comportement est
**figé dans le code** : ni le délai avant escalade, ni les destinataires ne sont configurables.

**B1 rend cela configurable via un écran d'administration**, en répliquant le pattern éprouvé de
B4 (`PaymentAlertRule` — alertes de paiement J-N : entité → repo → service → DTO → controller →
page React + i18n).

**Niveau retenu : Standard, avec escalade hiérarchique contextuelle.** La règle configure
*quand* escalader (délai après dépassement) + activation/libellé. Le *qui* n'est **pas** configuré
par rôle : il est **déduit automatiquement** de la position du step en retard dans la chaîne
d'approbation (voir §2bis). Le **DAF (seul)** n'intervient qu'en **dernier recours**.

> **Pourquoi pas une liste de rôles à cocher (révision du 1er jet) :** l'escalade signifie
> « le responsable d'un step traîne → prévenir celui qui est au-dessus de lui ». Le destinataire
> dépend donc de *qui* est en retard, pas d'un rôle fixe. Un validateur N1 est toujours le maillon
> le plus bas : il est *sujet* d'escalade, jamais *cible*. Le seul validateur destinataire possible
> est le **N2, et uniquement quand son propre N1 (même département) est en retard**. Tout le reste
> remonte au DAF. Une liste de rôles à cocher (« notifier tous les N2 ») serait grossière
> (tous les départements) et métier-faux.

> **L'Admin n'est PAS destinataire d'escalade.** L'email d'escalade `sla-escalation-manager`
> contient le montant + le fournisseur de la facture (données financières). Conformément à la
> règle `admin-no-financial-access`, l'Admin est retiré des destinataires ; seul le **DAF** reçoit
> l'escalade en dernier recours. ⚠ Cela **corrige** le comportement existant (le job notifiait
> DAF + Admin avec montants) — à logger dans `KNOWN_ISSUES_REGISTRY.md`. L'Admin conserve l'accès
> à l'**écran de config** (délais uniquement, aucune donnée financière).

---

## 2. Modèle de données

Nouvelle entité **`EscalationRule`** dans le domaine `workflow` (l'escalade porte sur les
`ApprovalStep`).

| Champ | Type | Rôle |
|-------|------|------|
| `id` | UUID | PK |
| `hoursAfterDeadline` | int (0–720) | Délai après dépassement avant escalade (0 = immédiat = comportement actuel ; max 720h = 30j) |
| `label` | String(255) nullable | Libellé descriptif |
| `active` | boolean (def. true) | actif/inactif — nommé `active` (pas `isActive`) pour éviter le double-préfixe Lombok (PROB-003) |
| `createdBy` | ManyToOne User (LAZY) | audit |
| `createdAt` / `updatedAt` | Instant (auditing) | audit |

> **Pas de champ `recipientRoles`** : le destinataire est déduit (voir §2bis), pas stocké.

**Migration Flyway : `V61__create_escalation_rules.sql`** (prochaine version libre ; V60 est la
dernière appliquée). On ne modifie JAMAIS une migration déjà appliquée (PROB-009). Table
`escalation_rules`.

---

## 2bis. Résolution automatique du destinataire (escalade hiérarchique)

Pour chaque `ApprovalStep` en retard, dans `DeadlineReminderJob` :

| Step en retard | Destinataire de l'escalade |
|---|---|
| **N1** (`stepOrder = 1`), département à 2 niveaux | l'**approbateur du N2** du même département |
| **N1**, département à 1 niveau (pas de N2) | **DAF (seul)** (dernier recours) |
| **N2** (`stepOrder = 2`) | **DAF (seul)** (dernier recours — aucun niveau au-dessus) |

Résolution triviale via l'API repo existante : le N2 d'une facture est
`approvalStepRepository.findByInvoiceIdAndStepOrder(invoiceId, 2)`. S'il est présent et a un
`approver`, c'est la cible ; sinon, dernier recours **DAF seul**
(`userRepository.findActiveUsersByRoleName("ROLE_DAF")`, déjà utilisé).

**Le DAF n'est notifié qu'en dernier recours** (pas de copie systématique, et **plus l'Admin** —
cf. §2 séparation des devoirs) — décision explicite pour éviter de spammer la direction à chaque
retard. Le `distinct` existant dédoublonne.

---

## 3. Backend (calque B4)

Domaine `com.oct.invoicesystem.domain.workflow` :

- **`EscalationRule`** (entité, mêmes annotations d'auditing que `PaymentAlertRule`)
- **`EscalationRuleRepository`** : `findByActiveTrue()`, `findAllByOrderByHoursAfterDeadlineAsc()`
- **`EscalationRuleDTO`** (record) : id, hoursAfterDeadline, label, active, createdAt, updatedAt
- **`EscalationRuleRequest`** (record) : `@PositiveOrZero @Max(720) int hoursAfterDeadline`,
  `@Size(max=255) String label`, `boolean active`
- **`EscalationRuleService`** (`@Transactional`, Javadoc) : `list / create / update / delete`
- **`EscalationRuleController`** `/api/v1/escalation-rules` : GET/POST/PUT/DELETE, `@PreAuthorize`,
  `@Operation` Swagger, réponses `ApiResponse<T>`

**Rôles d'accès à l'écran : `ROLE_ADMIN` + `ROLE_DAF`.**
> C'est une config workflow/opérationnelle, **pas une donnée financière** → l'Admin y a accès
> (cohérent avec la mémoire `admin-no-financial-access` : l'Admin est exclu du *financier*, pas de
> la config système). Contraste assumé avec B4, qui excluait l'Admin car les seuils de paiement
> sont financiers.

---

## 4. Branchement dans `DeadlineReminderJob`

Dans `sendDeadlineReminders()`, section « SLA Escalations » :

1. Charger les règles actives : `escalationRuleRepository.findByActiveTrue()`. Le seuil effectif
   = le **plus petit `hoursAfterDeadline`** parmi les règles actives (la règle la plus stricte
   déclenche). Aucune règle active → seuil 0 (fallback historique = escalade immédiate).
2. Pour chaque step en retard, `hoursOverdue` est déjà calculé.
3. Escalader seulement si `hoursOverdue >= seuil effectif`.
4. **Résoudre le destinataire** selon §2bis (N2 du même dépt, sinon DAF seul) et :
   - envoyer l'email `sla-escalation-manager` ;
   - **+ créer une notification in-app** au même destinataire (cf. §4bis).
5. **Fallback** — aucune règle active : seuil 0 + dernier recours DAF seul = comportement
   historique exact, comme B4 conserve son défaut 7 jours. **Aucun comportement perdu.**
6. L'avertissement à l'approbateur lui-même (`sla-escalation-approver`) reste **inchangé**.

Dédoublonnage des destinataires via la logique `distinct` déjà présente.

---

## 4bis. Notification in-app à l'escalade (nouveau)

Aujourd'hui l'escalade n'envoie **qu'un email** : `eventPublisher.publishEvent(ApprovalDeadlineEvent)`
n'est appelé que dans la boucle des *rappels* (`approaching`), jamais dans celle des *escalades*
(`overdue`). Le rappel a une notif in-app, l'escalade non — incohérence corrigée par B1.

- L'escalade crée une **notification in-app** au destinataire résolu (§2bis), via le même
  mécanisme `Notification` que les autres événements.
- **Type : `NotificationType.DEADLINE`** — la valeur existe déjà (enum stocké en `EnumType.STRING`),
  donc **aucune migration** côté notification. C'est aussi plus précis que le `VALIDATION`
  qu'utilise l'actuel `onApprovalDeadline`.
- Titre/message bilingues (fr/en), construits sans exposer de montant pour rester cohérent avec
  la séparation des devoirs (la notif in-app du DAF peut contenir le montant ; celle d'un N2
  validateur le peut aussi puisqu'il est dans la chaîne d'approbation — pas de fuite vers l'Admin
  qui n'est jamais destinataire).
- **Implémentation** : soit étendre `onApprovalDeadline` pour gérer le cas escalade, soit publier
  un nouvel événement dédié ; décision tranchée au plan d'implémentation. Préférence : un nouvel
  `ApprovalEscalationEvent` + listener, pour ne pas surcharger la logique « rappel » existante et
  garder la résolution contextuelle (N2 du même dépt) hors de l'ancienne logique par-statut.

---

## 5. Frontend

- **`EscalationRulesPage.tsx`** calqué sur `PaymentAlertRulesPage.tsx` : table + éditeur inline,
  React Query (`['escalation-rules']`), `PageRoleGuard allowedRoles={['ROLE_ADMIN','ROLE_DAF']}`.
  - Champ délai : input numérique (heures), affiché « +Nh après échéance ».
  - Champs label + actif/inactif identiques à B4.
  - Note explicative dans l'UI : « Le destinataire est déterminé automatiquement : le validateur
    de niveau supérieur du même département, sinon le DAF. »
- **Route** dans `AppRoutes.tsx` (`/escalation-rules` ou sous `/admin/...` selon convention
  existante — à confirmer à l'implémentation) + lien d'accès depuis l'écran admin approprié.
- **i18n** : clés `escalationRules.*` dans `frontend/src/i18n/fr.json` + `en.json` (UTF-8).
- **i18n backend** : `error.escalation_rule.*` + messages succès `escalation_rule.created/updated/deleted`
  dans `messages_fr.properties` (**ISO-8859-1 → iconv, pas d'em-dash/guillemets courbes**) +
  `messages_en.properties`.

---

## 6. Tests (TDD RED → GREEN)

- **`EscalationRuleServiceTest`** (unitaire) : create / update / delete (happy path) ; rejet
  `hoursAfterDeadline` négatif ou > 720 ; seuil effectif = min des règles actives. (≥ 2 edge cases.)
- **`EscalationRuleControllerIntegrationTest`** : endpoints CRUD ; accès autorisé DAF/Admin ;
  accès refusé pour un rôle non autorisé (ex. ASSISTANT_COMPTABLE).
- **Résolution du destinataire** (idéalement testée) : N1 en retard dans un dépt à 2 niveaux →
  cible = approbateur N2 ; N2 en retard → cible = DAF (seul, sans Admin). Sinon couvert manuellement.
- **Notification in-app** : l'escalade persiste une `Notification` de type `DEADLINE` au
  destinataire résolu (vérifier le `NotificationType` et le destinataire).

**Vérification :** `./mvnw test` (domaine workflow vert) + `npx tsc --noEmit` + `npx vitest run`.
Les 4 échecs vitest pré-existants (InvoiceTimeline / useAuth / e2e) restent **hors scope**.

---

## 7. Documentation & clôture

- Logger tout bug réel rencontré dans `docs/KNOWN_ISSUES_REGISTRY.md` **avant** commit, **dont la
  correction de séparation des devoirs** : le job notifiait l'Admin avec des montants de facture
  (violation `admin-no-financial-access`) → désormais DAF seul en dernier recours.
- Basculer M4 #11 et M6 #6 en ✅ dans `docs/COMPLIANCE_MATRIX.md`.
- Commit unique (1 commit = 1 sujet) ; **ne pas pousser** (demander avant). PR seulement à la fin
  du lot.
- Émettre un bloc de reprise paste-ready pour la session suivante (B2).
