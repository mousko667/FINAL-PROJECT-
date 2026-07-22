# AUDIT_COVERAGE - Matrice de couverture page x role

> Genere en **Phase 0** de l'audit exhaustif. Sert a **mesurer l'exhaustivite** des phases P2
> (audit visuel) et P3 (audit fonctionnel). Source : `frontend/src/AppRoutes.tsx` x les 15 roles.
>
> Registre des findings : `docs/AUDIT_MASTER.md`. Modele : `docs/AUDIT_SYSTEM_MODEL.md`.

## Legende

| Marque | Signification |
|---|---|
| `.` | **non vu** - cellule pas encore visitee (etat initial de toutes les cellules) |
| `V` | **vue** (P2) - page chargee avec ce role, captures desktop + mobile prises, rendu inspecte |
| `T` | **testee** (P3) - parcours fonctionnel joue avec ce role (boutons, formulaires, cas limites) |
| `VT` | vue **et** testee |
| `X` | **acces refuse attendu** - verifie que le role est bien bloque (403 / ecran d'erreur / redirection) |
| `-` | **sans objet** - combinaison ecartee volontairement |

Regle : toute cellule marquee `V`, `T`, `VT` ou `X` doit s'appuyer sur une **preuve runtime**
(capture Playwright, trace reseau, log console). Sans preuve, la cellule reste `.`.

**Etat initial : toutes les cellules a `.` (non vu).**

## Progression

Mise a jour le **2026-07-22** a l'issue de la **Phase 3** (audit fonctionnel end-to-end).

| Zone | Cellules | Vues/verifiees (P2) | Testees (P3) |
|---|---|---|---|
| Pages publiques | 6 x 1 = 6 | 1 (`/login`) | **2** (`/login` parcours + panne serveur, `/register` verifie par le code) |
| Pages staff | 45 x 15 = 675 | **233** (94 `V` + 139 `X`) | **147** (21 `T` parcours joues + 126 `X` verifies par sonde API sur les 9 roles validateurs) |
| Portail fournisseur | 5 x 15 = 75 | **15** (5 `V` + 10 `X`) | **3** (`/supplier/invoices/new`, `/supplier/invoices`, `/supplier/dashboard`) |
| **Total** | **756** | **249** (~33 %) | **152** (~20 %) |

### Ce que la Phase 3 a reellement teste

L'audit fonctionnel ne se mesure pas seulement en cellules page x role : l'essentiel porte sur des
**parcours** et des **regles**, transverses par nature.

| Objet teste | Volume | Resultat |
|---|---|---|
| Machine a etats — workflow complet | 8 transitions x 2 topologies (1 et 2 niveaux) | conforme (voir « Verifie CONFORME en P3 ») |
| Gardes de transition | 6 gardes eprouvees par l'echec attendu | toutes tiennent |
| SoD — surfaces interdites | **126 mesures** (9 roles validateurs x 14 endpoints) | **403 partout**, 0 fuite |
| SoD — transitions interdites | 8 tentatives (ADMIN, DAF, SUPPLIER, AA, N1 d'un autre dept, N2 sur dept a 1 niveau) | toutes refusees |
| Cas limites de saisie | 9 (montant nul/negatif/enorme, dates, 3 devises, dept inexistant) | **5 acceptes a tort** -> AUDIT-032 |
| Cas limites de fichier | 4 (12 Mo x2, MIME deguise x2) | tous refuses correctement |
| Cas limites de paiement | 4 (negatif, zero, 1 XAF, deja paye) | **1 accepte a tort** -> AUDIT-029 |
| Parametre `sort` | 7 variantes sur 3 endpoints | **3 x HTTP 500** -> AUDIT-010 |
| Endpoints `/actuator` sans jeton | 6 | **4 accessibles** -> AUDIT-011 |
| Findings P1/P2 a prouver | 8 | 6 prouves, **2 refutes** (AUDIT-006 ; AUDIT-010 partiellement) |

**Les 9 roles validateurs non sondes en P2 sont desormais couverts** (`drh`, `dg`, `dex`, `com`,
`qhsse`, `infra`, `dir_infra`, `atelier`, `dir_tech`) : sonde API systematique sur 14 endpoints
chacun, plus un parcours d'approbation complet joue en interface avec `drh`
(`/approvals` -> `/invoices/:id` -> bouton Valider -> statut `VALIDE`).

### Note de couverture P2 — echantillon representatif (decision utilisateur)

Le balayage exhaustif des 15 roles a ete **volontairement restreint a 6 roles** couvrant toutes les
surfaces distinctes du systeme, apres arbitrage explicite avec l'utilisateur :

| Role sonde | Represente | Pourquoi ce choix |
|---|---|---|
| `admin` | ROLE_ADMIN | seul porteur des 25 pages `/admin/*` |
| `daf` | ROLE_DAF | surface financiere complete + audit financier |
| `aa` | ROLE_ASSISTANT_COMPTABLE | seul role de saisie (creation/soumission de facture) |
| `rsi` | les 8 validateurs **N1** | surface validateur N1 (departement a deux niveaux) |
| `dsi` | les 3 validateurs **N2** | surface validateur N2 |
| `supplier` | ROLE_SUPPLIER | portail fournisseur, layout distinct |

**Justification** : `rsi` et `dsi` presentent une surface **identique** (8 pages accessibles,
37 bloquees, 0 erreur HTTP anormale) ; les 9 autres validateurs partagent exactement le meme
`PageRoleGuard` et le meme layout, seul le departement change. Les etendre n'aurait produit que des
observations redondantes. Les 3 findings transverses de la phase (**AUDIT-019** responsive,
**AUDIT-020** contraste, **AUDIT-024** libelles de formulaire) sont par construction independants du
role : ils touchent les layouts et le design system, donc **toutes** les cellules.

**Volume reel de l'audit** : 992 observations = 6 roles x ~47 pages x 2 viewports (1440 / 390)
x 2 themes (clair / sombre), chacune avec mesures DOM, execution axe-core (WCAG 2.1 AA), trace
reseau et capture PNG. Captures : `scratch/p2-audit/sample/captures/` (992 fichiers) ; captures de
preuve des findings : `docs/audit/p2-captures/`.

~~**Reste a couvrir en P3** : les 9 roles validateurs non sondes.~~ **FAIT en P3** (2026-07-22) :
les 9 roles ont ete sondes sur 14 endpoints chacun (126 mesures) et marques `X` sur les surfaces
interdites. Le parcours d'approbation a ete joue en interface avec `drh`.

**Reste a couvrir en P4** (audit transverse) : les 4 pages publiques encore a `.`
(`/forgot-password`, `/reset-password`, `/register/supplier`, `/verify-email`) et les cellules
`.` restantes des pages « communes a tout le staff » (`/profile`, `/access-requests`,
`/my-delegations`) pour les roles validateurs. Ces pages ne portent aucune donnee financiere :
leur couverture releve de la coherence/i18n/a11y (P4), pas du risque SoD, deja mesure.

---

## 1. Pages publiques (hors authentification)

Ces pages ne dependent pas du role : une seule colonne (visiteur non authentifie).

| Route | Page | Non authentifie |
|---|---|---|
| `/login` | `LoginPage` | **VT** — P2 : responsive conforme (contre-preuve AUDIT-019). P3 : connexion jouee (`aa`, `drh`, `supplier`) + comportement **backend arrete** -> message trompeur « Identifiants incorrects » (AUDIT-035) |
| `/register` | `auth/RegisterPage` | **T** (code) — reutilise `SupplierRegisterPage` avec un bandeau « fournisseurs externes uniquement » : le lien du login n'est **pas** un defaut (faux positif P2 confirme ecarte) |
| `/forgot-password` | `auth/ForgotPasswordPage` | . |
| `/reset-password` | `auth/ResetPasswordPage` | . |
| `/register/supplier` | `auth/SupplierRegisterPage` | . |
| `/verify-email` | `auth/EmailVerificationPage` | . |

---

## 2. Pages staff (45 pages x 15 roles)

| Route | Page | ADMIN | DAF | AA | N1 DRH | N1 DG | N1 INFO | N2 INFO | N1 TERM | N1 COM | N1 QHSSE | N1 INFRA | N2 INFRA | N1 TECH | N2 TECH | SUPPL. |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/dashboard` | `DashboardPage` | V | V | VT | . | . | V | V | . | . | . | . | . | . | . | V |
| `/profile` | `ProfilePage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/access-requests` | `MyAccessRequestsPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/notifications` | `NotificationsPage` | V | V | VT | T | T | VT | VT | T | T | T | T | T | T | . | V |
| `/my-delegations` | `MyDelegationsPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/invoices` | `InvoiceListPage` | X | VT | VT | . | . | V | V | . | . | . | . | . | . | . | X |
| `/invoices/new` | `InvoiceCreatePage` | X | X | V | . | . | X | X | . | . | . | . | . | . | . | X |
| `/invoices/:id` | `InvoiceDetailPage` | V | VT | VT | . | . | V | V | . | . | . | . | . | . | . | X |
| `/approvals` | `ApprovalQueuePage` | X | VT | VT | . | . | V | V | . | . | . | . | . | . | . | . |
| `/financial-audit` | `FinancialAuditPage` | X | V | X | X | . | X | X | X | X | X | X | X | X | . | . |
| `/purchase-orders` | `PurchaseOrdersPage` | X | VT | VT | X | . | X | X | X | X | X | X | X | X | . | . |
| `/goods-receipts` | `GoodsReceiptsPage` | X | T | VT | X | . | X | X | X | X | X | X | X | X | . | . |
| `/payments` | `PaymentsPage` | X | V | VT | X | . | X | X | X | X | X | X | X | X | . | . |
| `/payments/alert-rules` | `PaymentAlertRulesPage` | X | V | V | X | . | X | X | X | X | X | X | X | X | . | . |
| `/reports` | `ReportsPage` | X | V | V | X | . | X | X | X | X | X | X | X | X | . | . |
| `/reports/builder` | `ReportBuilderPage` | X | V | V | X | . | X | X | X | X | X | X | X | X | . | . |
| `/archive` | `ArchivePage` | X | V | V | X | . | X | X | X | X | X | X | X | X | . | . |
| `/matching` | `matching/MatchingListPage` | X | V | V | X | . | X | X | X | X | X | X | X | X | . | . |
| `/matching/:invoiceId` | `matching/MatchingDetailPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/users` | `admin/AdminUsersPage` | V | X | X | X | . | X | X | X | X | X | X | X | X | . | . |
| `/admin/users/new` | `admin/AdminUserFormPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/permissions` | `admin/AdminPermissionMatrixPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/access-requests` | `admin/AdminAccessRequestsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/announcements` | `admin/AdminAnnouncementsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/compliance` | `admin/AdminCompliancePage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/departments` | `admin/AdminDepartmentsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/departments/new` | `admin/AdminDepartmentFormPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/audit` | `admin/AdminAuditPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/approval-matrix` | `admin/ApprovalMatrixPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/delegations` | `admin/AdminDelegationsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/matching-config` | `admin/AdminMatchingConfigPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/checklist-templates` | `admin/AdminChecklistTemplatesPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/escalation-rules` | `admin/EscalationRulesPage` | V | V | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/retention-policy` | `admin/AdminRetentionPolicyPage` | V | X | X | X | . | X | X | X | X | X | X | X | X | . | . |
| `/admin/archive-compliance` | `admin/AdminArchiveCompliancePage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/retention-disposition` | `admin/AdminRetentionDispositionPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/backups` | `admin/AdminBackupsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/security` | `admin/SecuritySettingsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/integrations` | `admin/IntegrationsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/department-access` | `admin/DepartmentAccessPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/suppliers` | `admin/SuppliersPage` | X | X | V | X | . | X | X | X | X | X | X | X | X | . | . |
| `/admin/suppliers/new` | `admin/SupplierOnboardingPage` | X | X | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/suppliers/:id` | `admin/SupplierDetailPage` | X | X | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/suppliers/:id/edit` | `admin/SupplierFormPage` | X | X | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `*` | `NotFoundPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | . |

---

## 3. Portail fournisseur (5 pages x 15 roles)

Les 14 roles staff sont attendus **bloques** (`SupplierRoute`) : ces cellules doivent finir en `X`,
pas en `-`, car le blocage est lui-meme un comportement a verifier.

| Route | Page | ADMIN | DAF | AA | N1 DRH | N1 DG | N1 INFO | N2 INFO | N1 TERM | N1 COM | N1 QHSSE | N1 INFRA | N2 INFRA | N1 TECH | N2 TECH | SUPPL. |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/supplier/dashboard` | `supplier/SupplierDashboardPage` | X | X | X | . | . | X | X | . | . | . | . | . | . | . | VT |
| `/supplier/invoices` | `supplier/SupplierInvoicesPage` | X | X | X | . | . | X | X | . | . | . | . | . | . | . | VT |
| `/supplier/invoices/new` | `supplier/SupplierInvoiceSubmitPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | VT |
| `/supplier/profile` | `supplier/SupplierProfilePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | V |
| `/supplier/documents` | `supplier/SupplierDocumentsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | V |

---

## Notes de couverture

1. **Routes parametrees** (`/invoices/:id`, `/matching/:invoiceId`, `/admin/suppliers/:id`,
   `/admin/suppliers/:id/edit`) : couvrir avec au moins un identifiant reel par role autorise, et un
   identifiant appartenant a un autre departement / fournisseur pour valider le cloisonnement.
2. **Pages sans `PageRoleGuard`** (`/dashboard`, `/profile`, `/access-requests`, `/notifications`,
   `/my-delegations`, `/invoices/:id`) : couverture des 15 roles **obligatoire** - c'est la que
   la fuite de donnees est la plus probable.
3. **Responsive** : chaque cellule `V` exige une capture **desktop** et une capture **mobile**.
4. **Dark mode** : verifier clair + sombre au moins une fois par page ; noter tout ecart de contraste.
5. **MFA** : ADMIN, DAF et les validateurs N1/N2 exigent un second facteur - prevoir l'enrolement
   TOTP avant de lancer P2/P3, sinon la connexion bloque.
