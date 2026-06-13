package com.oct.invoicesystem.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.model.SecurityPolicy;
import com.oct.invoicesystem.domain.auth.service.SecurityPolicyService;
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

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SecurityPolicyService securityPolicyService;

    @MockBean
    private SecurityHelper securityHelper;

    private SecurityPolicy policy() {
        return SecurityPolicy.builder()
                .id(UUID.randomUUID())
                .mfaRequired(true).sessionTimeoutMinutes(60)
                .maxLoginAttempts(5).minPasswordLength(8)
                .isActive(true)
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    void getPolicy_asAdmin_returns200() throws Exception {
        when(securityPolicyService.getActivePolicy()).thenReturn(policy());

        mockMvc.perform(get("/api/v1/admin/security-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(true))
                .andExpect(jsonPath("$.data.sessionTimeoutMinutes").value(60))
                .andExpect(jsonPath("$.data.maxLoginAttempts").value(5))
                .andExpect(jsonPath("$.data.minPasswordLength").value(8));
    }

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin")
    void updatePolicy_asAdmin_returns200() throws Exception {
        User admin = User.builder().id(UUID.randomUUID()).username("admin").build();
        when(securityHelper.currentUser(any(Authentication.class))).thenReturn(admin);
        SecurityPolicy updated = SecurityPolicy.builder()
                .id(UUID.randomUUID())
                .mfaRequired(false).sessionTimeoutMinutes(30)
                .maxLoginAttempts(7).minPasswordLength(12)
                .isActive(true).build();
        when(securityPolicyService.update(anyBoolean(), anyInt(), anyInt(), anyInt(), any(User.class)))
                .thenReturn(updated);

        Map<String, Object> body = Map.of(
                "mfaRequired", false, "sessionTimeoutMinutes", 30,
                "maxLoginAttempts", 7, "minPasswordLength", 12);

        mockMvc.perform(put("/api/v1/admin/security-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mfaRequired").value(false))
                .andExpect(jsonPath("$.data.minPasswordLength").value(12));
    }

    @Test
    @WithMockUser(roles = "SUPPLIER", username = "supplier")
    void getPolicy_asNonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/security-policy"))
                .andExpect(status().isForbidden());
    }
}
