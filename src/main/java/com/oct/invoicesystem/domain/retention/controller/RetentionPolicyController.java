package com.oct.invoicesystem.domain.retention.controller;

import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;
import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyDTO;
import com.oct.invoicesystem.domain.retention.dto.RetentionPolicyRequest;
import com.oct.invoicesystem.domain.retention.service.RetentionPolicyService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Configuration of the singleton document-retention policy (B2, M9 #7 / M14 #6). Technical/compliance
 * setting with no financial data — ADMIN only. Read by {@code DocumentRetentionJob} at runtime.
 */
@RestController
@RequestMapping("/api/v1/retention-policy")
@RequiredArgsConstructor
@Tag(name = "Retention Policy", description = "Configurable document-retention policy")
public class RetentionPolicyController {

    private final RetentionPolicyService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get the retention policy")
    public ApiResponse<RetentionPolicyDTO> get() {
        return ApiResponse.success(service.get());
    }

    @GetMapping("/compliance")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get retention compliance status")
    public ApiResponse<RetentionComplianceDTO> compliance() {
        return ApiResponse.success(service.evaluateCompliance());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update the retention policy")
    public ApiResponse<RetentionPolicyDTO> update(
            @Valid @RequestBody RetentionPolicyRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(service.update(request, currentUser), "retention_policy.updated");
    }
}
