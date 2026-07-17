package com.oct.invoicesystem.domain.audit.controller;

import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;
import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Audit log access is split by role per OCT briefing §5.5 and §5.6:
 * - ROLE_ADMIN  → system/security logs (logins, role changes, integration events)
 * - ROLE_DAF    → financial logs (invoice, approval, payment events)
 *
 * System action keywords (Admin-visible): HTTP_REQUEST, LOGIN, LOGOUT,
 *   ROLE_CHANGE, USER_CREATE, USER_UPDATE, INTEGRATION, SECURITY
 * Financial action keywords (DAF-visible): INVOICE, APPROVE, REJECT,
 *   PAYMENT, MATCHING, ARCHIVE, SUBMIT, BON_A_PAYER
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Journal d'audit système (Admin) et financier (DAF)")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;
    private final com.oct.invoicesystem.domain.audit.service.AuditAnomalyService auditAnomalyService;
    private final com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;
    private final org.springframework.context.MessageSource messageSource;
    private final com.oct.invoicesystem.shared.util.SecurityHelper securityHelper;

    // System/security audit trail — Administrator only
    private static final List<String> SYSTEM_ACTIONS = List.of(
            "HTTP_REQUEST", "HTTP_REQUEST_SYSTEM", "LOGIN", "LOGOUT", "ROLE_CHANGE", "USER_CREATE",
            "USER_UPDATE", "USER_DELETE", "PROFILE_UPDATE", "INTEGRATION", "SECURITY", "CONFIG_CHANGE",
            "MFA", "MFA_RESET", "ACCESS_DENIED"
    );

    // Financial audit trail — CFO (DAF) only
    private static final List<String> FINANCIAL_ACTIONS = List.of(
            "HTTP_REQUEST_FINANCIAL", "INVOICE_CREATE", "INVOICE_SUBMIT", "INVOICE_UPDATE", "INVOICE_DELETE",
            "APPROVE", "REJECT", "BON_A_PAYER", "PAYMENT", "MATCHING", "ARCHIVE",
            "MATCHING_OVERRIDE", "RESUBMIT", "ACCESS_DENIED", "RETENTION_FLAG"
    );

    /**
     * System/security audit trail — ROLE_ADMIN only.
     * Filtered to system and security events only.
     */
    @GetMapping("/system")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Journal système", description = "Événements de sécurité et d'administration (ADMIN uniquement)")
    public ApiResponse<PagedResponse<AuditLogDTO>> getSystemLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogDTO> result = auditService.searchLogsWithActionFilter(
                userId, entityType, null, action, SYSTEM_ACTIONS, null, null, pageable);
        return ApiResponse.success(PagedResponse.of(result), "audit.system.retrieved");
    }

    /**
     * Financial audit trail — ROLE_DAF only.
     * Filtered to invoice, approval, and payment events only.
     */
    @GetMapping("/financial")
    @PreAuthorize("hasRole('DAF')")
    @Operation(summary = "Journal financier", description = "Événements de facturation et paiement (DAF uniquement)")
    public ApiResponse<PagedResponse<AuditLogDTO>> getFinancialLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogDTO> result = auditService.searchLogsWithActionFilter(
                userId, entityType, entityId, action, FINANCIAL_ACTIONS, null, null, pageable);
        return ApiResponse.success(PagedResponse.of(result), "audit.financial.retrieved");
    }

    /**
     * Returns the audit action set the current user is allowed to see, enforcing the system/financial
     * separation of duties (audit finding N3, PROB-065): ADMIN sees only system/security actions,
     * DAF sees only financial actions. Without this, the combined endpoint leaked both journals to
     * both roles (DAF reading the system journal, admin reading the financial one).
     */
    private List<String> allowedActionsForCurrentUser(org.springframework.security.core.Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin ? SYSTEM_ACTIONS : FINANCIAL_ACTIONS;
    }

    /**
     * Legacy combined endpoint — accessible to both roles, but each role only sees its own action
     * subset (system for ADMIN, financial for DAF), enforced here via the allowed-action filter so
     * the two journals never cross (audit finding N3).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")
    @Operation(summary = "Recherche combinée", description = "Recherche dans les logs autorisés pour le rôle courant")
    public ApiResponse<PagedResponse<AuditLogDTO>> searchLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            org.springframework.security.core.Authentication authentication
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        // N3: route to the role-scoped action set (like /summary/export) instead of the unfiltered search.
        Page<AuditLogDTO> result = auditService.searchLogsWithActionFilter(
                userId, entityType, entityId, action, allowedActionsForCurrentUser(authentication), from, to, pageable);
        return ApiResponse.success(PagedResponse.of(result), "audit.retrieved");
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Détection d'anomalies d'audit (statistique)",
            description = "Utilisateurs au volume d'activité anormal ou aux accès refusés excessifs (M10)")
    public ApiResponse<java.util.List<com.oct.invoicesystem.domain.audit.dto.AuditAnomalyDTO>> getAnomalies() {
        return ApiResponse.success(auditAnomalyService.detectAnomalies());
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")
    @Operation(summary = "Export du journal d'audit (csv|excel|pdf)")
    public org.springframework.http.ResponseEntity<byte[]> exportLogs(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.Instant to,
            java.util.Locale locale,
            org.springframework.security.core.Authentication authentication) {
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        java.util.Locale loc = locale != null ? locale : java.util.Locale.getDefault();
        // Force ASC sort by createdAt for export, as requested by the user
        Pageable pageable = PageRequest.of(0, 10000, Sort.by(Sort.Direction.ASC, "createdAt"));
        // N3: export only the role-scoped action set (ADMIN=system, DAF=financial), never the
        // unfiltered journal — otherwise the export leaks the other role's journal to a file.
        java.util.List<AuditLogDTO> logs =
                auditService.searchLogsWithActionFilter(null, entityType, null, action,
                        allowedActionsForCurrentUser(authentication), from, to, pageable).getContent();
        java.util.List<String> headers = java.util.List.of(
                messageSource.getMessage("export.header.audit.date", null, loc),
                messageSource.getMessage("export.header.audit.user", null, loc),
                messageSource.getMessage("export.header.audit.action", null, loc),
                messageSource.getMessage("export.header.audit.entity", null, loc),
                messageSource.getMessage("export.header.audit.entity_id", null, loc),
                messageSource.getMessage("export.header.audit.ip", null, loc));
        java.util.List<java.util.List<String>> rows = logs.stream().map(l -> java.util.List.of(
                l.createdAt() == null ? "" : l.createdAt().toString(),
                l.userId() == null ? "" : l.userId().toString(),
                l.action() == null ? "" : l.action(),
                l.entityType() == null ? "" : l.entityType(),
                l.entityId() == null ? "" : l.entityId(),
                l.ipAddress() == null ? "" : l.ipAddress())).toList();
        String title = messageSource.getMessage("export.title.audit", null, loc);
        String periodLabel = null;
        if (from != null || to != null) {
            String fromStr = from != null ? from.toString() : "...";
            String toStr = to != null ? to.toString() : "...";
            periodLabel = messageSource.getMessage("export.pdf.period", new Object[]{fromStr, toStr}, loc);
        }
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(securityHelper.currentUser(authentication), messageSource, periodLabel, null, loc);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=audit_export." + fmt.extension)
                .contentType(org.springframework.http.MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

    // ---- M10 #12 : aggregated audit summary report (role-scoped, SoD per PROB-065) ----

    private java.util.List<String> actionsForScope(String scope) {
        return "financial".equalsIgnoreCase(scope) ? FINANCIAL_ACTIONS : SYSTEM_ACTIONS;
    }

    private java.time.LocalDate orDefault(java.time.LocalDate v, java.time.LocalDate fallback) {
        return v == null ? fallback : v;
    }

    @GetMapping("/summary/system")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rapport de synthèse audit système", description = "Totaux agrégés des événements système (ADMIN)")
    public ApiResponse<com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO> systemSummary(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        java.time.LocalDate t = orDefault(to, java.time.LocalDate.now());
        java.time.LocalDate f = orDefault(from, t.minusDays(30));
        return ApiResponse.success(auditService.summarize(f, t, SYSTEM_ACTIONS), "audit.summary.retrieved");
    }

    @GetMapping("/summary/financial")
    @PreAuthorize("hasRole('DAF')")
    @Operation(summary = "Rapport de synthèse audit financier", description = "Totaux agrégés des événements financiers (DAF)")
    public ApiResponse<com.oct.invoicesystem.domain.audit.dto.AuditSummaryDTO> financialSummary(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        java.time.LocalDate t = orDefault(to, java.time.LocalDate.now());
        java.time.LocalDate f = orDefault(from, t.minusDays(30));
        return ApiResponse.success(auditService.summarize(f, t, FINANCIAL_ACTIONS), "audit.summary.retrieved");
    }

    @GetMapping("/summary/export")
    @PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")
    @Operation(summary = "Export du rapport de synthèse audit (csv|excel|pdf)")
    public org.springframework.http.ResponseEntity<byte[]> exportSummary(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "system") String scope,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            java.util.Locale locale,
            org.springframework.security.core.Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isDaf = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_DAF"));
        boolean financial = "financial".equalsIgnoreCase(scope);
        // SoD guard: ADMIN may only export system, DAF may only export financial.
        if ((financial && !isDaf) || (!financial && !isAdmin)) {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        java.util.Locale loc = locale != null ? locale : java.util.Locale.getDefault();
        java.time.LocalDate t = orDefault(to, java.time.LocalDate.now());
        java.time.LocalDate f = orDefault(from, t.minusDays(30));
        var summary = auditService.summarize(f, t, actionsForScope(scope));
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        java.util.List<String> headers = java.util.List.of(
                messageSource.getMessage("export.header.audit.dimension", null, loc),
                messageSource.getMessage("export.header.audit.label", null, loc),
                messageSource.getMessage("export.header.audit.count", null, loc));
        java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
        appendDim(rows, messageSource.getMessage("export.header.audit.action", null, loc), summary.byAction());
        appendDim(rows, messageSource.getMessage("export.header.audit.user", null, loc), summary.byUser());
        appendDim(rows, messageSource.getMessage("export.header.audit.entity", null, loc), summary.byEntityType());
        appendDim(rows, "Jour", summary.byDay());
        String title = messageSource.getMessage("export.title.audit_summary", null, loc);
        String period = messageSource.getMessage("export.pdf.period", new Object[]{f.toString(), t.toString()}, loc);
        com.oct.invoicesystem.shared.export.ReportMetadata meta =
                com.oct.invoicesystem.shared.export.ReportMetadata.of(securityHelper.currentUser(auth), messageSource, period, null, loc);
        byte[] body = tabularExportService.export(fmt, title, headers, rows, meta, messageSource);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=audit_summary." + fmt.extension)
                .contentType(org.springframework.http.MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

    private void appendDim(java.util.List<java.util.List<String>> rows, String dim,
                           java.util.List<com.oct.invoicesystem.domain.audit.dto.CountEntry> entries) {
        for (var e : entries) {
            rows.add(java.util.List.of(dim, e.label() == null ? "" : e.label(), String.valueOf(e.count())));
        }
    }
}
