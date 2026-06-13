package com.oct.invoicesystem.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private UUID userId;
    private String username;
    private String firstName;
    private String lastName;
    private List<String> roles;

    /** Inactivity timeout in minutes — the frontend signs the user out after this idle period. */
    @JsonProperty("session_timeout_minutes")
    private Integer sessionTimeoutMinutes;

    @JsonProperty("mfa_required")
    private Boolean mfaRequired;

    @JsonProperty("mfa_setup_required")
    private Boolean mfaSetupRequired;

    @JsonProperty("pre_auth_token")
    private String preAuthToken;
}
