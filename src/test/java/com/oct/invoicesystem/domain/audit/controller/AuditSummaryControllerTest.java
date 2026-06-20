package com.oct.invoicesystem.domain.audit.controller;

import com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO;
import com.oct.invoicesystem.domain.audit.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditSummaryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AuditService auditService;

    @BeforeEach
    void stubSummary() {
        when(auditService.summarize(any(), any(), any())).thenReturn(new AuditSummaryDTO(
                LocalDate.now().minusDays(30), LocalDate.now(), 0L,
                List.of(), List.of(), List.of(), List.of()));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void systemSummary_allowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/system")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "DAF")
    void systemSummary_forbiddenForDaf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/system")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "DAF")
    void financialSummary_allowedForDaf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/financial")).andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "DAF")
    void export_systemScope_forbiddenForDaf() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/summary/export").param("scope", "system"))
                .andExpect(status().isForbidden());
    }
}
