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

Mise a jour le **2026-07-22** a l'issue de la **Phase 2** (audit visuel).

| Zone | Cellules | Vues/verifiees (P2) | Testees (P3) |
|---|---|---|---|
| Pages publiques | 6 x 1 = 6 | 1 (`/login`) | 0 |
| Pages staff | 45 x 15 = 675 | **233** (94 `V` + 139 `X`) | 0 |
| Portail fournisseur | 5 x 15 = 75 | **15** (5 `V` + 10 `X`) | 0 |
| **Total** | **756** | **249** (~33 %) | **0** |

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

**Reste a couvrir en P3** : les 9 roles validateurs non sondes (`drh`, `dg`, `dex`, `com`, `qhsse`,
`infra`, `dir_infra`, `atelier`, `dir_tech`) — a marquer `T` lors des parcours fonctionnels, le
volet visuel etant deja couvert par `rsi`/`dsi`.

---

## 1. Pages publiques (hors authentification)

Ces pages ne dependent pas du role : une seule colonne (visiteur non authentifie).

| Route | Page | Non authentifie |
|---|---|---|
| `/login` | `LoginPage` | V (desktop 1440 + mobile 390, clair) — responsive **conforme**, contre-preuve d'AUDIT-019 |
| `/register` | `auth/RegisterPage` | . |
| `/forgot-password` | `auth/ForgotPasswordPage` | . |
| `/reset-password` | `auth/ResetPasswordPage` | . |
| `/register/supplier` | `auth/SupplierRegisterPage` | . |
| `/verify-email` | `auth/EmailVerificationPage` | . |

---

## 2. Pages staff (45 pages x 15 roles)

| Route | Page | ADMIN | DAF | AA | N1 DRH | N1 DG | N1 INFO | N2 INFO | N1 TERM | N1 COM | N1 QHSSE | N1 INFRA | N2 INFRA | N1 TECH | N2 TECH | SUPPL. |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/dashboard` | `DashboardPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/profile` | `ProfilePage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/access-requests` | `MyAccessRequestsPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/notifications` | `NotificationsPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/my-delegations` | `MyDelegationsPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | V |
| `/invoices` | `InvoiceListPage` | X | V | V | . | . | V | V | . | . | . | . | . | . | . | X |
| `/invoices/new` | `InvoiceCreatePage` | X | X | V | . | . | X | X | . | . | . | . | . | . | . | X |
| `/invoices/:id` | `InvoiceDetailPage` | V | V | V | . | . | V | V | . | . | . | . | . | . | . | X |
| `/approvals` | `ApprovalQueuePage` | X | V | V | . | . | V | V | . | . | . | . | . | . | . | . |
| `/financial-audit` | `FinancialAuditPage` | X | V | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/purchase-orders` | `PurchaseOrdersPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/goods-receipts` | `GoodsReceiptsPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/payments` | `PaymentsPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/payments/alert-rules` | `PaymentAlertRulesPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/reports` | `ReportsPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/reports/builder` | `ReportBuilderPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/archive` | `ArchivePage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/matching` | `matching/MatchingListPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/matching/:invoiceId` | `matching/MatchingDetailPage` | X | V | V | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/users` | `admin/AdminUsersPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
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
| `/admin/retention-policy` | `admin/AdminRetentionPolicyPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/archive-compliance` | `admin/AdminArchiveCompliancePage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/retention-disposition` | `admin/AdminRetentionDispositionPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/backups` | `admin/AdminBackupsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/security` | `admin/SecuritySettingsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/integrations` | `admin/IntegrationsPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/department-access` | `admin/DepartmentAccessPage` | V | X | X | . | . | X | X | . | . | . | . | . | . | . | . |
| `/admin/suppliers` | `admin/SuppliersPage` | X | X | V | . | . | X | X | . | . | . | . | . | . | . | . |
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
| `/supplier/dashboard` | `supplier/SupplierDashboardPage` | X | X | X | . | . | X | X | . | . | . | . | . | . | . | V |
| `/supplier/invoices` | `supplier/SupplierInvoicesPage` | X | X | X | . | . | X | X | . | . | . | . | . | . | . | V |
| `/supplier/invoices/new` | `supplier/SupplierInvoiceSubmitPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | V |
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
