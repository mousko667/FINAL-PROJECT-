package com.oct.invoicesystem.domain.auth.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for the system-wide security policy (P11-40 / REQ-02).
 */
public record SecurityPolicyDTO(
        UUID id,
        boolean mfaRequired,
        int sessionTimeoutMinutes,
        int maxLoginAttempts,
        int minPasswordLength,
        UUID updatedBy,
        Instant updatedAt
) {}
