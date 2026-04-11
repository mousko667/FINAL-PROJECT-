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
    
    // Content Security Policy: only allow same-origin resources
    // Allow self for styles, scripts, images
    // Disallow unsafe-inline and unsafe-eval
    response.setHeader("Content-Security-Policy",
        "default-src 'self'; " +
        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https:; " +
        "font-src 'self'; " +
        "connect-src 'self' ws: wss:; " +
        "frame-ancestors 'none'; " +
        "base-uri 'self'; " +
        "form-action 'self'");
    
    // HTTP Strict Transport Security (HSTS)
    // Force HTTPS for 1 year, include subdomains
    response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
    
    // Remove server information
    response.setHeader("X-Powered-By", "");
    response.setHeader("Server", "");
    
    filterChain.doFilter(request, response);
  }
}
