package com.oct.invoicesystem.domain.user.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record UserDTO(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String preferredLang,
        boolean isActive,
        List<String> roles,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {}
