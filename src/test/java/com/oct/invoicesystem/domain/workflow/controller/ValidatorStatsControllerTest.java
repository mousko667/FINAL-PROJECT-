package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.dto.ValidatorStatsResponse;
import com.oct.invoicesystem.domain.workflow.service.ApprovalService;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ValidatorStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private SecurityHelper securityHelper;

    @Test
    @WithMockUser(roles = "VALIDATEUR_N1_INFO", username = "validator")
    void myStats_asValidator_returns200WithStats() throws Exception {
        User user = User.builder().id(UUID.randomUUID()).username("validator").build();
        when(securityHelper.currentUser(any(Authentication.class))).thenReturn(user);
        when(approvalService.getValidatorStats(user.getId())).thenReturn(new ValidatorStatsResponse(7L, 3L));

        mockMvc.perform(get("/api/v1/workflow/my-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.approvedTotal").value(7))
                .andExpect(jsonPath("$.data.processedThisMonth").value(3));
    }

    @Test
    @WithMockUser(roles = "SUPPLIER", username = "supplier")
    void myStats_asSupplier_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/workflow/my-stats"))
                .andExpect(status().isForbidden());
    }
}
