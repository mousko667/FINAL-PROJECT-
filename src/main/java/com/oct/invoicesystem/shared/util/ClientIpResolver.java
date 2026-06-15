package com.oct.invoicesystem.shared.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single source of truth for resolving the originating client IP behind a proxy.
 * Prefers {@code X-Forwarded-For} (first hop), then {@code X-Real-IP}, then the socket address.
 *
 * <p>Consolidated from the previously-duplicated logic in {@code AuditLoggingFilter} and
 * {@code RateLimitingFilter} so every place that records a client IP (audit log, rate limiter,
 * document-access log) resolves it identically.
 *
 * <p>NOTE: {@code X-Forwarded-For} is client-controllable and must only be trusted when the app
 * sits behind a trusted reverse proxy that overwrites it. Ensure forwarded-headers handling is
 * enabled at the platform/proxy layer for this to be authoritative.
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) {
            return xri;
        }
        return request.getRemoteAddr();
    }
}
