package com.oct.invoicesystem.domain.user.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for an active user session, as listed by admins.
 */
public record ActiveSessionDTO(
        UUID id,
        UUID userId,
        String username,
        String ipAddress,
        Instant createdAt,
        Instant expiresAt
) {}
