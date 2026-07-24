package com.oct.invoicesystem.domain.workflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUDIT-036 — verifies the V49 catch-up migration that repairs the "Controle AA" approval step
 * frozen at PENDING.
 *
 * <p>Runs on a throwaway PostgreSQL (Testcontainers) with Flyway ACTIVE, not on the H2 of the
 * {@code test} profile: the {@code test} profile disables Flyway (the schema is built by Hibernate),
 * so the migration under test would never run there. The container replays every migration, then the
 * test seeds a PENDING AA control row and re-applies V49's own SQL (read from the classpath, never
 * duplicated) to prove it flips exactly the frozen rows.</p>
 *
 * <p><b>The test fabricates its own red on purpose.</b> On a fresh container V49 has already run and
 * flipped nothing (no legacy PENDING rows exist), so a bare "no PENDING remain" assertion would be
 * green before V49 was ever written — vacuous. Seeding a PENDING row after the migrations, then
 * running V49's statement, makes the assertion genuinely falsifiable: without the fix the row stays
 * PENDING. The REJECTED counter-row proves the scope is narrow (a terminal reject is not resurrected).</p>
 *
 * <p><b>Out of the gate by deliberate decision (PROB-115).</b> Tagged {@code requires-docker}, excluded
 * from {@code ./mvnw test}: Docker Desktop 29 returns an empty {@code /info} envelope (HTTP 400) to
 * third-party HTTP clients, so no container starts on this machine. The migration is instead verified
 * in runtime on the dev database (9 PENDING → 9 APPROVED with action_at). Run on demand:</p>
 *
 * <pre>{@code ./mvnw test -Prequires-docker -Dtest=AaControlBackfillMigrationTest}</pre>
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@Tag("requires-docker")
class AaControlBackfillMigrationTest {

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
    void v49_flipsFrozenPendingAaSteps_toApprovedWithActionAt_andLeavesRejectedUntouched() throws Exception {
        UUID invoiceId = jdbc.queryForObject("SELECT id FROM invoices LIMIT 1", UUID.class);

        UUID pendingStep = insertAaStep(invoiceId, 900, "PENDING", null);
        UUID rejectedStep = insertAaStep(invoiceId, 901, "REJECTED", null);

        // Re-run V49's statement verbatim (its own SQL, loaded from the classpath).
        jdbc.execute(readMigrationSql("db/migration/V49__backfill_aa_control_step_approved.sql"));

        // The frozen PENDING AA control is now APPROVED, with action_at backfilled from created_at.
        assertThat(statusOf(pendingStep)).isEqualTo("APPROVED");
        assertThat(actionAtIsNull(pendingStep))
                .as("a repaired AA control must carry an action_at").isFalse();

        // Counter-proof: a terminal REJECTED AA step is not resurrected.
        assertThat(statusOf(rejectedStep)).isEqualTo("REJECTED");
    }

    private UUID insertAaStep(UUID invoiceId, int stepOrder, String status, java.time.Instant actionAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO approval_steps "
                        + "(id, invoice_id, step_order, step_name_fr, step_name_en, department_code, "
                        + " status, action_at, created_at) "
                        + "VALUES (?, ?, ?, 'Controle AA', 'AA Control', 'INFO', ?, ?, now())",
                id, invoiceId, stepOrder, status, actionAt);
        return id;
    }

    private String statusOf(UUID stepId) {
        return jdbc.queryForObject("SELECT status FROM approval_steps WHERE id = ?", String.class, stepId);
    }

    private boolean actionAtIsNull(UUID stepId) {
        Integer nulls = jdbc.queryForObject(
                "SELECT count(*) FROM approval_steps WHERE id = ? AND action_at IS NULL",
                Integer.class, stepId);
        return nulls != null && nulls > 0;
    }

    private String readMigrationSql(String classpathLocation) throws Exception {
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
