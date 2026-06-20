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

    /** Aggregated audit summary over [from, to], restricted to allowedActions (M10 #12). */
    com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO summarize(
            java.time.LocalDate from, java.time.LocalDate to, java.util.List<String> allowedActions);
}
