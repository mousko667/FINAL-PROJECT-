package com.oct.invoicesystem.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * HTTP Security Headers Filter
 * Adds security headers to all responses:
 * - X-Frame-Options: DENY (prevent clickjacking)
 * - X-Content-Type-Options: nosniff (prevent MIME sniffing)
 * - Content-Security-Policy: strict
 * - Strict-Transport-Security: HSTS
 */
@Component
@Slf4j
public class HttpSecurityHeadersFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    
    // Prevent clickjacking attacks
    response.setHeader("X-Frame-Options", "DENY");
    
    // Prevent MIME type sniffing (e.g., serving JS as text/html)
    response.setHeader("X-Content-Type-Options", "nosniff");
    
    // Content Security Policy — no unsafe-inline or unsafe-eval.
    // The React SPA is bundled into static files; all scripts and styles are external assets.
    // WebSocket connections to the same origin are permitted for STOMP notifications.
    response.setHeader("Content-Security-Policy",
        "default-src 'self'; " +
        "script-src 'self'; " +
        "style-src 'self'; " +
        "img-src 'self' data: https:; " +
        "font-src 'self'; " +
        "connect-src 'self' ws: wss:; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self'; " +
        "object-src 'none'");
    
    // HTTP Strict Transport Security (HSTS)
    // Force HTTPS for 1 year, include subdomains
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
    
    // Suppress server fingerprinting headers.
    // NOTE: we must NOT call setHeader(name, "") here — that emits the header with an
    // empty value, which OWASP ZAP still flags as an information-leak (rule 10037,
    // "Server Leaks Information via X-Powered-By"). The HttpServletResponse API has no
    // removeHeader(), so the correct fix is to never set these headers at all: Spring
    // Boot / Tomcat do not emit X-Powered-By by default, and the Server header is
    // stripped at the reverse-proxy (nginx) layer in production. Leaving them unset
    // keeps both headers absent from the response.
    // ⚠ LESSON LEARNED (PROB-068, 2026-06-27): setHeader(name, "") leaks an empty header.
    // See docs/KNOWN_ISSUES_REGISTRY.md for full context.

    filterChain.doFilter(request, response);
  }
}
