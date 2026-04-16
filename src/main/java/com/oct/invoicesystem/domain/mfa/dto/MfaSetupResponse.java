package com.oct.invoicesystem.domain.mfa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {
    private String qrCodeUrl;
    private String secret;
}
