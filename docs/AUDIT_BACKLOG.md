# AUDIT_BACKLOG — Backlog priorisé et plan d'exécution de la P6

> **Livrable de la Phase 5 (consolidation).** Produit une **décision**, pas du code : les 45 findings
> du registre `docs/AUDIT_MASTER.md` sont regroupés en **lots cohérents**, ordonnés par valeur/risque,
> et les 8 décisions de cadrage en attente ont été tranchées avec l'utilisateur le **2026-07-23**.
>
> Registre unique : `docs/AUDIT_MASTER.md` (source de vérité des findings et de leur statut).
> Méthodologie : `docs/superpowers/specs/2026-07-22-audit-exhaustif-systeme-design.md`.
> Modèle système : `docs/AUDIT_SYSTEM_MODEL.md` · Couverture : `docs/AUDIT_COVERAGE.md`.
>
> Branche : `audit/exhaustif-p5-backlog` (depuis `audit/exhaustif-p4-transverse` = `6c85fd9`).
> **Aucun code n'est modifié en P5.**

---

## 1. Décisions de cadrage — tranchées avec l'utilisateur (2026-07-23)

Ces huit décisions sont **métier**, pas techniques : elles ne pouvaient pas être prises
unilatéralement. Chacune est également consignée dans la ligne du finding concerné
(`docs/AUDIT_MASTER.md`, colonne **Statut**).

| # | Findings | Question | **Décision retenue** | Conséquence sur le backlog |
|---|---|---|---|---|
| **D1** | AUDIT-001 · AUDIT-031 | Le fournisseur doit-il pouvoir facturer contre un PO ? | **OUI** — sélecteur de PO au formulaire + persistance des `lineItems` | Les deux findings sont **CORRIGÉS en P6**, dans cet ordre imposé : **AUDIT-031 d'abord**, AUDIT-001 ensuite (lot **V1-B**). Nouvel endpoint `GET /api/v1/supplier/purchase-orders` scopé au fournisseur connecté. |
| **D2** | AUDIT-029 | Paiement intégral, ou règlements partiels cumulés ? | **Paiement intégral obligatoire** (`amountPaid == invoice.amount`, refus i18n sinon) | Lot **V1-C**. Pas de migration, pas de cumul : `existsByInvoiceId` reste en vigueur. Écarte le chantier « reste à payer ». |
| **D3** | AUDIT-030 | Séparer `PAYE` et `ARCHIVE`, ou retirer le filtre `PAYE` ? | **Séparer** — le paiement laisse la facture en `PAYE` ; l'archivage devient une action explicite | Lot **V1-C** (même fichier que D2 : `PaymentServiceImpl.finalizePayment`). Le drapeau `AUTO_ARCHIVE` cesse d'être émis par le paiement ; le filtre « Payé » de l'interface devient enfin utile. |
| **D4** | AUDIT-032 · AUDIT-033 | Mono-devise XAF, ou multidevise ? | **Mono-devise XAF stricte** — liste blanche `XAF` seule côté DTO, EUR/USD retirés du sélecteur | Lot **V1-D**. Le chantier multidevise est **écarté** (hors périmètre : ni taux, ni conversion, ni agrégation par devise ne sont demandés). Inclut le nettoyage des données de test `USD`/`XOF` injectées en P3. |
| **D5** | AUDIT-008 · AUDIT-009 · piste **O-3** | Jusqu'où va l'ADMIN « technique » ? | **Retirer les 3 surfaces à l'ADMIN** : seuils de matching → **DAF** ; disposition `PURGED` → **double validation** (ADMIN propose, DAF confirme) ; référentiel fournisseur → **AA** | Lot **V2-A**. Aligne le backend sur le frontend, qui réserve déjà `/admin/suppliers` à l'AA. La piste **O-3**, laissée ouverte en P1, est **close par cette décision** et devient un item du lot (pas un nouveau finding — règle 2 du registre). |
| **D6** | AUDIT-041 | Régénérer API.md depuis OpenAPI, ou le maintenir à la main ? | **Régénérer depuis OpenAPI** (springdoc 2.7.0 déjà présent au `pom.xml:223-225`) ; API.md est réduit à un guide d'usage | Lot **V4-B**. Priorité absolue au sein du lot : corriger les colonnes « Roles » qui accordent ADMIN sur 15 surfaces financières — **c'est la doc qui est fausse, pas le code**. |
| **D7** | AUDIT-003 · AUDIT-006 | Confirmer les révisions de sévérité issues des preuves runtime ? | **Confirmées** : AUDIT-003 **P1 → P2**, AUDIT-006 **P2 → P3** | Nouveau décompte : **10 P1 · 24 P2 · 11 P3**. Les deux findings restent à corriger, mais descendent en V3 et V4. |
| **D8** | AUDIT-013 | Quelle ampleur de rattrapage de la dette de tests ? | **Ciblé** : `AuthController`, `MatchingConfigController`, `SupplierController` **+ un test par finding corrigé** | Lot **V5-A** pour les 3 contrôleurs ; le « test par finding » est une **exigence de chaque lot**, pas un lot séparé (voir §4, critère de fin de vague). |

**Aucun finding n'est mis `HORS-SCOPE`.** Les 45 findings seront tous corrigés en P6 — le statut
`HORS-SCOPE`, qui aurait exigé une validation explicite tracée, n'est utilisé nulle part.

---

## 2. Décompte après révision des sévérités (D7)

| Sévérité | Avant P5 | **Après P5** | IDs |
|---|---|---|---|
| P0 (bloquant) | 0 | **0** | — |
| P1 (majeur) | 11 | **10** | AUDIT-001, 002, 018, 019, 026, 028, 029, 031, 032, 039 *(AUDIT-003 sort du groupe)* |
| P2 (mineur) | 24 | **24** | AUDIT-003 *(entrant)*, 004, 005, 007→013, 020→025, 030, 033→037, 040→043 |
| P3 (cosmétique / dette) | 10 | **11** | AUDIT-006 *(entrant)*, 014→017, 027, 038, 044, 045 |
| **Total** | **45** | **45** | — |

> Décompte contrôlé ligne à ligne : 10 + 24 + 11 = **45**. La synthèse de `AUDIT_MASTER.md` est
> alignée sur ces valeurs.

---

## 3. Les lots

Critères de regroupement : **même fichier**, **même cause racine**, ou **même compétence**
(un lot = un sous-agent, un commit, une revue). L'ordre des vagues suit la valeur/risque.

### Vague 1 — Contrôles financiers cassés (P1 fonctionnels)

> Ce que le système **promet et n'applique pas**. C'est ici que se joue la crédibilité en soutenance.

| Lot | Findings | Sujet | Fichiers principaux | Effort | Risque de régression | Dépendances | Tests à ajouter |
|---|---|---|---|---|---|---|---|
| **V1-A** | AUDIT-039 | Clé i18n `invoice.status.en_controle_aa` absente → **3 exports en 500** | `messages_fr.properties`, `messages_en.properties`, `InvoiceService.java:344`, `ReportServiceImpl.java:280,297,298,340` | **XS** | **Très faible** (ajout de clé + repli) | — | Test **paramétré** balayant les **10** valeurs de `InvoiceStatus` : chaque clé existe dans les 2 catalogues. Ce test aurait empêché le défaut. |
| **V1-B** | **AUDIT-031** *(bloque)* → **AUDIT-001** | Portail : `lineItems` jetés, puis absence de sélecteur de PO | `SupplierPortalController.java:260-281` (`toInvoice`) · nouvel endpoint `GET /supplier/purchase-orders` · `SupplierInvoiceSubmitPage.tsx` | **L** | **Moyen** (nouvelle surface d'API + formulaire) | **Ordre imposé** : AUDIT-031 **avant** AUDIT-001. Un sélecteur de PO sans persistance des lignes produirait des factures **systématiquement rejetées** à la soumission. | (1) « facture portail avec lignes → lignes persistées » ; (2) « `GET /supplier/purchase-orders` ne renvoie que les PO du fournisseur connecté » ; (3) parcours complet portail → PO → soumission → `MATCHED`. |
| **V1-C** | AUDIT-029 · AUDIT-030 | Paiement intégral obligatoire **+** séparation `PAYE` / `ARCHIVE` | `PaymentServiceImpl.java:74-111` (`recordPayment`) et `:117-138` (`finalizePayment`) · `StateMachineConfig.java:100-108` · filtre d'interface | **M** | **Moyen** (touche la machine à états — la suite backend couvre bien ce domaine) | Les deux findings sont dans **la même méthode** : à traiter en un seul lot, jamais séparément. | (1) « paiement ≠ montant dû → 400 i18n » ; (2) « paiement intégral → statut `PAYE`, **pas** `ARCHIVE` » ; (3) « action d'archivage explicite → `ARCHIVE` » ; (4) non-régression des 8 transitions du workflow. |
| **V1-D** | AUDIT-032 · AUDIT-033 | Validation métier absente à la création (montant ≤ 0, dates incohérentes, devise libre) | `InvoiceCreateRequest.java:14-26`, `InvoiceUpdateRequest` · `SupplierInvoiceSubmitPage.tsx` (sélecteur devise) | **S** | **Faible** (annotations de validation + liste déroulante) | AUDIT-033 est le volet interface d'AUDIT-032 : même lot. | (1) `@Positive` sur `amount` : 0 et −50 000 → 400 ; (2) `dueDate < issueDate` → 400 ; (3) devise ≠ `XAF` → 400 (dont `XOF` **et** `USD`) ; (4) nettoyage des données de test P3 vérifié. |
| **V1-E** | AUDIT-028 | `GET /goods-receipts` renvoie `List.of()` codé en dur → page GRN toujours vide | `GoodsReceiptController.java:51-61` · `GoodsReceiptsPage.tsx:60-64` | **S** | **Faible** (branche manquante à écrire, rien à modifier) | — | « liste sans filtre → 11 éléments » (le contrôleur fait partie des 14 non testés d'AUDIT-013). |

### Vague 2 — Séparation des devoirs (SoD) et cloisonnement

> Le critère d'audit le plus structurant. **ADMIN ≠ accès financier** : on **ferme**, on n'ouvre jamais.

| Lot | Findings | Sujet | Fichiers principaux | Effort | Risque de régression | Dépendances | Tests à ajouter |
|---|---|---|---|---|---|---|---|
| **V2-A** | AUDIT-018 · AUDIT-007 · **piste O-3** · AUDIT-008 · AUDIT-009 | Fermeture des surfaces ADMIN (décision **D5**) : documents, `/workflow/steps`, `/checklist`, seuils de matching, purge, référentiel fournisseur | `InvoiceDocumentController.java:65-69,76-89` · `ApprovalController` · `InvoiceChecklistController` · `MatchingConfigController.java:46` · `RetentionDispositionController.java:39` · `SupplierController.java:61,67,75,81,95,141,149,158,171,193` | **M** | **Moyen** (des `@PreAuthorize` sur 6 contrôleurs — un excès de fermeture casserait un parcours légitime) | AUDIT-018 **étend** AUDIT-007 : une seule correction pour les deux. La règle de non-régression la plus simple : **« si `GET /invoices/{id}` refuse, ses documents et son circuit refusent aussi »**. | (1) « ADMIN → 403 » sur `/documents`, `/workflow/steps`, `/checklist` ; (2) « validateur d'un autre département → 403 » sur les 3 mêmes ; (3) « ADMIN → 403 » sur l'écriture des seuils de matching ; (4) double validation `PURGED` ; (5) « ADMIN → 403 » sur `/suppliers`. |
| **V2-B** | AUDIT-002 · AUDIT-034 | Propriété du PO non vérifiée (fuite inter-fournisseurs) **+** notes d'écart divulgantes **+** rapprochement en échec jamais persisté | `InvoiceStateMachineServiceImpl.java:170-201` (`performMatchingCheck`) · `ThreeWayMatchingService.java:68-80,189+` (`generateDiscrepancyNotes`) | **M** | **Moyen** (transaction `REQUIRES_NEW` sur la persistance du résultat) | Même chemin d'exécution : la comparaison de propriété doit intervenir **avant** le rapprochement, et le message renvoyé doit devenir générique. | (1) « PO d'un autre fournisseur → refus, message ne divulguant aucune donnée » ; (2) « rapprochement en `MISMATCH` → résultat **persisté** malgré le rejet » (aujourd'hui la table reste à 0). |
| **V2-C** | AUDIT-026 · AUDIT-016 | Un fournisseur atteint 4 écrans staff : `ProtectedRoute` filtre par **liste noire** de préfixes | `ProtectedRoute.tsx:19-27,47-49` (`StaffRoute`, **code mort**) · `AppRoutes.tsx:87,88,89,100` | **S** | **Faible** (inverser la logique ; `StaffRoute` est **déjà écrit**) | `StaffRoute` (AUDIT-016, code mort) **est** précisément le correctif manquant d'AUDIT-026 : un seul lot. | Test frontend « compte `SUPPLIER` sur une route staff → redirection vers `/supplier/dashboard` », sur les 4 routes concernées. |
| **V2-D** | AUDIT-011 · AUDIT-012 | `/actuator/**` en `permitAll()` · secret MinIO par défaut dans le profil partagé | `SecurityConfig.java:66` · `application.yaml:105,127` | **XS** | **Faible — mais un point de vigilance** : `/actuator/health` **doit rester** en `permitAll`, le healthcheck du conteneur `oct_backend` en dépend. | Même fichier de configuration : un seul lot. | (1) « `/actuator/metrics` sans jeton → 401 » ; (2) « `/actuator/health` sans jeton → 200 » (non-régression du healthcheck). |
| **V2-E** | AUDIT-010 · AUDIT-037 | Paramètre `sort` sans liste blanche (3 sites, **atteignable par un compte SUPPLIER**) · `/pending-validation` en 500 (`LazyInitializationException`) | `InvoiceController.java:182-186` · `InvoiceService.java:247-252,353-355` · `DepartmentService.java:33-39` | **S** | **Faible** (une annotation `@Transactional` + une liste blanche) | Deux défauts « 500 sur un endpoint public », même compétence. **Note P5** : `/pending-validation` n'a **aucun appelant frontend** — le conserver et le corriger reste le choix le plus sûr (endpoint documenté et autorisé), sa suppression demanderait de vérifier les intégrations externes. | (1) « `?sort=inexistant` → 200 avec le tri par défaut » sur les 3 sites ; (2) « `/pending-validation` avec un rôle validateur → 200 » (les 11 rôles renvoient 500 aujourd'hui). |

### Vague 3 — Expérience utilisateur cassée (P1/P2 frontend)

| Lot | Findings | Sujet | Fichiers principaux | Effort | Risque de régression | Dépendances | Tests à ajouter |
|---|---|---|---|---|---|---|---|
| **V3-A** | AUDIT-019 | **Aucun responsive** : sidebar 256 px figée, contenu réduit à 134 px sur mobile — **50 pages** touchées | `Sidebar.tsx:93` · `SupplierLayout.tsx:45` (**même classe, même défaut**) · `AppShell.tsx:7-9` · `Header.tsx` (bouton menu à créer) | **L** | **Élevé** — le seul lot qui touche **toutes** les pages authentifiées | Les deux layouts portent la classe `w-64` identique : **à corriger ensemble**, sinon le portail fournisseur reste cassé. | Vérification **runtime Playwright** à 390 px sur un échantillon staff + portail (le responsive ne se teste pas en vitest). Contre-preuve utile : `/login` est déjà conforme. |
| **V3-B** | AUDIT-014 · AUDIT-035 · AUDIT-025 · AUDIT-022 | Gestion d'erreurs frontend : **84/95 mutations sans `onError`**, panne serveur → déconnexion silencieuse **et** message « Identifiants incorrects » trompeur, état vide présenté comme une panne, 400 systématique sur `/reports` | Intercepteur axios · `LoginPage.tsx` · `MatchingDetailPage.tsx:27,65` · `ReportsPage.tsx:55-56,132-142` · 20 fichiers sans aucune gestion | **L** | **Moyen** (l'intercepteur axios est un point de passage global) | **Ordre interne** : corriger d'abord l'**intercepteur** (distinguer `!error.response` d'un 401) et `LoginPage` — c'est le défaut le plus visible, il touche **tous** les utilisateurs dès la première seconde d'une panne. Les `onError` des 20 fichiers viennent ensuite. | (1) « échec réseau → bannière service indisponible, session **conservée** » ; (2) « 401 → déconnexion » ; (3) « `/matching/:id` en 404 → état vide, pas d'écran d'erreur » ; (4) « `/reports` ne déclenche `payment-cycle` qu'avec deux dates ». |
| **V3-C** | AUDIT-021 · AUDIT-042 · AUDIT-043 | Chaîne de décision de la langue : `<html lang="en">` figé · `lng:'fr'` court-circuite le détecteur (le choix EN ne survit pas à un F5) · **3 formats concurrents** de dates/montants | `index.html:2` · `i18n/index.ts:16` · `lib/format.ts:1-19` (constante `FR` forcée, **29 fichiers**) · 7 sites en régime B · `ReportsPage.tsx:479,513` · `AdminBackupsPage.tsx:91,172` | **M** | **Faible** (le helper est centralisé ; les régimes B/C/D migrent mécaniquement) | Les trois findings relèvent de **la même chaîne** : corriger `lng` sans corriger le helper laisserait une interface anglaise avec des montants français. | (1) « bascule EN → rechargement → l'interface reste en EN » ; (2) « `documentElement.lang` suit `i18n.language` » ; (3) tests unitaires du helper sur les deux locales. |
| **V3-D** | AUDIT-004 · AUDIT-005 · AUDIT-003 | Gardes de page et navigation : `MyDelegationsPage` sans garde · entrée « Escalades » **invisible pour le DAF** (garde `ROLE_DAF` morte, imbriquée dans le bloc ADMIN) · `/invoices/:id` sans `PageRoleGuard` (refus incohérent) | `MyDelegationsPage.tsx` · `Sidebar.tsx:206-208` (à sortir du bloc ouvert `:188`) · `InvoiceDetailPage.tsx` | **S** | **Faible** (le backend couvre déjà les trois cas — c'est de l'homogénéité d'interface) | AUDIT-003 est **P2 depuis D7** : aucune fuite de donnée, le backend renvoie bien 403. | (1) « DAF voit l'entrée Escalades dans la sidebar » ; (2) « ADMIN sur `/invoices/:id` → écran *Accès non autorisé*, pas *Une erreur est survenue* ». |

### Vague 4 — Accessibilité, cohérence, dette

| Lot | Findings | Sujet | Fichiers principaux | Effort | Risque de régression | Dépendances | Tests à ajouter |
|---|---|---|---|---|---|---|---|
| **V4-A** | AUDIT-024 · AUDIT-020 · AUDIT-023 | Accessibilité (WCAG 2.1 AA) : **25+ fichiers** de formulaires sans `htmlFor` (dont `/register/supplier`, page **publique**, 10 champs `critical`) · token `--ink-faint` sous le seuil dans les 2 thèmes (**362 usages**, 47/47 pages) · libellé tronqué + région défilable inatteignable au clavier | `index.css:92,157` (2 définitions **seulement** : correction centralisée) · `AdminUserFormPage.tsx:104` · `SupplierRegisterPage` · `AdminPermissionMatrixPage.tsx:130-131` · `Sidebar.tsx:93` | **L** *(mécanique)* | **Faible** (aucun changement fonctionnel) | Une seule famille, à traiter en un lot — **le meilleur candidat pour un sous-agent Sonnet**, relu par Opus. P4 a confirmé qu'AUDIT-020/021/023/024 couvrent **la totalité** des défauts a11y mesurables. | Rejouer **axe-core** (WCAG 2.1 AA) sur les 47 pages **+ les 4 pages publiques** : objectif **0 violation** `label`, `select-name`, `button-name`, `color-contrast`. |
| **V4-B** | AUDIT-041 · AUDIT-017 | Documentation en retard sur le code : API.md à 39 % avec **19 chemins fantômes** et **15 surfaces financières accordées à tort à ADMIN** · `CLAUDE.md §5` sans `EN_CONTROLE_AA`, `§9` « DAF ou ADMIN » faux | `docs/API.md` (régénéré via springdoc, décision **D6**) · `CLAUDE.md §5` et `§9` · Javadoc `InvoiceController.java:324` | **M** | **Nul** (documentaire) — **mais un piège** : « aligner le code sur la doc » en ouvrant l'accès à ADMIN serait une **régression SoD**. La doc est fausse, pas le code. | Même cause racine qu'AUDIT-039 (`EN_CONTROLE_AA` ajouté au code sans propager). À faire **après** V2-A, pour documenter les permissions **corrigées** et non les anciennes. | Test de cohérence : chaque chemin documenté existe dans les annotations `@*Mapping`. |
| **V4-C** | AUDIT-036 | Étape « Contrôle AA » figée à `PENDING` en base — **8 lignes**, aucune `APPROVED`, y compris sur des factures **archivées** | `ApprovalServiceImpl.java:73-84` (`assignAA`) — à aligner sur `validateN1` (`:99`) et `validateN2` (`:117`) | **S** | **Moyen** — migration de rattrapage sur `approval_steps`, table soumise aux **contraintes de rétention financière** (`V33`) : à vérifier **avant** toute mise à jour. | — | (1) « après `assign-aa`, l'étape est `APPROVED` avec `action_at` renseigné » ; (2) migration de rattrapage vérifiée sur les 8 lignes existantes. |
| **V4-C bis** | AUDIT-040 | N+1 **quadratique** : `findAll()` de l'historique **à l'intérieur** d'une boucle par facture (delta mesuré = 25 scans pour 25 factures) | `ReportServiceImpl.java:675` (dans la boucle ouverte `:674`) et `:638` | **S** | **Faible** (aucun changement de comportement attendu) | Indolore au volume de démonstration (114 ms), mais **quadratique** en production. P4 a confirmé que le défaut est **isolé à cette méthode**, pas systémique — les autres rapports sont sobres (1 à 7 scans). | (1) Non-régression sur la **valeur retournée** par le rapport (la sortie doit être identique) ; (2) mesure `seq_scan` **avant/après** : le delta doit tomber de 25 à ~1. |
| **V4-D** | AUDIT-015 · AUDIT-044 · AUDIT-027 · AUDIT-038 · AUDIT-045 · AUDIT-006 | Dette et finition : 11 sites affichant le message backend brut · `ErrorBoundary` avec 3 chaînes FR en dur · titre d'onglet **`frontend`** sur les 56 pages · fil d'Ariane fournisseur pointant vers `/dashboard` · `react-pdf` en import statique (**378 Ko / 110 Ko gzip**) · try/catch brut du portail | 11 fichiers (utilitaire partagé) · `ErrorBoundary.tsx:35,38,45` · `index.html:7` · `Header.tsx:77` · `InvoiceDetailPage.tsx:11` · `SupplierPortalController.java:220-240` | **M** | **Faible** | AUDIT-006 est **P3 depuis D7** (aucun 500 constaté en runtime : mise en conformité CLAUDE.md §3). AUDIT-027 partage `index.html` avec AUDIT-021 (V3-C) : vérifier l'absence de conflit. | (1) `lazy()` + `<Suspense>` sur la visionneuse → chunk `InvoiceDetailPage` sous 100 Ko ; (2) 3 clés i18n de l'`ErrorBoundary` présentes dans les 2 catalogues. |

### Vague 5 — Filet de sécurité (dette de tests)

| Lot | Findings | Sujet | Effort | Risque | Dépendances | Tests à ajouter |
|---|---|---|---|---|---|---|
| **V5-A** | AUDIT-013 | Tests d'intégration ciblés (décision **D8**) : `AuthController` (login, MFA/TOTP, **verrouillage après 5 échecs**, réinitialisation), `MatchingConfigController` (le contrôle financier de V2-A), `SupplierController` (coordonnées bancaires chiffrées) | **L** | **Nul** (ajout de tests uniquement) | À placer **en dernier** : les tests doivent couvrir le comportement **corrigé**, pas l'ancien. `MatchingConfigController` dépend de V2-A, `SupplierController` aussi. | La couverture des 11 autres contrôleurs non testés reste une dette assumée, à consigner dans `docs/TASKS.md §A`. |

---

## 4. Plan d'exécution de la Phase 6

### 4.1 Ordre des vagues et rationnel

| Vague | Lots | Findings | Pourquoi à ce rang |
|---|---|---|---|
| **V1** | V1-A → V1-E | 9 | Les **contrôles financiers que le système promet et n'applique pas**. V1-A d'abord car c'est un XS qui débloque 3 exports en 500 — meilleur rapport valeur/effort du backlog. |
| **V2** | V2-A → V2-E | 12 | La **SoD** : critère d'audit le plus structurant, et le plus scruté en soutenance. Après V1 car V2-B (propriété du PO) partage le chemin de rapprochement avec V1-B. |
| **V3** | V3-A → V3-D | 11 | Ce que l'utilisateur **voit**. V3-A (responsive) est le plus gros risque du backlog : il touche 50 pages, donc il ne doit pas cohabiter avec un autre lot frontend dans la même vague de revue. |
| **V4** | V4-A → V4-D (dont V4-C bis) | 13 | Accessibilité, cohérence documentaire, dette. Volume important mais **risque faible** : le meilleur terrain pour les sous-agents Sonnet. V4-B **après** V2-A, pour documenter les permissions corrigées. |
| **V5** | V5-A | 1 | Le **filet**, en dernier : les tests couvrent le comportement corrigé. |

### 4.2 Critère de fin par vague — non négociable

Une vague est terminée **si et seulement si** :

1. **Tous les findings du lot sont au statut `CORRIGÉ`** dans `docs/AUDIT_MASTER.md`, chacun avec la
   preuve de correction (test ajouté, ou mesure runtime pour les défauts visuels/fonctionnels).
2. **Backend `./mvnw.cmd test` : 0 échec, 0 erreur** — plancher **≥ 628 tests** (baseline P1), en
   hausse à chaque vague puisque chaque lot ajoute ses tests.
3. **Frontend `npm run test` : 0 échec** — plancher **≥ 237 tests**.
4. Les deux suites sont lancées **SÉPARÉMENT**. ⚠ Un lancement simultané produit de faux échecs
   (`Failed to start forks worker`) par contention CPU — vérifié en P2, P3 **et** P4. Ce n'est jamais
   un test cassé, mais on ne s'appuie jamais sur un résultat obtenu en parallèle.
5. **Aucune excuse « échec pré-existant »** : la baseline est verte des deux côtés (règle
   `no-failures-on-task-completion`).
6. **Revue Opus** du diff complet de la vague avant le commit, y compris pour le travail des
   sous-agents Sonnet.
7. **Commit par lot** (`fix(audit): V{n}-{X} — AUDIT-0NN, AUDIT-0MM — description`), une branche par
   vague (`fix/audit-p6-v{n}-{sujet}`), merge `--ff-only` dans `main` **avec le feu vert explicite**
   de l'utilisateur. Push tous les 10 commits.
8. **Prompt de reprise paste-ready** en fin de vague, pour la vague suivante.

### 4.3 Stratégie de test par nature de défaut

| Nature | Vérification | Pourquoi
|---|---|---|
| Règle métier backend (V1-C, V1-D, V2-B) | Test d'intégration : cas nominal **+ cas refusé** | La preuve d'un contrôle, c'est le **refus** qu'il produit. |
| SoD / permissions (V2-A, V2-C) | Test d'intégration « rôle interdit → 403 » sur **chaque** endpoint fermé | Une fermeture non testée se rouvre à la première refonte. |
| Visuel / responsive (V3-A) | **Playwright runtime** à 390 px, staff **et** portail | Un DOM correct peut masquer un GET cassé (règle `verify-runtime-not-snapshot`, PROB-038). |
| Accessibilité (V4-A) | **axe-core** rejoué sur 47 pages + 4 publiques, objectif 0 violation | Le seul critère a11y mesurable ; l'« à l'œil » ne prouve rien. |
| Performance (V4-D, AUDIT-040 en V4) | Mesure **avant/après** (compteurs `seq_scan`, taille de chunk) | ⚠ Écarter les faux positifs d'initialisation : le pic de 1,46 s sur `/payments/export` était le coût unique d'Apache POI (63 ms ensuite). |
| i18n (V1-A, V3-C, V4-D) | Test paramétré sur l'**enum** + parité des catalogues | La parité FR↔EN **ne détecte pas** une clé absente **des deux** côtés — c'est exactement ce qui a produit AUDIT-039. |
| Documentaire (V4-B) | Test de cohérence chemins documentés ↔ annotations `@*Mapping` | Sans test, API.md redérivera dans les trois mois. |

### 4.4 Garde-fous permanents de la P6

- **Le CODE fait foi contre la documentation.** API.md accorde ADMIN sur 15 surfaces financières :
  c'est la **doc** qui est fausse. L'« aligner » en ouvrant l'accès serait une régression SoD.
- **`ROLE_ADMIN` n'a pas d'accès financier** — critère d'audit, jamais un bug à « corriger » en
  ouvrant l'accès.
- **Devise = XAF** (Franc CFA BEAC). **Jamais XOF.**
- **Ne jamais lancer deux charges lourdes en parallèle** (Maven, vitest, Playwright).
- **Vérifier le code réel avant de conclure.** P3 a réfuté 2 findings par la mesure, P4 a écarté un
  faux positif de performance. Aucun finding n'est repris pour argent comptant sans relire sa preuve.
- **Ne pas ré-instruire ce qui est `CONFORME`.** Les 4 sections « Vérifié CONFORME » de
  `AUDIT_MASTER.md` (P1, P2, P3, P4) recensent ce qui est **sain** : machine à états, gardes de
  transition, injection SQL, cloisonnement du journal d'audit, parité i18n, `SupplierRoute`, contrôle
  des fichiers déposés, limitation de débit… Ces points ne doivent consommer **aucun** budget.
- **Une branche = un sujet**, `docs/QA_*.md` et `scratch/` appartiennent à l'utilisateur : ne pas les
  toucher.

### 4.5 Répartition des modèles

| Type de lot | Modèle | Lots concernés |
|---|---|---|
| Corrections mécaniques et répétitives | **Sonnet** (sous-agent), **relu par Opus** | V4-A (a11y, 25+ fichiers), V3-C (migration du helper de format), V4-D (dette) |
| Règles métier, machine à états, SoD | **Opus** directement | V1-B, V1-C, V2-A, V2-B, V4-C |
| Revue de fin de vague | **Opus**, systématique | toutes |

### 4.6 Condition de sortie de la P6

`docs/AUDIT_MASTER.md` ne contient **plus aucune ligne** au statut `OUVERT` ou `EN COURS` — les 45
findings sont `CORRIGÉ` (aucun `HORS-SCOPE`, décision de la P5). Les deux suites sont vertes, et une
passe de vérification runtime finale confirme le comportement sur les rôles clés : `admin`, `daf`,
`aa`, un validateur N1, un N2, `supplier`.
