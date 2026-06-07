package com.oct.invoicesystem.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record UserCreateRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        @NotBlank String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String preferredLang,
        String employeeId,
        UUID departmentId,
        BigDecimal approvalLimit,
        List<String> roles
) {
    public UserCreateRequest(
            String username,
            String email,
            String password,
            String firstName,
            String lastName,
            String preferredLang,
            List<String> roles
    ) {
        this(username, email, password, firstName, lastName, preferredLang, null, null, null, roles);
    }
}
