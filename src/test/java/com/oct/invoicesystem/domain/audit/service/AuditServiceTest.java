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
    void getLogs_ReturnsPage() {
        PageRequest pr = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(pr)).thenReturn(Page.empty());
        Page<AuditLogDTO> result = auditService.getLogs(pr);
        assertNotNull(result);
    }
    
    @Test
    void getLogsByUser_ReturnsPage() {
        PageRequest pr = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), eq(pr))).thenReturn(Page.empty());
        Page<AuditLogDTO> result = auditService.getLogsByUser(UUID.randomUUID(), pr);
        assertNotNull(result);
    }
    
    @Test
    void getLogsByEntity_ReturnsPage() {
        PageRequest pr = PageRequest.of(0, 10);
        when(auditLogRepository.findAll(any(Specification.class), eq(pr))).thenReturn(Page.empty());
        Page<AuditLogDTO> result = auditService.getLogsByEntity("INVOICE", "123", pr);
        assertNotNull(result);
    }
}
