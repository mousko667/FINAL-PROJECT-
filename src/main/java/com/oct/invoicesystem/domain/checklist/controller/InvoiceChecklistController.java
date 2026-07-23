package com.oct.invoicesystem.domain.checklist.controller;

import com.oct.invoicesystem.domain.checklist.dto.ChecklistResponseRequest;
import com.oct.invoicesystem.domain.checklist.dto.InvoiceChecklistDTO;
import com.oct.invoicesystem.domain.checklist.service.ChecklistService;
import com.oct.invoicesystem.domain.invoice.service.InvoiceService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Invoice-side validation checklist (B1, M4): the applicable template merged with any answers, and
 * an endpoint to record the validator's answers. Staff-only (suppliers excluded), like the rest of
 * the approval workflow.
 */
@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/checklist")
@RequiredArgsConstructor
@Tag(name = "Invoice Checklist", description = "Validation checklist shown during invoice review")
public class InvoiceChecklistController {

    private final ChecklistService checklistService;
    private final SecurityHelper securityHelper;
    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Get the validation checklist for an invoice")
    public ApiResponse<InvoiceChecklistDTO> get(@PathVariable UUID invoiceId, Authentication authentication) {
        // AUDIT-007/018: the checklist follows the invoice's own access rule.
        invoiceService.getByIdScoped(invoiceId, securityHelper.currentUser(authentication));
        return ApiResponse.success(checklistService.getInvoiceChecklist(invoiceId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER') and !hasRole('ADMIN')")
    @Operation(summary = "Record the validator's checklist answers for an invoice")
    public ApiResponse<InvoiceChecklistDTO> save(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody ChecklistResponseRequest request,
            Authentication authentication) {
        User actor = securityHelper.currentUser(authentication);
        // AUDIT-007/018: answering the checklist of another department's invoice is denied too.
        invoiceService.getByIdScoped(invoiceId, actor);
        return ApiResponse.success(checklistService.saveResponse(invoiceId, request, actor), "checklist.response.saved");
    }
}
