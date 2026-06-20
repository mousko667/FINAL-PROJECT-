package com.oct.invoicesystem.domain.retention.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Update payload for the singleton retention policy (B2). */
public record RetentionPolicyRequest(
        @Min(1) @Max(100) int retentionYears,
        boolean active
) {}
