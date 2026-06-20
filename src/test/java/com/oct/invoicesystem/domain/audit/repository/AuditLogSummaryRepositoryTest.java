package com.oct.invoicesystem.domain.audit.repository;

import com.oct.invoicesystem.domain.audit.model.AuditLog;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level verification of the M10 #12 aggregated summary queries.
 *
 * <p>{@code createdAt} carries {@code @CreationTimestamp}, which Hibernate sets at insert time and
 * would overwrite any builder value, so each seeded row's date is forced afterwards via a native
 * UPDATE. The {@code test} profile runs H2 in PostgreSQL mode (jsonb columns map cleanly there).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuditLogSummaryRepositoryTest {

    @Autowired private AuditLogRepository repo;
    @Autowired private EntityManager em;

    private final Instant now = Instant.now();

    private final List<String> systemActions = List.of("LOGIN", "USER_CREATE");

    private void seed(String action, String entityType, Instant at) {
        AuditLog saved = repo.save(AuditLog.builder()
                .action(action).entityType(entityType).entityId("E1").createdAt(at).build());
        // Override the @CreationTimestamp-generated value so window filtering can be exercised.
        em.createNativeQuery("UPDATE audit_logs SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(at))
                .setParameter(2, saved.getId())
                .executeUpdate();
        em.flush();
        em.clear();
    }

    @BeforeEach
    void seedAll() {
        seed("LOGIN", "User", now);
        seed("LOGIN", "User", now.minus(1, ChronoUnit.DAYS));
        seed("USER_CREATE", "User", now);
        seed("INVOICE_CREATE", "Invoice", now); // hors SYSTEM scope
        seed("LOGIN", "User", now.minus(40, ChronoUnit.DAYS)); // hors fenêtre
    }

    @Test
    void summaryByAction_respectsWindowAndAllowedActions() {
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        List<Object[]> rows = repo.summaryByAction(from, to, systemActions);
        // LOGIN=2 (le 3e est hors fenêtre), USER_CREATE=1, INVOICE_CREATE exclu (hors scope)
        assertThat(rows).extracting(r -> (String) r[0]).containsExactlyInAnyOrder("LOGIN", "USER_CREATE");
        long loginCount = rows.stream().filter(r -> r[0].equals("LOGIN")).mapToLong(r -> ((Number) r[1]).longValue()).sum();
        assertThat(loginCount).isEqualTo(2);
    }

    @Test
    void summaryByEntityType_groupsByEntity() {
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        List<Object[]> rows = repo.summaryByEntityType(from, to, systemActions);
        assertThat(rows).extracting(r -> (String) r[0]).containsExactly("User"); // Invoice exclu (scope)
    }

    @Test
    void summaryByDay_groupsByCalendarDay() {
        Instant from = now.minus(30, ChronoUnit.DAYS);
        Instant to = now.plus(1, ChronoUnit.DAYS);
        List<Object[]> rows = repo.summaryByDay(from, to, systemActions);
        // au moins 2 jours distincts (aujourd'hui + hier)
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);
    }
}
