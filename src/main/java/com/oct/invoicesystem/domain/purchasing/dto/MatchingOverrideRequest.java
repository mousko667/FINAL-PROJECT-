package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for overriding a three-way matching mismatch.
 * Used by DAF/Admin to force an invoice through despite discrepancies.
 */
public record MatchingOverrideRequest(
        @NotBlank(message = "validation.mismatch_override_reason_required")
        @Size(min = 10, max = 500, message = "validation.mismatch_override_reason_length")
        String overrideReason
) {}
