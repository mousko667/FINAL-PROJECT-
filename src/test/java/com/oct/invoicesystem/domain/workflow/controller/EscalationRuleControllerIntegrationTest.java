package com.oct.invoicesystem.domain.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EscalationRuleControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "DAF")
    void list_asDaf_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/escalation-rules"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returnsCreated() throws Exception {
        EscalationRuleRequest req = new EscalationRuleRequest(24, "After 1 day", true);
        mockMvc.perform(post("/api/v1/escalation-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void list_asAssistantComptable_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/escalation-rules"))
                .andExpect(status().isForbidden());
    }
}
