package com.oct.invoicesystem.domain.audit.controller;

import com.oct.invoicesystem.domain.audit.dto.AuditLogDTO;
import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('DAF')")
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
        
        return ApiResponse.success(
                PagedResponse.of(result),
                "Audit logs retrieved successfully"
        );
    }
}
