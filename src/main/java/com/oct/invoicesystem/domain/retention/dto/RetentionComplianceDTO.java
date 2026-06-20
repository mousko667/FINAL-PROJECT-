package com.oct.invoicesystem.domain.retention.dto;

import com.oct.invoicesystem.domain.retention.model.RetentionComplianceStatus;

import java.time.Instant;

/** Computed retention-compliance view for the admin audit screen (M10 #10). */
public record RetentionComplianceDTO(
        RetentionComplianceStatus status,
        int retentionYears,
        boolean active,
        Instant lastSweepAt,
        Integer lastFlaggedCount,
        boolean sweepOverdue,
        Instant updatedAt
) {}
