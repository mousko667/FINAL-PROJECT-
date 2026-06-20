package com.oct.invoicesystem.domain.retention.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetentionPolicyControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_asAdmin_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/retention-policy"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returnsOk() throws Exception {
        RetentionPolicyRequest req = new RetentionPolicyRequest(8, true);
        mockMvc.perform(put("/api/v1/retention-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_invalidYears_returnsBadRequest() throws Exception {
        RetentionPolicyRequest req = new RetentionPolicyRequest(0, true);
        mockMvc.perform(put("/api/v1/retention-policy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void get_asDaf_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/retention-policy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void get_asAssistantComptable_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/retention-policy"))
                .andExpect(status().isForbidden());
    }
}
