# N7 — SoD accès référentiel fournisseur (DAF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retirer l'accès en lecture du `ROLE_DAF` à 6 endpoints d'administration du référentiel fournisseur, tout en conservant son accès à `/performance` (analytics financier).

**Architecture:** Modification de 6 annotations `@PreAuthorize` sur deux contrôleurs Spring MVC. Aucune modification de service, de schéma, ni de frontend (déjà conforme). Tests d'intégration MockMvc pour figer la règle SoD (DAF → 403).

**Tech Stack:** Spring Boot 3.4, Spring Security (`@PreAuthorize`, `hasAnyRole`), JUnit 5, MockMvc, `@WithMockUser`.

## Global Constraints

- Devise du système = **XAF** (jamais XOF) — non pertinent ici mais règle projet.
- `@PreAuthorize` obligatoire sur chaque méthode de contrôleur (CLAUDE.md §3) — on modifie, on ne retire pas l'annotation.
- Gate backend : baseline **620/0/0/0** — un task n'est « fait » qu'avec 0 échec.
- Commande de gate : `rm -rf target/surefire-reports && export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany && ./mvnw test`.
- `docs/KNOWN_ISSUES_REGISTRY.md` contient un octet NUL → append via **heredoc bash**, jamais Edit.
- Package racine = `com.oct.invoicesystem` (pas `com.oct.invoice`).
- Une branche = un sujet ; aucun merge/push sans feu vert explicite de l'utilisateur.

---

### Task 1: Retirer ROLE_DAF des 6 endpoints lecture référentiel fournisseur (+ tests SoD)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java` (lignes 75, 81, 95, 171)
- Modify: `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierRelationshipController.java` (lignes 32, 57)
- Test: `src/test/java/com/oct/invoicesystem/domain/supplier/controller/SupplierIntegrationTest.java` (ajout de méthodes de test)

**Interfaces:**
- Consomme : le pattern de test existant du fichier — `@WithMockUser(roles = "…")`, helper privé `createSupplier(String companyName, String taxId, String email)` retournant l'`id` String, helper `cleanupSupplier(String id)`, imports `get`/`status`/`jsonPath` déjà présents.
- Produit : aucun symbole consommé par un autre task (task unique).

**Endpoints concernés (rappel) :**
- `SupplierController` (`@RequestMapping("/api/v1/suppliers")`) : `GET /{id}` (75), `GET /` (81), `GET /export` (95), `GET /{id}/documents` (171).
- `SupplierRelationshipController` (`@RequestMapping("/api/v1/suppliers/{supplierId}")`) : `GET /contracts` (32), `GET /communications` (57).
- **INCHANGÉ** : `GET /{id}/performance` (`SupplierController` ~164, `hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')`).

- [ ] **Step 1: Écrire les tests qui échouent (SoD DAF → 403 + non-régression AA + performance conservé)**

Ajouter ces 4 méthodes dans `SupplierIntegrationTest.java`, juste avant l'accolade fermante finale de la classe (après `shouldPersistAndFilterByCategory`, ligne ~381) :

```java
    // ──────────────────────────────────────────────────────────────────────────
    // N7 (SoD) : le DAF valide/paie mais n'administre PAS le référentiel fournisseur.
    // Il perd l'accès lecture au référentiel (détail, liste, export, documents,
    // contrats, communications) et ne garde que /performance (analytics financier).
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void n7_dafIsForbiddenOnSupplierReferentialReads() throws Exception {
        // Un supplier réel est créé par un ADMIN (helper) puis lu par le DAF → 403 attendu.
        String id = createSupplier("N7 SoD Co", "TAX-N7-001", "n7.sod@example.com");
        try {
            // GET /{id} — détail
            mockMvc.perform(get("/api/v1/suppliers/{id}", id))
                    .andExpect(status().isForbidden());
            // GET / — liste/recherche
            mockMvc.perform(get("/api/v1/suppliers"))
                    .andExpect(status().isForbidden());
            // GET /export
            mockMvc.perform(get("/api/v1/suppliers/export"))
                    .andExpect(status().isForbidden());
            // GET /{id}/documents
            mockMvc.perform(get("/api/v1/suppliers/{id}/documents", id))
                    .andExpect(status().isForbidden());
            // GET /{supplierId}/contracts
            mockMvc.perform(get("/api/v1/suppliers/{supplierId}/contracts", id))
                    .andExpect(status().isForbidden());
            // GET /{supplierId}/communications
            mockMvc.perform(get("/api/v1/suppliers/{supplierId}/communications", id))
                    .andExpect(status().isForbidden());
        } finally {
            cleanupSupplier(id);
        }
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void n7_assistantComptableStillReadsSupplierReferential() throws Exception {
        // Non-régression : l'AA garde l'accès lecture (détail + liste).
        String id = createSupplier("N7 AA Co", "TAX-N7-AA-001", "n7.aa@example.com");
        try {
            mockMvc.perform(get("/api/v1/suppliers/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            mockMvc.perform(get("/api/v1/suppliers").param("taxId", "TAX-N7-AA-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            cleanupSupplier(id);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void n7_adminStillReadsSupplierDetail() throws Exception {
        // Non-régression : l'ADMIN garde l'accès lecture au détail (il administre le référentiel).
        String id = createSupplier("N7 Admin Co", "TAX-N7-ADM-001", "n7.adm@example.com");
        try {
            mockMvc.perform(get("/api/v1/suppliers/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        } finally {
            cleanupSupplier(id);
        }
    }

    @Test
    @WithMockUser(roles = "DAF")
    void n7_dafKeepsPerformanceAccess() throws Exception {
        // /performance reste accessible au DAF : ici supplier sans facture → 404 (pas 403).
        // Le 404 (et non 403) prouve que l'autorisation DAF passe toujours sur /performance.
        String id = createSupplier("N7 Perf Co", "TAX-N7-PERF-001", "n7.perf@example.com");
        try {
            mockMvc.perform(get("/api/v1/suppliers/{id}/performance", id))
                    .andExpect(status().isNotFound());
        } finally {
            cleanupSupplier(id);
        }
    }
```

- [ ] **Step 2: Lancer les nouveaux tests pour vérifier qu'ils échouent au bon endroit**

Run : `export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany && ./mvnw test -Dtest=SupplierIntegrationTest#n7_dafIsForbiddenOnSupplierReferentialReads`

Expected : **FAIL** — le DAF reçoit `200` au lieu de `403` (message du type `Status expected:<403> but was:<200>`), car les `@PreAuthorize` autorisent encore `'DAF'`. (`n7_dafKeepsPerformanceAccess` et les non-régressions AA/ADMIN doivent, elles, déjà passer — c'est normal.)

- [ ] **Step 3: Retirer `'DAF'` des 4 `@PreAuthorize` de `SupplierController.java`**

Dans `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java`, remplacer sur les méthodes `getSupplier` (75), `searchSuppliers` (81), `exportSuppliers` (95), `listSupplierDocuments` (171) :

```java
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
```
par :
```java
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
```

⚠ Ces 4 méthodes portent une chaîne `@PreAuthorize` identique — appliquer le remplacement **uniquement** sur ces 4 méthodes (`getSupplier`, `searchSuppliers`, `exportSuppliers`, `listSupplierDocuments`). **NE PAS** toucher `getPerformanceMetrics` (ligne 164) dont l'annotation est `hasAnyRole('ASSISTANT_COMPTABLE', 'DAF')` (différente, à conserver).

- [ ] **Step 4: Retirer `'DAF'` des 2 `@PreAuthorize` de `SupplierRelationshipController.java`**

Dans `src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierRelationshipController.java`, sur les méthodes `GET /contracts` (32) et `GET /communications` (57), remplacer :

```java
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE', 'DAF')")
```
par :
```java
    @PreAuthorize("hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')")
```

⚠ Ne toucher que les deux `@GetMapping` (contracts/communications). Les `@PostMapping`/`@DeleteMapping` (contrats/comms écriture) sont déjà `hasAnyRole('ADMIN', 'ASSISTANT_COMPTABLE')` → ne pas y toucher.

- [ ] **Step 5: Lancer la classe de test complète pour vérifier que tout passe**

Run : `export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany && ./mvnw test -Dtest=SupplierIntegrationTest`

Expected : **PASS** — toutes les méthodes vertes, y compris les 4 nouvelles (`n7_dafIsForbiddenOnSupplierReferentialReads`, `n7_assistantComptableStillReadsSupplierReferential`, `n7_adminStillReadsSupplierDetail`, `n7_dafKeepsPerformanceAccess`).

- [ ] **Step 6: Lancer le gate backend complet (non-régression globale)**

Run : `rm -rf target/surefire-reports && export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany && ./mvnw test`

Expected : **BUILD SUCCESS**, Tests run: **≥ 624**, Failures: 0, Errors: 0, Skipped: 0 (baseline 620 + 4 nouveaux). Si un test préexistant asseyait un accès DAF 200 sur l'un des 6 endpoints, le corriger en 403 (voir spec §5) puis relancer.

- [ ] **Step 7: Living-doc — journaliser PROB-123 dans KNOWN_ISSUES_REGISTRY.md (heredoc bash)**

⚠ NE PAS utiliser Edit (octet NUL dans le fichier). Append via heredoc bash :

```bash
cat >> docs/KNOWN_ISSUES_REGISTRY.md <<'EOF'

## PROB-123 — Le DAF accedait en lecture au referentiel fournisseur (SoD)

- **Date** : 2026-07-18
- **Finding** : N7 (audit exhaustif 2026-07-17).
- **Cause racine** : les @PreAuthorize backend des lectures du referentiel fournisseur
  autorisaient ROLE_DAF (detail, liste, export, documents, contrats, communications),
  alors que le frontend (pages PageRoleGuard AA-only + menu AA-only) et la regle SoD
  l'excluaient deja. Le garde-fou reel (backend) etait plus permissif que l'UI.
- **Separation des taches** : le DAF valide et paie les factures ; il n'administre pas
  le referentiel fournisseur (administre par ADMIN + ASSISTANT_COMPTABLE).
- **Solution** : retrait de 'DAF' de 6 @PreAuthorize :
  - SupplierController : getSupplier, searchSuppliers, exportSuppliers, listSupplierDocuments.
  - SupplierRelationshipController : GET /contracts, GET /communications.
  GET /performance CONSERVE pour le DAF (analytics financier, Module 11).
- **Regle preventive** : toute surface du referentiel fournisseur exclut le DAF cote
  backend ET frontend. Le DAF ne garde que l'analytics /performance. Verifier la
  coherence back/front des @PreAuthorize a chaque ajout d'endpoint fournisseur.
EOF
```

- [ ] **Step 8: Living-doc — marquer N7 ✅ dans QA_AUDIT_EXHAUSTIF.md (NON commité)**

Dans `docs/QA_AUDIT_EXHAUSTIF.md`, sur la ligne du tableau récap (`| N7 | 🟠 | SoD | …`), passer `🟠` → `✅` et compléter la colonne verdict avec « CORRIGÉ 2026-07-18 (fix/n7-sod-supplier) ». Puis, dans la section détaillée `### N7 …`, ajouter en tête un bloc « ✅ CORRIGÉ » résumant le retrait des 6 @PreAuthorize et la conservation de /performance. **Ce fichier n'est PAS commité** (fichier de suivi non suivi par git).

- [ ] **Step 9: Commit (code + tests + KNOWN_ISSUES, PAS QA_AUDIT)**

```bash
git add src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierController.java \
        src/main/java/com/oct/invoicesystem/domain/supplier/controller/SupplierRelationshipController.java \
        src/test/java/com/oct/invoicesystem/domain/supplier/controller/SupplierIntegrationTest.java \
        docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(supplier): N7 — retirer l'acces DAF au referentiel fournisseur (SoD)

Retrait de ROLE_DAF sur 6 endpoints lecture (detail, liste, export,
documents, contrats, communications). GET /performance conserve
(analytics financier DAF). Frontend deja conforme (AA-only). PROB-123.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

- [ ] **Step 10: Vérification runtime SoD (API + comptes réels)**

Prérequis : conteneurs up (`docker start oct_mailhog oct_minio oct_frontend` si besoin) et backend redéployé avec le nouveau jar :
`./mvnw.cmd -q -DskipTests package && docker cp target/invoice-system-1.0.0-SNAPSHOT.jar oct_backend:/app/app.jar && docker restart oct_backend`.

Puis (rate-limit login 5/min/IP → obtenir le JWT DAF une seule fois et le réutiliser) :

```bash
JWT=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"daf","password":"Test1234!"}' | \
  python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# Attendu : 403 (le DAF n'administre plus le referentiel)
curl -s -o /dev/null -w "suppliers list -> %{http_code}\n" \
  -H "Authorization: Bearer $JWT" http://localhost:8080/api/v1/suppliers

# Attendu : 200 (ou 404 si le supplier n'a pas de facture) — surtout PAS 403
curl -s -o /dev/null -w "performance -> %{http_code}\n" \
  -H "Authorization: Bearer $JWT" \
  http://localhost:8080/api/v1/suppliers/<UN_SUPPLIER_ID>/performance
```

Expected : `suppliers list -> 403` et `performance -> 200` (ou `404`, jamais `403`). Si `login` renvoie `mfa_required:true` (le DAF a la MFA obligatoire), suivre le second appel OTP ; sinon utiliser un compte de test sans MFA active pour cette vérif et le noter.

- [ ] **Step 11: Finaliser la branche**

Ne rien merger/pousser. Résumer à l'utilisateur : commit(s), résultat du gate, résultat de la vérif runtime, et demander le feu vert pour merge/push (l'utilisateur pousse lui-même).

---

## Self-Review

**Spec coverage :**
- Spec §3 (6 @PreAuthorize) → Steps 3-4. ✅
- Spec §4 (/performance inchangé + écriture intacte) → garde-fou explicite dans Steps 3-4 + test `n7_dafKeepsPerformanceAccess`. ✅
- Spec §5 (tests DAF 403 / 200 performance / non-régression AA+ADMIN) → Step 1 (4 méthodes). ✅
- Spec §6 (PROB-123 heredoc, N7 ✅ QA non commité, gate 620+, vérif runtime, pas de push) → Steps 7-11. ✅

**Placeholder scan :** `<UN_SUPPLIER_ID>` en Step 10 est un vrai placeholder d'exécution runtime (l'ID dépend de la base au moment de la vérif) — laissé volontairement, documenté. Aucun autre TBD/TODO. ✅

**Type consistency :** helpers `createSupplier(String,String,String)` → `String id` et `cleanupSupplier(String)` utilisés exactement comme définis dans le fichier existant (lignes 231-249). Imports `get`/`status`/`jsonPath`/`param` déjà présents. ✅
