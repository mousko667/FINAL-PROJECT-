package com.oct.invoicesystem.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.auth.service.SecurityPolicyService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Restricts high-privilege users without verified MFA to setup endpoints only.
 * Runs after JWT authentication has established the security context.
 */
@Component
@RequiredArgsConstructor
public class MfaSetupEnforcementFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final SecurityPolicyService securityPolicyService;

    // In dev profile, allow accounts with mfa_verified=true (even if no secret) to pass through.
    // This lets pre-seeded dev accounts work without going through TOTP setup.
    @Value("${app.security.mfa.enforce-secret-check:true}")
    private boolean enforceSecretCheck;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            filterChain.doFilter(request, response);
            return;
        }

        // When enforceSecretCheck=true (production), mfa_verified=true is only trusted when
        // a real TOTP secret exists. When false (dev), mfa_verified=true is trusted as-is,
        // allowing pre-seeded accounts without a TOTP device to access the system.
        boolean trulyVerified;
        if (enforceSecretCheck) {
            trulyVerified = user.isMfaVerified()
                    && user.getMfaSecret() != null
                    && !user.getMfaSecret().isBlank();
        } else {
            trulyVerified = user.isMfaVerified();
        }

        // Policy check is last so the `||` short-circuits: the DB read only happens for a
        // privilege-role user who is not yet verified and not on a setup path (a rare case).
        if (!requiresMandatoryMfa(user) || trulyVerified || isAllowedPath(request.getRequestURI())
                || !securityPolicyService.getActivePolicy().getMfaRequired()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error("mfa_setup_required", null)));
    }

    private boolean isAllowedPath(String requestUri) {
        return "/api/v1/auth/mfa/setup".equals(requestUri)
                || "/api/v1/auth/mfa/confirm".equals(requestUri);
    }

    private boolean requiresMandatoryMfa(User user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(role -> "ROLE_ADMIN".equals(role)
                        || "ROLE_DAF".equals(role)
                        || "ROLE_ASSISTANT_COMPTABLE".equals(role)
                        || role.startsWith("ROLE_VALIDATEUR_N1_")
                        || role.startsWith("ROLE_VALIDATEUR_N2_"));
    }
}
