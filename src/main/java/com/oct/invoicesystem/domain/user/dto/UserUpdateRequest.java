package com.oct.invoicesystem.domain.user.dto;

import jakarta.validation.constraints.Email;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UserUpdateRequest(
        // Optional: null keeps the current username. When present, the service enforces uniqueness
        // and revokes the user's active sessions (username is the login identifier).
        @jakarta.validation.constraints.Size(min = 3, max = 100) String username,
        @Email String email,
        String firstName,
        String lastName,
        String preferredLang,
        String employeeId,
        UUID departmentId,
        BigDecimal approvalLimit,
        List<String> roles
) {
    public UserUpdateRequest(
            String email,
            String firstName,
            String lastName,
            String preferredLang,
            List<String> roles
    ) {
        this(null, email, firstName, lastName, preferredLang, null, null, null, roles);
    }
}
