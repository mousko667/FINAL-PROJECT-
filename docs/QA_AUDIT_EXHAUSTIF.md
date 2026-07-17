# Audit exhaustif — OCT Invoice System (piloté par le cahier des charges)

> **Méthode.** 6 agents d'exploration parallèles (workflow/state-machine, séparation des devoirs
> sur 40 controllers, câblage front↔back, tokens CSS/agencement, i18n/données, dashboards/nav par
> rôle) balayant **tout** le code, puis **confirmation runtime** (Playwright + API) des cas les plus
> graves. Source de vérité = `docs/WORKFLOW.md`, `docs/PRD.md`, `docs/REQUIREMENTS-MATRIX.md`.
>
> **Ce document complète** `QA_SPEC_VS_REEL_REPORT.md` (workflow AA, cumul DAF, admin↔fournisseurs,
> audit `ACCESS_DENIED`, tokens, PO, notifications, largeurs) : les écarts déjà décrits là-bas ne
> sont pas répétés ; ci-dessous, **les défauts NOUVEAUX** trouvés en balayant l'ensemble du système.
>
> **Date : 2026-07-17.** Audit d'état — aucune correction appliquée. Chaque point = `file:line` +
> (quand applicable) preuve runtime capturée.

---

## Récapitulatif par gravité (nouveaux findings)

| # | Gravité | Domaine | Écart | Preuve |
|---|:------:|---------|-------|--------|
| N1 | 🔴 | Workflow | `InvoiceValidationService` = **code mort** : garde version resubmit, matching-au-resubmit non branchés | code + runtime |
| N2 | 🔴 | Workflow | Rejet **DOUBLON impossible** (`[DOUBLON]`=9 car. < garde 10) | **runtime : 400 vs 200** |
| N3 | 🔴 | SoD | `/audit-logs` (+ `/export`) **sans scope** → DAF voit le journal **système**, admin voit le **financier** | **runtime confirmé** |
| N4 | 🟠 | Nav/Sécurité | **18 pages admin sans `PageRoleGuard`** → tout staff ouvre `/admin/*` par URL (données sauvées par le back seul) | **runtime : drh ouvre /admin/users** |
| N5 | 🟠 | Workflow | Notifs internes rejet/BAP → **fournisseur** au lieu de l'AA (factures portail) | **code CONFIRMÉ** |
| N6 | 🟠 | Workflow | **BON_A_PAYER ne notifie pas le fournisseur** (viole matrice §7) | **code CONFIRMÉ** |
| N7 | 🟠 | SoD | DAF a accès large au **référentiel fournisseur** (liste/export/docs/contrats/comms) | code |
| N8 | 🟠 | Câblage | 4 endpoints rapports **sans UI** + **détail GRN** + **édition annonce** inaccessibles | code |
| N9 | 🔴 | SoD | Validateurs **sans cloisonnement départemental** (lecture/export factures/matching d'autres dépts) | **runtime CONFIRMÉ (rehaussé 🟠→🔴)** |
| N10 | 🟡 | Workflow | Assignation **N2 hors state-machine** ; branche N1 2-niveaux inatteignable | code |
| N11 | 🟡 | UI | Bug token `primary` : **~62 fichiers** (`text-primary` 62, `bg-primary` 53, `border-primary` 13) | CSS compilé |
| N12 | 🟡 | i18n | ~5 pages admin : dates en **format US** en mode EN (`toLocaleString(i18n.language)`) | code |
| N13 | 🟠 | Workflow | Délégation `deptCode="DAF"` accorde le Bon à Payer à un non-DAF ; création sans validation du deptCode | **code CONFIRMÉ (rehaussé 🟡→🟠)** |

### Findings ajoutés dans cette passe (2026-07-17, section B — 3 axes rebalayés)

| # | Gravité | Domaine | Écart | Preuve |
|---|:------:|---------|-------|--------|
| N14 | 🔴 | Nav/SoD | `InvoiceCreatePage` (« Saisir facture ») **sans aucune garde de rôle** → DAF/admin/validateur ouvrent le formulaire de saisie AA | code |
| N15 | 🔴 | Nav/SoD | Pages **fournisseur admin** (`SuppliersPage` + detail/form/onboarding) sans `PageRoleGuard`, et **admin traité comme rôle habilité fournisseur** (bouton « nouveau fournisseur ») | code |
| N16 | 🔴 | Nav/SoD | Dashboard admin : **quick-action + entrée de menu « Fournisseurs »** proposées à l'admin (viole « admin = ZÉRO fournisseur ») | code |
| N17 | 🔴 | i18n | `GlobalExceptionHandler.resolve()` **ne traduit jamais un message contenant un espace** → ~40 messages métier en **anglais brut**, identiques FR/EN | **runtime CONFIRMÉ** |
| N18 | 🟠 | i18n | `error.access_denied` **absente de `messages_fr`** → tout accès refusé renvoie l'anglais brut aux francophones | **runtime CONFIRMÉ** |
| N19 | 🟠 | UI | CTA `bg-primary text-primary-foreground` **invisibles** (double token cassé) : login, confirmation, ErrorBoundary, indicateur d'étape | CSS + code |
| N20 | 🟠 | UI | Tables larges en `overflow-hidden` → **colonnes coupées** (ApprovalMatrix, AdminAudit, Integrations, Payments) ; ~26 tables sans wrapper scrollable | code |
| N21 | 🟠 | Nav | `ApprovalQueuePage` + `InvoiceListPage` sans `PageRoleGuard` (admin atteint file d'approbation / liste factures par URL) | code |
| N22 | 🟡 | UI | Sidebar archives `w-64 shrink-0` non-responsive + item sélectionné invisible (token) ; `max-w-*` incohérents (2xl→5xl + pleine largeur) sur 17 pages | code |
| N23 | 🟡 | i18n | Bean-Validation non résolue (`handleValidationException`/`ConstraintViolation`) → clés `validation.*`/`webhook.*` brutes ; DTO paiement + délégations en littéral (EN et FR figés) | code |
| N24 | 🟡 | i18n/UI | Libellés figés JSX (`Notes`, `Total`, `Actions`, `Description`…) ; KPI `avgProcessingTime` suffixe `j.` figé sur Dashboard vs `t()` sur Reports ; `report.pdf.executive.title` absente de FR | code |
| N25 | 🟡 | Matching | Matching 3-way (financier) ouvert à **tous les validateurs N1/N2** (`STAFF_ROLES`) — spec = activité AA/DAF | code |

---

## 🔴 CRITIQUES

### N1 — `InvoiceValidationService` est du code mort : les règles resubmit/version/matching-au-resubmit ne s'exécutent jamais

- **Constat.** Les méthodes `validateResubmissionVersion`, `validateApproverIsNotSubmitter`,
  `validateDafApprover`, `validateRejectionReason`, `validateArchiveIsAutomatic`
  ([`InvoiceValidationService.java:51-102`](../src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceValidationService.java#L51))
  n'ont **aucun appelant** dans `src/main` (seuls les tests les référencent). Ce `@Service` censé
  centraliser les 10 règles métier de WORKFLOW §8 est **décoratif**.
- **Conséquence directe — resoumission sans contrôle (WORKFLOW §4/§8-4).** La transition
  `REJETE → SOUMIS` ([`StateMachineConfig.java:114-117`](../src/main/java/com/oct/invoicesystem/config/StateMachineConfig.java#L114))
  n'a **aucun `.guard()`**, et `resubmitInvoice`
  ([`InvoiceController.java:302`](../src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java#L302))
  n'appelle aucune validation de version → une facture rejetée peut être **renvoyée en boucle sans
  la moindre correction**.
- **Conséquence — matching MISMATCH non re-vérifié au resubmit** (CLAUDE.md Phase 9 / WORKFLOW §11) :
  le bloc matching/doublon est gardé `if (event == SUBMIT)` seulement
  ([`InvoiceStateMachineServiceImpl.java:84-93`](../src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java#L84))
  → une facture MISMATCH rejetée puis resoumise repasse **sans override DAF/ADMIN**.
- **Risque.** Contournement complet des règles anti-fraude sur le chemin rejet→resoumission.
- **Correctif.** Brancher `InvoiceValidationService` sur les transitions réelles (submit, resubmit,
  reject, validate, bon_a_payer) ou ajouter les gardes correspondantes à la state machine.

### N2 — Rejeter une facture pour « Doublon » est cassé (motif ≥10 caractères mal appliqué)

- **Constat.** Le contrôle « motif ≥10 caractères » (WORKFLOW §8-2) s'applique à la **chaîne composée
  `[CODE]`**, pas au motif humain. `[DOUBLON]` = **9 caractères** → sous le seuil → exception.
  `ApprovalController.java:122-130` ne valide la longueur que si `reasonCode==AUTRE` ; le
  `RejectionReasonGuard.java:24` re-teste `≥10` sur `[CODE]`.
- **Preuve runtime (même facture, même instant) :**
  - `POST …/workflow/reject {"reasonCode":"DOUBLON"}` → **HTTP 400** (`Transition denied for event REJECT`).
  - `POST …/workflow/reject {"reasonCode":"MONTANT_INCORRECT"}` → **HTTP 200** ✅.
  - Seule différence : longueur du libellé (`[DOUBLON]` 9 car. vs `[MONTANT_INCORRECT]` 19 car.).
- **Risque.** Un des motifs de rejet les plus courants (doublon) est **inutilisable** sans texte
  libre. Symétriquement, la règle §8-2 est « satisfaite » par le padding des crochets, pas par un
  vrai motif — l'intention est violée dans les deux sens.
- **Correctif.** Appliquer la longueur minimale au **détail humain** (ou retirer la contrainte de
  longueur quand un code prédéfini est fourni).

### N3 — Le journal d'audit combiné fuit dans les deux sens (pas de séparation système/financier)

- **Constat.** `GET /audit-logs` et `GET /audit-logs/export`
  ([`AuditController.java:103-104` et `:129-130`](../src/main/java/com/oct/invoicesystem/domain/audit/controller/AuditController.java#L103))
  sont gardés `hasRole('ADMIN') or hasRole('DAF')` et appellent `searchLogs(...)` **sans aucun
  filtre de scope** (contrairement à `/system`, `/financial`, `/summary/export` qui filtrent).
- **Preuve runtime :** `GET /audit-logs` renvoie un contenu **identique** pour admin et DAF,
  contenant des événements **système** (`LOGIN`, `USER_CREATE`, `USER_UPDATE`, `SECURITY`,
  `HTTP_REQUEST`). Donc le **DAF lit le journal système** (réservé admin, PRD L76) et l'admin lit le
  financier (viole « admin zéro accès financier », PRD L40). Exportable en fichier.
- **Correctif.** Router `/audit-logs` et `/export` vers `searchLogsWithActionFilter` avec la liste
  d'actions correspondant au rôle courant (comme `/summary/export`).

---

## 🟠 MAJEURS

### N4 — 18 pages admin sans garde de rôle au niveau page (défense en profondeur cassée)

- **Constat.** 18 pages sous `pages/admin/` n'ont **aucun** `PageRoleGuard`/`RoleGuard`
  (AdminUsersPage, SecuritySettingsPage, AdminPermissionMatrixPage, IntegrationsPage, SuppliersPage,
  AdminAuditPage, AdminCompliancePage, AdminDepartmentsPage, ApprovalMatrixPage, DepartmentAccessPage,
  AdminAnnouncementsPage, AdminChecklistTemplatesPage, AdminAccessRequestsPage, SupplierDetailPage,
  SupplierFormPage, SupplierOnboardingPage, AdminUserFormPage, AdminDepartmentFormPage).
  `ProtectedRoute` ne vérifie que « authentifié » + supplier↔staff, **pas** le rôle admin.
- **Preuve runtime.** Connecté en **drh (validateur N1)**, l'ouverture par URL de `/admin/users`,
  `/admin/security`, `/admin/permissions`, `/admin/integrations` **affiche la page admin** (pas de
  redirection, pas de message « Accès non autorisé »). Seul le **backend** sauve la mise : les
  appels de données renvoient **403** (`/users=403`, `/admin/sessions=403`, `/roles=403`,
  `/integrations/connectors=403`).
- **Risque.** L'interface admin (structure, formulaires, actions) est **exposée** à tout staff ;
  la seule barrière est le back — un unique `@PreAuthorize` oublié = fuite directe. Contraste : les
  pages financières (`ReportsPage`, `PaymentsPage`…) **ont** leur `PageRoleGuard`.
- **Correctif.** Ajouter `<PageRoleGuard allowedRoles={['ROLE_ADMIN']}>` aux 18 pages.

### N5 — Notifications internes « rejet » et « Bon à Payer » envoyées au fournisseur au lieu de l'Assistant Comptable — 🟠 CONFIRMÉ (code)

- **Constat (chemin réel corrigé).** Les listeners vivent dans
  `.../notification/event/listener/` (le doc initial disait `.../listener/`). `onInvoiceRejected`
  ([`EmailNotificationListener.java:94`](../src/main/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListener.java#L94))
  notifie `invoice.getSubmittedBy()` comme « l'AA submitter », puis `notifySupplier(...)` en plus
  ([`:100`](../src/main/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListener.java#L100)).
- **Preuve code du couplage.** Pour une facture portail, `SupplierPortalController.toInvoice`
  ([`:259-280`](../src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java#L259))
  fait `.submittedBy(actor)` où `actor.setId(actorId)` et l'appelant passe `user.getId()` du compte
  **fournisseur** connecté ([`:82`](../src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java#L82)).
  Donc `submittedBy = compte fournisseur` → sur rejet d'une facture portail, le fournisseur reçoit
  **deux** mails (submitter + supplier) et **aucun ASSISTANT_COMPTABLE** n'est ciblé (le side-effect
  §4 « Notify ASSISTANT_COMPTABLE to process payment » manque sa cible).
- **Preuve runtime (partielle).** Repro d'un cycle portail neuf **bloquée** : le compte de test
  `supplier` renvoie `POST /supplier/invoices` → 400 « Supplier not found or deleted » (fournisseur
  seedé soft-deleted/absent). Le mécanisme reste établi de façon déterministe par la lecture de code
  ci-dessus ; une repro MailHog nécessiterait de réparer le seed fournisseur (hors périmètre d'un
  audit d'état).
- **Correctif.** Router ces notifications internes vers le(s) compte(s) ASSISTANT_COMPTABLE, pas
  vers `submittedBy`.

### N6 — BON_A_PAYER ne notifie pas le fournisseur (viole la matrice §7) — 🟠 CONFIRMÉ (code)

- **Constat.** `onBonAPayer` ne notifie que `submittedBy`
  ([`EmailNotificationListener.java:106-117`](../src/main/java/com/oct/invoicesystem/domain/notification/event/listener/EmailNotificationListener.java#L106)) —
  **aucun `notifySupplier(...)`**, contrairement à `onInvoiceRejected` (`:100`) et `onInvoicePayed`
  (`:130`). Or §7 (Supplier) exige « Invoice approved (BON_A_PAYER) → Supplier ».
- **Risque.** Le fournisseur n'est jamais informé de l'approbation de sa facture. Cumulé à N5 : sur
  une facture portail, le BAP notifie `submittedBy` = le fournisseur en tant que « submitter » mais
  via le mauvais template interne, et **jamais** via le template fournisseur — l'AA n'est pas prévenu
  de payer.

### N7 — Le DAF a un accès large au référentiel fournisseur (confidentiel)

- **Constat.** `GET /suppliers`, `/{id}`, `/export`, `/{id}/documents`, `/{id}/performance`
  ([`SupplierController.java:74-95,163-171`](../src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java#L74))
  et `/contracts`, `/communications`
  ([`SupplierRelationshipController.java:32,57`](../src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierRelationshipController.java#L32))
  incluent `DAF`. La gestion fournisseur relève de l'AA (données confidentielles) ; le DAF fait
  approbation/BAP/audit financier.
- **Correctif.** Retirer `DAF` de ces lectures (comme on retire `ADMIN`). *(Ta consigne : ne rien
  laisser à l'admin — ce point ajoute le volet DAF.)*

### N8 — Fonctionnalités backend terminées mais inaccessibles depuis l'UI

- `ReportController` **`/reports/summary`** (L53), **`/reports/activity`** (L60),
  **`/reports/payment-cycle`** (L140), **`/reports/supplier/{id}/payments`** (L150) : **jamais
  appelés** côté front → rapports invisibles.
- **Détail GRN** : `GET /goods-receipts/{id}` (L44) existe, mais les lignes de `GoodsReceiptsPage.tsx`
  (L188) n'ont **aucun `onClick`** → impossible de voir les items/quantités/prix d'un GRN.
- **Édition d'annonce** : `PUT /announcements/{id}` (L57) existe, mais `AdminAnnouncementsPage` n'a
  **aucun bouton Modifier** (seulement créer/activer/supprimer) → CRUD incomplet.
- *(Rappel déjà documenté ailleurs : PO sans détail/modif/suppr, notif→facture, classer-dossier.)*

### N9 — Validateurs sans cloisonnement départemental (périmètre/IDOR) — 🔴 CONFIRMÉ RUNTIME (rehaussé 🟠→🔴)

- **Constat.** `GET /invoices`, `/{id}`, `/pending-validation`, `/{id}/matching`, `/{id}/history`,
  `/{id}/export/pdf` ([`InvoiceController.java:76,135,161,186,221,338`](../src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java#L76))
  et `GET /matching`, `/{id}/lines` ([`MatchingQueryController.java:36,49`](../src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingQueryController.java#L36))
  sont gardés `isAuthenticated() and !SUPPLIER and !ADMIN` — **tout** validateur, quel que soit son
  département, peut lire/exporter les factures et rapprochements d'**autres** départements.
- **Confirmation service (lecture code).** `InvoiceService.getById`
  ([`InvoiceService.java:155-158`](../src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceService.java#L155))
  ne filtre **par aucun scope** (aucune référence au département de l'utilisateur courant) ; `listInvoices`
  ([`:167-203`](../src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceService.java#L167))
  reçoit `departmentId` comme **filtre optionnel fourni par l'appelant**, jamais imposé selon le rôle.
- **Preuve runtime (connecté `drh` = `ROLE_VALIDATEUR_N1_DRH`, dépt DRH) :**
  - `GET /invoices?page=0&size=50` → **200**, renvoie **25 factures de TOUS les départements** :
    FIN, COM, INFO, TERM, QHSSE, DG, INFRA, TECH (pas seulement DRH).
  - Sur une facture **INFO** (`FAC-2026-00002`, `9cc12a26…`) : `GET /invoices/{id}` → **200**
    (montant 800 000 XAF, fournisseur, dépt INFO lisibles) ; `/{id}/history` → **200** ;
    `/{id}/export/pdf` → **200** (exfiltration PDF possible) ; `GET /matching` → **200**.
    (`/{id}/matching` → 404 = « pas de matching sur cette facture », pas un refus de scope.)
- **Verdict.** Le validateur DRH accède intégralement aux données financières de **tous** les
  départements. La spec veut « validateurs de LEUR département uniquement ». **Faux positif écarté :
  aucun filtrage service.** 🔴 confirmé.

---

## 🟡 MINEURS / À CONFIRMER

### N10 — Assignation N2 hors state-machine
`ApprovalServiceImpl.assignReviewer` ([`:44-63`](../src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java#L44)) :
la branche `EN_VALIDATION_N1 && requiresN2` **jette une exception** (on ne peut « assigner » en N1
d'un dept 2-niveaux par cette voie), et la branche N2 crée un step PENDING avec le commentaire
« No state machine event for this » → l'assignation N2 est un effet de bord ad hoc **hors** de la
machine à états documentée §3. Double source de vérité, incohérences de statut possibles.

### N11 — Bug de token `primary` : ampleur ~62 fichiers
Le CSS compilé servi contient `.bg-primary{background-color:var(--primary)}`,
`.text-primary{color:var(--primary)}`, `.border-primary{border-color:var(--primary)}`,
`.text-muted-foreground{color:var(--muted-foreground)}` — **sans `hsl()`** → valeurs invalides
(`--primary` = triplet HSL brut) → fond transparent / texte noir. Usage source : **`text-primary`
62 fichiers, `bg-primary` 53, `border-primary` 13**. (Les autres tokens cassés — destructive, muted,
accent, border — ne sont pas utilisés, 0 fichier.) Correctif : `hsl(var(--…))` dans
`tailwind.config.js`. *(Détaillé dans `QA_SPEC_VS_REEL_REPORT.md §3`.)*

### N12 — Dates en format US en mode anglais
~5 pages admin utilisent `toLocaleString(i18n.language)` (AdminArchiveCompliancePage:86,
AdminRetentionDispositionPage:85, AdminRetentionPolicyPage:44, SecuritySettingsPage:277-278) : quand
`i18n.language==="en"`, le format devient US M/D/YYYY au lieu de passer par le helper `format.ts`
(`fr-FR`). *(fr.json/en.json symétriques (1292 clés), aucun XOF résiduel — i18n globalement sain.)*

### N13 — Délégation → droit DAF via `deptCode="DAF"` — 🟠 CONFIRMÉ (code, rehaussé 🟡→🟠)

- **Mécanisme.** `ApprovalServiceImpl.checkRole` ([`:221-228`](../src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java#L221))
  dérive `deptCode` de `requiredRole.replaceAll("^ROLE_(VALIDATEUR_N[12]_)?","")` → pour `ROLE_DAF`,
  `deptCode="DAF"`, puis accepte toute délégation active dont `departmentCode.equals("DAF")` (228,
  `return` sans exception).
- **Point d'appel critique.** `bonAPayer` ([`:102-114`](../src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java#L102))
  et `reject` en état VALIDE ([`:137`](../src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java#L137))
  passent `checkRole(user, "ROLE_DAF")` → une délégation `deptCode="DAF"` **accorde le Bon à Payer
  (gate final §8-5) à un non-DAF**.
- **Aggravant — création sans garde-fou.** `DelegationService.createDelegation`
  ([`:46-68`](../src/main/java/com/oct/invoicesystem/domain/workflow/service/DelegationService.java#L46))
  ne valide **que** délégant≠délégataire (50) et dates cohérentes (53) : **aucune validation du
  `departmentCode`** — `"DAF"` (comme toute chaîne) est accepté. Deux voies l'exposent :
  - `POST /delegations` (ADMIN, [`DelegationController.java:32-49`](../src/main/java/com/oct/invoicesystem/domain/workflow/controller/DelegationController.java#L32)) :
    l'admin (qui n'a **aucun** droit financier) peut **fabriquer** le droit de Bon à Payer pour un tiers.
  - `POST /delegations/mine` (approbateurs, [`:72-87`](../src/main/java/com/oct/invoicesystem/domain/workflow/controller/DelegationController.java#L72)) :
    `createSelfDelegation` ([`:79-96`](../src/main/java/com/oct/invoicesystem/domain/workflow/service/DelegationService.java#L79))
    utilise le `departmentCode` fourni **tel quel** sans vérifier que le délégant détient réellement
    `ROLE_DAF` → un validateur quelconque peut déléguer un droit DAF qu'il ne possède pas.
- **Verdict.** Confirmé par lecture de code (chemin exploitable). Décision métier restante : une
  délégation doit-elle pouvoir transférer le Bon à Payer ? Sinon bloquer `deptCode="DAF"` à la
  création **et** exiger que le délégant possède le rôle délégué.

---

## Passe complémentaire 2026-07-17 — 3 axes rebalayés (section B)

> ⚠ **Localisation frontend corrigée.** Le code React est sous `invoice-system/frontend/src/**`
> (le `src/**` mentionné dans les prompts d'agents = module backend Java). Les `fichier:ligne`
> ci-dessous sont relatifs à `frontend/`. Le composant garde s'appelle `RoleGuard`/`PageRoleGuard`
> (`components/auth/RoleGuard.tsx`). Le routeur `AppRoutes.tsx:74-145` **ne pose aucune garde de
> rôle au niveau `<Route>`** : toute la protection dépend d'un `PageRoleGuard` posé **dans** la page.

### N14 — 🔴 `InvoiceCreatePage` (« Saisir facture ») sans aucune garde de rôle
Route `/invoices/new` (`AppRoutes.tsx:91`). `InvoiceCreatePage.tsx` n'a **aucun** `PageRoleGuard` ni
lecture de rôle (grep « RoleGuard|roles.includes » = 0). La saisie de facture est réservée à l'AA
(spec) ; ici **DAF, admin, tout validateur** peuvent ouvrir le formulaire de saisie par URL. Contredit
« AA saisit/paie » et « DAF = PAS d'actions AA ». *(La garde back reste la dernière barrière —
`InvoiceService:336` « Only ASSISTANT_COMPTABLE can create » — mais la défense en profondeur front est
absente.)*

### N15 — 🔴 Pages fournisseur admin sans garde + admin traité comme rôle fournisseur
`admin/SuppliersPage.tsx` (route `/admin/suppliers`, `AppRoutes.tsx:126`) : **aucun** `PageRoleGuard`,
et pire, la page habilite explicitement l'admin — `isAdmin = roles.includes('ROLE_ADMIN')` (`:22`),
bouton « nouveau fournisseur » réservé à l'admin (`:65-70`). `SupplierDetailPage`, `SupplierFormPage`,
`SupplierOnboardingPage` (routes `:127-129`) : idem, sans garde → tout le sous-cycle fournisseur admin
est atteignable **DAF et admin** par URL. Viole frontalement « Admin = ZÉRO fournisseur » ET « DAF =
ZÉRO gestion fournisseur » (décision user : rien à l'admin, la gestion va à l'AA).

### N16 — 🔴 Dashboard/menu admin : « Fournisseurs » proposé à l'admin
Quick-action « Registre fournisseurs » sur le dashboard admin (`DashboardPage.tsx:134-140`, cible
`/admin/suppliers`) **et** entrée de menu « Fournisseurs » visible par l'admin (`Sidebar.tsx:204`).
L'admin ne doit avoir **aucune** surface fournisseur (confidentiel). *(Corollaire UI de N15/N7.)*

### N17 — 🔴 i18n backend court-circuitée : messages métier en anglais brut — CONFIRMÉ RUNTIME
`GlobalExceptionHandler.resolve()` (`src/main/java/.../shared/exception/GlobalExceptionHandler.java:40-47`)
ne traduit **que** si le message ne contient **aucun espace** :
`if (message == null || message.isBlank() || message.contains(" ")) return message;`. Donc **tout
message métier multi-mots est renvoyé tel quel**, jamais traduit. ~40 `throw new WorkflowException/
ValidationException("texte anglais")` sont concernés (ApprovalServiceImpl, InvoiceService,
ThreeWayMatchingService, PurchaseOrderService…).
- **Preuve runtime.** `POST /invoices/{BON_A_PAYER}/workflow/validate-n1` avec `Accept-Language: fr`
  **et** `en` → message **identique** : `"Invoice is not in N1 validation state"`. Aucune traduction.

### N18 — 🟠 `error.access_denied` absente de `messages_fr` — CONFIRMÉ RUNTIME
La clé existe en EN (`messages_en.properties:106`) mais **pas** en FR ; `GlobalExceptionHandler:126`
l'utilise sur toute `AccessDeniedException`.
- **Preuve runtime.** `GET /users` refusé au `drh` avec `Accept-Language: fr` → message renvoyé
  **en anglais** : `"You do not have permission to perform this action"`. *(Fichiers backend réels
  dans `src/main/resources/i18n/` : FR = 280 clés, EN = 282 — l'agent a aussi relevé
  `report.pdf.executive.title` manquante en FR → N24.)*

### N19 — 🟠 CTA invisibles (double token cassé `bg-primary text-primary-foreground`)
Le composant `Button` (variant `primary` en dégradé hex OCT) est **sain** ; le défaut vient des
boutons **inline** `bg-primary text-primary-foreground` (les deux faces du token cassé → ni fond ni
texte peints). Cas critiques : `ErrorBoundary.tsx:43` (écran d'erreur), `ConfirmDialog.tsx:51`,
`LoginPage.tsx:284/353/436` (3 CTA connexion), indicateur d'étape actif `InvoiceCreatePage.tsx:167`,
`DashboardPanels.tsx:85`, ~55 occurrences au total. *(Sous-cas de N11, ampleur précisée.)*

### N20 — 🟠 Tables larges coupées / sans overflow
Le composant sain `ui/Table.tsx:14` (`w-full overflow-x-auto`) est **peu utilisé**. Tables larges dans
un wrapper `overflow-hidden` → **colonnes coupées, données inaccessibles** :
`ApprovalMatrixPage.tsx:85`, `AdminAuditPage.tsx:263`, `IntegrationsPage.tsx:192`, `PaymentsPage.tsx:345`,
`EscalationRulesPage:118`, `MyDelegationsPage:120`, `AdminRetentionDispositionPage:67`… + ~18 tables
sans **aucun** wrapper scrollable (`ApprovalQueuePage:117`, `InvoiceListPage:195`, `SuppliersPage:138`,
`PurchaseOrdersPage:173`, `FinancialAuditPage:140`, `GoodsReceiptsPage:177`, `MatchingListPage:77`…).

### N21 — 🟠 `ApprovalQueuePage` + `InvoiceListPage` sans `PageRoleGuard`
`ApprovalQueuePage.tsx` (route `:93`) et `InvoiceListPage.tsx` (route `:90`) lisent les rôles seulement
pour moduler l'UI, sans garde de page → l'admin (ZÉRO financier, non validateur) atteint la file
d'approbation et la liste des factures par URL. *(Les pages financières `Reports/Payments/…` ONT bien
leur garde — c'est vérifié conforme.)*

### N22 — 🟡 Sidebar archives non-responsive + largeurs de page incohérentes
`ArchiveFolderTree.tsx:204` : `w-64 shrink-0 … min-h-[500px]` — largeur fixe sans repli responsive,
comprime le contenu sur écran étroit ; l'item sélectionné `bg-primary/10 text-primary` (`:139,226,233`)
est **invisible** (token cassé). Largeurs de conteneur disparates : `max-w-2xl`→`5xl` mélangés + 17
pages sans `max-w` du tout ; le `container` de `tailwind.config.js` n'est utilisé nulle part.

### N23 — 🟡 Bean-Validation non résolue + littéraux DTO/délégations
`handleValidationException`/`handleConstraintViolationException` (`GlobalExceptionHandler.java:55-63,
77-84`) concatènent `getDefaultMessage()` **sans** `resolve()` → clés `validation.*`/`webhook.*`
renvoyées brutes. `payment/dto/PaymentRequest.java:11-18` : messages **anglais littéraux** au lieu de
clés. `workflow/service/DelegationService.java:51,54,88,129` : messages **français en dur** (incohérent,
non traduits en EN). *(Complète N17.)*

### N24 — 🟡 Libellés figés JSX + KPI divergent + clé PDF manquante
Textes codés en dur visibles utilisateur : `PaymentsPage:123` « Notes (optionnel) », `GoodsReceiptsPage`
« Notes »/« Description »/« Prix unitaire » (`:122,137,141,184`), « Total » (`InvoiceCreatePage:330,346`,
`InvoiceDetailPage:454`), « Actions » (`MyDelegationsPage:126`, `AdminAnnouncementsPage:110`,
`IntegrationConnectors:92`). KPI `avgProcessingTime` : suffixe **`j.` figé** sur `DashboardPage.tsx:284`
vs `t('dashboard.days')` sur `ReportsPage.tsx:164` (même chiffre, unité non traduite sur le Dashboard).
`report.pdf.executive.title` absente de `messages_fr` (titre PDF non traduit).

### N25 — 🟡 Matching 3-way ouvert à tous les validateurs
`MatchingListPage.tsx:42` / `MatchingDetailPage.tsx:68` gardés `STAFF_ROLES` (inclut tous les N1/N2),
menu `Sidebar.tsx:136`. Le rapprochement 3-way est une activité financière (spec : AA/DAF) ; les
validateurs « de leur département uniquement » y accèdent globalement. Lié à N9.

> **Points confirmés conformes par la passe B** (aucun finding) : redirection post-login cohérente par
> rôle (`LoginPage.tsx:79-84`, dashboard polymorphe) ; quick-actions AA/DAF/validateur/supplier
> cohérentes (hors N16 admin) ; toutes les pages **financières** ont le bon `allowedRoles` ; un
> validateur ne peut PAS ouvrir Paiements/Rapports/Audit financier (gardes présentes) ; **aucun XOF**
> actif côté backend (seule occurrence = migration V45 XOF→XAF).

---

## Points VÉRIFIÉS conformes (couverture rassurante)

- **Anti-auto-approbation** (`ensureNotSubmitter`, `ensureNotN1Approver`) présente sur validateN1/N2/
  bonAPayer ✅. **DAF = gate final tous dépts** ✅. **Archive automatique** après PAYE, ARCHIVE manuel
  refusé ✅. **Routing 1/2 niveaux** basé sur `Department.requiresN2` en base ✅. **MISMATCH bloquant
  au SUBMIT** avec override ✅ (le trou est au resubmit, N1).
- **Saisie/paiement = AA-only au niveau garde** (le DAF **pur** est bien exclu de create/submit/pay/
  PO/GRN écriture) ✅. **Bank details & budget non exposés** dans les DTO ✅. **Aucun controller sans
  `@PreAuthorize`** ✅. **AccessRequest** bloque l'auto-attribution ADMIN/DAF/SUPPLIER ✅.
- **i18n symétrique** (1292/1292), **aucun XOF**, helper `format.ts` en `fr-FR` ✅.
- **Pages financières** (`ReportsPage`, `PaymentsPage`, `FinancialAuditPage`, matching) **ont** leur
  `PageRoleGuard` ✅ (ce sont les pages **admin** qui l'ont oublié — N4).

---

## Plan de correction consolidé (avec les décisions déjà prises)

| Prio | Correctif | Réf |
|:----:|-----------|-----|
| 1 🔴 | Router `/audit-logs` + `/export` par scope de rôle | N3 |
| 2 🔴 | Brancher `InvoiceValidationService` (version resubmit + matching au resubmit) | N1 |
| 3 🔴 | Corriger la longueur min. du motif de rejet (débloquer DOUBLON) | N2 |
| 4 🔴 | Retirer `ROLE_ASSISTANT_COMPTABLE` du compte `daf` | *(décision prise)* |
| 5 🔴 | `hsl(var(--…))` dans `tailwind.config.js` (~62 fichiers) | N11 |
| 6 🔴 | Ajouter l'étape de validation/rejet AA à SOUMIS (toutes factures) | *(décision prise)* |
| 7 🟠 | `PageRoleGuard ADMIN` sur les 18 pages admin | N4 |
| 8 🟠 | Retirer **tout** accès fournisseur à ADMIN **et DAF** | *(décision + N7)* |
| 9 🟠 | Corriger le routage des notifications rejet/BAP (→ AA) + BAP→fournisseur | N5, N6 |
| 10 🟠 | Cloisonner les validateurs par département (lectures factures/matching) | N9 |
| 11 🟠 | Câbler les 4 rapports + détail GRN + édition annonce | N8 |
| 12 🟠 | Retirer `ACCESS_DENIED` de `FINANCIAL_ACTIONS` | *(décision prise)* |
| 13 🔴 | `PageRoleGuard` sur `InvoiceCreatePage` (AA) + retirer `isAdmin` habilité fournisseur + garde sur pages fournisseur admin | N14, N15 |
| 14 🔴 | Retirer quick-action + entrée menu « Fournisseurs » côté admin | N16 |
| 15 🔴 | i18n backend : `resolve()` traduire aussi les messages avec espace (basculer tous les `throw new` sur des clés) + résoudre Bean-Validation | N17, N23 |
| 16 🟠 | Ajouter `error.access_denied` (+ `report.pdf.executive.title`) à `messages_fr` | N18, N24 |
| 17 🟠 | Corriger le double token cassé (`hsl()`) → débloque aussi les CTA invisibles N19 | N11, N19 |
| 18 🟠 | Tables : wrapper `overflow-x-auto` (généraliser `ui/Table`) au lieu de `overflow-hidden` | N20 |
| 19 🟠 | `PageRoleGuard` sur `ApprovalQueuePage` + `InvoiceListPage` | N21 |
| 20 🟡 | PO : détail/modif/suppr + filtre date ; audit financier propre ; largeurs ; classer-dossier | *(rapport précédent)* |
| 21 🟡 | Assignation N2 dans la state machine ; dates `fr-FR` ; délégation deptCode="DAF" ; matching validateurs ; sidebar archives/largeurs ; libellés figés/KPI | N10, N12, N13, N22, N24, N25 |

## Axe C — sécurité pure / edge cases / perf (couverture partielle)

Effleuré dans cette session, à approfondir dans une passe dédiée si besoin :
- **MFA enforcement — VÉRIFIÉ CONFORME (pas un finding).** `AuthService.requiresMandatoryMfaSetup`
  ([`:368-373`](../src/main/java/com/oct/invoicesystem/domain/auth/service/AuthService.java#L368))
  impose le setup MFA à **tout** compte staff non `mfaVerified` (deny-list, supplier exclu) → login
  renvoie `mfaSetupRequired: true` (`:100-110`), et l'étape OTP (`:112`) est déclenchée dès que le
  compte est enrôlé+vérifié. L'état « MFA non enrôlé » des comptes de test (admin/aa/daf) est un
  artefact de seed, **pas** un défaut d'enforcement.
- **Cumul de rôle DAF — CONFIRMÉ RUNTIME.** Login `daf` renvoie
  `roles: ["ROLE_ASSISTANT_COMPTABLE","ROLE_DAF"]` → le cumul SoD est toujours actif (correctif déjà
  décidé, non encore appliqué).
- **Non couverts** (à faire dans une passe C dédiée) : JWT/refresh (rotation, révocation fine),
  rate-limit au-delà de `/auth/login`, vérification AES-256 en base sur bank details, payloads
  invalides / double-submit / bornes de pagination-tri par endpoint, performance sous charge.

---

## Décisions tranchées par l'utilisateur (2026-07-17) — à appliquer lors des corrections

1. **Validateurs cross-département (N9, 🔴)** → **CLOISONNER par département.** Un validateur ne
   voit/exporte que les factures de SON département ; AA/DAF conservent la vue globale. À appliquer
   au **niveau service** (`InvoiceService.getById`/`listInvoices` + matching), pas seulement aux gardes.
2. **Délégation → DAF (N13, 🟠)** → **BLOQUER + exiger le rôle chez le délégant.** Valider
   `departmentCode` à la création ; refuser une délégation transférant `ROLE_DAF`/Bon à Payer sauf si
   le **délégant détient réellement `ROLE_DAF`**. L'admin ne peut plus fabriquer ce droit via
   `POST /delegations`.
3. **Communications fournisseur** → **GARDER append-only** (aucune suppression ; cohérent règle
   journaux append-only CLAUDE.md). Pas de changement.
4. **Matching 3-way (N25, 🟡)** → **RÉSERVER à AA + DAF.** Retirer les rôles validateurs de
   `MatchingListPage`/`MatchingDetailPage` (`STAFF_ROLES` → AA/DAF) et de l'entrée de menu.

---

## Clôture de l'audit — 2026-07-17

- **Bloc A (findings à confirmer).** N9 → 🔴 **confirmé runtime** (rehaussé). N5 + N6 → 🟠 **confirmés
  par code** (repro MailHog bloquée par un seed fournisseur cassé, mécanisme établi ligne à ligne).
  N13 → 🟠 **confirmé par code** (rehaussé). **Aucun faux positif.**
- **Bloc B (3 axes rebalayés).** 12 nouveaux findings **N14→N25** (dont 4 🔴 : saisie facture sans
  garde, fournisseur admin ouvert + admin habilité, menu/quick-action fournisseur admin, i18n backend
  anglais brut confirmé runtime).
- **Bloc C.** MFA enforcement vérifié **conforme** ; cumul DAF reconfirmé ; reste sécurité/edge/perf
  listés non couverts.
- **Total consolidé QA_AUDIT_EXHAUSTIF** : 25 findings (N1→N25). **Toujours audit d'état — aucune
  correction appliquée.** Prochaine étape = trancher les 4 décisions ci-dessus puis attaquer le plan
  de correction (21 lignes).
