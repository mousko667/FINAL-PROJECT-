package com.oct.invoicesystem.domain.access.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for a staff user requesting one additional role (P11-17).
 * {@code requestedRole} must be one of the canonical assignable role names (validated server-side).
 */
public record AccessRequestCreateRequest(
        @NotBlank String requestedRole,
        @NotBlank @Size(max = 1000) String reason
) {}
