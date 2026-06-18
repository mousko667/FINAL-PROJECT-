package com.oct.invoicesystem.domain.report.controller;

import com.oct.invoicesystem.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage for {@code GET /api/v1/reports/cash-flow} against a real PostgreSQL database
 * (PROB-054). The pre-existing {@link ReportControllerTest} mocks {@link
 * com.oct.invoicesystem.domain.report.service.ReportService}, so it never executes the underlying
 * {@code InvoiceRepository.findAllWithFilters} query — the very query that threw
 * {@code SQLGrammarException} at runtime because its nullable date parameters were untyped.
 *
 * <p>This test invokes the real service and repository, so the query is genuinely prepared by
 * PostgreSQL. It must run on PostgreSQL (not H2), which silently infers null-parameter types and
 * would therefore pass even against the broken query.</p>
 */
@AutoConfigureMockMvc
class CashFlowProjectionIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "DAF")
    void getCashFlowProjection_runsRealQuery_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/reports/cash-flow").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalProjected").exists());
    }
}
