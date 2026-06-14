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

### [PROB-042] Ré-vérification SHA-256 au téléchargement — non implémentée (P11-49, REQ-14)
- **Catégorie :** Backend / Sécurité
- **Sévérité :** 🟡 Mineur
- **Statut :** ❌ Non implémenté (scope partiel P11-49 assumé)
- **Description :** Le SHA-256 d'un document est calculé et persisté au téléversement (`InvoiceDocumentService.computeSha256`), mais il n'est jamais recalculé/comparé au moment du téléchargement. Le texte de `ArchivePage` affirmait à tort « SHA-256 vérifié à chaque téléchargement » — corrigé en P11-49 pour ne décrire que le comportement réel (empreinte calculée + stockée au téléversement).
- **Solution recommandée :** Dans `InvoiceDocumentService.generateDownloadUrl()` (ou un proxy de téléchargement côté backend), re-télécharger l'objet depuis MinIO, recalculer le SHA-256 et le comparer à `checksumSha256` stocké ; logguer/bloquer en cas de divergence (corruption ou altération).

---

### [PROB-043] Politique de rétention 10 ans — non automatisée (P11-49, REQ-14)
- **Catégorie :** Backend
- **Sévérité :** 🟡 Mineur
- **Statut :** ❌ Non implémenté (scope partiel P11-49 assumé)
- **Description :** La « conservation 10 ans » est une politique documentaire, mais aucun job n'applique de cycle de vie (pas de purge/archivage froid/verrou légal automatisés). Le texte `ArchivePage` laissait entendre une application active — corrigé en P11-49 pour formuler un objectif de politique, pas une garantie technique.
- **Solution recommandée :** Tâche planifiée (`@Scheduled`) parcourant les documents au-delà de la période de rétention, avec règle de conservation/légal-hold configurable, et transition de stockage (ou suppression) tracée dans l'audit.

---

### [PROB-044] Versioning de documents — non implémenté (P11-50, REQ-16)
- **Catégorie :** Backend
- **Sévérité :** 🟡 Mineur
- **Statut :** ❌ Non implémenté (scope partiel P11-50 assumé)
- **Description :** P11-50 a livré la traçabilité d'accès (`document_access_log` append-only + hook dans `download()`), mais pas le versioning : re-téléverser un document écrase/ajoute sans historique de versions ni numéro de révision.
- **Solution recommandée :** Ajouter `version` + `superseded_by`/`previous_version_id` sur `invoice_documents` (ou une table `document_versions`), conserver chaque révision dans MinIO sous une clé distincte, et exposer l'historique des versions au détail de la facture.

---

### [PROB-045] Visionneuse de documents in-app — non implémentée (P11-50, REQ-16)
- **Catégorie :** Frontend
- **Sévérité :** 🟡 Mineur
- **Statut :** ❌ Non implémenté (scope partiel P11-50 assumé)
- **Description :** Les documents se téléchargent via une URL pré-signée (ouverture/nouvel onglet) ; il n'existe pas de visionneuse intégrée (aperçu PDF/image dans l'application) ni de prévisualisation inline.
- **Solution recommandée :** Composant visionneuse (ex. `react-pdf` pour les PDF, `<img>` pour les images) chargeant l'URL pré-signée dans une modale, avec pagination PDF et zoom.

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

### [PROB-025] `GET /api/v1/purchase-orders` (sans `supplierId`) retournait TOUTES les commandes, non paginées, alors que le frontend attendait déjà une réponse paginée
- **Catégorie :** Backend / Performance + Contrat API
- **Sévérité :** 🟡 Moyenne (P3-01)
- **Découvert :** 2026-06-12 — Audit Phase 3 (performance & données)
- **Symptôme :** `PurchaseOrderService.listAll()` appelait `purchaseOrderRepository.findAll()` (toutes les lignes, y compris soft-deleted) et `PurchaseOrderController.listPurchaseOrders` renvoyait `ApiResponse<List<PurchaseOrderDTO>>` brut. `frontend/src/pages/PurchaseOrdersPage.tsx` appelle pourtant déjà `GET /purchase-orders?page=&size=20` et lit `data.data.content`/`totalPages`/`totalElements` — un contrat déjà attendu côté frontend mais jamais implémenté côté backend, donc `data?.content` était `undefined` et la liste ne s'affichait jamais en pratique pour les volumes réels.
- **Cause racine :** Le endpoint a été implémenté avant que le frontend ne soit aligné sur un contrat paginé (`PagedResponse<T>`), et n'a jamais été mis à jour quand `PurchaseOrdersPage.tsx` a commencé à attendre ce contrat. De plus, `findAll()` n'excluait pas les enregistrements soft-deleted (`deletedAt IS NOT NULL`).
- **Solution appliquée :** Nouvelle méthode `PurchaseOrderRepository.findAllActive(Pageable)` (`WHERE po.deletedAt IS NULL`, paginée). `PurchaseOrderService.listAll(Pageable)` délègue à cette méthode. `PurchaseOrderController.listPurchaseOrders` accepte désormais `page`/`size` (défauts `0`/`20`) et renvoie `ApiResponse<PagedResponse<PurchaseOrderDTO>>` via `PagedResponse.of(Page<T>)`. La branche `supplierId != null` (filtre non paginé existant, `listBySupplier`) est conservée mais enveloppée dans un `PagedResponse` à une seule page, pour garder un contrat de réponse homogène quel que soit le chemin.
- **Règle préventive :** Quand le frontend attend déjà un contrat (`{content, totalElements, totalPages, last}`) pour un endpoint, vérifier que le backend le produit réellement — un mismatch silencieux de ce type ne casse aucun test backend existant (aucun test ne couvrait `GET /purchase-orders` sans `supplierId`) mais casse la fonctionnalité en usage réel. Tout nouveau endpoint de liste doit utiliser `Pageable`/`PagedResponse<T>` dès le départ, jamais `List<T>` brut.
- **Fichiers modifiés :** `PurchaseOrderRepository.java` (nouvelle méthode `findAllActive(Pageable)`), `PurchaseOrderService.java` (`listAll(Pageable)`), `PurchaseOrderController.java` (`listPurchaseOrders` paginé), `PurchaseOrderControllerTest.java` (nouveau fichier — 3 tests : liste paginée sans `supplierId`, liste avec `supplierId`, 403 pour rôle non autorisé)

---

### [PROB-026] `invoices.supplier_id` sans index malgré 4+ requêtes filtrant sur cette colonne
- **Catégorie :** Backend / Performance / Base de données
- **Sévérité :** 🟡 Moyenne (P3-02)
- **Découvert :** 2026-06-12 — Audit Phase 3 (performance & données)
- **Symptôme :** `V14__update_invoices_supplier_fk.sql` ajoute la colonne `invoices.supplier_id` (FK vers `suppliers`) mais sans index associé. `InvoiceRepository` filtre sur `i.supplier.id = :supplierId` dans `findAllWithFilters`, `countInvoicesByStatusForSupplier`, `countInvoicesByMatchingStatusForSupplier` et `findNextExpectedPaymentDateForSupplier` — toutes ces requêtes (notamment le tableau de bord du portail fournisseur, rendu accessible par P11-02/PROB-022) effectuent un scan complet de `invoices` sans index.
- **Cause racine :** Oubli lors de `V14` — un index a été ajouté pour `users.supplier_id` (`V15`) et `purchase_orders.supplier_id` (`V17`), mais pas pour `invoices.supplier_id`.
- **Solution appliquée :** Nouvelle migration Flyway `V43__add_invoices_supplier_id_index.sql` : `CREATE INDEX IF NOT EXISTS idx_invoices_supplier_id ON invoices(supplier_id)`. (Numérotée V43, et non V42, car V42 a été utilisé par PROB-024 pour la FK `audit_logs.user_id`.)
- **Règle préventive :** Toute migration qui ajoute une colonne `*_id` utilisée comme clé étrangère ou comme filtre de requête doit ajouter l'index correspondant dans la même migration (cf. `V15`, `V17`). Note : cette migration n'est pas exercée par `mvnw test` (le profil `test` utilise `ddl-auto: create-drop`, Flyway désactivé — cf. PROB-024) ; elle s'applique uniquement en production/staging via Flyway.
- **Fichiers modifiés :** `V43__add_invoices_supplier_id_index.sql` (nouveau)

---

### [PROB-027] `WebhookService` : `deliveryTimeoutSeconds` jamais utilisé, `RestTemplate` sans timeout, et retries bloquants via `Thread.sleep` (jusqu'à 755s par livraison)
- **Catégorie :** Backend / Performance / Fiabilité
- **Sévérité :** 🟠 Élevée (P3-04)
- **Découvert :** 2026-06-12 — Audit Phase 3 (performance & données)
- **Symptôme :** `WebhookService.deliveryTimeoutSeconds` (`@Value("${webhook.delivery.timeout.seconds:5}")`) n'était lu nulle part : `WebConfig.restTemplate()` retournait un `RestTemplate` par défaut (timeout infini). La chaîne de retry (`deliverWithRetry`, jusqu'à `MAX_RETRIES=3`) appelait `Thread.sleep(delayMs)` avec `RETRY_DELAYS_MS = {5000, 25000, 125000}` directement dans la méthode `@Async`/`@Transactional` `deliverWebhook` — au pire cas (timeout infini + 3 sleeps), un thread du pool `Async-*` ET potentiellement une connexion DB transactionnelle pouvaient être bloqués jusqu'à ~755s pour une seule livraison de webhook.
- **Cause racine :** `deliveryTimeoutSeconds` a été ajouté comme propriété de configuration mais jamais câblé dans un `ClientHttpRequestFactory` ; le retry a été implémenté avec `Thread.sleep` (le moyen le plus simple d'exprimer un backoff) sans tenir compte du fait que la méthode tourne dans le pool `@Async` à taille limitée (`AsyncConfig` : `corePoolSize=5`, `maxPoolSize=10`, `queueCapacity=25`) — un webhook lent/en échec pouvait donc épuiser le pool et retarder toutes les autres livraisons asynchrones (emails, notifications, audit).
- **Solution appliquée :** (1) `WebConfig` : nouveau bean `ClientHttpRequestFactory` (`SimpleClientHttpRequestFactory`) avec `connectTimeout`/`readTimeout` = `webhook.delivery.timeout.seconds` (×1000ms), injecté dans le `RestTemplate`. (2) `WebhookService` : injection de `TaskScheduler` (bean Spring Boot auto-configuré, `@EnableScheduling` déjà présent dans `AsyncConfig`). La logique de retry est scindée : `deliverWithRetry` effectue UNE tentative ; en cas d'échec/non-2xx, `scheduleRetry` appelle `taskScheduler.schedule(() -> deliverWithRetry(...), Instant.now().plus(delay))` au lieu de `Thread.sleep` — le thread `Async-*` est libéré immédiatement, le backoff 5s/25s/125s est conservé via le scheduler.
- **Règle préventive :** Dans une méthode `@Async`, ne jamais utiliser `Thread.sleep` pour différer un traitement — cela bloque un thread du pool borné pendant toute la durée du délai. Utiliser `TaskScheduler.schedule(Runnable, Instant)` (ou `ScheduledExecutorService`) pour planifier une exécution future sans bloquer. Tout appel HTTP sortant via `RestTemplate` doit avoir un `ClientHttpRequestFactory` avec timeouts explicites — le défaut Spring est un timeout infini.
- **Fichiers modifiés :** `WebConfig.java` (nouveau bean `ClientHttpRequestFactory`, `RestTemplate` utilise ce factory), `WebhookService.java` (injection `TaskScheduler`, `deliverWithRetry`/`scheduleRetry` non-bloquants), `WebhookServiceTest.java` (2 nouveaux tests : `testDeliverWebhook_OnFailure_SchedulesRetryWithoutBlocking` vérifie l'absence de blocage + délai ~5s planifié via `TaskScheduler`, `testDeliverWebhook_OnSuccess_DoesNotScheduleRetry` vérifie qu'aucun retry n'est planifié en cas de succès)

---

### [PROB-028] `AdminSessionController` injectait `ActiveSessionRepository` directement, bypassant la couche service (violation de la règle absolue P1-05)
- **Catégorie :** Backend / Architecture
- **Sévérité :** 🟠 Élevée (P1-05, règle absolue n°1 — "never bypass service layer from controller")
- **Découvert :** 2026-06-12 — Audit Phase 1 (architecture), sous-phase P11-D
- **Symptôme :** `AdminSessionController` (`GET /api/v1/admin/sessions`, `DELETE /api/v1/admin/sessions/user/{userId}`) injectait directement `ActiveSessionRepository` et construisait la réponse `listActiveSessions` en mappant manuellement chaque entité `ActiveSession` vers un `Map<String, Object>` brut dans le contrôleur — violant à la fois "never bypass service layer from controller" et "never expose JPA entities directly" (les entités `ActiveSession`/`User` étaient partiellement sérialisées via la map ad-hoc).
- **Cause racine :** Endpoint d'administration ajouté rapidement (gestion des sessions actives) sans suivre le pattern `Controller → Service → Repository` déjà en place pour les autres modules ; aucune couche service n'existait pour `ActiveSession`.
- **Solution appliquée :** Nouveau DTO `record ActiveSessionDTO(UUID id, UUID userId, String username, String ipAddress, Instant createdAt, Instant expiresAt)` dans `domain/user/dto/` — les noms de champs correspondent exactement à l'interface TypeScript `ActiveSession` déjà utilisée par `frontend/src/pages/admin/SecuritySettingsPage.tsx` (Jackson sérialise les accesseurs de `record` avec ces noms, donc aucun changement frontend requis). Nouveau service `AdminSessionService` (`domain/auth/service/`) avec `listActiveSessions()` (mappe `ActiveSessionRepository.findAllActive(Instant.now())` vers `List<ActiveSessionDTO>`) et `revokeUserSessions(UUID)` (délègue à `sessionRepository.revokeAllForUser`). `AdminSessionController` ne dépend plus que de `AdminSessionService`.
- **Règle préventive :** Tout nouveau contrôleur doit dépendre exclusivement d'un service (jamais d'un `*Repository`), et toute méthode de service exposée à un contrôleur doit retourner un DTO (`record` ou classe dédiée), jamais une entité JPA ni une `Map<String, Object>` ad-hoc. Avant de créer un endpoint d'administration "rapide", vérifier s'il existe déjà un service pour l'agrégat concerné ; sinon en créer un, même minimal.
- **Fichiers modifiés :** `ActiveSessionDTO.java` (nouveau), `AdminSessionService.java` (nouveau), `AdminSessionController.java` (refactorisé pour dépendre de `AdminSessionService`), `AdminSessionControllerTest.java` (nouveau — 4 tests : liste en ADMIN retourne 200 avec le bon shape `ActiveSessionDTO[]`, liste en non-ADMIN retourne 403, révocation en ADMIN retourne 200 et appelle `adminSessionService.revokeUserSessions`, révocation en non-ADMIN retourne 403)

---

### [PROB-029] `IntegrationStatusController` injectait `WebhookRepository` et `WebhookDeliveryRepository` directement, bypassant la couche service (violation de la règle absolue P1-05)
- **Catégorie :** Backend / Architecture
- **Sévérité :** 🟠 Élevée (P1-05, règle absolue n°1 — "never bypass service layer from controller")
- **Découvert :** 2026-06-13 — Audit Phase 1 (architecture), sous-phase P11-D
- **Symptôme :** `IntegrationStatusController` (`GET /api/v1/integrations/status`) injectait directement `WebhookRepository` et `WebhookDeliveryRepository`, et effectuait lui-même le mapping `Webhook` + dernière `WebhookDelivery` → `WebhookStatusResponse` (méthode privée `mapWebhookToStatus`) — violant "never bypass service layer from controller".
- **Cause racine :** Endpoint de supervision (santé des intégrations webhook) ajouté directement dans le contrôleur sans passer par `WebhookService`, qui existait déjà pour ce domaine et exposait déjà `getActiveWebhooks()`.
- **Solution appliquée :** Nouvelle méthode `WebhookService.getIntegrationStatus()` qui encapsule `webhookRepository.findByIsActiveTrue()`, le mapping vers `WebhookStatusResponse` et la consultation de `deliveryRepository.findLatestDeliveryByWebhook(webhook)` (logique déplacée telle quelle depuis le contrôleur, désormais privée au service). `IntegrationStatusController` ne dépend plus que de `WebhookService`.
- **Règle préventive :** Avant d'ajouter un nouvel endpoint dans un domaine qui possède déjà un service (`WebhookService` pour `domain/webhook`), vérifier si la logique peut être exprimée comme une nouvelle méthode de ce service plutôt que comme un accès direct aux repositories depuis un nouveau contrôleur.
- **Fichiers modifiés :** `WebhookService.java` (nouvelle méthode `getIntegrationStatus()` + `mapWebhookToStatus()` privée), `IntegrationStatusController.java` (refactorisé pour dépendre de `WebhookService`), `IntegrationStatusControllerTest.java` (nouveau — 2 tests : statut en ADMIN retourne 200 avec le bon shape, statut en non-ADMIN retourne 403), `WebhookServiceTest.java` (2 nouveaux tests : `testGetIntegrationStatus` vérifie le mapping incluant la dernière livraison, `testGetIntegrationStatus_NoDeliveries` vérifie l'absence de champs de livraison quand aucune livraison n'existe)

---

### [PROB-030] `WebhookController` injectait `WebhookRepository`, `WebhookDeliveryRepository` et `WebhookMapper` directement dans `listWebhooks()`, `deactivateWebhook()` et `getDeliveryLog()`, bypassant la couche service (violation de la règle absolue P1-05)
- **Catégorie :** Backend / Architecture
- **Sévérité :** 🟠 Élevée (P1-05, règle absolue n°1 — "never bypass service layer from controller")
- **Découvert :** 2026-06-13 — Audit Phase 1 (architecture), sous-phase P11-D
- **Symptôme :** `WebhookController` injectait directement `WebhookRepository`, `WebhookDeliveryRepository` et `WebhookMapper`. `listWebhooks()` appelait `webhookRepository.findByIsActiveTrue()` puis `webhookMapper.toResponseWithoutSecret`. `deactivateWebhook(UUID)` faisait un pré-contrôle `webhookRepository.findById(id)` pour lever un `ResourceNotFoundException` (404) avant de déléguer à `webhookService.deactivateWebhook(id)` (qui levait elle-même un `IllegalArgumentException` → 400, jamais atteint en pratique). `getDeliveryLog(UUID, Pageable)` faisait le même pré-contrôle puis appelait directement `deliveryRepository.findByWebhookOrderByCreatedAtDesc(webhook, pageable)` et construisait à la main la réponse `WebhookDeliveryResponse`/`PagedResponse`.
- **Cause racine :** Endpoints ajoutés progressivement (P9, webhooks) en réutilisant le pattern le plus rapide (accès direct aux repositories + mapping inline dans le contrôleur) au lieu d'étendre `WebhookService`, qui existait déjà pour ce domaine.
- **Solution appliquée :** Trois nouvelles méthodes sur `WebhookService` : `listActiveWebhooks()` (retourne `List<WebhookResponse>`, encapsule `webhookRepository.findByIsActiveTrue()` + `webhookMapper.toResponseWithoutSecret`) ; `getDeliveryLog(UUID webhookId, Pageable pageable)` (retourne `PagedResponse<WebhookDeliveryResponse>`, encapsule la recherche du webhook + `deliveryRepository.findByWebhookOrderByCreatedAtDesc` + mapping, via `PagedResponse.of(...)`) ; `deactivateWebhook(UUID)` modifiée pour lever `ResourceNotFoundException` (au lieu de `IllegalArgumentException`) quand le webhook n'existe pas, ce qui préserve le comportement HTTP 404 précédemment assuré par le pré-contrôle du contrôleur. `WebhookService` dépend désormais aussi de `WebhookMapper`. `WebhookController` ne dépend plus que de `WebhookService` ; ses 3 méthodes ne font plus qu'appeler le service et retourner le résultat.
- **Règle préventive :** Un pré-contrôle `repository.findById(id).orElseThrow(...)` dans un contrôleur, suivi d'un appel à une méthode de service qui refait la même recherche, est un signal de violation P1-05 — la levée de l'exception "not found" doit se faire dans le service, pas dans le contrôleur. Avant d'ajouter une méthode à un contrôleur, vérifier si le service du domaine peut l'exposer directement avec le DTO de sortie déjà construit.
- **Fichiers modifiés :** `WebhookService.java` (nouvelles méthodes `listActiveWebhooks()`, `getDeliveryLog(UUID, Pageable)` ; `deactivateWebhook` lève désormais `ResourceNotFoundException` ; nouvelle dépendance `WebhookMapper`), `WebhookController.java` (refactorisé pour dépendre uniquement de `WebhookService`), `WebhookControllerTest.java` (4 tests réécrits pour mocker `WebhookService` au lieu des repositories : `testListWebhooks`, `testDeactivateWebhook`, `testDeactivateWebhookNotFound`, `testGetDeliveryLog`), `WebhookServiceTest.java` (4 nouveaux tests : `testDeactivateWebhook_NotFound`, `testListActiveWebhooks`, `testGetDeliveryLog`, `testGetDeliveryLog_NotFound`)

---

### [PROB-031] `DelegationController` injectait `UserRepository` directement et résolvait les entités `User` (délégant/délégataire) dans `createDelegation()`, bypassant la couche service (violation de la règle absolue P1-05)
- **Catégorie :** Backend / Architecture
- **Sévérité :** 🟠 Élevée (P1-05, règle absolue n°1 — "never bypass service layer from controller")
- **Découvert :** 2026-06-13 — Audit Phase 1 (architecture), sous-phase P11-D
- **Symptôme :** `DelegationController` injectait directement `UserRepository`. Dans `createDelegation()`, il faisait `userRepository.findById(delegatorId).orElseThrow(...)` et `userRepository.findById(delegateeId).orElseThrow(...)` pour résoudre les deux entités `User` avant d'appeler `delegationService.createDelegation(delegator, delegatee, ...)` — la résolution des entités (accès repository) se faisait donc dans le contrôleur. De plus, `createDelegation()` et `listDelegations()` construisaient leurs réponses sous forme de `Map<String,Object>` inline plutôt qu'avec un DTO typé.
- **Cause racine :** Endpoint de délégation écrit en résolvant les utilisateurs au plus court depuis le contrôleur (accès direct `UserRepository`) au lieu de passer les identifiants au service et de laisser celui-ci résoudre les entités. Les réponses `Map` ad hoc venaient du même raccourci.
- **Solution appliquée :** Nouvelle surcharge `DelegationService.createDelegation(UUID delegatorId, UUID delegateeId, String departmentCode, LocalDate fromDate, LocalDate toDate, String reason, User createdBy)` qui résout les deux utilisateurs via `UserRepository` (nouvelle dépendance du service), lève `ResourceNotFoundException` ("Delegator not found" / "Delegatee not found", préservant le comportement HTTP 404 antérieur du contrôleur), puis délègue à la surcharge entité existante `createDelegation(User, User, ...)`. `DelegationController` ne dépend plus que de `DelegationService` + `SecurityHelper` et passe les UUID bruts. Nouveau record typé `DelegationDTO` (id, delegatorUsername, delegateeUsername, departmentCode, fromDate, toDate, reason, createdAt) remplaçant les `Map<String,Object>` inline — sur-ensemble des champs précédents, donc rétro-compatible.
- **Règle préventive :** Un contrôleur ne doit jamais injecter un `*Repository` pour résoudre des entités à partir d'identifiants reçus dans la requête — il passe les identifiants au service, qui résout les entités et lève l'exception "not found". Préférer un DTO/record typé en sortie plutôt qu'une `Map<String,Object>` construite à la main.
- **Fichiers modifiés :** `DelegationService.java` (nouvelle surcharge `createDelegation(UUID, UUID, ...)` + nouvelle dépendance `UserRepository`), `DelegationController.java` (refactorisé pour dépendre uniquement de `DelegationService`/`SecurityHelper`, passe les UUID, retourne des `DelegationDTO`), `DelegationDTO.java` (nouveau record), `DelegationServiceTest.java` (3 nouveaux tests : `createDelegationByIds_valid_resolvesUsersAndPersists`, `createDelegationByIds_delegatorNotFound_throwsResourceNotFound`, `createDelegationByIds_delegateeNotFound_throwsResourceNotFound`), `DelegationControllerTest.java` (nouveau — 6 tests : création en ADMIN retourne 201 avec le `DelegationDTO`, création en non-ADMIN retourne 403, utilisateur introuvable retourne 404, liste des délégations actives en ADMIN, révocation en ADMIN, révocation en non-ADMIN retourne 403)

---

### [PROB-032] `InvoiceDocumentController` injectait `UserRepository` directement pour résoudre l'identifiant de l'uploader (`getActorId`), bypassant la couche service (violation de la règle absolue P1-05)
- **Catégorie :** Backend / Architecture
- **Sévérité :** 🟠 Élevée (P1-05, règle absolue n°1 — "never bypass service layer from controller")
- **Découvert :** 2026-06-13 — Audit Phase 1 (architecture), sous-phase P11-D
- **Symptôme :** `InvoiceDocumentController` injectait directement `UserRepository`. Sa méthode privée `getActorId(Authentication)` faisait `userRepository.findByUsername(username).map(User::getId).orElseThrow(...)` pour transformer le nom d'utilisateur authentifié en `UUID`, avant de le passer à `invoiceDocumentService.upload(invoiceId, file, actorId)` — accès repository depuis le contrôleur.
- **Cause racine :** Le contrôleur résolvait lui-même l'uploader (nom → id) au lieu de transmettre le nom d'utilisateur au service, alors que `InvoiceDocumentService` injectait déjà `UserRepository` et résolvait déjà l'utilisateur par id à l'intérieur de `upload(...)`.
- **Solution appliquée :** Nouvelle surcharge `InvoiceDocumentService.upload(UUID invoiceId, MultipartFile file, String username)` qui résout l'uploader via `userRepository.findByUsername(username)` (lève `ResourceNotFoundException` si absent) puis délègue à la surcharge existante `upload(UUID, MultipartFile, UUID)`. `InvoiceDocumentController` n'injecte plus `UserRepository`, supprime `getActorId`, et passe `authentication.getName()` au service. Le contrôleur ne dépend plus que d'`InvoiceDocumentService`.
- **Règle préventive :** Un contrôleur ne doit pas transformer le `Authentication`/nom d'utilisateur en entité ou identifiant via un repository — il transmet le nom d'utilisateur (ou le `Authentication`) au service, qui résout l'utilisateur. Réutiliser la dépendance `UserRepository` déjà présente dans le service plutôt que d'en injecter une seconde dans le contrôleur.
- **Fichiers modifiés :** `InvoiceDocumentService.java` (nouvelle surcharge `upload(UUID, MultipartFile, String)`), `InvoiceDocumentController.java` (suppression de `UserRepository` et de `getActorId`, passe `authentication.getName()`), `InvoiceDocumentControllerTest.java` (suppression du `@MockBean UserRepository`, le mock du service utilise désormais `eq("assistant")` au lieu de `eq(user.getId())`), `InvoiceDocumentServiceTest.java` (2 nouveaux tests : `uploadByUsername_resolvesUserAndDelegates`, `uploadByUsername_unknownUser_throwsResourceNotFound`)

---

### [PROB-033] Défaut `MINIO_SECRET_KEY` du service `backend` (`dany`) incohérent avec le mot de passe du serveur MinIO (`dany1234`) — et tâche P11-13 marquée « faite » alors que le correctif n'était pas appliqué
- **Catégorie :** Infrastructure / Docker
- **Sévérité :** 🟠 Élevée (P5-02 — la stack ne s'authentifie pas à MinIO sur un clone neuf)
- **Découvert :** 2026-06-13 — Audit Phase 1, sous-phase P11-E (P11-13)
- **Symptôme :** Dans `docker-compose.yml`, le service `minio` (`MINIO_ROOT_PASSWORD`) et `minio_init` (`mc alias set`) avaient pour défaut `${MINIO_SECRET_KEY:-dany1234}`, mais le service `backend` avait `${MINIO_SECRET_KEY:-dany}`. Sur un clone neuf sans `MINIO_SECRET_KEY` défini dans `.env`, le backend tentait de s'authentifier à MinIO avec `dany` alors que le serveur attend `dany1234` → échec d'accès au stockage objet. Aggravant : `docs/TASKS.md` marquait déjà P11-13 `[x]` « Completed » en décrivant à tort le changement comme portant sur `minio_init`, alors que la ligne fautive (`backend`) n'avait jamais été modifiée.
- **Cause racine :** (1) Le défaut du backend n'avait pas été aligné lors d'un changement antérieur des défauts MinIO. (2) La case P11-13 avait été cochée sur la foi de la description plutôt que sur la vérification du fichier réel — exactement le piège que la règle d'audit « distrust everything, verify by execution, not by reading » vise à éviter. La résolution locale `docker compose config` affichait `dany1234` uniquement parce qu'un `.env` local fournissait la valeur, masquant le défaut.
- **Solution appliquée :** Défaut du service `backend` changé de `${MINIO_SECRET_KEY:-dany}` à `${MINIO_SECRET_KEY:-dany1234}`. Les trois emplacements (`minio`, `minio_init`, `backend`) ont désormais le même défaut. Vérifié par exécution avec `.env` neutralisé : `docker compose --env-file <vide> config` → `MINIO_SECRET_KEY: dany1234` et `MINIO_ROOT_PASSWORD: dany1234` résolvent à l'identique. Note de tâche P11-13 corrigée dans `docs/TASKS.md` pour décrire le vrai changement.
- **Règle préventive :** Ne jamais cocher une tâche sur la base de sa description — vérifier le fichier/comportement réel. Pour les valeurs partagées entre services compose (mots de passe, clés), définir le défaut une seule fois ou s'assurer que tous les `${VAR:-defaut}` portent le MÊME défaut. Vérifier les défauts avec `.env` neutralisé (`--env-file` vide), pas avec l'environnement local qui peut masquer l'incohérence.
- **Fichiers modifiés :** `docker-compose.yml` (défaut `MINIO_SECRET_KEY` du backend → `dany1234`), `docs/TASKS.md` (note P11-13 corrigée), `docs/ARCHITECTURE.md` (§9 « Docker Compose Services » : `postgres` retiré de la liste car host-native, `minio_init` ajouté — cohérence avec §4.3, P11-14)

---

### [PROB-034] `ApprovalController.getApprovalSteps` exposait un `List<Map<String,Object>>` non typé au lieu d'un DTO (violation de "never return raw maps / always use typed DTOs")
- **Catégorie :** Backend / Architecture
- **Sévérité :** 🟡 Mineure (P1-07 — qualité/type-safety, pas de bug fonctionnel)
- **Découvert :** 2026-06-13 — Audit Phase 1 (architecture), sous-phase P11-G
- **Symptôme :** `GET /api/v1/invoices/{invoiceId}/workflow/steps` retournait `ApiResponse<List<Map<String,Object>>>` ; `ApprovalServiceImpl.getApprovalSteps` construisait à la main une `LinkedHashMap` de 12 clés par étape. Pas de contrat typé : ni Swagger, ni le compilateur, ni le frontend ne pouvaient vérifier la forme de la réponse.
- **Cause racine :** Endpoint écrit au plus court (map inline) au lieu d'un record DTO, alors que la convention du projet impose "never returns raw entities — always maps to DTOs".
- **Solution appliquée :** Nouveau record `ApprovalStepResponse` (12 champs : id, stepOrder, stepName, stepNameFr, departmentCode, status, approverUsername, approverName, comments, rejectionReason, deadline, actionAt) — noms/ordre identiques aux anciennes clés de la map, donc forme JSON inchangée pour le frontend. `ApprovalService` (interface), `ApprovalServiceImpl` (mapping via stream, suppression de `Map`/`LinkedHashMap`/`ArrayList`) et `ApprovalController` retournent désormais la liste typée.
- **Règle préventive :** Ne jamais retourner `Map<String,Object>` (ni `List<Map<...>>`) d'un service/contrôleur — définir un record DTO. Si la map a des clés stables, c'est déjà un DTO qui s'ignore. Conserver l'ordre/les noms de champs lors de la conversion pour ne pas casser les consommateurs existants.
- **Fichiers modifiés :** `ApprovalStepResponse.java` (nouveau record), `ApprovalService.java` (signature), `ApprovalServiceImpl.java` (mapping stream), `ApprovalController.java` (type de retour), `ApprovalServiceTest.java` (2 nouveaux tests : `getApprovalSteps_mapsEntityFieldsToTypedDto`, `getApprovalSteps_nullApprover_yieldsNullUsernameAndName`)

---

### [PROB-035] Tuiles KPI du tableau de bord validateur affichant des chiffres GLOBAUX sous des libellés personnels (correction trompeuse) + clés i18n mortes de délégation
- **Catégorie :** Frontend / Backend / Qualité des données
- **Sévérité :** 🟡 Moyenne (REQ-04 — donnée trompeuse présentée à l'utilisateur)
- **Découvert :** 2026-06-13 — passe de revue (self-review) après P11-42
- **Symptôme :** Le premier correctif de P11-42 a remplacé les placeholders `—` des tuiles "Traitées ce mois"/"Approuvées" du dashboard validateur par des valeurs dérivées de `reportService.getKpis` — mais `getKpis` renvoie des agrégats **système (globaux)**, sans dimension par-validateur ni mensuelle. Résultat : des chiffres globaux affichés sous des tuiles d'apparence personnelle. C'est sans doute **pire** que le `—` d'origine (un `—` dit honnêtement « pas de donnée » ; un nombre global déguisé en métrique personnelle est activement faux). La sémantique « ce mois » avait aussi été abandonnée (relabel "Traitées") faute de donnée mensuelle.
- **Cause racine :** « Câbler à `getKpis` » a été pris au pied de la lettre alors que `getKpis` n'a ni scoping par utilisateur ni granularité mensuelle ; la tuile a été remplie avec la seule donnée disponible (globale) au lieu de créer la donnée réellement demandée.
- **Solution appliquée :** Nouvel endpoint **scopé validateur** : `ApprovalStepRepository.countByApproverIdAndStatus` + `countByApproverIdAndStatusInAndActionAtGreaterThanEqual` ; `ApprovalService.getValidatorStats(UUID approverId)` → `ValidatorStatsResponse(approvedTotal, processedThisMonth)` (approuvées par CET approbateur, toutes périodes ; traitées = APPROVED+REJECTED depuis le 1er du mois) ; exposé via `GET /api/v1/workflow/my-stats` (nouveau `ValidatorStatsController`, dépend uniquement du service — P1-05). Le dashboard interroge `/workflow/my-stats` (uniquement pour les validateurs ; `canViewKpis` ramené à AA/DAF) et restaure le libellé honnête "Traitées ce mois". **Nettoyage associé :** suppression de 9 clés i18n mortes — `approvals.delegation.*` (title/to/from/until/reason/save/active/none) + `approvals.delegate` — vestiges d'un design « délégation self-service » jamais construit (le backend est admin-only, cf. P11-44) — et de `dashboard.processed` (remplacée par `dashboard.processedThisMonth`).
- **Règle préventive :** Ne jamais remplir une tuile « métrique personnelle » avec une donnée globale faute de mieux — soit on crée la donnée scopée, soit on étiquette explicitement la portée, soit on laisse vide. Une « correction » qui rend une valeur trompeuse est pire que le placeholder honnête. Vérifier la portée réelle (par-utilisateur ? mensuelle ?) d'un endpoint avant de l'utiliser pour une tuile contextuelle.
- **Fichiers modifiés :** `ApprovalStepRepository.java` (2 méthodes de comptage), `ApprovalService.java`/`ApprovalServiceImpl.java` (`getValidatorStats`), `ValidatorStatsResponse.java` (nouveau record), `ValidatorStatsController.java` (nouveau), `DashboardPage.tsx` (query `/workflow/my-stats`, tuiles recâblées, `canViewKpis` corrigé), `en.json`/`fr.json` (`dashboard.processedThisMonth` ajoutée ; `dashboard.processed` + `approvals.delegation.*` + `approvals.delegate` supprimées), `ApprovalServiceTest.java` (+1 test), `ValidatorStatsControllerTest.java` (nouveau, 2 tests)

---

### [PROB-036] Formulaire de politique de sécurité « simulation only » rendu réel + leçon : un enforcement basé sur une config en base doit avoir un fallback sûr
- **Catégorie :** Backend / Sécurité
- **Sévérité :** 🟠 Moyenne (REQ-02 — formulaire factice ; + régression d'auth évitée de justesse)
- **Découvert :** 2026-06-13 — sous-phase P11-I / P11-40
- **Symptôme :** `SecuritySettingsPage` affichait un formulaire (MFA requise, timeout de session, max tentatives, longueur min de mot de passe) entièrement en `useState` local, non persisté (`handleSave` ne faisait que `setSaved(true)` + bannière « simulation only »). Aucun des réglages n'était réellement appliqué.
- **Cause racine :** La page avait été scaffoldée sans backend (pas d'entité/endpoint). Les valeurs réelles étaient codées en dur ailleurs : `AuthService.MAX_FAILED_LOGIN_ATTEMPTS = 5`, MFA par rôle en dur dans `MfaSetupEnforcementFilter`, `@Size(min = 8)` statiques pour les mots de passe.
- **Solution appliquée :** Nouvelle `SecurityPolicy` (entité singleton + migration `V44`, seedée avec les anciens défauts) + `SecurityPolicyService` + `SecurityPolicyController` (`GET`/`PUT /api/v1/admin/security-policy`, ADMIN). Enforcement réel : `maxLoginAttempts` lu depuis la policy ; `mfaRequired` respecté par le filtre MFA ; `minPasswordLength` validé programmatiquement aux 3 points de définition de mot de passe (création, reset, inscription fournisseur) ; `sessionTimeout` = durée du jeton d'accès à chaque nouvelle connexion (surcharge `JwtService.generateToken`), avec note UI honnête (les jetons déjà émis gardent leur TTL). Frontend recâblé (GET/PUT), bannière « simulation only » retirée.
- **Leçon / Régression évitée :** Le profil test désactive Flyway (`ddl-auto: create-drop`, pas de seed), donc la table `security_policy` était **vide** en test. La première version de `getActivePolicy()` levait `ResourceNotFoundException` (→ 404) quand aucune ligne active n'existait — ce qui a **cassé l'inscription fournisseur** (`SupplierPortalIntegrationTest` : register → 404, 2 régressions). **Règle préventive :** un point d'enforcement qui lit une configuration en base ne doit JAMAIS casser le flux critique (auth) si la config est absente — il doit retomber sur des défauts sûrs. `getActivePolicy()` retourne désormais des défauts (`orElseGet`) au lieu de lever ; `update()` ne désactive l'ancienne ligne que si elle existe. Vérifier ce fallback sur tout enforcement config-driven.
- **Fichiers modifiés :** `V44__create_security_policy.sql` (nouveau), `SecurityPolicy.java`, `SecurityPolicyRepository.java`, `SecurityPolicyDTO.java`, `SecurityPolicyUpdateRequest.java`, `SecurityPolicyService.java`, `SecurityPolicyController.java` (nouveaux), `AuthService.java` (maxLoginAttempts + sessionTimeout + validation mot de passe ×2), `MfaSetupEnforcementFilter.java` (mfaRequired), `UserService.java` (validation mot de passe), `JwtService.java` (surcharge expiration), `SecuritySettingsPage.tsx` (GET/PUT), `en.json`/`fr.json` (4 clés `admin.security.*`), `SecurityPolicyServiceTest.java` + `SecurityPolicyControllerTest.java` (nouveaux), `UserServiceTest.java` (mock ajouté)

---

### [PROB-037] Durcissement P11-40 (suite à self-review) : timeout de session réel, MFA-off effectif, fallback sans masquage, i18n 100%
- **Catégorie :** Backend / Frontend / Sécurité
- **Sévérité :** 🟠 Moyenne (corrections de comportement de sécurité suite à revue)
- **Découvert :** 2026-06-13 — passe de revue de P11-40 (à la demande de l'utilisateur)
- **Symptôme / décisions :** La première version de P11-40 (PROB-036) laissait 3 comportements discutables, corrigés ici :
  1. **Timeout de session non réel** : le timeout ne faisait que fixer la durée du jeton d'accès, mais le refresh token (7 j) prolongeait tout silencieusement → l'utilisateur n'était jamais déconnecté. **Corrigé** en vrai timeout d'**inactivité** : (serveur) `ActiveSession.expiresAt = now + timeout` au login ; au `/auth/refresh`, la session doit exister et ne pas être expirée/révoquée sinon `401 session.expired` ; sliding (l'expiry est repoussé à chaque refresh) ; (frontend) hook `useSessionTimeout` qui déconnecte précisément après `timeout` d'inactivité et rafraîchit proactivement (à mi-parcours) tant que l'utilisateur est actif, gardant la session serveur vivante. Le timeout est exposé dans `LoginResponse` (`session_timeout_minutes`).
  2. **« MFA requise » off ne désactivait pas la MFA existante** : seul le *forçage du setup* était désactivé. **Corrigé** : le login conditionne les DEUX blocs MFA (setup + OTP) sur `policy.mfaRequired` — off ⇒ aucun OTP demandé même pour les comptes MFA configurés. Réversible (secrets jamais effacés).
  3. **Fallback aux défauts masquant une config manquante** : `getActivePolicy()` retournait *silencieusement* des défauts. **Corrigé** sans masquage : un `@EventListener(ApplicationReadyEvent)` (`ensureDefaultPolicyExists`) seede une policy par défaut au démarrage si aucune n'existe (+ WARNING) — c'est le mécanisme principal (en prod la policy existe donc toujours). `getActivePolicy()` retombe sur les défauts **en loggant un WARNING** (jamais en silence) — filet de sécurité pour ne pas casser l'auth si la ligne est absente au moment d'une lecture (cas du profil test où `ddl-auto: create-drop` recrée le schéma entre contextes partagés et vide la table). `updated_by` rendu nullable (entité + migration `V45`) pour le seed système sans auteur. `update()` ne désactive l'ancienne ligne que si elle existe.
- **Règle préventive :** Un « timeout de session » doit réellement déconnecter (inactivité côté serveur + frontend), pas seulement raccourcir un jeton qu'un refresh prolonge. Un toggle de sécurité (MFA on/off) doit s'appliquer au comportement runtime (login), pas seulement au provisioning. Une config critique manquante se seede au démarrage avec un WARNING — jamais un fallback muet.
- **Tests / vérif :** `SecurityPolicyServiceTest` (auto-seed), `SecurityPolicyIntegrationTest` (PUT round-trip + versioning réels en base ; refresh refusé après expiration de session → 401). i18n : `SecuritySettingsPage` 100% bilingue (20 clés), parité 585/585. Frontend `tsc --noEmit` + `npm run build` OK.
- **Fichiers modifiés :** `SecurityPolicy.java` (updated_by nullable), `V45__security_policy_updated_by_nullable.sql` (nouveau), `SecurityPolicyService.java` (strict + ensureDefaultPolicyExists), `AuthService.java` (timeout d'inactivité au login + refresh, MFA conditionnée), `JwtService.java`, `LoginResponse.java` (session_timeout_minutes), `SecuritySettingsPage.tsx` (GET/PUT + i18n), `authSlice.ts`, `LoginPage.tsx`, `useSessionTimeout.ts` (nouveau hook), `App.tsx`, `apiClient.ts`, `en.json`/`fr.json`, `SecurityPolicyServiceTest.java`, `SecurityPolicyIntegrationTest.java` (nouveau)

---

### [PROB-038] `SecuritySettingsPage` préfixait ses appels d'API avec `/api/v1` alors qu'`apiClient` a déjà ce préfixe dans son `baseURL` → double préfixe `/api/v1/api/v1/...` → 404 sur GET/PUT policy et sessions (formulaire jamais hydraté, sauvegarde impossible)
- **Catégorie :** Frontend
- **Sévérité :** 🔴 Élevée (la fonctionnalité P11-40 était **entièrement non fonctionnelle à l'exécution** — masquée par les valeurs par défaut du `useState`)
- **Découvert :** 2026-06-13 — vérification UI runtime de P11-40 (« cliquer et observer », à la demande de l'utilisateur)
- **Symptôme :** À l'ouverture de `/admin/security`, le formulaire affichait des valeurs (60/5/8/MFA) qui **paraissaient** correctes mais provenaient des défauts du composant, pas du backend. La trace réseau montrait `GET http://localhost:8080/api/v1/api/v1/admin/security-policy => 404` (préfixe `/api/v1` dupliqué). Le PUT, le GET des sessions et le DELETE de révocation étaient cassés de la même manière.
- **Cause racine :** `apiClient` est configuré avec `baseURL = 'http://localhost:8080/api/v1'`. Tous les chemins passés à `apiClient` doivent donc être **relatifs** (`/admin/security-policy`). Les 4 appels de `SecuritySettingsPage` incluaient à tort le préfixe absolu `/api/v1/...`, produisant `…/api/v1/api/v1/…`. Toutes les autres pages (`/invoices`, `/matching-config`, etc.) utilisent correctement des chemins relatifs ; cette page était la seule incohérente.
- **Solution appliquée :** Retrait du préfixe `/api/v1` des 4 appels de `SecuritySettingsPage.tsx` (GET policy, PUT policy, GET sessions, DELETE session). Vérifié à l'exécution : GET `200` (hydrate le formulaire avec la vraie ligne en base), PUT `200` (timeout 60→15 persisté, nouvelle ligne active = versioning confirmé, valeur retenue après rechargement complet), tableau « Sessions actives » peuplé, bascule FR/EN 100% bilingue.
- **Règle préventive :** Quand un client HTTP a un `baseURL` contenant déjà un préfixe de chemin (`/api/v1`), **toujours** passer des chemins relatifs aux méthodes du client — jamais le préfixe absolu. Un 404 dont l'URL contient un segment dupliqué (`/api/v1/api/v1`) est la signature de ce bug. **Et surtout** : un formulaire qui affiche des valeurs par défaut côté composant peut masquer un GET cassé — la seule preuve qu'il fonctionne est l'observation de la requête réseau réelle (le snapshot DOM « avait l'air bon » ; seule la vérification runtime a révélé le 404). Vérifier toute nouvelle page câblée à une API en regardant la trace réseau, pas seulement le rendu.
- **Fichiers modifiés :** `SecuritySettingsPage.tsx` (4 chemins d'appel : suppression du préfixe `/api/v1`)

---

### [PROB-039] Remise au vert des 27 échecs de référence (baseline pré-existant) — 5 causes racines distinctes, toutes dans le harnais de test sauf une lacune fonctionnelle réelle
- **Catégorie :** Tests / Sécurité / Workflow
- **Sévérité :** 🟠 Moyenne (dette de test masquant la santé réelle de la suite ; bloquait la garde de régression)
- **Découvert :** 2026-06-13 — investigation des 27 échecs (25 failures + 2 errors sur 303 tests), à la demande de l'utilisateur
- **Méthode :** Confirmé par exécution + `git` que les 27 sont **antérieurs à P11-40** (aucun de ces tests touché depuis le commit pré-P11-40 ; la seule modif du filtre MFA *assouplissait* le comportement). Cinq groupes de causes racines :
  1. **Filtre MFA bloque les comptes de test privilégiés (9 échecs : Approval ×4, Notification ×3, Payment ×3 → 400 `mfa_setup_required`).** Les helpers `createUser` des tests créaient des utilisateurs à rôle obligatoire-MFA (`ASSISTANT_COMPTABLE`, `DAF`, `VALIDATEUR_*`) sans `mfaVerified`. **De plus**, `enforce-secret-check: false` n'était défini que sous le profil **dev** dans `application.yaml`, pas en **test** (où le défaut `true` exigeait un secret TOTP réel). **Corrigé :** `mfaEnabled/mfaVerified=true` posé dans les helpers (Approval/Notification/Payment) + `app.security.mfa.enforce-secret-check: false` ajouté à `application-test.yml`. `MfaIntegrationTest` reste vert (ses users ont `mfaVerified=false` → toujours bloqués, ce qui est l'assertion testée).
  2. **RBAC reports : tests obsolètes (10 échecs : ReportControllerTest).** Les tests attendaient `ADMIN` autorisé, un rôle `AUDITEUR` (supprimé du système via `V31`), et `ASSISTANT_COMPTABLE` refusé. Le contrat réel (et la **règle de séparation des pouvoirs confirmée par l'utilisateur : l'ADMIN n'accède PAS aux données financières**) est **DAF + ASSISTANT_COMPTABLE uniquement**. Le `ReportController` était déjà correct ; **les tests ont été réécrits** (ADMIN → 403, ASSISTANT → 200, cas AUDITEUR supprimés).
  3. **RBAC liste factures : tests listent en `ADMIN` (3 échecs : Invoice ×1, InvoicePerformance ×2 → 403).** `GET /invoices` exclut l'ADMIN par design (`!hasRole('ADMIN')` — même règle de séparation des pouvoirs). Tests corrigés (rôle `DAF` pour le happy-path ; ajout d'un test explicite ADMIN → 403).
  4. **StateMachine `advanceTo` (2 errors : t5, t10).** Le helper utilisait le mauvais approbateur (`n2Info`) pour les étapes N1 d'un dept two-level, faisant échouer `ASSIGN_REVIEWER` depuis SOUMIS. Helper corrigé pour toujours utiliser `n1Info` aux étapes N1.
  5. **Mocks/attentes obsolètes (3 échecs : ApprovalServiceTest NPE, UserServiceTest, p3_19).** `ApprovalServiceTest` ne mockait pas `ApprovalDelegationRepository` (ajouté lors de la délégation d'approbation) → NPE ; ajout du `@Mock`. `UserServiceTest.createUser_Success` attendait `save()` ×1 alors que le service save ×2 (UUID puis rôles) ; attente passée à ×2. `p3_19` attendait 400 là où l'autorisation 403 précède correctement la `WorkflowException`.
- **Lacune fonctionnelle réelle révélée (pas qu'un test) :** `p3_18_rejectAndResubmit` appelait `POST /api/v1/invoices/{id}/resubmit` qui **n'existait pas** côté staff (seul le portail fournisseur exposait `RESUBMIT`). Or `WORKFLOW.md` prévoit « REJETE → SOUMIS for correction » par l'assistant. **Endpoint ajouté** : `POST /api/v1/invoices/{id}/resubmit` (`@PreAuthorize hasRole('ASSISTANT_COMPTABLE')`, envoie l'event `RESUBMIT`). Les clés i18n `action.resubmit.success` (FR/EN) existaient déjà.
- **Règle préventive :** (a) Tout helper de test créant un utilisateur à rôle privilégié doit poser `mfaVerified=true` ET le profil test doit avoir `enforce-secret-check=false` — sinon le `MfaSetupEnforcementFilter` renvoie 400 silencieusement. (b) Une config qui ne vaut que pour un profil (`on-profile: dev`) ne s'applique PAS aux autres ; vérifier la duplication nécessaire en test. (c) Les attentes RBAC d'un test sont un *contrat* : si elles divergent du `@PreAuthorize`, trancher via `docs/API.md` **et** les règles métier (ici : ADMIN sans accès financier). (d) Un test rouge peut révéler une vraie lacune fonctionnelle (resubmit staff), pas seulement un test obsolète — distinguer avant de « corriger le test ».
- **Fichiers modifiés :** `application-test.yml` (enforce-secret-check), `InvoiceController.java` (endpoint resubmit), `ApprovalControllerTest.java` (MFA helper + p3_19 + suppression méthode morte), `NotificationControllerTest.java`, `PaymentControllerTest.java` (MFA), `ReportControllerTest.java` (réécriture RBAC), `InvoiceControllerTest.java` (rôle liste + test ADMIN 403), `InvoicePerformanceTest.java` (rôle DAF), `StateMachineTransitionExhaustiveTest.java` (helper advanceTo), `ApprovalServiceTest.java` (mock delegationRepository), `UserServiceTest.java` (save ×2). Résultat : **306 tests, 0 échec, 0 erreur**.

---

### [PROB-040] `UserService.assignRoles` créait un `UserRole` sans poser son `@EmbeddedId` (`UserRoleId`) → `PUT /users/{id}/roles` renvoyait 500 (`JpaSystemException` / NPE au flush) — endpoint entièrement cassé, jamais détecté car le seul test mockait le service
- **Catégorie :** Backend / Persistence
- **Sévérité :** 🔴 Élevée (l'assignation de rôles via `PUT /users/{id}/roles` était totalement non fonctionnelle en runtime)
- **Découvert :** 2026-06-14 — vérification runtime de P11-18 (matrice de permissions), premier vrai consommateur de l'endpoint
- **Symptôme :** Cocher un rôle dans la matrice puis Enregistrer → `PUT /users/{id}/roles` → **500** `{"message":"An unexpected error occurred"}`. Log backend : `JpaSystemException: Could not set value of type [java.util.UUID]: 'UserRoleId.roleId' (setter)` causé par `NullPointerException: Cannot invoke "Object.getClass()" because "o" is null`.
- **Cause racine :** `assignRoles` construisait `new UserRole()` puis posait seulement `setUser`/`setRole`, **sans jamais poser l'`@EmbeddedId` composite `UserRoleId{userId, roleId}`**. À la sauvegarde, Hibernate tentait de dériver l'id composite et échouait (id null). Tous les autres points d'écriture (`createUser`, helpers de test) posaient explicitement `new UserRoleId(userId, roleId)` ; `assignRoles` était le seul à l'omettre. Non détecté parce que `UserControllerTest` **mocke `UserService`** (`@MockBean` + `doNothing()`), donc le flush JPA réel n'était jamais exercé.
- **Solution appliquée :** Ajout de `userRole.setId(new UserRoleId(user.getId(), role.getId()))` dans `assignRoles` (même pattern que `createUser`). Vérifié en runtime : `PUT /users/{id}/roles` → **200**, persistance confirmée après rechargement (rôle ajouté + rôles existants préservés). Nouveau test d'intégration `UserServiceIntegrationTest` (`assignRoles_persistsRolesWithCompositeKey`, `assignRoles_replacesExistingRoles`) qui exerce le **vrai** service contre H2 (aurait attrapé le NPE).
- **Règle préventive :** Pour une entité de jointure à clé composite (`@EmbeddedId`), **toujours** instancier et poser l'`@EmbeddedId` avant de persister — ne jamais compter sur une dérivation implicite via `setUser`/`setRole`. Et : un endpoint dont le test **mocke entièrement la couche service** n'est PAS couvert au niveau persistence ; tout endpoint qui écrit en base a besoin d'au moins un test d'intégration exécutant le vrai service (la vérification runtime « cliquer et observer » l'a révélé ; cf. [[verify-runtime-not-snapshot]]).
- **Fichiers modifiés :** `UserService.java` (`assignRoles` pose le `UserRoleId`), `UserServiceIntegrationTest.java` (nouveau, 2 tests de non-régression).

---

### [PROB-041] Export CSV des utilisateurs : `LazyInitializationException` en parcourant `User.userRoles` hors session Hibernate
- **Catégorie :** Backend / Persistence
- **Sévérité :** 🟠 Moyenne (l'export CSV — P11-16 — renvoyait 500)
- **Découvert :** 2026-06-14 — vérification runtime de P11-16 (premier appel réel de l'export)
- **Symptôme :** `GET /api/v1/users/export/csv` → 500 `LazyInitializationException: failed to lazily initialize a collection of role: User.userRoles - no Session` à `UserCsvService.exportUsersToCsv`.
- **Cause racine :** `exportUsersToCsv` parcourait `u.getUserRoles()` (collection LAZY) **sans transaction** ; la session JPA était déjà fermée au moment de l'itération. Le test unitaire ne l'avait pas attrapé car la classe de test est `@Transactional` (session ouverte pendant tout le test) — un faux négatif classique.
- **Solution appliquée :** Ajout de `@Transactional(readOnly = true)` sur `exportUsersToCsv` pour garder la session ouverte pendant la sérialisation. (L'import reste **non** transactionnel au niveau méthode : chaque ligne crée son user via `UserService.createUser` qui est déjà `@Transactional`, garantissant l'isolation ligne par ligne — une ligne en échec ne rollback pas les valides.)
- **Règle préventive :** Toute méthode service qui sérialise des entités en parcourant des associations LAZY (export, mapping manuel) doit être `@Transactional(readOnly = true)`. Attention : un test `@Transactional` masque ce bug — la vérification runtime (hors session) est la vraie preuve (cf. [[verify-runtime-not-snapshot]]).
- **Fichiers modifiés :** `UserCsvService.java` (`@Transactional(readOnly=true)` sur l'export).

---

## RÈGLE OBLIGATOIRE — MISE À JOUR DE CE FICHIER

> Tout agent ou développeur qui :
> - Résout un bug → ajoute l'entrée complète sous "PROBLÈMES RÉSOLUS"
> - Découvre un bug non résolu → ajoute l'entrée sous "PROBLÈMES EN COURS"
> - Identifie un pattern récurrent → ajoute une règle préventive
>
> Ce fichier doit être mis à jour AVANT le commit du fix, pas après.
> C'est la mémoire technique du projet — elle s'améliore à chaque incident.
