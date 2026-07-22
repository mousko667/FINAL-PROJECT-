# AUDIT_MASTER — Registre unique de l'audit exhaustif

> **Registre persistant sur les 7 phases (P0→P6).** C'est la source de vérité unique des findings.
> Méthodologie : `docs/superpowers/specs/2026-07-22-audit-exhaustif-systeme-design.md`.
> Modèle du système : `docs/AUDIT_SYSTEM_MODEL.md`. Couverture : `docs/AUDIT_COVERAGE.md`.
>
> Ouvert le **2026-07-22** sur la branche `audit/exhaustif-p0-comprehension` (depuis `main` = `c4f5e11`).

---

## Règles du registre — à ne jamais enfreindre

1. **Un ID n'est jamais réutilisé.** Numérotation continue `AUDIT-001`, `AUDIT-002`, … Prochain ID
   libre : **AUDIT-002**.
2. **Un finding n'est jamais réévalué deux fois.** S'il ressort dans une phase ultérieure, on
   complète la ligne existante (preuve runtime, dépendances) — on n'en crée pas une nouvelle.
3. **Preuve runtime obligatoire** pour tout finding visuel ou fonctionnel (capture Playwright, trace
   réseau, log console). Un finding sans preuve reste « à confirmer » et ne peut pas passer en P6.
4. **Aucun finding n'est émis en P0.** P0 = compréhension + mise en place. Les findings commencent
   en P1. AUDIT-001 est la seule exception : il était pré-identifié avant le lancement de l'audit.
5. **Statuts autorisés** : `OUVERT` · `EN COURS` · `CORRIGÉ` · `HORS-SCOPE`.
   `HORS-SCOPE` exige une validation explicite de l'utilisateur, consignée dans la ligne.
6. **Sévérités** : `P0` bloquant (le système est faux ou dangereux) · `P1` majeur (fonction cassée
   ou faille SoD) · `P2` mineur (gêne réelle, contournable) · `P3` cosmétique / dette / suggestion.
7. **Fin d'audit (P6)** : plus aucune ligne en `OUVERT` ou `EN COURS`.

---

## Registre

| ID | Phase | Module | Sévérité | Titre | Localisation | Cause probable | Preuve runtime | Solution proposée | Dépendances | Statut |
|---|---|---|---|---|---|---|---|---|---|---|
| AUDIT-001 | P0 | supplier-portal | À confirmer (P2/P3 proposé) | Portail fournisseur : aucun endpoint de lecture des PO — le fournisseur recopie le numéro de commande à la main | `src/main/java/com/oct/invoicesystem/domain/invoice/model/Invoice.java:138` (`purchaseOrderId`) · `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java:273` (`toInvoice`) | `purchaseOrderId` est un champ **plat déclaratif** (UUID repris tel quel de la requête), pas une relation « facturer contre une commande ». `SupplierPortalController` (classe `@PreAuthorize("hasRole('SUPPLIER')")`, base `/api/v1/supplier`) n'expose que factures / documents / profil / dashboard — **aucun `GET` de PO ouverts**. Le fournisseur doit donc connaître le numéro de commande hors système (email, appel, contrat) et le saisir manuellement ; seul le three-way matching côté OCT le vérifie a posteriori. | À vérifier en **P3** | Ajouter un endpoint `GET /api/v1/supplier/purchase-orders` listant les PO **ouverts du fournisseur connecté**, puis une sélection (liste déroulante / recherche) dans `SupplierInvoiceSubmitPage` au lieu d'une saisie libre. Fiabilise le matching et supprime les fautes de frappe. **Décision corriger / hors-scope à prendre en P5**, cahier des charges sous les yeux. | — | OUVERT |
| AUDIT-002 | P1 | supplier-portal / purchasing | **P1** (majeur — SoD / fuite inter-fournisseurs) | Aucun contrôle de propriété du `purchaseOrderId` saisi par le fournisseur : un fournisseur peut référencer le PO d'un **autre** fournisseur | `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java:260-281` (`toInvoice`) · `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java:170-201` (`performMatchingCheck`) · `src/main/java/com/oct/invoicesystem/domain/purchasing/model/PurchaseOrder.java:52-53` | `toInvoice` recopie `request.purchaseOrderId()` sans validation. `performMatchingCheck` charge ensuite le PO par `findByIdActive(...)` **sans jamais comparer `po.getSupplier().getId()` au fournisseur de la facture**, alors que `PurchaseOrder` porte bien un `supplier_id` non nul. Le rapprochement 3-way peut donc s'exécuter — et réussir — contre le bon de commande d'un tiers, ce qui vaut à la fois divulgation indirecte (montants/quantités d'un concurrent via le résultat du matching) et contournement du contrôle financier. Aucun test ne couvre ce cas (`SupplierPortalController` n'a pas de test d'intégration dédié). | Vide (P1 statique) — **à prouver en P3** : soumettre au portail une facture référençant un PO d'un autre fournisseur | Dans `performMatchingCheck` (et/ou à la création côté portail), rejeter si `po.getSupplier().getId()` ≠ fournisseur de la facture, avec un message i18n dédié. Ajouter un test d'intégration « PO d'un autre fournisseur → refus ». | Recoupe AUDIT-001 (un `GET /supplier/purchase-orders` scopé supprimerait la saisie libre) | OUVERT |
| AUDIT-003 | P1 | invoice / frontend | **P1** (majeur — SoD ADMIN) | `InvoiceDetailPage` n'a aucun `PageRoleGuard` : toute la donnée financière d'une facture est lisible par URL directe | `frontend/src/pages/InvoiceDetailPage.tsx` (aucune occurrence de `PageRoleGuard` dans tout le fichier) · à comparer à `frontend/src/pages/InvoiceListPage.tsx:313` (gardé par `ALLOWED_ROLES`) · route `frontend/src/AppRoutes.tsx:92` | Les routes ne portent aucune restriction de rôle (`ProtectedRoute` ne vérifie que l'authentification) : la protection est portée par la page. `InvoiceDetailPage` est la seule page financière sans garde. Seuls certains boutons sont conditionnés (`canOverride`, `canClassify`), **pas la lecture**. Le backend refuse bien ADMIN sur `GET /api/v1/invoices/{id}` (`InvoiceController.java:99`, `!hasRole('ADMIN')`), donc l'impact réel dépend du rendu en cas de 403 — d'où la nécessité d'une preuve runtime. | Vide (P1 statique) — **à prouver en P2/P3** : ouvrir `/invoices/:id` connecté en `admin` et observer l'écran + la trace réseau | Ajouter `<PageRoleGuard allowedRoles={ALLOWED_ROLES}>` en réutilisant la constante de `InvoiceListPage.tsx:17`, pour un refus homogène et explicite côté UI. | — | OUVERT |
| AUDIT-004 | P1 | workflow / frontend | **P2** (mineur — garde absente, backend couvrant) | `MyDelegationsPage` sans `PageRoleGuard` alors que la Sidebar réserve l'entrée aux validateurs + DAF | `frontend/src/pages/MyDelegationsPage.tsx` (aucun `PageRoleGuard`) · `frontend/src/components/layout/Sidebar.tsx:141-152` (entrée sous garde DAF + 11 validateurs) | Écart nav ⊂ page : un `ROLE_ADMIN` ou `ROLE_ASSISTANT_COMPTABLE` qui saisit `/my-delegations` atteint la page. Le backend protège les appels (`DelegationController`, constante `APPROVER_ROLES` = DAF + 11 validateurs, **sans ADMIN** — vérifié lignes 64-70), donc la page s'affichera vide/en erreur plutôt que de fuiter des données. Défaut d'homogénéité plutôt que faille. | Vide (P1 statique) — à confirmer en P3 | Ajouter un `PageRoleGuard` reprenant exactement la liste de `Sidebar.tsx:141-149`. | — | OUVERT |
| AUDIT-005 | P1 | workflow / frontend | **P2** (mineur — fonction inaccessible) | Entrée de navigation « Escalades » invisible pour le DAF : son `RoleGuard` est imbriqué dans le bloc ADMIN | `frontend/src/components/layout/Sidebar.tsx:206-208` (garde interne `['ROLE_ADMIN','ROLE_DAF']`) imbriqué dans le bloc ouvert ligne 188 (`['ROLE_ADMIN']`) et fermé ligne 213 | Le garde externe ADMIN filtre avant le garde interne : la condition `ROLE_DAF` est **morte**. Or `frontend/src/pages/admin/EscalationRulesPage.tsx:57` autorise bien `['ROLE_ADMIN','ROLE_DAF']` et le backend aussi (`EscalationRuleController`, `hasAnyRole('ADMIN','DAF')`). Le DAF dispose donc du droit mais n'a aucun chemin de navigation — fonction atteignable uniquement par URL directe. | Vide (P1 statique) — à confirmer en P2 (connexion `daf`, inspection de la sidebar) | Sortir le bloc `<RoleGuard allowedRoles={['ROLE_ADMIN','ROLE_DAF']}>` du bloc ADMIN et le placer au niveau des autres sections. | — | OUVERT |
| AUDIT-006 | P1 | supplier-portal | **P2** (mineur — gestion d'erreurs) | Seul `try/catch` brut des 40 contrôleurs : requalifie les erreurs métier en HTTP 500 | `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java:220-240` (`catch (Exception e)` → `throw new RuntimeException(...)`) | Violation directe de CLAUDE.md §3 (« tout passe par `GlobalExceptionHandler`, pas de try/catch brut en contrôleur »). Le `catch (Exception e)` capture aussi les `ValidationException` métier levées par `supplierService.uploadDocument` (fichier vide, >10 Mo, MIME refusé) et les relance en `RuntimeException`, qui tombe dans le handler générique (`GlobalExceptionHandler.java:142`) → **500 au lieu de 400/413**. Le fournisseur reçoit « une erreur inattendue » au lieu du motif réel. | Vide (P1 statique) — à prouver en P3 (upload d'un fichier >10 Mo au portail) | Supprimer le bloc try/catch et laisser remonter ; encapsuler uniquement les exceptions techniques vérifiées (`NoSuchAlgorithmException`, I/O) dans une `ValidationException`/exception métier dédiée. | — | OUVERT |
| AUDIT-007 | P1 | invoice / documents | **P2** (mineur — cloisonnement inter-départements) | Documents de facture énumérables et téléchargeables par tout utilisateur interne, sans contrôle de département | `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceDocumentController.java:65-69` (`list`) et `:76-89` (`download`), tous deux `@PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")` | Le seul garde-fou est « pas SUPPLIER » : **ADMIN passe ce filtre**, ainsi que n'importe quel validateur d'un autre département. `listByInvoice` (`InvoiceDocumentService.java:198`) ne filtre que par `invoiceId`. À décharge, `generateDownloadUrlAndLog` (`:219`) utilise bien `findByIdAndInvoiceId`, donc pas de confusion document↔facture, et chaque téléchargement est journalisé (`DocumentAccessLog`, append-only). Le défaut est l'absence de contrôle de périmètre, pas un IDOR de couple d'identifiants. | Vide (P1 statique) — **à prouver en P3** : `GET /api/v1/invoices/{id}/documents` avec un compte validateur d'un autre département, et avec `admin` | Aligner sur `InvoiceController` en excluant ADMIN (`!hasRole('ADMIN')`) et ajouter un contrôle de périmètre départemental côté service, cohérent avec `DepartmentAccess`. | Recoupe AUDIT-003 (même principe : ADMIN ≠ donnée financière) | OUVERT |
| AUDIT-008 | P1 | purchasing / matching | **P2** (mineur — SoD ADMIN sur un contrôle financier) | ADMIN modifie **seul** les seuils de tolérance du rapprochement 3-way | `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingConfigController.java:46` (`POST`, `hasRole('ADMIN')`) et `:37` (`GET`, `hasAnyRole('ADMIN','ASSISTANT_COMPTABLE')`) · page `frontend/src/pages/admin/AdminMatchingConfigPage.tsx:80` (ADMIN) | Les seuils de tolérance et l'exigence de GRN sont **le** paramètre qui décide si un écart de facturation passe ou bloque. Les confier à ADMIN seul — rôle par ailleurs privé de tout accès financier — permet à un administrateur technique de relâcher un contrôle financier sans qu'aucun rôle financier (DAF) ne soit dans la boucle. À trancher au regard du cahier des charges : paramétrage « technique » assumé, ou contrôle financier à cosigner par le DAF ? | Vide (P1 statique) | Décision en P5. Piste : passer l'écriture à `hasAnyRole('ADMIN','DAF')` ou à DAF seul, et tracer le changement au journal d'audit financier. | — | OUVERT |
| AUDIT-009 | P1 | retention / compliance | **P2** (mineur — SoD ADMIN sur pièces financières) | ADMIN décide seul de la **purge** d'un document, y compris pièce justificative de facture | `src/main/java/com/oct/invoicesystem/domain/retention/controller/RetentionDispositionController.java:39` (`PUT …/disposition`, `hasRole('ADMIN')`) et `:32` · `RetentionPolicyController.java:30/37/44` (ADMIN) | La disposition `PURGED` détruit une pièce justificative. Le pouvoir de destruction appartient donc entièrement à un rôle réputé sans accès financier, alors que CLAUDE.md §3 impose le soft-delete des enregistrements financiers et que `V33__enforce_financial_retention.sql` verrouille la suppression dure en base. Tension entre « ADMIN = gardien de la rétention » (défendable) et « ADMIN ≠ financier ». | Vide (P1 statique) | Décision en P5. Piste : double validation (ADMIN propose, DAF confirme) sur la disposition `PURGED` uniquement ; la politique de rétention peut rester ADMIN. | AUDIT-008 (même arbitrage SoD) | OUVERT |
| AUDIT-010 | P1 | transverse / API | **P2** (mineur — robustesse / divulgation de schéma) | Paramètre `sort` repris du client sans liste blanche (3 sites), atteignable depuis le portail fournisseur | `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java:182-186` · `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceService.java:247-252` · `src/main/java/com/oct/invoicesystem/domain/department/service/DepartmentService.java:33-39` | `sort.split(",")[0]` est passé tel quel à `Sort.by`. Pas d'injection SQL (Spring Data valide la propriété contre le métamodèle), mais un nom de propriété arbitraire provoque une 500 divulguant la structure de l'entité, et une valeur de type `relation.sousRelation` déclenche des jointures non prévues. `InvoiceService.java:247` est atteint depuis `SupplierPortalController`, donc exposé à un compte SUPPLIER. `sortParams[0]` sur une chaîne vide lève en plus une exception non métier. | Vide (P1 statique) — à prouver en P3 (`?sort=` vide, puis `?sort=nimportequoi`) | Liste blanche des colonnes triables par endpoint, repli silencieux sur le tri par défaut si la valeur est inconnue ou vide. | — | OUVERT |
| AUDIT-011 | P1 | infrastructure / sécurité | **P2** (mineur — exposition de configuration) | `/actuator/**` en `permitAll()` : `metrics` et `info` accessibles sans authentification | `src/main/java/com/oct/invoicesystem/config/SecurityConfig.java:66` (`.requestMatchers("/actuator/**").permitAll()`) · `src/main/resources/application.yaml:127` (`include: health,info,metrics`) · `:140` (`env.enabled: true`) | `health` protège son détail (`show-details: when-authorized`), mais `metrics` et `info` restent ouverts à un anonyme : noms de beans, métriques JVM/HTTP, éventuellement chemins d'URI. Surface de reconnaissance inutile pour un système financier exposé. | Vide (P1 statique) — à prouver en P3 (`GET /actuator/metrics` sans jeton) | Restreindre `/actuator/**` à ADMIN (sauf `/actuator/health` en `permitAll` pour les sondes), ou réduire `include` à `health` seul en production. | — | OUVERT |
| AUDIT-012 | P1 | infrastructure / secrets | **P2** (mineur — secret par défaut actif en production) | Secret MinIO avec valeur par défaut en dur dans le profil **partagé** (donc actif en prod si la variable manque) | `src/main/resources/application.yaml:105` (`secret-key: ${MINIO_SECRET_KEY:dev-minio-secret-change-me}`) | Contrairement à `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`/`ENCRYPTION_KEY` (`:83-84`, `:91`) qui n'ont **aucune** valeur par défaut, le secret MinIO en a une, et hors du bloc `dev` : si `MINIO_SECRET_KEY` n'est pas défini en production, le stockage documentaire démarre avec un secret public connu. Viole CLAUDE.md §3 (« jamais de secret en dur »). | Vide (P1 statique) | Retirer la valeur par défaut (`${MINIO_SECRET_KEY}`) pour que l'absence fasse échouer le démarrage, et ne garder un défaut que dans le bloc `dev`. | — | OUVERT |
| AUDIT-013 | P1 | tests | **P2** (mineur — dette de tests sur des surfaces sensibles) | 14 contrôleurs sur 40 sans aucune couverture de test, dont `AuthController` et `SupplierController` | Contrôleurs sans aucune référence dans `src/test` (recherche littérale) : `AuthController`, `SupplierController`, `SupplierRelationshipController`, `AnnouncementController`, `ArchiveFolderController`, `BackupController`, `ChecklistTemplateController`, `GoodsReceiptController`, `IntegrationConnectorController`, `InvoiceChecklistController`, `MatchingConfigController`, `PaymentAlertRuleController`, `RoleController`, `SecurityHealthController` | CLAUDE.md §3 impose « un test d'intégration pour chaque endpoint de contrôleur ». `AuthController` (login, MFA/TOTP, réinitialisation de mot de passe) est la surface la plus sensible du système et n'a aucun test ; `MatchingConfigController` porte le contrôle financier d'AUDIT-008 ; `SupplierController` gère des coordonnées bancaires chiffrées. La suite est verte (628 tests) mais aveugle sur ces chemins. | Vide (P1 statique) | Prioriser en P6 : d'abord `AuthController` (login + MFA + verrouillage après 5 échecs), puis `MatchingConfigController` et `SupplierController`. | AUDIT-002 (le test « PO d'un autre fournisseur » relève du même manque) | OUVERT |
| AUDIT-014 | P1 | transverse / frontend | **P3** (dette — gestion d'erreurs muette) | ~14 fichiers avec des mutations `apiClient` sans aucun `onError` : les échecs sont silencieux | `frontend/src/pages/admin/AdminCompliancePage.tsx:30,33,34,37,38,40,43,44,46` (10 mutations, 0 gestion) · `frontend/src/pages/ReportsPage.tsx:164,168` (exports) · `frontend/src/components/admin/IntegrationConnectors.tsx:35,39,43,47,51` · `frontend/src/components/archive/ArchiveFolderTree.tsx:33,42,50` · `frontend/src/pages/admin/AdminAccessRequestsPage.tsx:39` · `frontend/src/pages/NotificationsPage.tsx:49,54` · `frontend/src/pages/supplier/SupplierProfilePage.tsx:32` · et 7 autres | Aucun `onError` ni rendu `isError` : en cas d'échec serveur, l'utilisateur ne reçoit **aucun retour** — l'action semble avoir réussi. Particulièrement gênant sur les exports de rapports et sur l'approbation/refus des demandes d'accès. | Vide (P1 statique) — à confirmer en P3 (backend arrêté, déclencher une mutation) | Ajouter un `onError` homogène (toast + message traduit), en commençant par `AdminCompliancePage` et `ReportsPage`. | AUDIT-015 (même famille : traitement des erreurs backend) | OUVERT |
| AUDIT-015 | P1 | i18n / frontend | **P3** (dette — message non traduit) | 11 sites affichent le message d'erreur backend **brut**, sans le passer par `t()` | `frontend/src/pages/GoodsReceiptsPage.tsx:177` · `frontend/src/pages/InvoiceCreatePage.tsx:399` · `frontend/src/pages/PaymentsPage.tsx:138` · `frontend/src/pages/supplier/SupplierInvoiceSubmitPage.tsx:289` · `frontend/src/pages/admin/AdminDelegationsPage.tsx:102` · `frontend/src/pages/admin/AdminMatchingConfigPage.tsx:60` · `frontend/src/pages/admin/SecuritySettingsPage.tsx:80` · `frontend/src/pages/MyAccessRequestsPage.tsx:76` · `frontend/src/pages/MyDelegationsPage.tsx:54` · `frontend/src/components/matching/MatchingLineResolveModal.tsx:34` · `frontend/src/pages/admin/AdminUsersPage.tsx:102` | Le backend renvoie des **clés i18n** comme messages d'erreur (règle PROB-006) ; ces sites les affichent telles quelles → l'utilisateur voit `error.payment.only_bon_a_payer` au lieu d'une phrase. Le motif correct existe déjà dans le code : `frontend/src/components/invoice/InvoiceActionPanel.tsx:174-180` (traduit, et ne retombe sur le brut que si `t(key) === key`), repris aussi en `PaymentsPage.tsx:505` — le même fichier est donc incohérent avec lui-même. | Vide (P1 statique) — à confirmer en P3 (provoquer une erreur métier) | Extraire le motif d'`InvoiceActionPanel.tsx:174-180` dans un utilitaire partagé et l'appliquer aux 11 sites. | AUDIT-014 | OUVERT |
| AUDIT-016 | P1 | frontend / dette | **P3** (dette — code mort et incohérences mineures) | Export mort `StaffRoute`, rôle fantôme `ROLE_VALIDATEUR_N1_FIN`, `any` en signature publique | `frontend/src/components/auth/ProtectedRoute.tsx:49` (`StaffRoute` défini, jamais importé) · `frontend/src/pages/ProfilePage.tsx:42` (`ROLE_VALIDATEUR_N1_FIN`) · `frontend/src/api/suppliers.ts:45` (`filters: Record<string, any>`) · 7 casts `as any` sur des erreurs axios | `ROLE_VALIDATEUR_N1_FIN` a été supprimé du système — `frontend/src/constants/roles.ts:4` et `V5__seed_roles_and_admin.sql:5` le documentent explicitement — mais il subsiste dans la liste `MFA_REQUIRED_ROLES` de `ProfilePage`. Sans effet fonctionnel (aucun utilisateur ne porte ce rôle), mais c'est une incohérence avec la source de vérité. `StaffRoute` est du code mort. | Vide (P1 statique) | Supprimer `StaffRoute`, retirer `ROLE_VALIDATEUR_N1_FIN` de `ProfilePage.tsx:42`, typer `useSuppliers`, introduire un type `ApiError` partagé pour éliminer les 7 casts. | — | OUVERT |
| AUDIT-017 | P1 | documentation | **P3** (cohérence documentaire) | `CLAUDE.md` en retard sur le code : étape `EN_CONTROLE_AA` absente du §5 et override de matching décrit « DAF ou ADMIN » | `CLAUDE.md §5` (workflow sans `EN_CONTROLE_AA`) · `CLAUDE.md §9` (« ROLE_DAF ou ROLE_ADMIN » pour l'override) vs code `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java:322` (`@PreAuthorize("hasRole('DAF')")`) — la Javadoc de cette même méthode (`:324`) répète l'erreur « DAF or Admin » | Deux écarts doc/code. Pour l'override, le **code fait foi** et est conforme à la SoD (ADMIN ≠ financier) : c'est la documentation qui est fausse, pas le code — la corriger en ouvrant l'accès à ADMIN serait une régression de sécurité. Pour `EN_CONTROLE_AA`, `docs/WORKFLOW.md:49-50` documente bien l'étape ; seul `CLAUDE.md §5` est périmé. | Sans objet (documentaire) | Mettre `CLAUDE.md §5` à jour avec `SOUMIS → EN_CONTROLE_AA → EN_VALIDATION_N1`, corriger `CLAUDE.md §9` en « DAF uniquement », et corriger la Javadoc `InvoiceController.java:324`. | — | OUVERT |

---

## Baseline de tests (établie au début de P1, 2026-07-22)

Mesurée **avant** toute analyse, sur `audit/exhaustif-p1-code-statique` (= `6d71d6d`, aucun code
modifié depuis `main`). C'est la référence contre laquelle toute régression de P6 sera jugée.

| Suite | Commande | Résultat |
|---|---|---|
| Backend | `./mvnw.cmd test` | **628 tests · 0 échec · 0 erreur · 0 ignoré** — `BUILD SUCCESS` |
| Frontend | `npm run test` (vitest) | **237 tests · 60 fichiers · 0 échec** |

⚠ Un premier lancement **simultané** des deux suites a produit 1 échec frontend apparent
(`Failed to start forks worker` / `Timeout waiting for worker to respond`) : c'est une
**contention CPU** entre Maven et vitest, pas un test cassé. Relancée seule, la suite frontend
passe intégralement. **Ne pas lancer les deux suites en parallèle.**

La baseline est **entièrement verte des deux côtés** : l'audit démarre sur une suite saine.
P1 n'a modifié **aucun** fichier de code — seuls les documents d'audit sont touchés.

## Synthèse par statut

| Statut | Nombre |
|---|---|
| OUVERT | 17 |
| EN COURS | 0 |
| CORRIGÉ | 0 |
| HORS-SCOPE | 0 |
| **Total** | **17** |

## Synthèse par sévérité

| Sévérité | Nombre | IDs |
|---|---|---|
| P0 (bloquant) | 0 | — |
| P1 (majeur) | 2 | AUDIT-002, AUDIT-003 |
| P2 (mineur) | 10 | AUDIT-004 → AUDIT-013 |
| P3 (cosmétique / dette) | 4 | AUDIT-014 → AUDIT-017 |
| À confirmer | 1 | AUDIT-001 |

## Synthèse par phase

| Phase | Findings émis | État |
|---|---|---|
| P0 — Amorçage + compréhension | 1 (pré-identifié) | ✅ terminée |
| P1 — Audit code statique | 16 (AUDIT-002 → AUDIT-017) | ✅ terminée |
| P2 — Audit visuel + responsive | — | à faire |
| P3 — Audit fonctionnel end-to-end | — | à faire |
| P4 — Audit transverse (perf, i18n, a11y) | — | à faire |
| P5 — Consolidation + backlog priorisé | — | à faire |
| P6 — Correction par vagues | — | à faire |

---

## Pistes ouvertes P0 — arbitrage rendu en P1 (clos)

Les 10 observations de P0 ont toutes été instruites **dans le code réel** en P1. Chacune est soit
promue en `AUDIT-NNN`, soit **écartée avec justification**. Cette section est close : elle ne doit
plus être ré-instruite dans les phases suivantes.

| # | Observation P0 | Verdict P1 | Justification (vérifiée dans le code) |
|---|---|---|---|
| O-1 | `EN_CONTROLE_AA` absent de la doc | **Écartée partiellement → AUDIT-017** | La prémisse est **fausse pour `docs/WORKFLOW.md`** : les lignes 49-50, 81, 92, 135-138 documentent explicitement l'étape et le rôle requis par état source. Seul `CLAUDE.md §5` est périmé → consigné en AUDIT-017 (P3, documentaire). Aucun défaut de code. |
| O-2 | `POST /validate-n1` ouvert au DAF | **ÉCARTÉE — pas une faille** | Défense en profondeur en aval. `RoleMatchGuard.java:56-57` exige `dept.getN1Role()` pour `VALIDATE_N1`, et `ApprovalServiceImpl.validateN1` (`:94`) appelle `checkRole(currentUser, invoice.getDepartment().getN1Role())`. Un DAF sans le rôle N1 du département est donc **refusé** (`AccessDeniedException`). Le `@PreAuthorize` large n'est qu'un premier filtre grossier. S'ajoutent `ensureNotSubmitter`, `ensureNotN1Approver` (N2 ≠ N1) et `ensureWithinApprovalLimit`. |
| O-3 | `/api/v1/suppliers/**` ouvert à ADMIN + AA vs frontend AA seul | **ÉCARTÉE en P1 — à re-trancher en P5** | Écart réel et confirmé (10 endpoints `hasAnyRole('ADMIN','ASSISTANT_COMPTABLE')`, `SupplierController.java:61,67,75,81,95,141,149,171,193`, plus `:158` ADMIN seul), mais ce n'est pas un défaut isolé : c'est **le même arbitrage SoD** que AUDIT-008/009 (jusqu'où va l'ADMIN « technique »). Le référentiel fournisseur contient des coordonnées bancaires chiffrées → à trancher avec le cahier des charges en P5, pas à décider unilatéralement en P1. Couvert par AUDIT-013 pour le volet « aucun test ». |
| O-4 | Override matching : DAF seul (code) vs « DAF ou ADMIN » (doc) | **Promue → AUDIT-017** | Le **code fait foi** et est conforme à la SoD : `InvoiceController.java:322` = `hasRole('DAF')`. La doc est fausse — et la Javadoc de la méthode elle-même (`:324`) répète l'erreur. « Corriger » en ouvrant l'accès à ADMIN serait une **régression de sécurité**. |
| O-5 | `RECORD_PAYMENT` / `ARCHIVE` sans garde de machine à états | **ÉCARTÉE — contrôle équivalent en service** | `ARCHIVE` a une garde explicite : `InvoiceStateMachineServiceImpl.java:76-82` rejette l'événement si le drapeau `AUTO_ARCHIVE` est absent (« Archive transition is automatic »). `RECORD_PAYMENT` n'est émis que par `PaymentServiceImpl.finalizePayment` (`:129`), atteignable uniquement via `recordPayment`, qui vérifie le statut `BON_A_PAYER` (`:80`), l'absence de paiement existant (`:83`) et est protégé par `@PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")`. Le contrôle est déplacé, pas absent. |
| O-6 | `InvoiceDetailPage` sans `PageRoleGuard` | **PROMUE → AUDIT-003 (P1)** | Confirmé : aucune occurrence de `PageRoleGuard` dans le fichier, contre `InvoiceListPage.tsx:313` qui en a un. |
| O-7 | `/payments/alert-rules` page orpheline | **ÉCARTÉE — prémisse fausse** | La page **est** atteignable : `PaymentsPage.tsx:286` contient un `<Link to="/payments/alert-rules">`. Elle n'a pas d'entrée dans la Sidebar globale, ce qui est un choix de navigation contextuelle cohérent (sous-page de Paiements), pas un orphelin. Par ailleurs l'audit frontend confirme **zéro page orpheline** : les 56 pages sont toutes routées dans `AppRoutes.tsx`. |
| O-8 | Pas de contrôle de propriété du `purchaseOrderId` au portail | **PROMUE → AUDIT-002 (P1)** | Confirmé sur deux niveaux : `toInvoice` (`SupplierPortalController.java:260-281`) recopie l'UUID sans validation, et `performMatchingCheck` (`InvoiceStateMachineServiceImpl.java:180`) charge le PO sans comparer son `supplier_id` (pourtant `nullable = false`, `PurchaseOrder.java:52-53`). |
| O-9 | `APPROVER_ROLES` non résolue | **ÉCARTÉE — conforme** | Lue : `DelegationController.java:64-70` = `ROLE_DAF` + les 11 validateurs N1/N2. **ADMIN en est absent**, conformément à la SoD. Aucun défaut. |
| O-10 | `DepartmentTransitionGuard` : critère « deux niveaux » | **ÉCARTÉE — conforme** | Lu intégralement : le routage N1→N2 repose sur `Department.isRequiresN2()`, **drapeau en base**, jamais une liste en dur (`requiresN2` et `isSingleLevel` lisent le `Department` depuis l'extended state). Conforme au cahier des charges et à la règle « seuils/critères en base, pas en dur ». |

### Ce qui a été vérifié et s'est révélé CONFORME (aucun finding)

Consigné pour éviter toute ré-instruction en P2→P4 :

- **Couverture `@PreAuthorize`** : les 40 contrôleurs (219 endpoints) sont couverts, soit méthode par
  méthode, soit au niveau classe (`SupplierPortalController:53`, `UserController:36`, `RoleController:25`).
  **Zéro endpoint non protégé.**
- **Cloisonnement du journal d'audit** : `AuditController` n'est **pas** une violation SoD malgré le
  `hasRole('ADMIN') or hasRole('DAF')` de `/`, `/export` et `/summary/export` : les trois passent par
  `allowedActionsForCurrentUser(authentication)` (`:132`, `:164`), qui restreint chaque rôle à son
  propre sous-ensemble d'actions (ADMIN=système, DAF=financier). C'est le correctif du finding
  antérieur N3, explicitement commenté aux lignes 111-114 et 161-162.
- **Injection SQL/JPQL** : aucune concaténation d'entrée utilisateur. Aucun `EntityManager`,
  `createQuery` ni `createNativeQuery` dans tout `src/main/java`. Les 2 requêtes natives
  (`InvoiceRepository:132`, `SupplierRepository:42`) sont intégralement paramétrées.
- **Journalisation de données sensibles** : zéro. Les 3 occurrences contenant un mot-clé sensible
  journalisent un libellé, jamais une valeur (`MfaService.java:85` journalise un message constant
  sans interpoler l'OTP).
- **Devise XAF** : zéro occurrence de `XOF` dans `frontend/src` et dans `src/main/java`. Les seules
  mentions sont dans `V45__normalize_currency_xof_to_xaf.sql`, la migration qui **élimine** XOF.
  Aucune régression.
- **i18n backend** : parité parfaite FR/EN — **310 clés de chaque côté, aucune clé orpheline dans un
  sens ni dans l'autre** (comparaison des jeux de clés, pas seulement des effectifs).
- **Encodage `messages_fr.properties`** : ⚠ **la consigne héritée est périmée.** Le fichier est en
  **UTF-8**, et `MessageSourceConfig.java:19` déclare explicitement `setDefaultEncoding("UTF-8")`.
  L'ensemble est donc cohérent. La règle « ISO-8859-1 / ASCII \uXXXX » ne correspond plus au code.
- **Dette frontend** : zéro `TODO`/`FIXME`/`HACK`, zéro `console.log`, zéro `@ts-ignore` en code de
  production. Zéro page orpheline, zéro composant non importé.
- **Dette backend** : zéro `TODO`/`FIXME`, zéro `System.out.println`, zéro `printStackTrace`.
- **Traces de pile** : non exposées (`application.yaml:69-72` et `:314-317`,
  `include-stacktrace: never`) ; le handler générique renvoie un message neutre.
- **Machine à états** : 14 transitions, aucune transition sortant de `ARCHIVE` (terminal), aucune
  sortie de `REJETE` autre que `RESUBMIT`. Aucune transition impossible ni orpheline détectée.
