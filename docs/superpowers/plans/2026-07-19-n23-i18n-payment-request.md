# N23 — i18n PaymentRequest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faire en sorte que les 4 messages de validation de `PaymentRequest` soient renvoyés dans la langue de l'appelant (FR/EN) au lieu d'être figés en anglais.

**Architecture :** Les messages littéraux anglais des annotations Bean Validation sont remplacés par des clés `{validation.payment.*}` entre accolades. Bean Validation interpole ces clés depuis le bundle `messages` selon `LocaleContextHolder`, donc `error.getDefaultMessage()` renvoie déjà le texte traduit. Aucun changement au `GlobalExceptionHandler`, aucune migration.

**Tech Stack :** Spring Boot 3.4, Jakarta Bean Validation, Spring MessageSource (bundle `i18n/messages_{fr,en}.properties`), JUnit 5 + MockMvc (`@SpringBootTest`, profil `test`).

## Global Constraints

- Devise du système = **XAF** (jamais XOF) — sans objet ici mais règle projet.
- `messages_fr.properties` et `messages_en.properties` vivent sous `src/main/resources/i18n/`.
- `messages_fr.properties` = UTF-8 ; **pas** d'em-dash ni de curly quotes ; accents écrits en `\uXXXX` (cohérent avec l'existant, ex. `enregistré`).
- Les 4 clés doivent exister dans FR **ET** EN, mêmes noms, même ordre.
- Gate backend : `./mvnw test` doit rester ≥ 624/0/0/0 (aucune excuse « pré-existant »).
- `docs/KNOWN_ISSUES_REGISTRY.md` contient un octet NUL → **append via heredoc bash uniquement**, jamais Edit. Prochain PROB = **PROB-128**.
- Une branche = un sujet : tout le travail sur `fix/n23-i18n-payment-request`.

---

### Task 1 : i18n PaymentRequest — clés + traductions + test

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentRequest.java` (lignes 11-18)
- Modify: `src/main/resources/i18n/messages_fr.properties` (ajout de 4 clés)
- Modify: `src/main/resources/i18n/messages_en.properties` (ajout de 4 clés)
- Test: `src/test/java/com/oct/invoicesystem/domain/payment/controller/PaymentControllerTest.java` (ajout de 2 méthodes de test)

**Interfaces:**
- Consumes: `MessageSource` / bundle `i18n/messages_{fr,en}` (mécanisme Bean Validation existant, rien à écrire).
- Produces: 4 clés i18n `validation.payment.amount_required`, `validation.payment.amount_positive`, `validation.payment.method_required`, `validation.payment.date_required` — consommées uniquement par les annotations de `PaymentRequest`.

---

- [ ] **Step 1 : Écrire les tests qui échouent**

Ajouter ces deux méthodes à la fin de la classe `PaymentControllerTest` (avant l'accolade fermante finale, ligne 481). Elles réutilisent l'infrastructure existante (`mockMvc`, `assistant`, `invoice` en `BON_A_PAYER`, imports déjà présents `post`, `status`, `jsonPath`, `MediaType`, `SecurityMockMvcRequestPostProcessors`, `UsernamePasswordAuthenticationToken`).

Le corps JSON omet `amountPaid`, `paymentMethod` et `paymentDate` → déclenche les 3 `@NotNull`. On envoie `{}` (objet vide) : Jackson mappe des `null`, Bean Validation lève `MethodArgumentNotValidException`, `handleValidationException` remplit `$.errors[]` avec `champ: message traduit`.

```java
    // ──────────────────────────────────────────────────────────────────────────
    // N23 (i18n) : les messages de validation de PaymentRequest doivent être
    // renvoyés dans la langue de l'appelant (Accept-Language), pas figés en anglais.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void recordPayment_validationMessages_localizedInFrench() throws Exception {
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .header("Accept-Language", "fr")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                // Le message français doit apparaître ; l'anglais littéral ne doit PAS.
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("obligatoire"))))
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("Payment method is required")))));
    }

    @Test
    void recordPayment_validationMessages_localizedInEnglish() throws Exception {
        mockMvc.perform(post("/api/v1/payments/invoice/" + invoice.getId())
                        .header("Accept-Language", "en")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                new UsernamePasswordAuthenticationToken(assistant, null, java.util.List.of(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ASSISTANT_COMPTABLE")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("is required"))));
    }
```

- [ ] **Step 2 : Lancer les tests pour vérifier qu'ils échouent**

Run: `./mvnw test -Dtest=PaymentControllerTest#recordPayment_validationMessages_localizedInFrench+recordPayment_validationMessages_localizedInEnglish`

Attendu : `recordPayment_validationMessages_localizedInFrench` **ÉCHOUE** — avant le fix, `PaymentRequest` porte `@NotNull(message = "Payment method is required")` (anglais littéral, sans accolades) ; en FR l'appelant reçoit toujours l'anglais → aucun item ne contient « obligatoire », et l'item interdit « Payment method is required » **est** présent. (Le test EN peut passer accidentellement dès maintenant ; c'est le test FR qui pilote le fix.)

- [ ] **Step 3 : Remplacer les 4 messages littéraux par des clés i18n**

Dans `src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentRequest.java`, remplacer le bloc du record (lignes 10-25) pour que les 4 annotations utilisent des clés entre accolades :

```java
public record PaymentRequest(
        @NotNull(message = "{validation.payment.amount_required}")
        @Positive(message = "{validation.payment.amount_positive}")
        BigDecimal amountPaid,

        @NotNull(message = "{validation.payment.method_required}")
        PaymentMethod paymentMethod,

        @NotNull(message = "{validation.payment.date_required}")
        Instant paymentDate,

        String reference,

        /** true = paiement planifie (SCHEDULED) ; absent/false = execute immediatement (PROCESSED). */
        Boolean scheduled
) {}
```

- [ ] **Step 4 : Ajouter les 4 clés dans messages_en.properties**

Ajouter ces 4 lignes dans `src/main/resources/i18n/messages_en.properties` (à la suite des autres clés `validation.*` — repérer un bloc `validation.` existant et insérer juste après ; l'ordre exact dans le fichier n'importe pas fonctionnellement, mais grouper avec les `validation.` est plus propre) :

```
validation.payment.amount_required=Amount paid is required
validation.payment.amount_positive=Amount paid must be positive
validation.payment.method_required=Payment method is required
validation.payment.date_required=Payment date is required
```

Utiliser un heredoc bash pour éviter tout souci d'encodage/quotes (pur ASCII ici) :

```bash
cat >> src/main/resources/i18n/messages_en.properties <<'EOF'
validation.payment.amount_required=Amount paid is required
validation.payment.amount_positive=Amount paid must be positive
validation.payment.method_required=Payment method is required
validation.payment.date_required=Payment date is required
EOF
```

- [ ] **Step 5 : Ajouter les 4 clés dans messages_fr.properties (accents en \uXXXX)**

Les valeurs FR contiennent des accents (`é`). Le fichier étant lu comme un `.properties`, écrire les accents en séquences d'échappement `\uXXXX` (comme le reste du fichier, ex. `enregistré`). Correspondances : `é`=`é`, `à`=`à`, `è`=`è`.

Valeurs cibles (rendu lisible) :
- `validation.payment.amount_required` → « Le montant payé est obligatoire »
- `validation.payment.amount_positive` → « Le montant payé doit être positif »
- `validation.payment.method_required` → « La méthode de paiement est obligatoire »
- `validation.payment.date_required` → « La date de paiement est obligatoire »

Ajouter via heredoc bash (ASCII pur grâce aux `\uXXXX`, donc pas de risque de corruption d'encodage) :

```bash
cat >> src/main/resources/i18n/messages_fr.properties <<'EOF'
validation.payment.amount_required=Le montant payé est obligatoire
validation.payment.amount_positive=Le montant payé doit être positif
validation.payment.method_required=La méthode de paiement est obligatoire
validation.payment.date_required=La date de paiement est obligatoire
EOF
```

(`ê` = `ê` pour « être ».)

- [ ] **Step 6 : Lancer les tests pour vérifier qu'ils passent**

Run: `./mvnw test -Dtest=PaymentControllerTest`

Attendu : **tous** les tests de `PaymentControllerTest` PASSENT (les 2 nouveaux + les ~15 existants). Le test FR renvoie maintenant « ... obligatoire » et non plus l'anglais.

- [ ] **Step 7 : Gate complet**

Run: `rm -rf target/surefire-reports && export DB_NAME=oct_invoice DB_USER=postgres DB_PASSWORD=dany && ./mvnw test`

Attendu : `Tests run: 626, Failures: 0, Errors: 0, Skipped: 0` (624 baseline + 2 nouveaux). Si un autre test casse, investiguer avant de continuer (règle « no failures on task completion »).

- [ ] **Step 8 : Living documentation — PROB-128 (heredoc, jamais Edit)**

```bash
cat >> docs/KNOWN_ISSUES_REGISTRY.md <<'EOF'

## PROB-128 — Messages de validation PaymentRequest figés en anglais (N23)

**Root cause :** `PaymentRequest` déclarait ses 4 messages Bean Validation en anglais
littéral (`@NotNull(message = "Amount paid is required")`, etc.). Contenant des espaces, ces
chaînes ne matchent pas la regex `I18N_KEY` de `GlobalExceptionHandler.resolve()` et sont
renvoyées verbatim, quelle que soit la locale `Accept-Language`.

**Solution :** Remplacer les 4 messages par des clés entre accolades
`{validation.payment.*}` (dto/PaymentRequest.java) + ajouter ces clés dans
`i18n/messages_fr.properties` et `i18n/messages_en.properties`. Bean Validation interpole
les `{...}` depuis le bundle selon la locale, donc le message livré est déjà traduit.

**Preventive rule :** Tout message d'annotation Bean Validation destiné à l'utilisateur DOIT
être une clé i18n entre accolades `{...}` — jamais une phrase en dur. Les clés existantes
écrites sans accolades (ex. `"validation.session_timeout_min"`) fonctionnent seulement parce
que `resolve()` les rattrape par forme ; ne pas s'appuyer là-dessus pour les nouveaux DTO.
EOF
```

- [ ] **Step 9 : Marquer N23 ✅ dans le suivi d'audit (non commité)**

Éditer `docs/QA_AUDIT_EXHAUSTIF.md` : passer la ligne N23 de son état ouvert à ✅ (résolu 2026-07-19, PROB-128). Ce fichier n'est **pas** commité (convention de suivi, non-suivi).

- [ ] **Step 10 : Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentRequest.java \
        src/main/resources/i18n/messages_fr.properties \
        src/main/resources/i18n/messages_en.properties \
        src/test/java/com/oct/invoicesystem/domain/payment/controller/PaymentControllerTest.java \
        docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(payment): N23 — i18n des messages de validation PaymentRequest (PROB-128)"
```

(Ne PAS `git add .` : `docs/QA_*`, `docs/SOD_AUDIT_REPORT.md`, `scratch/` restent non-suivis.)

---

## Self-Review

**Spec coverage :**
- PaymentRequest → clés `{...}` : Step 3 ✅
- 4 clés FR + EN : Steps 4-5 ✅
- Test FR≠anglais + EN : Step 1 ✅
- Gate ≥ 624 : Step 7 ✅ (cible 626)
- PROB-128 heredoc : Step 8 ✅
- N23 ✅ non commité : Step 9 ✅
- Branche un sujet : header + Step 10 ✅

**Placeholder scan :** aucun TBD/TODO ; tout le code (record, clés, tests, heredocs) est fourni littéralement.

**Type consistency :** les 4 clés `validation.payment.{amount_required,amount_positive,method_required,date_required}` sont identiques entre PaymentRequest (Step 3), EN (Step 4), FR (Step 5) et PROB-128 (Step 8). Le test cible « obligatoire » (présent dans 3 des 4 valeurs FR) et « is required » (EN) — cohérent avec les valeurs écrites.
