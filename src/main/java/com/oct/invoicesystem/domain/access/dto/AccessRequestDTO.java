package com.oct.invoicesystem.domain.access.dto;

import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Read model for an access request (P11-17). Carries denormalised requester / reviewer display
 * fields so the admin queue and the "my requests" view need no extra lookups.
 */
public record AccessRequestDTO(
        UUID id,
        UUID requesterId,
        String requesterUsername,
        String requesterName,
        String requestedRole,
        String reason,
        AccessRequestStatus status,
        UUID reviewedById,
        String reviewedByName,
        String reviewComment,
        ZonedDateTime createdAt,
        ZonedDateTime reviewedAt
) {}
