package com.oct.invoicesystem.domain.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;
import com.oct.invoicesystem.domain.audit.model.AuditLog;
import com.oct.invoicesystem.domain.audit.repository.AuditLogRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditServiceImpl auditService;

    @Test
    void logAction_WithValidData_SavesLog() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\"}");

        Map<String, String> oldVal = Map.of("key", "value");
        Map<String, String> newVal = Map.of("key", "value");

        auditService.logAction(userId, "INVOICE", "INV-123", "CREATE", oldVal, newVal, "127.0.0.1", "Mozilla");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("INVOICE", saved.getEntityType());
        assertEquals("INV-123", saved.getEntityId());
        assertEquals("CREATE", saved.getAction());
        assertEquals("{\"key\":\"value\"}", saved.getOldValue());
        assertEquals("{\"key\":\"value\"}", saved.getNewValue());
        assertEquals("127.0.0.1", saved.getIpAddress());
        assertEquals("Mozilla", saved.getUserAgent());
        assertEquals(user, saved.getUser());
    }

    @Test
    void logAction_WithNullUser_SavesLogWithoutUser() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\"}");

        auditService.logAction(null, "SYSTEM", "SYS", "BOOT", "test", null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNull(saved.getUser());
    }

    @Test
    void logAction_WhenSerializationFails_SavesFallbackLog() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Error") {});

        auditService.logAction(null, "INVOICE", "123", "UPDATE", "old", "new", null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals("{\"error\": \"serialization_failed\"}", saved.getOldValue());
    }

    @Test
    void searchLogs_WithNoFilters_ReturnsPage() {
        PageRequest pr = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), eq(pr))).thenReturn(Page.empty());
        Page<AuditLogDTO> result = auditService.searchLogs(null, null, null, null, pr);
        assertNotNull(result);
    }
    
    @Test
    void searchLogs_WithFilters_ReturnsPage() {
        PageRequest pr = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), eq(pr))).thenReturn(Page.empty());
        Page<AuditLogDTO> result = auditService.searchLogs(UUID.randomUUID(), "INVOICE", "123", "CREATE", pr);
        assertNotNull(result);
    }

    /**
     * Regression test for REQ-17: an HTTP-originated audit entry for a financial endpoint
     * (action="HTTP_REQUEST_FINANCIAL", written by AuditLoggingFilter) must be retrievable
     * via searchLogsWithActionFilter when FINANCIAL_ACTIONS (AuditController's allow-list)
     * is passed as the allowed-actions set.
     */
    @Test
    void searchLogsWithActionFilter_WithHttpRequestFinancialAction_IsReturned() {
        PageRequest pr = PageRequest.of(0, 10);
        AuditLog entry = AuditLog.builder()
                .entityType("FINANCIAL_ACTION")
                .entityId("/api/v1/invoices")
                .action("HTTP_REQUEST_FINANCIAL")
                .build();

        when(auditLogRepository.findAll(any(Specification.class), eq(pr)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(entry)));

        java.util.List<String> financialActions = java.util.List.of(
                "HTTP_REQUEST_FINANCIAL", "INVOICE_CREATE", "INVOICE_SUBMIT", "INVOICE_UPDATE", "INVOICE_DELETE",
                "APPROVE", "REJECT", "BON_A_PAYER", "PAYMENT", "MATCHING", "ARCHIVE",
                "MATCHING_OVERRIDE", "RESUBMIT");

        Page<AuditLogDTO> result = auditService.searchLogsWithActionFilter(
                null, null, null, null, financialActions, pr);

        assertEquals(1, result.getTotalElements());
        assertEquals("HTTP_REQUEST_FINANCIAL", result.getContent().get(0).action());
    }

    @Test
    void summarize_assemblesDtoAndComputesTotal() {
        java.time.LocalDate from = java.time.LocalDate.now().minusDays(30);
        java.time.LocalDate to = java.time.LocalDate.now();
        java.util.List<String> actions = java.util.List.of("LOGIN", "USER_CREATE");

        when(auditLogRepository.summaryByAction(any(), any(), eq(actions)))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{"LOGIN", 5L}, new Object[]{"USER_CREATE", 2L}));
        when(auditLogRepository.summaryByUser(any(), any(), eq(actions)))
                .thenReturn(java.util.Arrays.<Object[]>asList(new Object[]{"alice", 4L}, new Object[]{null, 3L}));
        when(auditLogRepository.summaryByEntityType(any(), any(), eq(actions)))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{"User", 7L}));
        when(auditLogRepository.summaryByDay(any(), any(), eq(actions)))
                .thenReturn(java.util.List.<Object[]>of(new Object[]{java.sql.Date.valueOf(to), 7L}));

        com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO dto =
                auditService.summarize(from, to, actions);

        assertEquals(7L, dto.totalEvents()); // 5 + 2
        assertEquals(2, dto.byAction().size());
        assertEquals("alice", dto.byUser().get(0).label());
        // user null -> label fallback "—"
        assertEquals("—", dto.byUser().get(1).label());
    }
}
