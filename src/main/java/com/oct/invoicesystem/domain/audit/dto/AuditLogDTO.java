package com.oct.invoicesystem.domain.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDTO(
        UUID id,
        UUID userId,
        String entityType,
        String entityId,
        String action,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {}
