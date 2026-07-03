# Design — Correctifs AUDIT_GENERAL_2026-07-02 + refonte UI « Registre »

**Date :** 2026-07-03
**Branche de travail :** `chore/sanitize-docs-migrations` (4 commits non poussés, ~75 fichiers non commités)
**Séquencement validé (utilisateur) :** **audit d'abord, puis redesign** — corriger le comportement
avant de redessiner, pour ne pas retoucher deux fois les composants partagés
(`InvoiceActionPanel`, `StatusBadge`, `ExportMenu`, `ApprovalQueuePage`).

---

## 0. État de départ vérifié (2026-07-03)

- **Git :** branche `chore/sanitize-docs-migrations`. Les 75 fichiers non commités sont du
  travail antérieur de l'utilisateur (solo dev) : essentiellement le **wizard onboarding
  M8 #10** (`SupplierOnboardingPage`, `SupplierController` PATCH activate/suspend +
  `ensureOnboardingComplete`), l'**arborescence d'archives** (`ArchiveFolder*`,
  `V36`/`V37`), les **sauvegardes admin** (`BackupController`, `BackupService`, `V39`,
  `AdminBackupsPage`), la **résolution ligne matching** (`V38`/`V41`/`V42`), et des
  nettoyages i18n/refactor (DTO `department` plat, en-têtes export via `MessageSource`).
- **Tests de l'arbre existant (vérifiés) :** frontend `tsc --noEmit` **0 erreur** ;
  vitest **80/80** (un timeout flaky isolé sous charge machine, vert en isolation) ;
  backend `./mvnw test` = **539 tests, 0 échec, 3 ERREURS** — voir §0bis.
- **Migrations :** contiguës V1→V42. **Prochaine = V43.**
- **Le 🔴 BLOQUANT et la majorité des MAJEURS de l'audit ne sont PAS corrigés** dans ce
  diff (vérifié : aucun appel `/workflow/assign` nulle part dans `frontend/src` ; ADMIN
  toujours sur les 4 endpoints paiement ; `InvoiceDocumentController` toujours
  `isAuthenticated()` ; `MARK_PAID` poste toujours `BANK_TRANSFER`).

**Décision préalable :** vérifier que l'arbre existant est vert (`mvnw test` + vitest + tsc),
puis le committer en commits thématiques (onboarding / archives / backups / cleanups i18n)
pour repartir sur une base propre AVANT d'appliquer les correctifs d'audit. Si un test est
rouge (hors flakiness prouvée), STOP + rapport avant de committer.

### 0bis. Les 3 erreurs backend — diagnostic (bloque le commit de l'existant)

`ArchiveFolderIntegrationTest` (fichier non commité, nouveau) échoue 3× dans `setUp:37`
(`userRepository.findByUsername("admin")`) : `AEADBadTagException: Tag mismatch` au
déchiffrement de `User.mfaSecret` (`@Convert(EncryptionAttributeConverter)`, ligne 108).

**Cause racine (confirmée avec l'utilisateur) :** lors de la session d'audit (2026-07-02),
la MFA de l'admin étant déjà configurée, une **clé AES ad-hoc** a été utilisée pour tester ;
le `mfa_secret` de l'`admin` en base dev (`localhost:5433`) est donc chiffré avec une clé ≠
de celle du profil `test` (`TestEncryptionKey1234567890ABCDEF` dans
`application-test.yml` vs `ENCRYPTION_KEY=OCTInvoice2026SecretKeyAES256Dev` côté dev/prod).
`AbstractPostgresIntegrationTest` pointe la base dev partagée (Flyway off) → le row admin
n'est pas déchiffrable sous la clé de test. Les autres tests Postgres passent car ils ne
chargent jamais un champ chiffré de l'admin. **Ce n'est pas un bug du code archives** — c'est
exactement le symptôme de MAJEUR-6 (row admin dérivé).

**Correctif retenu (avant commit de l'existant) :** rendre `ArchiveFolderIntegrationTest`
robuste — ne pas dépendre du `mfa_secret` déchiffrable de l'admin. `createFolder` n'utilise
`user` que pour la FK `createdBy` : récupérer l'id d'un user via une requête ne sélectionnant
pas le champ chiffré, puis `getReferenceById(id)` (proxy lazy, aucune lecture/déchiffrement),
ou créer un user de test dédié dans la transaction (le test écrit déjà et compte sur le
rollback `@Transactional`). Objectif : gate backend **539/0/0** sans retoucher la base à la
main. Le fond (row admin) sera réglé par la migration **V43** (MAJEUR-6, §A9).

---

## 1. Périmètre

**Inclus :**
- **Track A — Audit** : le 🔴 BLOQUANT + les 21 MAJEURS confirmés + les 🟡 MINEURS rentables.
- **Track B — Redesign « Registre »** : refonte système globale (tokens → composants partagés
  → layout → écrans à fort trafic → reste des pages).

**Exclus de ce lot (décision utilisateur) — points « à confirmer avec le métier » (§4 audit) :**
transition `VALIDE → REJETE`, `ensureWithinApprovalLimit` absent du Bon à Payer, accès ADMIN
aux lectures PO/GRN, cloisonnement checklist par département (B-1). **Ne pas les traiter** ;
les laisser en `docs/TASKS.md §A` pour une session métier dédiée.

**Exclus (déjà tranché hors-scope) :** connecteurs ERP/banking/etc. réels (Module 12).

**Découpage en plans d'implémentation :** vu la taille, ce spec donnera **deux plans
séparés** — un plan Track A (audit), exécuté et validé en entier d'abord, puis un plan
Track B (redesign). On n'écrit le plan Track B qu'une fois Track A terminé et poussé, pour
que le redesign parte d'un comportement correct et figé.

---

## 2. TRACK A — Correctifs d'audit

Ordre = §6 de l'audit. Chaque item = un commit logique. Chaque bug corrigé → entrée
`docs/KNOWN_ISSUES_REGISTRY.md` (cause racine + correctif + règle préventive) + mise à jour
`docs/TASKS.md`.

### A0. 🔴 BLOQUANT — Workflow BAP cassé (SOUMIS → EN_VALIDATION_N1)

**Cause racine :** le frontend n'appelle jamais `POST /invoices/{id}/workflow/assign`
(événement `ASSIGN_REVIEWER`). Backend prêt (`ApprovalController.java:57-70`, autorise
AA + tous N1/N2). Conséquence : toute facture `SOUMIS` reste bloquée à vie.

**Correctif (frontend only) :**
1. `InvoiceActionPanel.tsx` — ajouter un bouton **« Démarrer la revue / Prendre en charge »**
   (action `ASSIGN_REVIEWER` → `POST /invoices/{id}/workflow/assign`) visible quand
   `status === 'SOUMIS'` pour les rôles AA + N1 + N2 + DAF (miroir de l'autorisation backend).
   Clé i18n `invoice.startReview` (FR « Démarrer la revue », EN « Start review »).
2. `ApprovalQueuePage.tsx` — faire apparaître les factures `SOUMIS` dans la file. Deux
   sous-files claires : **« À prendre en charge » (SOUMIS)** et **« À valider »
   (EN_VALIDATION_N1/N2)**, ou un filtre statut incluant `SOUMIS`. Le backend expose déjà la
   liste ; vérifier l'endpoint réellement consommé par la page avant de coder (règle PROB-005).
3. Vérifier l'invalidation React-Query (`['approval-queue']`, `['invoice', id]`) après assign.

**Tests :** test front (bouton visible sur SOUMIS pour AA, appelle le bon endpoint) ;
si un test d'intégration backend manque sur `/workflow/assign`, le compléter.

### A1. 🟠 MAJEUR-1 — IDOR documents de facture

`InvoiceDocumentController.java:65-84` (`GET /documents`, `GET /{docId}/download`) sont
`@PreAuthorize("isAuthenticated()")`. Correctif : restreindre à `!hasRole('SUPPLIER')` sur ces
deux lectures génériques (le portail fournisseur passe par `SupplierPortalController` avec
`ensureOwnInvoice`). **Tests d'intégration :** SUPPLIER → 403 ; staff → 200.

### A2. 🟠 MAJEUR-2 — SoD paiements : retirer ADMIN

`PaymentController.java:72,80,91,99` — retirer `'ADMIN'` des 4 lectures
(`GET /payments/invoice/{id}`, `GET /payments`, `GET /{id}/remittance`, `GET /export`).
Garder `ASSISTANT_COMPTABLE` + `DAF`. **Tests :** ADMIN → 403 sur les 4 ; DAF → 200.

### A3. 🟠 MAJEUR-11 — SoD : retirer ADMIN de `/suppliers/{id}/performance`

`SupplierController.java:147` — `GET /{id}/performance` = retirer `'ADMIN'`
(garder `ASSISTANT_COMPTABLE` + `DAF`). Aligné sur `admin-no-financial-access`.

### A4. 🟠 MAJEUR-12 — Métriques fournisseur factices sur exception

`SupplierController.getPerformanceMetrics` (:150-164) — ne plus fabriquer de métriques
(`invoiceAccuracyRate=1.0`, `rejectionRate=0.0`…) sur `ResourceNotFoundException` : propager
l'erreur (404). Supprimer aussi le double appel `getSupplier(id)`. **Test :** fournisseur
inexistant → 404 (plus de fausses métriques).

### A5. 🟠 MAJEUR-3 — Matching : exceptions avalées

`InvoiceStateMachineServiceImpl.performMatchingCheck` — le `catch (Exception e)` ne re-lève
que `WorkflowException` et avale le reste (`ValidationException` « No active matching
configuration », « no line items »…). Correctif : sur ce chemin critique, **ne tolérer aucune
exception silencieuse** — échouer la soumission si le matching ne peut pas être évalué
(re-lever en `WorkflowException`/`ValidationException`). **Tests :** facture avec PO sans
lignes / config inactive → la soumission échoue (au lieu de passer SOUMIS sans matching).

### A6. 🟠 MAJEUR-4 — Bouton « Record Payment » : moyen de paiement inexistant

`InvoiceActionPanel.tsx:53-59` — `MARK_PAID` poste `paymentMethod: 'BANK_TRANSFER'` (absent
de l'enum `VIREMENT/CHEQUE/ESPECES/MOBILE_MONEY`). **Décision : supprimer ce bouton** (il est
redondant avec le modal `PaymentsPage` qui, lui, envoie `VIREMENT` correctement et gère
méthode/date/référence). Retirer aussi la branche `case 'MARK_PAID'` de la mutation.

### A7. 🟠 MAJEUR-5 — 29 clés i18n manquantes

⚠ **Rappel encodage :** `messages_fr.properties` backend est **Latin-1 (ISO-8859-1)** —
convertir tout ajout avec `iconv`, jamais d'append UTF-8 direct, jamais d'em-dash/guillemets
courbes. `fr.json`/`en.json` frontend sont UTF-8 standard.

- Créer namespaces **FR+EN** dans `fr.json`/`en.json` : `supplier.onboarding.*`,
  `admin.backups.*`, `archiveFolders.*` (résoudre chaque clé réellement appelée par
  `SupplierOnboardingPage`, `AdminBackupsPage`, `ArchiveFolderTree`, `AssignFolderModal`).
- Corriger les 3 fautes de namespace `common.*` → `app.*` :
  `MatchingLineResolveModal.tsx:73` (`common.cancel`→`app.cancel`),
  `MatchingDetailPage.tsx` (`common.actions`→`app.actions`),
  `DepartmentAccessPage.tsx:21` (`common.loading`→`app.loading`),
  `AdminBackupsPage.tsx:115` (`common.actions`→`app.actions`).
- Traduire `invoice.duplicateWarning` (`InvoiceCreatePage.tsx:298`).

### A8. 🟠 MAJEUR-7 — ExportMenu sans gestion d'erreur

`components/ui/ExportMenu.tsx:31-50` — `download()` n'a aucun `catch` ; un blob d'erreur peut
s'écrire comme fichier. Correctif : `try/catch` autour du `download()`, message d'erreur i18n
(`export.error`), et ne pas écrire le blob si la réponse n'est pas OK.

### A9. 🟠 MAJEUR-6 — Compte admin re-cassé (`mfa_setup_required`) — migration Flyway

Créer **`V43__fix_admin_account.sql`** : aligner `admin` (hash BCrypt de `Test1234!` +
`mfa_verified = true`) sur les comptes du seed V34, de façon idempotente (UPDATE conditionnel),
pour que le fix survive à un re-seed. Récupérer le hash BCrypt exact depuis V34 pour cohérence.

### A10. 🟠 MAJEUR-8 — Délégation cassée (PLAUSIBLE — vérifier avant de corriger)

`RoleMatchGuard.java:63-68` ignore les délégations que `ApprovalServiceImpl.checkRole` prend
en compte. **D'abord** écrire un test qui reproduit : un délégataire accepté par le service
doit-il être refusé par la garde ? Si le test confirme le bug → aligner la garde (intégrer
`findActiveDelegationsForDelegatee`) ou ne pas re-vérifier le rôle dans la garde. Si le test
infirme → documenter et clore sans changement de code.

### A11. 🟠 MAJEUR-10 — Escalade via demande d'accès

`AccessRequestService.create` (l.51-57) n'interdit que `ROLE_SUPPLIER`. Correctif : **liste
blanche** des rôles auto-demandables, **excluant `ROLE_ADMIN` et `ROLE_DAF`** (et tout rôle
financier sensible). **Test :** demande de `ROLE_ADMIN`/`ROLE_DAF` → rejetée.

### A12. 🟠 MAJEUR-13 — Credentials d'intégration en clair

`IntegrationConnector.java:37-38` — `config` (`@Column(length=4000)`) n'est pas chiffré,
contrairement à `Supplier.bankDetails`. Correctif : ajouter
`@Convert(converter = EncryptionAttributeConverter.class)` sur `config`. ⚠ **Données
existantes :** si des connecteurs existent déjà en base avec `config` en clair, prévoir une
migration de rechiffrement OU documenter que seuls les nouveaux enregistrements sont chiffrés
(à trancher au moment du code selon présence de données). **Test :** round-trip lecture/écriture
+ vérifier que la valeur stockée en base n'est pas en clair.

### A13. 🟠 MAJEUR-9 — Budget départemental exposable (latent)

`DepartmentDTO.java:19` inclut `budget` ; `GET /departments` + `/{id}` sont
`isAuthenticated()` (donc SUPPLIER + ADMIN y accèdent). Correctif : **retirer `budget` du DTO
public** (ou créer un DTO restreint pour les lectures ouvertes ; le budget ne sert qu'aux
rapports internes DAF/AA). **Test :** `GET /departments` en tant que SUPPLIER ne contient
jamais `budget`.

### A14. 🟠 B-2 — Path traversal restore backup

`BackupController.restoreBackup` — `filename` concaténé (`"backups/" + filename`) sans
sanitisation → path traversal MinIO. Correctif : valider `filename` (rejeter `..`, `/`, `\`,
chemins absolus ; liste blanche de caractères). **Test :** `../` → 400.

> **B-1 (IDOR checklist)** est exclu de ce lot (point métier, décision utilisateur).

### A15. 🟡 MINEURS — par ordre de rentabilité

1. **Devise XAF → XOF partout** : `DashboardPage.tsx:447`, `PurchaseOrdersPage.tsx`
   (état initial `XAF`, MAJEUR-F4). XOF est la devise du projet.
2. **Formats FR forcés** : tous les `toLocaleString`/`Intl.*` sans locale → forcer `'fr-FR'`
   (dates JJ/MM/AAAA, montants séparés par espace). Cibles : dashboards, listes factures
   (staff + supplier), détail facture, paiements. **Cela recoupe le redesign** (montants en
   `tabular-nums` mono) → traiter le formatage ici, le style typographique en Track B.
3. **Libellés FR codés en dur cassant le mode EN** (§4 + §7bis.4 audit) : `RoleGuard.tsx`,
   `DashboardPage.tsx`, `ArchivePage.tsx`, `AdminBackupsPage.tsx` (en-têtes tableau),
   `Sidebar.tsx:226` (« Système opérationnel »), `Header.tsx` (breadcrumb entier +
   `BREADCRUMB_MAP`), `DocumentUploader.tsx` (composant entier, `t` importé jamais utilisé).
   → passer par `t()` avec clés FR+EN.
4. **15 clés backend absentes de `messages_en.properties`** (bloc MFA/verrouillage :
   `error.otp.*`, `mfa.*`, `error.account.locked`) → ajouter en EN.
5. **RT-5 départements seedés en anglais** : migration **V44** renommant les `department.name`
   en FR (`General Management` → `Direction Générale`, etc.), pas de correctif en dur front.
   (Numéro après V43.)
6. **MAJEUR-F2 confirmations destructives (~11 actions)** : créer un composant générique
   `ConfirmDialog` réutilisable + le brancher sur les ~11 emplacements listés (rejet demande
   d'accès, suppr. annonce, suppr. checklist/calendrier, révocation délégation, désactivation
   user / reset MFA, suppr. webhook, révocation session, suppr. rapport, suppr. connecteur,
   suppr. contrat).
7. **MAJEUR-F3 bouton « Importer » BC qui n'importe rien** (`PurchaseOrdersPage.tsx:99`) :
   le **désactiver** avec un message honnête i18n (l'import ERP est hors-scope Module 12) —
   ne pas simuler un import.
8. **MAJEUR-F1 « Revoke » révoque toutes les sessions** (`SecuritySettingsPage.tsx:84`) :
   corriger le libellé pour refléter la portée réelle (« Déconnecter toutes les sessions »)
   OU cibler une session unique si l'endpoint le permet — à vérifier au code.
9. **Nettoyage code mort** : `InvoiceCreatePage.tsx:67` (2ᵉ formulaire jetable `watch`),
   `InvoiceDocumentService.generateDownloadUrl` (probablement mort — confirmer avant suppr.).
10. **`POST /ocr/extract` sans fichier → 500 au lieu de 400** : valider la présence du fichier.

**Non prioritaires / à laisser en `TASKS.md §A`** (rentabilité faible, non bloquants) :
`AdminDepartmentsPage.tsx:20-21` hook conditionnel (stable en pratique), pluralisation i18n
(RT-6), « to » anglais (RT-7), et les MINEURS backend B-3…B-13 (sauf si triviaux au passage).
Ceux-ci seront évalués un par un ; on ne s'engage pas à tous les faire dans ce lot.

---

## 3. TRACK B — Refonte « Registre » (après Track A)

Direction validée en session précédente. Reconstruction à partir de la spec de tokens (la
maquette `oct-registre.html` d'origine n'est plus disponible — on la refait à l'identique).

### B0. Principes non négociables du « Registre »
- Navy `#0F2540` / or `#C8A84B` **disciplinés** : l'or ne sert qu'à la **marque** (sidebar,
  logo) et à l'**action primaire** (CTA). Jamais en décoration.
- Neutres **chauds** (biais or), **pas** de gris froid Tailwind par défaut.
- **Couleur sémantique d'état séparée de l'accent or** (5 sémantiques désaturées).
- **Signature visuelle : montants + références en `font-mono` + `tabular-nums`**, alignés à
  droite dans les tableaux, devise en gris clair à côté du nombre. Partout.
- **Filets fins** plutôt que cartes flottantes à ombre. `border-radius: 4px`, ombre quasi
  plate. Pas de `rounded-xl` généralisé, pas de `hover:shadow-md`.
- **Bande KPI unifiée** (un conteneur, séparateurs verticaux internes) plutôt que 4 cartes
  isolées à ombre.
- **Chips d'état à 2 couleurs max par écran** (sémantique + neutre). Fini les pastilles
  d'icônes arc-en-ciel.
- Mode sombre **conçu** (pas inversé) : fond `#12151A`, surface `#171B22`, filets
  `#262B34`/`#333A45` ; en sombre les accents or remontent (barres de classement passent au
  dégradé or).
- Eyebrows/labels de section en MAJUSCULES, `letter-spacing ≥ .1em`, 10-11px, `--ink-faint`.

### B1. Tokens — `frontend/src/index.css` + `tailwind.config.js`
Rester **cohérent avec le format shadcn HSL-triplet** (`--primary: 213 64% 16%`) pour les
tokens partagés avec shadcn/Radix. **Ne pas** introduire un second système de couleur.
Ajouter (en HSL-triplet) :
- Neutres chauds : `--ground` (#FBFAF7), `--hairline` (#EAE5DA), `--hairline-strong`
  (#DAD3C4), `--ink` (#23201A), `--ink-soft` (#6B6456), `--ink-faint` (#98917F).
- Sémantiques d'état (+ leurs bg) : `--pos` #3E7C5A / bg #EAF2EC, `--warn` #B5852A / bg
  #F6EEDC, `--hot` #C4622E / bg #F7EADF, `--crit` #A6432E / bg #F6E6E1, `--info` #2F6690 /
  bg #E4EDF3.
- Gold-deep : `--gold-deep` #9A7E2E.
- Bloc `.dark { … }` avec les valeurs sombres conçues.
- Utilitaire `.num` (ou classe Tailwind) : `font-family: <mono stack>; font-variant-numeric:
  tabular-nums;` pour les montants/références.
Exposer les nouveaux tokens comme couleurs Tailwind si besoin (ex. `text-pos`, `bg-warn-bg`).

### B2. Composants partagés
- **`StatusBadge.tsx`** : migrer la palette (slate/blue/amber/orange/teal/green/emerald/gray/
  red) vers les 5 sémantiques. **Mapping soigné** (préserver la distinction N1/N2) :
  `BROUILLON`→neutre, `SOUMIS`→info, `EN_VALIDATION_N1`→**warn**, `EN_VALIDATION_N2`→**hot**,
  `VALIDE`→pos (variante claire), `BON_A_PAYER`→pos, `PAYE`→pos (variante saturée),
  `ARCHIVE`→neutre, `REJETE`→crit. Conserver les 3 variantes (`pill`/`dot-only`/`inline`) et
  `data-status` (utilisé par les tests/e2e).
- **`Skeleton.tsx`** : shimmer sur neutres chauds.
- **`ExportMenu.tsx`** : style Registre (déjà touché en A8 pour le catch — ne pas re-toucher
  la logique, juste le style).
- **Bande KPI** : remplacer le pattern `KpiCard` isolé de `DashboardPage.tsx`/
  `DashboardPanels.tsx` par un composant `KpiBand` unifié (filets internes).
- **`AgingBucketsWidget.tsx`** : filets + tabular-nums.
- Composant **carte/panel générique** Registre (border fin + radius 4px + ombre plate) pour
  remplacer les `bg-white rounded-xl border` répétés.

### B3. Layout
- **`AppShell.tsx`** : `bg-gray-50` en dur → token `--ground`.
- **`Sidebar.tsx`** : déjà proche cible (`.oct-nav-active`). Vérifier cohérence état actif +
  corriger « Système opérationnel » codé en dur (recoupe A15.3).
- **`Header.tsx`** : breadcrumb i18n (recoupe A15.3) + style Registre.

### B4. Écrans à fort trafic (dans cet ordre)
`DashboardPage.tsx` (4 variantes de rôle), `InvoiceListPage.tsx`, `InvoiceDetailPage.tsx`,
`PaymentsPage.tsx`, `ApprovalQueuePage.tsx` (déjà touché en A0 — appliquer le style ici),
`matching/MatchingListPage.tsx`, `matching/MatchingDetailPage.tsx`.
Tables : filets horizontaux fins, header petites-majuscules sur `--ground`, hover teinté or
(~5% via `color-mix`), montants/refs en `.num` alignés à droite.

### B5. Reste des pages (`admin/*`, `supplier/*`, `reports`)
Migrer vers les tokens/composants partagés une fois le système stabilisé. **Ne pas redessiner
la structure** de chaque page — juste appliquer tokens + composants.

### Cadence Track B
Valider **par lot** (pas un mega-commit) : `feat(ui): …` par lot cohérent (tokens ;
composants partagés ; layout ; dashboards ; listes ; reste). Faire valider par l'utilisateur
lot par lot (mémoire `per-task-commit-and-handoff`).

---

## 4. Tests, qualité, docs (règles projet)

- **Gate de fin de tâche :** `./mvnw test` + `npx vitest run` + `npx tsc --noEmit`, **0 échec**
  (mémoire `no-failures-on-task-completion` : pas d'excuse « pre-existing » hors flakiness
  prouvée en isolation).
- **Chaque bug d'audit corrigé** → `docs/KNOWN_ISSUES_REGISTRY.md` (cause racine + correctif +
  règle préventive) AVANT le commit (CLAUDE.md §12).
- **`docs/TASKS.md`** tenu à jour au fil de l'eau.
- **Commits :** un par tâche logique, message `type(scope): description`, jamais
  `--no-verify`. **Push :** seulement sur validation explicite de l'utilisateur (mais règle
  mémoire `push-every-10-commits` : proposer un push dès 10 commits non poussés).
- **i18n :** toute chaîne visible passe par `t()`/`MessageSource`, dans les deux langues.
  Backend FR = Latin-1 (`iconv`).

---

## 5. Risques / points d'attention
- **Le formatage devise/date (A15.2) recoupe le redesign (B4 tabular-nums)** : traiter le
  *formatage fonctionnel* en Track A, le *style typographique* en Track B, sans conflit.
- **MAJEUR-13 données existantes** : décider au code si rechiffrement nécessaire.
- **`StatusBadge` `data-status`** : ne pas casser les sélecteurs de tests e2e existants.
- **Vérifier l'endpoint réel** consommé par `ApprovalQueuePage` avant A0 (règle PROB-005 :
  page vide = souvent mauvais endpoint/champ, pas base vide).
- **`messages_fr.properties` Latin-1** : corruption d'accents garantie si append UTF-8.
```
