package com.oct.invoicesystem.domain.retention.dto;

import java.time.Instant;

/** View of the singleton retention policy (B2). */
public record RetentionPolicyDTO(
        int retentionYears,
        boolean active,
        Instant lastSweepAt,
        Integer lastFlaggedCount,
        Instant updatedAt
) {}
