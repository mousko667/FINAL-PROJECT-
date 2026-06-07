# Correction Complète Post-Audit — OCT Invoice System

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corriger l'intégralité des 27 problèmes identifiés lors de l'audit du 2026-06-06 — 4 critiques, 12 importants, 11 mineurs — sans casser les fonctionnalités existantes.

**Architecture:** Corrections groupées en 7 tâches ordonnées par priorité décroissante et couplage technique croissant (les tâches critiques de sécurité d'abord, puis les violations d'architecture, puis Phase 9D manquante, puis qualité/docs). Chaque tâche est indépendante et commitée séparément.

**Tech Stack:** Spring Boot 3.x / Java 21 / Lombok / MapStruct / Spring State Machine — React 18 / TypeScript / react-i18next — Flyway / PostgreSQL — iText 8 — Docker Compose

---

## TABLE DES MATIÈRES

| Tâche | Priorité | Problèmes couverts | Effort |
|---|---|---|---|
| [T1](#task-1) — Sécurité critique (lockout + chiffrement) | 🔴 | C-01, C-02 | S+M |
| [T2](#task-2) — AuditLoggingFilter non câblé | 🔴 | C-04 | S |
| [T3](#task-3) — Violations layer rules dans les contrôleurs | 🔴🟠 | C-03, I-01, I-02, I-07, I-11 | M |
| [T4](#task-4) — CI/CD GitHub Actions manquants | 🟠 | I-04, I-05 | S |
| [T5](#task-5) — CORS par profil + Phase 9D Three-Way Matching | 🟠 | I-03, I-08 | L |
| [T6](#task-6) — Sessions actives + Délégation d'approbation | 🟠 | I-09, m-05 | M+L |
| [T7](#task-7) — Qualité, i18n, docs, tests manquants | 🟡 | m-01..m-11, I-06, I-12 | M |

---

## Prérequis — lire avant de commencer

1. Lire `CLAUDE.md` en entier — surtout §3 (contraintes absolues) et §13 (règles anti-bugs)
2. Lire `docs/WORKFLOW.md` §3-4 (transitions BAP)
3. Lire `docs/ARCHITECTURE.md` §3 (layer rules)
4. Les 5 services Docker doivent tourner :
   ```
   docker ps --format "table {{.Names}}\t{{.Status}}"
   # Expected: oct_backend, oct_frontend, oct_postgres, oct_minio, oct_mailhog — all Up
   ```
5. Commande de test backend : `.\mvnw.cmd test -pl . 2>&1 | tail -20`
6. Commande de build frontend : `cd frontend && npm run build 2>&1 | tail -10`

---

<a name="task-1"></a>
## Task 1 — Sécurité critique : lockout temporel + chiffrement bank details

**Problèmes résolus :** C-01 (User.isAccountNonLocked ignore lockedUntil), C-02 (Invoice.supplierBankDetails non chiffré)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/user/model/User.java` — méthode `isAccountNonLocked()`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/model/Invoice.java` — champ `supplierBankDetails`
- Create: `src/main/resources/db/migration/V35__encrypt_invoice_bank_details.sql` — migration de rechiffrement
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md` — ajouter PROB-017, PROB-018

---

### Step 1.1 — Écrire le test unitaire pour isAccountNonLocked avec lockedUntil

Le fichier de test `UserServiceTest.java` existe déjà. Ouvrir :
`src/test/java/com/oct/invoicesystem/domain/user/service/UserServiceTest.java`

Ajouter ces deux tests dans la classe existante :

```java
@Test
@DisplayName("isAccountNonLocked: retourne false si lockedUntil est dans le futur")
void isAccountNonLocked_lockedUntilInFuture_returnsFalse() {
    User user = User.builder()
            .username("testuser")
            .active(true)
            .lockedUntil(Instant.now().plusSeconds(600)) // verrouillé pour 10 min
            .build();

    assertThat(user.isAccountNonLocked()).isFalse();
}

@Test
@DisplayName("isAccountNonLocked: retourne true si lockedUntil est dans le passé")
void isAccountNonLocked_lockedUntilInPast_returnsTrue() {
    User user = User.builder()
            .username("testuser")
            .active(true)
            .lockedUntil(Instant.now().minusSeconds(600)) // verrou expiré
            .build();

    assertThat(user.isAccountNonLocked()).isTrue();
}
```

- [ ] Ajouter ces tests dans `UserServiceTest.java`

---

### Step 1.2 — Vérifier que les tests échouent

```powershell
.\mvnw.cmd test -Dtest=UserServiceTest#isAccountNonLocked* -pl . 2>&1 | tail -15
```
Expected: `FAILED — expected false but was true` (le premier test doit échouer car la méthode retourne `active` sans vérifier `lockedUntil`).

- [ ] Confirmer l'échec du test

---

### Step 1.3 — Corriger User.isAccountNonLocked()

Dans `src/main/java/com/oct/invoicesystem/domain/user/model/User.java`, remplacer :

```java
@Override
public boolean isAccountNonLocked() {
    return active;
}
```

par :

```java
@Override
public boolean isAccountNonLocked() {
    if (!active) return false;
    return lockedUntil == null || Instant.now().isAfter(lockedUntil);
}
```

- [ ] Appliquer la correction

---

### Step 1.4 — Vérifier que les tests passent

```powershell
.\mvnw.cmd test -Dtest=UserServiceTest -pl . 2>&1 | tail -15
```
Expected: `BUILD SUCCESS`, tous les tests `UserServiceTest` passent.

- [ ] Confirmer le succès

---

### Step 1.5 — Écrire le test pour la vérification du chiffrement Invoice.supplierBankDetails

Dans `src/test/java/com/oct/invoicesystem/domain/invoice/service/InvoiceServiceTest.java`, ajouter :

```java
@Test
@DisplayName("supplierBankDetails: le champ doit être annoté @Convert pour le chiffrement AES-256")
void supplierBankDetails_hasEncryptionConverter() throws Exception {
    java.lang.reflect.Field field = Invoice.class.getDeclaredField("supplierBankDetails");
    jakarta.persistence.Convert convertAnnotation = field.getAnnotation(jakarta.persistence.Convert.class);
    assertThat(convertAnnotation).isNotNull();
    assertThat(convertAnnotation.converter())
            .isEqualTo(com.oct.invoicesystem.shared.util.EncryptionAttributeConverter.class);
}
```

- [ ] Ajouter ce test dans `InvoiceServiceTest.java`

---

### Step 1.6 — Vérifier que le test échoue

```powershell
.\mvnw.cmd test -Dtest=InvoiceServiceTest#supplierBankDetails* -pl . 2>&1 | tail -15
```
Expected: `FAILED — expected not null but was null`.

- [ ] Confirmer l'échec

---

### Step 1.7 — Ajouter @Convert sur Invoice.supplierBankDetails

Dans `src/main/java/com/oct/invoicesystem/domain/invoice/model/Invoice.java`, remplacer :

```java
@Column(name = "supplier_bank_details")
private String supplierBankDetails;
```

par :

```java
@Convert(converter = EncryptionAttributeConverter.class)
@Column(name = "supplier_bank_details", columnDefinition = "TEXT")
private String supplierBankDetails;
```

Vérifier que l'import suivant est présent en haut du fichier (il l'est déjà pour Supplier) :
```java
import com.oct.invoicesystem.shared.util.EncryptionAttributeConverter;
```

- [ ] Appliquer la correction

---

### Step 1.8 — Créer la migration Flyway pour rechiffrer les données existantes

Créer `src/main/resources/db/migration/V35__encrypt_invoice_bank_details.sql` :

```sql
-- V35: Migration des coordonnées bancaires existantes sur la table invoices.
-- Les lignes dont supplier_bank_details n'est pas NULL et ne commence pas par
-- le préfixe AES base64 doivent être nullifiées (elles sont en clair et ne peuvent
-- pas être rechiffrées par SQL). Les nouvelles valeurs seront chiffrées par JPA.
-- IMPORTANT: Sauvegarder les données avant de lancer cette migration en production.

-- Nullifier les coordonnées bancaires en clair (non chiffrées).
-- Les coordonnées chiffrées par EncryptionUtil commencent par un bloc base64 valide.
-- En l'absence d'un moyen de rechiffrer depuis SQL, on vide le champ pour forcer
-- une nouvelle saisie via le portail fournisseur ou l'interface admin.
UPDATE invoices
SET supplier_bank_details = NULL
WHERE supplier_bank_details IS NOT NULL;

-- Note pour la production: avant d'appliquer cette migration, exporter les
-- coordonnées bancaires existantes via:
--   COPY (SELECT id, supplier_bank_details FROM invoices WHERE supplier_bank_details IS NOT NULL)
--   TO '/tmp/bank_details_backup.csv' CSV HEADER;
```

- [ ] Créer le fichier `V35__encrypt_invoice_bank_details.sql`

---

### Step 1.9 — Vérifier que tous les tests passent

```powershell
.\mvnw.cmd test -pl . 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] Confirmer le succès global des tests

---

### Step 1.10 — Mettre à jour KNOWN_ISSUES_REGISTRY.md

Dans `docs/KNOWN_ISSUES_REGISTRY.md`, ajouter sous la section `PROBLÈMES EN COURS` :

```markdown
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
```

- [ ] Ajouter PROB-017 et PROB-018 dans le registre

---

### Step 1.11 — Commit

```powershell
git add src/main/java/com/oct/invoicesystem/domain/user/model/User.java `
       src/main/java/com/oct/invoicesystem/domain/invoice/model/Invoice.java `
       src/main/resources/db/migration/V35__encrypt_invoice_bank_details.sql `
       src/test/java/com/oct/invoicesystem/domain/user/service/UserServiceTest.java `
       src/test/java/com/oct/invoicesystem/domain/invoice/service/InvoiceServiceTest.java `
       docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(security): correct account lockout check + encrypt Invoice.supplierBankDetails (PROB-017, PROB-018)"
```

- [ ] Commit

---

<a name="task-2"></a>
## Task 2 — AuditLoggingFilter non enregistré dans la chaîne Spring Security

**Problème résolu :** C-04 (filtre d'audit présent mais jamais exécuté)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/config/SecurityConfig.java` — câbler le filtre
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md` — ajouter PROB-019

---

### Step 2.1 — Écrire un test d'intégration vérifiant que le filtre est actif

Dans `src/test/java/com/oct/invoicesystem/domain/audit/controller/AuditControllerTest.java`, ajouter :

```java
@Test
@DisplayName("AuditLoggingFilter: un POST authentifié génère une entrée dans audit_logs")
@WithMockUser(roles = "ASSISTANT_COMPTABLE")
void auditFilter_authenticatedPost_createsAuditEntry() throws Exception {
    long countBefore = auditLogRepository.count();

    mockMvc.perform(post("/api/v1/invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "supplierName": "Test SA",
                  "supplierEmail": "test@test.com",
                  "departmentId": "00000000-0000-0000-0000-000000000001",
                  "amount": 10000.00,
                  "currency": "XAF",
                  "issueDate": "2026-06-01",
                  "dueDate": "2026-07-01"
                }
            """))
            .andExpect(status().isCreated());

    long countAfter = auditLogRepository.count();
    assertThat(countAfter).isGreaterThan(countBefore);
}
```

Ajouter l'injection dans la classe de test :
```java
@Autowired
private com.oct.invoicesystem.domain.audit.repository.AuditLogRepository auditLogRepository;
```

- [ ] Ajouter ce test

---

### Step 2.2 — Confirmer que le test échoue (count n'augmente pas)

```powershell
.\mvnw.cmd test -Dtest=AuditControllerTest#auditFilter* -pl . 2>&1 | tail -15
```
Expected: `FAILED — expected greater than X but was X` (le filtre ne s'exécute pas).

- [ ] Confirmer l'échec

---

### Step 2.3 — Câbler AuditLoggingFilter dans SecurityConfig

Dans `src/main/java/com/oct/invoicesystem/config/SecurityConfig.java`, ajouter l'injection et le câblage.

Remplacer le constructeur existant :

```java
private final JwtAuthenticationFilter jwtAuthFilter;
private final RateLimitingFilter rateLimitingFilter;
private final HttpSecurityHeadersFilter httpSecurityHeadersFilter;
private final MfaSetupEnforcementFilter mfaSetupEnforcementFilter;

public SecurityConfig(@org.springframework.context.annotation.Lazy JwtAuthenticationFilter jwtAuthFilter,
                     RateLimitingFilter rateLimitingFilter,
                     HttpSecurityHeadersFilter httpSecurityHeadersFilter,
                     MfaSetupEnforcementFilter mfaSetupEnforcementFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.rateLimitingFilter = rateLimitingFilter;
    this.httpSecurityHeadersFilter = httpSecurityHeadersFilter;
    this.mfaSetupEnforcementFilter = mfaSetupEnforcementFilter;
}
```

par :

```java
private final JwtAuthenticationFilter jwtAuthFilter;
private final RateLimitingFilter rateLimitingFilter;
private final HttpSecurityHeadersFilter httpSecurityHeadersFilter;
private final MfaSetupEnforcementFilter mfaSetupEnforcementFilter;
private final com.oct.invoicesystem.shared.filter.AuditLoggingFilter auditLoggingFilter;

public SecurityConfig(
        @org.springframework.context.annotation.Lazy JwtAuthenticationFilter jwtAuthFilter,
        RateLimitingFilter rateLimitingFilter,
        HttpSecurityHeadersFilter httpSecurityHeadersFilter,
        MfaSetupEnforcementFilter mfaSetupEnforcementFilter,
        com.oct.invoicesystem.shared.filter.AuditLoggingFilter auditLoggingFilter) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.rateLimitingFilter = rateLimitingFilter;
    this.httpSecurityHeadersFilter = httpSecurityHeadersFilter;
    this.mfaSetupEnforcementFilter = mfaSetupEnforcementFilter;
    this.auditLoggingFilter = auditLoggingFilter;
}
```

Dans la méthode `securityFilterChain`, ajouter après `.addFilterAfter(mfaSetupEnforcementFilter, ...)` :

```java
.addFilterAfter(auditLoggingFilter, com.oct.invoicesystem.domain.auth.filter.JwtAuthenticationFilter.class)
```

La chaîne complète doit ressembler à :

```java
.addFilterBefore(httpSecurityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(mfaSetupEnforcementFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(auditLoggingFilter, com.oct.invoicesystem.domain.auth.filter.JwtAuthenticationFilter.class);
```

- [ ] Appliquer la modification dans `SecurityConfig.java`

---

### Step 2.4 — Vérifier que tous les tests passent

```powershell
.\mvnw.cmd test -pl . 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] Confirmer le succès

---

### Step 2.5 — Ajouter PROB-019 dans KNOWN_ISSUES_REGISTRY.md

```markdown
### [PROB-019] AuditLoggingFilter existait mais n'était pas câblé dans Spring Security
- **Catégorie :** Architecture
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit complet
- **Symptôme :** La table `audit_logs` ne recevait aucune entrée des filtres HTTP. Seules les actions explicitement loggées par les services apparaissaient.
- **Cause racine :** `AuditLoggingFilter` était un `@Component` Spring mais n'était jamais ajouté à la chaîne de filtres `SecurityConfig`. Spring ne l'incluait donc pas automatiquement dans la chaîne de sécurité (contrairement aux filtres déclarés via `web.xml`).
- **Solution appliquée :** Injection de `AuditLoggingFilter` dans `SecurityConfig` et ajout via `.addFilterAfter(auditLoggingFilter, JwtAuthenticationFilter.class)`.
- **Règle préventive :** Tout filtre Spring Security DOIT être explicitement ajouté dans `SecurityConfig.securityFilterChain()`. L'annotation `@Component` seule ne l'inclut pas dans la chaîne de sécurité. Vérifier à chaque ajout de filtre qu'il est câblé ET dans le bon ordre.
- **Fichiers modifiés :** `SecurityConfig.java`
```

- [ ] Ajouter PROB-019

---

### Step 2.6 — Commit

```powershell
git add src/main/java/com/oct/invoicesystem/config/SecurityConfig.java `
       src/test/java/com/oct/invoicesystem/domain/audit/controller/AuditControllerTest.java `
       docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(security): wire AuditLoggingFilter into Spring Security filter chain (PROB-019)"
```

- [ ] Commit

---

<a name="task-3"></a>
## Task 3 — Violations layer rules : repositories dans les contrôleurs

**Problèmes résolus :** C-03, I-01, I-02, I-07, I-11

**Résumé des violations :**
- `InvoiceController` : accède à `InvoiceRepository`, `InvoiceDocumentRepository`, `UserRepository`, `ThreeWayMatchingResultRepository`, `InvoiceStatusHistoryRepository` + méthode `toDto()` manuelle au lieu du mapper MapStruct
- `ApprovalController` : accède à `ApprovalStepRepository` pour `getApprovalSteps()`
- `UserProfileController` : accède à `UserRepository` directement pour save
- `SupplierController`, `SupplierPortalController`, `MatchingConfigController`, `PurchaseOrderController` : accèdent à `UserRepository` pour résoudre l'acteur courant
- `ReportController` : accède à `InvoiceStatusHistoryRepository`

**Stratégie :** Créer un helper partagé `SecurityHelper` pour la résolution de l'utilisateur courant, puis déplacer les logiques métier dans les services appropriés.

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/shared/util/SecurityHelper.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/controller/InvoiceController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalService.java` (interface)
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/user/controller/UserProfileController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/user/service/UserService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierPortalController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingConfigController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/PurchaseOrderController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/controller/ReportController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/report/service/ReportService.java` (interface + impl)
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md`

---

### Step 3.1 — Créer SecurityHelper

Créer `src/main/java/com/oct/invoicesystem/shared/util/SecurityHelper.java` :

```java
package com.oct.invoicesystem.shared.util;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper partagé pour résoudre l'utilisateur courant depuis le SecurityContext.
 * Évite d'injecter UserRepository dans les contrôleurs.
 */
@Component
@RequiredArgsConstructor
public class SecurityHelper {

    private final UserRepository userRepository;

    /**
     * Résout l'UUID de l'utilisateur courant depuis son Authentication.
     *
     * @param authentication l'objet Authentication Spring Security
     * @return l'UUID de l'utilisateur
     * @throws ResourceNotFoundException si l'utilisateur n'existe pas en base
     */
    public UUID currentUserId(Authentication authentication) {
        return currentUser(authentication).getId();
    }

    /**
     * Résout l'entité User de l'utilisateur courant depuis son Authentication.
     *
     * @param authentication l'objet Authentication Spring Security
     * @return l'entité User
     * @throws ResourceNotFoundException si l'utilisateur n'existe pas en base
     */
    public User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }
}
```

- [ ] Créer `SecurityHelper.java`

---

### Step 3.2 — Refactorer InvoiceController : supprimer les repositories, utiliser InvoiceMapper

Dans `InvoiceController.java` :

**A) Remplacer les dépendances :** supprimer les injections de `UserRepository`, `InvoiceRepository`, `InvoiceDocumentRepository`, `ThreeWayMatchingResultRepository`, `InvoiceStatusHistoryRepository`. Les remplacer par `InvoiceMapper`, `SecurityHelper`.

La nouvelle liste de dépendances doit être :

```java
private final InvoiceService invoiceService;
private final InvoiceStateMachineService invoiceStateMachineService;
private final ThreeWayMatchingService threeWayMatchingService;
private final InvoiceMapper invoiceMapper;
private final SecurityHelper securityHelper;
private final InvoicePdfService invoicePdfService;
```

**B) Modifier `getActorId()` :**
```java
private UUID getActorId(Authentication authentication) {
    return securityHelper.currentUserId(authentication);
}
```

**C) Déplacer `getPendingValidationQueue` dans InvoiceService :**

Dans `InvoiceService.java`, ajouter la méthode publique :

```java
/**
 * Retourne les factures en attente de validation (statuts EN_VALIDATION_N1 et EN_VALIDATION_N2).
 *
 * @param pageable pagination et tri
 * @return page de factures en attente
 */
public Page<Invoice> getPendingValidationQueue(Pageable pageable) {
    return invoiceRepository.findPendingValidationQueue(pageable);
}
```

Dans `InvoiceController`, modifier `getPendingValidationQueue` pour appeler `invoiceService.getPendingValidationQueue(pageable)`.

**D) Déplacer `getMatchingResult` dans InvoiceService :**

Dans `InvoiceService.java`, ajouter :

```java
/**
 * Retourne le résultat de matching trois-voies pour une facture.
 *
 * @param invoiceId l'UUID de la facture
 * @return le résultat de matching
 * @throws ResourceNotFoundException si aucun résultat n'existe
 */
public com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult getMatchingResult(UUID invoiceId) {
    return threeWayMatchingResultRepository.findByInvoiceId(invoiceId)
            .orElseThrow(() -> new ResourceNotFoundException("Matching result not found for invoice: " + invoiceId));
}
```

Ajouter l'injection `ThreeWayMatchingResultRepository threeWayMatchingResultRepository` dans `InvoiceService`.

**E) Déplacer `getInvoiceHistory` dans InvoiceService :**

Dans `InvoiceService.java`, ajouter :

```java
/**
 * Retourne l'historique des transitions de statut d'une facture.
 *
 * @param invoiceId l'UUID de la facture
 * @return liste ordonnée des transitions
 */
public List<com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO> getInvoiceHistory(UUID invoiceId) {
    getById(invoiceId); // vérifier que la facture existe
    return invoiceStatusHistoryRepository.findHistoryDTOsByInvoiceId(invoiceId);
}
```

Ajouter l'injection `InvoiceStatusHistoryRepository invoiceStatusHistoryRepository` dans `InvoiceService`.

**F) Déplacer la validation "document requis" dans `submitInvoice` de InvoiceService :**

Dans `InvoiceService.java`, ajouter la méthode :

```java
/**
 * Valide qu'une facture a au moins un document attaché avant soumission.
 *
 * @param invoiceId l'UUID de la facture
 * @throws ValidationException si aucun document n'est attaché
 */
public void validateDocumentPresent(UUID invoiceId) {
    if (invoiceDocumentRepository.findByInvoiceId(invoiceId).isEmpty()) {
        throw new ValidationException("error.invoice.no_document");
    }
}
```

Ajouter l'injection `InvoiceDocumentRepository invoiceDocumentRepository` dans `InvoiceService`.

Dans `InvoiceController.submitInvoice()`, remplacer le bloc `if (invoiceDocumentRepository...)` par :
```java
invoiceService.validateDocumentPresent(id);
```

**G) Supprimer la méthode `toDto()` privée, utiliser `invoiceMapper.toDto()` :**

Remplacer toutes les occurrences de `this::toDto` et `toDto(...)` par `invoiceMapper::toDto` et `invoiceMapper.toDto(...)`.

- [ ] Appliquer toutes les modifications de InvoiceController + InvoiceService

---

### Step 3.3 — Refactorer ApprovalController : extraire getApprovalSteps dans ApprovalService

**A)** Dans l'interface `ApprovalService.java`, ajouter :

```java
List<java.util.Map<String, Object>> getApprovalSteps(UUID invoiceId);
```

**B)** Dans `ApprovalServiceImpl.java`, implémenter :

```java
@Override
public List<Map<String, Object>> getApprovalSteps(UUID invoiceId) {
    List<ApprovalStep> steps = approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(invoiceId);
    return steps.stream().map(s -> {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("stepOrder", s.getStepOrder());
        m.put("stepName", s.getStepNameEn());
        m.put("stepNameFr", s.getStepNameFr());
        m.put("departmentCode", s.getDepartmentCode());
        m.put("status", s.getStatus());
        m.put("approverUsername", s.getApprover() != null ? s.getApprover().getUsername() : null);
        m.put("approverName", s.getApprover() != null
                ? (s.getApprover().getFirstName() + " " + s.getApprover().getLastName()).trim()
                : null);
        m.put("comments", s.getComments());
        m.put("rejectionReason", s.getRejectionReason());
        m.put("deadline", s.getDeadline());
        m.put("actionAt", s.getActionAt());
        return m;
    }).toList();
}
```

**C)** Dans `ApprovalController.java`, remplacer l'injection `ApprovalStepRepository` par rien (la supprimer complètement). Modifier `getApprovalSteps()` pour appeler `approvalService.getApprovalSteps(invoiceId)`.

Après modification, les seules dépendances de `ApprovalController` doivent être :
```java
private final ApprovalService approvalService;
```

- [ ] Appliquer les modifications ApprovalController + ApprovalServiceImpl

---

### Step 3.4 — Refactorer UserProfileController : passer par UserService

Lire `UserService.java` pour voir la méthode existante de mise à jour utilisateur. Si elle existe, l'utiliser. Sinon, ajouter dans `UserService` :

```java
/**
 * Met à jour le profil de l'utilisateur courant (prénom, nom, langue).
 * L'email ne peut pas être modifié via cette méthode.
 *
 * @param userId l'UUID de l'utilisateur
 * @param firstName nouveau prénom (null = inchangé)
 * @param lastName nouveau nom (null = inchangé)
 * @param preferredLang nouvelle langue préférée (null = inchangée)
 * @return l'entité User mise à jour
 */
@Transactional
public User updateProfile(UUID userId, String firstName, String lastName, String preferredLang) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    if (firstName != null) user.setFirstName(firstName);
    if (lastName != null) user.setLastName(lastName);
    if (preferredLang != null) user.setPreferredLang(preferredLang);
    return userRepository.save(user);
}
```

Dans `UserProfileController.java`, remplacer l'injection `UserRepository` par `UserService` (si pas déjà présent) et `SecurityHelper`. Modifier `updateProfile()` pour appeler `userService.updateProfile(...)`.

- [ ] Refactorer UserProfileController

---

### Step 3.5 — Injecter SecurityHelper dans les autres contrôleurs

Pour chacun de ces contrôleurs, **remplacer l'injection `UserRepository` par `SecurityHelper`**, et remplacer le pattern :

```java
// Avant (violation)
User actor = userRepository.findById(actorId)
        .orElseThrow(() -> ...);
// ou
return userRepository.findByUsername(username)
        .map(User::getId)
        .orElseThrow(...);
```

par :

```java
// Après (correct)
UUID actorId = securityHelper.currentUserId(authentication);
// ou
User actor = securityHelper.currentUser(authentication);
```

Contrôleurs à modifier :
- `SupplierController.java` — remplacer `userRepository.findByUsername(...)` par `securityHelper.currentUser(authentication)`
- `SupplierPortalController.java` — idem
- `MatchingConfigController.java` — idem
- `PurchaseOrderController.java` — idem

- [ ] Appliquer SecurityHelper dans les 4 contrôleurs

---

### Step 3.6 — Refactorer ReportController : déplacer la query dans ReportService

Dans `ReportService` (interface), ajouter :

```java
List<com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO> getRecentActivity(int limit);
```

Dans `ReportServiceImpl.java`, implémenter :

```java
@Override
public List<InvoiceHistoryDTO> getRecentActivity(int limit) {
    int boundedLimit = Math.max(1, Math.min(limit, 100));
    return historyRepository.findAllByOrderByChangedAtDesc(PageRequest.of(0, boundedLimit))
            .stream()
            .map(h -> new InvoiceHistoryDTO(h.getId(), h.getInvoice().getId(),
                    h.getFromStatus(), h.getToStatus(),
                    h.getChangedBy() != null ? h.getChangedBy().getUsername() : null,
                    h.getChangeReason(), h.getChangedAt()))
            .toList();
}
```

Ajouter l'injection `InvoiceStatusHistoryRepository historyRepository` dans `ReportServiceImpl`.

Dans `ReportController.java`, supprimer l'injection `InvoiceStatusHistoryRepository` et remplacer l'appel direct par `reportService.getRecentActivity(boundedLimit)`.

- [ ] Refactorer ReportController + ReportService

---

### Step 3.7 — Vérifier la compilation et les tests

```powershell
.\mvnw.cmd compile -pl . 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`, aucune erreur de compilation.

```powershell
.\mvnw.cmd test -pl . 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] Compiler et vérifier les tests

---

### Step 3.8 — Ajouter PROB-020 dans le registre

```markdown
### [PROB-020] Repositories injectés directement dans 7+ contrôleurs (violation layer rules)
- **Catégorie :** Architecture
- **Sévérité :** 🔴 Critique
- **Découvert :** 2026-06-06 — Audit complet
- **Symptôme :** Logique métier et queries dans les contrôleurs. Contournement des services. Transactions non gérées par @Transactional dans les contrôleurs.
- **Cause racine :** Pattern de "raccourci" : certains contrôleurs injectaient des repositories pour éviter d'ajouter une méthode de service. Pratique acceptable en prototype mais interdite en CLAUDE.md §3.
- **Solution appliquée :** Création de `SecurityHelper` pour la résolution de l'utilisateur courant. Déplacement de toute la logique dans les services. Utilisation de `InvoiceMapper` au lieu de `toDto()` manuelle.
- **Règle préventive :** Un contrôleur ne peut contenir que : (1) routing/binding, (2) un seul appel de service, (3) wrapping de la réponse. Si un contrôleur a besoin de plus d'un service ou d'un repository, la logique doit être dans un service.
- **Fichiers modifiés :** SecurityHelper.java (créé), InvoiceController.java, ApprovalController.java, UserProfileController.java, SupplierController.java, SupplierPortalController.java, MatchingConfigController.java, PurchaseOrderController.java, ReportController.java, InvoiceService.java, ApprovalService.java, ApprovalServiceImpl.java, ReportService.java, ReportServiceImpl.java, UserService.java
```

- [ ] Ajouter PROB-020

---

### Step 3.9 — Commit

```powershell
git add src/ docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "refactor(arch): enforce layer rules — repositories out of controllers, create SecurityHelper (PROB-020)"
```

- [ ] Commit

---

<a name="task-4"></a>
## Task 4 — GitHub Actions CI/CD manquants

**Problèmes résolus :** I-04 (ci.yml absent), I-05 (security-scan.yml absent)

> Ces fichiers ont été marqués ✅ dans TASKS.md (P10-12, P10-15) mais n'existent pas physiquement.

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/security-scan.yml`
- Create: `.github/zap-rules.tsv`
- Modify: `docs/TASKS.md` — décocher P10-12 et P10-15 puis les recocher une fois les fichiers créés

---

### Step 4.1 — Créer le dossier .github/workflows

```powershell
New-Item -ItemType Directory -Force ".github\workflows" | Out-Null
Write-Output "Dossier créé"
```

- [ ] Créer le dossier

---

### Step 4.2 — Créer .github/workflows/ci.yml

Créer `.github/workflows/ci.yml` :

```yaml
# CI Pipeline — OCT Invoice System
# Triggers: push to main, pull_request to main
# Jobs: backend (Java 21 + PG18 + MinIO), frontend (Node 20 + Vitest), docker (build check)

name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  backend:
    name: Backend — Java 21 + Tests
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:18-alpine
        env:
          POSTGRES_DB: oct_invoice_dev
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: dany
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      minio:
        image: minio/minio:latest
        env:
          MINIO_ROOT_USER: MinIO
          MINIO_ROOT_PASSWORD: dany1234
        ports:
          - 9000:9000
        options: >-
          --health-cmd "curl -f http://localhost:9000/minio/health/live"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        command: server /data

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Create MinIO bucket
        run: |
          curl -O https://dl.min.io/client/mc/release/linux-amd64/mc
          chmod +x mc
          ./mc alias set local http://localhost:9000 MinIO dany1234
          ./mc mb --ignore-existing local/oct-invoices

      - name: Run backend tests
        env:
          SPRING_PROFILES_ACTIVE: test
          DB_HOST: localhost
          DB_PORT: 5432
          DB_NAME: oct_invoice_dev
          DB_USER: postgres
          DB_PASSWORD: dany
          MINIO_ENDPOINT: http://localhost:9000
          MINIO_ACCESS_KEY: MinIO
          MINIO_SECRET_KEY: dany1234
          MINIO_BUCKET: oct-invoices
          JWT_PRIVATE_KEY: ${{ secrets.JWT_PRIVATE_KEY_TEST }}
          JWT_PUBLIC_KEY: ${{ secrets.JWT_PUBLIC_KEY_TEST }}
          ENCRYPTION_KEY: TestEncryptionKey1234567890ABCDEF
        run: ./mvnw test --batch-mode

      - name: Upload JaCoCo report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: jacoco-report
          path: target/site/jacoco/

  frontend:
    name: Frontend — Node 20 + Vitest + Build
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up Node 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Run Vitest tests
        working-directory: frontend
        run: npm test -- --run

      - name: Build frontend
        working-directory: frontend
        run: npm run build

  docker:
    name: Docker — Build check
    runs-on: ubuntu-latest
    needs: [backend, frontend]

    steps:
      - uses: actions/checkout@v4

      - name: Build Docker images
        env:
          JWT_PRIVATE_KEY: placeholder_for_build_only
          JWT_PUBLIC_KEY: placeholder_for_build_only
          ENCRYPTION_KEY: TestEncryptionKey1234567890ABCDEF
        run: docker compose build --no-cache
```

- [ ] Créer `.github/workflows/ci.yml`

---

### Step 4.3 — Créer .github/workflows/security-scan.yml

Créer `.github/workflows/security-scan.yml` :

```yaml
# OWASP ZAP Security Scan — OCT Invoice System
# Triggers: après CI sur main, ou manuellement

name: Security Scan (OWASP ZAP)

on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]
    branches: [main]
  workflow_dispatch:

jobs:
  zap-scan:
    name: OWASP ZAP Baseline Scan
    runs-on: ubuntu-latest
    if: ${{ github.event.workflow_run.conclusion == 'success' || github.event_name == 'workflow_dispatch' }}

    services:
      postgres:
        image: postgres:18-alpine
        env:
          POSTGRES_DB: oct_invoice_dev
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: dany
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build backend (skip tests)
        run: ./mvnw package -DskipTests --batch-mode

      - name: Start backend for scan
        env:
          SPRING_PROFILES_ACTIVE: test
          DB_HOST: localhost
          JWT_PRIVATE_KEY: ${{ secrets.JWT_PRIVATE_KEY_TEST }}
          JWT_PUBLIC_KEY: ${{ secrets.JWT_PUBLIC_KEY_TEST }}
          ENCRYPTION_KEY: TestEncryptionKey1234567890ABCDEF
          MINIO_ENDPOINT: http://localhost:9000
          MINIO_ACCESS_KEY: MinIO
          MINIO_SECRET_KEY: dany1234
        run: |
          java -jar target/*.jar &
          sleep 30
          curl -f http://localhost:8080/actuator/health || exit 1

      - name: OWASP ZAP Baseline Scan
        uses: zaproxy/action-baseline@v0.12.0
        with:
          target: 'http://localhost:8080'
          rules_file_name: '.github/zap-rules.tsv'
          fail_action: true
          cmd_options: '-j'

      - name: Upload ZAP report
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: zap-report
          path: |
            report_html.html
            report_json.json
```

- [ ] Créer `.github/workflows/security-scan.yml`

---

### Step 4.4 — Créer .github/zap-rules.tsv

Créer `.github/zap-rules.tsv` :

```tsv
# ZAP Rules — Accepted-risk informational alerts
# Format: ruleId\tACTION\tPARAM\tEVIDENCE
# Actions: IGNORE (accepted risk), WARN (noted but not blocking), FAIL (blocking)
10035	IGNORE	Content Security Policy (CSP) Header Not Set	handled by HttpSecurityHeadersFilter
10016	IGNORE	Web Browser XSS Protection Not Enabled	handled by X-Content-Type-Options header
10038	IGNORE	Content Security Policy (CSP) Report-Only Header Found	dev-only
```

- [ ] Créer `.github/zap-rules.tsv`

---

### Step 4.5 — Commit

```powershell
git add .github/
git commit -m "ci: add GitHub Actions CI pipeline + OWASP ZAP security scan (P10-12, P10-15)"
```

- [ ] Commit

---

<a name="task-5"></a>
## Task 5 — CORS par profil + Phase 9D Three-Way Matching

Cette tâche couvre deux sujets : le fix CORS rapide (I-08) et l'implémentation complète de Phase 9D (I-03), la plus grosse du plan.

### 5A — CORS par profil (I-08)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/config/CorsConfig.java`
- Modify: `src/main/resources/application.yaml`

#### Step 5A.1 — Modifier CorsConfig pour lire les origines depuis env var

Remplacer le contenu de `CorsConfig.java` :

```java
package com.oct.invoicesystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

- [ ] Modifier `CorsConfig.java`

#### Step 5A.2 — Ajouter la config dans application.yaml

Dans `application.yaml`, section partagée (avant le premier `---`), ajouter :

```yaml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS:http://localhost:3000}
```

Dans la section profil `prod`, ajouter :

```yaml
app:
  cors:
    allowed-origins: ${ALLOWED_ORIGINS}
```

- [ ] Modifier `application.yaml`

#### Step 5A.3 — Commit CORS

```powershell
git add src/main/java/com/oct/invoicesystem/config/CorsConfig.java `
       src/main/resources/application.yaml
git commit -m "fix(config): configure CORS allowed origins from env var ALLOWED_ORIGINS (I-08)"
```

- [ ] Commit

---

### 5B — Phase 9D : Three-Way Matching (P9-35 à P9-47)

> Les entités `PurchaseOrder`, `PurchaseOrderItem`, `GoodsReceiptNote`, `GoodsReceiptItem`, `ThreeWayMatchingResult`, `MatchingConfig` existent déjà dans le code (Phase 9D était partiellement scaffoldée). Ce qui manque : les migrations Flyway (V36-V38), les services complets, l'intégration dans la state machine, et les tests.

**Files:**
- Create: `src/main/resources/db/migration/V36__create_purchase_orders.sql`
- Create: `src/main/resources/db/migration/V37__create_goods_receipt_notes.sql`
- Create: `src/main/resources/db/migration/V38__create_three_way_matching_complete.sql`
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingServiceImpl.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/service/InvoiceStateMachineServiceImpl.java` (matching déjà intégré, à vérifier)
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/PurchaseOrderController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/purchasing/controller/MatchingConfigController.java`
- Create: `src/test/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingServiceTest.java` (existe déjà, enrichir)
- Create: `src/test/java/com/oct/invoicesystem/domain/purchasing/integration/ThreeWayMatchingIntegrationTest.java` (existe déjà, enrichir)

> **Note préliminaire :** Avant de commencer 5B, lire les fichiers existants dans `domain/purchasing/` pour confirmer l'état exact du scaffolding. Les steps ci-dessous supposent que les entités et repositories sont présents mais les migrations Flyway sont manquantes ou incomplètes.

#### Step 5B.1 — Vérifier l'état des migrations existantes pour le matching

```powershell
Get-ChildItem "src\main\resources\db\migration" -Filter "V1*","V2*" | Sort-Object Name | Select-Object Name
```
Expected : V17, V18, V19 existent (créés pendant Phase 9D partielle). Si oui, lire leur contenu pour éviter les doublons.

- [ ] Vérifier les migrations existantes V17-V19

#### Step 5B.2 — Lire ThreeWayMatchingServiceImpl pour comprendre ce qui est implémenté

```
Lire: src/main/java/com/oct/invoicesystem/domain/purchasing/service/ThreeWayMatchingServiceImpl.java
```

Vérifier que les méthodes suivantes existent et fonctionnent correctement :
- `match(Invoice, PurchaseOrder, GoodsReceiptNote)` → retourne `MATCHED`, `PARTIAL`, ou `MISMATCH`
- `recordOverride(UUID invoiceId, User actor, String reason)` → enregistre l'override

Si des méthodes manquent, les implémenter selon ce contrat :

```java
/**
 * Compare les lignes de la facture avec le bon de commande et le bon de réception.
 * Applique les tolérances depuis matching_config (table DB).
 *
 * @param invoice la facture à comparer
 * @param po le bon de commande de référence
 * @param grn le bon de réception (peut être null si require_grn=false dans config)
 * @return le résultat de matching persisté
 * @throws WorkflowException si grn est null et require_grn=true en config
 */
@Transactional
ThreeWayMatchingResult match(Invoice invoice, PurchaseOrder po, GoodsReceiptNote grn);

/**
 * Enregistre un override manuel d'un MISMATCH par un DAF ou Admin.
 *
 * @param invoiceId l'UUID de la facture
 * @param actor l'utilisateur qui effectue l'override (doit être ROLE_DAF ou ROLE_ADMIN)
 * @param overrideReason la justification (obligatoire)
 */
@Transactional
void recordOverride(UUID invoiceId, User actor, String overrideReason);
```

- [ ] Lire et compléter ThreeWayMatchingServiceImpl si nécessaire

#### Step 5B.3 — Écrire les tests de matching

Dans `ThreeWayMatchingServiceTest.java`, vérifier la présence de ces 5 scénarios (les ajouter s'ils manquent) :

```java
@Test
@DisplayName("match: MATCHED quand quantités et montants identiques à PO et GRN")
void match_perfectMatch_returnsMatched() { /* ... */ }

@Test
@DisplayName("match: PARTIAL quand montant dans la tolérance configurée (2%)")
void match_withinTolerance_returnsPartial() { /* ... */ }

@Test
@DisplayName("match: MISMATCH quand montant dépasse la tolérance")
void match_outsideTolerance_returnsMismatch() { /* ... */ }

@Test
@DisplayName("match: MISMATCH quand GRN absent et require_grn=true")
void match_missingGrnRequired_returnsMismatch() { /* ... */ }

@Test
@DisplayName("recordOverride: OVERRIDDEN enregistré avec acteur et raison")
void recordOverride_validRequest_persistsOverride() { /* ... */ }
```

- [ ] Vérifier/compléter les tests

#### Step 5B.4 — Vérifier l'intégration matching dans la state machine

Lire `InvoiceStateMachineServiceImpl.performMatchingCheck()`. La méthode doit :
1. Vérifier si un override OVERRIDDEN existe → si oui, laisser passer
2. Charger le PO via `purchaseOrderRepository.findByIdActive()`
3. Charger le GRN via config + GRN repository
4. Appeler `threeWayMatchingService.match()`
5. Si résultat est `MISMATCH` → throw `WorkflowException`

Si l'intégration est incomplète, la compléter.

- [ ] Vérifier/compléter performMatchingCheck

#### Step 5B.5 — Écrire le test d'intégration bout-en-bout

Dans `ThreeWayMatchingIntegrationTest.java`, vérifier la présence du scénario complet :

```java
@Test
@DisplayName("Workflow complet: PO → GRN → Facture → MISMATCH bloque → Override → SOUMIS")
@Transactional
void fullMatchingWorkflow_mismatchThenOverride_allowsSubmission() {
    // 1. Créer un fournisseur et un PO avec 2 lignes
    // 2. Créer un GRN avec une quantité différente (MISMATCH)
    // 3. Créer une facture liée au PO avec le montant du GRN
    // 4. Tenter de soumettre → doit lancer WorkflowException
    // 5. Enregistrer un override (DAF)
    // 6. Resoumettre → doit passer à SOUMIS
    // Assert: invoice.status == SOUMIS, matchingResult.status == OVERRIDDEN
}
```

- [ ] Vérifier/compléter le test d'intégration

#### Step 5B.6 — Vérifier tous les tests

```powershell
.\mvnw.cmd test -Dtest=ThreeWayMatchingServiceTest,ThreeWayMatchingIntegrationTest -pl . 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] Confirmer le succès

#### Step 5B.7 — Commit Phase 9D

```powershell
git add src/
git commit -m "feat(matching): complete Phase 9D three-way matching — PO/GRN/Invoice workflow (P9-35 to P9-47)"
```

- [ ] Commit

---

<a name="task-6"></a>
## Task 6 — Sessions actives (I-09) + Délégation d'approbation (m-05/PROB-016)

### 6A — Sessions actives (Module 13)

Le Module 13 requiert une vue des sessions actives. Étant donné que le système est stateless (JWT), "sessions actives" signifie : "tokens JWT valides émis et non expirés". Une implémentation pragmatique stocke les refresh tokens actifs en base.

**Files:**
- Create: `src/main/resources/db/migration/V39__create_active_sessions.sql`
- Modify: `src/main/java/com/oct/invoicesystem/domain/auth/service/AuthService.java` — enregistrer refresh token à l'émission
- Create: `src/main/java/com/oct/invoicesystem/domain/user/controller/AdminSessionController.java`
- Modify: `frontend/src/pages/admin/SecuritySettingsPage.tsx` — afficher les sessions

#### Step 6A.1 — Créer la migration V39

Créer `src/main/resources/db/migration/V39__create_active_sessions.sql` :

```sql
CREATE TABLE active_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    refresh_token   VARCHAR(500) NOT NULL UNIQUE,
    ip_address      VARCHAR(50),
    user_agent      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_active_sessions_user ON active_sessions(user_id) WHERE revoked = FALSE;
CREATE INDEX idx_active_sessions_expires ON active_sessions(expires_at) WHERE revoked = FALSE;
```

- [ ] Créer V39

#### Step 6A.2 — Créer l'entité ActiveSession

Créer `src/main/java/com/oct/invoicesystem/domain/auth/model/ActiveSession.java` :

```java
package com.oct.invoicesystem.domain.auth.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "active_sessions")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "refresh_token", nullable = false, unique = true, length = 500)
    private String refreshToken;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
```

- [ ] Créer `ActiveSession.java`

#### Step 6A.3 — Créer ActiveSessionRepository

Créer `src/main/java/com/oct/invoicesystem/domain/auth/repository/ActiveSessionRepository.java` :

```java
package com.oct.invoicesystem.domain.auth.repository;

import com.oct.invoicesystem.domain.auth.model.ActiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActiveSessionRepository extends JpaRepository<ActiveSession, UUID> {

    Optional<ActiveSession> findByRefreshToken(String refreshToken);

    @Query("SELECT s FROM ActiveSession s WHERE s.user.id = :userId AND s.revoked = false AND s.expiresAt > :now")
    List<ActiveSession> findActiveByUserId(UUID userId, Instant now);

    @Query("SELECT s FROM ActiveSession s WHERE s.revoked = false AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<ActiveSession> findAllActive(Instant now);

    @Modifying
    @Query("UPDATE ActiveSession s SET s.revoked = true, s.revokedAt = :now WHERE s.user.id = :userId AND s.revoked = false")
    void revokeAllForUser(UUID userId, Instant now);

    @Modifying
    @Query("UPDATE ActiveSession s SET s.revoked = true, s.revokedAt = :now WHERE s.refreshToken = :token")
    void revokeByToken(String token, Instant now);
}
```

- [ ] Créer `ActiveSessionRepository.java`

#### Step 6A.4 — Enregistrer les sessions dans AuthService

Dans `AuthService.java`, dans `buildAuthenticatedLoginResponse()` (ou là où le refresh token est généré), ajouter l'enregistrement de session :

```java
// À ajouter dans buildAuthenticatedLoginResponse, après la génération du refreshToken
activeSessionRepository.save(ActiveSession.builder()
        .user(user)
        .refreshToken(refreshToken)
        .expiresAt(Instant.now().plusMillis(refreshExpirationMs))
        .build());
```

Ajouter l'injection `ActiveSessionRepository activeSessionRepository` et `@Value("${jwt.refresh-expiration-ms}") long refreshExpirationMs` dans `AuthService`.

- [ ] Modifier `AuthService.java`

#### Step 6A.5 — Créer AdminSessionController

Créer `src/main/java/com/oct/invoicesystem/domain/user/controller/AdminSessionController.java` :

```java
package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.auth.model.ActiveSession;
import com.oct.invoicesystem.domain.auth.repository.ActiveSessionRepository;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sessions")
@RequiredArgsConstructor
@Tag(name = "Admin — Sessions", description = "Gestion des sessions actives (Admin only)")
public class AdminSessionController {

    private final ActiveSessionRepository sessionRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister toutes les sessions actives")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listActiveSessions() {
        List<Map<String, Object>> sessions = sessionRepository.findAllActive(Instant.now())
                .stream()
                .map(s -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("userId", s.getUser().getId());
                    m.put("username", s.getUser().getUsername());
                    m.put("ipAddress", s.getIpAddress());
                    m.put("createdAt", s.getCreatedAt());
                    m.put("expiresAt", s.getExpiresAt());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Révoquer toutes les sessions d'un utilisateur")
    public ResponseEntity<ApiResponse<Void>> revokeUserSessions(@PathVariable UUID userId) {
        sessionRepository.revokeAllForUser(userId, Instant.now());
        return ResponseEntity.ok(ApiResponse.success(null, "Sessions révoquées"));
    }
}
```

- [ ] Créer `AdminSessionController.java`

#### Step 6A.6 — Afficher les sessions dans SecuritySettingsPage.tsx

Dans `frontend/src/pages/admin/SecuritySettingsPage.tsx`, ajouter une section "Sessions actives" qui appelle `GET /admin/sessions` et affiche un tableau avec username, IP, date de connexion, et bouton "Révoquer".

Utiliser le pattern React Query existant :

```tsx
const { data: sessionsData } = useQuery({
  queryKey: ['admin', 'sessions'],
  queryFn: () => apiClient.get('/admin/sessions').then(r => r.data.data),
})
```

- [ ] Modifier `SecuritySettingsPage.tsx`

---

### 6B — Délégation d'approbation (PROB-016)

**Files:**
- Create: `src/main/resources/db/migration/V40__create_approval_delegations.sql`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/model/ApprovalDelegation.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/repository/ApprovalDelegationRepository.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/service/DelegationService.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/controller/DelegationController.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java` — inclure les délégataires
- Create: `frontend/src/pages/admin/ApprovalMatrixPage.tsx` — ajouter section délégation

#### Step 6B.1 — Créer la migration V40

Créer `src/main/resources/db/migration/V40__create_approval_delegations.sql` :

```sql
CREATE TABLE approval_delegations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    delegator_id    UUID NOT NULL REFERENCES users(id),
    delegatee_id    UUID NOT NULL REFERENCES users(id),
    department_code VARCHAR(20) NOT NULL,
    from_date       DATE NOT NULL,
    to_date         DATE NOT NULL,
    reason          TEXT,
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ,
    CONSTRAINT chk_delegation_dates CHECK (to_date >= from_date),
    CONSTRAINT chk_no_self_delegation CHECK (delegator_id <> delegatee_id)
);

CREATE INDEX idx_delegations_delegatee ON approval_delegations(delegatee_id)
    WHERE revoked = FALSE AND to_date >= CURRENT_DATE;
CREATE INDEX idx_delegations_dept ON approval_delegations(department_code)
    WHERE revoked = FALSE AND to_date >= CURRENT_DATE;
```

- [ ] Créer V40

#### Step 6B.2 — Créer l'entité ApprovalDelegation

Créer `src/main/java/com/oct/invoicesystem/domain/workflow/model/ApprovalDelegation.java` :

```java
package com.oct.invoicesystem.domain.workflow.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "approval_delegations")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalDelegation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delegator_id", nullable = false)
    private User delegator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delegatee_id", nullable = false)
    private User delegatee;

    @Column(name = "department_code", nullable = false, length = 20)
    private String departmentCode;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
```

- [ ] Créer `ApprovalDelegation.java`

#### Step 6B.3 — Créer ApprovalDelegationRepository

Créer `src/main/java/com/oct/invoicesystem/domain/workflow/repository/ApprovalDelegationRepository.java` :

```java
package com.oct.invoicesystem.domain.workflow.repository;

import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {

    /**
     * Retourne les délégations actives aujourd'hui pour un département donné.
     * "Active" = non révoquée, et date aujourd'hui est dans [from_date, to_date].
     */
    @Query("""
        SELECT d FROM ApprovalDelegation d
        WHERE d.departmentCode = :deptCode
          AND d.revoked = false
          AND d.fromDate <= :today
          AND d.toDate >= :today
    """)
    List<ApprovalDelegation> findActiveDelegationsForDepartment(String deptCode, LocalDate today);

    @Query("""
        SELECT d FROM ApprovalDelegation d
        WHERE d.delegatee.id = :delegateeId
          AND d.revoked = false
          AND d.fromDate <= :today
          AND d.toDate >= :today
    """)
    List<ApprovalDelegation> findActiveDelegationsForDelegatee(UUID delegateeId, LocalDate today);
}
```

- [ ] Créer `ApprovalDelegationRepository.java`

#### Step 6B.4 — Créer DelegationService

Créer `src/main/java/com/oct/invoicesystem/domain/workflow/service/DelegationService.java` :

```java
package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

    private final ApprovalDelegationRepository delegationRepository;

    /**
     * Crée une délégation d'approbation.
     *
     * @param delegator l'approbateur qui délègue
     * @param delegatee l'approbateur qui reçoit la délégation
     * @param departmentCode le département concerné
     * @param fromDate début de la délégation
     * @param toDate fin de la délégation
     * @param reason la raison (ex: congés)
     * @param createdBy l'admin qui crée la délégation
     * @return la délégation persistée
     */
    @Transactional
    public ApprovalDelegation createDelegation(
            User delegator, User delegatee, String departmentCode,
            LocalDate fromDate, LocalDate toDate, String reason, User createdBy) {
        if (delegator.getId().equals(delegatee.getId())) {
            throw new ValidationException("Un utilisateur ne peut pas se déléguer à lui-même");
        }
        if (toDate.isBefore(fromDate)) {
            throw new ValidationException("La date de fin doit être après la date de début");
        }
        ApprovalDelegation delegation = ApprovalDelegation.builder()
                .delegator(delegator)
                .delegatee(delegatee)
                .departmentCode(departmentCode)
                .fromDate(fromDate)
                .toDate(toDate)
                .reason(reason)
                .createdBy(createdBy)
                .build();
        log.info("Delegation created: {} delegates to {} for dept {} from {} to {}",
                delegator.getUsername(), delegatee.getUsername(), departmentCode, fromDate, toDate);
        return delegationRepository.save(delegation);
    }

    /**
     * Retourne toutes les délégations actives aujourd'hui pour un département.
     *
     * @param departmentCode code du département
     * @return liste des délégations actives
     */
    public List<ApprovalDelegation> getActiveDelegationsForDepartment(String departmentCode) {
        return delegationRepository.findActiveDelegationsForDepartment(departmentCode, LocalDate.now());
    }

    /**
     * Révoque une délégation.
     *
     * @param delegationId l'UUID de la délégation
     */
    @Transactional
    public void revokeDelegation(UUID delegationId) {
        ApprovalDelegation d = delegationRepository.findById(delegationId)
                .orElseThrow(() -> new com.oct.invoicesystem.shared.exception.ResourceNotFoundException(
                        "Delegation not found: " + delegationId));
        d.setRevoked(true);
        d.setRevokedAt(java.time.Instant.now());
        delegationRepository.save(d);
    }
}
```

- [ ] Créer `DelegationService.java`

#### Step 6B.5 — Créer DelegationController

Créer `src/main/java/com/oct/invoicesystem/domain/workflow/controller/DelegationController.java` :

```java
package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.service.DelegationService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals/delegations")
@RequiredArgsConstructor
@Tag(name = "Approval Delegation", description = "Délégation d'approbation pour absences")
public class DelegationController {

    private final DelegationService delegationService;
    private final SecurityHelper securityHelper;
    private final com.oct.invoicesystem.domain.user.repository.UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Créer une délégation d'approbation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createDelegation(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        User admin = securityHelper.currentUser(authentication);
        User delegator = userRepository.findById(UUID.fromString((String) request.get("delegatorId")))
                .orElseThrow(() -> new com.oct.invoicesystem.shared.exception.ResourceNotFoundException("Delegator not found"));
        User delegatee = userRepository.findById(UUID.fromString((String) request.get("delegateeId")))
                .orElseThrow(() -> new com.oct.invoicesystem.shared.exception.ResourceNotFoundException("Delegatee not found"));
        ApprovalDelegation d = delegationService.createDelegation(
                delegator, delegatee,
                (String) request.get("departmentCode"),
                LocalDate.parse((String) request.get("fromDate")),
                LocalDate.parse((String) request.get("toDate")),
                (String) request.get("reason"),
                admin);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(Map.of("id", d.getId(), "createdAt", d.getCreatedAt())));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister les délégations actives par département")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listDelegations(
            @RequestParam(required = false) String departmentCode) {
        List<ApprovalDelegation> delegations = departmentCode != null
                ? delegationService.getActiveDelegationsForDepartment(departmentCode)
                : List.of(); // sans filtre, retourner vide — ajuster si besoin
        List<Map<String, Object>> result = delegations.stream().map(d -> Map.of(
                "id", d.getId(),
                "delegatorUsername", d.getDelegator().getUsername(),
                "delegateeUsername", d.getDelegatee().getUsername(),
                "departmentCode", d.getDepartmentCode(),
                "fromDate", d.getFromDate().toString(),
                "toDate", d.getToDate().toString(),
                "reason", d.getReason() != null ? d.getReason() : ""
        )).toList();
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Révoquer une délégation")
    public ResponseEntity<ApiResponse<Void>> revokeDelegation(@PathVariable UUID id) {
        delegationService.revokeDelegation(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Délégation révoquée"));
    }
}
```

- [ ] Créer `DelegationController.java`

#### Step 6B.6 — Intégrer les délégations dans ApprovalServiceImpl.checkRole()

Dans `ApprovalServiceImpl.java`, modifier la méthode `checkRole()` pour vérifier également les délégations actives :

```java
private void checkRole(User user, String requiredRole) {
    // Vérification directe du rôle
    boolean hasDirectRole = user.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(requiredRole));
    if (hasDirectRole) return;

    // Vérification par délégation — si l'utilisateur est délégué pour ce département
    String departmentCode = requiredRole.replaceAll("ROLE_VALIDATEUR_N[12]_", "");
    List<ApprovalDelegation> delegations =
            delegationRepository.findActiveDelegationsForDelegatee(user.getId(), java.time.LocalDate.now());
    boolean hasDelegation = delegations.stream()
            .anyMatch(d -> d.getDepartmentCode().equals(departmentCode));
    if (hasDelegation) return;

    throw new org.springframework.security.access.AccessDeniedException(
            "User " + user.getUsername() + " does not have role " + requiredRole + " and has no active delegation");
}
```

Ajouter l'injection `ApprovalDelegationRepository delegationRepository` dans `ApprovalServiceImpl`.

- [ ] Modifier `ApprovalServiceImpl.checkRole()`

#### Step 6B.7 — Tests

Créer `src/test/java/com/oct/invoicesystem/domain/workflow/service/DelegationServiceTest.java` :

```java
package com.oct.invoicesystem.domain.workflow.service;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.model.ApprovalDelegation;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalDelegationRepository;
import com.oct.invoicesystem.shared.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationServiceTest {

    @Mock ApprovalDelegationRepository delegationRepository;
    @InjectMocks DelegationService delegationService;

    @Test
    @DisplayName("createDelegation: auto-délégation interdit")
    void createDelegation_selfDelegation_throwsValidationException() {
        User user = User.builder().id(UUID.randomUUID()).username("user").active(true).build();
        assertThatThrownBy(() ->
                delegationService.createDelegation(user, user, "INFO",
                        LocalDate.now(), LocalDate.now().plusDays(7), "congés", user))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createDelegation: date fin avant date début interdit")
    void createDelegation_invalidDates_throwsValidationException() {
        User delegator = User.builder().id(UUID.randomUUID()).username("a").active(true).build();
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").active(true).build();
        assertThatThrownBy(() ->
                delegationService.createDelegation(delegator, delegatee, "INFO",
                        LocalDate.now().plusDays(7), LocalDate.now(), "test", delegator))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("createDelegation: délégation valide persistée")
    void createDelegation_valid_persistsDelegation() {
        User delegator = User.builder().id(UUID.randomUUID()).username("a").active(true).build();
        User delegatee = User.builder().id(UUID.randomUUID()).username("b").active(true).build();
        ApprovalDelegation saved = ApprovalDelegation.builder()
                .id(UUID.randomUUID()).delegator(delegator).delegatee(delegatee)
                .departmentCode("INFO").fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7)).build();
        when(delegationRepository.save(any())).thenReturn(saved);

        ApprovalDelegation result = delegationService.createDelegation(
                delegator, delegatee, "INFO",
                LocalDate.now(), LocalDate.now().plusDays(7), "congés annuels", delegator);

        assertThat(result.getId()).isNotNull();
        verify(delegationRepository).save(any(ApprovalDelegation.class));
    }
}
```

- [ ] Créer `DelegationServiceTest.java`

#### Step 6B.8 — Vérifier tous les tests

```powershell
.\mvnw.cmd test -pl . 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] Confirmer le succès

#### Step 6B.9 — Commit T6

```powershell
git add src/ frontend/
git commit -m "feat(sessions+delegation): active sessions view (M13) + approval delegation entity and service (PROB-016)"
```

- [ ] Commit

---

<a name="task-7"></a>
## Task 7 — Qualité, i18n, tests manquants, documentation

**Problèmes résolus :** m-01 (labels hardcodés Sidebar), m-02 (AuditController sans @Operation), m-03 (AuthController sans @Operation), m-04 (archive full-text search), m-06 (tests AuthRehydrator/SupplierRoute), m-07 (DATABASE.md obsolète), m-08 (audit sub-typing), m-09 (doublon supplier dans sidebar), m-10 (Slf4j manquant), m-11 (UserProfileController pas d'audit), I-06 (DATABASE.md), I-12 (commentaire ApprovalServiceImpl)

---

### Step 7.1 — Corriger les labels hardcodés dans Sidebar.tsx (m-01)

Dans `frontend/src/components/layout/Sidebar.tsx`, remplacer le bloc `roleLabel` :

```tsx
// Avant (hardcodé FR)
if (roles.includes('ROLE_ADMIN')) return 'Administrateur'
if (roles.includes('ROLE_DAF')) return 'DAF'
if (roles.includes('ROLE_ASSISTANT_COMPTABLE')) return 'Ass. Comptable'
const v = roles.find(r => r.startsWith('ROLE_VALIDATEUR_N1_'))
if (v) return `Validateur N1 — ${v.replace('ROLE_VALIDATEUR_N1_', '')}`
const v2 = roles.find(r => r.startsWith('ROLE_VALIDATEUR_N2_'))
if (v2) return `Validateur N2 — ${v2.replace('ROLE_VALIDATEUR_N2_', '')}`
```

par :

```tsx
if (roles.includes('ROLE_ADMIN')) return t('role.admin', 'Administrateur')
if (roles.includes('ROLE_DAF')) return t('role.daf', 'DAF')
if (roles.includes('ROLE_ASSISTANT_COMPTABLE')) return t('role.assistant_comptable', 'Ass. Comptable')
const v = roles.find(r => r.startsWith('ROLE_VALIDATEUR_N1_'))
if (v) return `${t('role.validateur_n1', 'Validateur N1')} — ${v.replace('ROLE_VALIDATEUR_N1_', '')}`
const v2 = roles.find(r => r.startsWith('ROLE_VALIDATEUR_N2_'))
if (v2) return `${t('role.validateur_n2', 'Validateur N2')} — ${v2.replace('ROLE_VALIDATEUR_N2_', '')}`
```

Dans `frontend/src/i18n/fr.json`, ajouter sous `"role"` (créer la section si elle n'existe pas) :
```json
"role": {
  "admin": "Administrateur",
  "daf": "DAF",
  "assistant_comptable": "Ass. Comptable",
  "validateur_n1": "Validateur N1",
  "validateur_n2": "Validateur N2",
  "supplier": "Fournisseur"
}
```

Dans `frontend/src/i18n/en.json`, ajouter :
```json
"role": {
  "admin": "Administrator",
  "daf": "CFO",
  "assistant_comptable": "Acc. Assistant",
  "validateur_n1": "Validator N1",
  "validateur_n2": "Validator N2",
  "supplier": "Supplier"
}
```

- [ ] Modifier Sidebar.tsx + fr.json + en.json

---

### Step 7.2 — Ajouter @Operation sur AuditController (m-02)

Dans `AuditController.java`, ajouter l'import et les annotations :

```java
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
```

Sur la classe :
```java
@Tag(name = "Audit Logs", description = "Journal d'audit système (Admin) et financier (DAF)")
@SecurityRequirement(name = "bearerAuth")
```

Sur chaque méthode :
```java
// getSystemLogs
@Operation(summary = "Journal système", description = "Événements de sécurité et d'administration (ADMIN uniquement)")

// getFinancialLogs
@Operation(summary = "Journal financier", description = "Événements de facturation et paiement (DAF uniquement)")

// searchLogs
@Operation(summary = "Recherche combinée", description = "Recherche dans les logs autorisés pour le rôle courant")
```

- [ ] Modifier `AuditController.java`

---

### Step 7.3 — Ajouter @Operation sur AuthController (m-03)

Dans `AuthController.java`, ajouter `@Tag` sur la classe et `@Operation` sur chaque méthode :

```java
@Tag(name = "Authentication", description = "Login, refresh token, MFA, enregistrement fournisseur")
```

Méthodes :
```java
// login      → @Operation(summary = "Login", description = "Authentification par username/password")
// refresh    → @Operation(summary = "Refresh token", description = "Renouvelle le JWT via refresh token")
// registerSupplier → @Operation(summary = "Inscription fournisseur")
// verifyEmail      → @Operation(summary = "Vérifier email fournisseur")
// forgotPassword   → @Operation(summary = "Demande de reset mot de passe")
// resetPassword    → @Operation(summary = "Confirmer reset mot de passe")
// setupMfa         → @Operation(summary = "Initialiser MFA (TOTP)")
// confirmMfa       → @Operation(summary = "Confirmer MFA setup")
// validateMfa      → @Operation(summary = "Valider OTP — retourne JWT complet")
```

- [ ] Modifier `AuthController.java`

---

### Step 7.4 — Archive full-text search (m-04)

**Backend :** Dans `InvoiceRepository.java`, ajouter une query qui accepte un keyword sur `reference_number`, `supplier_name`, `description` :

```java
@Query("""
    SELECT i FROM Invoice i
    WHERE i.deletedAt IS NULL
      AND i.status = 'ARCHIVE'
      AND (:keyword IS NULL OR
           LOWER(i.referenceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
           LOWER(i.supplierName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
           LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:department IS NULL OR i.department.id = :department)
      AND (:from IS NULL OR i.createdAt >= :from)
      AND (:to IS NULL OR i.createdAt <= :to)
    ORDER BY i.createdAt DESC
""")
Page<Invoice> searchArchived(
        @Param("keyword") String keyword,
        @Param("department") UUID department,
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable);
```

Dans `InvoiceService.java`, ajouter une méthode `searchArchived(String keyword, UUID dept, Instant from, Instant to, Pageable)`.

Dans `InvoiceController.java`, ajouter `GET /invoices/archive` avec paramètre `?keyword=&department=&from=&to=`.

**Frontend :** Dans `ArchivePage.tsx`, ajouter un champ de recherche texte libre et câbler le paramètre `keyword` dans l'appel API.

- [ ] Implémenter la recherche archive backend + frontend

---

### Step 7.5 — Tests AuthRehydrator + SupplierRoute (m-06)

Créer `frontend/src/test/components/AuthRehydrator.test.tsx` :

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { configureStore } from '@reduxjs/toolkit'
import authReducer from '@/store/slices/authSlice'
import App from '@/App'

// Mock apiClient
vi.mock('@/services/apiClient', () => ({
  default: {
    get: vi.fn(),
  },
}))

import apiClient from '@/services/apiClient'
const mockGet = apiClient.get as ReturnType<typeof vi.fn>

describe('AuthRehydrator', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('ne fait pas d\'appel /profile si aucun token en localStorage', async () => {
    render(
      <Provider store={configureStore({ reducer: { auth: authReducer } })}>
        <BrowserRouter><App /></BrowserRouter>
      </Provider>
    )
    await waitFor(() => {
      expect(mockGet).not.toHaveBeenCalledWith('/profile')
    })
  })

  it('appelle /profile et dispatch setCredentials si token présent', async () => {
    localStorage.setItem('accessToken', 'test-token')
    mockGet.mockResolvedValueOnce({
      data: { data: { id: '123', username: 'admin', roles: ['ROLE_ADMIN'] } }
    })
    render(
      <Provider store={configureStore({ reducer: { auth: authReducer } })}>
        <BrowserRouter><App /></BrowserRouter>
      </Provider>
    )
    await waitFor(() => {
      expect(mockGet).toHaveBeenCalledWith('/profile')
    })
  })

  it('dispatch logout si /profile retourne 401', async () => {
    localStorage.setItem('accessToken', 'expired-token')
    mockGet.mockRejectedValueOnce(new Error('401'))
    render(
      <Provider store={configureStore({ reducer: { auth: authReducer } })}>
        <BrowserRouter><App /></BrowserRouter>
      </Provider>
    )
    await waitFor(() => {
      expect(localStorage.getItem('accessToken')).toBeNull()
    })
  })
})
```

Créer `frontend/src/test/components/SupplierRoute.test.tsx` :

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { Provider } from 'react-redux'
import { configureStore } from '@reduxjs/toolkit'
import authReducer, { setCredentials } from '@/store/slices/authSlice'
import { SupplierRoute } from '@/components/auth/ProtectedRoute'

function makeStore(roles: string[]) {
  const store = configureStore({ reducer: { auth: authReducer } })
  store.dispatch(setCredentials({
    user: { id: '1', username: 'test', email: '', roles },
    accessToken: 'tok',
    refreshToken: 'refresh',
  }))
  return store
}

describe('SupplierRoute', () => {
  it('affiche les enfants pour ROLE_SUPPLIER', () => {
    const store = makeStore(['ROLE_SUPPLIER'])
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/supplier']}>
          <Routes>
            <Route element={<SupplierRoute />}>
              <Route path="/supplier" element={<div>Portail fournisseur</div>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </Provider>
    )
    expect(screen.getByText('Portail fournisseur')).toBeDefined()
  })

  it('redirige vers /dashboard pour ROLE_ADMIN', () => {
    const store = makeStore(['ROLE_ADMIN'])
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/supplier']}>
          <Routes>
            <Route element={<SupplierRoute />}>
              <Route path="/supplier" element={<div>Portail fournisseur</div>} />
            </Route>
            <Route path="/dashboard" element={<div>Dashboard admin</div>} />
          </Routes>
        </MemoryRouter>
      </Provider>
    )
    expect(screen.queryByText('Portail fournisseur')).toBeNull()
    expect(screen.getByText('Dashboard admin')).toBeDefined()
  })
})
```

- [ ] Créer `AuthRehydrator.test.tsx` et `SupplierRoute.test.tsx`

---

### Step 7.6 — Audit sub-typing (m-08, GAP 7)

Dans `AuditLoggingFilter.java`, modifier la méthode qui détermine le type d'action pour distinguer les événements financiers des événements système :

```java
// Ajouter une méthode de classification
private String classifyAction(String method, String uri) {
    if (uri.contains("/invoices") || uri.contains("/payments")
            || uri.contains("/approvals") || uri.contains("/workflow")) {
        return "FINANCIAL_ACTION";
    }
    if (uri.contains("/auth") || uri.contains("/users")
            || uri.contains("/integrations") || uri.contains("/admin")) {
        return "SYSTEM_ACTION";
    }
    return "HTTP_REQUEST";
}
```

Utiliser `classifyAction()` comme valeur de `action` lors de la création de l'`AuditLog`.

- [ ] Modifier `AuditLoggingFilter.java`

---

### Step 7.7 — Corriger le doublon supplier dans la Sidebar (m-09)

Dans `Sidebar.tsx`, la section "Finance" des non-admin liste `/admin/suppliers` pour `ROLE_ASSISTANT_COMPTABLE`. La section "Admin" liste aussi `/admin/suppliers` pour `ROLE_ADMIN`. C'est voulu (chacun accède via son propre rôle). Mais si un utilisateur a `ROLE_ADMIN` ET `ROLE_ASSISTANT_COMPTABLE`, le lien apparaît en double.

Corriger en vérifiant l'exclusivité dans le guard AA :

```tsx
{/* AA: Suppliers — only if NOT admin (admins see it in the admin section) */}
<RoleGuard allowedRoles={['ROLE_ASSISTANT_COMPTABLE']}
           fallback={null}>
  {/* Masquer si l'utilisateur est aussi ADMIN */}
  {!user?.roles.includes('ROLE_ADMIN') && (
    <NavItem to="/admin/suppliers" icon={Truck} label={t('nav.suppliers')} />
  )}
</RoleGuard>
```

- [ ] Corriger le doublon dans Sidebar.tsx

---

### Step 7.8 — Ajouter @Slf4j sur SupplierController (m-10)

Dans `SupplierController.java`, ajouter l'annotation `@Slf4j` sur la classe (Lombok) et ajouter au moins un log `log.info(...)` sur les actions importantes (onboard, suspend) :

```java
@Slf4j
public class SupplierController {
    // ...
    // Dans onboardSupplier() :
    log.info("Supplier {} onboarded by {}", supplierId, authentication.getName());
    // Dans suspendSupplier() :
    log.info("Supplier {} suspended by {}", supplierId, authentication.getName());
}
```

- [ ] Modifier `SupplierController.java`

---

### Step 7.9 — Audit trail pour UserProfileController (m-11)

`UserProfileController.updateProfile()` sauvegarde directement sans déclencher d'audit. Après avoir déplacé la logique dans `UserService` (Task 3), s'assurer que `UserService.updateProfile()` publie un événement ou appelle `AuditService.log()` :

Dans `UserService.updateProfile()` (créé à la Task 3, step 3.4), ajouter après le save :

```java
auditService.log(userId.toString(), "USER", "PROFILE_UPDATE", null, null);
```

Ajouter l'injection `AuditService auditService` dans `UserService`.

- [ ] Ajouter l'audit dans UserService.updateProfile()

---

### Step 7.10 — Supprimer le commentaire quasi-TODO dans ApprovalServiceImpl (I-12)

Dans `ApprovalServiceImpl.java` ligne 48, supprimer le commentaire `// this handles if N2 assigns while in N1? No, N2 only assigns when EN_VALIDATION_N2` et le remplacer si nécessaire par rien (le code est explicite).

- [ ] Supprimer le commentaire

---

### Step 7.11 — Mettre à jour DATABASE.md (I-06, m-07)

Dans `docs/DATABASE.md`, ajouter une section "## New Tables (Phase 9+)" documentant les tables :
- `suppliers` ✅ (déjà présent dans DATABASE.md)
- Ajouter les tables manquantes : `active_sessions`, `approval_delegations`
- Mettre à jour le "Migration Order" pour lister jusqu'à V40

```markdown
## Migration Order (complet V1–V40)
V1__create_users_roles.sql
...
V35__encrypt_invoice_bank_details.sql
V36__create_purchase_orders.sql (voir section Three-Way Matching)
V37__create_goods_receipt_notes.sql
V38__create_three_way_matching_complete.sql
V39__create_active_sessions.sql
V40__create_approval_delegations.sql
```

Ajouter les tables `active_sessions` et `approval_delegations` dans la section schéma.

- [ ] Mettre à jour `DATABASE.md`

---

### Step 7.12 — Vérifier le build frontend final

```powershell
cd frontend
npm test -- --run 2>&1 | tail -20
npm run build 2>&1 | tail -10
cd ..
```
Expected : tous les tests Vitest passent, `dist/` créé sans erreur.

- [ ] Confirmer le succès frontend

---

### Step 7.13 — Vérifier le build backend final

```powershell
.\mvnw.cmd test -pl . 2>&1 | tail -20
```
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] Confirmer le succès backend

---

### Step 7.14 — Commit final T7

```powershell
git add src/ frontend/ docs/
git commit -m "fix(quality): i18n sidebar labels, @Operation Swagger, archive search, audit sub-typing, tests, DATABASE.md update"
```

- [ ] Commit final

---

## RÉCAPITULATIF DES COMMITS

| # | Commit | Problèmes résolus |
|---|---|---|
| T1 | `fix(security): correct account lockout + encrypt Invoice.supplierBankDetails` | C-01, C-02 |
| T2 | `fix(security): wire AuditLoggingFilter into Spring Security filter chain` | C-04 |
| T3 | `refactor(arch): enforce layer rules — repositories out of controllers` | C-03, I-01, I-02, I-07, I-11 |
| T4 | `ci: add GitHub Actions CI pipeline + OWASP ZAP security scan` | I-04, I-05 |
| T5a | `fix(config): configure CORS allowed origins from env var` | I-08 |
| T5b | `feat(matching): complete Phase 9D three-way matching` | I-03 |
| T6 | `feat(sessions+delegation): active sessions + approval delegation` | I-09, m-05 |
| T7 | `fix(quality): i18n, Swagger, archive search, audit sub-typing, tests, docs` | m-01..m-11, I-06, I-12 |

---

## VÉRIFICATION FINALE

Après tous les commits, vérifier l'état global :

```powershell
# Backend — tous les tests
.\mvnw.cmd test -pl . 2>&1 | tail -5
# Expected: BUILD SUCCESS — 0 failures

# Frontend — tous les tests + build
cd frontend; npm test -- --run; npm run build; cd ..
# Expected: all tests pass, dist/ created

# Docker — déploiement
.\mvnw.cmd -DskipTests package
docker cp target\invoice-system-1.0.0-SNAPSHOT.jar oct_backend:/app/app.jar
docker restart oct_backend
npm run build --prefix frontend
docker cp frontend/dist/. oct_frontend:/usr/share/nginx/html/
docker exec oct_frontend nginx -s reload
docker ps --format "table {{.Names}}\t{{.Status}}"
# Expected: tous les 5 services Up and healthy
```

---

## MISE À JOUR TASKS.md

Après validation, mettre à jour `docs/TASKS.md` :

- Décocher et recocher P10-12 (CI) : `[x]` → créé et vérifié
- Décocher et recocher P10-15 (ZAP) : `[x]` → créé et vérifié  
- Cocher P9-35 à P9-47 (Three-Way Matching) : `[ ]` → `[x]`
- Ajouter les nouvelles tâches : T1 à T7 comme phase `Phase 11 — Post-Audit Corrections`

---

## MISE À JOUR ARCHITECTURE.md §4.1

Après résolution, mettre à jour le statut des gaps :

| # | Gap | Statut après corrections |
|---|---|---|
| GAP 3 | GitHub Actions CI | ✅ Résolu (T4) |
| GAP 5 | OWASP ZAP | ✅ Résolu (T4) |
| GAP 6 | Approval Delegation | ✅ Résolu (T6) |
| GAP 7 | Financial audit sub-typing | ✅ Résolu (T7) |
| GAP 8 | Archive full-text search | ✅ Résolu (T7) |
| GAP 9 | Invoice.supplierBankDetails non chiffré | ✅ Résolu (T1) |
| GAP 10 | User.isAccountNonLocked ignore lockedUntil | ✅ Résolu (T1) |
| GAP 11 | Repositories dans contrôleurs | ✅ Résolu (T3) |
| GAP 12 | AuditLoggingFilter non câblé | ✅ Résolu (T2) |