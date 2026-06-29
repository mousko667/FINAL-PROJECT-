package com.oct.invoicesystem.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link HttpSecurityHeadersFilter}.
 *
 * <p>Covers the happy path (all hardening headers present) plus two edge cases that
 * matter for the OWASP ZAP baseline scan (G3):
 * <ul>
 *   <li>fingerprinting headers must be ABSENT — not merely empty (ZAP rule 10037);</li>
 *   <li>the filter chain must always continue so the response is not swallowed.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HttpSecurityHeadersFilterTest {

  private final HttpSecurityHeadersFilter filter = new HttpSecurityHeadersFilter();

  @Test
  void addsAllSecurityHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
    assertThat(response.getHeader("Strict-Transport-Security"))
        .isEqualTo("max-age=31536000; includeSubDomains; preload");
  }

  @Test
  void doesNotEmitFingerprintingHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    // Regression for PROB-085: setHeader(name, "") used to leak an empty header that
    // ZAP rule 10037 still flagged. The headers must be completely absent.
    assertThat(response.containsHeader("X-Powered-By")).isFalse();
    assertThat(response.containsHeader("Server")).isFalse();
  }

  @Test
  void alwaysContinuesFilterChain() throws Exception {
    HttpServletRequest request = new MockHttpServletRequest();
    HttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
