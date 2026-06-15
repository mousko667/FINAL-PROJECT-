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
                userId, entityType, null, action, SYSTEM_ACTIONS, pageable);
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
                userId, entityType, entityId, action, FINANCIAL_ACTIONS, pageable);
        return ApiResponse.success(PagedResponse.of(result), "audit.financial.retrieved");
    }

    /**
     * Legacy combined endpoint — kept for backwards compatibility, accessible to both roles
     * but each role only sees their own subset (enforced at service layer).
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")
    @Operation(summary = "Recherche combinée", description = "Recherche dans les logs autorisés pour le rôle courant")
    public ApiResponse<PagedResponse<AuditLogDTO>> searchLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditLogDTO> result = auditService.searchLogs(userId, entityType, entityId, action, pageable);
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
            @RequestParam(required = false) String action) {
        var fmt = com.oct.invoicesystem.shared.export.TabularExportService.Format.from(format);
        Pageable pageable = PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createdAt"));
        java.util.List<AuditLogDTO> logs =
                auditService.searchLogs(null, entityType, null, action, pageable).getContent();
        java.util.List<String> headers = java.util.List.of("Date", "User ID", "Action", "Entity", "Entity ID", "IP");
        java.util.List<java.util.List<String>> rows = logs.stream().map(l -> java.util.List.of(
                l.createdAt() == null ? "" : l.createdAt().toString(),
                l.userId() == null ? "" : l.userId().toString(),
                l.action() == null ? "" : l.action(),
                l.entityType() == null ? "" : l.entityType(),
                l.entityId() == null ? "" : l.entityId(),
                l.ipAddress() == null ? "" : l.ipAddress())).toList();
        byte[] body = tabularExportService.export(fmt, "Audit", headers, rows);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=audit_export." + fmt.extension)
                .contentType(org.springframework.http.MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }
}
