# Disposition de rétention — supprimer l'« ATTENTION permanent » de la carte de conformité

**Date :** 2026-06-21
**Origine :** observation de la revue finale whole-branch de M10 #10 (raffinement de M10 #10 / rétention M9-M14)
**Branche :** fix/a1-cashflow-sqlgrammar

---

## 1. Problème & objectif

La carte « Conformité de la rétention » (M10 #10, `/admin/audit`) affiche le statut
`CONFORME / ATTENTION / NON_CONFORME`. La règle ATTENTION se déclenche notamment quand
`lastFlaggedCount > 0`. Or `DocumentRetentionJob` est **non-destructif** : il marque (audit
`RETENTION_FLAG`) tout document au-delà de l'horizon de rétention mais ne le supprime ni ne
l'archive. `lastFlaggedCount` = nombre de documents périmés trouvés au dernier balayage → dès
qu'un document dépasse son horizon, **chaque** balayage quotidien le recompte → la carte reste
en **ATTENTION en permanence**, ce qui dilue le signal (« action requise » devient indiscernable
de « la rétention fonctionne comme prévu »).

**Objectif :** un document périmé n'alimente l'alerte ATTENTION que tant qu'il est **en attente
d'une décision de disposition**. Une fois traité (conservé ou purgé), il ne compte plus → la carte
repasse CONFORME.

### Hors périmètre (YAGNI)
- Pas de suppression physique du fichier MinIO (`PURGED` marque la décision + audit ; la
  suppression effective reste une décision délibérée séparée — cohérent avec le caractère
  non-destructif déjà documenté du job).
- Pas d'écran de gestion frontend dans cette itération : endpoints API ADMIN seulement.
  La liste/gestion UI est remise à un lot ultérieur.
- Pas d'état `LEGAL_HOLD` distinct : `RETAINED` couvre la conservation délibérée.
- Aucune modification de la logique de `evaluateCompliance()` (voir §4).

---

## 2. Architecture & flux

```
DocumentRetentionJob (quotidien)
  ├─ docs uploadés avant cutoff ET retention_disposition = PENDING   ← ne recompte plus les traités
  ├─ logue RETENTION_FLAG (audit, inchangé)
  └─ recordSweep(now, count = nb de PENDING périmés)

RetentionPolicyService.evaluateCompliance()   ← INCHANGÉ
  └─ ATTENTION si lastFlaggedCount > 0  →  désormais = "périmés en attente" (s'éteint après traitement)

Disposition (nouveau, ADMIN, SoD PROB-065) :
  GET /api/v1/retention/pending-documents              → liste des docs périmés PENDING
  PUT /api/v1/retention/documents/{id}/disposition     { disposition: RETAINED|PURGED }
      → set disposition + at + by, audit RETENTION_DISPOSITION
```

Isolation : la disposition est un attribut du document (domaine `invoice`) ; l'évaluation de
conformité reste dans `retention`. On corrige la **source** du compteur, pas la règle.

---

## 3. Modèle de données & migration

### 3.1 Enum
`RetentionDisposition { PENDING, RETAINED, PURGED }` — package `domain.invoice.model`.
- `PENDING` : périmé (ou non), décision non prise. Défaut.
- `RETAINED` : conservé délibérément (valeur légale / legal-hold).
- `PURGED` : décision de suppression / archivage froid actée (marquage seul).

### 3.2 Entité `InvoiceDocument` — 3 nouveaux champs
```java
@Enumerated(EnumType.STRING)
@Column(name = "retention_disposition", nullable = false, length = 20)
@Builder.Default
private RetentionDisposition retentionDisposition = RetentionDisposition.PENDING;

@Column(name = "retention_disposition_at")
private Instant retentionDispositionAt;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "retention_disposition_by")
private User retentionDispositionBy;
```

### 3.3 Migration Flyway V63 (immutabilité — PROB-009 : nouvelle version, jamais d'édition d'une migration appliquée)
Fichier : `src/main/resources/db/migration/V63__add_retention_disposition.sql`
```sql
ALTER TABLE invoice_documents
    ADD COLUMN retention_disposition    VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN retention_disposition_at TIMESTAMP,
    ADD COLUMN retention_disposition_by UUID REFERENCES users(id);
```

Sémantique du défaut : `PENDING` sur tout le parc existant est sans danger — seuls les documents
**périmés ET PENDING** sont comptés ; un document récent est PENDING mais jamais au-delà du cutoff.

---

## 4. Job & évaluation de conformité

### 4.1 Repository `InvoiceDocumentRepository`
```java
List<InvoiceDocument> findByUploadedAtBeforeAndRetentionDisposition(
        Instant cutoff, RetentionDisposition disposition);
```
(La méthode existante `findByUploadedAtBefore` peut rester ; le job utilise la nouvelle.)

### 4.2 `DocumentRetentionJob`
Unique changement de fond — ne ramasse que les PENDING périmés :
```java
List<InvoiceDocument> expired =
    invoiceDocumentRepository.findByUploadedAtBeforeAndRetentionDisposition(
        cutoff, RetentionDisposition.PENDING);
```
Reste inchangé : log `RETENTION_FLAG` par doc, `recordSweep(now, expired.size())`.
Conséquence : `lastFlaggedCount` = périmés **non traités** → retombe à 0 quand l'ADMIN a tout
traité → carte CONFORME.

### 4.3 `evaluateCompliance()`
**Aucune modification.** La règle existante `ATTENTION si lastFlaggedCount > 0` prend le bon sens
dès que le job alimente le compteur avec les seuls PENDING.

---

## 5. Endpoints de disposition (ADMIN)

### 5.1 DTO
`RetentionPendingDocumentDTO` (record, package `domain.retention.dto`) :
```
id                   : UUID
invoiceId            : UUID
originalFilename     : String
uploadedAt           : Instant
retentionDisposition : RetentionDisposition
```

### 5.2 Service `RetentionDispositionService` (package `domain.retention.service`)
Dépend de `InvoiceDocumentRepository`, `RetentionPolicyService` (pour le seuil/horizon), `AuditService`.
```java
List<RetentionPendingDocumentDTO> listPendingExpired();
    // docs périmés (uploadedAt < cutoff calculé depuis retentionYears) ET disposition = PENDING

RetentionPendingDocumentDTO setDisposition(UUID docId, RetentionDisposition target, User actor);
    // target ∈ {RETAINED, PURGED} ; refuse PENDING → ValidationException (400) ;
    // 404 ResourceNotFoundException si doc inconnu ;
    // set retentionDisposition + retentionDispositionAt(now) + retentionDispositionBy(actor) ;
    // audit RETENTION_DISPOSITION (entité INVOICE_DOCUMENT, docId, ancienne→nouvelle valeur)
```

### 5.3 Contrôleur `RetentionDispositionController` (`/api/v1/retention`)
`@PreAuthorize("hasRole('ADMIN')")` sur chaque méthode (SoD PROB-065).
```
GET /api/v1/retention/pending-documents
    → ApiResponse<List<RetentionPendingDocumentDTO>>
PUT /api/v1/retention/documents/{id}/disposition
    body { "disposition": "RETAINED" | "PURGED" }
    → ApiResponse<RetentionPendingDocumentDTO>, message "retention.disposition.updated"
```
Requête body : record `RetentionDispositionRequest(@NotNull RetentionDisposition disposition)`.

### 5.4 i18n backend
Clé `retention.disposition.updated` dans :
- `messages_fr.properties` ⚠ ISO-8859-1 → ajout via iconv, ASCII-safe (pas d'em-dash/quotes courbes).
- `messages_en.properties` (ASCII).

### 5.5 Tests
- Service (unitaires) : `listPendingExpired` ne renvoie que périmé+PENDING ; `setDisposition`
  RETAINED et PURGED écrivent disposition+at+by et appellent l'audit ; refus de PENDING (400) ;
  404 doc inconnu.
- Contrôleur (intégration) : GET 200 ADMIN / 403 DAF ; PUT 200 ADMIN, 403 DAF, 404 doc inconnu,
  400 body invalide.

---

## 6. Carte audit & documentation

### 6.1 `RetentionComplianceCard`
Logique inchangée (consomme toujours `GET /retention-policy/compliance`, affiche `lastFlaggedCount`).
Seul ajustement i18n : libellé `admin.audit.retention.flagged` →
« Documents périmés en attente » / « Expired documents pending » (fr/en), pour refléter la
nouvelle sémantique du compteur.

### 6.2 Documentation
- `docs/COMPLIANCE_MATRIX.md` ligne 335 (M10 #10) reste ✅ ; ajouter une note : le compteur
  reflète les documents périmés **en attente de disposition** ; le statut s'éteint après traitement
  via `PUT /retention/documents/{id}/disposition`.
- Pas de PROB (amélioration de design, pas un bug).
- Pas de nouvelle ligne de matrice (raffinement de M10 #10 / rétention).

---

## 7. Critère de fin
- `./mvnw.cmd test` : 0 échec backend.
- `tsc` : 0 erreur. `vitest` : vert.
- Carte CONFORME après traitement de tous les périmés PENDING (vérifiable par la logique du job +
  test service).
