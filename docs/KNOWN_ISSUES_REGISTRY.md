# KNOWN ISSUES REGISTRY — OCT Invoice System

> **Purpose :** Ce fichier est le registre vivant de TOUS les problèmes rencontrés sur ce projet,
> comment ils ont été résolus, et quelles règles préventives ont été ajoutées.
>
> **Règle obligatoire :** Tout agent ou développeur qui rencontre un nouveau bug ou problème
> DOIT l'ajouter ici AVANT de commiter le fix, en suivant le format ci-dessous.
> Ce fichier permet d'anticiper les mêmes erreurs dans les projets futurs.

---

## FORMAT D'ENTRÉE

```
### [PROB-NNN] Titre court du problème
- **Catégorie :** Backend | Frontend | Infrastructure | Sécurité | Architecture
- **Sévérité :** 🔴 Critique | 🟠 Important | 🟡 Mineur
- **Découvert :** {date} — {contexte de découverte}
- **Symptôme :** Ce qui était visible pour l'utilisateur ou le développeur
- **Cause racine :** L'explication technique précise du pourquoi
- **Solution appliquée :** Ce qui a été fait, avec les fichiers modifiés
- **Règle préventive :** La règle à suivre pour ne jamais reproduire ce problème
- **Fichiers modifiés :** Liste des fichiers changés
```

---

## PROBLÈMES RÉSOLUS — SPRINT 1 (Bugs Critiques)

### [PROB-001] Perte de session après rechargement de page
- **Catégorie :** Frontend
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit Playwright : toutes les routes protégées redirigaient vers /login après F5
- **Symptôme :** L'utilisateur connecté était déconnecté à chaque rechargement de page, même avec un token valide en localStorage
- **Cause racine :** Le token JWT était persisté en localStorage mais l'objet `user` du store Redux était `null` au démarrage (état initial). Le `ProtectedRoute` lisait `user` depuis Redux (null) et non le token depuis localStorage. Il n'y avait aucune rehydratation de l'objet user au démarrage de l'app.
- **Solution appliquée :** Création du composant `AuthRehydrator` dans `App.tsx` qui, au démarrage, lit le token depuis localStorage, appelle `GET /api/v1/profile`, dispatch `setCredentials` avec le user réel, et bloque le rendu jusqu'à la fin. L'objet user est aussi sérialisé en localStorage via `setCredentials` pour éviter les race conditions.
- **Règle préventive :** Toute app React avec JWT localStorage DOIT avoir un composant de rehydratation qui appelle le backend au démarrage. Ne jamais initialiser `user: null` dans le state Redux si un token existe. Toujours appeler `/profile` pour valider le token ET reconstruire le user.
- **Fichiers modifiés :** `frontend/src/App.tsx`, `frontend/src/store/slices/authSlice.ts`

---

### [PROB-002] Routes fournisseur inaccessibles (redirect vers /dashboard)
- **Catégorie :** Frontend
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Les pages /supplier/* redirigaient vers /dashboard pour les utilisateurs ROLE_SUPPLIER
- **Symptôme :** Le portail fournisseur était complètement inaccessible malgré un token valide
- **Cause racine :** Les routes `/supplier/*` étaient wrappées dans `<ProtectedRoute>` qui vérifie `isAuthenticated && !isSupplier`. Les fournisseurs étaient donc systématiquement rejetés par leur propre guard.
- **Solution appliquée :** Création de `<SupplierRoute>` dans `ProtectedRoute.tsx` (vérifie `isAuthenticated && isSupplier`) et remplacement de `<ProtectedRoute>` par `<SupplierRoute>` pour toutes les routes `/supplier/*` dans `AppRoutes.tsx`.
- **Règle préventive :** Séparer explicitement les guards de route par rôle. Ne jamais utiliser un guard générique pour des rôles aux permissions opposées. Tester chaque rôle distinct sur ses routes dédiées avant de déployer.
- **Fichiers modifiés :** `frontend/src/components/auth/ProtectedRoute.tsx`, `frontend/src/AppRoutes.tsx`

---

### [PROB-003] isActive toujours false pour tous les utilisateurs
- **Catégorie :** Backend
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Tous les comptes apparaissaient comme inactifs dans l'admin
- **Symptôme :** `isActive: false` retourné pour tous les utilisateurs dans l'API, tous les comptes semblaient désactivés
- **Cause racine :** Convention JavaBeans + Lombok : `boolean isActive` génère un getter `isIsActive()` (préfixe double). MapStruct cherche `isActive()` et ne trouve rien → mappe `false` par défaut. La colonne DB s'appelait `is_active` (correct), mais le getter Java était `isIsActive()`.
- **Solution appliquée :** Renommage du champ `boolean isActive` en `boolean active` sur l'entité `User`. Lombok génère alors `isActive()` (correct). `UserDTO` utilise `@JsonProperty("isActive")` pour maintenir la compatibilité JSON. JPQL corrigé (`u.isActive` → `u.active`). Migration `V34__fix_users_is_active.sql` appliquée.
- **Règle préventive :** Pour les champs booléens avec Lombok : nommer `boolean active` (pas `boolean isActive`). Lombok génère `isActive()` automatiquement. `boolean isXxx` génère `isIsXxx()` — toujours invalide. Appliquer cette règle à TOUTE entité avec un champ booléen préfixé par `is`.
- **Fichiers modifiés :** `User.java`, `UserDTO.java`, `UserMapper.java`, `UserRepository.java`, `AuthService.java`, `V34__fix_users_is_active.sql`, 8 fichiers de test

---

### [PROB-004] RoleGuard affichait "Accès non autorisé" dans la navbar
- **Catégorie :** Frontend
- **Sévérité :** 🟠 Important
- **Découvert :** 2026-06-06 — Des messages d'erreur apparaissaient dans la sidebar pour les items de menu non autorisés
- **Symptôme :** Au lieu de cacher silencieusement les liens de navigation non autorisés, un composant "Accès non autorisé" s'affichait dans la barre latérale
- **Cause racine :** Le `fallback` par défaut du `RoleGuard` était un composant visuel (`DefaultFallback`). Quand utilisé dans la navbar, ce fallback s'affichait à la place du lien, visible dans la sidebar.
- **Solution appliquée :** Split en deux composants : `RoleGuard` (fallback=null par défaut, pour la navigation) et `PageRoleGuard` (affiche DefaultFallback avec ShieldOff icon, pour les pages). Toute la sidebar utilise `RoleGuard` avec `fallback={null}`. Les pages protégées utilisent `PageRoleGuard`.
- **Règle préventive :** Toujours distinguer les guards de navigation (doivent être silencieux, `fallback=null`) des guards de page (peuvent afficher un message). Ne jamais partager le même composant guard pour les deux usages.
- **Fichiers modifiés :** `frontend/src/components/auth/RoleGuard.tsx`, `frontend/src/components/layout/Sidebar.tsx`, `ReportsPage.tsx`, `PurchaseOrdersPage.tsx`, `FinancialAuditPage.tsx`

---

## PROBLÈMES RÉSOLUS — SPRINT 2 (Fixes Fonctionnels)

### [PROB-005] Journal d'audit toujours vide (0 entrées)
- **Catégorie :** Frontend
- **Sévérité :** 🟠 Important
- **Découvert :** 2026-06-06 — La page AdminAuditPage affichait "Aucun résultat" malgré 100+ actions en DB
- **Symptôme :** Tableau d'audit vide, aucune entrée affichée
- **Cause racine :** L'endpoint appelé était `/audit-logs/system` qui filtre uniquement les événements de type `SYSTEM` — or le filtre `AuditLoggingFilter` enregistre des événements de type `HTTP_REQUEST`. L'endpoint correct est `/audit-logs` (sans filtre).
- **Solution appliquée :** Changement d'endpoint dans `AdminAuditPage.tsx` : `/audit-logs/system` → `/audit-logs`. Correction du champ date (`log.performedAt` → `log.createdAt ?? log.performedAt`) avec garde `isNaN`.
- **Règle préventive :** Toujours vérifier l'endpoint d'une page vide avec les DevTools Network avant de supposer que la DB est vide. Si une page affiche "no data", la première vérification est : est-ce que l'API renvoie vraiment 0 éléments ? Ajouter un log du nombre de résultats dans chaque page de liste.
- **Fichiers modifiés :** `frontend/src/pages/admin/AdminAuditPage.tsx`, `frontend/src/pages/FinancialAuditPage.tsx`

---

### [PROB-006] Messages d'erreur backend affichés comme clés i18n brutes
- **Catégorie :** Frontend
- **Sévérité :** 🟡 Mineur
- **Découvert :** 2026-06-06 — `error.invoice.no_document` affiché tel quel dans l'UI au lieu d'un message lisible
- **Symptôme :** Le texte `error.invoice.no_document` apparaissait dans le panneau d'action de la facture au lieu du message traduit
- **Cause racine :** Le backend retourne des clés i18n comme chaîne de message d'erreur. Le frontend affichait la chaîne brute sans tentative de traduction.
- **Solution appliquée :** Dans `InvoiceActionPanel.tsx` : `const translated = t(key); return translated !== key ? translated : key` — si la traduction existe, utiliser la traduction ; sinon afficher la clé telle quelle (meilleur que rien).
- **Règle préventive :** Toujours passer les messages d'erreur backend par `t()` avant affichage. Si le backend peut retourner des clés i18n, le frontend doit toujours tenter de les traduire. Documenter les clés d'erreur backend dans `fr.json` / `en.json`.
- **Fichiers modifiés :** `frontend/src/components/invoice/InvoiceActionPanel.tsx`

---

### [PROB-007] Erreurs WebSocket 401 polluant la console
- **Catégorie :** Frontend
- **Sévérité :** 🟡 Mineur
- **Découvert :** 2026-06-06 — Console browser saturée d'erreurs 401 WebSocket pour les utilisateurs non connectés
- **Symptôme :** Des erreurs `STOMP: Error: Request failed with status code 401` apparaissaient en boucle dans la console
- **Cause racine :** Le hook `useWebSocket` initialisait une connexion STOMP même avant que l'utilisateur soit authentifié, ou relançait des tentatives de connexion sans back-off.
- **Solution appliquée :** Ajout d'un handler silencieux `onStompError: () => {}` dans `useWebSocket.ts` pour éviter le log console. Vérification du token avant l'initialisation.
- **Règle préventive :** Toujours implémenter un handler d'erreur silencieux pour les connexions WebSocket/STOMP qui peuvent légitimement échouer (utilisateur non connecté, réseau temporairement coupé). Ne jamais laisser une erreur réseau se propager dans la console en production.
- **Fichiers modifiés :** `frontend/src/hooks/useWebSocket.ts`

---

### [PROB-008] Email utilisateur modifiable par l'utilisateur lui-même
- **Catégorie :** Sécurité
- **Sévérité :** 🟠 Important
- **Découvert :** 2026-06-06 — L'endpoint PUT /profile acceptait le champ email dans le body
- **Symptôme :** N'importe quel utilisateur pouvait changer son email d'authentification sans vérification
- **Cause racine :** Le `UserProfileController.updateProfile()` incluait le champ `email` dans les champs autorisés à la mise à jour sans vérification ni processus de confirmation par email.
- **Solution appliquée :** Suppression du bloc de mutation email dans `UserProfileController`. L'email ne peut désormais être changé que par un admin via l'interface d'administration.
- **Règle préventive :** Le changement d'email est une opération de sécurité, pas un simple PUT. Il doit toujours passer par : (1) envoi d'un lien de confirmation au nouvel email, (2) validation du token, (3) mise à jour. Ne jamais permettre à un utilisateur de changer son identifiant d'authentification en un seul appel API non confirmé.
- **Fichiers modifiés :** `backend/src/main/java/.../user/controller/UserProfileController.java`

---

### [PROB-009] Violation checksum Flyway après modification de migrations appliquées
- **Catégorie :** Infrastructure
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Le backend ne démarrait plus après modification de V32 et V34
- **Symptôme :** `FlywayException: Validate failed: Migration checksum mismatch for migration V32`
- **Cause racine :** Des migrations déjà appliquées (et donc inscrites dans `flyway_schema_history` avec leur checksum) avaient été modifiées. Flyway valide le checksum à chaque démarrage et refuse de continuer en cas de divergence.
- **Solution appliquée :** Revert des migrations V32 et V34 à leur contenu original exact. Les nouvelles fonctionnalités ont été déplacées dans de nouvelles migrations (V35+).
- **Règle préventive :** **JAMAIS modifier une migration Flyway déjà appliquée.** Une fois une migration exécutée en dev, son contenu est verrouillé. Toute modification doit créer une NOUVELLE migration avec le prochain numéro de version. Cette règle est absolue — même pour corriger une coquille.
- **Fichiers modifiés :** `V32__*.sql` (revert), `V34__*.sql` (revert)

---

### [PROB-010] Tests de compilation échouent après renommage isActive → active
- **Catégorie :** Backend
- **Sévérité :** 🟠 Important
- **Découvert :** 2026-06-06 — `mvnw test` échouait avec erreur de compilation sur les fichiers de test
- **Symptôme :** `error: cannot find symbol: method isActive(boolean)` dans 8 fichiers de test
- **Cause racine :** Un remplacement trop large (sed/regex) avait changé `.isActive(true)` en `.active(true)` dans les builders Webhook, Department et MatchingConfig — entités qui utilisent encore `Boolean isActive` (pas renommé). Seul `User` avait été renommé.
- **Solution appliquée :** Script Python ciblé pour revenir à `.isActive(true)` uniquement sur les builders non-User. Tests recompilés et passants.
- **Règle préventive :** Lors d'un renommage de champ : (1) identifier précisément TOUTES les entités affectées avant le remplacement, (2) utiliser un remplacement spécifique à la classe (`User.builder().active(true)`) et non un remplacement global (`.isActive(true)` → `.active(true)`), (3) compiler ET tester immédiatement après chaque renommage.
- **Fichiers modifiés :** 8 fichiers de test Spring Boot

---

## PROBLÈMES RÉSOLUS — SPRINT 4 (UI/UX)

### [PROB-011] Nginx sert l'ancienne version du frontend après deploy
- **Catégorie :** Infrastructure
- **Sévérité :** 🟠 Important
- **Découvert :** 2026-06-06 — Après `npm run build`, les modifications n'apparaissaient pas dans le navigateur
- **Symptôme :** Les nouvelles pages (ApprovalMatrix, Security, Integrations) affichaient 404 malgré un build réussi
- **Cause racine :** Docker container nginx servait les fichiers de l'image Docker originale, pas les nouveaux fichiers du build. `docker restart` ne suffit pas car les fichiers dans `/usr/share/nginx/html/` font partie du container filesystem, pas d'un volume monté.
- **Solution appliquée :** Procédure correcte : `npm run build` → `docker cp dist/. oct_frontend:/usr/share/nginx/html/` → `docker exec oct_frontend nginx -s reload`.
- **Règle préventive :** Le déploiement frontend dans Docker nginx sans rebuild d'image nécessite TOUJOURS : (1) build local, (2) `docker cp` des fichiers dans le container, (3) `nginx -s reload` (pas `docker restart`). Documenter cette procédure dans chaque projet. Ne jamais supposer qu'un `docker restart` met à jour les fichiers statiques.
- **Fichiers modifiés :** Procédure de déploiement documentée dans `ARCHITECTURE.md`

---

### [PROB-012] iText 8 — API Cell.setBorderColor() inexistante
- **Catégorie :** Backend
- **Sévérité :** 🟠 Important
- **Découvert :** 2026-06-06 — Compilation échouait lors de l'implémentation du PDF export
- **Symptôme :** `error: cannot find symbol: method setBorderColor(DeviceRgb)` sur `Cell`
- **Cause racine :** Dans iText 8, les bordures de cellule ne se configurent pas via `setBorderColor()` mais via `setBorder(new SolidBorder(color, width))`. L'API a changé entre iText 5/7 et iText 8.
- **Solution appliquée :** Remplacement de tous les `setBorderColor(color)` par `setBorder(new SolidBorder(color, 0.5f))`. Import de `com.itextpdf.layout.borders.SolidBorder`.
- **Règle préventive :** Lors de l'utilisation d'iText 8, toujours vérifier la version de l'API. iText 8 a cassé la compatibilité avec iText 5/7. Les borders se gèrent via `setBorder(Border)`, pas `setBorderColor(Color)`. Avant d'écrire du code iText, consulter la doc iText 8 spécifiquement (pas les tutoriels génériques qui ciblent iText 5).
- **Fichiers modifiés :** `InvoicePdfService.java`

---

### [PROB-013] BigDecimal.valueOf() ne prend pas BigDecimal en paramètre
- **Catégorie :** Backend
- **Sévérité :** 🟡 Mineur
- **Découvert :** 2026-06-06 — Erreur de compilation dans InvoicePdfService
- **Symptôme :** `error: no suitable method found for valueOf(BigDecimal)`
- **Cause racine :** `BigDecimal.valueOf()` n'accepte que `long` ou `double`, pas `BigDecimal`. Pour convertir un `BigDecimal` en `long`, il faut `.longValue()`.
- **Solution appliquée :** `BigDecimal.valueOf(item.getQuantity())` → `BigDecimal.valueOf(item.getQuantity().longValue())`
- **Règle préventive :** `BigDecimal.valueOf()` est une factory method pour créer un BigDecimal depuis un primitif. Ne pas l'utiliser pour caster un BigDecimal existant. Utiliser `.longValue()`, `.intValue()`, ou `.doubleValue()` pour convertir depuis un BigDecimal.
- **Fichiers modifiés :** `InvoicePdfService.java`

---

## PROBLÈMES EN COURS / NON RÉSOLUS

### [PROB-014] JWT utilise HS256 au lieu de RS256
- **Catégorie :** Sécurité
- **Sévérité :** 🔴 Critique (pour production)
- **Statut :** ⏳ Non résolu — acceptable pour projet académique
- **Description :** JJWT 0.12.6 est configuré avec un secret symétrique HMAC-SHA256. La bonne pratique est RSA-2048 (RS256) pour une signature asymétrique qui permet la vérification sans partager le secret.
- **Solution recommandée :** Générer une paire de clés RSA-2048 (`keytool` ou OpenSSL), configurer `application.yml` avec `jwt.private-key` et `jwt.public-key`, migrer `JwtService` vers `Jwts.builder().signWith(privateKey, SignatureAlgorithm.RS256)`.

---

### [PROB-015] Déploiement frontend Docker scrollbar invisible dans Playwright fullPage
- **Catégorie :** Frontend / Tests
- **Sévérité :** 🟡 Mineur
- **Statut :** ⏳ Contournement connu
- **Description :** `browser_take_screenshot(fullPage=true)` ne capture pas le contenu dans `overflow-y-auto` des containers imbriqués (AppShell `main`). Le screenshot tronque à la hauteur du viewport.
- **Contournement :** Utiliser `browser_evaluate(() => document.querySelector('main').scrollTo(0, scrollHeight))` puis screenshot normal du viewport. Ou agrandir la fenêtre Playwright à 1920×2160 avant le screenshot.

---

### [PROB-016] Approbation délégation — non implémentée
- **Catégorie :** Backend + Frontend
- **Sévérité :** 🟠 Important
- **Statut :** ❌ Non implémenté
- **Description :** Les requirements (Module 6) prévoient la délégation d'approbation pour les absences. Aucune entité, aucun service, aucun endpoint, aucune UI n'existe.
- **Solution recommandée :** Créer entité `ApprovalDelegation` (delegatorId, delegateeId, fromDate, toDate, departmentCode), service + endpoint `POST /approvals/delegate`, modifier `ApprovalService.findEligibleApprover()` pour inclure les délégataires actifs.

---

## PROBLÈMES RÉSOLUS — AUDIT 2026-06-07 (Sécurité)

### [PROB-017] User.isAccountNonLocked() ignorait lockedUntil
- **Catégorie :** Sécurité
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit complet
- **Symptôme :** Un compte verrouillé via `locked_until` pouvait encore s'authentifier si `active=true`, car Spring Security appelle `isAccountNonLocked()` qui ne vérifiait que le champ `active`.
- **Cause racine :** `User.isAccountNonLocked()` retournait `return active;` sans consulter `lockedUntil`. Le verrou temporel était seulement vérifié manuellement dans `AuthService.ensureAccountNotLocked()`, qui peut être contournée.
- **Solution appliquée :** `isAccountNonLocked()` retourne désormais `active && (lockedUntil == null || Instant.now().isAfter(lockedUntil))`.
- **Règle préventive :** La méthode `isAccountNonLocked()` de `UserDetails` est le seul vrai garde Spring Security. Toute logique de verrouillage doit y résider, pas seulement dans le service d'authentification.
- **Fichiers modifiés :** `User.java`

### [PROB-018] Invoice.supplierBankDetails stocké en clair (absence de @Convert)
- **Catégorie :** Sécurité
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit complet
- **Symptôme :** Les coordonnées bancaires du champ legacy `supplier_bank_details` sur la table `invoices` étaient stockées en texte clair en base de données.
- **Cause racine :** Le champ `Invoice.supplierBankDetails` n'avait pas l'annotation `@Convert(converter = EncryptionAttributeConverter.class)`, contrairement à `Supplier.bankDetails` qui était correctement chiffré.
- **Solution appliquée :** Ajout de `@Convert(converter = EncryptionAttributeConverter.class)` sur `Invoice.supplierBankDetails`. Migration V35 vide les données legacy en clair (rechiffrement impossible depuis SQL).
- **Règle préventive :** Tout champ contenant des données financières ou personnelles sensibles DOIT avoir `@Convert(EncryptionAttributeConverter)`. Créer un test de réflexion qui vérifie la présence de l'annotation sur tous les champs `*BankDetails`, `*Secret`, `*Password` dans les entités JPA.
- **Fichiers modifiés :** `Invoice.java`, `V35__encrypt_invoice_bank_details.sql`

### [PROB-019] AuditLoggingFilter existait mais n'était pas câblé dans Spring Security
- **Catégorie :** Architecture
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit complet
- **Symptôme :** La table `audit_logs` ne recevait aucune entrée des filtres HTTP. Seules les actions explicitement loggées par les services apparaissaient.
- **Cause racine :** `AuditLoggingFilter` était un `@Component` Spring mais n'était jamais ajouté à la chaîne de filtres `SecurityConfig`. Spring ne l'incluait donc pas automatiquement dans la chaîne de sécurité (contrairement aux filtres déclarés via `web.xml`).
- **Solution appliquée :** Injection de `AuditLoggingFilter` dans `SecurityConfig` et ajout via `.addFilterAfter(auditLoggingFilter, UsernamePasswordAuthenticationFilter.class)`. Note : `JwtAuthenticationFilter` ne peut pas être utilisé comme point d'ancrage dans `addFilterAfter` car il s'agit d'un filtre custom sans ordre enregistré — Spring Security exige un filtre standard (comme `UsernamePasswordAuthenticationFilter`) comme référence.
- **Règle préventive :** Tout filtre Spring Security DOIT être explicitement ajouté dans `SecurityConfig.securityFilterChain()`. L'annotation `@Component` seule ne l'inclut pas dans la chaîne de sécurité. Pour `addFilterAfter`/`addFilterBefore`, utiliser uniquement des filtres Spring Security standard ayant un ordre enregistré (ex: `UsernamePasswordAuthenticationFilter`). Vérifier à chaque ajout de filtre qu'il est câblé ET dans le bon ordre.
- **Fichiers modifiés :** `SecurityConfig.java`

### [PROB-020] Repositories injectés directement dans 7+ contrôleurs (violation layer rules)
- **Catégorie :** Architecture
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit complet
- **Symptôme :** Logique métier et queries dans les contrôleurs. Contournement des services. Transactions non gérées par @Transactional dans les contrôleurs.
- **Cause racine :** Pattern de "raccourci" : certains contrôleurs injectaient des repositories pour éviter d'ajouter une méthode de service. Pratique acceptable en prototype mais interdite en CLAUDE.md §3.
- **Solution appliquée :** Création de `SecurityHelper` pour la résolution de l'utilisateur courant. Déplacement de toute la logique dans les services. Utilisation de `InvoiceMapper` au lieu de `toDto()` manuelle.
- **Règle préventive :** Un contrôleur ne peut contenir que : (1) routing/binding, (2) un seul appel de service, (3) wrapping de la réponse. Si un contrôleur a besoin de plus d'un service ou d'un repository, la logique doit être dans un service.
- **Fichiers modifiés :** SecurityHelper.java (créé), InvoiceController.java, ApprovalController.java, UserProfileController.java, SupplierController.java, SupplierPortalController.java, MatchingConfigController.java, PurchaseOrderController.java, ReportController.java, InvoiceService.java, ApprovalService.java, ApprovalServiceImpl.java, ReportService.java, ReportServiceImpl.java, UserService.java, SupplierService.java, SupplierServiceImpl.java

---

## PROBLÈMES RÉSOLUS — AUDIT 2026-06-11/12 (Correction Cycle, Phase 11)

### [PROB-021] `/audit-logs/system` et `/audit-logs/financial` toujours vides — allow-lists d'actions ne correspondaient à aucune valeur réellement écrite
- **Catégorie :** Backend
- **Sévérité :** 🔴 Critique (REQ-17)
- **Découvert :** 2026-06-12 — Audit complet (Phase 6, Module 10)
- **Symptôme :** `GET /api/v1/audit-logs/system` (ADMIN) et `GET /api/v1/audit-logs/financial` (DAF) renvoient toujours 0 résultat, quel que soit le volume réel de la table `audit_logs`.
- **Cause racine :** Double désalignement. (1) `AuditController.SYSTEM_ACTIONS`/`FINANCIAL_ACTIONS` filtrent sur la colonne `action` avec des valeurs métier (`LOGIN`, `INVOICE_CREATE`, `APPROVE`...), mais (2) `AuditLoggingFilter` (seul écrivain pour le trafic HTTP normal) écrivait `"FINANCIAL_ACTION"`/`"SYSTEM_ACTION"`/`"HTTP_REQUEST"` dans la colonne **`entityType`** (pas `action`), et une chaîne libre `"METHOD URI -> STATUS"` (ex. `"POST /api/v1/invoices -> 201"`) dans `action`. Aucune valeur écrite par le filtre ne correspondait à aucune entrée des deux listes — les deux endpoints étaient structurellement morts pour tout le trafic HTTP. Cela explique aussi pourquoi PROB-005 (2026-06-06) avait dû contourner le problème en pointant les pages frontend vers `/audit-logs` (sans filtre) plutôt que de corriger le filtre lui-même — **ce contournement reste en place côté frontend** (les deux pages utilisent toujours `/audit-logs`), mais les endpoints filtrés sont maintenant fonctionnels pour un usage futur respectant le scoping par rôle (`/system` = ADMIN uniquement, `/financial` = DAF uniquement) — contrairement à `/audit-logs` qui renvoie les mêmes données aux deux rôles.
- **Solution appliquée :** `AuditLoggingFilter.classifyAction()` renvoie maintenant `"HTTP_REQUEST_FINANCIAL"` / `"HTTP_REQUEST_SYSTEM"` / `"HTTP_REQUEST"` dans le paramètre `action` (nouvelle méthode séparée de `classifyEntityType()` qui conserve `"FINANCIAL_ACTION"`/`"SYSTEM_ACTION"`/`"HTTP_REQUEST"` dans `entityType`). `AuditController.SYSTEM_ACTIONS`/`FINANCIAL_ACTIONS` incluent désormais ces deux nouvelles valeurs (+ `"PROFILE_UPDATE"`, déjà émis par `UserService.java:185` mais absent de `SYSTEM_ACTIONS`).
- **Règle préventive :** Quand un filtre HTTP générique et un contrôleur de recherche partagent un "vocabulaire" d'actions via une colonne DB, ce vocabulaire DOIT être vérifié par un test qui exerce les DEUX côtés (écriture par le filtre + lecture filtrée par le contrôleur/service), pas seulement chacun isolément. Un test qui mocke `AuditService` dans `AuditLoggingFilterTest` ET un test qui exerce `searchLogsWithActionFilter` avec les valeurs réellement produites par le filtre sont tous deux nécessaires.
- **Fichiers modifiés :** `AuditLoggingFilter.java`, `AuditController.java`, `AuditLoggingFilterTest.java` (créé), `AuditServiceTest.java`, `AuditControllerTest.java` (chemin `/api/audit-logs` → `/api/v1/audit-logs`, 3 tests préexistants en échec depuis BASELINE.md, corrigés au passage)

### [PROB-022] `GET /api/v1/supplier/profile` exposait l'entité JPA `Supplier` complète, incluant `bankDetails` chiffré
- **Catégorie :** Backend / Sécurité
- **Sévérité :** 🟠 Élevée (P2-02)
- **Découvert :** 2026-06-12 — Audit complet (Phase 2, Module 8)
- **Symptôme :** `SupplierPortalController.getProfile()` retournait `ApiResponse<Supplier>` (l'entité JPA brute) au lieu d'un DTO. Le champ `bankDetails` (déchiffré automatiquement par `EncryptionAttributeConverter` lors de la lecture JPA) apparaissait donc dans la réponse JSON de `GET /api/v1/supplier/profile` — violation de CLAUDE.md §3 ("never expose JPA entities directly") et exposition d'une donnée sensible déchiffrée à l'utilisateur final.
- **Cause racine :** `getProfile()` appelait `supplierService.findEntityById(supplierId)` (retourne `Supplier`) au lieu de `supplierService.getSupplier(supplierId)` (retourne `SupplierResponse`, déjà utilisé par `updateProfile()` et par `SupplierController` pour les mêmes données). Le mapping DTO existait déjà — seul `getProfile()` ne l'utilisait pas.
- **Solution appliquée :** `getProfile()` retourne maintenant `ApiResponse<SupplierResponse>` via `supplierService.getSupplier(supplierId)` (même DTO que `PUT /profile`, qui n'inclut pas `bankDetails` — `SupplierMapper.toResponse()` l'ignore déjà via `unmappedTargetPolicy = IGNORE`). Le frontend (`SupplierProfilePage.tsx`) traitait déjà `bankDetails` comme write-only ("Leave blank to keep the current bank details unchanged") — aucun changement frontend nécessaire, le comportement existant était anticipé pour ce DTO.
- **Règle préventive :** Toute méthode de contrôleur dont la signature de retour est une entité `@Entity` (`ApiResponse<EntityName>`) est un signal d'alerte — vérifier si un DTO + mapper équivalent existe déjà avant d'en créer un nouveau.
- **Fichiers modifiés :** `SupplierPortalController.java` (`getProfile()`), `SupplierPortalIntegrationTest.java` (ajout d'assertions `bankDetails`/`bank_details` absent du JSON dans `fullSupplierFlow_IntegrationTest`)

### [PROB-023] `AuditLoggingFilter.resolveUserId()` retournait toujours `null` — aucune entrée d'audit HTTP n'enregistrait l'utilisateur authentifié
- **Catégorie :** Backend
- **Sévérité :** 🟠 Élevée (REQ-18)
- **Découvert :** 2026-06-12 — Audit complet (Phase 6, Module 10)
- **Symptôme :** Toute entrée `audit_logs` créée par `AuditLoggingFilter` (trafic HTTP non-GET ou 401/403) avait `user_id = NULL`, même pour des requêtes authentifiées par JWT — impossible de savoir QUI a effectué une action depuis le journal d'audit HTTP.
- **Cause racine :** `resolveUserId()` retournait `null` de manière inconditionnelle (placeholder laissé par PROB-021/P11-01, commentaire "to avoid a DB call in the filter"). En réalité, `JwtAuthenticationFilter` (`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`) s'exécute AVANT `AuditLoggingFilter` (`addFilterAfter(..., UsernamePasswordAuthenticationFilter.class)`) et place dans `SecurityContextHolder` une `Authentication` dont le principal est l'entité `User` elle-même (`User implements UserDetails`, chargée par `CustomUserDetailsService.loadUserByUsername()`). L'UUID était donc déjà disponible en mémoire, sans appel DB supplémentaire — le commentaire d'origine était une fausse hypothèse.
- **Solution appliquée :** `resolveUserId()` lit `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`, et si c'est une instance de `User` (cas authentifié), retourne `user.getId()` ; sinon `null` (requêtes anonymes). Aucun appel DB ajouté.
- **Règle préventive :** Avant d'écrire "to avoid a DB call" comme justification pour ne pas résoudre une donnée, vérifier si la donnée est déjà présente dans le contexte de sécurité/principal — `User implements UserDetails` rend `getId()` gratuit dans tout filtre exécuté après `JwtAuthenticationFilter`.
- **Fichiers modifiés :** `AuditLoggingFilter.java` (`resolveUserId()`), `AuditLoggingFilterTest.java` (nouveau test `doFilter_OnAuthenticatedRequest_LogsActingUserId`, + `@AfterEach` pour nettoyer `SecurityContextHolder`)

---

### [PROB-024] Régression introduite par PROB-023 : `ApprovalControllerTest.cleanDb()` violait la FK `audit_logs.user_id → users.id`
- **Catégorie :** Backend / Tests
- **Sévérité :** 🟠 Élevée (régression P11-03)
- **Découvert :** 2026-06-12 — suite complète `mvnw test` exécutée après PROB-023, immédiatement avant le commit
- **Symptôme :** Après la correction de PROB-023, `AuditLoggingFilter` écrit désormais un `user_id` réel (non-null) dans `audit_logs` pour toute requête HTTP authentifiée non-GET (y compris celles exécutées pendant `@BeforeEach setUp()` des tests d'intégration). `ApprovalControllerTest.cleanDb()` appelle `userRepository.deleteAll()` (ligne 306) sans purger au préalable les lignes `audit_logs` qui référencent ces utilisateurs, provoquant une `DataIntegrityViolationException` (violation de contrainte FK `fkjs4iimve3y0xssbtve5ysyef0`). Résultat : 3 des 4 tests de `ApprovalControllerTest` passaient de "FAILURE" (échec d'assertion, déjà pré-existant) à "ERROR" (exception dans `setUp()`), portant le total de 27 à 28 échecs/erreurs.
- **Cause racine :** `audit_logs` est une table append-only (V25 : triggers PostgreSQL interdisant UPDATE/DELETE). La relation `AuditLog.user` (`@ManyToOne @JoinColumn(name = "user_id")`) ne précisait aucune action `ON DELETE`, donc Hibernate générait par défaut une contrainte FK `NO ACTION` — y compris dans le schéma `ddl-auto: create-drop` utilisé par les tests (Flyway n'est pas la source de vérité du schéma de test). Avant PROB-023, `user_id` était toujours `NULL`, donc la FK n'était jamais exercée par `cleanDb()` ; PROB-023 a rendu `user_id` non-null, exposant ce problème latent.
- **Solution appliquée :** Ajout de `@OnDelete(action = OnDeleteAction.SET_NULL)` (org.hibernate.annotations) sur `AuditLog.user`, afin que la suppression d'un utilisateur mette `audit_logs.user_id` à `NULL` au lieu d'échouer — cohérent avec le caractère append-only de la table (la ligne de log est conservée, seule la référence FK est anonymisée). Migration `V42__audit_logs_user_fk_on_delete_set_null.sql` ajoutée pour appliquer le même comportement en production via Flyway (`ALTER TABLE ... ON DELETE SET NULL`).
- **Règle préventive :** Toute relation `@ManyToOne` vers `users` (ou toute autre entité supprimable) depuis une table append-only/historique doit explicitement déclarer `@OnDelete(action = OnDeleteAction.SET_NULL)`, sans quoi un changement apparemment indépendant (ex. peupler une colonne FK auparavant toujours `NULL`) peut casser silencieusement les tests d'intégration qui font `deleteAll()` en `@BeforeEach`/`@AfterEach`. Lors de la correction d'un bug "valeur toujours NULL", toujours faire tourner la suite COMPLÈTE (`mvnw test`), pas seulement le test ciblé — un test ciblé ne détecte pas les effets de bord sur d'autres classes de test.
- **Fichiers modifiés :** `AuditLog.java` (`@OnDelete(action = OnDeleteAction.SET_NULL)` sur le champ `user`), `V42__audit_logs_user_fk_on_delete_set_null.sql` (nouveau)

---

## RÈGLE OBLIGATOIRE — MISE À JOUR DE CE FICHIER

> Tout agent ou développeur qui :
> - Résout un bug → ajoute l'entrée complète sous "PROBLÈMES RÉSOLUS"
> - Découvre un bug non résolu → ajoute l'entrée sous "PROBLÈMES EN COURS"
> - Identifie un pattern récurrent → ajoute une règle préventive
>
> Ce fichier doit être mis à jour AVANT le commit du fix, pas après.
> C'est la mémoire technique du projet — elle s'améliore à chaque incident.
