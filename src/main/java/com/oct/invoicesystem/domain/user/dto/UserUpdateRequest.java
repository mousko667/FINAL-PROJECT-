package com.oct.invoicesystem.domain.user.dto;

import jakarta.validation.constraints.Email;
import java.util.List;

public record UserUpdateRequest(
        @Email String email,
        String firstName,
        String lastName,
        String preferredLang,
        List<String> roles
) {}
