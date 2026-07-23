package com.oct.invoicesystem.domain.retention.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.invoice.model.RetentionDisposition;
import com.oct.invoicesystem.domain.retention.dto.RetentionDispositionRequest;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.service.RetentionDispositionService;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetentionDispositionControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean RetentionDispositionService service;

    @Test
    @WithMockUser(roles = "ADMIN")
    void pending_asAdmin_returnsOk() throws Exception {
        when(service.listPendingExpired()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/retention/pending-documents"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void pending_asDaf_returnsOk() throws Exception {
        // AUDIT-009 / D5: the DAF is the second man on a purge, so the pending list — which now
        // also carries the purges the ADMIN proposed — must be readable by them.
        when(service.listPendingExpired()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/retention/pending-documents"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setDisposition_asAdmin_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setDisposition(eq(id), eq(RetentionDisposition.RETAINED), any()))
                .thenReturn(new RetentionPendingDocumentDTO(id, UUID.randomUUID(), "old.pdf", null, RetentionDisposition.RETAINED));
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.RETAINED))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void setDisposition_asDaf_reachesTheService() throws Exception {
        // AUDIT-009 / D5: the endpoint accepts the DAF (they confirm PURGED); WHICH transitions the
        // DAF may perform is a business rule enforced in the service, covered by
        // RetentionDispositionServiceTest.
        UUID id = UUID.randomUUID();
        when(service.setDisposition(eq(id), eq(RetentionDisposition.PURGED), any()))
                .thenReturn(new RetentionPendingDocumentDTO(id, UUID.randomUUID(), "old.pdf", null, RetentionDisposition.PURGED));
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.PURGED))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void setDisposition_asAssistantComptable_returnsForbidden() throws Exception {
        // Only the ADMIN (proposer) and the DAF (confirmer) take part in the two-man rule.
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.PURGED))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setDisposition_unknownDoc_returnsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setDisposition(eq(id), eq(RetentionDisposition.PURGED), any()))
                .thenThrow(new ResourceNotFoundException("Document not found with id " + id));
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RetentionDispositionRequest(RetentionDisposition.PURGED))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setDisposition_nullDisposition_returnsBadRequest() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(put("/api/v1/retention/documents/" + id + "/disposition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
