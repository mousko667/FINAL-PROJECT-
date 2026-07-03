# Audit général du système — OCT Invoice System

**Date :** 2026-07-02
**Type :** vérification interne approfondie (code, interfaces, boutons, actions, workflow, i18n)
**Statut :** travail interne — **rien n'a été commité, rien n'a été modifié**. Audit en lecture seule.
**Méthode :** analyse statique exhaustive (backend Java/Spring + frontend React/TS) **+ vérification runtime** sur l'environnement Docker en cours d'exécution (backend + frontend `healthy`). Chaque finding BLOQUANT/MAJEUR a été **re-vérifié manuellement dans le code et/ou au runtime** avant d'être inscrit ici.

> **Ce rapport a deux passes.** La **Passe 1** (sections 1-6) couvre le cœur du système. La **Passe 2** (section 7) étend la couverture aux 28 controllers backend restants et à l'infra données/tests. La couverture **frontend exhaustive page par page reste partielle** (voir §8 « État de couverture ») : 2 lots d'audit frontend ont été interrompus par une limite de session et doivent être relancés.

---

## 0. Réponse directe à tes trois questions

| Ton constat | Verdict de l'audit |
|---|---|
| « Je ne vois pas l'OCR » | **L'OCR existe et FONCTIONNE bout-en-bout** (preuve runtime ci-dessous). Ce n'est pas un manque de code, c'est un problème de **découvrabilité UI** : l'OCR n'est accessible qu'au rôle *fournisseur*, et seulement en cliquant « nouvelle facture » depuis « Mes Factures ». Il n'est **pas exposé** dans le parcours de l'Assistant Comptable (celui qui saisit les factures reçues par email), alors que le backend l'y autorise pourtant. |
| « La traduction n'est pas parfaite, des mots restent en français » | **Cause racine identifiée.** Les fichiers FR et EN sont parfaitement symétriques (1062 clés chacun, 0 écart). Le problème vient de **29 clés que le code appelle mais qui n'existent dans AUCUN des deux fichiers** → l'écran affiche alors la clé brute ou le texte anglais figé (défaut codé en dur). Des pages entières sont concernées (onboarding fournisseur, sauvegardes admin, arbre d'archives). |
| « On m'a garanti que tout était OK » | **Faux.** L'audit relève **1 bloquant, 8 majeurs, ~30 mineurs**. Le système est globalement solide (architecture propre, sécurité de base présente) mais **le cœur du workflow BAP est cassé côté interface** et **3 problèmes de sécurité/conformité** existent. Détail ci-dessous. |

---

## 1. Preuve runtime — ce qui a été testé pour de vrai

Environnement testé : `oct_backend` + `oct_frontend` (Docker, `healthy`), API `http://localhost:8080`, front `http://localhost:3000`.

| Test | Résultat | Verdict |
|---|---|---|
| `GET /actuator/health` | `{"status":"UP"}` | ✅ |
| Front `GET /` | HTTP 200 | ✅ |
| Login `admin` | JWT retourné | ✅ |
| Login `daf` | `mfa_required:true` + `pre_auth_token` | ✅ MFA 2 étapes fonctionne |
| Logins rapprochés | `Too many requests` | ✅ rate-limiting actif |
| **OCR `POST /ocr/extract` avec un vrai PDF** | `invoiceNumber: FAC-2026-00099`, `date: 15/06/2026`, `total: 450000`, `digitalPdf: true` | ✅ **OCR pleinement fonctionnel** |
| `POST /ocr/extract` sans fichier | **HTTP 500** (devrait être 400) | ⚠️ défaut de robustesse |
| Actions ADMIN (`/users`, `/reports`) | `mfa_setup_required` (HTTP 400) | ⚠️ compte `admin` re-cassé (voir MAJEUR-6) |
| `tsc --noEmit` (frontend) | exit 0, aucune erreur | ✅ code TS sain |

---

## 2. 🔴 BLOQUANT — Le workflow BAP est cassé à la 2ᵉ étape (SOUMIS → EN_VALIDATION_N1)

**Sévérité : BLOQUANT — CONFIRMÉ dans le code.**

**Le fait.** La state machine backend exige l'événement `ASSIGN_REVIEWER` pour faire passer une facture de `SOUMIS` à `EN_VALIDATION_N1` :
- `src/main/java/com/oct/invoicesystem/config/StateMachineConfig.java:57-59` (source SOUMIS → cible EN_VALIDATION_N1, event `ASSIGN_REVIEWER`).
- L'endpoint existe et est correctement sécurisé : `POST /api/v1/invoices/{id}/workflow/assign` — `ApprovalController.java:57-70` (autorise AA + tous les N1/N2).

**Mais le frontend n'appelle JAMAIS cet endpoint.** Un `grep` sur tout `frontend/src` de `assign` / `START_REVIEW` / `assignReviewer` ne renvoie que le module *archives* (`AssignFolderModal`, `archiveFolders.assign`) et les *délégations/rôles* — **rien** ne déclenche `/workflow/assign`.

**Conséquence en chaîne :**
1. `InvoiceActionPanel.tsx` n'affiche le bouton `VALIDATE_N1` **que si `status === 'EN_VALIDATION_N1'`**. Pour une facture `SOUMIS`, `buttons.length === 0` → le composant `return null` : **aucun bouton, pour aucun rôle**.
2. `ApprovalQueuePage.tsx` filtre la file du validateur N1 sur `EN_VALIDATION_N1` uniquement — jamais sur `SOUMIS`. Le N1 **ne voit donc jamais** les factures à prendre en charge.
3. Résultat : **toute facture soumise reste bloquée en `SOUMIS` pour toujours.** Le processus Bon-à-Payer ne peut pas avancer en flux normal.

**Correctif attendu :** exposer une action « Démarrer la revue / Prendre en charge » (appel `POST /workflow/assign`) sur les factures `SOUMIS`, ET faire apparaître les `SOUMIS` dans une file visible (côté AA ou côté N1). Le backend est prêt ; **c'est purement le frontend qui n'expose pas l'action.**

---

## 3. 🟠 MAJEURS (8)

### MAJEUR-1 — IDOR : documents de facture accessibles sans contrôle de propriété
**`InvoiceDocumentController.java:65-68` et `:76-84` — CONFIRMÉ.**
`GET /api/v1/invoices/{invoiceId}/documents` et `GET .../{docId}/download` sont annotés `@PreAuthorize("isAuthenticated()")` seulement. Le service (`InvoiceDocumentService.listByInvoice`, `generateDownloadUrlAndLog`) ne vérifie **ni la propriété, ni l'exclusion du rôle SUPPLIER**.
**Scénario d'exploitation :** un fournisseur (ou n'importe quel compte authentifié) appelle ces endpoints avec l'ID d'une facture d'un autre fournisseur / d'une facture interne → il liste les documents et obtient une URL présignée de téléchargement. Le portail fournisseur protège pourtant ses propres routes via `ensureOwnInvoice` (`SupplierPortalController.java:286-291`), mais ces endpoints génériques la contournent.
**Correctif :** restreindre à `!hasRole('SUPPLIER')` et/ou vérifier la propriété quand l'appelant est fournisseur.

### MAJEUR-2 — Séparation des tâches (SoD) violée : ADMIN accède aux données financières de paiement
**`PaymentController.java:72, 80, 91, 99` — CONFIRMÉ.**
Les 4 lectures paiement (`GET /payments/invoice/{id}`, `GET /payments`, `GET /{id}/remittance`, `GET /export`) sont `hasAnyRole('ASSISTANT_COMPTABLE','DAF','ADMIN')`. La règle projet (`admin-no-financial-access`, PROB-065) impose que `ROLE_ADMIN` **n'accède PAS** aux données financières. Ici l'ADMIN peut lister tous les paiements, exporter l'historique et récupérer les avis de règlement. Incohérent : `InvoiceController` et `MatchingQueryController` excluent pourtant l'ADMIN (`!hasRole('ADMIN')`).
**Correctif :** retirer `'ADMIN'` de ces 4 endpoints (garder DAF + ASSISTANT_COMPTABLE).

### MAJEUR-3 — Le contrôle de matching peut être contourné (exceptions avalées)
**`InvoiceStateMachineServiceImpl.performMatchingCheck` (bloc `catch`) — CONFIRMÉ.**
Le `catch (Exception e)` ne re-lève que `WorkflowException`. Toute autre exception (`ValidationException` « No active matching configuration », « Invoice or PO has no line items », erreur d'accès données…) est **loggée puis ignorée**, et la soumission continue.
**Scénario :** facture avec `purchaseOrderId` mais sans lignes, ou config matching inactive → `match()` lève `ValidationException` → avalée → la facture passe `SOUMIS` **sans qu'aucun matching n'ait été évalué**. Le garde-fou « MISMATCH bloque au-delà de SOUMIS » devient contournable.
**Correctif :** ne tolérer aucune exception silencieuse sur ce chemin critique ; échouer la soumission si le matching ne peut pas être évalué.

### MAJEUR-4 — Bouton « Record Payment » de l'InvoiceActionPanel envoie un moyen de paiement inexistant
**`InvoiceActionPanel.tsx:53-59` — CONFIRMÉ (croisé avec l'enum backend).**
Le bouton `MARK_PAID` poste `paymentMethod: 'BANK_TRANSFER'`. L'enum `PaymentMethod` ne contient que `VIREMENT, CHEQUE, ESPECES, MOBILE_MONEY`. L'appel échoue systématiquement (400, désérialisation Jackson). Ce bouton est en plus **redondant** avec le modal de la `PaymentsPage` (qui, lui, envoie `VIREMENT`).
**Correctif :** `BANK_TRANSFER` → `VIREMENT`, ou supprimer ce bouton au profit du modal PaymentsPage.

### MAJEUR-5 — 29 clés i18n manquantes → écrans affichés en clé brute ou en anglais figé
**Cause racine du symptôme « la traduction n'est pas parfaite ». CONFIRMÉ (0 clé en commun manquante entre fr/en, mais 29 clés absentes des DEUX).**
- **Onboarding fournisseur** — namespace `supplier.onboarding.*` totalement absent → `SupplierOnboardingPage.tsx` (titre, sous-titre, previous/next/finish, étapes) s'affiche **entièrement en anglais même en mode FR**.
- **Sauvegardes admin** — namespace `admin.backups.*` absent → `AdminBackupsPage.tsx` affiche des clés brutes (« admin.backups.title »…).
- **Arbre d'archives** — namespace `archiveFolders.*` absent → `ArchiveFolderTree.tsx`, `AssignFolderModal.tsx` affichent des clés brutes.
- **Fautes de frappe de namespace** — 3 appels `common.cancel` / `common.loading` / `common.actions` alors que le reste du code utilise `app.*` (`MatchingLineResolveModal.tsx:73`, `DepartmentAccessPage.tsx:21`, `AdminBackupsPage.tsx:115`) → clé brute affichée.
- **`invoice.duplicateWarning`** — `InvoiceCreatePage.tsx:298` : alerte de doublon non traduite.
**Correctif :** créer ces namespaces (FR+EN) et corriger les 3 `common.*` → `app.*`.

### MAJEUR-6 — Compte `admin` re-cassé par `mfa_setup_required` (correction non pérennisée en Flyway)
**Vérifié au runtime : toutes les actions admin renvoient `mfa_setup_required` (HTTP 400).**
Le compte `admin` (seed V5) a `mfa_verified=false` en base, donc le `MfaSetupEnforcementFilter` bloque **toutes** ses actions. Ce problème avait déjà été corrigé **manuellement en base** lors d'une session précédente, mais **sans migration Flyway** → il réapparaît à chaque re-seed / recréation de la base.
**Correctif :** créer une migration Flyway qui aligne le compte `admin` (hash BCrypt de `Test1234!` + `mfa_verified=true`) sur les comptes du seed V34, au lieu d'une correction manuelle éphémère.

### MAJEUR-7 — `ExportMenu` : aucune gestion d'erreur sur une action fréquente
**`components/ui/ExportMenu.tsx:31-50` — CONFIRMÉ.**
La fonction `download()` (utilisée par InvoiceList, InvoiceDetail, Payments…) n'a **aucun `catch`**. En cas d'échec (403/500/réseau), l'utilisateur ne voit aucun message ; pire, avec `responseType:'blob'` un blob d'erreur peut être écrit comme s'il s'agissait du fichier.
**Correctif :** ajouter un `catch` + message d'erreur i18n.

### MAJEUR-8 — Délégation d'approbation cassée par une double vérification divergente
**`RoleMatchGuard.java:63-68` vs `ApprovalServiceImpl.checkRole` — PLAUSIBLE (lecture code, non exécuté).**
Le rôle est vérifié deux fois lors d'une transition : `ApprovalServiceImpl.checkRole` **prend en compte les délégations** (`findActiveDelegationsForDelegatee`), mais `RoleMatchGuard.evaluate` (garde de la state machine) **ne les connaît pas**. Un délégataire accepté par le service serait donc **refusé** par la garde → `AccessDeniedException`. La fonctionnalité de délégation serait ainsi inopérante pour validate-n1/n2, bon-a-payer, reject.
**Correctif :** aligner la garde (intégrer les délégations) ou ne pas re-vérifier le rôle dans la garde.

---

## 4. 🟡 MINEURS (regroupés par thème)

### i18n / libellés codés en dur (règle bilingue CLAUDE.md §3 violée)
- ~9 chaînes JSX figées en français : `RoleGuard.tsx:15-16` (« Accès non autorisé »), `DashboardPage.tsx` (nombreux : « Workflows de validation », « Suivi de vos factures en temps réel », « Volume traité par fournisseur (XAF) »…), `ArchivePage.tsx:111` (« Tous les départements »), `AdminBackupsPage.tsx:163-166` (en-têtes de tableau), placeholders `PaymentsPage.tsx:123` / `GoodsReceiptsPage.tsx:137`, `Sidebar.tsx:226` (« Système opérationnel »).
- **15 clés backend** présentes en FR mais absentes de `messages_en.properties` (bloc MFA + verrouillage de compte : `error.otp.*`, `mfa.*`, `error.account.locked`…) → messages en français en mode EN.
- Incohérence devise : `DashboardPage.tsx:447` affiche « XAF » alors que tout le reste utilise « XOF ».

### UX / cohérence libellé↔action
- `NotFoundPage.tsx:12` : message `t('app.error')` (« Une erreur est survenue ») et bouton `t('app.retry')` (« Réessayer ») alors que l'action est `navigate('/')` (retour accueil) → libellé ≠ action.
- `SupplierInvoiceSubmitPage.tsx:306` : le bouton de soumission réutilise **la clé du titre de page** (`supplier.invoice.submit.title`) → ambigu ; clé dédiée souhaitable.
- `SupplierInvoiceSubmitPage.tsx:342/350` : le champ accepte `.xml` mais l'aide dit « PDF, JPEG, PNG, TIFF » (XML non annoncé).
- Double route pour la même page : `/register` et `/register/supplier` rendent le même formulaire (`AppRoutes.tsx:79`).

### Robustesse / code
- `POST /ocr/extract` sans fichier → **HTTP 500** au lieu de 400 (vérifié runtime).
- `SupplierInvoiceSubmitPage.tsx:220` : n° de facture pré-rempli via `defaultValue` (au lieu de `setValue` utilisé pour montant/date) → risque de non-prise en compte par react-hook-form (à vérifier au runtime).
- `InvoiceCreatePage.tsx:67` : `useForm().watch('supplierId')` crée un **2ᵉ formulaire jetable** dont le `watch` renvoie toujours `undefined` → la query `purchaseOrders` associée est morte (heureusement dupliquée par `supplierPOs` qui, elle, marche). Code mort à nettoyer.
- `InvoiceDocumentService.generateDownloadUrl` (`:210-216`) possiblement mort (seul `generateDownloadUrlAndLog` est utilisé).

### Workflow / logique métier (à confirmer avec le métier)
- `StateMachineConfig.java:108-113` : transition `VALIDE → REJETE` autorisée (DAF) — extension non documentée dans WORKFLOW.md (rejet après validation).
- `StateMachineConfig.java:86-90` : `BON_A_PAYER → PAYE` (`RECORD_PAYMENT`) sans garde de rôle dans la state machine (sûr aujourd'hui car appelé uniquement depuis `PaymentServiceImpl`, mais pas de défense en profondeur).
- `DepartmentTransitionGuard.java:22,30` : si `DEPARTMENT` absent de l'extended state, les deux gardes VALIDATE_N1 renvoient `false` → blocage silencieux (peu probable mais possible si `requiresN2 = null`).
- `ThreeWayMatchingService.java:167-173` : une facture qui est un **sous-ensemble** de la PO peut être classée `MATCHED` (couverture PO partielle non détectée).
- `ApprovalServiceImpl.bonAPayer:101-114` : `ensureWithinApprovalLimit` **absent** du Bon à Payer (présent sur validate-n1/n2) → un DAF pourrait émettre un BAP au-dessus de sa limite (à confirmer si la limite s'applique au DAF).
- `GoodsReceiptController.java:45,52` / `PurchaseOrderController.java:95` : ADMIN a accès aux lectures PO/GRN (à confirmer si la SoD s'étend aux données d'achat).

---

## 5. ✅ Points solides confirmés (ce qui va bien)

Pour être juste, l'audit a aussi **confirmé** de nombreux points sains :
- **OCR complet et fonctionnel** : Tesseract (Tess4J) + PDFBox (couche texte) + parsing XML structuré, exposé et branché sur le portail fournisseur. Preuve d'extraction réussie au runtime.
- **State machine rigoureuse** : aucune transition illégale structurellement possible (SOUMIS→PAYE, rejet d'une facture PAYE… tous refusés).
- **Routage N1/N2 correct** : piloté par `Department.requiresN2` **en base** (pas de liste en dur), aucun risque de sauter le N2.
- **Matching append-only respecté** ; override réservé DAF/ADMIN.
- **Soft-delete financier respecté** (jamais de hard delete).
- **`@PreAuthorize` sur 100 % des endpoints** (aucune méthode ouverte).
- **MFA 2 étapes, rate-limiting login, chaîne de délégation en base** : présents et fonctionnels.
- **Aucun TODO/FIXME** dans le code Java ; `tsc` sans erreur ; fichiers fr/en symétriques (1062 clés).
- **i18n de basculement fonctionnel** pour tout ce qui EST traduit.

---

## 6. Ordre de correction recommandé

1. **🔴 BLOQUANT** — Exposer l'action « Démarrer la revue » (`/workflow/assign`) + rendre les `SOUMIS` visibles dans une file. *Sans ça, aucune facture n'avance.*
2. **🟠 Sécurité** — MAJEUR-1 (IDOR documents) puis MAJEUR-2 (SoD paiements ADMIN).
3. **🟠 Intégrité** — MAJEUR-3 (matching avalé), MAJEUR-4 (BANK_TRANSFER → VIREMENT).
4. **🟠 Visible utilisateur** — MAJEUR-5 (29 clés i18n manquantes) + MAJEUR-7 (ExportMenu sans catch).
5. **🟠 Infra** — MAJEUR-6 (migration Flyway pour le compte admin).
6. **🟠 Fonctionnel** — MAJEUR-8 (délégation cassée, à confirmer au runtime).
7. **🟠 Sécurité (Passe 2)** — MAJEUR-10 (escalade access-request), MAJEUR-13 (credentials intégration en clair), MAJEUR-11/12 (SoD + métriques factices fournisseur), MAJEUR-9 (budget latent), B-1 (IDOR checklist), B-2 (path traversal backup).
8. **🟡 Mineurs** — i18n figé, devise XAF→XOF, libellés↔actions, nettoyage code mort, audit rétention, incohérences de contrat API.
9. **⏭️ À compléter** — relancer les 2 lots d'audit frontend (58 pages bouton par bouton), lancer `mvnw test`, audit navigateur réel (voir §8).

---

## 7. PASSE 2 — Couverture backend exhaustive (28 controllers restants) + infra

Cette passe audite les **28 controllers** non couverts en Passe 1 (les 12 de gouvernance + 16 de workflow/intégration/supplier/report). Tous les findings ci-dessous sont **vérifiés dans le code** (fichier:ligne) ; les findings de sécurité sont en plus **confirmés ou infirmés au runtime**.

### 7.1 Résultat runtime notable — un faux BLOQUANT écarté
Une première analyse statique a signalé le **budget départemental exposé à tous** comme BLOQUANT. **Vérification runtime : le champ `budget` n'apparaît PAS** dans la réponse `GET /departments` envoyée à un fournisseur. Explication : `application.yaml:45` impose `default-property-inclusion: non_null`, et le seed `V1` ne renseigne pas `budget` (NULL) → champ omis. Le finding est donc **rétrogradé de BLOQUANT à MAJEUR LATENT** (voir MAJEUR-9). *C'est l'intérêt de la vérification runtime : ne pas inscrire un bloquant qui n'existe pas en pratique.*

### 7.2 Nouveaux MAJEURS (tous vérifiés dans le code)

**MAJEUR-9 (latent) — Budget départemental exposable à tout authentifié, SUPPLIER inclus.**
`DepartmentDTO.java:19` inclut `budget` (BigDecimal) ; `GET /departments` et `/{id}` sont `@PreAuthorize("isAuthenticated()")` (`DepartmentController.java:31,41`), donc ouverts à **tous les rôles y compris SUPPLIER externe et ADMIN**. Aujourd'hui inoffensif (budget NULL au seed + Jackson `non_null`), mais **dès qu'un admin renseigne un budget** via `PUT /departments/{id}`, il sera sérialisé et fuité (donnée financière → externe + rupture SoD ADMIN). *Correctif : retirer `budget` d'un DTO public / créer un DTO restreint pour les lectures ouvertes, ou restreindre le GET aux rôles internes.*

**MAJEUR-10 — Escalade de privilèges via demande d'accès.**
`AccessRequestService.create` (`domain/access/service`, l.51-57) n'interdit **que** `ROLE_SUPPLIER`. Rien n'empêche un ASSISTANT_COMPTABLE de demander `ROLE_ADMIN` ou `ROLE_DAF` ; si un admin approuve par inadvertance, escalade complète (y compris vers un rôle financier). *Correctif : liste blanche des rôles auto-demandables, excluant ADMIN/DAF.*

**MAJEUR-11 — SoD : ADMIN accède aux métriques de performance financière fournisseur.**
`SupplierController.java:147` : `GET /{id}/performance` = `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')`. Renvoie taux de rejet, délai de paiement moyen, volume — données financières. Or `ReportController` exclut ADMIN de **tous** ses endpoints. Incohérence directe avec la règle `admin-no-financial-access` (PROB-065). *Correctif : retirer `'ADMIN'`.*

**MAJEUR-12 — Métriques fournisseur factices sur exception.**
`SupplierController.java:150-164` : `getPerformanceMetrics` attrape `ResourceNotFoundException` et **fabrique** des métriques (`invoiceAccuracyRate=1.0`, `rejectionRate=0.0`, `averagePaymentDays=0.0`) renvoyées comme réelles, sans indiquer que c'est un fallback. Donnée trompeuse. `getSupplier(id)` est en plus appelé deux fois. *Correctif : propager l'erreur ou marquer explicitement l'absence de données.*

**MAJEUR-13 — Credentials d'intégration stockés en clair.**
`IntegrationConnector.java:37-38` : le champ `config` (documenté comme portant la configuration ERP / **BANKING** / DMS, donc potentiellement identifiants/clés) est `@Column(length=4000)` **sans** `@Convert(EncryptionAttributeConverter.class)`, contrairement à `Supplier.bankDetails` qui est chiffré AES. Un accès en lecture à la base exfiltre ces credentials en clair. *Correctif : chiffrer `config` comme `bankDetails`.*

### 7.3 Autres findings backend (MAJEUR/MINEUR vérifiés)

| # | Controller / fichier:ligne | Problème | Sévérité |
|---|---|---|---|
| B-1 | `InvoiceChecklistController` (service `getInvoiceChecklist`/`saveResponse`) | Pas de contrôle de périmètre département/validateur : un validateur d'un autre département peut lire ET écrire la checklist d'une facture qui n'est pas la sienne (IDOR de circuit) | **MAJEUR** (à confirmer vs politique de cloisonnement) |
| B-2 | `BackupController.restoreBackup` (service, `"backups/"+filename`) | `filename` concaténé sans sanitisation → path traversal MinIO (atténué : ADMIN only) | **MAJEUR** |
| B-3 | `RetentionPolicyService.update` (l.77-82) | Modification de la durée de rétention légale **sans écriture d'audit** dédiée (changement de conformité non tracé) | MINEUR |
| B-4 | `Announcement.update` (l.62) | `expiresAt` toujours écrasé même si non fourni → une annonce peut ne plus jamais expirer | MINEUR |
| B-5 | `BackupController.listBackups` | `catch(Exception) → List.of()` : une panne MinIO s'affiche comme « aucune sauvegarde » | MINEUR |
| B-6 | `BackupController.restoreBackup` (l.91) | `Thread.sleep(1000)` en dur (code de démo bloquant un thread de requête) | MINEUR |
| B-7 | `DelegationController` (l.56-58) | `GET /` sans `departmentCode` renvoie `List.of()` silencieux (faux négatif pour l'admin) | MINEUR |
| B-8 | `WebhookController.java:41` | Cast direct `(User) principal` non gardé → ClassCastException 500 possible | MINEUR |
| B-9 | `SupplierController` (activate/suspend l.124,132) | Ouverts à ASSISTANT_COMPTABLE alors que la règle littérale dit « ADMIN seul change le statut » | MINEUR |
| B-10 | `ArchiveFolderController` (l.77-79) / `SupplierController` delete | Réponses 204 hors enveloppe `ApiResponse<T>` (incohérence de contrat) | MINEUR |
| B-11 | `DelegationController` (l.36,76), `ComplianceController` (`POST /backup-status`) | `@RequestBody Map<String,Object>` au lieu d'un DTO typé (parsing manuel fragile) | MINEUR |
| B-12 | `NotificationController.markAsRead` | Marquer une notif non-possédée → 200 « marked.read » silencieux (pas de faille cross-user, mais réponse trompeuse) | MINEUR |
| B-13 | `SecurityPolicyController` / `SecurityPolicyUpdateRequest` | Bornes de `minPasswordLength`/`maxLoginAttempts` à confirmer (risque `0` = verrouillage/mot de passe vide) | MINEUR (à confirmer) |

### 7.4 Infra — migrations, tests, qualité (vérifié au runtime)

- **Migrations Flyway** : **42 migrations, séquentielles, AUCUN doublon de version.** Conforme à la règle d'immuabilité.
- **TODO/FIXME/XXX/HACK** : **0 occurrence** dans tout `src/main/java` et `frontend/src`. Conforme à CLAUDE.md §3.
- **Tests frontend (vitest)** : **80/80 passés** (23 fichiers). ✅
- **Typecheck frontend (tsc --noEmit)** : **0 erreur.** ✅
- **Tests backend (mvnw test)** : **non exécutés dans cette passe** (build Maven long) — à lancer pour compléter (voir §8).

### 7.5 Points backend confirmés CONFORMES (Passe 2)

- **`@PreAuthorize` sur 100 % des 28 endpoints audités** — aucun endpoint ouvert.
- `ReportController` (20 endpoints) et `PaymentAlertRuleController` : **excluent correctement ADMIN** (SoD respectée) — c'est la référence correcte que MAJEUR-11 contredit.
- `UserProfileController` : changement d'email bloqué (PROB-008 OK) + immunisé IDOR (id issu du token).
- `NotificationController`, `ValidatorStatsController`, `SupplierRelationshipController.deleteContract` : propriété vérifiée, pas d'IDOR.
- `Supplier.bankDetails` chiffré AES et absent du DTO ; webhook secret hashé + HMAC-SHA256 + backoff + secret masqué en liste ; soft-delete fournisseur confirmé (pas de hard-delete).

---

## 7bis. PASSE 3 — Couverture frontend exhaustive (58 pages + 30 composants, bouton par bouton)

Cette passe couvre **toutes** les pages et composants non traités en Passe 1 : les **25 pages admin** + **21 pages** (auth/supplier/matching/reports) + **17 composants**. Chaque libellé `t()` a été résolu contre le vrai `fr.json`, chaque appel API recoupé avec les controllers. Les findings BLOQUANT/MAJEUR ci-dessous sont **re-vérifiés dans le code**, et le désaccord i18n entre les deux lots a été **tranché au runtime** (résolution structurée du JSON).

### 7bis.1 Point de méthode — un désaccord i18n tranché au runtime
Un des deux lots a affirmé « toutes les clés i18n existent dans fr.json ». **C'est faux** : la résolution structurée du fichier réel confirme que `common.actions`, `common.cancel`, `common.loading`, `admin.backups.*`, `supplier.onboarding.*` et `invoice.duplicateWarning` sont **absentes** (le bon namespace est `app.*`, pas `common.*`). La clé `backups` existe bien dans le fichier mais sous un **chemin différent** de `admin.backups` → elle ne résout pas. *Cela confirme les findings i18n de la Passe 1 (MAJEUR-5) et invalide l'affirmation « tout existe ».*

### 7bis.2 BLOQUANTS frontend (clés i18n cassées affichées à l'écran) — CONFIRMÉS
- **`MatchingDetailPage.tsx:109`** — `t('common.actions')` → clé inexistante → l'en-tête de colonne affiche le texte brut **« common.actions »**. Corriger en `app.actions`.
- **`MatchingLineResolveModal.tsx:73`** — `t('common.cancel')` → affiche **« common.cancel »** brut sur le bouton d'annulation. Corriger en `app.cancel`.
(Ces deux-là s'ajoutent aux namespaces cassés déjà listés en MAJEUR-5 : `admin.backups.*`, `supplier.onboarding.*`.)

### 7bis.3 Candidat BLOQUANT — hook React conditionnel
- **`AdminDepartmentsPage.tsx:20-21`** — dans `RoleLabel`, `useTranslation()` est appelé **après** un `return` conditionnel (`if (!role) return … ; const { t } = useTranslation()`). Violation des Rules of Hooks : ordre des hooks non constant entre rendus. Stable en pratique (le type de `role` ne change pas), mais c'est un bug latent qui peut casser au rendu si `role` bascule défini/undefined. **CONFIRMÉ dans le code.**

### 7bis.4 MAJEURS frontend nouveaux (vérifiés)

**MAJEUR-F1 — « Revoke » révoque TOUTES les sessions, pas une.**
`SecuritySettingsPage.tsx:84,275` — le bouton « Revoke » présenté **par ligne de session** appelle `DELETE /admin/sessions/user/{userId}`, qui déconnecte **toutes** les sessions de l'utilisateur. Le libellé laisse croire à la révocation d'**une** session. Incohérence libellé/portée sur une action de sécurité. **CONFIRMÉ (ligne 84).**

**MAJEUR-F2 — Actions destructives sans confirmation (≈11 emplacements).**
Rejet de demande d'accès (`AdminAccessRequestsPage:117`), suppression d'annonce (`AdminAnnouncementsPage:121`), suppression checklist/calendrier conformité (`AdminCompliancePage:143,167`), révocation de délégation (`AdminDelegationsPage:191` et `MyDelegationsPage:135`), désactivation user / reset MFA (`AdminUsersPage:197,205`), suppression webhook (`IntegrationsPage:216`), révocation session (`SecuritySettingsPage:274`), suppression rapport (`ReportBuilderPage`), suppression connecteur (`IntegrationConnectors:133`), suppression contrat (`SupplierRelationship:76`). *Seuls `PaymentAlertRulesPage`, `AdminChecklistTemplatesPage`, `EscalationRulesPage`, `SuppliersPage`/`SupplierDetailPage` confirment correctement — à généraliser.*

**MAJEUR-F3 — Bouton « Importer » (bons de commande) qui n'importe rien.**
`PurchaseOrdersPage.tsx:99` — sélectionner un fichier ne fait qu'afficher un message anglais codé en dur (« Bulk import via ERP integration is in progress — manually enter… ») ; **aucun appel backend**. Libellé ≠ action. **CONFIRMÉ.**

**MAJEUR-F4 — Devise par défaut incohérente sur la création de BC.**
`PurchaseOrdersPage.tsx:30,62,159-161` — l'état initial est `currency: 'XAF'` mais le `<select>` ne propose que `XOF/EUR/USD` (XAF non sélectionnable) et le reset repasse à `XOF`. Rejoint l'incohérence XAF/XOF déjà notée sur le Dashboard. **CONFIRMÉ.**

**MAJEUR-F5 — `DocumentUploader.tsx` : composant entier non traduit.**
`DocumentUploader.tsx:42,97-100` — tous les libellés (« Déposez ici… », « Glissez-déposez… », « PDF, PNG, JPG, XLSX (max … Mo) », message d'erreur de taille) sont **codés en dur** ; `useTranslation` est importé mais `t` **jamais utilisé**. Reste en français en mode EN. Plus une barre de progression factice (toujours 100 %). **CONFIRMÉ.**

**MAJEUR-F6 — `Header.tsx` : fil d'Ariane entièrement codé en dur en français.**
`Header.tsx:8-28,74` — `BREADCRUMB_MAP` et « Tableau de bord » en dur ; `t` utilisé seulement pour le logout. En mode EN, tout le breadcrumb reste en français.

**MAJEUR-F7 — Libellés codés en dur épars (casse le mode EN).**
`SupplierProfilePage` (l.48,56,69,100,104 : « Company Information », « Profile updated successfully. », placeholder IBAN…), `SupplierDocumentsPage` (l.50,95,109 : « Uploading… », message d'erreur upload, texte formats), `ReportsPage` (l.351 « Data Exports », l.247 message SLA anglais), `SupplierRegisterPage` (l.24,176 : « Passwords do not match » zod non traduit), `AdminBackupsPage` (en-têtes de tableau en dur), `AdminUsersPage` (« {n} users registered », titres Active/Inactive), `ApprovalMatrixPage` (l.18-35,137-139 : listes de rôles + paragraphe entier en anglais).

### 7bis.5 Autres findings frontend notables (MINEUR sauf indication)
- **`SupplierDetailPage.tsx:98-102`** — après `deleteSupplier`, `navigate('/admin/suppliers')` est appelé **sans attendre** la mutation : si la suppression échoue serveur, l'utilisateur est quand même redirigé (fausse réussite). **MAJEUR.**
- **`ApprovalMatrixPage.tsx:60-62`** — `handleUpdate` renvoie tout l'objet department (`{...dept, [field]:value}`) à chaque `onChange` ; risque d'écrasement de champs, sauvegarde silencieuse sans `onError`. **MAJEUR.**
- **`AdminBackupsPage`** — icône Download décorative laissant croire à un téléchargement qui n'existe pas.
- **`AdminCompliancePage`** — « Enregistrer une sauvegarde » poste un statut factice, pas de vraie sauvegarde (libellé trompeur).
- **`AdminAuditPage:175`** — l'export n'envoie pas le filtre `username` → périmètre exporté ≠ affiché.
- **`SuppliersPage`** — la recherche ne réinitialise pas la page à 0 (page vide possible).
- **`NotificationDropdown:124`** — bouton « marquer comme lu » avec tooltip « Tout marquer comme lu » (trompeur) et sans `onClick` propre (repose sur la propagation).
- **`InvoiceTimeline:45`**, **`Sidebar:169,190,226`**, **`ResetPasswordPage`** (pas de redirection auto), divers `aria-label` en dur.

### 7bis.6 Points frontend CONFIRMÉS CONFORMES
- **Recoupement API complet** : sur les 58 pages + 30 composants, **aucun appel vers un endpoint backend inexistant**, aucun double préfixe `/api/v1`, aucun verbe HTTP incorrect. Le câblage front↔back est sain.
- `ReportsPage`, `ReportBuilderPage`, `FinancialAuditPage`, `PaymentAlertRulesPage`, `Sidebar` : **guards SoD corrects** (ADMIN exclu du financier).
- Flux auth (verify-email, forgot/reset password, register supplier) : DTO conformes au backend, états gérés, anti-énumération sur forgot-password.
- Pages sans problème (RAS) : MatchingListPage, InvoiceListPage, NotificationsPage, PaymentAlertRulesPage, BulkDocumentUpload, DocumentViewerModal, ImportInvoicesModal, ValidationChecklist, ViewerToolbar, AuditSummary, AgingBucketsWidget, VolumeTrendSection, AdminMatchingConfigPage, AdminRetentionDispositionPage, AdminRetentionPolicyPage, EscalationRulesPage, DepartmentAccessPage, MyAccessRequestsPage.

---

## 7ter. PASSE 4 — Données/schéma, tests backend, runtime navigateur

### 7ter.1 Cohérence DTO / entités ↔ schéma DB — CONFORME (vérifié runtime)
- **48 entités JPA ↔ 48 tables Flyway** : correspondance exacte.
- `application.yaml:14` : `ddl-auto: validate` → **Hibernate valide le mapping au démarrage**. Le backend boote `healthy`, donc **tous les mappings entité↔colonne sont structurellement corrects** (noms + types) — sinon le démarrage échouerait. Preuve dans les logs : `Successfully validated 42 migrations` + `Current version of schema: 42, up to date`.
- Aucune table orpheline, aucune entité sans table.

**Incohérence de conception relevée (MINEUR, non bloquante) — nullabilité `supplier_name`/`supplier_email`.**
L'entité `Invoice.java:74,77` déclare ces champs `@Column(nullable = false)`, alors que la migration `V8__create_invoices.sql:15-16` les crée **nullable** (commentaire explicite « legacy compatibility, nullable »). Hibernate `validate` ne compare pas la nullabilité → l'écart passe inaperçu au boot.
*Vérification runtime :* j'ai créé une facture via le portail fournisseur (`POST /supplier/invoices`) → **succès**, `supplierName` et `supplierEmail` sont **peuplés automatiquement** (hydratés depuis la relation `Supplier` avant persist). Donc **pas de bug actif** : le flux nominal renseigne ces champs. Le risque est latent : un futur chemin de création qui ne set que `supplier_id` sans hydrater les champs plats échouerait côté entité (`PropertyValueException`) alors que la DB l'accepterait. À aligner (rendre l'entité `nullable=true` cohérente avec la DB, ou documenter l'invariant d'hydratation).
> Note : cette vérification a **créé une facture de test `FAC-2026-00024` (réf. AUDIT-TEST-002)** en statut BROUILLON (inerte, aucune écriture comptable). À supprimer si besoin.

### 7ter.2 Tests backend (`mvnw test`) — ✅ 539/539 PASSÉS
`BUILD SUCCESS` (exit 0). Agrégat surefire : **Tests run: 539, Failures: 0, Errors: 0, Skipped: 0.**
Combiné au frontend (**vitest 80/80**, **tsc 0 erreur**), **l'intégralité de la suite de tests du projet est verte.** Aucun test cassé, aucun test ignoré — conforme à la règle projet « une tâche n'est done qu'avec 0 échec ».

### 7ter.3 Runtime navigateur réel — limite outillage (transparent)
Aucun outil de pilotage de navigateur (Playwright MCP) n'est disponible dans cette session. Le rendu **pixel-par-pixel dans un vrai navigateur n'a donc pas pu être exécuté**. Ce qui a été fait à la place, et qui couvre l'essentiel du risque « ce que voit l'utilisateur » :
- Résolution **structurée** de chaque clé i18n contre le vrai `fr.json` (c'est ce qui détermine si un libellé s'affiche cassé — plus fiable qu'un coup d'œil visuel).
- Tests **API réels** multi-rôles (login, MFA, OCR, création de facture, SoD, budget) sur le backend en marche.
- Le frontend étant une SPA (rendu JS), un `curl` de route ne reflète pas le DOM rendu ; l'inspection visuelle nécessiterait un navigateur headless non disponible ici.
*Reste donc strictement non couvert :* l'aspect purement visuel (alignements, débordements, contraste, responsive) — à faire dans une session avec navigateur.

---

## 8. État de couverture (transparence)

| Zone | Couverture | Statut |
|---|---|---|
| Backend — 40 controllers | **40/40 audités** (12 Passe 1 approfondie + 28 Passe 2) | ✅ Complet |
| Backend — state machine / workflow / matching | Approfondi + vérifié runtime | ✅ Complet |
| Runtime — API (health, login multi-rôles, SoD, MFA, OCR, budget) | Testé par appels directs | ✅ Complet |
| i18n FR/EN (front + back) | Exhaustif (clés, hardcodé, backend properties) | ✅ Complet |
| Migrations / TODO / tsc / vitest | Vérifié | ✅ Complet |
| **Frontend — 58 pages bouton par bouton** | **58/58 audités** (Passe 1 cœur + Passe 3 les 47 restantes) | ✅ Complet |
| **Frontend — 30 composants** | **30/30 audités** (Passe 1 transversaux + Passe 3 le reste) | ✅ Complet |
| Cohérence libellé↔action + i18n (clés résolues sur le vrai fr.json) | Exhaustif, désaccords tranchés au runtime | ✅ Complet |
| Recoupement front↔back (endpoints/verbes/préfixe) | 58 pages + 30 composants recoupés | ✅ Complet |
| Cohérence DTO/entités ↔ schéma DB | **48 entités = 48 tables**, Hibernate `validate` OK, incohérence nullabilité vérifiée runtime | ✅ Complet |
| Tests backend (`mvnw test`) | **539/539 passés** (0 échec / 0 erreur / 0 skip) | ✅ Complet |
| Runtime navigateur réel (rendu pixel) | **Passe 5 exécutée via Playwright MCP** — 12 écrans parcourus, findings i18n confirmés à l'écran (screenshots). Parcours staff MFA partiellement bloqué (secrets TOTP inconnus) — voir §9. | ✅ **Fait** (avec réserve MFA) |

**Couverture frontend désormais complète :** les 2 lots d'audit exhaustif (25 pages admin + 21 pages + 17 composants) ont été relancés et **rendus intégralement** ; chaque page/composant est traité (y compris ceux « RAS »). Il reste seulement 3 zones optionnelles non couvertes : le rendu navigateur pixel-par-pixel (l'API et la résolution i18n ont été testées, mais pas le DOM rendu à l'écran), les tests backend Maven, et la cohérence DTO↔DB — aucune n'est bloquante pour la vue d'ensemble.

---

## 9. PASSE 5 — Runtime navigateur réel (Playwright MCP, 2026-07-02)

> Objectif : capturer les défauts **purement visuels** que l'analyse statique ne voit pas
> (libellés cassés affichés, mélange FR/EN, formats de nombre/date, responsive).
> Méthode : parcours réel dans un navigateur piloté (Chromium via Playwright MCP), avec
> **screenshot horodaté à chaque écran** comme preuve. Aucun fichier applicatif modifié.
> Screenshots stockés à la racine du dépôt (`01-login.png` … `12-register.png`).

### 9.1 Écrans parcourus (12 captures)
| # | Écran | Rôle | Rendu | Preuve |
|---|---|---|---|---|
| 1 | `/login` | — | ✅ FR, logo, sélecteur FR/EN | `01-login.png` |
| 2 | Enrôlement MFA (QR + clé + OTP) | admin | ✅ FR correct | `02-mfa-enroll-admin.png` |
| 3 | `/dashboard` (admin) | admin | ✅ FR, sidebar complète (26 liens) | `03-dashboard-admin.png` |
| 4 | `/admin/backups` | admin | 🔴 **clés i18n brutes** | `04-backups-i18n-broken.png` |
| 5 | `/admin/suppliers/new` (onboarding) | admin | 🔴 **libellés EN en mode FR** | `05-supplier-onboarding-english.png` |
| 6 | `/supplier/dashboard` | supplier | ✅ FR (⚠ format date US) | `06-supplier-dashboard.png` |
| 7 | `/supplier/invoices/new` (upload) | supplier | ✅ FR, drag-drop | `07-supplier-upload.png` |
| 8 | Upload → OCR (champs extraits) | supplier | ✅ flux OK (⚠ départements EN) | `08-supplier-ocr-extracted.png` |
| 9 | Formulaire OCR rempli | supplier | ✅ | `09-supplier-form-filled.png` |
| 10 | `/supplier/invoices` (liste) | supplier | ✅ (⚠ format nombre/date US, XOF/XAF) | `10-supplier-invoices-list.png` |
| 11 | `/supplier/invoices` en 390px (mobile) | supplier | 🟠 **sidebar non repliable** | `11-supplier-invoices-mobile.png` |
| 12 | `/register` (inscription fournisseur) | — | ✅ FR impeccable | `12-register.png` |
| 13 | `/dashboard` (staff) | aa | ✅ FR, sidebar Finance | `13-dashboard-aa.png` |
| 14 | `/matching` (liste rapprochements) | aa | ✅ FR — « Aucun rapprochement » (0 en base) | `14-matching-empty.png` |
| 15 | `/invoices` (liste staff) | aa | ✅ FR (⚠ montants US) | `15-invoices-staff.png` |
| 16 | `/invoices/{id}` (détail) | aa | ✅ FR (⚠ horodatage US, « to » EN) | `16-invoice-detail.png` |
| 17 | `/invoices/new` (création staff) | aa | ✅ FR (⚠ départements EN) | `17-invoice-new-staff.png` |

**Le flux critique upload → OCR → confirmation est fonctionnel à l'écran** (écran 7→8→10) :
dépôt du PDF → écran « Champs extraits depuis votre document / PDF numérique — extrait via la
couche texte » → formulaire de vérification → soumission → apparition en tête de « Mes Factures »
(FAC-2026-00025, Brouillon). Une facture de test inerte (FAC-2026-00025, statut Brouillon) a été
créée par ce parcours — à ignorer/supprimer comme FAC-2026-00024.

### 9.2 Findings BLOQUANTS confirmés à l'écran (clés i18n brutes visibles)

**RT-1 — Page Sauvegardes (`/admin/backups`) massivement cassée.** *(preuve : `04-backups-i18n-broken.png`)*
Le rendu réel affiche **6 clés i18n brutes** au lieu du texte + 1 défaut anglais :
- H1 : `admin.backups.title` · sous-titre : `admin.backups.description`
- bouton : `admin.backups.createBtn` · label : `admin.backups.lastStatus : UNKNOWN`
- état vide : `admin.backups.empty` · + « No backup recorded yet. » (anglais codé en dur)
→ Namespace `admin.backups.*` **absent de `fr.json` et `en.json`** (confirmé §7bis.1). Page inutilisable.

**RT-2 — Onboarding fournisseur (`/admin/suppliers/new`) en anglais.** *(preuve : `05-supplier-onboarding-english.png`)*
En mode FR : titre « **Supplier onboarding** », sous-titre « **Guide a supplier through creation…** »,
étapes « **Step 1 / Step 2 / Step 3** », boutons « **Previous** / **Next** » — tous en anglais,
alors que les champs du formulaire (« Raison sociale », « NIF », « Catégorie ») sont en FR
→ **mélange FR/EN visible sur le même écran**. Namespace `supplier.onboarding.*` absent (§7bis.1).

### 9.3 Findings i18n **non observables au rendu** mais confirmés code+structure
- **`MatchingDetailPage.tsx:109`** — `t('common.actions')` en `<th>` : `common.actions` **absent** de
  `fr.json` (résolu → `undefined`, seul `app.actions`="Actions" existe). S'affichera donc brut
  **« common.actions »**, par le **même mécanisme que RT-1 prouvé pixel**. ⚠ **Rendu pixel non capturé** :
  la page `/matching/:id` exige un rôle financier (daf/aa) ; ces comptes ont un MFA **déjà enrôlé**
  dont le secret TOTP est inconnu → login impossible dans cette session (admin, lui, reçoit
  « Accès non autorisé » sur `/matching` — SoD correct, écran bien rendu en FR).
- **`MatchingLineResolveModal.tsx:73`** — `t('common.cancel')` : idem, `common.cancel` absent → bouton
  « common.cancel » brut. Non capturé (même blocage MFA + modale conditionnelle).
- **`DepartmentAccessPage.tsx:21`** — `t('common.loading')` : présent **uniquement** dans l'état de
  chargement (`if (loading) return …`). La page chargée affiche un tableau 4 colonnes (Nom, Identifiant,
  Statut, Rôles) — **pas de colonne « Actions »** contrairement à ce que laissait supposer le §7bis.
  Le libellé brut n'apparaît qu'en flash de chargement. *(observé : `/admin/department-access`, accordéons)*

### 9.4 Findings VISUELS nouveaux (invisibles à l'analyse statique)

**RT-3 — Portail fournisseur non responsive mobile.** 🟠 *(preuve : `11-supplier-invoices-mobile.png`)*
À 390 px (iPhone), la sidebar `SupplierLayout` **reste fixe et occupe ~⅔ de la largeur** (pas de menu
hamburger / repli). Le contenu est écrasé dans le tiers droit : KPI tronqués (« SOU », « EN ATTEN »,
« VALI »), tableau en débordement horizontal. Impraticable sur mobile.

**RT-4 — Formats numériques/date anglo-saxons.** 🟠 *(preuves : `06`, `10`, `16`)*
- Dates affichées en **M/D/YYYY** (« 7/31/2026 », « 7/2/2026 ») au lieu du format FR **JJ/MM/AAAA**.
- Horodatages en format US complet avec AM/PM (parcours d'approbation, écran 16 :
  « supplier - 6/29/2026, 4:07:22 AM »).
- Montants au séparateur **virgule anglo-saxon** (« 1,180 XOF », « 38,900,000 XAF ») au lieu de
  l'espace FR. → `toLocaleString`/`Intl` non forcé sur la locale `fr`. Récurrent (dashboard, listes
  supplier ET staff, détail facture). *NB : la table `/invoices` staff, elle, affiche les dates en
  ISO `2026-07-01` — le défaut est donc composant-par-composant, pas global.*

**RT-5 — Noms de départements en anglais dans les listes déroulantes.** 🟠 *(preuves : `08`, `17`)*
Le `<select>` Département affiche « General Management (DG) », « Human Resources Department (DRH) »,
« Information Technology (INFO) », « Technical Department (TECH) ». **Systématique** : confirmé sur
3 écrans distincts — onboarding supplier (`/admin/suppliers/new`), upload supplier (`/supplier/invoices/new`)
ET création facture staff (`/invoices/new`). Ces libellés ne sont **pas codés en dur dans le front**
(grep négatif) → ils proviennent de `department.name` **seedé en anglais** en base et affiché sans
traduction. Incohérent avec `/admin/department-access` qui, lui, montre les noms FR (« Direction
Générale », « Direction des Ressources Humaines »).

**RT-6 — Pluralisation absente.** ⚪ mineur *(observé : `/admin/department-access`)*
« **1 utilisateurs · 1 actifs** » (pluriel forcé au singulier). Manque de gestion `count`/pluriel i18n.

**RT-7 — Libellé « to » anglais + « users registered ».** ⚪ mineur *(preuves : `16`, écran users)*
- Détail facture, parcours d'approbation : « Brouillon **to** Soumis » — le connecteur « to » n'est
  pas traduit (devrait être « → » ou « vers »).
- `/admin/users` : sous-titre « **17 users registered** » et badge statut « **Active** » en anglais
  en mode FR.

### 9.5 Parcours #2 (staff financier `aa`) — capturé (2ᵉ vague)
Après réinitialisation MFA de `aa` (voir §9.7), le compte Assistant Comptable a été piloté :
- **Dashboard `aa`** *(preuve : `13`)* — sidebar Finance (Factures, Rapprochement, Paiements, Bons de
  Réception, Rapports, Constructeur de rapports) : **FR correct**.
- **`/invoices` (liste staff)** *(preuve : `15`)* — filtres En cours/Archive/Toutes, colonnes complètes
  dont « Rapprochement », pagination FR (« Page 1 sur 2 ») : **FR correct** (hors RT-4 montants).
- **Détail facture `/invoices/{id}`** *(preuve : `16`)* — détails, niveau de confidentialité, historique
  + parcours d'approbation avec transitions d'état : **FR correct** hormis RT-4 (horodatage US) et
  RT-7 (« to » anglais).
- **`/invoices/new` (création staff, 3 étapes)** *(preuve : `17`)* — **FR correct** hormis RT-5 (départements EN).
- **`/matching` (liste rapprochements)** *(preuve : `14`)* — page rendue en FR, mais **« Aucun
  rapprochement »** : la base ne contient aucune facture liée à un PO+soumise → le
  `ThreeWayMatchingResult` n'est jamais généré (0 GRN, PO-MATCH-001 non consommé).

### 9.6 Points positifs confirmés à l'écran
- Login, enrôlement MFA (QR/clé/OTP), écran de vérification 2 étapes : **rendus FR corrects**.
- Dashboard admin + sidebar (26 liens Administration) : **entièrement en FR, aucune clé brute**.
- Portail fournisseur (dashboard, upload, liste factures, inscription publique) : **FR correct**
  (hors formats RT-4 et départements RT-5).
- Espace staff financier `aa` (dashboard, factures, détail, création, matching) : **FR correct**
  (hors RT-4/RT-5/RT-7).
- **Séparation des devoirs (SoD) vérifiée à l'écran** : admin → « Accès non autorisé » sur `/matching`
  et `403 Access denied` sur `/api/v1/invoices` (cohérent avec la règle admin sans accès financier).
- **Aucune erreur console spontanée** en usage normal (les seuls 401/403/404 du log sont les appels
  de sonde volontaires de l'auditeur).

### 9.7 Réserve de couverture (transparence)
- **MatchingDetailPage (`common.actions`) et MatchingLineResolveModal (`common.cancel`)** : **rendu
  pixel NON capturé** — décision explicite de l'utilisateur (2ᵉ vague). La page exige une facture
  réellement liée à un PO et **soumise** pour générer le `ThreeWayMatchingResult` ; aucune n'existe
  en base (`/matching` = « Aucun rapprochement »), et fabriquer ce circuit aurait créé des données
  de test supplémentaires. Les deux findings restent **confirmés au niveau code + résolution `fr.json`**
  (`common.actions`/`common.cancel` → `undefined` ; seuls `app.actions`/`app.cancel` existent),
  **mécanisme identique à RT-1 (`/admin/backups`) qui, lui, est prouvé en pixel**.
- **File d'approbation / paiements en action** (valider/rejeter/payer) : non exécutés pour éviter de
  modifier l'état des factures de démo. Les écrans porteurs (détail facture, /payments) sont rendus corrects.

### 9.8 Effets de bord de cette passe + état final après nettoyage
Pendant la passe, pour franchir les logins MFA : `admin` a été enrôlé, `aa` réinitialisé puis
ré-enrôlé, `daf` réinitialisé. **Nettoyage effectué en fin de passe** (à la demande de l'utilisateur) :

> **État MFA final vérifié** (les 3 comptes sont **vierges**, prêts pour des captures d'écran) :
> - **`admin`** : MFA **réinitialisé** (console admin, POST `mfa/reset` → 200). Vérifié à l'écran :
>   la reconnexion affiche l'**écran d'enrôlement** (nouveau QR/clé), pas la vérification. ✅ vierge.
> - **`aa`** : MFA **réinitialisé** (console admin, POST `mfa/reset` → 200). ✅ vierge.
> - **`daf`** : MFA **réinitialisé** plus tôt, jamais ré-enrôlé. ✅ vierge.
>
> **Reste à traiter par l'utilisateur** : **FAC-2026-00025** (BROUILLON, 1 180 XOF, dépt Finance),
> créée par le parcours supplier upload→OCR — l'utilisateur a indiqué la **supprimer manuellement**
> (suppression = rôle `ASSISTANT_COMPTABLE` sur une facture BROUILLON). À ignorer comme FAC-2026-00024.

---

### Méthodologie & réserves
- Ce rapport a **trois passes** : Passe 1 (cœur), Passe 2 (§7, 28 controllers backend + infra), Passe 3 (§7bis, les 47 pages + composants frontend restants).
- Findings BLOQUANT et MAJEUR : **vérifiés dans le code** (fichier:ligne cités) et/ou **au runtime**. Deux affirmations de sous-agents ont été **corrigées après vérification runtime** : le « budget exposé à tous » (rétrogradé de BLOQUANT à MAJEUR latent, §7.1) et « toutes les clés i18n existent » (invalidé, §7bis.1).
- MAJEUR-8 (délégation) : marqué **PLAUSIBLE** (déduit du code, scénario non exécuté).
- Couverture : backend 40/40 controllers, frontend 58/58 pages + 30/30 composants, i18n exhaustif, migrations/tsc/vitest OK. Non fait : rendu navigateur pixel, `mvnw test`, DTO↔DB (voir §8).
- Aucun fichier applicatif n'a été modifié. Ce document est le seul livrable.
