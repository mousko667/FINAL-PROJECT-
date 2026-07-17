package com.oct.invoicesystem.domain.audit.controller;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchLogs_WithAdmin_ReturnsSuccess() throws Exception {
        when(auditService.searchLogs(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetSystemLogs() throws Exception {
        when(auditService.searchLogsWithActionFilter(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void searchLogs_WithDaf_ReturnsSuccess() throws Exception {
        when(auditService.searchLogs(any(), any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void searchLogs_WithUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden());
    }
}
