package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.workflow.dto.ApprovalRequest;
import com.oct.invoicesystem.domain.workflow.dto.ApprovalStepResponse;
import com.oct.invoicesystem.domain.workflow.dto.RejectRequest;
import com.oct.invoicesystem.domain.workflow.dto.RejectionReasonOption;
import com.oct.invoicesystem.domain.workflow.model.RejectionReasonCode;
import com.oct.invoicesystem.domain.workflow.service.ApprovalService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/workflow")
@RequiredArgsConstructor
@Tag(name = "Approval Workflow", description = "Endpoints for the invoice Bon à Payer workflow")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final org.springframework.context.MessageSource messageSource;

    @GetMapping("/rejection-reasons")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    @Operation(summary = "List predefined rejection reasons",
               description = "Returns the selectable rejection reason codes with localized labels")
    public ResponseEntity<ApiResponse<List<RejectionReasonOption>>> rejectionReasons(java.util.Locale locale) {
        List<RejectionReasonOption> options = java.util.Arrays.stream(RejectionReasonCode.values())
                .map(c -> new RejectionReasonOption(
                        c.name(),
                        messageSource.getMessage("invoice.reject.code." + c.name(), null, c.name(), locale)))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(options));
    }

    @GetMapping("/steps")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    @Operation(summary = "Get approval steps for an invoice")
    public ResponseEntity<ApiResponse<List<ApprovalStepResponse>>> getApprovalSteps(
            @PathVariable UUID invoiceId) {
        return ResponseEntity.ok(ApiResponse.success(approvalService.getApprovalSteps(invoiceId)));
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE','DAF','ADMIN') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_DRH') or hasAuthority('ROLE_VALIDATEUR_N1_DG') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFO') or hasAuthority('ROLE_VALIDATEUR_N1_TERM') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_COM') or hasAuthority('ROLE_VALIDATEUR_N1_QHSSE') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFRA') or hasAuthority('ROLE_VALIDATEUR_N1_TECH') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_INFO') or hasAuthority('ROLE_VALIDATEUR_N2_INFRA') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_TECH')")
    @Operation(summary = "Assign reviewer", description = "Self-assigns the current user as the reviewer for the current workflow step")
    public ResponseEntity<ApiResponse<Void>> assignReviewer(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId) {
        approvalService.assignReviewer(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(null, "action.assign.success"));
    }

    @PostMapping("/validate-n1")
    @PreAuthorize("hasRole('DAF') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_DRH') or hasAuthority('ROLE_VALIDATEUR_N1_DG') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFO') or hasAuthority('ROLE_VALIDATEUR_N1_TERM') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_COM') or hasAuthority('ROLE_VALIDATEUR_N1_QHSSE') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFRA') or hasAuthority('ROLE_VALIDATEUR_N1_TECH') " +
                  "or hasRole('ADMIN')")
    @Operation(summary = "Validate N1", description = "Records N1 validation decision")
    public ResponseEntity<ApiResponse<Void>> validateN1(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @RequestBody(required = false) ApprovalRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.validateN1(invoiceId, comment);
        return ResponseEntity.ok(ApiResponse.success(null, "action.validate.success"));
    }

    @PostMapping("/validate-n2")
    @PreAuthorize("hasAuthority('ROLE_VALIDATEUR_N2_INFO') or hasAuthority('ROLE_VALIDATEUR_N2_INFRA') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_TECH') or hasRole('ADMIN')")
    @Operation(summary = "Validate N2", description = "Records N2 validation decision")
    public ResponseEntity<ApiResponse<Void>> validateN2(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @RequestBody(required = false) ApprovalRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.validateN2(invoiceId, comment);
        return ResponseEntity.ok(ApiResponse.success(null, "action.validate.success"));
    }

    @PostMapping("/bon-a-payer")
    @PreAuthorize("hasRole('DAF') or hasRole('ADMIN')")
    @Operation(summary = "Bon à Payer", description = "Final DAF approval for the invoice")
    public ResponseEntity<ApiResponse<Void>> bonAPayer(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @RequestBody(required = false) ApprovalRequest request) {
        String comment = request != null ? request.getComment() : null;
        approvalService.bonAPayer(invoiceId, comment);
        return ResponseEntity.ok(ApiResponse.success(null, "action.bon_a_payer.success"));
    }

    @PostMapping("/reject")
    @PreAuthorize("hasRole('DAF') or hasRole('ADMIN') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_DRH') or hasAuthority('ROLE_VALIDATEUR_N1_DG') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFO') or hasAuthority('ROLE_VALIDATEUR_N1_TERM') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_COM') or hasAuthority('ROLE_VALIDATEUR_N1_QHSSE') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N1_INFRA') or hasAuthority('ROLE_VALIDATEUR_N1_TECH') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_INFO') or hasAuthority('ROLE_VALIDATEUR_N2_INFRA') " +
                  "or hasAuthority('ROLE_VALIDATEUR_N2_TECH')")
    @Operation(summary = "Reject Invoice", description = "Rejects the invoice back to the submitter")
    public ResponseEntity<ApiResponse<Void>> reject(
            @Parameter(description = "UUID of the invoice") @PathVariable UUID invoiceId,
            @Valid @RequestBody RejectRequest request) {
        String detail = request.getRejectionReason() == null ? "" : request.getRejectionReason().trim();
        if (request.getReasonCode() == RejectionReasonCode.AUTRE && detail.length() < 10) {
            throw new com.oct.invoicesystem.shared.exception.ValidationException(
                    "A detail of at least 10 characters is required when the reason is \"Other\".");
        }
        String composed = detail.isEmpty()
                ? "[" + request.getReasonCode().name() + "]"
                : "[" + request.getReasonCode().name() + "] " + detail;
        approvalService.reject(invoiceId, composed);
        return ResponseEntity.ok(ApiResponse.success(null, "action.reject.success"));
    }
}
