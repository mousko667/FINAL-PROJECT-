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

import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    @SuppressWarnings("unchecked")
    void searchLogs_WithAdmin_ScopedToSystemActions() throws Exception {
        // N3: the combined /audit-logs endpoint must route ADMIN to the SYSTEM action set only,
        // never the unfiltered journal (which would leak financial events to the admin).
        when(auditService.searchLogsWithActionFilter(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<String>> actions = ArgumentCaptor.forClass(List.class);
        verify(auditService).searchLogsWithActionFilter(any(), any(), any(), any(),
                actions.capture(), any(), any(), any(), any());
        assertThat(actions.getValue()).contains("LOGIN", "USER_CREATE", "SECURITY");
        assertThat(actions.getValue()).doesNotContain("PAYMENT", "BON_A_PAYER");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetSystemLogs() throws Exception {
        when(auditService.searchLogsWithActionFilter(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "DAF")
    @SuppressWarnings("unchecked")
    void searchLogs_WithDaf_ScopedToFinancialActions() throws Exception {
        // N3: the combined /audit-logs endpoint must route DAF to the FINANCIAL action set only,
        // never the system journal (login/user/security events are ADMIN-only).
        when(auditService.searchLogsWithActionFilter(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<List<String>> actions = ArgumentCaptor.forClass(List.class);
        verify(auditService).searchLogsWithActionFilter(any(), any(), any(), any(),
                actions.capture(), any(), any(), any(), any());
        assertThat(actions.getValue()).contains("PAYMENT", "BON_A_PAYER", "APPROVE");
        assertThat(actions.getValue()).doesNotContain("LOGIN", "USER_CREATE");
    }

    @Test
    @WithMockUser(roles = "USER")
    void searchLogs_WithUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void financialLogs_WithExcludeAction_ForwardsItToService() throws Exception {
        // Lot 9: the financial journal hides ACCESS_DENIED noise by default; the excludeAction
        // query param must reach the service so the exclusion happens server-side (accurate paging).
        when(auditService.searchLogsWithActionFilter(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/audit-logs/financial").param("excludeAction", "ACCESS_DENIED"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> excludeAction = ArgumentCaptor.forClass(String.class);
        verify(auditService).searchLogsWithActionFilter(any(), any(), any(), any(), any(),
                excludeAction.capture(), any(), any(), any());
        assertThat(excludeAction.getValue()).isEqualTo("ACCESS_DENIED");
    }
}
