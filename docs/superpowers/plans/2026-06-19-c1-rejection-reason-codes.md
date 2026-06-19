# C1 — Motifs de rejet prédéfinis — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer le rejet « texte libre » par un motif prédéfini obligatoire (dropdown) + détail libre optionnel, couvrant l'exigence M4 #8.

**Architecture:** Enum backend `RejectionReasonCode` exposée via un endpoint de lecture traduit. Le controller `/reject` compose une chaîne `"[CODE] détail"` stockée dans le champ `rejectionReason` existant (zéro migration, guard et historique inchangés). Le front charge la liste et envoie `{reasonCode, rejectionReason}`.

**Tech Stack:** Spring Boot 3 (MockMvc tests, `@SpringBootTest(classes = TestConfig.class)` + `@ActiveProfiles("test")`), React 18 + TS, react-i18next, @tanstack/react-query, axios (`apiClient`).

## Global Constraints

- Réponses API toujours enveloppées dans `ApiResponse<T>` (`ApiResponse.success(...)`).
- `@PreAuthorize` sur chaque méthode de controller.
- Jamais d'entité JPA exposée : DTO uniquement.
- Bilingue obligatoire : toute chaîne user-facing via i18n, parité FR/EN dans `messages_fr.properties` + `messages_en.properties` (back) et `fr`/`en` (front react-i18next).
- TDD RED→GREEN. Commit par tâche (1 commit = 1 sujet). Message : `type(scope): C1 — description` + ligne `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Commit via fichier `-F` (jamais here-string PowerShell).
- Logger chaque bug rencontré dans `docs/KNOWN_ISSUES_REGISTRY.md` AVANT le commit du fix.
- Build front : `npx vite build` ne typecheck pas → lancer `npx tsc --noEmit` séparément.
- Branche : `fix/a1-cashflow-sqlgrammar`. NE PAS pousser sans accord explicite de l'utilisateur.
- NE PAS toucher : `CLAUDE.md` (modifié), `docs/audit/`, `docs/SCOPE.md`, `docs/REQUIREMENTS-MATRIX.md`, `docs/REMEDIATION_PROMPT.md` (non-suivis volontairement).

---

## File Structure

- **Create** `src/main/java/com/oct/invoicesystem/domain/workflow/model/RejectionReasonCode.java` — enum des 6 motifs.
- **Modify** `src/main/java/com/oct/invoicesystem/domain/workflow/dto/RejectRequest.java` — ajout `reasonCode`, `rejectionReason` devient optionnel.
- **Create** `src/main/java/com/oct/invoicesystem/domain/workflow/dto/RejectionReasonOption.java` — DTO `{code,label}` pour le dropdown.
- **Modify** `src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java` — composition `[CODE] détail` dans `/reject` + nouvel endpoint `GET /rejection-reasons`.
- **Modify** `src/main/resources/i18n/messages_fr.properties` + `messages_en.properties` — libellés motifs + erreur.
- **Modify** `src/test/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalControllerTest.java` — tests back.
- **Modify** `frontend/src/components/invoice/InvoiceActionPanel.tsx` — `<select>` motif + détail conditionnel.
- **Modify** `frontend/src/i18n/locales/fr.json` + `en.json` (chemin réel à confirmer à l'étape front) — libellés motifs.
- **Modify** `frontend/src/test/components/InvoiceActionPanel.test.tsx` — tests front.

---

## Task 1: Enum `RejectionReasonCode` + endpoint de lecture traduit

**Files:**
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/model/RejectionReasonCode.java`
- Create: `src/main/java/com/oct/invoicesystem/domain/workflow/dto/RejectionReasonOption.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java`
- Modify: `src/main/resources/i18n/messages_fr.properties`, `messages_en.properties`
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalControllerTest.java`

**Interfaces:**
- Produces:
  - `enum RejectionReasonCode { MONTANT_INCORRECT, PIECE_MANQUANTE, DOUBLON, INFOS_FOURNISSEUR_INCORRECTES, HORS_BUDGET, AUTRE }`
  - `record RejectionReasonOption(String code, String label)`
  - `GET /api/v1/invoices/{invoiceId}/workflow/rejection-reasons` → `ApiResponse<List<RejectionReasonOption>>` (labels traduits via `Accept-Language`). Note : `{invoiceId}` est présent dans le path à cause du `@RequestMapping` de classe ; il est ignoré (toujours valide, pas de lookup).

- [ ] **Step 1: Écrire le test qui échoue (endpoint liste les 6 motifs traduits FR)**

Dans `ApprovalControllerTest.java`, ajouter (le helper `daf` est déjà provisionné dans `setUp`) :

```java
@Test
void rejectionReasons_returnsTranslatedOptions_fr() throws Exception {
    mockMvc.perform(get("/api/v1/invoices/" + UUID.randomUUID() + "/workflow/rejection-reasons")
                    .header("Accept-Language", "fr")
                    .with(SecurityMockMvcRequestPostProcessors.authentication(
                            new UsernamePasswordAuthenticationToken(daf.getUsername(), null,
                                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DAF"))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(6))
            .andExpect(jsonPath("$.data[?(@.code=='AUTRE')].label").value(org.hamcrest.Matchers.hasItem("Autre")));
}
```

Ajouter les imports manquants en tête de fichier : `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;` et `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;`.

- [ ] **Step 2: Lancer le test, vérifier l'échec**

Run: `./mvnw -q -Dtest=ApprovalControllerTest#rejectionReasons_returnsTranslatedOptions_fr test`
Expected: FAIL (404 / endpoint absent).

- [ ] **Step 3: Créer l'enum**

`RejectionReasonCode.java` :
```java
package com.oct.invoicesystem.domain.workflow.model;

/** Predefined invoice rejection reasons (M4 #8). Labels resolved via MessageSource. */
public enum RejectionReasonCode {
    MONTANT_INCORRECT,
    PIECE_MANQUANTE,
    DOUBLON,
    INFOS_FOURNISSEUR_INCORRECTES,
    HORS_BUDGET,
    AUTRE
}
```

- [ ] **Step 4: Créer le DTO option**

`RejectionReasonOption.java` :
```java
package com.oct.invoicesystem.domain.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A selectable predefined rejection reason")
public record RejectionReasonOption(String code, String label) {}
```

- [ ] **Step 5: Ajouter les libellés i18n (parité FR/EN)**

Dans `messages_fr.properties` :
```
invoice.reject.code.MONTANT_INCORRECT=Montant incorrect
invoice.reject.code.PIECE_MANQUANTE=Pièce justificative manquante
invoice.reject.code.DOUBLON=Facture en doublon
invoice.reject.code.INFOS_FOURNISSEUR_INCORRECTES=Informations fournisseur incorrectes
invoice.reject.code.HORS_BUDGET=Dépense hors budget
invoice.reject.code.AUTRE=Autre
error.reject.detail.required.for.other=Un détail d'au moins 10 caractères est requis lorsque le motif est « Autre ».
error.reject.code.required=Le motif de rejet est obligatoire.
```
Dans `messages_en.properties` (mêmes clés) :
```
invoice.reject.code.MONTANT_INCORRECT=Incorrect amount
invoice.reject.code.PIECE_MANQUANTE=Missing supporting document
invoice.reject.code.DOUBLON=Duplicate invoice
invoice.reject.code.INFOS_FOURNISSEUR_INCORRECTES=Incorrect supplier information
invoice.reject.code.HORS_BUDGET=Out-of-budget expense
invoice.reject.code.AUTRE=Other
error.reject.detail.required.for.other=A detail of at least 10 characters is required when the reason is "Other".
error.reject.code.required=A rejection reason is required.
```

- [ ] **Step 6: Ajouter l'endpoint dans `ApprovalController`**

Injecter `MessageSource` via le constructeur (le controller est `@RequiredArgsConstructor` : ajouter un champ `private final org.springframework.context.MessageSource messageSource;`). Ajouter la méthode :
```java
@GetMapping("/rejection-reasons")
@PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
@Operation(summary = "List predefined rejection reasons", description = "Returns the selectable rejection reason codes with localized labels")
public ResponseEntity<ApiResponse<List<RejectionReasonOption>>> rejectionReasons(
        java.util.Locale locale) {
    List<RejectionReasonOption> options = java.util.Arrays.stream(
                    com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode.values())
            .map(c -> new RejectionReasonOption(
                    c.name(),
                    messageSource.getMessage("invoice.reject.code." + c.name(), null, c.name(), locale)))
            .toList();
    return ResponseEntity.ok(ApiResponse.success(options));
}
```
Ajouter l'import `import com.oct.invoicesystem.domain.workflow.dto.RejectionReasonOption;`.

- [ ] **Step 7: Lancer le test, vérifier le succès**

Run: `./mvnw -q -Dtest=ApprovalControllerTest#rejectionReasons_returnsTranslatedOptions_fr test`
Expected: PASS.

- [ ] **Step 8: Commit**

Écrire le message dans `.git/COMMIT_C1_T1.txt` puis :
```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add src/main/java/com/oct/invoicesystem/domain/workflow/model/RejectionReasonCode.java \
        src/main/java/com/oct/invoicesystem/domain/workflow/dto/RejectionReasonOption.java \
        src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java \
        src/main/resources/i18n/messages_fr.properties src/main/resources/i18n/messages_en.properties \
        src/test/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalControllerTest.java
git commit -F .git/COMMIT_C1_T1.txt && rm -f .git/COMMIT_C1_T1.txt
```
Contenu de `.git/COMMIT_C1_T1.txt` :
```
feat(workflow): C1 — enum RejectionReasonCode + endpoint rejection-reasons

Liste de 6 motifs de rejet prédéfinis, libellés i18n FR/EN, exposés via
GET /workflow/rejection-reasons pour peupler le dropdown front (M4 #8).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

## Task 2: `RejectRequest` + composition `[CODE] détail` dans `/reject`

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/dto/RejectRequest.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalControllerTest.java`

**Interfaces:**
- Consumes: `RejectionReasonCode` (Task 1), `approvalService.reject(UUID, String)` (existant, inchangé).
- Produces: `RejectRequest { RejectionReasonCode reasonCode; String rejectionReason; }`. Chaîne persistée = `"[CODE] détail"` ou `"[CODE]"` si pas de détail.

- [ ] **Step 1: Écrire les tests qui échouent (composition + validation AUTRE + code null)**

Dans `ApprovalControllerTest.java`. Ces tests s'appuient sur un helper qui amène une facture à `EN_VALIDATION_N1` côté DRH ; réutiliser le pattern déjà présent dans la classe pour le test de rejet existant (chercher le test de reject actuel et copier sa mise en situation — même `n1Drh`, même façon d'amener la facture en `EN_VALIDATION_N1`). Squelette :

```java
@Test
void reject_withCodeAndDetail_persistsBracketedReason() throws Exception {
    Invoice inv = anInvoiceInN1Drh(); // helper de mise en situation (voir test reject existant)
    RejectRequest req = RejectRequest.builder()
            .reasonCode(com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode.MONTANT_INCORRECT)
            .rejectionReason("le HT ne correspond pas au BDC")
            .build();
    mockMvc.perform(post("/api/v1/invoices/" + inv.getId() + "/workflow/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req))
                    .with(SecurityMockMvcRequestPostProcessors.authentication(authOf(n1Drh, "ROLE_VALIDATEUR_N1_DRH"))))
            .andExpect(status().isOk());
    String stored = approvalStepRepository.findByInvoiceIdOrderByStepOrderAsc(inv.getId())
            .stream().filter(s -> s.getRejectionReason() != null).findFirst().orElseThrow().getRejectionReason();
    assertEquals("[MONTANT_INCORRECT] le HT ne correspond pas au BDC", stored);
}

@Test
void reject_withOtherCodeAndNoDetail_returns400() throws Exception {
    Invoice inv = anInvoiceInN1Drh();
    RejectRequest req = RejectRequest.builder()
            .reasonCode(com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode.AUTRE)
            .rejectionReason(null)
            .build();
    mockMvc.perform(post("/api/v1/invoices/" + inv.getId() + "/workflow/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req))
                    .with(SecurityMockMvcRequestPostProcessors.authentication(authOf(n1Drh, "ROLE_VALIDATEUR_N1_DRH"))))
            .andExpect(status().isBadRequest());
}

@Test
void reject_withNullCode_returns400() throws Exception {
    Invoice inv = anInvoiceInN1Drh();
    RejectRequest req = RejectRequest.builder().reasonCode(null).rejectionReason("un détail valide ici").build();
    mockMvc.perform(post("/api/v1/invoices/" + inv.getId() + "/workflow/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req))
                    .with(SecurityMockMvcRequestPostProcessors.authentication(authOf(n1Drh, "ROLE_VALIDATEUR_N1_DRH"))))
            .andExpect(status().isBadRequest());
}
```

Note d'implémentation des helpers : si `anInvoiceInN1Drh()` et `authOf(...)` n'existent pas déjà dans la classe, les extraire du test de rejet existant (refactor local) — NE PAS inventer une mécanique différente de mise en situation que celle déjà utilisée par la classe.

- [ ] **Step 2: Lancer les tests, vérifier l'échec**

Run: `./mvnw -q -Dtest=ApprovalControllerTest#reject_withCodeAndDetail_persistsBracketedReason+reject_withOtherCodeAndNoDetail_returns400+reject_withNullCode_returns400 test`
Expected: FAIL (composition absente, validation absente). Le test « code null » peut déjà passer si `@NotNull` ajouté ; sinon il guide l'ajout.

- [ ] **Step 3: Modifier `RejectRequest`**

```java
package com.oct.invoicesystem.domain.workflow.dto;

import com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to reject an invoice")
public class RejectRequest {

    @NotNull(message = "{error.reject.code.required}")
    @Schema(description = "Predefined rejection reason code", example = "MONTANT_INCORRECT")
    private RejectionReasonCode reasonCode;

    @Schema(description = "Optional free-text detail (mandatory, min 10 chars, only when reasonCode = AUTRE)",
            example = "Le HT ne correspond pas au BDC")
    private String rejectionReason;
}
```

- [ ] **Step 4: Modifier la méthode `reject` du controller (validation AUTRE + composition)**

Remplacer le corps de `reject(...)` :
```java
public ResponseEntity<ApiResponse<Void>> reject(
        @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
        @Valid @RequestBody RejectRequest request) {
    String detail = request.getRejectionReason() == null ? "" : request.getRejectionReason().trim();
    if (request.getReasonCode() == com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode.AUTRE
            && detail.length() < 10) {
        throw new com.oct.invoicesystem.shared.exception.ValidationException(
                "A detail of at least 10 characters is required when the reason is \"Other\".");
    }
    String composed = detail.isEmpty()
            ? "[" + request.getReasonCode().name() + "]"
            : "[" + request.getReasonCode().name() + "] " + detail;
    approvalService.reject(invoiceId, composed);
    return ResponseEntity.ok(ApiResponse.success(null, "action.reject.success"));
}
```
Vérifier que `ValidationException` produit bien un 400 via `GlobalExceptionHandler` (c'est le cas dans ce projet ; sinon adapter l'exception au type 400 existant).

- [ ] **Step 5: Lancer les tests, vérifier le succès**

Run: `./mvnw -q -Dtest=ApprovalControllerTest test`
Expected: PASS (toute la classe, y compris l'ancien test de rejet ajusté au nouveau payload — voir Step 6).

- [ ] **Step 6: Ajuster l'ancien test de rejet existant**

L'ancien test de rejet (texte libre) construit un `RejectRequest` sans `reasonCode` → il enverra maintenant 400. Le mettre à jour pour fournir un `reasonCode` (ex. `MONTANT_INCORRECT`) + détail, et adapter son assertion sur la chaîne stockée au format `"[MONTANT_INCORRECT] ..."`. Relancer `./mvnw -q -Dtest=ApprovalControllerTest test` → PASS.

- [ ] **Step 7: Lancer la suite back complète (non-régression)**

Run: `./mvnw -q test`
Expected: 0 failure (baseline ≈ 390 verts + nouveaux tests). Si un autre test construisait un `RejectRequest`, l'ajuster de la même façon.

- [ ] **Step 8: Commit**

Contenu `.git/COMMIT_C1_T2.txt` :
```
feat(workflow): C1 — RejectRequest reasonCode obligatoire + composition [CODE] détail

reasonCode (enum) obligatoire ; détail libre optionnel sauf AUTRE (≥10 car.).
Le controller compose "[CODE] détail" stocké dans rejectionReason existant —
pas de migration, guard/historique inchangés (M4 #8).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add src/main/java/com/oct/invoicesystem/domain/workflow/dto/RejectRequest.java \
        src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java \
        src/test/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalControllerTest.java
git commit -F .git/COMMIT_C1_T2.txt && rm -f .git/COMMIT_C1_T2.txt
```

---

## Task 3: Front — `<select>` motif + détail conditionnel dans `InvoiceActionPanel`

**Files:**
- Modify: `frontend/src/components/invoice/InvoiceActionPanel.tsx`
- Modify: `frontend/src/i18n/locales/fr.json` + `en.json` (confirmer le chemin réel des locales au début de la tâche via recherche)
- Test: `frontend/src/test/components/InvoiceActionPanel.test.tsx`

**Interfaces:**
- Consumes: `GET /workflow/rejection-reasons` → `ApiResponse<{code,label}[]>` (Task 1) ; `POST /workflow/reject` body `{reasonCode, rejectionReason}` (Task 2).
- Produces: modale de rejet avec `<select id="reject-reason-code">` + textarea `#reject-reason-input` conditionnel.

- [ ] **Step 1: Confirmer le chemin des locales front**

Run: rechercher les fichiers de traduction front (ex. `frontend/src/i18n/locales/fr.json` ou clés inline). Utiliser l'emplacement réel pour les clés `invoice.rejectReason.code.*`. Aligner les noms de clés sur la convention déjà utilisée par `InvoiceActionPanel` (`t('invoice.reject', ...)` etc.).

- [ ] **Step 2: Écrire le test front qui échoue**

Dans `InvoiceActionPanel.test.tsx`, ajouter un test qui : rend le panel pour un validateur N1 sur une facture `EN_VALIDATION_N1`, clique « Reject », vérifie qu'un `<select>` `#reject-reason-code` est présent, que `#btn-confirm-reject` est désactivé tant qu'aucun code n'est choisi. Mocker la requête `rejection-reasons` (réutiliser le pattern de mock déjà présent dans ce fichier de test ; s'il n'y en a pas, mocker `apiClient.get` pour renvoyer `{data:{data:[{code:'MONTANT_INCORRECT',label:'Montant incorrect'},{code:'AUTRE',label:'Autre'}]}}`).

- [ ] **Step 3: Lancer le test, vérifier l'échec**

Run: `cd frontend && npx vitest run src/test/components/InvoiceActionPanel.test.tsx`
Expected: FAIL (`#reject-reason-code` absent).

- [ ] **Step 4: Implémenter le select + détail conditionnel**

Dans `InvoiceActionPanel.tsx` :
- Ajouter un state `reasonCode` (`useState('')`).
- Charger les motifs via `useQuery({ queryKey: ['rejection-reasons'], queryFn: () => apiClient.get('/invoices/'+invoice.id+'/workflow/rejection-reasons').then(r => r.data.data) })`, activé seulement quand la modale est ouverte (`enabled: !!pendingAction`).
- Dans la modale, au-dessus du textarea, ajouter :
```tsx
<div>
  <label className="block text-sm text-gray-600 mb-1">
    {t('invoice.rejectReasonCode', 'Rejection reason')} *
  </label>
  <select
    id="reject-reason-code"
    value={reasonCode}
    onChange={(e) => setReasonCode(e.target.value)}
    className="w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-300"
  >
    <option value="">{t('app.select', 'Select...')}</option>
    {(reasons ?? []).map((r: {code:string;label:string}) => (
      <option key={r.code} value={r.code}>{r.label}</option>
    ))}
  </select>
</div>
```
- Le textarea devient « Détail (optionnel) » ; libellé via `t('invoice.rejectReasonDetail', 'Detail (optional)')`. Quand `reasonCode === 'AUTRE'`, ajouter `*` et exiger ≥10 car.
- Bouton confirmer : `disabled` si `!reasonCode || (reasonCode === 'AUTRE' && rejectReason.trim().length < 10) || mutation.isPending`.
- Le payload du `case 'REJECT'` devient `{ reasonCode, rejectionReason: reason }` ; adapter la signature `mutationFn` pour passer `reasonCode`. Réinitialiser `reasonCode` dans `onSuccess` et à l'annulation.

- [ ] **Step 5: Lancer le test, vérifier le succès**

Run: `cd frontend && npx vitest run src/test/components/InvoiceActionPanel.test.tsx`
Expected: PASS.

- [ ] **Step 6: Ajouter les clés i18n front (parité FR/EN)**

Ajouter `invoice.rejectReasonCode`, `invoice.rejectReasonDetail`, `app.select` (si absent) dans les locales FR et EN. Les libellés des motifs eux-mêmes viennent du backend (pas besoin de les dupliquer côté front).

- [ ] **Step 7: Typecheck + build front + parité i18n**

Run: `cd frontend && npx tsc --noEmit && npx vite build`
Expected: 0 erreur TS, build OK. Vérifier la parité des clés FR/EN (même mécanisme que d'habitude sur le projet).

- [ ] **Step 8: Commit**

Contenu `.git/COMMIT_C1_T3.txt` :
```
feat(frontend): C1 — dropdown motif de rejet + détail conditionnel

Modale de rejet : select des motifs (chargés via /workflow/rejection-reasons),
détail optionnel sauf AUTRE (≥10 car.), confirm désactivé sans motif. Payload
{reasonCode, rejectionReason} (M4 #8).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add frontend/src/components/invoice/InvoiceActionPanel.tsx \
        frontend/src/test/components/InvoiceActionPanel.test.tsx \
        frontend/src/i18n
git commit -F .git/COMMIT_C1_T3.txt && rm -f .git/COMMIT_C1_T3.txt
```

---

## Task 4: Basculer M4 #8 en ✅ + clôture C1

**Files:**
- Modify: `docs/COMPLIANCE_MATRIX.md`

- [ ] **Step 1: Mettre à jour la matrice**

Dans `docs/COMPLIANCE_MATRIX.md`, ligne M4 UI #8 : passer 🟠 → ✅, preuve = « dropdown motif prédéfini obligatoire + détail libre (AUTRE → détail ≥10 car.) ; enum `RejectionReasonCode`, endpoint `/workflow/rejection-reasons`, composé `[CODE] détail`. Fait (C1, 2026-06-19) ». Mettre à jour la ligne « Gaps M4 » (retirer #8) et le tableau de synthèse M4 (un 🟠 de moins).

- [ ] **Step 2: Commit**

Contenu `.git/COMMIT_C1_T4.txt` :
```
docs(C1): M4 #8 motif de rejet prédéfini → ✅

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
```bash
cd "c:/Users/Dany/Documents/FINAL PROJECT/invoice-system"
git add docs/COMPLIANCE_MATRIX.md
git commit -F .git/COMMIT_C1_T4.txt && rm -f .git/COMMIT_C1_T4.txt
```

- [ ] **Step 3: Bloc de reprise (cadence projet)**

Émettre un bloc paste-ready résumant : C1 terminé (4 commits), suite back verte, tsc+build verts, prochain item = **C2 (export dédié page Paiements, M7 #11)**. Demander à l'utilisateur s'il valide avant de passer à C2.

---

## Self-Review

**Spec coverage :**
- Liste + détail libre → Task 2 (composition) + Task 3 (UI). ✅
- Enum back + i18n → Task 1. ✅
- Endpoint lecture motifs → Task 1. ✅
- Validation AUTRE dans le controller → Task 2 Step 4. ✅
- Rétrocompat / pas de migration / guard inchangé → garanti par la composition `[CODE] détail` (toujours ≥10 car.), Task 2. ✅
- Parité i18n FR/EN → Task 1 Step 5 (back) + Task 3 Step 6 (front). ✅
- Tests back + front → Tasks 1-3. ✅
- Bascule matrice ✅ → Task 4. ✅

**Placeholder scan :** les helpers front/back (`anInvoiceInN1Drh`, `authOf`, mock `apiClient`) renvoient explicitement au pattern déjà présent dans les fichiers de test concernés, avec instruction de NE PAS inventer une mécanique différente — pas de TODO laissé ouvert.

**Type consistency :** `RejectionReasonCode` (enum), `RejectionReasonOption(code,label)`, `RejectRequest{reasonCode,rejectionReason}`, chaîne `"[CODE] détail"` — noms cohérents entre Tasks 1→2→3. Endpoint `/workflow/rejection-reasons` et `/workflow/reject` cohérents avec le `@RequestMapping` de classe.
