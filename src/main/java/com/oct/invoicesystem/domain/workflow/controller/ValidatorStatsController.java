package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.workflow.dto.ValidatorStatsResponse;
import com.oct.invoicesystem.domain.workflow.service.ApprovalService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Dashboard stats scoped to the current validator/approver (REQ-04 / P11-42).
 * Lives outside {@code ApprovalController} because that controller is invoice-scoped
 * ({@code /invoices/{invoiceId}/workflow}).
 */
@RestController
@RequestMapping("/api/v1/workflow/my-stats")
@RequiredArgsConstructor
@Tag(name = "Validator Stats", description = "Tableau de bord du validateur — ses propres métriques")
public class ValidatorStatsController {

    private final ApprovalService approvalService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    @Operation(summary = "Get the current user's approval stats (approved total, processed this month)")
    public ResponseEntity<ApiResponse<ValidatorStatsResponse>> myStats(Authentication authentication) {
        UUID userId = securityHelper.currentUser(authentication).getId();
        return ResponseEntity.ok(ApiResponse.success(approvalService.getValidatorStats(userId)));
    }
}
