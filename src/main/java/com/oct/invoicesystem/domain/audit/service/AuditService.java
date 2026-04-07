package com.oct.invoicesystem.domain.audit.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;

import java.util.UUID;

public interface AuditService {
    
    /**
     * Async append-only logging of an action.
     */
    void logAction(UUID userId, String entityType, String entityId, String action, Object oldValue, Object newValue, String ipAddress, String userAgent);

    /**
     * Read-only fetch for controllers.
     */
    // Unified search for controllers
    Page<AuditLogDTO> searchLogs(UUID userId, String entityType, String entityId, String action, Pageable pageable);
}
