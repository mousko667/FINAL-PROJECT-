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
                    String action = classifyAction(method, uri, status);
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
     * Derives a *specific, human-meaningful* audit action from the HTTP method + path (e.g.
     * LOGIN, INVOICE_SUBMIT, APPROVE, PAYMENT, USER_CREATE) rather than the old coarse
     * HTTP_REQUEST_* tag — so the "Action" column actually differs from the "Entité" column and
     * tells the auditor what happened. Every value returned here is present in
     * {@code AuditController}'s SYSTEM_ACTIONS / FINANCIAL_ACTIONS allow-lists so the filtered
     * /system and /financial views keep working (the coarse HTTP_REQUEST_* remain as fallback).
     */
    private String classifyAction(String method, String uri, int status) {
        if (status == 401 || status == 403) {
            return "ACCESS_DENIED";
        }
        // Auth / security
        if (uri.endsWith("/auth/login")) return "LOGIN";
        if (uri.contains("/mfa/")) return "MFA";
        if (uri.contains("/auth")) return "SECURITY";

        // Invoices & workflow
        if (uri.contains("/resubmit")) return "RESUBMIT";
        if (uri.contains("/approvals") || uri.contains("/workflow")) {
            if (uri.contains("reject")) return "REJECT";
            return "APPROVE";
        }
        if (uri.contains("/payments")) return "PAYMENT";
        if (uri.contains("/matching")) return uri.contains("override") ? "MATCHING_OVERRIDE" : "MATCHING";
        if (uri.contains("/invoices")) {
            if ("POST".equalsIgnoreCase(method)) return "INVOICE_CREATE";
            if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) return "INVOICE_UPDATE";
            if ("DELETE".equalsIgnoreCase(method)) return "INVOICE_DELETE";
            return "INVOICE_SUBMIT";
        }

        // Users / roles / config
        if (uri.contains("/roles") || uri.contains("/permissions")) return "ROLE_CHANGE";
        if (uri.contains("/users")) {
            if ("POST".equalsIgnoreCase(method)) return "USER_CREATE";
            if ("DELETE".equalsIgnoreCase(method)) return "USER_DELETE";
            return "USER_UPDATE";
        }
        if (uri.contains("/profile")) return "PROFILE_UPDATE";
        if (uri.contains("/integrations") || uri.contains("/webhooks")) return "INTEGRATION";
        if (uri.contains("/security") || uri.contains("/sessions")) return "SECURITY";
        if (uri.contains("/departments") || uri.contains("/matching-config")) return "CONFIG_CHANGE";

        // Fallbacks keep the old coarse tags (still allow-listed) so nothing is lost.
        if (uri.contains("/invoices") || uri.contains("/payments")
                || uri.contains("/approvals") || uri.contains("/workflow")) {
            return "HTTP_REQUEST_FINANCIAL";
        }
        if (uri.contains("/admin")) {
            return "HTTP_REQUEST_SYSTEM";
        }
        return "HTTP_REQUEST";
    }

    /** The resource the action targeted — distinct from the action verb above. */
    private String classifyEntityType(String uri) {
        if (uri.contains("/invoices")) return "INVOICE";
        if (uri.contains("/payments")) return "PAYMENT";
        if (uri.contains("/approvals") || uri.contains("/workflow")) return "APPROVAL";
        if (uri.contains("/matching")) return "MATCHING";
        if (uri.contains("/users") || uri.contains("/roles") || uri.contains("/permissions")) return "USER";
        if (uri.contains("/suppliers") || uri.contains("/supplier")) return "SUPPLIER";
        if (uri.contains("/departments")) return "DEPARTMENT";
        if (uri.contains("/integrations") || uri.contains("/webhooks")) return "INTEGRATION";
        if (uri.contains("/auth") || uri.contains("/profile") || uri.contains("/admin")) return "SECURITY";
        return "SYSTEM";
    }

    private UUID resolveUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return null;
    }

}
