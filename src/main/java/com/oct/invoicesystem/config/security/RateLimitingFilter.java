package com.oct.invoicesystem.config.security;

import com.oct.invoicesystem.shared.util.ClientIpResolver;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter for auth endpoints (login + refresh).
 * Limit: 5 requests per minute per IP address.
 */
@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

  // Map to store buckets per IP address
  private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
  
  // 5 requests per minute
  private static final int REQUESTS_PER_MINUTE = 5;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    
    String path = request.getRequestURI();
    
    // Only apply rate limiting to auth endpoints
    if (path.contains("/api/v1/auth/login") || path.contains("/api/v1/auth/refresh")) {
      String clientIp = getClientIp(request);
      Bucket bucket = cache.computeIfAbsent(clientIp, k -> createNewBucket());
      
      if (!bucket.tryConsume(1)) {
        log.warn("Rate limit exceeded for IP: {}", clientIp);
        response.setStatus(429); // Too Many Requests
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Too many requests. Please try again later.\"}");
        return;
      }
    }
    
    filterChain.doFilter(request, response);
  }

  private Bucket createNewBucket() {
    Bandwidth limit = Bandwidth.classic(REQUESTS_PER_MINUTE, Refill.intervally(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)));
    return Bucket4j.builder()
        .addLimit(limit)
        .build();
  }

  private String getClientIp(HttpServletRequest request) {
    return ClientIpResolver.resolve(request);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
    return false; // Apply to all requests, will check path inside
  }
}
