package com.oct.invoicesystem.domain.security.controller;

import com.oct.invoicesystem.domain.security.dto.SecurityHealthDTO;
import com.oct.invoicesystem.domain.security.service.SecurityHealthService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Security-health dashboard endpoint (P11-53 / REQ-24, partial scope). ADMIN only — this is
 * operational security posture (encryption, MFA adoption, login failures, webhook reliability),
 * not financial data.
 */
@RestController
@RequestMapping("/api/v1/admin/security-health")
@RequiredArgsConstructor
@Tag(name = "Security Health", description = "Operational security posture dashboard")
@SecurityRequirement(name = "bearerAuth")
public class SecurityHealthController {

    private final SecurityHealthService securityHealthService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get the security-health snapshot",
            description = "Encryption coverage, MFA adoption, login-failure trend, webhook delivery success (ADMIN only)")
    public ResponseEntity<ApiResponse<SecurityHealthDTO>> getSecurityHealth() {
        return ResponseEntity.ok(ApiResponse.success(securityHealthService.getSecurityHealth()));
    }
}
