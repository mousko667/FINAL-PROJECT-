# M7 #4 + M11 #5 — Statut de paiement (SCHEDULED/PROCESSED) + rapport cycle de paiement

**Date :** 2026-06-27
**Branche :** chore/sanitize-docs-migrations
**Modules :** M7 #4 (statut paiement), M11 #5 (rapport cycle de paiement)
**TASKS.md :** lignes 298 (M7 #4) et 441 (M11 #5)

## Contexte (vérifié dans le code réel)

Aujourd'hui, `PaymentServiceImpl.recordPayment` crée le `Payment` ET déclenche
immédiatement, en un seul appel : génération de l'avis de paiement (remittance),
publication de `InvoicePayedEvent`, puis les transitions state machine
`RECORD_PAYMENT` (BON_A_PAYER → PAYE) et `ARCHIVE` (PAYE → ARCHIVE). Tout paiement
est donc de facto « exécuté » instantanément. L'entité `Payment` n'a **aucun** champ
de statut. Il n'existe aucune notion de paiement *planifié* (à exécuter plus tard).

Côté reporting, cash-flow (`/reports/cash-flow`, corrigé PROB-054) et processing-time
existent, mais il n'y a **pas** de rapport « cycle de paiement » explicite mesurant les
délais entre les étapes du workflow BAP.

## Décisions de cadrage (issues du brainstorming)

1. **Statut additif**, sans toucher la state machine BAP. On n'introduit pas de nouvel
   état de facture ; on ajoute un statut **sur le paiement**.
2. Transition SCHEDULED → PROCESSED par **action manuelle** (endpoint dédié), pas de job.
3. **Rétro-compatibilité** : un paiement naît PROCESSED par défaut (comportement actuel
   « 1 clic = payé + archivé » intact). Planification = opt-in explicite.
4. Batch (`recordBatchPayment`) **inchangé** : crée toujours des paiements PROCESSED.
5. Rapport cycle = **délais par étape** (soumission → validation → BAP → paiement,
   planifié vs réel).
6. **Front inclus** dans ce lot.

## Architecture

### 1. Modèle de données

**Nouvel enum** `domain/payment/model/PaymentStatus` :
```java
public enum PaymentStatus { SCHEDULED, PROCESSED }
```

**Champs ajoutés à `Payment`** :
- `@Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) private PaymentStatus status;`
- `@Column(name = "processed_date") private Instant processedDate;` (nullable — date
  d'exécution **réelle**, distincte de `paymentDate` qui reste la date prévue / d'effet).

**Migration Flyway** (prochain numéro de version contigu — à déterminer à
l'implémentation en listant `src/main/resources/db/migration/`) :
```sql
ALTER TABLE payments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED';
ALTER TABLE payments ADD COLUMN processed_date TIMESTAMP;
UPDATE payments SET processed_date = payment_date WHERE processed_date IS NULL;
```
Le DEFAULT `PROCESSED` rend tout l'historique existant cohérent (déjà exécuté).
Pas de modification d'une migration déjà appliquée (PROB-009).

### 2. Flux de création et de finalisation

**`PaymentRequest`** : ajout d'un champ optionnel `Boolean scheduled` (nullable ;
absent ou `false` → PROCESSED immédiat ; `true` → SCHEDULED).

**`PaymentDTO`** : ajout de `PaymentStatus status` et `Instant processedDate`.

**`recordPayment`** (refactor) :
- Validations actuelles inchangées (facture en BON_A_PAYER, pas de paiement existant).
- Si `scheduled == true` :
  - crée le `Payment` avec `status = SCHEDULED`, `processedDate = null` ;
  - **n'effectue PAS** la finalisation (pas de remittance, pas d'event, pas de
    transitions). La facture reste en BON_A_PAYER.
- Sinon (défaut) :
  - crée le `Payment` avec `status = PROCESSED`, `processedDate = now()` ;
  - appelle `finalizePayment(payment, userId)`.

**Méthode privée `finalizePayment(Payment payment, UUID userId)`** : extrait la
séquence aujourd'hui en ligne dans `recordPayment` — génération remittance, publication
`InvoicePayedEvent`, transitions `RECORD_PAYMENT` puis `ARCHIVE`. Appelée par les deux
chemins (création PROCESSED directe ; et `processPayment`). Élimine la duplication.

**Nouvelle méthode `processPayment(UUID paymentId, UUID userId)` (service + interface)** :
- charge le paiement ; s'il n'existe pas → `ResourceNotFoundException` ;
- si `status != SCHEDULED` → `WorkflowException("payment.already.processed")` ;
- passe `status = PROCESSED`, `processedDate = now()`, sauvegarde ;
- appelle `finalizePayment(payment, userId)` ;
- retourne `PaymentDTO`.
- `@Transactional`.

**Nouvel endpoint** `POST /api/v1/payments/{paymentId}/process` :
- `@PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")` (même rôle que `recordPayment`) ;
- `@Operation` Swagger ;
- retourne `ApiResponse.success(dto, "payment.processed.success")`.

`recordBatchPayment` : inchangé (chemin PROCESSED via `self.recordPayment` sans `scheduled`).

### 3. Rapport « cycle de paiement » (M11 #5)

**Endpoint** `GET /api/v1/reports/payment-cycle?from={ISO date}&to={ISO date}` :
- `@PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")` — **ADMIN exclu** (SoD,
  aligné sur `/cash-flow`).
- Période appliquée sur `processedDate` (paiements réellement exécutés).

**DTO `PaymentCycleReportDTO`** (record) :
- `long invoicesPaidCount` — nb de factures avec paiement PROCESSED dans la période ;
- `Double avgSubmissionToBapDays` — délai moyen soumission → BON_A_PAYER ;
- `Double avgBapToPaymentDays` — délai moyen BON_A_PAYER → paiement réel (`processedDate`) ;
- `Double avgScheduledToProcessedDays` — pour les paiements passés par SCHEDULED :
  délai moyen `paymentDate` (prévu) → `processedDate` (réel) ;
- `Double avgTotalCycleDays` — soumission → paiement réel (bout en bout).
- Les moyennes sont `null` (ou 0) si l'échantillon est vide ; à fixer explicitement à
  l'implémentation.

**Calcul** : agrégation en service Java (moyennes de `Duration` entre `Instant`), pas de
SQL exotique (évite le risque PROB-054). La source des délais inter-étapes (soumission,
BAP) s'aligne sur le mécanisme déjà utilisé par `/bottlenecks` et processing-time :
l'historique de transitions de la facture. **À l'implémentation, grep le code réel
(entité d'historique de statut, ex. InvoiceStatusHistory) avant d'écrire la requête** —
ne pas présumer du nom.

### 4. Front (frontend/src/pages/PaymentsPage.tsx)

**`RecordPaymentModal`** : ajout d'une case à cocher « Planifier ce paiement (à exécuter
plus tard) ». Décochée (défaut) → POST sans `scheduled` (PROCESSED immédiat). Cochée →
POST `scheduled: true` ; le bouton devient « Planifier le paiement ».

**Interface `Payment`** (front) : ajout `status: 'SCHEDULED' | 'PROCESSED'` et
`processedDate?: string`.

**Tableau « Historique des paiements »** : nouvelle colonne **Statut** —
- `SCHEDULED` → badge ambre « Planifié » + bouton **« Marquer exécuté »**
  (`POST /payments/{id}/process`, puis invalidation des queries `payments` +
  `invoices-bon-a-payer`) ;
- `PROCESSED` → badge vert « Exécuté ».
- Le lien avis de paiement (remittance) ne s'affiche que pour PROCESSED.

Le rapport cycle de paiement côté front : exposé sur la page Reports existante si un
emplacement naturel existe (à vérifier à l'implémentation) ; sinon noté en suivi non
bloquant dans TASKS.md §A.

### 5. i18n

**Backend** (`messages_fr.properties` = **ISO-8859-1**, ajouter via iconv, sans em-dash
ni quotes courbes ; `messages_en.properties` = UTF-8) :
- `payment.scheduled.success`, `payment.processed.success`, `payment.already.processed`,
  + libellé(s) du rapport si nécessaire.

**Front** (`fr.json` ET `en.json`, parité obligatoire) :
- `payments.scheduleThis`, `payments.status.scheduled`, `payments.status.processed`,
  `payments.markProcessed`, `payments.colStatus`.

## Tests (CLAUDE.md §8 — happy + ≥2 edge + endpoint avec rôle)

**`PaymentServiceTest`** :
- record PROCESSED direct (happy — finalisation déclenchée, comportement actuel) ;
- record SCHEDULED (pas de remittance / event / transition, facture reste BON_A_PAYER) ;
- `processPayment` happy (SCHEDULED → PROCESSED, finalisation déclenchée) ;
- `processPayment` sur paiement déjà PROCESSED (edge → `WorkflowException`) ;
- `processPayment` sur paiement inexistant (edge → `ResourceNotFoundException`).

**`PaymentControllerTest`** :
- `POST /payments/{id}/process` avec ASSISTANT_COMPTABLE (200) ;
- même endpoint avec un rôle interdit (403).

**`ReportServiceTest#paymentCycle_*`** :
- happy (plusieurs factures payées, délais calculés) ;
- période vide (moyennes null/0, count 0) ;
- au moins une facture passée par SCHEDULED (alimente `avgScheduledToProcessedDays`).

**`ReportControllerTest`** :
- `GET /reports/payment-cycle` avec DAF (200) ;
- avec ADMIN (403 — SoD).

**Front (vitest)** : `PaymentsPage` — badge statut affiché ; bouton « Marquer exécuté »
présent uniquement sur les lignes SCHEDULED.

## Gate qualité (avant le commit unique)

- `./mvnw test` : 0 échec.
- `npm run test` (vitest) : 0 échec.
- `tsc` : 0 erreur.
- Parité i18n fr/en vérifiée.
- 1 tâche = 1 commit atomique. Push dès 10 commits non poussés.

## Hors scope (noté pour décision ultérieure)

- Job `@Scheduled` d'auto-exécution des paiements planifiés échus (option écartée au
  cadrage).
- Planification en lot (batch reste immédiat).
- Annulation / re-planification d'un paiement SCHEDULED.
