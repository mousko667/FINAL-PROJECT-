package com.oct.invoicesystem.domain.user.dto;

import java.util.UUID;

/**
 * Lightweight view of an assignable role, used by the permission-matrix editor (P11-18) to map
 * role names to their UUIDs (required by {@code PUT /users/{id}/roles}).
 */
public record RoleDTO(
        UUID id,
        String name,
        String description
) {}
