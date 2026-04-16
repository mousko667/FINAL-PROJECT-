package com.oct.invoicesystem.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

        if (!requiresMandatoryMfa(user) || user.isMfaVerified() || isAllowedPath(request.getRequestURI())) {
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
                        || role.startsWith("ROLE_VALIDATEUR_N1_")
                        || role.startsWith("ROLE_VALIDATEUR_N2_"));
    }
}
