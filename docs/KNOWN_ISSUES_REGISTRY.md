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

---

## RÈGLE OBLIGATOIRE — MISE À JOUR DE CE FICHIER

> Tout agent ou développeur qui :
> - Résout un bug → ajoute l'entrée complète sous "PROBLÈMES RÉSOLUS"
> - Découvre un bug non résolu → ajoute l'entrée sous "PROBLÈMES EN COURS"
> - Identifie un pattern récurrent → ajoute une règle préventive
>
> Ce fichier doit être mis à jour AVANT le commit du fix, pas après.
> C'est la mémoire technique du projet — elle s'améliore à chaque incident.
