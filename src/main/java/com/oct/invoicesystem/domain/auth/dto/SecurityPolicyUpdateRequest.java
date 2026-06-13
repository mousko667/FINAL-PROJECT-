package com.oct.invoicesystem.domain.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for updating the system-wide security policy (P11-40 / REQ-02).
 */
public record SecurityPolicyUpdateRequest(
        @NotNull Boolean mfaRequired,

        @NotNull
        @Min(value = 5, message = "validation.session_timeout_min")
        @Max(value = 480, message = "validation.session_timeout_max")
        Integer sessionTimeoutMinutes,

        @NotNull
        @Min(value = 3, message = "validation.max_login_attempts_min")
        @Max(value = 10, message = "validation.max_login_attempts_max")
        Integer maxLoginAttempts,

        @NotNull
        @Min(value = 8, message = "validation.min_password_length_min")
        @Max(value = 64, message = "validation.min_password_length_max")
        Integer minPasswordLength
) {}
