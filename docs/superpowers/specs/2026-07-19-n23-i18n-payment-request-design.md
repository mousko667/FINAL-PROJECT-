# N23 — i18n des messages de validation de PaymentRequest

**Date :** 2026-07-19
**Finding audit :** N23 (🟡 i18n)
**Branche :** `fix/n23-i18n-payment-request`
**PROB :** PROB-128

## Problème

Les 4 contraintes de validation de
`src/main/java/com/oct/invoicesystem/domain/payment/dto/PaymentRequest.java`
portent des messages **littéraux anglais** :

```java
@NotNull(message = "Amount paid is required")     // ligne 11
@Positive(message = "Amount paid must be positive") // ligne 12
@NotNull(message = "Payment method is required")   // ligne 15
@NotNull(message = "Payment date is required")     // ligne 18
```

Ces chaînes contiennent des espaces. Le `GlobalExceptionHandler.resolve()`
(`src/main/java/com/oct/invoicesystem/shared/exception/GlobalExceptionHandler.java:48-53`)
ne traduit une chaîne que si elle matche la regex `I18N_KEY`
(`^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$` — points, **aucun espace**). Une phrase anglaise
avec des espaces ne matche pas → elle est renvoyée **verbatim en anglais**, quel que soit
l'en-tête `Accept-Language`. Violation de la règle CLAUDE.md §3 « Bilingual ».

### Ce qui N'EST PAS le problème (vérifié empiriquement 2026-07-19)

Les ~35 autres occurrences `message = "validation.*"` / `"webhook.*"` dans les DTO
purchasing / webhook / auth sont écrites **sans accolades** mais fonctionnent déjà :
`resolve()` les reconnaît par forme (clé point-séparée sans espace) et les traduit via
`MessageSource`. Elles ne sont donc PAS un bug user-facing → **hors périmètre** de ce fix.
Les 4 messages de statut de `IntegrationConnectorService` sont du diagnostic technique
interne → également hors périmètre (décision user 2026-07-19).

## Solution

### 1. PaymentRequest.java — clés i18n entre accolades

Remplacer les 4 messages littéraux par des clés Bean Validation **entre accolades** :

| Ligne | Avant | Après |
|-------|-------|-------|
| 11 | `"Amount paid is required"` | `"{validation.payment.amount_required}"` |
| 12 | `"Amount paid must be positive"` | `"{validation.payment.amount_positive}"` |
| 15 | `"Payment method is required"` | `"{validation.payment.method_required}"` |
| 18 | `"Payment date is required"` | `"{validation.payment.date_required}"` |

Les accolades sont **nécessaires** : Bean Validation interpole `{...}` depuis le bundle
`messages` **au moment de la construction du message de violation**. Donc
`error.getDefaultMessage()` renverra directement le texte **déjà traduit** selon la locale,
et `resolve()` le laissera passer inchangé (une phrase traduite avec espaces ne matche pas
`I18N_KEY`, ce qui est correct).

### 2. Clés i18n (FR + EN)

Ajouter 4 clés dans **`src/main/resources/i18n/messages_fr.properties`** ET
**`src/main/resources/i18n/messages_en.properties`**. Préfixe `validation.payment.*`
(cohérent avec les `validation.tolerance_*` existants).

**messages_fr.properties** (UTF-8, accents en `\uXXXX` comme le reste du fichier) :
```
validation.payment.amount_required=Le montant payé est obligatoire
validation.payment.amount_positive=Le montant payé doit être positif
validation.payment.method_required=La méthode de paiement est obligatoire
validation.payment.date_required=La date de paiement est obligatoire
```

**messages_en.properties** :
```
validation.payment.amount_required=Amount paid is required
validation.payment.amount_positive=Amount paid must be positive
validation.payment.method_required=Payment method is required
validation.payment.date_required=Payment date is required
```

### 3. Test

Ajouter un test d'intégration (contrôleur paiement) OU un test ciblé sur le
`GlobalExceptionHandler` / la validation, vérifiant qu'une requête paiement invalide :
- avec `Accept-Language: fr` → renvoie le message **français** (ex. contient « obligatoire »),
  et **PAS** la chaîne `Amount paid is required` ;
- avec `Accept-Language: en` → renvoie le message anglais.

Choisir le niveau (intégration MockMvc vs unitaire) selon les patterns de tests paiement
existants dans le projet (à repérer au moment du plan).

## Contraintes / hors périmètre

- **Aucune migration Flyway** (aucun changement de schéma).
- Ne PAS toucher les ~35 clés sans accolades des autres DTO (fonctionnent déjà).
- Ne PAS toucher `IntegrationConnectorService`.
- `messages_fr.properties` = UTF-8, **pas** d'em-dash ni de curly quotes ; accents en `\uXXXX`.

## Definition of Done

- [ ] `PaymentRequest.java` : 4 messages → clés `{validation.payment.*}`.
- [ ] 4 clés ajoutées dans `messages_fr` ET `messages_en` (mêmes clés, même ordre).
- [ ] Test asserte résolution FR (≠ anglais) et EN.
- [ ] Gate backend `./mvnw test` ≥ 624/0/0/0.
- [ ] `docs/KNOWN_ISSUES_REGISTRY.md` : PROB-128 (heredoc, jamais Edit).
- [ ] `docs/QA_AUDIT_EXHAUSTIF.md` : N23 marqué ✅ (non commité, convention).
- [ ] Branche `fix/n23-i18n-payment-request` scopée un seul sujet.
