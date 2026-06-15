package com.oct.invoicesystem.domain.announcement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 2000) String body,
        String severity,         // INFO | WARNING | CRITICAL (defaults to INFO)
        Instant expiresAt        // optional
) {}
