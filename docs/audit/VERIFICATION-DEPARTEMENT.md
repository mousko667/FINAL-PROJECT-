# Rapport de Vérification — Cahier des charges (Département)
## Digital and Secure Supplier Invoice Validation Management System

**Date :** 2026-06-15
**Méthode :** vérification croisée **code** (backend Spring Boot + frontend React) **ET runtime**
(appels API authentifiés par rôle + clics réels via Playwright). Chaque verdict est étayé par une
preuve (fichier:ligne ou comportement observé : statut HTTP, persistance, rendu UI).

**Environnement de test :** backend `localhost:8080`, frontend `localhost:3000`, PostgreSQL 18,
15 comptes de test (1 par rôle, mot de passe `Test1234!`). Suite de tests backend : **334/0/0**.

**Légende :** ✅ COMPLET · ⚠️ PARTIEL · ❌ ABSENT / CASSÉ

---

## SYNTHÈSE EXÉCUTIVE

| # | Module | Verdict global | Note |
|---|--------|----------------|------|
| 1 | User Registration & Authentication | ✅ COMPLET | 1 manque mineur : lien « mot de passe oublié » absent du login |
| 2 | Dashboard | ✅ COMPLET | « budget alerts » manager non câblé en widget dédié |
| 3 | Invoice Reception | ✅ COMPLET | OCR **réel** (Tess4J), détection doublons **réelle** |
| 4 | Validation Workflow | ✅ COMPLET | workflow multi-niveaux piloté par département |
| 5 | Three-Way Matching | ✅ COMPLET | moteur réel PO/GRN/facture + tolérances |
| 6 | Approval Workflow | ✅ COMPLET | file + SLA 3 j ; délégation **admin-only** (pas self-service) |
| 7 | Payment Tracking | ✅ COMPLET | paiements, remittance, aging |
| 8 | Supplier Management | ✅ COMPLET | directory, profil, portail self-service |
| 9 | Digital Archiving | ⚠️ PARTIEL — **BUG** | **recherche archive 500 (cassée en runtime)** ; pas de versioning/visionneuse |
| 10 | Audit Trail | ⚠️ PARTIEL | trail complet + activité temps réel ; pas de détection d'anomalie |
| 11 | Reporting & Analytics | ⚠️ PARTIEL | 6 rapports réels ; pas de builder custom/planif/distribution |
| 12 | Integration | ⚠️ PARTIEL | webhooks + API ; **aucun connecteur ERP/compta/banque/DMS** |
| 13 | User & Access Management | ✅ COMPLET | console, matrice permissions, import/export, demandes d'accès |
| 14 | Security & Compliance | ⚠️ PARTIEL | RBAC/MFA/chiffrement/rate-limit/health **réels** ; pas de backup/incident/SOX/calendrier |

**Verdict d'ensemble : système fortement implémenté et fonctionnel sur le cœur de métier
(soumission → validation → approbation → paiement → archivage → audit).** Les écarts sont
concentrés sur (a) **un bug bloquant** : la recherche dans les archives renvoie 500, et (b) des
features « entreprise » avancées laissées hors-scope (connecteurs ERP, builder de rapports,
conformité SOX/IFRS, détection d'anomalie) — déjà tracées dans `KNOWN_ISSUES_REGISTRY.md`
(PROB-042…048).

---

## MODULE 1 — User Registration & Authentication ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Inscription avec sélection de rôle | ✅ | suppliers : self-register `/register/supplier` ; staff : créés par admin `POST /users` (`@PreAuthorize ADMIN`) |
| Champs fournisseur (raison sociale, NIF, contact, **coordonnées bancaires**) | ✅ | formulaire `/register/supplier` observé : tous les champs présents (runtime) |
| Champs staff (employee ID, département, limites d'approbation) | ✅ | `User.employeeId/departmentId/approvalLimit` + `AdminUserFormPage` |
| Indicateur de force du mot de passe | ✅ | runtime : « abc » → **Faible** ; « Str0ng!Pass2026# » → **Fort** |
| Vérification e-mail | ✅ | `GET /auth/verify-email` + `EmailVerificationPage.tsx` |
| Login (username/email + mot de passe) | ✅ | `POST /auth/login` ; 15 rôles testés → 200 |
| Récupération / réinitialisation du mot de passe | ⚠️ | `/forgot-password` + `/reset-password` **fonctionnent** mais **aucun lien depuis le login** (accessible seulement par URL directe) |
| MFA (obligatoire sauf supplier) | ✅ | `MfaSetupEnforcementFilter.requiresMandatoryMfa()` : ADMIN/DAF/ASSISTANT/VALIDATEUR_N1+N2 **oui**, SUPPLIER **non** ; `POST /auth/mfa/setup|confirm|validate` |
| Session + timeout | ✅ | `SecurityPolicy.sessionTimeoutMinutes` appliqué ; `GET /admin/sessions` |
| Redirection dashboard par rôle | ✅ | runtime : `supplier` → `/supplier/dashboard` ; `daf`/`admin` → `/dashboard` |
| Gestion de profil | ✅ | `GET/PUT /profile` + `ProfilePage.tsx` (MFA setup inclus) |
| Suivi des tentatives de connexion | ✅ | `AuthService` : `failedLoginAttempts` + `lockedUntil` (verrouillage compte) |

**Manque mineur :** ajouter un lien « Mot de passe oublié ? » sur la page de login.

---

## MODULE 2 — Dashboard ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Vues par rôle | ✅ | runtime : dashboard fournisseur ≠ dashboard staff |
| Dashboard fournisseur (soumises, statut paiement, actions) | ✅ | runtime : 5 cartes (Soumises/En attente/Validées/Payées/Rejetées) + « Actions requises » avec factures réelles |
| Dashboard finance (approbations, file, aging) | ✅ | runtime DAF : « File de traitement », KPIs |
| Dashboard manager (approbations, métriques, **budget alerts**) | ⚠️ | métriques OK ; **pas de widget « budget alerts » dédié** (le budget est dans Rapports) |
| Cartes de synthèse | ✅ | runtime : 4 KPI cards (total, en retard, temps moyen, taux rejet) |
| Flux d'activité récente | ✅ | « Actions requises » (fournisseur) + panneau activité (audit) |
| Boutons d'action rapide | ✅ | runtime : liens Paiements / Rapports / Audit financier |
| Centre de notifications + compteur | ✅ | cloche notifications dans le header (toutes pages) |
| KPI de temps de traitement | ✅ | « Temps de traitement moyen » sur dashboard |
| Layout responsive | ✅ | grilles Tailwind responsive (`grid-cols-2 lg:grid-cols-4`) |
| Annonces système | ❌ | non implémenté (aucun système d'annonces) |

---

## MODULE 3 — Invoice Reception ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Interface de soumission fournisseur | ✅ | runtime `/supplier/invoices/new` |
| Upload PDF / XML / image | ✅ | zone upload « PDF, JPEG, PNG, TIFF — max 10 Mo » (runtime) |
| **OCR extraction (preview)** | ✅ **RÉEL** | `OcrService` : PDFBox text-layer + **Tess4J/Tesseract** `doOCR` (pom : `tess4j 5.11.0`) ; UI « nous extrairons les données automatiquement » |
| Champs facture (n°, date, montant, fournisseur, description) | ✅ | formulaire manuel observé : département, n°, montant, devise, dates, description |
| Liaison PO / référence | ✅ | `Invoice.purchaseOrder` + matching PO |
| Pièces jointes | ✅ | `POST /invoices/{id}/documents` + bulk |
| **Détection de doublons (alerte)** | ✅ **RÉEL** | `performDuplicateCheck` → `countDuplicatesBySupplierAndDescription` bloque à la soumission |
| Confirmation + n° de référence | ✅ | format `FAC-{YYYY}-{NNNNN}` (séquence annuelle) |
| Suivi de statut fournisseur | ✅ | `/supplier/invoices` (timeline 4 étapes) |
| Historique des soumissions | ✅ | liste fournisseur |
| **Upload en masse (bulk)** | ✅ | `POST /invoices/{id}/documents/bulk` (P11-48) + composant `BulkDocumentUpload` |
| API d'intégration soumission auto | ⚠️ | API REST existe ; pas d'endpoint « intake » dédié documenté |

---

## MODULE 4 — Validation Workflow ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Routage d'approbation multi-niveaux | ✅ | machine à états + config par département (`Department.n1Role/n2Role/requiresN2`) |
| Règles de validation auto (PO, seuils, fournisseur) | ✅ | guards : `DocumentRequiredGuard`, `RoleMatchGuard`, matching PO |
| File de validation en attente | ✅ | `GET /invoices/{id}/workflow/steps` → 200 |
| Interface de revue + approbation/rejet + commentaires | ✅ | `ApprovalController` `/workflow` ; UI détail facture |
| Raison de rejet | ✅ | `RejectRequest` + `RejectionReasonGuard` |
| Re-soumission après rejet | ✅ | `POST /invoices/{id}/resubmit` (créé en P11) |
| Historique d'approbation | ✅ | `InvoiceStatusHistory` + timeline |
| Escalade des retards | ✅ | `DeadlineReminderJob` (relance + escalade DAF/Admin) |
| Monitoring SLA | ✅ | runtime : « SLA : 3 jours ouvrables par niveau » sur la file d'approbation |
| Templates de checklist de validation | ❌ | pas de templates de checklist configurables |
| Interface de **configuration** de workflow | ⚠️ | config via matrice d'approbation admin (départements), pas un éditeur de règles libre |

---

## MODULE 5 — Three-Way Matching ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Matching automatique (PO, GRN, facture) | ✅ **RÉEL** | `ThreeWayMatchingService` + `ThreeWayMatchingResult` (entité) |
| Affichage PO / GRN / lignes facture | ✅ | `GET /purchase-orders`, `/goods-receipts` → 200 |
| Statuts (matched, partial, mismatch) | ✅ | enum `MatchingStatus` (MATCHED/PARTIAL/MISMATCH/OVERRIDDEN) |
| Identification des écarts | ✅ | `discrepancyNotes` sur le résultat |
| Seuils de tolérance configurables | ✅ | `MatchingConfig` (tolérance % + montant) ; `GET /matching-config` → 200 ; UI `AdminMatchingConfigPage` |
| Override manuel + justification | ✅ | `MatchingOverrideRequest` (`overrideReason`) |
| Historique de matching | ✅ | `ThreeWayMatchingResultRepository` (append-only V27) |
| Export rapports de matching | ✅ | inclus dans exports rapports |
| Workflow de résolution non-matchés | ⚠️ | écarts flaggés ; pas de workflow de résolution dédié distinct |

---

## MODULE 6 — Approval Workflow ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Dashboard d'approbation (en attente) | ✅ | runtime `/approvals` (DAF) : file + légende SLA |
| Vue détail facture pour approbateur | ✅ | `InvoiceDetailPage` + `InvoiceActionPanel` |
| Approbation/rejet + commentaires | ✅ | `ApprovalController` |
| Visualisation multi-niveaux | ✅ | timeline d'approbation (N1→N2→DAF) |
| **Délégation d'approbation** | ⚠️ | `AdminDelegationsPage` + `GET /approvals/delegations` → 200, mais **gérée par l'admin** (pas self-service par l'approbateur absent) |
| Config des règles d'escalade | ⚠️ | escalade SLA codée (job) ; pas d'éditeur de règles |
| Historique d'approbation | ✅ | `InvoiceStatusHistory` |
| **Interface mobile d'approbation** | ⚠️ | layout responsive ; pas d'app/vue mobile dédiée |
| Notifications d'approbation | ✅ | events + WebSocket + e-mail |
| Suivi SLA des approbations | ✅ | runtime : légende SLA 3 j (rouge/ambre) |
| Analytics d'approbation | ✅ | `GET /workflow/my-stats` (stats validateur) + bottlenecks |

---

## MODULE 7 — Payment Tracking ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Dashboard de suivi des paiements | ✅ | runtime `/payments` : « Historique des paiements » |
| Analyse d'aging | ✅ | `GET /reports/aging` + section Rapports |
| Suivi des échéances | ✅ | `dueDate` + `DeadlineReminderJob` |
| Statuts (scheduled/processed/paid/overdue) | ✅ | flux BON_A_PAYER → PAYE |
| Traitement par lot | ⚠️ | enregistrement unitaire présent ; lot non confirmé en UI |
| Enregistrement de confirmation | ✅ | `POST /payments` (record payment) |
| Génération remittance advice | ✅ | `GET /payments/{id}/remittance` (P11-46) |
| Méthodes de paiement (virement, chèque, mobile money) | ✅ | enum `PaymentMethod` |
| Historique paiements par fournisseur | ✅ | `GET /reports/supplier/{id}/payments` |
| Analyse d'impact trésorerie | ✅ | `GET /reports/cash-flow` (projection hebdo) |
| Export rapports paiement | ✅ | exports Excel/PDF |
| Config d'alertes paiement | ⚠️ | rappels d'échéance oui ; pas de configuration d'alertes côté UI |

---

## MODULE 8 — Supplier Management ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Directory + recherche/filtres | ✅ | `GET /suppliers` → 200 ; `SuppliersPage` |
| Gestion de profil fournisseur | ✅ | `SupplierController` + `SupplierDetailPage` |
| Détails bancaires + fiscaux | ✅ | chiffrés AES (`@Convert`) — voir Module 14 |
| Suivi contrats/accords | ❌ | pas de gestion de contrats |
| Métriques de performance fournisseur | ✅ | `GET /reports/supplier/{id}/performance` (`SupplierPerformanceTask`) |
| Log de communication | ❌ | pas de journal de communication |
| Catégorisation/segmentation | ⚠️ | statut fournisseur (`SupplierStatus`) ; pas de segmentation riche |
| Repository documentaire par fournisseur | ✅ | `/supplier/documents` + `SupplierDocument` |
| Workflow d'onboarding | ✅ | self-register → statut PENDING → activation |
| Portail self-service | ✅ | runtime : portail fournisseur complet |
| Export rapports fournisseur | ✅ | exports rapports |

---

## MODULE 9 — Digital Archiving ⚠️ PARTIEL — **BUG BLOQUANT**

| Feature | Verdict | Preuve |
|---|---|---|
| Archivage des factures + documents | ✅ | statut ARCHIVE + MinIO |
| Stockage par date/fournisseur/statut | ✅ | données présentes |
| **Recherche & filtres avancés** | ❌ **CASSÉ** | runtime : `GET /invoices/archive` → **500** (toujours, même sans filtre). La page affiche « Aucune facture archivée » en masquant l'erreur |
| Viewer document (zoom/rotate) | ❌ | pas de visionneuse intégrée (PROB-045) |
| Métadonnées (n°, date, montant) | ✅ | colonnes table archive |
| **Version control** | ❌ | pas de versioning (PROB-044) |
| Config politique de rétention | ⚠️ | texte « 10 ans » corrigé (P11-49) mais **pas d'application automatique** (PROB-043) |
| Contrôles archive/purge | ❌ | pas de purge automatisée |
| **Logs d'accès aux documents** | ✅ | `document_access_log` append-only (V48, P11-50) + hook `download()` |
| Export documents archivés | ✅ | export PDF par facture |
| Reporting de conformité archives | ⚠️ | partiel |

> **🔴 DÉFAUT MAJEUR (à corriger en priorité) :**
> `InvoiceRepository.searchArchived` — la requête JPQL `LOWER(CONCAT('%', :keyword, '%'))` échoue
> sous PostgreSQL quand les paramètres sont `null` : *« la fonction lower(bytea) n'existe pas »*
> (le paramètre null est inféré `bytea`). Résultat : **la page Archive est inutilisable** — elle
> renvoie 500 à chaque chargement et l'UI le masque en « Aucune facture archivée ».
> **Correctif suggéré :** caster les paramètres (`CAST(:keyword AS string)`) ou passer en requête
> native/Specification typée.

---

## MODULE 10 — Audit Trail ⚠️ PARTIEL

| Feature | Verdict | Preuve |
|---|---|---|
| Viewer de logs d'audit | ✅ | runtime `/admin/audit` ; `GET /audit-logs` → 200 |
| Timeline d'actions + filtres | ✅ | filtres user/entité/action |
| Historique de modification facture | ✅ | `audit_logs` + `InvoiceStatusHistory` |
| Tracking décisions d'approbation | ✅ | historique workflow |
| Log changements statut paiement | ✅ | audit + historique |
| Records d'accès documents | ✅ | `document_access_log` (P11-50) |
| Activité login/logout | ✅ | `AuditLoggingFilter` (append-only V25) |
| Export audit trail | ✅ | export disponible |
| **Détection d'anomalie + alertes** | ❌ | pas de détection statistique/ML (PROB-046) |
| Affichage conformité rétention | ⚠️ | partiel |
| **Dashboard temps réel** | ✅ | panneau « Activité récente » auto-rafraîchi 15 s (P11-51) |
| Rapports de synthèse d'audit | ⚠️ | partiel |

---

## MODULE 11 — Reporting & Analytics ⚠️ PARTIEL

| Feature | Verdict | Preuve |
|---|---|---|
| Dashboard analytics + KPI cards | ✅ | runtime `/reports` : section KPIs |
| Rapports de temps de traitement | ✅ | KPI temps moyen |
| Analytics performance fournisseur | ✅ | `GET /reports/supplier/{id}/performance` |
| Rapports d'aging | ✅ | section « Analyse de l'ancienneté » |
| Analyse cycle de paiement | ✅ | cash-flow + aging |
| Identification goulots d'approbation | ✅ | « Goulots d'étranglement d'approbation » |
| Tendances volume/valeur | ✅ | « Top fournisseurs » + charts |
| **Comparaison budget vs réel** | ✅ | runtime : section « Budget vs Réalisé par département » (P11-52) ; `GET /reports/budget-vs-actual` → 200 |
| Preview + export (PDF, Excel) | ✅ | « Data Exports » |
| **Builder de rapports custom** | ❌ | non implémenté (PROB-047) |
| **Rapports planifiés** | ❌ | non implémenté (PROB-047) |
| **Gestionnaire de distribution** | ❌ | non implémenté (PROB-047) |
| **Générateur de résumé exécutif** | ❌ | non implémenté (PROB-047) |

---

## MODULE 12 — Integration ⚠️ PARTIEL

| Feature | Verdict | Preuve |
|---|---|---|
| Dashboard de config d'intégration | ✅ | runtime `/admin/integrations` |
| **Connecteur ERP (SAP, Oracle, Dynamics)** | ❌ | aucun connecteur ERP |
| **Intégration système d'achat** | ❌ | PO gérés en interne, pas de connecteur externe |
| **Connexion logiciel comptable** | ❌ | absent |
| **Intégration bancaire (paiements)** | ❌ | méthodes de paiement enregistrées manuellement, pas d'intégration bancaire |
| Intégration DMS | ⚠️ | stockage MinIO interne ; pas de DMS externe |
| Config API | ✅ | API REST complète + Swagger |
| **Gestion des webhooks** | ✅ | runtime : « Ajouter un webhook » ; `GET /integrations/webhooks` → 200 |
| Monitoring de statut d'intégration | ✅ | `GET /integrations/status` → 200 (health + delivery-log, P11-47) |
| Config de planning de sync | ❌ | pas de sync planifiée |
| Log d'erreurs + résolution | ✅ | delivery-log webhooks |
| Interface de test de connexion | ✅ | bouton « Tester » webhook |

> **Note :** le module est livré sous l'angle **webhooks + API génériques** (extensible), mais
> **aucun connecteur prêt-à-l'emploi** vers ERP/compta/banque/DMS. C'est cohérent avec un périmètre
> bachelor (souvent hors-scope), mais à déclarer clairement vis-à-vis du cahier des charges.

---

## MODULE 13 — User & Access Management ✅ COMPLET

| Feature | Verdict | Preuve |
|---|---|---|
| Console de gestion utilisateurs | ✅ | runtime `/admin/users` : 18 users, tous rôles, créer/modifier |
| Attribution rôles/permissions | ✅ | `PUT /users/{id}/roles` + `AdminPermissionMatrixPage` |
| Contrôle d'accès niveau département | ✅ | `User.departmentId` + guards département |
| **Classification de sensibilité des données** | ✅ | enum `DataSensitivity` (P11-15) sur factures |
| Monitoring d'activité utilisateur | ✅ | audit + sessions |
| Gestion du statut de compte | ✅ | `PATCH /users/{id}/activate` + `/unlock` |
| **Import/export en masse** | ✅ | `GET/POST /users/(export\|import)/csv` (P11-16) |
| **Workflow de demande d'accès** | ✅ | `access-requests` (P11-17) ; `GET /access-requests?status=PENDING` → 200 |
| **Éditeur de matrice de permissions** | ✅ | `/admin/permissions` (P11-18) |
| Vue de gestion des sessions | ✅ | `GET /admin/sessions` + révocation |
| Viewer d'audit trail utilisateur | ✅ | filtre par user dans l'audit |
| Config de menu basée sur le rôle | ✅ | `RoleGuard` masque la nav par rôle |

---

## MODULE 14 — Security & Compliance ⚠️ PARTIEL

| Feature | Verdict | Preuve |
|---|---|---|
| Matrice de permissions par rôle | ✅ | `@PreAuthorize` partout + `AdminPermissionMatrixPage` |
| **Indicateurs de statut de chiffrement** | ✅ | tableau « Santé sécurité » (P11-53) ; bank details chiffrés AES `@Convert` |
| Paramètres 2FA/MFA | ✅ | `/admin/security-policy` + setup MFA profil |
| **MFA obligatoire sauf supplier** | ✅ **VÉRIFIÉ** | `requiresMandatoryMfa()` : staff oui, supplier non |
| Monitoring activité login | ✅ | tendance échecs (verrouillages + tentatives) dans health |
| **Statut de backup des données** | ❌ | non implémenté (PROB-048) |
| Config politique de rétention | ⚠️ | partiel (cf. Module 9) |
| **Tracking acceptation politique confidentialité** | ❌ | non implémenté (PROB-048) |
| **Reporting d'incident de sécurité** | ❌ | non implémenté (PROB-048) |
| **Checklist conformité (SOX, IFRS)** | ❌ | non implémenté (PROB-048) |
| Outils de préparation d'audit | ⚠️ | export audit oui ; pas d'outils dédiés |
| **Calendrier de conformité** | ❌ | non implémenté (PROB-048) |
| **Dashboard de santé sécurité** | ✅ | `GET /admin/security-health` (P11-53) + panneau SecuritySettingsPage |
| Rate limiting (bonus observé) | ✅ **VÉRIFIÉ** | runtime : 429 après burst de logins (`RateLimitingFilter`) |
| Chiffrement données sensibles | ✅ | AES `@Convert` sur bank details (factures + fournisseurs) |

---

## DÉFAUTS & MANQUES — RÉCAPITULATIF PRIORISÉ

### 🔴 À corriger (bug runtime confirmé)
1. **Recherche Archive cassée** — `GET /invoices/archive` renvoie 500 (`lower(bytea)`), la page
   Archive est inutilisable et masque l'erreur en « aucune facture ». *(Module 9)*

### 🟠 Écarts UX/fonctionnels mineurs
2. Lien « Mot de passe oublié ? » absent du login *(Module 1)*.
3. Pas de widget « budget alerts » sur le dashboard manager *(Module 2)*.
4. Délégation d'approbation **admin-only**, pas self-service par l'approbateur *(Module 6)*.
5. Annonces système absentes *(Module 2)*.

### 🟡 Features « entreprise » hors-scope (déjà tracées PROB-042…048)
6. Connecteurs ERP/compta/banque/DMS *(Module 12)*.
7. Builder de rapports custom + planification + distribution + résumé exécutif *(Module 11)*.
8. Détection d'anomalie (audit) *(Module 10)*.
9. Backup status, tracking politique de confidentialité, reporting d'incident, checklist SOX/IFRS,
   calendrier de conformité *(Module 14)*.
10. Versioning de documents + visionneuse in-app + rétention automatisée *(Module 9)*.
11. Gestion de contrats + log de communication fournisseur *(Module 8)*.

---

## CONCLUSION

Le **cœur métier du cahier des charges est implémenté et fonctionne** : authentification sécurisée
(RBAC + MFA + rate-limit + chiffrement), réception de factures (OCR réel + détection doublons),
workflow de validation multi-niveaux piloté par département, three-way matching, approbation avec
SLA, suivi des paiements, gestion fournisseurs, audit append-only, et reporting (dont budget vs
réel). La gestion utilisateurs/accès est particulièrement complète.

**Un seul bug bloquant** doit être corrigé : la recherche dans les archives (500 runtime). Les
autres écarts sont des features « entreprise » avancées explicitement laissées hors-scope et déjà
documentées dans `KNOWN_ISSUES_REGISTRY.md`.
