package com.oct.invoicesystem.domain.user;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifie la separation des taches (SoD) entre {@code ROLE_ASSISTANT_COMPTABLE} et
 * {@code ROLE_DAF} telle que LIVREE par la migration V47.
 *
 * <p>Tourne sur un PostgreSQL jetable (Testcontainers) avec Flyway ACTIVE, et non sur le H2 du
 * profil {@code test} : H2 ne sait pas executer le trigger PL/pgSQL de V47, et le profil de test
 * desactive Flyway ({@code application-test.yml}), donc ni le trigger ni le seed {@code aa2} n'y
 * existeraient. Le conteneur rejoue toutes les migrations, ce qui teste la migration reelle et
 * permet l'INSERT destructif sans toucher la base de developpement.</p>
 *
 * <p>Le profil {@code test} est conserve pour les proprietes non-datasource (cles JWT, cle AES,
 * MinIO) dont le contexte a besoin au demarrage ; le {@link DynamicPropertySource} ci-dessous
 * surcharge le datasource, active Flyway et neutralise le {@code ddl-auto} d'Hibernate afin que
 * le schema soit bien construit par les migrations.</p>
 *
 * <p><b>Hors du gate par decision deliberee (PROB-115).</b> Cette classe porte le tag
 * {@code requires-docker}, exclu de {@code ./mvnw test} : Docker Desktop 29 renvoie une enveloppe
 * {@code /info} vide (HTTP 400) a tout client HTTP tiers, donc aucun conteneur ne demarre sur
 * cette machine. Le test n'est ni neutralise par {@code assumeTrue} ni rabattu sur H2 — il reste
 * exact et executable a la demande :</p>
 *
 * <pre>{@code ./mvnw test -Prequires-docker -Dtest=SodRoleConstraintTest}</pre>
 *
 * <p>En attendant, V47 est verifiee en runtime sur la base de developpement (Task 5 du plan), ou
 * le cumul AA+DAF existe reellement et ou le {@code DELETE} a donc un effet observable.</p>
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Tag("requires-docker")
class SodRoleConstraintTest {

    private static final String SOD_VIOLATION_MESSAGE = "SoD violation";

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

    /**
     * Verifie que le {@code DELETE} de V47 retire bien {@code ROLE_ASSISTANT_COMPTABLE} du compte
     * {@code daf}.
     *
     * <p><b>Ce test fabrique lui-meme son propre rouge — ce n'est pas un bug, c'est delibere.</b>
     * Le cumul AA+DAF n'existe dans <b>AUCUNE migration</b> : {@code V34__seed_test_users.sql}
     * (l.156) n'attribue que {@code role_daf} au compte {@code daf}. Le cumul constate provient
     * d'une <b>attribution manuelle jamais versionnee</b> dans la base de developpement.</p>
     *
     * <p>Consequence : sur un conteneur vierge, qui ne rejoue que les migrations, le compte
     * {@code daf} est deja propre et une simple assertion de nettoyage serait <b>verte avant meme
     * que V47 existe</b> — donc vide de sens. Le test reproduit donc artificiellement la derive
     * (trigger desactive le temps de l'INSERT, puisque V47 l'interdit desormais) avant de rejouer
     * le {@code DELETE}, ce qui rend l'assertion reellement falsifiable.</p>
     *
     * <p>Le {@code DELETE} de V47 reste utile en production comme en dev : c'est le seul moyen
     * <b>versionne</b> de corriger la base de developpement, ou la derive existe pour de vrai.</p>
     */
    @Test
    void v47RemovesAaRoleFromDafAccount() {
        jdbc.execute("ALTER TABLE user_roles DISABLE TRIGGER trg_enforce_sod_aa_daf");
        try {
            jdbc.update("INSERT INTO user_roles (user_id, role_id) "
                    + "SELECT u.id, r.id FROM users u, roles r "
                    + "WHERE u.username = 'daf' AND r.name = 'ROLE_ASSISTANT_COMPTABLE' "
                    + "ON CONFLICT DO NOTHING");
        } finally {
            jdbc.execute("ALTER TABLE user_roles ENABLE TRIGGER trg_enforce_sod_aa_daf");
        }

        assertThat(countRoleOf("daf", "ROLE_ASSISTANT_COMPTABLE"))
                .as("le cumul doit etre reproduit avant de verifier le nettoyage")
                .isEqualTo(1);

        jdbc.update("DELETE FROM user_roles ur USING users u, roles r "
                + "WHERE ur.user_id = u.id AND ur.role_id = r.id "
                + "AND u.username = 'daf' AND r.name = 'ROLE_ASSISTANT_COMPTABLE'");

        assertThat(countRoleOf("daf", "ROLE_ASSISTANT_COMPTABLE")).isZero();
    }

    /** Le compte {@code daf} livre par les migrations ne doit porter que {@code ROLE_DAF}. */
    @Test
    void dafAccountMustNotHoldAssistantComptableRole() {
        assertThat(countRoleOf("daf", "ROLE_ASSISTANT_COMPTABLE")).isZero();
        assertThat(countRoleOf("daf", "ROLE_DAF")).isEqualTo(1);
    }

    /** V47 doit seeder {@code aa2}, second assistant comptable servant de repli. */
    @Test
    void fallbackAssistantComptableAccountExists() {
        assertThat(countRoleOf("aa2", "ROLE_ASSISTANT_COMPTABLE")).isEqualTo(1);
    }

    /** Le trigger doit refuser l'ajout de {@code ROLE_DAF} a un assistant comptable. */
    @Test
    void databaseRejectsDafRoleOnAssistantComptable() {
        UUID userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'aa2'", UUID.class);
        UUID dafRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE name = 'ROLE_DAF'", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, dafRoleId))
                .hasMessageContaining(SOD_VIOLATION_MESSAGE);

        assertThat(countRoleOf("aa2", "ROLE_DAF"))
                .as("l'insertion refusee ne doit laisser aucune trace")
                .isZero();
    }

    /** Le trigger doit aussi refuser le cumul dans le sens inverse (AA ajoute a un DAF). */
    @Test
    void databaseRejectsAssistantComptableRoleOnDaf() {
        UUID userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'daf'", UUID.class);
        UUID aaRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE name = 'ROLE_ASSISTANT_COMPTABLE'", UUID.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, aaRoleId))
                .hasMessageContaining(SOD_VIOLATION_MESSAGE);
    }

    /** Le trigger ne doit pas gener l'attribution d'un role sans conflit de SoD. */
    @Test
    void databaseAllowsNonConflictingRole() {
        UUID userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE username = 'aa2'", UUID.class);
        UUID otherRoleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE name = 'ROLE_VALIDATEUR_N1_DRH'", UUID.class);

        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, otherRoleId);

        assertThat(countRoleOf("aa2", "ROLE_VALIDATEUR_N1_DRH")).isEqualTo(1);

        jdbc.update("DELETE FROM user_roles WHERE user_id = ? AND role_id = ?", userId, otherRoleId);
    }

    private Integer countRoleOf(String username, String roleName) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_roles ur "
                        + "JOIN users u ON u.id = ur.user_id "
                        + "JOIN roles r ON r.id = ur.role_id "
                        + "WHERE u.username = ? AND r.name = ?",
                Integer.class, username, roleName);
    }
}
