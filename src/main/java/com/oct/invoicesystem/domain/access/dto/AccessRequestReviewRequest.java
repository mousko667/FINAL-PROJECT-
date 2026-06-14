package com.oct.invoicesystem.domain.access.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin review decision for a pending access request (P11-17).
 * {@code approve = true} adds the requested role to the requester; {@code false} rejects it.
 * An optional comment explains the decision (typically a rejection reason).
 */
public record AccessRequestReviewRequest(
        @NotNull Boolean approve,
        @Size(max = 1000) String comment
) {}
