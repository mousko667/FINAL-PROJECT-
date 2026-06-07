package com.oct.invoicesystem.domain.audit.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;

import java.util.List;
import java.util.UUID;

public interface AuditService {

    void logAction(UUID userId, String entityType, String entityId, String action, Object oldValue, Object newValue, String ipAddress, String userAgent);

    Page<AuditLogDTO> searchLogs(UUID userId, String entityType, String entityId, String action, Pageable pageable);

    /** Filtered search restricted to a given set of allowed action types. */
    Page<AuditLogDTO> searchLogsWithActionFilter(UUID userId, String entityType, String entityId, String action, List<String> allowedActions, Pageable pageable);
}
