# AUDIT — Modèle du système OCT Invoice (Phase 0)

> Document produit en **Phase 0** de l'audit exhaustif (voir
> `docs/superpowers/specs/2026-07-22-audit-exhaustif-systeme-design.md`).
> Objet : construire un modèle complet du système **sans aucune modification de code**.
> Tout ce qui suit a été lu dans le code réel au 2026-07-22 (branche `audit/exhaustif-p0-comprehension`,
> partant de `main` = `c4f5e11`). Aucun finding n'est émis ici : les findings commencent en P1.
>
> Les zones d'incertitude sont regroupées en **§12 — Zones que je ne comprends pas** et
> **§13 — Informations manquantes**. Elles sont à lever en P1→P4.

---

## 1. Architecture

### 1.1 Topologie du dépôt

Le dépôt Git est **`invoice-system/`** (et non le dossier parent `FINAL PROJECT/`).

| Élément | Emplacement | Remarque |
|---|---|---|
| Backend Spring Boot | `src/main/java/com/oct/invoicesystem/` | à la racine du dépôt, **pas** dans `backend/` |
| Ressources backend | `src/main/resources/` | `db/migration/`, `i18n/` |
| Frontend React/TS | `frontend/src/` | projet Vite séparé, `package.json` propre |
| Docs | `docs/` | PRD, WORKFLOW, ARCHITECTURE, API, TASKS, KNOWN_ISSUES_REGISTRY… |
| Compose | `docker-compose.yml` | 5 services **hors** PostgreSQL (voir §1.4) |

### 1.2 Backend — Spring Boot 3.4 / Java 21

Découpage **par domaine** (`domain/<domaine>/{controller,service,model,dto,repository}`), et non par couche
technique. 18 domaines identifiés :

`access` · `announcement` · `audit` · `auth` · `checklist` · `compliance` · `department` · `invoice` ·
`mfa` · `notification` · `ocr` · `payment` · `purchasing` · `report` · `retention` · `security` ·
`storage` · `supplier` · `user` · `webhook` · `workflow`

Chiffres relevés : **40 contrôleurs**, **51 services**, **48 migrations Flyway** (V1→V48),
**~64 classes de modèle** dont 17 enums.

Éléments transverses :
- `config/StateMachineConfig.java` — machine à états Spring StateMachine (§4).
- `shared/` — `ApiResponse<T>`, `TabularExportService`, utilitaires d'export.
- 6 tâches planifiées (`@Scheduled`) : `BackupService`, `DocumentRetentionJob`, `DeadlineReminderJob`,
  `ScheduledReportJob`, `SupplierPerformanceTask`, `ConnectorSyncJob`.

### 1.3 Frontend — React 19 + TypeScript + Vite

- **56 pages** non-test sous `frontend/src/pages/` (dont 25 sous `admin/`, 5 sous `supplier/`,
  5 sous `auth/`, 2 sous `matching/`).
- Routage : `frontend/src/AppRoutes.tsx` — lazy loading systématique, 3 zones (public / staff / supplier).
- État : Redux Toolkit (`s.auth` pour l'utilisateur courant).
- i18n : `frontend/src/i18n` (react-i18next), FR par défaut + EN.
- Thème : dark mode via tokens CSS (design system « Registre », lots 1 et 2 mergés).
- Layouts : `components/layout/AppShell.tsx` + `Sidebar.tsx` (staff) et `layouts/SupplierLayout.tsx`
  (portail fournisseur, navigation totalement distincte).

### 1.4 Infrastructure

`docker-compose.yml` déclare **5 services** : `minio`, `minio_init`, `mailhog`, `backend`, `frontend`.

⚠ **PostgreSQL n'est pas dans le compose** : une instance **host-native PostgreSQL 18 sur le port 5433**
(base `oct_invoice`) doit tourner avant `docker compose up`. Le backend l'atteint via
`host.docker.internal`. C'est un point de fragilité pour la reproductibilité (voir §13).

Autres dépendances d'exécution : MinIO (stockage documents), MailHog (SMTP de dev), JWT asymétrique
(`JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` en env), `ENCRYPTION_KEY` (AES-256 pour les coordonnées bancaires).

---

## 2. Modules et frontières

| Module | Frontière fonctionnelle | Contrôleurs principaux |
|---|---|---|
| **auth** | login, refresh, inscription fournisseur, vérification email, mot de passe oublié, MFA/TOTP | `AuthController`, `SecurityPolicyController` |
| **user** | comptes, rôles, sessions actives, import/export CSV, profil | `UserController`, `RoleController`, `AdminSessionController`, `UserProfileController` |
| **department** | référentiel des départements + droits d'accès par département | `DepartmentController`, `DepartmentAccessController` |
| **supplier** | référentiel fournisseurs, contrats, communications, **portail fournisseur** | `SupplierController`, `SupplierRelationshipController`, `SupplierPortalController` |
| **purchasing** | bons de commande (PO), bons de réception (GRN), three-way matching + config | `PurchaseOrderController`, `GoodsReceiptController`, `MatchingQueryController`, `MatchingConfigController` |
| **invoice** | cycle de vie facture, documents, archivage/dossiers, import | `InvoiceController`, `InvoiceDocumentController`, `ArchiveFolderController` |
| **workflow** | approbations N1/N2, délégations, règles d'escalade, stats validateur | `ApprovalController`, `DelegationController`, `EscalationRuleController`, `ValidatorStatsController` |
| **payment** | paiements, lots, avis de règlement, règles d'alerte | `PaymentController`, `PaymentAlertRuleController` |
| **report** | KPI, aging, cash-flow, cycle de paiement, budget, report builder, exports | `ReportController` |
| **audit** | journaux système / financiers, anomalies, exports | `AuditController` |
| **compliance / retention** | incidents, checklists, calendrier, sauvegardes, politique de rétention, purge | `ComplianceController`, `BackupController`, `RetentionPolicyController`, `RetentionDispositionController` |
| **notification** | notifications in-app, email, WebSocket | `NotificationController` |
| **announcement** | annonces diffusées aux utilisateurs | `AnnouncementController` |
| **checklist** | modèles de checklist + réponses par facture | `ChecklistTemplateController`, `InvoiceChecklistController` |
| **access** | demandes d'accès utilisateur → validation admin | `AccessRequestController` |
| **webhook / integration** | webhooks HMAC, connecteurs ERP, statut d'intégration | `WebhookController`, `IntegrationConnectorController`, `IntegrationStatusController` |
| **ocr** | extraction OCR d'une facture déposée | `OcrController` |
| **security** | santé sécurité (indicateurs admin) | `SecurityHealthController` |

**Frontières notables (issues des audits SoD antérieurs) :**
- `supplier` ↔ reste du système : le portail est un contrôleur séparé (`/api/v1/supplier/**`) avec
  `@PreAuthorize("hasRole('SUPPLIER')")` **au niveau classe** + un contrôle de propriété
  (`ensureOwnInvoice`, `SupplierPortalController:287`).
- `audit` : séparation stricte journal **système** (ADMIN) / journal **financier** (DAF).
- `report` : réservé DAF + ASSISTANT_COMPTABLE, **ADMIN exclu** (séparation des devoirs).

---

## 3. Les 15 rôles

Catalogue canonique défini par `V5__seed_roles_and_admin.sql` (aucun autre rôle n'existe en base).

| # | Rôle | Compte de test | Périmètre (résumé code) |
|---|---|---|---|
| 1 | `ROLE_ADMIN` | `admin` | Utilisateurs, rôles, départements, sécurité, conformité, rétention, sauvegardes, intégrations, audit **système**, annonces, checklists, matrice de permissions. **Aucun accès financier** (factures, rapports, paiements, fournisseurs). |
| 2 | `ROLE_DAF` | `daf` | Directeur financier : audit **financier**, rapports, paiements, PO/GRN, matching + override, archive, `bon-a-payer`, rejets, règles d'escalade. |
| 3 | `ROLE_ASSISTANT_COMPTABLE` | `aa` | Saisie et traitement des factures (création, édition, soumission, resoumission), fournisseurs, PO/GRN, paiements, matching, rapports, archive, contrôle AA. |
| 4 | `ROLE_VALIDATEUR_N1_DRH` | `drh` | Validation N1 département DRH |
| 5 | `ROLE_VALIDATEUR_N1_DG` | `dg` | Validation N1 Direction Générale |
| 6 | `ROLE_VALIDATEUR_N1_INFO` | `rsi` | Validation N1 Informatique (RSI) |
| 7 | `ROLE_VALIDATEUR_N2_INFO` | `dsi` | Validation N2 Informatique (DSI) |
| 8 | `ROLE_VALIDATEUR_N1_TERM` | `dex` | Validation N1 Terminal |
| 9 | `ROLE_VALIDATEUR_N1_COM` | `com` | Validation N1 Communication & RSE |
| 10 | `ROLE_VALIDATEUR_N1_QHSSE` | `qhsse` | Validation N1 QHSSE |
| 11 | `ROLE_VALIDATEUR_N1_INFRA` | `infra` | Validation N1 Infrastructure |
| 12 | `ROLE_VALIDATEUR_N2_INFRA` | `dir_infra` | Validation N2 Infrastructure |
| 13 | `ROLE_VALIDATEUR_N1_TECH` | `atelier` | Validation N1 Atelier / Direction Technique |
| 14 | `ROLE_VALIDATEUR_N2_TECH` | `dir_tech` | Validation N2 Direction Technique |
| 15 | `ROLE_SUPPLIER` | `supplier` | Portail fournisseur uniquement : ses propres factures, son profil, ses documents. |

Mot de passe de tous les comptes de test : `Test1234!` (seed `V34__seed_test_users.sql`, corrigé par
`V43` pour l'admin et `V46`/`V47` pour supplier/aa).

**MFA obligatoire** (CLAUDE.md §9) pour `ROLE_DAF`, `ROLE_ADMIN` et tous les validateurs N1/N2.

---

## 4. Machine à états

### 4.1 États

`InvoiceStatus` (`domain/invoice/model/InvoiceStatus.java`) — **10 états**, un de plus que le workflow
documenté dans CLAUDE.md §5 :

`BROUILLON` · `SOUMIS` · **`EN_CONTROLE_AA`** · `EN_VALIDATION_N1` · `EN_VALIDATION_N2` · `VALIDE` ·
`BON_A_PAYER` · `PAYE` · `ARCHIVE` · `REJETE`

⚠ `EN_CONTROLE_AA` (étape de contrôle par l'assistant comptable) a été introduit après la rédaction de
CLAUDE.md §5 — **CLAUDE.md est en retard sur le code**. Le flux réel passe par cette étape.

État initial : `BROUILLON`. État final : `ARCHIVE`.

### 4.2 Transitions et gardes (`config/StateMachineConfig.java`)

| # | Source → Cible | Événement | Garde(s) | Ligne |
|---|---|---|---|---|
| 1 | BROUILLON → SOUMIS | `SUBMIT` | `documentRequiredGuard` | 52-56 |
| 2 | SOUMIS → EN_CONTROLE_AA | `ASSIGN_AA` | `roleMatchGuard` | 58-62 |
| 3 | EN_CONTROLE_AA → EN_VALIDATION_N1 | `ASSIGN_REVIEWER` | `roleMatchGuard` | 64-68 |
| 4 | EN_CONTROLE_AA → REJETE | `REJECT` | `rejectionReasonGuard` ∧ `roleMatchGuard` | 70-74 |
| 5 | EN_VALIDATION_N1 → EN_VALIDATION_N2 | `VALIDATE_N1` | `departmentTransitionGuard.requiresN2` ∧ `roleMatchGuard` | 76-80 |
| 6 | EN_VALIDATION_N1 → VALIDE | `VALIDATE_N1` | `departmentTransitionGuard.isSingleLevel` ∧ `roleMatchGuard` | 82-86 |
| 7 | EN_VALIDATION_N2 → VALIDE | `VALIDATE_N2` | `roleMatchGuard` | 88-92 |
| 8 | VALIDE → BON_A_PAYER | `BON_A_PAYER` | `roleMatchGuard` | 94-98 |
| 9 | BON_A_PAYER → PAYE | `RECORD_PAYMENT` | **aucune garde** | 100-103 |
| 10 | PAYE → ARCHIVE | `ARCHIVE` | **aucune garde** | 105-108 |
| 11 | EN_VALIDATION_N1 → REJETE | `REJECT` | `rejectionReasonGuard` ∧ `roleMatchGuard` | 110-114 |
| 12 | EN_VALIDATION_N2 → REJETE | `REJECT` | idem | 116-120 |
| 13 | VALIDE → REJETE | `REJECT` | idem | 122-126 |
| 14 | REJETE → SOUMIS | `RESUBMIT` | `resubmissionVersionGuard` | 128-132 |

**Gardes** (`domain/workflow/guard/`) : `DocumentRequiredGuard` (au moins un document joint),
`RoleMatchGuard` (le rôle de l'acteur correspond à l'étape), `DepartmentTransitionGuard` (routage
N1→N2 selon le département), `RejectionReasonGuard` (motif de rejet obligatoire),
`ResubmissionVersionGuard` (la facture a bien été modifiée depuis le rejet — `versionAtRejection`, V48).

**Observations (à instruire en P1, pas des findings ici) :**
- Les transitions 9 et 10 (`RECORD_PAYMENT`, `ARCHIVE`) n'ont **aucune garde** au niveau machine à
  états ; le contrôle repose entièrement sur les `@PreAuthorize` des contrôleurs.
- Il n'existe **pas** de transition `SOUMIS → EN_VALIDATION_N1` directe : tout passe par
  `EN_CONTROLE_AA`.
- Aucune transition ne sort de `ARCHIVE` (état terminal) ni de `REJETE` autre que `RESUBMIT`.

### 4.3 Routage N1 → N2 par département (CLAUDE.md §5, confirmé par `DepartmentTransitionGuard`)

- **Deux niveaux (N1 → N2)** : Informatique (RSI→DSI), Infrastructure (Resp.→Directeur),
  Atelier/Direction Technique (Resp.→Directeur).
- **Un niveau (N1 seul)** : DRH, Direction Générale, Finance, Terminal, Communication & RSE, QHSSE.

---

## 5. Matrice de séparation des devoirs (SoD)

Reconstituée depuis les `@PreAuthorize` réels (§8) et les `PageRoleGuard` frontend (§10).
✅ = autorisé · ❌ = explicitement interdit · — = hors périmètre du rôle

| Capacité | ADMIN | DAF | AA | Valid. N1 | Valid. N2 | SUPPLIER |
|---|---|---|---|---|---|---|
| Voir la liste des factures | ❌ | ✅ | ✅ | ✅ | ✅ | ses factures |
| Créer / éditer / supprimer une facture | ❌ | ❌ (delete ✅) | ✅ | ❌ | ❌ | crée les siennes |
| Soumettre / resoumettre | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ (les siennes) |
| Contrôle AA (`assign-aa`) | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Valider N1 | ❌ | ✅ | ❌ | ✅ | ❌ | ❌ |
| Valider N2 | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Bon à payer | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Rejeter | ❌ | ✅ | ✅ | ✅ | ✅ | ❌ |
| Paiements (enregistrer / traiter) | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| Paiements (consulter / exporter) | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Rapports & KPI | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Audit **système** | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Audit **financier** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Fournisseurs (référentiel) | ✅ (partiel) | ❌ | ✅ | ❌ | ❌ | son profil |
| PO / GRN | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Matching (consulter) | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Matching (override) | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Archive / dossiers | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| Utilisateurs, rôles, sessions | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Sécurité, conformité, rétention, sauvegardes, intégrations | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Règles d'escalade | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

**Principe directeur validé** : `ROLE_ADMIN` est un administrateur **technique** — il n'a aucun accès
aux données financières. Toute apparition d'un accès financier pour ADMIN est un **finding**, pas une
fonctionnalité.

⚠ **Incohérence apparente relevée en lecture** (à instruire en P1, pas conclue ici) :
`SupplierController` autorise `hasAnyRole('ADMIN','ASSISTANT_COMPTABLE')` sur la plupart des endpoints
(création, édition, activation, suspension, export, documents), alors que la Sidebar et les
`PageRoleGuard` frontend réservent explicitement les fournisseurs à `ROLE_ASSISTANT_COMPTABLE`
(commentaire N16 dans `Sidebar.tsx:196`). Le backend semble donc plus permissif que le frontend.

---

## 6. Flux métier

### 6.1 Soumission d'une facture — deux points d'entrée

**(a) Voie interne (assistant comptable)**
1. `POST /api/v1/invoices` (AA) → facture `BROUILLON`.
2. `POST /api/v1/invoices/{id}/documents` — pièce jointe obligatoire (`DocumentRequiredGuard`).
3. `POST /api/v1/invoices/{id}/submit` → `SOUMIS`. Le three-way matching se déclenche automatiquement
   si `purchaseOrderId` est renseigné (CLAUDE.md §9).
4. Contrôle de doublon disponible : `GET /api/v1/invoices/duplicate-check`.

**(b) Voie portail fournisseur**
1. `POST /api/v1/supplier/invoices` — le fournisseur crée sa facture (`BROUILLON`).
2. `POST /api/v1/supplier/invoices/{id}/documents`.
3. `POST /api/v1/supplier/invoices/{id}/submit` → `SOUMIS`.
4. En cas de rejet : `POST /api/v1/supplier/invoices/{id}/resubmit`.

### 6.2 Contrôle AA puis validation N1/N2

1. `POST /api/v1/invoices/{id}/workflow/assign-aa` (AA) → `EN_CONTROLE_AA`.
2. `POST …/assign` — auto-assignation du relecteur → `EN_VALIDATION_N1`.
3. `POST …/validate-n1` (validateur N1 du département, ou DAF) :
   - département à deux niveaux → `EN_VALIDATION_N2` ;
   - département à un niveau → `VALIDE`.
4. `POST …/validate-n2` (validateur N2) → `VALIDE`.
5. `POST …/bon-a-payer` (DAF) → `BON_A_PAYER`.
6. À tout moment : `POST …/reject` (motif obligatoire) → `REJETE`, puis `RESUBMIT` → `SOUMIS`.

Mécanismes de soutien : délégations d'approbation (`/approvals/delegations`), règles d'escalade
(`/escalation-rules` + `DeadlineReminderJob`), checklists de validation par facture.

### 6.3 Three-way matching

Rapprochement **facture ↔ bon de commande (PO) ↔ bon de réception (GRN)**.
- Déclenchement automatique à la soumission si `purchaseOrderId` est présent.
- Seuils de tolérance en base (`matching_config`, seed `V40`), pas en dur.
- Résultat `ThreeWayMatchingResult` **append-only** ; résolutions ligne à ligne
  (`ThreeWayMatchingLineResolution`, V38/V41/V42, append-only + anti-truncate).
- Statut `MISMATCH` → blocage au-delà de `SOUMIS` sans override.
- Override : `POST /api/v1/invoices/{id}/matching/override` — **DAF uniquement** dans le code
  (CLAUDE.md §9 mentionne « DAF ou ADMIN » : divergence doc/code, à instruire en P1).
- Consultation : `GET /api/v1/matching`, `GET /api/v1/matching/{invoiceId}/lines`.
- Résolution d'une ligne : `POST /api/v1/matching/{invoiceId}/lines/{poLineId}/resolve` (DAF).

### 6.4 Paiement

1. `POST /api/v1/payments/invoice/{invoiceId}` (AA) — enregistre un paiement.
2. `POST /api/v1/payments/batch` (AA) — paiement par lot.
3. `POST /api/v1/payments/{paymentId}/process` (AA) — `SCHEDULED` → `PROCESSED`.
4. Transition facture `BON_A_PAYER → PAYE` (`RECORD_PAYMENT`).
5. Avis de règlement : `GET /api/v1/payments/{paymentId}/remittance`.
6. Règles d'alerte de paiement : `/api/v1/payment-alert-rules` (DAF + AA).

### 6.5 Archivage et rétention

1. Transition `PAYE → ARCHIVE`.
2. Dossiers d'archive : `GET/POST /api/v1/archive/folders`, classement
   `PATCH /api/v1/archive/invoices/{id}/folder`.
3. Politique de rétention : `/api/v1/retention-policy` (ADMIN, singleton en base).
4. Disposition : `PENDING` → `RETAINED` / `PURGED` (`/api/v1/retention/documents/{id}/disposition`).
5. `DocumentRetentionJob` applique la politique ; rapport de conformité
   `GET /api/v1/retention-policy/compliance` et `GET /api/v1/compliance/archive-report`.
6. Règle CLAUDE.md §3 : les enregistrements financiers sont en **soft delete** uniquement
   (`deleted_at`), garanti aussi côté base par `V33__enforce_financial_retention.sql`.

---

## 7. Dépendances

### 7.1 Techniques

| Dépendance | Rôle | Point de rupture |
|---|---|---|
| PostgreSQL 18 (host, port 5433) | base de données | **hors compose** — doit être démarré à la main |
| Flyway | migrations V1→V48 | checksum verrouillé : jamais modifier une migration appliquée (PROB-009) |
| MinIO | stockage des documents | `MinioStorageService`, bucket `oct-invoices` |
| MailHog | SMTP de dev | `EmailService`, notifications |
| Spring StateMachine | workflow facture | `StateMachineConfig` |
| Spring Security + JWT (RSA) | authentification | clés en variables d'environnement |
| iText 8 | génération PDF | pièges connus PROB-012/013 |
| Apache Tika | validation MIME des fichiers | obligatoire avant stockage |
| WebSocket / STOMP | notifications temps réel | JWT sur la poignée de main |
| react-pdf 9.2.1 | visionneuse de documents | zoom/rotation (C3) |

### 7.2 Fonctionnelles (ordre de dépendance métier)

`Département` → `Utilisateur` (rattachement) → `Fournisseur` → `PO` → `GRN` → `Facture` →
`Matching` → `Workflow d'approbation` → `Paiement` → `Archive` → `Rétention/Purge`.

Le journal d'audit (`audit_logs`, append-only via `V32`) et les notifications sont transverses à
toutes ces étapes.

---

## 8. API — endpoints par domaine

Carte complète extraite du code (méthode HTTP · chemin · `@PreAuthorize` réel).
`base` = `@RequestMapping` de la classe. Les contrôleurs annotés au **niveau classe** sont signalés.

### auth — `/api/v1/auth`
| Méthode | Chemin | Autorisation |
|---|---|---|
| POST | `/login` | `permitAll()` |
| POST | `/refresh` | `permitAll()` |
| POST | `/register/supplier` | `permitAll()` |
| GET | `/verify-email` | `permitAll()` |
| POST | `/forgot-password` | `permitAll()` |
| POST | `/reset-password` | `permitAll()` |
| POST | `/mfa/setup` | `isAuthenticated()` |
| POST | `/mfa/confirm` | `isAuthenticated()` |
| POST | `/mfa/validate` | `permitAll()` |

### user — `/api/v1/users` (⚠ `@PreAuthorize("hasRole('ADMIN')")` **au niveau classe**)
`GET /` · `GET /{id}` · `POST /` · `PUT /{id}` · `PATCH /{id}/activate` · `PUT /{id}/roles` ·
`POST /{id}/unlock` · `POST /{id}/mfa/reset` · `GET /export/csv` · `GET /export` · `POST /import/csv`

### rôles / sessions / profil / sécurité
| Endpoint | Autorisation |
|---|---|
| `GET /api/v1/roles` | ADMIN (niveau classe) |
| `GET /api/v1/admin/sessions` · `DELETE /user/{userId}` | ADMIN |
| `GET`/`PUT /api/v1/profile` | `isAuthenticated()` |
| `GET`/`PUT /api/v1/admin/security-policy` | ADMIN |
| `GET /api/v1/admin/security-health` | ADMIN |
| `GET /api/v1/admin/department-access` | ADMIN |

### invoice — `/api/v1/invoices`
| Méthode | Chemin | Autorisation |
|---|---|---|
| GET | `/` | authentifié, **ni SUPPLIER ni ADMIN** |
| GET | `/export` | authentifié, ni SUPPLIER ni ADMIN |
| GET | `/{id}` | authentifié, ni SUPPLIER ni ADMIN |
| GET | `/duplicate-check` | `ASSISTANT_COMPTABLE`, `SUPPLIER` |
| GET | `/pending-validation` | DAF, AA + tous les validateurs N1/N2 |
| GET | `/{id}/matching` · `/{id}/matching/export` · `/{id}/history` · `/{id}/export/pdf` | authentifié, ni SUPPLIER ni ADMIN |
| POST | `/` · `/import` | `ASSISTANT_COMPTABLE` |
| PUT | `/{id}` | `ASSISTANT_COMPTABLE` |
| PATCH | `/{id}/sensitivity` | DAF, AA |
| DELETE | `/{id}` | `ASSISTANT_COMPTABLE` |
| POST | `/{id}/submit` · `/{id}/resubmit` | `ASSISTANT_COMPTABLE` |
| POST | `/{id}/matching/override` | **DAF** |
| GET | `/archive` | DAF, AA |

### invoice — documents & archive
| Endpoint | Autorisation |
|---|---|
| `POST /api/v1/invoices/{invoiceId}/documents` · `/bulk` | `ASSISTANT_COMPTABLE` |
| `GET …/documents` · `GET …/{docId}/download` | authentifié, non-SUPPLIER |
| `GET /api/v1/archive/folders` | DAF, AA |
| `POST`/`PUT`/`DELETE /api/v1/archive/folders…` | ADMIN |
| `PATCH /api/v1/archive/invoices/{invoiceId}/folder` | DAF, AA |

### workflow — `/api/v1/invoices/{invoiceId}/workflow`
| Endpoint | Autorisation |
|---|---|
| `GET /rejection-reasons` · `GET /steps` | authentifié, non-SUPPLIER |
| `POST /assign` | AA, DAF + tous validateurs N1/N2 |
| `POST /assign-aa` | `ASSISTANT_COMPTABLE` |
| `POST /validate-n1` | DAF + tous validateurs **N1** |
| `POST /validate-n2` | validateurs **N2** (INFO, INFRA, TECH) |
| `POST /bon-a-payer` | **DAF** |
| `POST /reject` | DAF, AA + tous validateurs N1/N2 |

### workflow — délégations & escalades
`/api/v1/approvals/delegations` : `POST /` et `GET /` → ADMIN ; `POST|GET /mine`,
`/eligible-delegatees`, `DELETE /mine/{id}` → `APPROVER_ROLES` (constante) ; `DELETE /{id}` → ADMIN.
`/api/v1/escalation-rules` : GET/POST/PUT/DELETE → ADMIN, DAF.
`/api/v1/workflow/my-stats` : authentifié non-SUPPLIER.

### purchasing
| Endpoint | Autorisation |
|---|---|
| `POST /api/v1/purchase-orders` · `PUT /{id}` · `DELETE /{id}` | `ASSISTANT_COMPTABLE` |
| `GET /api/v1/purchase-orders` · `/{id}` | AA, DAF |
| `POST /api/v1/goods-receipts` | `ASSISTANT_COMPTABLE` |
| `GET /api/v1/goods-receipts` · `/{id}` | AA, DAF |
| `GET /api/v1/matching` · `/{invoiceId}/lines` | AA, DAF |
| `POST /api/v1/matching/{invoiceId}/lines/{poLineId}/resolve` | **DAF** |
| `GET /api/v1/matching-config` | ADMIN, AA |
| `POST /api/v1/matching-config` | **ADMIN** |

### payment
`POST /invoice/{invoiceId}` · `/batch` · `/{paymentId}/process` → `ASSISTANT_COMPTABLE`.
`GET /invoice/{invoiceId}` · `GET /` · `/{paymentId}/remittance` · `/export` → AA, DAF.
`/api/v1/payment-alert-rules` (GET/POST/PUT/DELETE) → DAF, AA.

### report — `/api/v1/reports` (**tous** DAF + AA, ADMIN exclu)
`/kpis` · `/activity` · `/export/excel` · `/export/pdf/audit/{id}` · `/export/pdf/compliance` ·
`/aging` · `/aging/buckets` · `/cash-flow` · `/payment-cycle` · `/supplier/{id}/payments` ·
`/bottlenecks` · `/supplier/{id}/performance` · `/budget-vs-actual` · `/budget-alerts` ·
`/definitions` (GET/POST/DELETE) · `/definitions/{id}/run` · `/definitions/{id}/preview` ·
`/volume-trend` · `/executive-summary`

### audit — `/api/v1/audit-logs`
| Endpoint | Autorisation |
|---|---|
| `GET /system` · `/anomalies` · `/summary/system` | **ADMIN** |
| `GET /financial` · `/summary/financial` | **DAF** |
| `GET /` · `/export` · `/summary/export` | ADMIN **ou** DAF |

### supplier (back-office) — `/api/v1/suppliers`
`POST /` · `PUT /{id}` · `GET /{id}` · `GET /` · `GET /export` · `PATCH /{id}/activate` ·
`PATCH /{id}/suspend` · `GET|POST /{id}/documents` → **ADMIN, AA** ;
`DELETE /{id}` → **ADMIN** ; `GET /{id}/performance` → **AA, DAF**.
`/api/v1/suppliers/{supplierId}/contracts|communications` → ADMIN, AA.

### supplier — portail `/api/v1/supplier` (⚠ `hasRole('SUPPLIER')` **au niveau classe**, ligne 53)
`POST /invoices` · `GET /invoices` · `POST /invoices/{id}/submit` · `POST /invoices/{id}/resubmit` ·
`POST /invoices/{id}/documents` · `GET|PUT /profile` · `GET /dashboard` · `POST|GET /documents`

> **Aucun endpoint de lecture des bons de commande** dans ce contrôleur — origine d'**AUDIT-001**.

### compliance / retention / backup
`/api/v1/compliance/**` → ADMIN, sauf `POST /incidents` (authentifié non-SUPPLIER) et
`GET|POST /privacy-acceptance` (authentifié). `/api/v1/backups/**` → ADMIN.
`/api/v1/retention-policy/**` et `/api/v1/retention/**` → ADMIN.

### divers
`/api/v1/notifications/**` → authentifié. `/api/v1/announcements` GET → authentifié, le reste ADMIN.
`/api/v1/departments` GET → authentifié, écritures ADMIN. `/api/v1/checklist-templates/**` → ADMIN ;
`/api/v1/invoices/{id}/checklist` → authentifié non-SUPPLIER. `/api/v1/access-requests` :
POST + `GET /mine` → authentifié non-SUPPLIER, `GET /` + `PATCH /{id}` → ADMIN.
`/api/v1/ocr/extract` → SUPPLIER, AA. `/api/v1/integrations/**` → ADMIN.

---

## 9. Schéma de base de données — entités clés

48 migrations (V1→V48). Entités principales par domaine :

| Table / entité | Migration | Points saillants |
|---|---|---|
| `departments` | V1 | référentiel, drapeau « deux niveaux d'approbation » |
| `suppliers` | V2, V24 | coordonnées bancaires **AES-256**, soft delete, statut `PENDING_VERIFICATION→ACTIVE→SUSPENDED` |
| `users` | V3, V34, V43, V46, V47 | `mfa_enabled`/`mfa_verified` obligatoires à tout seed (leçon PROB-097) |
| `roles`, `user_roles` | V4, V5 | catalogue figé de 15 rôles |
| `purchase_orders`, `purchase_order_items` | V6 | source du three-way matching |
| `goods_receipts`, `goods_receipt_items` | V7 | réception physique |
| `invoices` | V8, V35, V37, V48 | `purchase_order_id` = **UUID plat**, `matching_status`, `version_at_rejection`, soft delete |
| `invoice_items` | V10 | lignes de facture |
| `invoice_documents` | V11 | pièces jointes MinIO + disposition de rétention |
| `invoice_status_history` | V12 | historique des transitions |
| `matching`, `matching_line_resolutions` | V9, V38, V40, V41, V42 | **append-only**, anti-truncate, config des seuils |
| `notifications` | V13 | in-app |
| `payments` | V14, V35 | statut `SCHEDULED`/`PROCESSED` |
| `audit_logs` | V15, V32 | **append-only** (contrainte base) |
| `webhooks`, `webhook_deliveries` | V16 | HMAC-SHA256, journal append-only |
| `approval_steps`, `approval_delegations` | V17, V19 | étapes N1/N2, délégations |
| `active_sessions` | V18 | sessions révocables par l'admin |
| `security_policy` | V20 | singleton |
| `access_requests` | V21 | demandes d'accès |
| `document_access_log` | V22 | traçabilité des consultations |
| `announcements` | V23 | annonces |
| `report_definitions` | V25 | report builder |
| `integration_connectors` | V26, V44 | config **chiffrée** |
| `payment_alert_rules`, `escalation_rules` | V27, V28 | automatisation |
| `retention_policy` | V29 | singleton |
| conformité (incidents, checklist, calendrier, sauvegardes, acceptation vie privée) | V30, V39 | |
| `validation_checklists` | V31 | modèles + réponses |
| `archive_folders` | V36, V37 | arborescence d'archivage |

Migrations correctives notables : **V33** (rétention financière : interdiction de suppression dure),
**V45** (normalisation de la devise `XOF` → **`XAF`**), **V47** (SoD AA/DAF).

⚠ **Devise du système = `XAF`** (Franc CFA BEAC, Gabon/CEMAC). Toute occurrence de `XOF` est une
régression.

---

## 10. Inventaire des 56 pages frontend

Source : `AppRoutes.tsx` (route) + `PageRoleGuard allowedRoles` dans chaque page + visibilité
dans `Sidebar.tsx`.
⚠ Les routes elles-mêmes **ne portent aucune restriction de rôle** : `ProtectedRoute` vérifie
seulement l'authentification (staff) et `SupplierRoute` la qualité de fournisseur. La restriction
par rôle est portée **par la page**. Une page sans `PageRoleGuard` est donc accessible à tout
utilisateur staff authentifié qui connaît l'URL.

### 10.1 Pages publiques (6)
| Page | Route |
|---|---|
| `LoginPage` | `/login` |
| `auth/RegisterPage` | `/register` |
| `auth/ForgotPasswordPage` | `/forgot-password` |
| `auth/ResetPasswordPage` | `/reset-password` |
| `auth/SupplierRegisterPage` | `/register/supplier` |
| `auth/EmailVerificationPage` | `/verify-email` |

### 10.2 Pages staff (45)
| Page | Route | `PageRoleGuard` | Visible dans la sidebar pour |
|---|---|---|---|
| `DashboardPage` | `/dashboard` | **aucun** | tous |
| `ProfilePage` | `/profile` | **aucun** | tous |
| `MyAccessRequestsPage` | `/access-requests` | **aucun** | tous |
| `NotificationsPage` | `/notifications` | **aucun** | tous |
| `MyDelegationsPage` | `/my-delegations` | **aucun** | DAF + validateurs |
| `InvoiceListPage` | `/invoices` | `ALLOWED_ROLES` (DAF, AA, tous validateurs) | idem |
| `InvoiceCreatePage` | `/invoices/new` | `ROLE_ASSISTANT_COMPTABLE` | — |
| `InvoiceDetailPage` | `/invoices/:id` | **aucun** | — |
| `ApprovalQueuePage` | `/approvals` | `ALLOWED_ROLES` (DAF + validateurs) | DAF + validateurs |
| `FinancialAuditPage` | `/financial-audit` | `ROLE_DAF` | DAF |
| `PurchaseOrdersPage` | `/purchase-orders` | AA, DAF | AA, DAF |
| `GoodsReceiptsPage` | `/goods-receipts` | AA, DAF | AA, DAF |
| `PaymentsPage` | `/payments` | AA, DAF | AA, DAF |
| `PaymentAlertRulesPage` | `/payments/alert-rules` | DAF, AA | — (pas d'entrée) |
| `ReportsPage` | `/reports` | DAF, AA | DAF, AA |
| `ReportBuilderPage` | `/reports/builder` | DAF, AA | DAF, AA |
| `ArchivePage` | `/archive` | DAF, AA | AA, DAF |
| `matching/MatchingListPage` | `/matching` | AA, DAF | AA, DAF |
| `matching/MatchingDetailPage` | `/matching/:invoiceId` | AA, DAF | — |
| `NotFoundPage` | `*` | — | — |
| `admin/AdminUsersPage` | `/admin/users` | ADMIN | ADMIN |
| `admin/AdminUserFormPage` | `/admin/users/new` | ADMIN | — |
| `admin/AdminPermissionMatrixPage` | `/admin/permissions` | ADMIN | ADMIN |
| `admin/AdminAccessRequestsPage` | `/admin/access-requests` | ADMIN | ADMIN |
| `admin/AdminAnnouncementsPage` | `/admin/announcements` | ADMIN | ADMIN |
| `admin/AdminCompliancePage` | `/admin/compliance` | ADMIN | ADMIN |
| `admin/AdminDepartmentsPage` | `/admin/departments` | ADMIN | ADMIN |
| `admin/AdminDepartmentFormPage` | `/admin/departments/new` | ADMIN | — |
| `admin/AdminAuditPage` | `/admin/audit` | ADMIN | ADMIN |
| `admin/ApprovalMatrixPage` | `/admin/approval-matrix` | ADMIN | ADMIN |
| `admin/AdminDelegationsPage` | `/admin/delegations` | ADMIN | ADMIN |
| `admin/AdminMatchingConfigPage` | `/admin/matching-config` | ADMIN | ADMIN |
| `admin/AdminChecklistTemplatesPage` | `/admin/checklist-templates` | ADMIN | ADMIN |
| `admin/EscalationRulesPage` | `/admin/escalation-rules` | ADMIN, DAF | ADMIN (imbriqué) |
| `admin/AdminRetentionPolicyPage` | `/admin/retention-policy` | ADMIN | ADMIN |
| `admin/AdminArchiveCompliancePage` | `/admin/archive-compliance` | ADMIN | ADMIN |
| `admin/AdminRetentionDispositionPage` | `/admin/retention-disposition` | ADMIN | ADMIN |
| `admin/AdminBackupsPage` | `/admin/backups` | ADMIN | ADMIN |
| `admin/SecuritySettingsPage` | `/admin/security` | ADMIN | ADMIN |
| `admin/IntegrationsPage` | `/admin/integrations` | ADMIN | ADMIN |
| `admin/DepartmentAccessPage` | `/admin/department-access` | ADMIN | ADMIN |
| `admin/SuppliersPage` | `/admin/suppliers` | **`ROLE_ASSISTANT_COMPTABLE`** | AA seulement |
| `admin/SupplierOnboardingPage` | `/admin/suppliers/new` | `ROLE_ASSISTANT_COMPTABLE` | — |
| `admin/SupplierDetailPage` | `/admin/suppliers/:id` | `ROLE_ASSISTANT_COMPTABLE` | — |
| `admin/SupplierFormPage` | `/admin/suppliers/:id/edit` | `ROLE_ASSISTANT_COMPTABLE` | — |

### 10.3 Pages portail fournisseur (5)
| Page | Route | Accès | Visible dans la nav fournisseur |
|---|---|---|---|
| `supplier/SupplierDashboardPage` | `/supplier/dashboard` | `SupplierRoute` | ✅ |
| `supplier/SupplierInvoicesPage` | `/supplier/invoices` | `SupplierRoute` | ✅ |
| `supplier/SupplierInvoiceSubmitPage` | `/supplier/invoices/new` | `SupplierRoute` | — |
| `supplier/SupplierProfilePage` | `/supplier/profile` | `SupplierRoute` | ✅ |
| `supplier/SupplierDocumentsPage` | `/supplier/documents` | `SupplierRoute` | ✅ |

**Total : 6 + 45 + 5 = 56 pages.**

### 10.4 Pages sans garde de rôle explicite (à instruire en P1/P3)
`DashboardPage`, `ProfilePage`, `MyAccessRequestsPage`, `NotificationsPage`, `MyDelegationsPage`,
`InvoiceDetailPage`. Les cinq premières sont plausiblement voulues (communes à tout le staff) ;
**`InvoiceDetailPage` est la seule surprise** — page de détail financier sans `PageRoleGuard`, alors
que `InvoiceListPage` en a un. La protection réelle vient du backend
(`!hasRole('SUPPLIER') and !hasRole('ADMIN')`), à confirmer en runtime P3.

---

## 11. Comptes de test (rappel opérationnel P2/P3)

Tous : mot de passe **`Test1234!`**.
`admin` · `daf` · `aa` · `drh` · `dg` · `rsi` (N1 INFO) · `dsi` (N2 INFO) · `dex` (N1 TERM) ·
`com` · `qhsse` · `infra` (N1 INFRA) · `dir_infra` (N2 INFRA) · `atelier` (N1 TECH) ·
`dir_tech` (N2 TECH) · `supplier`.

⚠ MFA obligatoire pour ADMIN / DAF / validateurs : prévoir l'enrôlement TOTP ou des comptes à MFA
vierge avant les phases runtime.

---

## 12. Zones que je ne comprends pas (à lever en P1→P4)

1. **`EN_CONTROLE_AA` vs la documentation.** CLAUDE.md §5 et `docs/WORKFLOW.md` décrivent
   `SOUMIS → EN_VALIDATION_N1` sans étape intermédiaire, alors que le code impose
   `SOUMIS → EN_CONTROLE_AA → EN_VALIDATION_N1`. Je ne sais pas si la doc est simplement en retard
   ou si l'étape AA a été ajoutée sans mise à jour du cahier des charges.
2. **Qui valide N1 exactement.** `POST /validate-n1` autorise **`hasRole('DAF')`** en plus des
   validateurs N1. Je ne comprends pas si le DAF est un validateur N1 universel de secours ou si
   c'est un reliquat. Même question pour `POST /reject` ouvert à AA + DAF + tous les validateurs.
3. **Rôle du DAF dans `assign-aa`.** L'endpoint est réservé à `ASSISTANT_COMPTABLE` alors que le
   commentaire de la spec V47 évoquait un cumul AA/DAF. Le compte `daf` de la base de dev cumulait
   historiquement AA+DAF sans que ce cumul figure dans une migration (mémoire
   `dev-db-drifts-from-migrations`) — impossible de trancher sans regarder la base en P3.
4. **Divergence backend/frontend sur les fournisseurs.** Le backend ouvre `/api/v1/suppliers/**` à
   ADMIN + AA ; le frontend réserve la page à AA seul avec un commentaire explicite « l'admin a zéro
   surface fournisseur » (N16). Lequel fait foi ? Impact SoD direct.
5. **Override de matching : DAF seul ou DAF+ADMIN ?** Le code dit DAF seul
   (`InvoiceController:322`), CLAUDE.md §9 dit « DAF ou ADMIN ». Divergence à trancher.
6. **`APPROVER_ROLES` dans `DelegationController`.** Constante non résolue lors de l'extraction ;
   je ne connais pas encore sa composition exacte.
7. **`DepartmentTransitionGuard`.** Je sais qu'il route N1→N2, mais je n'ai pas lu comment il
   détermine « deux niveaux » (drapeau en base sur `departments` ou liste en dur ?). Point sensible
   pour la conformité au cahier des charges.
8. **Absence de garde sur `RECORD_PAYMENT` et `ARCHIVE`.** Ces deux transitions n'ont aucune garde
   de machine à états. Je ne sais pas si un contrôle équivalent existe en amont dans
   `PaymentService`/`InvoiceService`.
9. **Portail fournisseur et matching.** Le fournisseur saisit `purchaseOrderId` sans jamais voir la
   liste des PO (AUDIT-001). Je ne sais pas encore ce qui se passe côté runtime quand il saisit un
   UUID inexistant ou celui d'un PO d'un autre fournisseur — contrôle de propriété non vu dans le code lu.
10. **Page `/payments/alert-rules` sans entrée de navigation.** Route et garde existent, mais aucune
    entrée dans la Sidebar : accessible uniquement par URL directe. Volontaire ou oubli ?
11. **`InvoiceDetailPage` sans `PageRoleGuard`** alors que toutes ses sœurs financières en ont un.

---

## 13. Informations manquantes (à collecter avant P2/P3)

1. **État runtime** : Docker éteint, PostgreSQL host non vérifié. Rien n'a été exécuté en P0 — les
   phases P2/P3 supposent la pile relancée (`docker compose up -d` + PostgreSQL 18 port 5433).
2. **État MFA réel des comptes de test** : selon la mémoire projet, `admin`/`aa`/`daf` avaient été
   laissés à MFA vierge pour les captures. À revérifier en base avant P2, sinon les connexions
   bloqueront.
3. **Jeu de données** : volume et cohérence des factures/PO/GRN seedés inconnus. Un audit fonctionnel
   sérieux (P3) a besoin d'au moins une facture dans chaque état de la machine.
4. **Suite de tests** : nombre de tests backend et frontend au vert non revérifié en P0 (aucun test
   lancé, aucun code touché). Baseline à établir au début de P1.
5. **`docs/API.md` vs code** : la carte d'API de §8 vient du code ; l'écart avec `docs/API.md` n'a pas
   été mesuré. C'est un travail de P1/P4 (cohérence documentaire).
6. **Couverture i18n** : nombre de clés FR/EN et clés manquantes non mesurés (P4). Rappel : 
   `src/main/resources/i18n/messages_fr.properties` est en **ISO-8859-1 / ASCII `\uXXXX`** — jamais
   d'append UTF-8 brut.
7. **Composants frontend hors `pages/`** : les composants partagés (`components/**`) n'ont pas été
   inventoriés en P0 ; le code mort et les doublons relèvent de P1.
8. **Performance** : aucune mesure (N+1, requêtes lentes, taille du bundle) — P4 avec chrome-devtools MCP.
9. **Accessibilité** : aucune mesure ; P2/P4 par injection manuelle d'axe-core via Playwright.

---

## 14. Gate de Phase 0

- [x] `docs/AUDIT_SYSTEM_MODEL.md` produit (ce document)
- [x] `docs/AUDIT_MASTER.md` initialisé avec AUDIT-001
- [x] `docs/AUDIT_COVERAGE.md` produit (matrice page × rôle, toutes cellules « non vu »)
- [x] **Aucune modification de code** — P0 est en lecture seule ; la suite de tests est donc
      inchangée par rapport à `main` (`c4f5e11`)
- [ ] Commit sur `audit/exhaustif-p0-comprehension`
- [ ] Prompt de reprise Phase 1 généré
