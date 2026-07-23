package com.oct.invoicesystem.domain.purchasing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingConfigUpdateRequest;
import com.oct.invoicesystem.domain.purchasing.service.MatchingConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-008 / decision D5 — the three-way matching tolerance thresholds are a financial control:
 * they decide whether a billing discrepancy passes or blocks. They therefore belong to the DAF, and
 * the ADMIN — which has no financial access — must not be able to read or relax them.
 *
 * <p>This controller was one of the three untested controllers of AUDIT-013.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchingConfigControllerSodTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MatchingConfigService matchingConfigService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateConfig_asAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/matching-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MatchingConfigUpdateRequest(new BigDecimal("99.00"), new BigDecimal("1000000"), false))))
                .andExpect(status().isForbidden());
        // The relaxation must never reach the service.
        verify(matchingConfigService, never()).updateConfig(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getConfig_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/matching-config"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void updateConfig_asAssistantComptable_returns403() throws Exception {
        // The AA reads the thresholds (they run the matching) but does not set them.
        mockMvc.perform(post("/api/v1/matching-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MatchingConfigUpdateRequest(new BigDecimal("5.00"), new BigDecimal("1000"), true))))
                .andExpect(status().isForbidden());
        verify(matchingConfigService, never()).updateConfig(any(), any(), any(), any());
    }
}
