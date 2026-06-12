package com.oct.invoicesystem.shared.filter;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.user.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link AuditLoggingFilter} classifies HTTP-originated audit entries into
 * action values ("HTTP_REQUEST_FINANCIAL" / "HTTP_REQUEST_SYSTEM") that
 * {@code AuditController}'s SYSTEM_ACTIONS / FINANCIAL_ACTIONS allow-lists actually contain.
 * Regression test for REQ-17: previously the filter wrote "FINANCIAL_ACTION" / "SYSTEM_ACTION"
 * into entityType and a free-form "METHOD URI -> STATUS" string into action, neither of which
 * matched any value in those allow-lists, so /audit-logs/system and /audit-logs/financial
 * always returned zero rows for HTTP-originated entries.
 */
@ExtendWith(MockitoExtension.class)
class AuditLoggingFilterTest {

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_OnInvoiceWritePost_LogsFinancialAction() throws Exception {
        AuditLoggingFilter filter = new AuditLoggingFilter(auditService);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/invoices");
        when(request.getUserPrincipal()).thenReturn(null);
        when(response.getStatus()).thenReturn(201);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        ArgumentCaptor<String> entityTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).logAction(any(), entityTypeCaptor.capture(), any(),
                actionCaptor.capture(), any(), any(), any(), any());

        assertEquals("FINANCIAL_ACTION", entityTypeCaptor.getValue());
        assertEquals("HTTP_REQUEST_FINANCIAL", actionCaptor.getValue());
    }

    @Test
    void doFilter_OnUserAdminPost_LogsSystemAction() throws Exception {
        AuditLoggingFilter filter = new AuditLoggingFilter(auditService);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getUserPrincipal()).thenReturn(null);
        when(response.getStatus()).thenReturn(201);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> entityTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).logAction(any(), entityTypeCaptor.capture(), any(),
                actionCaptor.capture(), any(), any(), any(), any());

        assertEquals("SYSTEM_ACTION", entityTypeCaptor.getValue());
        assertEquals("HTTP_REQUEST_SYSTEM", actionCaptor.getValue());
    }

    @Test
    void doFilter_OnUnclassifiedWritePost_LogsHttpRequest() throws Exception {
        AuditLoggingFilter filter = new AuditLoggingFilter(auditService);

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/ocr/extract");
        when(request.getUserPrincipal()).thenReturn(null);
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).logAction(any(), any(), any(),
                actionCaptor.capture(), any(), any(), any(), any());

        assertEquals("HTTP_REQUEST", actionCaptor.getValue());
    }

    @Test
    void doFilter_OnAuthenticatedRequest_LogsActingUserId() throws Exception {
        AuditLoggingFilter filter = new AuditLoggingFilter(auditService);

        UUID userId = UUID.randomUUID();
        User principal = User.builder().id(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/invoices");
        when(request.getUserPrincipal()).thenReturn(null);
        when(response.getStatus()).thenReturn(201);

        filter.doFilterInternal(request, response, filterChain);

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(auditService).logAction(userIdCaptor.capture(), any(), any(),
                any(), any(), any(), any(), any());

        assertEquals(userId, userIdCaptor.getValue());
    }

    @Test
    void doFilter_OnGetWithOkStatus_DoesNotPersistAuditLog() throws Exception {
        AuditLoggingFilter filter = new AuditLoggingFilter(auditService);

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/invoices");
        when(response.getStatus()).thenReturn(200);

        filter.doFilterInternal(request, response, filterChain);

        verify(auditService, org.mockito.Mockito.never())
                .logAction(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
