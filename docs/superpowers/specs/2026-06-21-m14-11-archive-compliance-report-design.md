# M14 #11 — Rapport de conformité spécifique aux archives — Design

**Date :** 2026-06-21
**Branche :** fix/a1-cashflow-sqlgrammar
**Backlog :** docs/COMPLIANCE_MATRIX.md Module 9 (Digital Archiving), UI Element #11 (statut 🟠 → ✅)
**Réf REMEDIATION_PROMPT.md :** section C, M14

## 1. Problème

Le module conformité M14 (`ComplianceService` : incidents, checklist SOX/IFRS/LOCAL,
calendrier, statut backup, acceptation politique de confidentialité) existe, mais **ne
produit aucun rapport de conformité ciblé sur les archives documentaires**. La matrice
M9 UI #11 reste donc 🟠 : « Module conformité (M14) existe ; pas de rapport de conformité
spécifique aux archives ».

L'objectif est un **rapport agrégé en lecture seule** qui donne à l'ADMIN une vue de l'état
de conformité du dépôt d'archives, sans aucune donnée financière.

## 2. Décisions de cadrage (validées avec l'utilisateur)

1. **Accès : ADMIN uniquement, AUCUNE donnée financière.** La rétention/conformité est un
   paramètre système → ADMIN (cohérent avec B2 et la disposition de rétention). Aucun montant
   n'apparaît dans le rapport, ce qui évite tout conflit avec la séparation des pouvoirs
   (PROB-065 : les rapports *financiers* sont DAF + ASSISTANT_COMPTABLE, l'ADMIN n'y accède pas ;
   ici le rapport est purement documentaire/technique, donc ADMIN est le bon — et seul — destinataire).
2. **Contenu : 4 sections** — couverture d'archivage, intégrité (SHA-256), état de rétention
   (réutilisé de M10 #10), dispositions & versioning.
3. **Exposition : 1 endpoint agrégé en lecture seule + 1 page UI ADMIN.**
   **AUCUNE migration, AUCUNE table** (snapshot temps réel calculé à la volée).
   Pas de persistance/historique, pas d'export PDF/CSV (hors périmètre ; tâches futures possibles).

## 3. Architecture

Nouveau service léger dans le sous-domaine existant `domain/compliance`.

- **Endpoint :** `GET /api/v1/compliance/archive-report`
  → `ApiResponse<ArchiveComplianceReportDTO>`
- **Sécurité :** `@PreAuthorize("hasRole('ADMIN')")`. ADMIN uniquement.
- **Service :** `ArchiveComplianceService.generateReport()` orchestre 4 calculs et réutilise
  les services/DTO existants. Read-only (`@Transactional(readOnly = true)`).
- **Swagger :** `@Operation` sur l'endpoint du `ComplianceController` (ou un controller dédié — voir §6).
- **DTO only**, jamais d'entité JPA exposée. Réponse enveloppée dans `ApiResponse<T>`.

## 4. Contrat de données (DTO)

`ArchiveComplianceReportDTO` (record) composé de sous-records :

```
ArchiveComplianceReportDTO(
    Instant generatedAt,
    CoverageSection coverage,
    IntegritySection integrity,
    RetentionComplianceDTO retention,   // RÉUTILISÉ tel quel de M10 #10 (domain.retention.dto)
    LifecycleSection lifecycle
)

CoverageSection(
    long archivedInvoices,         // factures au statut ARCHIVE
    long archivedWithDocument,     // dont au moins 1 InvoiceDocument
    long archivedWithoutDocument,  // archivedInvoices - archivedWithDocument
    double coverageRate            // archivedWithDocument / archivedInvoices (0.0 si 0 facture)
)

IntegritySection(
    long totalDocuments,
    long withChecksum,             // checksum_sha256 présent
    long missingChecksum,          // totalDocuments - withChecksum
    double integrityRate           // withChecksum / totalDocuments (1.0 si 0 document)
)

LifecycleSection(
    long pending,                  // retention_disposition = PENDING
    long retained,                 // = RETAINED
    long purged,                   // = PURGED
    long versionedDocuments        // superseded_by_document_id IS NOT NULL (proxy versioning)
)
```

**Note d'honnêteté sur l'intégrité :** `InvoiceDocument.checksumSha256` est `NOT NULL` en base
(`@Column(nullable = false)`). En régime normal, `missingChecksum` vaut donc 0 et
`integrityRate` = 1.0. Cette section est une **preuve de conformité** (chaque archive porte son
empreinte SHA-256 d'intégrité), pas un détecteur d'anomalie. On l'assume explicitement plutôt
que de fabriquer une fausse métrique. La requête `withChecksum` teste tout de même
`checksum_sha256 IS NOT NULL AND checksum_sha256 <> ''` pour rester robuste.

## 5. Calcul des données (sans rien inventer — vérifié sur le code réel)

Tout est calculé par des requêtes `count` (aucun chargement de liste → pas de risque
`SQLGrammarException`, aucun paramètre nullable non typé, cf. PROB-038).

| Donnée | Source réelle | Implémentation |
|--------|---------------|----------------|
| `archivedInvoices` | `Invoice.status = ARCHIVE` (`InvoiceStatus.ARCHIVE` existe) | `InvoiceRepository.countByStatus(InvoiceStatus.ARCHIVE)` (nouvelle méthode dérivée) |
| `archivedWithDocument` | factures ARCHIVE ayant ≥1 `InvoiceDocument` | nouvelle `@Query` count avec jointure `DISTINCT i.id` |
| `totalDocuments` | `invoice_documents` | `InvoiceDocumentRepository.count()` (hérité de JpaRepository) |
| `withChecksum` | `checksum_sha256` non vide | nouvelle `@Query` count |
| `pending/retained/purged` | `retention_disposition` (enum `RetentionDisposition`) | `countByRetentionDisposition(...)` (×3, méthode dérivée) |
| `versionedDocuments` | `superseded_by_document_id IS NOT NULL` | `countBySupersededByDocumentIdIsNotNull()` (méthode dérivée) |
| `retention` | déjà calculé par M10 #10 | appel direct au service produisant `RetentionComplianceDTO` (RÉUTILISATION, aucun recalcul) |

**Méthodes repository à ajouter :**
- `InvoiceRepository` : `long countByStatus(InvoiceStatus status)` + une `@Query` count
  pour les factures ARCHIVE ayant un document.
- `InvoiceDocumentRepository` : `long countByRetentionDisposition(RetentionDisposition d)`,
  `long countBySupersededByDocumentIdIsNotNull()`, + `@Query` count `withChecksum`.

**Réutilisation rétention :** le `RetentionComplianceDTO` est produit aujourd'hui pour
l'écran audit (M10 #10). Le service archive l'appelle et l'imbrique tel quel — pas de duplication
de la logique de sweep/sweepOverdue/lastFlaggedCount.

## 6. Controller

Ajout d'une méthode `getArchiveComplianceReport()` au `ComplianceController` existant
(le rapport appartient au domaine conformité M14).

- `@GetMapping("/archive-report")`
- `@PreAuthorize("hasRole('ADMIN')")`
- `@Operation(summary = "...")` Swagger
- retourne `ApiResponse.success(report)`

## 7. Frontend

- **Page** `/admin/archive-compliance` (section ADMIN de la sidebar), pattern identique à
  `AdminRetentionPolicyPage` (page existante B2).
- 4 sections/cartes en **lecture seule** : Couverture, Intégrité, Rétention (badge de statut +
  réutilisation du rendu de statut M10), Cycle de vie (PENDING/RETAINED/PURGED + versionnés).
- Taux affichés en pourcentage. Accès non-ADMIN → écran refusé (pattern existant `PageRoleGuard`).
- Appel API : `GET /api/v1/compliance/archive-report`.

## 8. i18n

- **Frontend** : bloc `archiveCompliance` dans `frontend/src/i18n/fr.json` et `en.json`
  (UTF-8, accents OK, parité stricte des clés).
- **Backend** : aucune nouvelle clé attendue (rapport en lecture seule, sans message d'erreur
  métier spécifique ; les 400/403 passent par le `GlobalExceptionHandler` existant).
  Si une clé FR devenait nécessaire, elle serait ajoutée en ASCII-safe et le fichier
  `messages_fr.properties` converti via iconv (ISO-8859-1, cf. règle projet).

## 9. Tests (critère de fin : 0 échec backend, tsc 0, vitest vert)

- **Service unitaire** (`ArchiveComplianceServiceTest`) : couverture (avec/sans archives,
  division par zéro → 0.0), intégrité (rate=1.0 si 0 doc), lifecycle (les 3 dispositions +
  versionnés), agrégation `generatedAt` non nul, réutilisation du `RetentionComplianceDTO` (mocké).
- **Intégration controller** (`ComplianceControllerIT` ou IT dédié) : ADMIN → 200 + structure DTO ;
  DAF → 403 ; ASSISTANT_COMPTABLE → 403.
- **Frontend** (vitest) : page charge et affiche les 4 sections (api mocké) ; accès refusé non-ADMIN.

## 10. Mise à jour documentaire

- `docs/COMPLIANCE_MATRIX.md` : M9 UI #11 🟠 → ✅ avec preuve ; mettre à jour la ligne
  « Gaps M9 » (retirer « pas de rapport conformité archives dédié ») et le tableau de synthèse M9.
- `docs/KNOWN_ISSUES_REGISTRY.md` : uniquement si un bug réel est rencontré (sinon rien à logger).

## 11. Hors périmètre (YAGNI)

- Persistance / historique des rapports (pas de table, pas de migration V64).
- Export PDF/CSV téléchargeable (tâche future éventuelle).
- Toute donnée financière (montants) — exclue par décision SoD.
- Arborescence de dossiers (M9 #1) et contrôles de purge UI (M9 #8) — items distincts.
