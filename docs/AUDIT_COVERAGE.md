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

| Zone | Cellules | Vues (P2) | Testees (P3) |
|---|---|---|---|
| Pages publiques | 6 x 1 = 6 | 0 | 0 |
| Pages staff | 45 x 15 = 675 | 0 | 0 |
| Portail fournisseur | 5 x 15 = 75 | 0 | 0 |
| **Total** | **756** | **0** | **0** |

---

## 1. Pages publiques (hors authentification)

Ces pages ne dependent pas du role : une seule colonne (visiteur non authentifie).

| Route | Page | Non authentifie |
|---|---|---|
| `/login` | `LoginPage` | . |
| `/register` | `auth/RegisterPage` | . |
| `/forgot-password` | `auth/ForgotPasswordPage` | . |
| `/reset-password` | `auth/ResetPasswordPage` | . |
| `/register/supplier` | `auth/SupplierRegisterPage` | . |
| `/verify-email` | `auth/EmailVerificationPage` | . |

---

## 2. Pages staff (45 pages x 15 roles)

| Route | Page | ADMIN | DAF | AA | N1 DRH | N1 DG | N1 INFO | N2 INFO | N1 TERM | N1 COM | N1 QHSSE | N1 INFRA | N2 INFRA | N1 TECH | N2 TECH | SUPPL. |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/dashboard` | `DashboardPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/profile` | `ProfilePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/access-requests` | `MyAccessRequestsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/notifications` | `NotificationsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/my-delegations` | `MyDelegationsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/invoices` | `InvoiceListPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/invoices/new` | `InvoiceCreatePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/invoices/:id` | `InvoiceDetailPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/approvals` | `ApprovalQueuePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/financial-audit` | `FinancialAuditPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/purchase-orders` | `PurchaseOrdersPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/goods-receipts` | `GoodsReceiptsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/payments` | `PaymentsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/payments/alert-rules` | `PaymentAlertRulesPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/reports` | `ReportsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/reports/builder` | `ReportBuilderPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/archive` | `ArchivePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/matching` | `matching/MatchingListPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/matching/:invoiceId` | `matching/MatchingDetailPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/users` | `admin/AdminUsersPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/users/new` | `admin/AdminUserFormPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/permissions` | `admin/AdminPermissionMatrixPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/access-requests` | `admin/AdminAccessRequestsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/announcements` | `admin/AdminAnnouncementsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/compliance` | `admin/AdminCompliancePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/departments` | `admin/AdminDepartmentsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/departments/new` | `admin/AdminDepartmentFormPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/audit` | `admin/AdminAuditPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/approval-matrix` | `admin/ApprovalMatrixPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/delegations` | `admin/AdminDelegationsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/matching-config` | `admin/AdminMatchingConfigPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/checklist-templates` | `admin/AdminChecklistTemplatesPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/escalation-rules` | `admin/EscalationRulesPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/retention-policy` | `admin/AdminRetentionPolicyPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/archive-compliance` | `admin/AdminArchiveCompliancePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/retention-disposition` | `admin/AdminRetentionDispositionPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/backups` | `admin/AdminBackupsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/security` | `admin/SecuritySettingsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/integrations` | `admin/IntegrationsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/department-access` | `admin/DepartmentAccessPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/suppliers` | `admin/SuppliersPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/suppliers/new` | `admin/SupplierOnboardingPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/suppliers/:id` | `admin/SupplierDetailPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/admin/suppliers/:id/edit` | `admin/SupplierFormPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `*` | `NotFoundPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |

---

## 3. Portail fournisseur (5 pages x 15 roles)

Les 14 roles staff sont attendus **bloques** (`SupplierRoute`) : ces cellules doivent finir en `X`,
pas en `-`, car le blocage est lui-meme un comportement a verifier.

| Route | Page | ADMIN | DAF | AA | N1 DRH | N1 DG | N1 INFO | N2 INFO | N1 TERM | N1 COM | N1 QHSSE | N1 INFRA | N2 INFRA | N1 TECH | N2 TECH | SUPPL. |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `/supplier/dashboard` | `supplier/SupplierDashboardPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/supplier/invoices` | `supplier/SupplierInvoicesPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/supplier/invoices/new` | `supplier/SupplierInvoiceSubmitPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/supplier/profile` | `supplier/SupplierProfilePage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |
| `/supplier/documents` | `supplier/SupplierDocumentsPage` | . | . | . | . | . | . | . | . | . | . | . | . | . | . | . |

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
