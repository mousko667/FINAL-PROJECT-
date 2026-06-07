package com.oct.invoicesystem.domain.user.dto;

import jakarta.validation.constraints.Email;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UserUpdateRequest(
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
        this(email, firstName, lastName, preferredLang, null, null, null, roles);
    }
}
