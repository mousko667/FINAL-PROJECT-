package com.oct.invoicesystem.domain.compliance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArchiveComplianceControllerIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void report_asAdmin_returnsOkWithSections() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/archive-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverage").exists())
                .andExpect(jsonPath("$.data.integrity").exists())
                .andExpect(jsonPath("$.data.retention").exists())
                .andExpect(jsonPath("$.data.lifecycle").exists())
                .andExpect(jsonPath("$.data.generatedAt").exists());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void report_asDaf_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/archive-report"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void report_asAssistantComptable_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/compliance/archive-report"))
                .andExpect(status().isForbidden());
    }
}
