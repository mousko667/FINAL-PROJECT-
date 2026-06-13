package com.oct.invoicesystem.domain.auth.controller;

import com.oct.invoicesystem.domain.auth.dto.SecurityPolicyDTO;
import com.oct.invoicesystem.domain.auth.dto.SecurityPolicyUpdateRequest;
import com.oct.invoicesystem.domain.auth.model.SecurityPolicy;
import com.oct.invoicesystem.domain.auth.service.SecurityPolicyService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/security-policy")
@RequiredArgsConstructor
@Tag(name = "Security Policy", description = "Politique de sécurité système (MFA, sessions, mots de passe)")
public class SecurityPolicyController {

    private final SecurityPolicyService securityPolicyService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get the active system-wide security policy")
    public ResponseEntity<ApiResponse<SecurityPolicyDTO>> getPolicy() {
        return ResponseEntity.ok(ApiResponse.success(toDTO(securityPolicyService.getActivePolicy())));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update the system-wide security policy (ADMIN only)")
    public ResponseEntity<ApiResponse<SecurityPolicyDTO>> updatePolicy(
            @Valid @RequestBody SecurityPolicyUpdateRequest request,
            Authentication authentication) {
        User admin = securityHelper.currentUser(authentication);
        SecurityPolicy updated = securityPolicyService.update(
                request.mfaRequired(),
                request.sessionTimeoutMinutes(),
                request.maxLoginAttempts(),
                request.minPasswordLength(),
                admin);
        return ResponseEntity.ok(ApiResponse.success(toDTO(updated), "security_policy.updated"));
    }

    private SecurityPolicyDTO toDTO(SecurityPolicy p) {
        return new SecurityPolicyDTO(
                p.getId(),
                p.getMfaRequired(),
                p.getSessionTimeoutMinutes(),
                p.getMaxLoginAttempts(),
                p.getMinPasswordLength(),
                p.getUpdatedBy() != null ? p.getUpdatedBy().getId() : null,
                p.getUpdatedAt());
    }
}
