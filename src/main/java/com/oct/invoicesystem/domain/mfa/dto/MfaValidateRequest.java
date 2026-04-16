package com.oct.invoicesystem.domain.mfa.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaValidateRequest {
    @NotBlank
    private String preAuthToken;

    @NotBlank
    private String otp;
}
