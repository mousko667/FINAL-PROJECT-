package com.oct.invoicesystem.domain.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;
import com.oct.invoicesystem.domain.audit.model.AuditLog;
import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AuditServiceImpl#searchLogsWithActionFilter} can drop noise entries via the
 * {@code excludeAction} parameter, exercised against a real H2 database (the Specification runs as
 * SQL). The financial journal is dominated by {@code ACCESS_DENIED} probe traffic; the DAF view hides
 * it by default (excludeAction=ACCESS_DENIED) and re-shows it by omitting the parameter.
 *
 * <p>The service is constructed by hand from the injected JPA repositories rather than declared as a
 * Spring bean: registering an {@code ObjectMapper} bean in a shared test context would override the
 * JSR-310–aware application mapper and break JSON serialization in every other {@code @SpringBootTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuditServiceExcludeActionTest {

    @Autowired private AuditLogRepository repo;
    @Autowired private UserRepository userRepository;

    private AuditServiceImpl service;

    private final List<String> financialActions = List.of(
            "INVOICE_CREATE", "INVOICE_SUBMIT", "BON_A_PAYER", "PAYMENT", "ACCESS_DENIED");

    @BeforeEach
    void setUp() {
        service = new AuditServiceImpl(repo, userRepository, new ObjectMapper());
        repo.save(AuditLog.builder().action("INVOICE_SUBMIT").entityType("INVOICE").entityId("E1").build());
        repo.save(AuditLog.builder().action("BON_A_PAYER").entityType("APPROVAL").entityId("E2").build());
        repo.save(AuditLog.builder().action("ACCESS_DENIED").entityType("SECURITY").entityId("E3").build());
        repo.save(AuditLog.builder().action("ACCESS_DENIED").entityType("SECURITY").entityId("E4").build());
    }

    @Test
    void excludeAction_dropsMatchingEntries() {
        Page<AuditLogDTO> result = service.searchLogsWithActionFilter(
                null, null, null, null, financialActions, "ACCESS_DENIED", null, null,
                PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(AuditLogDTO::action)
                .containsExactlyInAnyOrder("INVOICE_SUBMIT", "BON_A_PAYER");
        assertThat(result.getContent()).extracting(AuditLogDTO::action).doesNotContain("ACCESS_DENIED");
    }

    @Test
    void nullExcludeAction_keepsAllAllowedEntries() {
        Page<AuditLogDTO> result = service.searchLogsWithActionFilter(
                null, null, null, null, financialActions, null, null, null,
                PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(4);
        assertThat(result.getContent()).extracting(AuditLogDTO::action).contains("ACCESS_DENIED");
    }
}
