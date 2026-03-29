package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.workflow.dto.ApprovalRequest;
import com.oct.invoicesystem.domain.workflow.dto.RejectRequest;
import com.oct.invoicesystem.domain.workflow.service.ApprovalService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/workflow")
@RequiredArgsConstructor
@Tag(name = "Approval Workflow", description = "Endpoints for the invoice Bon à Payer workflow")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/assign")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Assign reviewer", description = "Self-assigns the current user as the reviewer for the current workflow step")
    public ResponseEntity<ApiResponse<Void>> assignReviewer(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId) {
        approvalService.assignReviewer(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(null, "action.assign.success"));
    }

    @PostMapping("/validate-n1")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate N1", description = "Records N1 validation decision")
    public ResponseEntity<ApiResponse<Void>> validateN1(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @RequestBody(required = false) ApprovalRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.validateN1(invoiceId, comment);
        return ResponseEntity.ok(ApiResponse.success(null, "action.validate.success"));
    }

    @PostMapping("/validate-n2")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validate N2", description = "Records N2 validation decision")
    public ResponseEntity<ApiResponse<Void>> validateN2(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @RequestBody(required = false) ApprovalRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.validateN2(invoiceId, comment);
        return ResponseEntity.ok(ApiResponse.success(null, "action.validate.success"));
    }

    @PostMapping("/bon-a-payer")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bon à Payer", description = "Final DAF approval for the invoice")
    public ResponseEntity<ApiResponse<Void>> bonAPayer(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @RequestBody(required = false) ApprovalRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.bonAPayer(invoiceId, comment);
        return ResponseEntity.ok(ApiResponse.success(null, "action.bon_a_payer.success"));
    }

    @PostMapping("/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reject Invoice", description = "Rejects the invoice back to the submitter")
    public ResponseEntity<ApiResponse<Void>> reject(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @Valid @RequestBody RejectRequest request) {
        approvalService.reject(invoiceId, request.getRejectionReason());
        return ResponseEntity.ok(ApiResponse.success(null, "action.reject.success"));
    }
}
