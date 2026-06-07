package com.oct.invoicesystem.domain.auth.controller;

import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.LoginResponse;
import com.oct.invoicesystem.domain.auth.dto.PasswordResetConfirmRequest;
import com.oct.invoicesystem.domain.auth.dto.PasswordResetRequest;
import com.oct.invoicesystem.domain.auth.dto.RefreshTokenRequest;
import com.oct.invoicesystem.domain.auth.dto.SupplierRegistrationRequest;
import com.oct.invoicesystem.domain.auth.service.AuthService;
import com.oct.invoicesystem.domain.mfa.dto.MfaConfirmRequest;
import com.oct.invoicesystem.domain.mfa.dto.MfaSetupResponse;
import com.oct.invoicesystem.domain.mfa.dto.MfaValidateRequest;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, refresh token, MFA, enregistrement fournisseur")
public class AuthController {

    private final AuthService authService;
    private final org.springframework.context.MessageSource messageSource;

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Refresh token")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request), "Token refreshed"));
    }

    @PostMapping("/register/supplier")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Inscription fournisseur")
    public ResponseEntity<ApiResponse<Void>> registerSupplier(@Valid @RequestBody SupplierRegistrationRequest request, java.util.Locale locale) {
        authService.registerSupplier(request);
        return ResponseEntity.ok(ApiResponse.success(null, messageSource.getMessage("supplier.registration.success", null, locale)));
    }

    @GetMapping("/verify-email")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Vérifier email fournisseur")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token, java.util.Locale locale) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(null, messageSource.getMessage("supplier.email.verified", null, locale)));
    }

    @PostMapping("/forgot-password")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Demande de reset mot de passe")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success(null, "If an account exists for this email, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Confirmer reset mot de passe")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successful"));
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initialiser MFA (TOTP)")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(@AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(authService.setupMfa(currentUser)));
    }

    @PostMapping("/mfa/confirm")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Confirmer MFA setup")
    public ResponseEntity<ApiResponse<Void>> confirmMfa(
            @Valid @RequestBody MfaConfirmRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        authService.confirmMfa(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/mfa/validate")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Valider OTP — retourne JWT complet")
    public ResponseEntity<ApiResponse<LoginResponse>> validateMfa(@Valid @RequestBody MfaValidateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.validateMfa(request)));
    }
}
