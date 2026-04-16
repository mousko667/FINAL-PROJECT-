package com.oct.invoicesystem.domain.auth.controller;

import com.oct.invoicesystem.domain.auth.dto.LoginRequest;
import com.oct.invoicesystem.domain.auth.dto.LoginResponse;
import com.oct.invoicesystem.domain.auth.dto.RefreshTokenRequest;
import com.oct.invoicesystem.domain.auth.dto.SupplierRegistrationRequest;
import com.oct.invoicesystem.domain.auth.service.AuthService;
import com.oct.invoicesystem.domain.mfa.dto.MfaConfirmRequest;
import com.oct.invoicesystem.domain.mfa.dto.MfaSetupResponse;
import com.oct.invoicesystem.shared.response.ApiResponse;
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
public class AuthController {

    private final AuthService authService;
    private final org.springframework.context.MessageSource messageSource;

    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request), "Login successful"));
    }

    @PostMapping("/refresh")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request), "Token refreshed"));
    }

    @PostMapping("/register/supplier")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Void>> registerSupplier(@Valid @RequestBody SupplierRegistrationRequest request, java.util.Locale locale) {
        authService.registerSupplier(request);
        return ResponseEntity.ok(ApiResponse.success(null, messageSource.getMessage("supplier.registration.success", null, locale)));
    }

    @GetMapping("/verify-email")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam("token") String token, java.util.Locale locale) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(null, messageSource.getMessage("supplier.email.verified", null, locale)));
    }

    @PostMapping("/mfa/setup")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setupMfa(@AuthenticationPrincipal UserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(authService.setupMfa(currentUser)));
    }

    @PostMapping("/mfa/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> confirmMfa(
            @Valid @RequestBody MfaConfirmRequest request,
            @AuthenticationPrincipal UserDetails currentUser) {
        authService.confirmMfa(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
