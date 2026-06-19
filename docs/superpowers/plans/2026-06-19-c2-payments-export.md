# C2 — Export dédié page Paiements (M7 #11) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ajouter un export dédié (CSV/Excel/PDF) de l'historique des paiements sur la page Paiements, comblant le gap M7 #11.

**Architecture:** Reproduction stricte du pattern d'export standard du projet (cf. `UserController.exportUsers`) : un endpoint `GET /api/v1/payments/export?format=...` délègue à un nouveau `PaymentService.exportPayments(...)` qui construit des lignes depuis l'entité `Payment` et appelle le `TabularExportService` partagé. Côté front, un `<ExportMenu>` est ajouté dans l'en-tête « Historique des paiements ».

**Tech Stack:** Spring Boot 3 (JPA, Spring Security, JUnit 5 + MockMvc + Mockito), React 18 + TypeScript (Vitest, Vite), `TabularExportService` (POI + iText), `ExportMenu` (axios blob).

## Global Constraints

- Wrap REST responses in `ApiResponse<T>` SAUF les téléchargements de fichiers, qui renvoient `ResponseEntity<byte[]>` avec `Content-Disposition` (cf. `UserController.exportUsers`).
- `@PreAuthorize` obligatoire sur chaque méthode de contrôleur. Rôles export = `hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')` (alignés sur `listPayments`).
- Jamais d'entité JPA exposée : ici on n'expose que des `byte[]` (fichier), pas de fuite d'entité.
- i18n : le bouton réutilise la clé existante `app.export` (FR + EN déjà présentes). Aucune nouvelle clé.
- Format : `TabularExportService.Format.from(format)` (CSV par défaut si inconnu/null).
- Commits : 1 commit = 1 sujet. `./mvnw test` doit passer avant tout commit backend ; `npx tsc --noEmit` + `npm run build` + `vitest` concerné avant commit front.
- `PaymentServiceImpl` a un **constructeur explicite** (pas `@RequiredArgsConstructor`) — toute nouvelle dépendance doit y être ajoutée manuellement.

---

## File Structure

- `src/main/java/.../payment/service/PaymentService.java` — ajout signature `exportPayments`.
- `src/main/java/.../payment/service/PaymentServiceImpl.java` — implémentation + injection `TabularExportService` (constructeur explicite) + nouvelle dépendance repo non paginée.
- `src/main/java/.../payment/repository/PaymentRepository.java` — variante `findAll`/`findByInvoiceDepartmentCode` non paginée (via `List` ou `Pageable.unpaged()`).
- `src/main/java/.../payment/controller/PaymentController.java` — endpoint `GET /export` + helper `fileResponse`.
- `src/test/java/.../payment/service/PaymentServiceTest.java` — test unitaire `exportPayments`.
- `src/test/java/.../payment/controller/PaymentControllerTest.java` — tests intégration export (200 + filtre + 403).
- `frontend/src/pages/PaymentsPage.tsx` — `<ExportMenu>` dans l'en-tête Historique.

---

### Task 1: Backend — service `exportPayments` (unité + impl)

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceImpl.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/repository/PaymentRepository.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes : `TabularExportService.export(Format, String, List<String>, List<List<String>>)`, `TabularExportService.Format` ; `Payment.getInvoice()` → `Invoice.getReferenceNumber()/getSupplierName()/getCurrency()` ; `Payment.getRecordedBy().getUsername()`.
- Produces : `byte[] PaymentService.exportPayments(String departmentCode, TabularExportService.Format format)`.

- [ ] **Step 1: Écrire le test unitaire qui échoue**

Ajouter dans `PaymentServiceTest` : déclarer les mocks manquants en haut de classe (à côté des `@Mock` existants) puis le test. NOTE : `PaymentServiceImpl` ayant un constructeur explicite, `@InjectMocks` câblera les nouveaux `@Mock` automatiquement par type ; ajouter donc aussi les mocks `TabularExportService` et `ObjectProvider<PaymentService>` requis par le constructeur.

```java
    @Mock
    private com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<PaymentService> selfProvider;

    @Test
    void exportPayments_buildsRowsFromEntities() {
        Invoice inv = new Invoice();
        inv.setReferenceNumber("INV-1");
        inv.setSupplierName("ACME");
        inv.setCurrency("XAF");

        User recorder = new User();
        recorder.setUsername("assistant");

        Payment p = new Payment();
        p.setInvoice(inv);
        p.setAmountPaid(new BigDecimal("1500.00"));
        p.setPaymentMethod(PaymentMethod.VIREMENT);
        p.setReference("PAY-1");
        p.setPaymentDate(Instant.parse("2026-06-01T00:00:00Z"));
        p.setRecordedBy(recorder);

        when(paymentRepository.findAll()).thenReturn(java.util.List.of(p));
        when(tabularExportService.export(
                eq(com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV),
                eq("Payments"),
                anyList(), anyList()))
            .thenReturn("CSV".getBytes());

        byte[] out = paymentService.exportPayments(
                null, com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV);

        assertArrayEquals("CSV".getBytes(), out);

        ArgumentCaptor<java.util.List<java.util.List<String>>> rowsCap = ArgumentCaptor.forClass(java.util.List.class);
        verify(tabularExportService).export(
                eq(com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV),
                eq("Payments"), anyList(), rowsCap.capture());
        java.util.List<String> row = rowsCap.getValue().get(0);
        assertEquals("INV-1", row.get(0));
        assertEquals("ACME", row.get(1));
        assertEquals("VIREMENT", row.get(2));
        assertEquals("PAY-1", row.get(3));
        assertEquals("1500.00", row.get(4));
        assertEquals("XAF", row.get(5));
        assertEquals("assistant", row.get(7));
    }
```

Ajouter les imports nécessaires : `import org.mockito.ArgumentCaptor;` (déjà présent), `import static org.mockito.ArgumentMatchers.anyList;`, `import static org.junit.jupiter.api.Assertions.assertArrayEquals;` (couvert par `assertions.*` déjà importé en wildcard).

- [ ] **Step 2: Lancer le test → échec attendu**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=PaymentServiceTest test`
Expected: échec de COMPILATION — `exportPayments` n'existe pas dans `PaymentService` / constructeur sans `TabularExportService`.

- [ ] **Step 3: Ajouter la signature à l'interface**

Dans `PaymentService.java`, ajouter l'import et la méthode :

```java
import com.oct.invoicesystem.shared.export.TabularExportService;
```
```java
    byte[] exportPayments(String departmentCode, TabularExportService.Format format);
```

- [ ] **Step 4: Ajouter une lecture non paginée au repository**

Dans `PaymentRepository.java`, ajouter (en plus de l'existant `findByInvoiceDepartmentCode(String, Pageable)`) :

```java
    java.util.List<Payment> findByInvoiceDepartmentCode(String departmentCode);
```

(`findAll()` est déjà fourni par `JpaRepository` pour le cas sans filtre.)

- [ ] **Step 5: Implémenter `exportPayments` + injecter `TabularExportService`**

Dans `PaymentServiceImpl.java` :
- ajouter le champ `private final TabularExportService tabularExportService;`
- ajouter le paramètre au **constructeur explicite** et l'assignation correspondante ;
- ajouter l'import `import com.oct.invoicesystem.shared.export.TabularExportService;` et `import java.util.List;` (déjà présent).

```java
    @Override
    @Transactional(readOnly = true)
    public byte[] exportPayments(String departmentCode, TabularExportService.Format format) {
        List<Payment> payments = (departmentCode == null || departmentCode.isBlank())
                ? paymentRepository.findAll()
                : paymentRepository.findByInvoiceDepartmentCode(departmentCode);

        List<String> headers = List.of(
                "Référence facture", "Fournisseur", "Mode de paiement", "Référence paiement",
                "Montant payé", "Devise", "Date de paiement", "Enregistré par");

        List<List<String>> rows = payments.stream().map(p -> {
            Invoice inv = p.getInvoice();
            return List.of(
                    nz(inv == null ? null : inv.getReferenceNumber()),
                    nz(inv == null ? null : inv.getSupplierName()),
                    p.getPaymentMethod() == null ? "" : p.getPaymentMethod().name(),
                    nz(p.getReference()),
                    p.getAmountPaid() == null ? "" : p.getAmountPaid().toPlainString(),
                    nz(inv == null ? null : inv.getCurrency()),
                    p.getPaymentDate() == null ? "" : p.getPaymentDate().toString(),
                    p.getRecordedBy() == null ? "" : nz(p.getRecordedBy().getUsername()));
        }).toList();

        return tabularExportService.export(format, "Payments", headers, rows);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
```

- [ ] **Step 6: Lancer le test → succès attendu**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=PaymentServiceTest test`
Expected: PASS (tous les tests `PaymentServiceTest`, anciens + nouveau).

- [ ] **Step 7: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentService.java \
        src/main/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceImpl.java \
        src/main/java/com/oct/invoicesystem/domain/payment/repository/PaymentRepository.java \
        src/test/java/com/oct/invoicesystem/domain/payment/service/PaymentServiceTest.java
git commit -F- <<'EOF'
feat(payment): exportPayments service (M7 #11)

Build CSV/Excel/PDF rows from Payment entities via the shared
TabularExportService; honours the optional departmentCode filter.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 2: Backend — endpoint `GET /payments/export`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/controller/PaymentController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/payment/controller/PaymentControllerTest.java`

**Interfaces:**
- Consumes : `PaymentService.exportPayments(String, TabularExportService.Format)` (Task 1).
- Produces : `GET /api/v1/payments/export?departmentCode=&format=` → `ResponseEntity<byte[]>` avec `Content-Disposition: attachment; filename=payments.<ext>`.

- [ ] **Step 1: Écrire les tests d'intégration qui échouent**

Le `@BeforeEach` existant crée déjà `assistant` (ASSISTANT_COMPTABLE), `auditeur` (AUDITEUR interdit) et `invoice` en `BON_A_PAYER` dans le département `IT`. Ajouter d'abord un paiement enregistré dans `setUp` n'est pas souhaitable (toucherait les autres tests) → chaque test d'export crée son paiement via l'endpoint d'enregistrement existant.

Ajouter ces imports en haut du fichier :
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import org.springframework.http.HttpHeaders;
```

Ajouter les tests :
```java
    private void recordOnePayment() throws Exception {
        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1500.00"), PaymentMethod.VIREMENT, Instant.now(), "REF-EXP");
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void exportPayments_csv_returnsFileWithRow() throws Exception {
        recordOnePayment();

        mockMvc.perform(get("/api/v1/payments/export").param("format", "csv")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE"))))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("payments.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Référence facture")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INV-12345")));
    }

    @Test
    void exportPayments_departmentFilter_excludesOtherDept() throws Exception {
        recordOnePayment();

        // Le paiement vit dans le département "IT" ; un filtre sur un dept inexistant ne renvoie aucune ligne.
        mockMvc.perform(get("/api/v1/payments/export")
                        .param("format", "csv").param("departmentCode", "NOPE")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE"))))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("INV-12345"))));
    }

    @Test
    void exportPayments_forbiddenForAuditeur() throws Exception {
        mockMvc.perform(get("/api/v1/payments/export").param("format", "csv")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(auditeur, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITEUR"))))))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Lancer les tests → échec attendu**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=PaymentControllerTest test`
Expected: les 3 nouveaux tests échouent — endpoint `/export` inexistant (404 au lieu de 200/403).

- [ ] **Step 3: Implémenter l'endpoint**

Dans `PaymentController.java`, ajouter les imports :
```java
import com.oct.invoicesystem.shared.export.TabularExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
```
Ajouter le champ injecté (le contrôleur utilise `@RequiredArgsConstructor`, donc un `final` suffit) :
```java
    private final TabularExportService tabularExportService;
```
Ajouter la méthode + helper :
```java
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")
    @Operation(summary = "Export payments (csv|excel|pdf)",
            description = "Unified export of the payment history in the requested format")
    public ResponseEntity<byte[]> exportPayments(
            @RequestParam(required = false) String departmentCode,
            @RequestParam(defaultValue = "csv") String format) {
        TabularExportService.Format fmt = TabularExportService.Format.from(format);
        byte[] body = paymentService.exportPayments(departmentCode, fmt);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments." + fmt.extension)
                .contentType(MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }
```

- [ ] **Step 4: Lancer les tests → succès attendu**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q -Dtest=PaymentControllerTest test`
Expected: PASS (anciens + 3 nouveaux).

- [ ] **Step 5: Suite backend complète**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system" && ./mvnw -q test`
Expected: BUILD SUCCESS, 0 failure (aligné sur le baseline 394/0/0 + nouveaux tests).

- [ ] **Step 6: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add src/main/java/com/oct/invoicesystem/domain/payment/controller/PaymentController.java \
        src/test/java/com/oct/invoicesystem/domain/payment/controller/PaymentControllerTest.java
git commit -F- <<'EOF'
feat(payment): GET /payments/export endpoint (M7 #11)

CSV/Excel/PDF download of the payment history, optional departmentCode
filter, restricted to ASSISTANT_COMPTABLE / DAF / ADMIN.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 3: Frontend — `<ExportMenu>` sur la page Paiements

**Files:**
- Modify: `frontend/src/pages/PaymentsPage.tsx`

**Interfaces:**
- Consumes : composant existant `ExportMenu` (`@/components/ui/ExportMenu`) ; endpoint `GET /payments/export?format=...` (Task 2). Note : `apiClient` a pour baseURL `/api/v1`, donc l'`endpoint` passé à `ExportMenu` est `/payments/export` (pas de préfixe — cf. les autres usages `/users/export`, `/invoices/export`).
- Produces : bouton d'export visible dans l'en-tête « Historique des paiements ».

- [ ] **Step 1: Ajouter l'import**

Dans `PaymentsPage.tsx`, après les imports existants :
```tsx
import { ExportMenu } from '@/components/ui/ExportMenu'
```

- [ ] **Step 2: Placer le menu dans l'en-tête Historique**

Remplacer le bloc d'en-tête de la carte « Historique des paiements » :
```tsx
          <div className="flex items-center gap-2 px-5 py-3 border-b">
            <FileText className="w-4 h-4 text-gray-500" />
            <h2 className="font-semibold text-gray-800 text-sm">{t('payments.history', 'Historique des paiements')}</h2>
            {payments && <span className="ml-auto text-xs text-gray-400">{payments.totalElements} {t('payments.total', 'paiements')}</span>}
          </div>
```
par :
```tsx
          <div className="flex items-center gap-2 px-5 py-3 border-b">
            <FileText className="w-4 h-4 text-gray-500" />
            <h2 className="font-semibold text-gray-800 text-sm">{t('payments.history', 'Historique des paiements')}</h2>
            {payments && <span className="text-xs text-gray-400">{payments.totalElements} {t('payments.total', 'paiements')}</span>}
            <div className="ml-auto">
              <ExportMenu endpoint="/payments/export" filename="payments" />
            </div>
          </div>
```
(le compteur perd `ml-auto`, désormais porté par le conteneur de l'`ExportMenu` aligné à droite.)

- [ ] **Step 3: tsc**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend" && npx tsc --noEmit`
Expected: aucune erreur.

- [ ] **Step 4: build vite**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend" && npm run build`
Expected: build réussi.

- [ ] **Step 5: vitest (garde-fou PROB-064)**

Run: `cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system/frontend" && npx vitest run src/pages/PaymentsPage`
Expected : soit PASS, soit « no test files found » (aucun test de page existant) — dans les deux cas, ne pas régresser. Si un fichier de test PaymentsPage existe et casse à cause de l'ajout, le corriger (mock éventuel d'`ExportMenu` non nécessaire — il ne déclenche aucun appel au montage).

- [ ] **Step 6: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/src/pages/PaymentsPage.tsx
git commit -F- <<'EOF'
feat(payment): export menu on Payments page (M7 #11)

Add the shared ExportMenu (CSV/Excel/PDF) to the payment-history card,
wired to GET /payments/export.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

---

### Task 4: Doc — basculer M7 #11 ✅ + clôture

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md`

**Interfaces:**
- Consumes : tâches 1–3 livrées et vertes.
- Produces : matrice à jour ; item C2 clos.

- [ ] **Step 1: Mettre à jour la ligne M7 #11**

Dans `docs/COMPLIANCE_MATRIX.md`, dans le tableau M7, passer la ligne 11 en ✅ avec une note du type :
`export paiement dédié — ExportMenu (CSV/Excel/PDF) + GET /payments/export (C2, 2026-06-19).`

- [ ] **Step 2: Mettre à jour la phrase « Gaps M7 »**

Remplacer `#11 export paiement dédié manquant.` par `~~#11 export paiement dédié manquant~~ **fait (C2, 2026-06-19)**.` (Plus aucun gap M7 ouvert.)

- [ ] **Step 3: Commit**

```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add docs/COMPLIANCE_MATRIX.md
git commit -F- <<'EOF'
docs(c2): M7 #11 ✅ — export paiement dédié livré

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
```

- [ ] **Step 4: Bloc de reprise (cadence projet)**

Émettre un bloc paste-ready résumant : C2 terminé (4 commits : spec + service + endpoint + front + doc), suite backend verte, tsc+build+vitest verts, M7 sans gap restant. Prochain item = **C3 (contrôles zoom/rotate du visualiseur de documents, M9 #4)**. Brancher pas encore poussée ; demander à l'utilisateur s'il valide C2 avant de passer à C3.

---

## Notes / pièges (rappel)

- **Pas de migration Flyway** : C2 n'ajoute aucune colonne (head reste V60).
- **KNOWN_ISSUES_REGISTRY** : ne logger un PROB-NNN que si un bug réel est rencontré pendant l'implémentation (sinon, pas d'entrée — C2 est une feature, pas un fix).
- **i18n** : aucune clé ajoutée → pas de manipulation `messages_fr.properties` (donc pas de piège ISO-8859-1 ici).
- **`./mvnw`** sous PowerShell : utiliser `./mvnw` via le Bash tool (POSIX) comme indiqué dans les commandes, ou `.\mvnw.cmd` en PowerShell.
