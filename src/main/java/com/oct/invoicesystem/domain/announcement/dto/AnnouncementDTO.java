package com.oct.invoicesystem.domain.announcement.dto;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementDTO(
        UUID id,
        String title,
        String body,
        String severity,
        boolean active,
        Instant createdAt,
        Instant expiresAt
) {}
