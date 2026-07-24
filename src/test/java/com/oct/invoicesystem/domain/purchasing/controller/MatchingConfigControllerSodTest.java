package com.oct.invoicesystem.domain.purchasing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingConfigDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingConfigUpdateRequest;
import com.oct.invoicesystem.domain.purchasing.mapper.MatchingConfigMapper;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import com.oct.invoicesystem.domain.purchasing.service.MatchingConfigService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @MockBean
    private MatchingConfigMapper matchingConfigMapper;

    @MockBean
    private SecurityHelper securityHelper;

    private MatchingConfigDTO dto() {
        return new MatchingConfigDTO(UUID.randomUUID(), new BigDecimal("2.00"),
                new BigDecimal("0.00"), true, true, UUID.randomUUID(), Instant.now());
    }

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

    // ── Nominal side of the control: the DAF owns the thresholds ──────────────
    // Without these, a whitelist that refused EVERYONE would still pass the 403 tests above.

    @Test
    @WithMockUser(roles = "DAF")
    void updateConfig_asDaf_returns200() throws Exception {
        when(securityHelper.currentUser(any(Authentication.class)))
                .thenReturn(User.builder().id(UUID.randomUUID()).username("daf").build());
        when(matchingConfigService.updateConfig(any(), any(), any(), any()))
                .thenReturn(new MatchingConfig());
        when(matchingConfigMapper.toDTO(any())).thenReturn(dto());

        mockMvc.perform(post("/api/v1/matching-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MatchingConfigUpdateRequest(new BigDecimal("2.00"), new BigDecimal("0.00"), true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tolerancePercentage").value(2.00));
        verify(matchingConfigService).updateConfig(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getConfig_asDaf_returns200() throws Exception {
        when(matchingConfigService.getActiveConfig()).thenReturn(new MatchingConfig());
        when(matchingConfigMapper.toDTO(any())).thenReturn(dto());

        mockMvc.perform(get("/api/v1/matching-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requireGrn").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getConfig_asAssistantComptable_returns200() throws Exception {
        // The AA runs the matching, so they may READ the thresholds — but not set them (tested above).
        when(matchingConfigService.getActiveConfig()).thenReturn(new MatchingConfig());
        when(matchingConfigMapper.toDTO(any())).thenReturn(dto());

        mockMvc.perform(get("/api/v1/matching-config"))
                .andExpect(status().isOk());
    }
}
