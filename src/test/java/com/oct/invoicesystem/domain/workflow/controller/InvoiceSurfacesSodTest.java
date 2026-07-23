package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.checklist.service.ChecklistService;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.workflow.service.ApprovalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AUDIT-007 / AUDIT-018 — the satellite surfaces of an invoice (approval trail, checklist) must obey
 * the invoice's own access rule.
 *
 * <p>The runtime probe of the audit found the ADMIN and an out-of-department validator both getting
 * <strong>200</strong> on {@code /workflow/steps} and {@code /checklist} while {@code GET /invoices/{id}}
 * returned <strong>403</strong> for them. The non-regression rule these tests encode is therefore:
 * <em>if the invoice is denied, its approval trail and its checklist are denied too.</em></p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceSurfacesSodTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    @MockBean
    private ApprovalService approvalService;

    @MockBean
    private ChecklistService checklistService;

    @MockBean
    private com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void workflowSteps_asAdmin_returns403() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/{id}/workflow/steps", invoiceId))
                .andExpect(status().isForbidden());
        verify(approvalService, never()).getApprovalSteps(any());
    }

    @Test
    @WithMockUser(roles = "VALIDATEUR_N1_DRH", username = "drh")
    void workflowSteps_asValidatorOfAnotherDepartment_returns403() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceService.getByIdScoped(eq(invoiceId), any()))
                .thenThrow(new AccessDeniedException("error.access_denied"));

        mockMvc.perform(get("/api/v1/invoices/{id}/workflow/steps", invoiceId))
                .andExpect(status().isForbidden());
        // The nominative trail (who validated, when, with which comment) must not be built at all.
        verify(approvalService, never()).getApprovalSteps(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void checklistGet_asAdmin_returns403() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/invoices/{id}/checklist", invoiceId))
                .andExpect(status().isForbidden());
        verify(checklistService, never()).getInvoiceChecklist(any());
    }

    @Test
    @WithMockUser(roles = "VALIDATEUR_N1_DRH", username = "drh")
    void checklistGet_asValidatorOfAnotherDepartment_returns403() throws Exception {
        UUID invoiceId = UUID.randomUUID();
        when(invoiceService.getByIdScoped(eq(invoiceId), any()))
                .thenThrow(new AccessDeniedException("error.access_denied"));

        mockMvc.perform(get("/api/v1/invoices/{id}/checklist", invoiceId))
                .andExpect(status().isForbidden());
        verify(checklistService, never()).getInvoiceChecklist(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void checklistSave_asAdmin_returns403() throws Exception {
        UUID invoiceId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/invoices/{id}/checklist", invoiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChecklistPayload()))
                .andExpect(status().isForbidden());
        verify(checklistService, never()).saveResponse(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "VALIDATEUR_N1_DRH", username = "drh")
    void checklistSave_asValidatorOfAnotherDepartment_returns403() throws Exception {
        // Writing answers onto another department's invoice is a leak in the other direction.
        UUID invoiceId = UUID.randomUUID();
        when(invoiceService.getByIdScoped(eq(invoiceId), any()))
                .thenThrow(new AccessDeniedException("error.access_denied"));

        mockMvc.perform(post("/api/v1/invoices/{id}/checklist", invoiceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validChecklistPayload()))
                .andExpect(status().isForbidden());
        verify(checklistService, never()).saveResponse(any(), any(), any());
    }

    /**
     * A payload the backend actually accepts (templateId + items are both {@code @NotNull}), so a
     * 403 in these tests proves the authorization refusal and not a validation 400.
     */
    private String validChecklistPayload() {
        return "{\"templateId\":\"" + UUID.randomUUID() + "\",\"items\":[]}";
    }
}
