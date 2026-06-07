package com.oct.invoicesystem.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.ZonedDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UserDTO(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String preferredLang,
        String employeeId,
        UUID departmentId,
        BigDecimal approvalLimit,
        @JsonProperty("isActive") boolean active,
        List<String> roles,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public UserDTO(
            UUID id,
            String username,
            String email,
            String firstName,
            String lastName,
            String preferredLang,
            boolean active,
            List<String> roles,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        this(id, username, email, firstName, lastName, preferredLang, null, null, null,
                active, roles, createdAt, updatedAt);
    }
}
