package com.oct.invoicesystem.shared.filter;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLoggingFilter extends OncePerRequestFilter {

    private final AuditService auditService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String username = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";

            log.info("AUDIT | {} {} | Status: {} | User: {} | Time: {}ms",
                    method, uri, status, username, duration);

            // Only persist write operations and access-denied events to the audit_logs DB table.
            // Read-only GET requests are retained in application logs only to avoid table bloat.
            if (!method.equalsIgnoreCase("GET") || status == 401 || status == 403) {
                try {
                    UUID userId = resolveUserId(request);
                    String ipAddress = ClientIpResolver.resolve(request);
                    String userAgent = request.getHeader("User-Agent");

                    String entityType = classifyEntityType(uri);
                    String action = classifyAction(uri);
                    String details = "{\"duration_ms\":" + duration + ", \"method\":\"" + method + "\", \"status\":" + status + "}";

                    auditService.logAction(userId, entityType, uri, action, null, details, ipAddress, userAgent);
                } catch (Exception ex) {
                    // Audit persistence failure must never affect the response
                    log.warn("Failed to persist HTTP audit log entry for {} {}: {}", method, uri, ex.getMessage());
                }
            }
        }
    }

    /**
     * Classifies the request into an audit action recognized by {@code AuditController}'s
     * SYSTEM_ACTIONS / FINANCIAL_ACTIONS allow-lists, so HTTP-originated audit entries are
     * actually retrievable via {@code GET /api/v1/audit-logs/system} and {@code /financial}.
     */
    private String classifyAction(String uri) {
        if (uri.contains("/invoices") || uri.contains("/payments")
                || uri.contains("/approvals") || uri.contains("/workflow")) {
            return "HTTP_REQUEST_FINANCIAL";
        }
        if (uri.contains("/auth") || uri.contains("/users")
                || uri.contains("/integrations") || uri.contains("/admin")) {
            return "HTTP_REQUEST_SYSTEM";
        }
        return "HTTP_REQUEST";
    }

    private String classifyEntityType(String uri) {
        if (uri.contains("/invoices") || uri.contains("/payments")
                || uri.contains("/approvals") || uri.contains("/workflow")) {
            return "FINANCIAL_ACTION";
        }
        if (uri.contains("/auth") || uri.contains("/users")
                || uri.contains("/integrations") || uri.contains("/admin")) {
            return "SYSTEM_ACTION";
        }
        return "HTTP_REQUEST";
    }

    private UUID resolveUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

}
