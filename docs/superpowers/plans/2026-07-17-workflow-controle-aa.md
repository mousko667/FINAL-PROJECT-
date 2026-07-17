# SoD compte DAF (V47) + etape de controle AA — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retirer le cumul de roles AA+DAF du compte `daf` (avec garde anti-cumul en base et second compte AA de repli), puis inserer un etat obligatoire `EN_CONTROLE_AA` entre `SOUMIS` et `EN_VALIDATION_N1`.

**Architecture:** La migration V47 traite la separation des taches (donnees + contrainte). Ensuite, la transition `SOUMIS -> EN_VALIDATION_N1` de la state machine est **remplacee** (pas doublee) par `SOUMIS -> EN_CONTROLE_AA -> EN_VALIDATION_N1`, ce qui rend le controle de l'assistant comptable obligatoire. Le rejet AA reutilise `RejectionReasonGuard` et le `RESUBMIT` existant.

**Tech Stack:** Spring Boot 3.4 / Java 21 / Spring State Machine / PostgreSQL 18 (port 5433) / Flyway / React 19 + TS / Vitest

**Spec:** `docs/superpowers/specs/2026-07-17-workflow-controle-aa-design.md`

## Global Constraints

- **Repertoire git :** `c:\Users\Dany\Documents\FINAL PROJECT\invoice-system`
- **Frontend sous `frontend/src/**`** (PAS `src/` = backend Java)
- **Gate obligatoire avant CHAQUE commit :** `./mvnw test` a **0 failure** (regle « no failures on task completion »). Frontend : `npx tsc --noEmit` + `npx vitest run`.
- **Un commit par tache.** Message en francais, sans accents dans le sujet.
- **`messages_fr.properties` est ISO-8859-1 / pur-ASCII `\uXXXX`** : ajouter des cles UNIQUEMENT via script Python binaire (`open(path,"ab")`), jamais Edit/Write (PROB-107). Les heredocs bash mangent les `\u` -> ecrire le script dans un fichier puis l'executer.
- **`docs/KNOWN_ISSUES_REGISTRY.md` contient des octets NUL** -> append via heredoc bash, jamais Edit. Prochain numero libre : **PROB-115**.
- **Flyway est immuable** : ne JAMAIS modifier une migration deja appliquee. V46 est la derniere -> **V47 est libre**.
- **psql :** `/c/Program Files/PostgreSQL/18/bin/psql.exe` (pas dans le PATH), connexion `postgresql://postgres:dany@localhost:5433/oct_invoice`
- **Rate-limit login : 5 req/min/IP** -> espacer les logins, reutiliser les JWT.
- **Devise du systeme = XAF** (jamais XOF).
- **Ne PAS commiter** : `docs/QA_FINAL_*`, `docs/QA_SPEC_VS_REEL_REPORT.md`, `docs/SOD_AUDIT_REPORT.md`, `scratch/`.

---

## File Structure

| Fichier | Responsabilite | Tache |
|---|---|---|
| `src/main/resources/db/migration/V47__enforce_sod_aa_daf.sql` | Retrait role AA du `daf`, seed `aa2`, contrainte anti-cumul | 1 |
| `src/test/java/.../domain/user/SodRoleConstraintTest.java` | Verifie que la base refuse le cumul AA+DAF | 1 |
| `src/main/java/.../domain/invoice/model/InvoiceStatus.java` | +`EN_CONTROLE_AA` | 2 |
| `src/main/java/.../domain/invoice/statemachine/InvoiceEvent.java` | +`ASSIGN_AA` | 2 |
| `src/main/java/.../config/StateMachineConfig.java` | Transitions remplacees | 2 |
| `src/main/java/.../domain/workflow/guard/RoleMatchGuard.java` | Garde `ASSIGN_AA` -> role AA | 2 |
| `src/main/java/.../domain/workflow/service/ApprovalServiceImpl.java` | `assignAA()`, bascule de `assignReviewer` | 3 |
| `src/main/java/.../domain/workflow/service/ApprovalService.java` | Signature `assignAA` | 3 |
| `src/main/java/.../domain/workflow/controller/ApprovalController.java` | `POST /assign-aa` | 3 |
| `src/main/resources/messages.properties` / `messages_fr.properties` | Cles `error.approval.*` | 3 |
| `frontend/src/types/invoice.ts` | Type du nouvel etat | 4 |
| `frontend/src/components/ui/StatusBadge.tsx` | Badge du nouvel etat | 4 |
| `frontend/src/components/invoice/InvoiceActionPanel.tsx` | Actions AA | 4 |
| `frontend/src/pages/{ApprovalQueue,Dashboard,InvoiceDetail,InvoiceList}Page.tsx`, `pages/supplier/SupplierInvoicesPage.tsx` | Filtres/affichage | 4 |

---

## Task 1: V47 — separation des taches AA/DAF

**Files:**
- Create: `src/main/resources/db/migration/V47__enforce_sod_aa_daf.sql`
- Create: `src/test/java/com/oct/invoicesystem/domain/user/SodRoleConstraintTest.java`
- Modify: `docs/KNOWN_ISSUES_REGISTRY.md` (append PROB-115)

**Interfaces:**
- Consumes: rien (premiere tache)
- Produces: compte `daf` sans `ROLE_ASSISTANT_COMPTABLE` ; compte `aa2` (AA de repli) ; trigger `trg_enforce_sod_aa_daf` rejetant le cumul avec le message `SoD violation: user cannot hold both ROLE_ASSISTANT_COMPTABLE and ROLE_DAF`

**Contexte verifie (ne pas re-deriver) :**
- `user_roles` = `(user_id uuid NOT NULL, role_id uuid NOT NULL, assigned_at timestamptz NOT NULL DEFAULT now(), assigned_by uuid NULL)`, PK `(user_id, role_id)`
- Hash BCrypt de `Test1234!` (copie du compte `aa`) : `$2b$12$FFscGrU53UfITyv/j1yDS.hptsmXAPJ7dLKZuNsjjRu/qK4mOXF.e`
- Convention compte `aa` : email `aa@oct.local`, first_name `Alice`, last_name `Assistante`
- Roles `ROLE_ASSISTANT_COMPTABLE` et `ROLE_DAF` existent dans `roles`

### AMENDEMENT (2026-07-17) — le test SoD tourne sous Testcontainers, PAS sous H2

**Le probleme (verifie) :** le profil `test` tourne sur **H2 en memoire**
(`src/test/resources/application-test.yml` : `jdbc:h2:mem:testdb;MODE=PostgreSQL`) et surtout
**`spring.flyway.enabled: false`** (l.30) — le schema de test est genere par Hibernate
`ddl-auto: create-drop`, aucune migration n'est jouee.

Consequences, dans les deux sens :
1. **V47 ne cassera PAS les autres tests** : Flyway etant desactive sous H2, la migration
   (et son trigger PL/pgSQL, que H2 ne sait pas executer) n'y est jamais jouee. Il n'y a donc
   **rien a neutraliser** dans le profil H2 des autres tests.
2. **Mais un test SoD sous `@ActiveProfiles("test")` serait faux** : ni le trigger, ni le seed
   `aa2`, ni le retrait du role sur `daf` n'existeraient jamais dans H2. Il echouerait toujours.

**Decision user (2026-07-17) : Testcontainers + Flyway ACTIVE, dans le gate normal.**
C'est la seule option qui teste le trigger **tel qu'il sera livre**.

**Ne PAS etendre `AbstractPostgresIntegrationTest`** (`src/test/java/.../support/`) : ce patron
pointe sur la base de dev `localhost:5433/oct_invoice` avec **Flyway desactive**, et sa Javadoc
impose des tests **read-only** — or le test SoD doit faire un `INSERT` qui viole le trigger.
Il est inadapte ici.

`SodRoleConstraintTest` sera le **premier test Testcontainers du projet** : pas de patron interne
a copier. Verifie : les artefacts sont **deja dans `pom.xml`** (`testcontainers.version=1.20.4`,
`junit-jupiter` + `postgresql`, scope test) -> **aucune dependance a ajouter** ; Docker tourne
(29.1.2). Le conteneur rejoue V1..V47 (~1-2 min ajoutees au gate : accepte).

### AMENDEMENT 2 (2026-07-17) — le test SoD est finalement HORS GATE (PROB-115)

**L'amendement ci-dessus n'a pas pu etre applique tel quel : Testcontainers ne demarre aucun
conteneur sur cette machine.** Docker Desktop 29 renvoie une enveloppe `/info` vide (HTTP 400) a
tout client HTTP tiers, alors que le CLI Docker fonctionne. Diagnostic etabli en changeant 5
variables sans jamais changer la reponse (identique au caractere pres) : npipe ET tcp:2375 ;
docker-java 3.4.0 ET 3.4.2 (Testcontainers 1.20.4 ET 1.21.3, la derniere publiee) ;
`DOCKER_API_VERSION` 1.44 ET 1.52. La piste "config Testcontainers" est **epuisee** — ne pas la
re-explorer.

**Decision du porteur du projet : OPTION D — le test sort du gate**, la retrogradation de Docker
etant ecartee (risque sur les 5 conteneurs OCT a 3 semaines de la soutenance, pour un seul test).

- Mecanisme : `@Tag("requires-docker")` sur la classe + `<excludedGroups>requires-docker</excludedGroups>`
  dans la config surefire du `pom.xml`. **Exclusion explicite**, PAS d'`assumeTrue` silencieux, PAS
  de repli sur H2 (un test qui se skippe tout seul en vert masquerait le trigger qu'il verifie).
- Le bump `testcontainers.version` 1.20.4 -> **1.21.3** est conserve (scope test, sans regression).
- **Commande exacte pour relancer le test** (il reste au depot et executable a la demande) :

```bash
./mvnw test -Prequires-docker -Dtest=SodRoleConstraintTest
```

- **Verification de V47** : elle se fait en **runtime a la Task 5** sur la base de dev reelle —
  plus probant qu'un conteneur vierge, car c'est la que le cumul AA+DAF existe vraiment et donc
  que le `DELETE` a un effet observable.
- **Le cumul AA+DAF n'est dans AUCUNE migration** (`V34:156` ne donne que `role_daf`) : c'est une
  derive manuelle de la base de dev. Le test le reproduit donc artificiellement (trigger desactive
  le temps de l'INSERT) pour que l'assertion de nettoyage soit falsifiable — le rouge attendu au
  Step 2 ci-dessous est donc **inobservable tel qu'ecrit**.

- [ ] **Step 1: Ecrire le test qui echoue**

Creer `src/test/java/com/oct/invoicesystem/domain/user/SodRoleConstraintTest.java`.

Le squelette ci-dessous est une **intention** : ecrire le test complet et compilable.
Points non negociables — `@Container` + `PostgreSQLContainer`, `spring.flyway.enabled=true`,
`ddl-auto=none` (c'est Flyway qui construit le schema, pas Hibernate), et **pas**
d'`@ActiveProfiles("test")` sur le datasource (le `@DynamicPropertySource` doit gagner ; si le
profil `test` est necessaire pour les autres proprietes — cles JWT, AES, MinIO — le garder mais
verifier que le datasource et Flyway sont bien surcharges).

```java
package com.oct.invoicesystem.domain.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie la separation des taches AA/DAF telle que LIVREE par la migration V47.
 *
 * <p>Tourne sur un PostgreSQL jetable (Testcontainers) avec Flyway ACTIVE, et non sur le H2
 * du profil {@code test} : H2 ne sait pas executer le trigger PL/pgSQL de V47, et le profil
 * de test desactive Flyway ({@code application-test.yml}), donc ni le trigger ni le seed
 * {@code aa2} n'y existeraient. Le conteneur rejoue toutes les migrations, ce qui teste la
 * migration reelle et permet l'INSERT destructif sans toucher la base de developpement.</p>
 */
@Testcontainers
@SpringBootTest
class SodRoleConstraintTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void dafAccountMustNotHoldAssistantComptableRole() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles ur "
                        + "JOIN users u ON u.id = ur.user_id "
                        + "JOIN roles r ON r.id = ur.role_id "
                        + "WHERE u.username = 'daf' AND r.name = 'ROLE_ASSISTANT_COMPTABLE'",
                Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void fallbackAssistantComptableAccountExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles ur "
                        + "JOIN users u ON u.id = ur.user_id "
                        + "JOIN roles r ON r.id = ur.role_id "
                        + "WHERE u.username = 'aa2' AND r.name = 'ROLE_ASSISTANT_COMPTABLE'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void databaseRejectsAaDafRoleCumulation() {
        UUID userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'aa2'", UUID.class);
        UUID dafRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE name = 'ROLE_DAF'", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, dafRoleId))
                .hasMessageContaining("SoD violation");
    }
}
```

> **Si le conteneur ne demarre pas** (image absente, Docker down) : ne PAS rabattre le test sur
> H2 et ne PAS le neutraliser par un `assumeTrue` silencieux — ce serait rendre le trigger
> non teste. Remonter le blocage.

> **Si les migrations echouent sur le conteneur vierge** (une migration anterieure supposant un
> etat de la base de dev) : c'est un vrai defaut a remonter, pas a contourner en desactivant
> Flyway.

- [ ] **Step 2: Lancer le test pour verifier qu'il echoue**

Run: `./mvnw test -Dtest=SodRoleConstraintTest`
Expected: FAIL — `dafAccountMustNotHoldAssistantComptableRole` attend 0 mais trouve 1 ;
`fallbackAssistantComptableAccountExists` attend 1 mais trouve 0 ;
`databaseRejectsAaDafRoleCumulation` ne leve rien (l'INSERT passe).
Le conteneur doit demarrer et V1..V46 s'appliquer : si le test echoue au demarrage du contexte
plutot que sur les assertions, c'est un probleme d'infra, pas le rouge attendu.

- [ ] **Step 3: Ecrire la migration V47**

Creer `src/main/resources/db/migration/V47__enforce_sod_aa_daf.sql` :

```sql
-- V47: Enforce separation of duties (SoD) between ROLE_ASSISTANT_COMPTABLE and ROLE_DAF.
--
-- Why: the `daf` test account held BOTH ROLE_ASSISTANT_COMPTABLE and ROLE_DAF (verified in
-- the running database: it was the only account cumulating two business roles). That breaks
-- separation of duties — the DAF could approve (bon a payer) invoices they entered themselves.
--
-- Three data-only + constraint operations:
--   1. Remove ROLE_ASSISTANT_COMPTABLE from the `daf` account.
--   2. Seed a second assistant comptable (`aa2`) as a fallback: once `daf` loses the AA role,
--      `aa` would be the ONLY AA in the system, and the AA control step (EN_CONTROLE_AA) is a
--      mandatory pass-through. Approval delegation CANNOT cover the AA: approval_delegations
--      is keyed on department_code NOT NULL and the AA is transverse (no department), so
--      ApprovalServiceImpl.checkRole would look for a department named ASSISTANT_COMPTABLE
--      that does not exist. A second account is the only viable fallback.
--   3. Add a trigger rejecting any future AA+DAF cumulation. It lives in the database rather
--      than in Java because there is no central UserServiceImpl: role writes go through User,
--      UserMapper and UserCsvService (CSV import), so an application-side guard on a single
--      path would be bypassed by the importer.
--
-- Idempotent: safe to re-run.

-- 1. Remove the AA role from the DAF account
DELETE FROM user_roles ur
USING users u, roles r
WHERE ur.user_id = u.id
  AND ur.role_id = r.id
  AND u.username = 'daf'
  AND r.name = 'ROLE_ASSISTANT_COMPTABLE';

-- 2. Seed the fallback assistant comptable account (password: Test1234!, same BCrypt hash
--    as the existing `aa` test account)
INSERT INTO users (username, email, password_hash, first_name, last_name, preferred_lang, is_active)
SELECT 'aa2', 'aa2@oct.local',
       '$2b$12$FFscGrU53UfITyv/j1yDS.hptsmXAPJ7dLKZuNsjjRu/qK4mOXF.e',
       'Bernard', 'Comptable', 'fr', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'aa2');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.username = 'aa2'
  AND r.name = 'ROLE_ASSISTANT_COMPTABLE'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

-- 3. Prevent any future AA+DAF cumulation, on every write path
CREATE OR REPLACE FUNCTION enforce_sod_aa_daf() RETURNS TRIGGER AS $$
DECLARE
    incoming_role TEXT;
    conflicting   TEXT;
BEGIN
    SELECT name INTO incoming_role FROM roles WHERE id = NEW.role_id;

    IF incoming_role = 'ROLE_ASSISTANT_COMPTABLE' THEN
        conflicting := 'ROLE_DAF';
    ELSIF incoming_role = 'ROLE_DAF' THEN
        conflicting := 'ROLE_ASSISTANT_COMPTABLE';
    ELSE
        RETURN NEW;
    END IF;

    IF EXISTS (
        SELECT 1 FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        WHERE ur.user_id = NEW.user_id
          AND r.name = conflicting
    ) THEN
        RAISE EXCEPTION 'SoD violation: user cannot hold both ROLE_ASSISTANT_COMPTABLE and ROLE_DAF';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_enforce_sod_aa_daf ON user_roles;
CREATE TRIGGER trg_enforce_sod_aa_daf
    BEFORE INSERT OR UPDATE ON user_roles
    FOR EACH ROW EXECUTE FUNCTION enforce_sod_aa_daf();
```

- [ ] **Step 4: Lancer le test pour verifier qu'il passe**

Run: `./mvnw test -Dtest=SodRoleConstraintTest`
Expected: PASS (3/3). Le conteneur rejoue V1..V47 : les trois tests doivent passer sans aucune
intervention manuelle sur une base (c'est tout l'interet du conteneur jetable).

- [ ] **Step 5: Verifier la base de developpement**

La base de dev n'est PAS touchee par le test (le conteneur est jetable). V47 s'y appliquera au
prochain demarrage du backend — c'est verifie en Task 5 (Step 2), pas ici.

Verification optionnelle a ce stade, seulement si le backend a deja ete redemarre :
```bash
"/c/Program Files/PostgreSQL/18/bin/psql.exe" "postgresql://postgres:dany@localhost:5433/oct_invoice" -c "SELECT u.username, string_agg(r.name, ', ' ORDER BY r.name) AS roles FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.username IN ('aa','aa2','daf') GROUP BY u.username ORDER BY u.username;"
```
Expected (apres application de V47) :
```
 aa   | ROLE_ASSISTANT_COMPTABLE
 aa2  | ROLE_ASSISTANT_COMPTABLE
 daf  | ROLE_DAF
```

- [ ] **Step 6: Gate complet**

Run: `./mvnw test`
Expected: 0 failure, 0 error.

Rappel du contexte verifie : les autres tests tournent sous H2 **avec Flyway desactive**, donc
V47 ne les atteint pas — aucun d'eux ne devrait bouger. Si des tests supposaient malgre tout le
cumul AA+DAF sur `daf` (fixtures Java construisant l'utilisateur en dur), les corriger ici :
c'est attendu, ils encodaient la violation SoD.

- [ ] **Step 7: Loguer PROB-115 dans le registre**

Run (heredoc — le registre contient des octets NUL, ne PAS utiliser Edit) :
```bash
cat >> docs/KNOWN_ISSUES_REGISTRY.md <<'EOF'

## PROB-115 — Cumul de roles AA+DAF sur le compte `daf` (violation SoD)

**Date:** 2026-07-17
**Gravite:** Majeur (separation des taches)
**Statut:** Corrige (V47)

**Symptome:** Le compte `daf` portait `ROLE_ASSISTANT_COMPTABLE` ET `ROLE_DAF` (seul compte du
systeme a cumuler deux roles metier). Le DAF pouvait ainsi approuver le bon a payer de factures
qu'il avait lui-meme saisies.

**Cause racine:** Attribution de roles non contrainte. Aucun `UserServiceImpl` ne centralise
l'ecriture des roles (elle passe par `User`, `UserMapper`, `UserCsvService`), donc aucun point
unique ou poser une garde applicative.

**Solution:** V47 — retrait du role AA sur `daf`, seed d'un second AA (`aa2`) comme repli (la
delegation est departementale et ne peut pas couvrir l'AA, transverse), et trigger
`trg_enforce_sod_aa_daf` rejetant tout futur cumul AA+DAF sur `user_roles` (couvre aussi
l'import CSV).

**Regle preventive:** Toute paire de roles incompatible (SoD) doit etre gardee en base, pas
seulement dans le service : les chemins d'ecriture sont multiples (API, import CSV, seed).
EOF
```

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V47__enforce_sod_aa_daf.sql \
        src/test/java/com/oct/invoicesystem/domain/user/SodRoleConstraintTest.java \
        docs/KNOWN_ISSUES_REGISTRY.md
git commit -m "fix(sod): retrait cumul AA+DAF du compte daf + repli aa2 + trigger anti-cumul (V47, PROB-115)"
```

---

## Task 2: Etat EN_CONTROLE_AA — enum, evenement, state machine

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/model/InvoiceStatus.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/invoice/statemachine/InvoiceEvent.java`
- Modify: `src/main/java/com/oct/invoicesystem/config/StateMachineConfig.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/guard/RoleMatchGuard.java`
- Test: `src/test/java/com/oct/invoicesystem/domain/invoice/service/StateMachineTransitionExhaustiveTest.java`

**Interfaces:**
- Consumes: V47 (Task 1) — le compte `daf` n'a plus le role AA
- Produces: `InvoiceStatus.EN_CONTROLE_AA` ; `InvoiceEvent.ASSIGN_AA` ; transitions `SOUMIS --ASSIGN_AA--> EN_CONTROLE_AA`, `EN_CONTROLE_AA --ASSIGN_REVIEWER--> EN_VALIDATION_N1`, `EN_CONTROLE_AA --REJECT--> REJETE`

**Contexte verifie :** `StateMachineConfig` configure les transitions via
`transitions.withExternal().source(...).target(...).event(...).guard(...)` chainees par `.and()`.
La transition actuelle `SOUMIS -> EN_VALIDATION_N1` (event `ASSIGN_REVIEWER`, guard
`roleMatchGuard`) est aux alentours des lignes 56-60.

- [ ] **Step 1: Ecrire le test qui echoue**

Ajouter dans `StateMachineTransitionExhaustiveTest` (adapter au style existant du fichier —
le lire d'abord) :

```java
@Test
void aaControlIsMandatoryBetweenSoumisAndValidationN1() {
    // SOUMIS ne mene plus directement a EN_VALIDATION_N1
    assertThat(canTransition(InvoiceStatus.SOUMIS, InvoiceEvent.ASSIGN_REVIEWER)).isFalse();
    // SOUMIS -> EN_CONTROLE_AA via ASSIGN_AA
    assertThat(targetOf(InvoiceStatus.SOUMIS, InvoiceEvent.ASSIGN_AA))
            .isEqualTo(InvoiceStatus.EN_CONTROLE_AA);
    // EN_CONTROLE_AA -> EN_VALIDATION_N1
    assertThat(targetOf(InvoiceStatus.EN_CONTROLE_AA, InvoiceEvent.ASSIGN_REVIEWER))
            .isEqualTo(InvoiceStatus.EN_VALIDATION_N1);
    // EN_CONTROLE_AA -> REJETE
    assertThat(targetOf(InvoiceStatus.EN_CONTROLE_AA, InvoiceEvent.REJECT))
            .isEqualTo(InvoiceStatus.REJETE);
}
```

> Si le fichier n'expose pas de helpers `canTransition` / `targetOf`, utiliser le mecanisme
> d'assertion deja present dans ce test (lire le fichier avant d'ecrire). Ne PAS introduire
> un style d'assertion different de l'existant.

- [ ] **Step 2: Lancer le test pour verifier qu'il echoue**

Run: `./mvnw test -Dtest=StateMachineTransitionExhaustiveTest`
Expected: FAIL — `ASSIGN_AA` et `EN_CONTROLE_AA` n'existent pas (erreur de compilation du test).

- [ ] **Step 3: Ajouter l'etat et l'evenement**

Dans `InvoiceStatus.java`, inserer entre `SOUMIS` et `EN_VALIDATION_N1` :

```java
    EN_CONTROLE_AA("En controle AA", "Under AA review"),
```

Dans `InvoiceEvent.java`, inserer apres `SUBMIT` :

```java
    ASSIGN_AA,
```

- [ ] **Step 4: Remplacer la transition dans StateMachineConfig**

Remplacer le bloc `SOUMIS -> EN_VALIDATION_N1` par les trois transitions suivantes
(la source `SOUMIS` de `ASSIGN_REVIEWER` devient `EN_CONTROLE_AA` — c'est ce **remplacement**
qui rend le controle AA obligatoire) :

```java
                .and()
                .withExternal()
                .source(InvoiceStatus.SOUMIS)
                .target(InvoiceStatus.EN_CONTROLE_AA)
                .event(InvoiceEvent.ASSIGN_AA)
                .guard(roleMatchGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_CONTROLE_AA)
                .target(InvoiceStatus.EN_VALIDATION_N1)
                .event(InvoiceEvent.ASSIGN_REVIEWER)
                .guard(roleMatchGuard)
                .and()
                .withExternal()
                .source(InvoiceStatus.EN_CONTROLE_AA)
                .target(InvoiceStatus.REJETE)
                .event(InvoiceEvent.REJECT)
                .guard(rejectionReasonGuard)
```

- [ ] **Step 5: Etendre RoleMatchGuard a ASSIGN_AA**

Dans `RoleMatchGuard.evaluate`, la branche existante est
`if (event == InvoiceEvent.ASSIGN_REVIEWER || event == InvoiceEvent.VALIDATE_N1)` (ligne ~47).
Ajouter **avant** cette branche le traitement du role AA (transverse, aucun departement) :

```java
        if (event == InvoiceEvent.ASSIGN_AA) {
            boolean isAa = user.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ASSISTANT_COMPTABLE"));
            if (!isAa) {
                log.warn("RoleMatchGuard: user {} is not ROLE_ASSISTANT_COMPTABLE", userId);
            }
            return isAa;
        }
```

- [ ] **Step 6: Lancer le test pour verifier qu'il passe**

Run: `./mvnw test -Dtest=StateMachineTransitionExhaustiveTest`
Expected: PASS.

- [ ] **Step 7: Gate complet**

Run: `./mvnw test`
Expected: 0 failure, 0 error.

Les tests suivants touchent `SOUMIS`/`assignReviewer` et vont probablement casser — les
corriger ici en les faisant passer par le nouvel etat (c'est attendu, ce ne sont pas des
regressions) : `InvoiceStateMachineServiceTest`, `ApprovalServiceTest`,
`ApprovalControllerTest`, `InvoiceServiceTest`, `PaymentServiceTest`, `PaymentControllerTest`,
`BatchPaymentIntegrationTest`, `ReportServiceTest`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/invoice/model/InvoiceStatus.java \
        src/main/java/com/oct/invoicesystem/domain/invoice/statemachine/InvoiceEvent.java \
        src/main/java/com/oct/invoicesystem/config/StateMachineConfig.java \
        src/main/java/com/oct/invoicesystem/domain/workflow/guard/RoleMatchGuard.java \
        src/test/java/
git commit -m "feat(workflow): etat EN_CONTROLE_AA obligatoire entre SOUMIS et EN_VALIDATION_N1"
```

---

## Task 3: Service, controller et i18n du controle AA

**Files:**
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalService.java`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceImpl.java:46-64`
- Modify: `src/main/java/com/oct/invoicesystem/domain/workflow/controller/ApprovalController.java`
- Modify: `src/main/resources/messages.properties`, `src/main/resources/messages_fr.properties`
- Test: `src/test/java/com/oct/invoicesystem/domain/workflow/service/ApprovalServiceTest.java`

**Interfaces:**
- Consumes: `InvoiceStatus.EN_CONTROLE_AA`, `InvoiceEvent.ASSIGN_AA` (Task 2)
- Produces: `ApprovalService.assignAA(UUID invoiceId)` ; `POST /api/approvals/{invoiceId}/assign-aa`

**Contexte verifie (ne pas re-deriver) :**
- `ApprovalServiceImpl.assignReviewer` (l.46) branche sur
  `if (invoice.getStatus() == InvoiceStatus.SOUMIS)`, appelle `checkRole(currentUser, dept.getN1Role())`,
  `createOrUpdateStep(...)` puis `sendEvent(ASSIGN_REVIEWER)`. Le `else` final leve
  `new WorkflowException("Cannot assign reviewer from state " + invoice.getStatus())` — message
  en dur, non traduisible (pas d'args) : le remplacer par une cle.
- **Signature reelle** (l.180) :
  `private ApprovalStep createOrUpdateStep(Invoice invoice, int stepOrder, User approver, String nameFr, String comment, String rejButtonReason, ApprovalStepStatus status)`
  -> un seul libelle (`nameFr`) ; la colonne `step_name_en` (NOT NULL en base) est remplie par
  la methode elle-meme, ne PAS tenter de la passer.
- **`stepOrder` deja utilises** : `1` = Validation N1, `2` = Validation N2, `3` = Bon a Payer.
  -> **`0` est libre** et convient au controle AA, qui precede N1.
- `ensureNotSubmitter(invoice, approver)` existe deja (l.234) et leve
  `error.approval.approver_is_submitter`. `checkRole(user, requiredRole)` est en l.215.

- [ ] **Step 1: Ecrire les tests qui echouent**

Ajouter dans `ApprovalServiceTest` (suivre le style de mock existant du fichier) :

```java
@Test
void assignAA_movesSoumisToEnControleAA_whenUserIsAssistantComptable() {
    // given: facture SOUMIS + utilisateur courant ROLE_ASSISTANT_COMPTABLE
    // when: approvalService.assignAA(invoiceId)
    // then: sendEvent(invoiceId, InvoiceEvent.ASSIGN_AA, null) est appele
    verify(invoiceStateMachineService).sendEvent(invoiceId, InvoiceEvent.ASSIGN_AA, null);
}

@Test
void assignAA_rejectsNonAssistantComptable() {
    // given: facture SOUMIS + utilisateur ROLE_VALIDATEUR_N1_INFO
    assertThatThrownBy(() -> approvalService.assignAA(invoiceId))
            .isInstanceOf(AccessDeniedException.class);
}

@Test
void assignReviewer_fromSoumis_isRefused_becauseAaControlIsRequired() {
    // given: facture SOUMIS + utilisateur N1
    assertThatThrownBy(() -> approvalService.assignReviewer(invoiceId))
            .isInstanceOf(WorkflowException.class)
            .hasMessage("error.approval.aa_control_required");
}
```

> Lire le fichier avant d'ecrire : reprendre exactement le mode de construction des mocks
> (`@Mock` / `when(...)`) et des fixtures de facture deja en place.

- [ ] **Step 2: Lancer les tests pour verifier qu'ils echouent**

Run: `./mvnw test -Dtest=ApprovalServiceTest`
Expected: FAIL — `assignAA` n'existe pas.

- [ ] **Step 3: Ajouter la signature au service**

Dans `ApprovalService.java`, a cote de `void assignReviewer(UUID invoiceId);` :

```java
    void assignAA(UUID invoiceId);
```

- [ ] **Step 4: Implementer assignAA et basculer assignReviewer**

Dans `ApprovalServiceImpl`, ajouter `assignAA` :

```java
    @Override
    @Transactional
    public void assignAA(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        User currentUser = getCurrentUser();

        if (invoice.getStatus() != InvoiceStatus.SOUMIS) {
            throw new WorkflowException("error.approval.not_in_soumis_state");
        }
        checkRole(currentUser, "ROLE_ASSISTANT_COMPTABLE");
        ensureNotSubmitter(invoice, currentUser);
        createOrUpdateStep(invoice, 0, currentUser, "Controle AA", null, null, ApprovalStepStatus.PENDING);
        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ASSIGN_AA, null);
    }
```

Puis, dans `assignReviewer`, remplacer la branche `SOUMIS` par `EN_CONTROLE_AA` et rendre le
message final traduisible :

```java
        if (invoice.getStatus() == InvoiceStatus.EN_CONTROLE_AA) {
            checkRole(currentUser, invoice.getDepartment().getN1Role());
            createOrUpdateStep(invoice, 1, currentUser, "Validation N1 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
            invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ASSIGN_REVIEWER, null);
        } else if (invoice.getStatus() == InvoiceStatus.SOUMIS) {
            throw new WorkflowException("error.approval.aa_control_required");
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N1 && invoice.getDepartment().isRequiresN2()) {
            throw new WorkflowException("error.approval.cannot_assign_n2_in_n1");
        } else if (invoice.getStatus() == InvoiceStatus.EN_VALIDATION_N2) {
            checkRole(currentUser, invoice.getDepartment().getN2Role());
            createOrUpdateStep(invoice, 2, currentUser, "Validation N2 - " + invoice.getDepartment().getCode(), null, null, ApprovalStepStatus.PENDING);
        } else {
            throw new WorkflowException("error.approval.cannot_assign_from_state");
        }
```

> **ATTENTION — point de vigilance de la spec (§3.3) :** `ensureNotSubmitter` dans `assignAA`
> peut bloquer le flux de saisie interne (c'est l'AA lui-meme qui soumet ces factures). Si le
> gate revele ce cas, **NE PAS contourner la regle SoD** : remonter au porteur du projet.
> Le second compte `aa2` (Task 1) est precisement le repli prevu pour cette situation.

- [ ] **Step 5: Exposer l'endpoint**

Dans `ApprovalController`, a cote de `assignReviewer` (l.~66), en copiant le style d'annotation
existant du fichier (`@Operation`, `ApiResponse`, etc.) :

```java
    @PostMapping("/{invoiceId}/assign-aa")
    @Operation(summary = "Controle AA", description = "Moves a SOUMIS invoice to EN_CONTROLE_AA (assistant comptable check)")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    public ResponseEntity<ApiResponse<Void>> assignAA(@PathVariable UUID invoiceId) {
        approvalService.assignAA(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
```

- [ ] **Step 6: Ajouter les cles i18n**

`messages.properties` (EN, UTF-8, Edit possible) :
```properties
error.approval.aa_control_required=This invoice must be checked by an assistant comptable first.
error.approval.not_in_soumis_state=Invoice is not in submitted state.
error.approval.cannot_assign_from_state=Cannot assign a reviewer from this state.
```

`messages_fr.properties` — **ISO-8859-1 / pur-ASCII, script Python binaire OBLIGATOIRE**.
Creer `scratch/add_fr_keys.py` (scratch/ est gitignore) :

```python
path = "src/main/resources/messages_fr.properties"
lines = [
    "error.approval.aa_control_required=Cette facture doit d'abord \\u00eatre contr\\u00f4l\\u00e9e par un assistant comptable.\n",
    "error.approval.not_in_soumis_state=La facture n'est pas \\u00e0 l'\\u00e9tat soumis.\n",
    "error.approval.cannot_assign_from_state=Impossible d'assigner un validateur depuis cet \\u00e9tat.\n",
]
with open(path, "ab") as f:
    for line in lines:
        f.write(line.encode("ascii"))
print("OK")
```

Run: `python scratch/add_fr_keys.py`

Verifier qu'aucun octet non-ASCII n'a ete introduit :
```bash
python -c "d=open('src/main/resources/messages_fr.properties','rb').read(); print('NON-ASCII' if any(b>127 for b in d) else 'ASCII OK')"
```
Expected: `ASCII OK`

- [ ] **Step 7: Lancer les tests pour verifier qu'ils passent**

Run: `./mvnw test -Dtest=ApprovalServiceTest`
Expected: PASS.

- [ ] **Step 8: Gate complet**

Run: `./mvnw test`
Expected: 0 failure, 0 error.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/oct/invoicesystem/domain/workflow/ \
        src/main/resources/messages.properties \
        src/main/resources/messages_fr.properties \
        src/test/java/
git commit -m "feat(workflow): endpoint et service de controle AA + cles i18n FR/EN"
```

---

## Task 4: Frontend — nouvel etat et actions AA

**Files:**
- Modify: `frontend/src/types/invoice.ts`
- Modify: `frontend/src/components/ui/StatusBadge.tsx`
- Modify: `frontend/src/components/invoice/InvoiceActionPanel.tsx`
- Modify: `frontend/src/pages/ApprovalQueuePage.tsx`, `DashboardPage.tsx`, `InvoiceDetailPage.tsx`, `InvoiceListPage.tsx`, `frontend/src/pages/supplier/SupplierInvoicesPage.tsx`
- Test: `frontend/src/test/components/StatusBadge.test.tsx`, `frontend/src/test/components/InvoiceActionPanel.startReview.test.tsx`

**Interfaces:**
- Consumes: `POST /api/approvals/{invoiceId}/assign-aa` (Task 3) ; statut `EN_CONTROLE_AA`
- Produces: rien (couche terminale)

**Contexte verifie :** 7 fichiers applicatifs + `types/invoice.ts` referencent `SOUMIS`.
4 fichiers de test referencent `SOUMIS` : `InvoiceActionPanel.startReview.test.tsx`,
`InvoiceTimeline.test.tsx`, `StatusBadge.test.tsx`, `useInvoices.test.tsx`.

- [ ] **Step 1: Ecrire le test qui echoue**

Dans `frontend/src/test/components/StatusBadge.test.tsx`, ajouter (suivre le style existant) :

```tsx
it('affiche le badge du statut EN_CONTROLE_AA', () => {
  render(<StatusBadge status="EN_CONTROLE_AA" />);
  expect(screen.getByText(/contr[oô]le AA/i)).toBeInTheDocument();
});
```

- [ ] **Step 2: Lancer le test pour verifier qu'il echoue**

Run: `cd frontend && npx vitest run src/test/components/StatusBadge.test.tsx`
Expected: FAIL — statut inconnu (erreur de type ou libelle absent).

- [ ] **Step 3: Ajouter le statut au type et au badge**

Dans `frontend/src/types/invoice.ts`, ajouter `EN_CONTROLE_AA` a l'union/enum de statut, entre
`SOUMIS` et `EN_VALIDATION_N1` (respecter la forme existante du type).

Dans `StatusBadge.tsx`, ajouter l'entree correspondante dans la map de libelles/couleurs, en
reprenant exactement la convention des autres statuts (libelle FR « En controle AA », couleur
coherente avec la famille « en cours »).

- [ ] **Step 4: Lancer le test pour verifier qu'il passe**

Run: `cd frontend && npx vitest run src/test/components/StatusBadge.test.tsx`
Expected: PASS.

- [ ] **Step 5: Cabler l'action AA dans InvoiceActionPanel**

Dans `InvoiceActionPanel.tsx` : quand `status === 'SOUMIS'` et que l'utilisateur a
`ROLE_ASSISTANT_COMPTABLE`, afficher deux actions — « Transmettre a la validation » (appelle
`POST /approvals/{id}/assign-aa`) et « Rejeter » (flux de rejet existant, motif obligatoire).
L'action « demarrer la revue » du N1 ne doit plus s'afficher sur `SOUMIS` mais sur
`EN_CONTROLE_AA`. Reutiliser le hook/client API deja utilise par le panneau — ne pas introduire
un nouveau client HTTP.

- [ ] **Step 6: Mettre a jour filtres et affichages**

Ajouter `EN_CONTROLE_AA` aux listes de statuts de `ApprovalQueuePage`, `DashboardPage`,
`InvoiceDetailPage`, `InvoiceListPage`, `SupplierInvoicesPage` (filtres, compteurs, timeline),
en suivant le traitement deja applique a `SOUMIS` dans chaque fichier.

- [ ] **Step 7: Gate frontend**

Run:
```bash
cd frontend && npx tsc --noEmit && npx vitest run
```
Expected: 0 erreur TypeScript ; 0 test en echec. Corriger ici les tests
`InvoiceActionPanel.startReview.test.tsx`, `InvoiceTimeline.test.tsx`, `useInvoices.test.tsx`
qui supposent `SOUMIS -> revue N1` (attendu, ce ne sont pas des regressions).

- [ ] **Step 8: Commit**

```bash
git add frontend/src/
git commit -m "feat(workflow-ui): statut EN_CONTROLE_AA et actions de controle AA"
```

---

## Task 5: Verification runtime et documentation

**Files:**
- Modify: `docs/WORKFLOW.md`
- Modify: `docs/superpowers/plans/2026-07-17-workflow-controle-aa.md` (cocher les cases)

**Interfaces:**
- Consumes: tout le reste
- Produces: preuve runtime

- [ ] **Step 1: Deployer**

Run:
```bash
./mvnw.cmd -q -DskipTests package
docker cp target/invoice-system-1.0.0-SNAPSHOT.jar oct_backend:/app/app.jar
docker restart oct_backend
cd frontend && npx vite build && docker cp dist/. oct_frontend:/usr/share/nginx/html/ && docker exec oct_frontend nginx -s reload
```

- [ ] **Step 2: Verifier que V47 s'est appliquee**

Run:
```bash
"/c/Program Files/PostgreSQL/18/bin/psql.exe" "postgresql://postgres:dany@localhost:5433/oct_invoice" -c "SELECT version, description, success FROM flyway_schema_history WHERE version = '47';"
```
Expected: une ligne, `success = t`.

- [ ] **Step 3: Verifier le workflow en runtime (driver l'app, pas seulement les tests)**

Rate-limit login : **5 req/min/IP** -> espacer les logins, reutiliser les JWT.

1. Login `aa` / `Test1234!` -> ouvrir la facture portail `FAC-2026-00026`
   (id `6813b350-6e98-4908-93af-2a60231aea8e`, etat BROUILLON -> la soumettre si besoin
   depuis le compte `supplier`).
2. Depuis `aa` : « Transmettre a la validation » -> la facture passe en **EN_CONTROLE_AA**.
3. Login `daf` / `Test1234!` -> verifier la **perte** des acces AA (aucune action de controle AA).
4. Login d'un N1 du departement -> « demarrer la revue » disponible depuis `EN_CONTROLE_AA`,
   **pas** depuis `SOUMIS`.
5. Rejet AA : depuis `aa`, rejeter une facture en `EN_CONTROLE_AA` sans motif (refuse) puis
   avec motif -> etat **REJETE**, motif visible.
6. `resubmit` -> retour a `SOUMIS`.

- [ ] **Step 4: Mettre a jour docs/WORKFLOW.md**

Mettre a jour le diagramme §3 et les sections de transition :

```
[BROUILLON] --submit--> [SOUMIS]
[SOUMIS] --assign_aa (AA)--> [EN_CONTROLE_AA]
[EN_CONTROLE_AA] --assign_reviewer--> [EN_VALIDATION_N1]
[EN_CONTROLE_AA] --reject (AA)--> [REJETE]
```

Documenter : acteur = `ROLE_ASSISTANT_COMPTABLE`, controle = acte humain (transmettre/rejeter
avec motif), l'AA ne peut pas controler une facture qu'il a soumise, et le compte `aa2` est le
repli (la delegation etant departementale, elle ne couvre pas l'AA).

- [ ] **Step 5: Commit**

```bash
git add docs/WORKFLOW.md docs/superpowers/plans/2026-07-17-workflow-controle-aa.md
git commit -m "docs(workflow): etape de controle AA dans le diagramme et les transitions"
```

---

## Dependance notee (hors perimetre)

L'AA **doit** etre notifie a la soumission, sinon les factures dorment en `SOUMIS` sans que
personne ne soit alerte. Ce sujet releve de **N5/N6** (deja au backlog du lot 3) et n'est PAS
traite ici : le melanger a ce plan mettrait deux sujets dans une branche (CLAUDE.md §11).
A traiter immediatement apres ce lot.
