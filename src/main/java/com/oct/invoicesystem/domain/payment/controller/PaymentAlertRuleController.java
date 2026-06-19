package com.oct.invoicesystem.domain.payment.controller;

import com.oct.invoicesystem.domain.payment.dto.PaymentAlertRuleDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentAlertRuleRequest;
import com.oct.invoicesystem.domain.payment.service.PaymentAlertRuleService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Configuration of payment due-date alert rules (B4, M7). Financial setting: accessible to DAF and
 * ASSISTANT_COMPTABLE only (ADMIN is excluded from financial data by separation of duties).
 */
@RestController
@RequestMapping("/api/v1/payment-alert-rules")
@RequiredArgsConstructor
@Tag(name = "Payment Alert Rules", description = "Configurable payment due-date alert thresholds (J-N)")
public class PaymentAlertRuleController {

    private final PaymentAlertRuleService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "List payment alert rules")
    public ApiResponse<List<PaymentAlertRuleDTO>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Create a payment alert rule")
    public ApiResponse<PaymentAlertRuleDTO> create(
            @Valid @RequestBody PaymentAlertRuleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(service.create(request, currentUser), "payment_alert.created");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Update a payment alert rule")
    public ApiResponse<PaymentAlertRuleDTO> update(
            @PathVariable UUID id, @Valid @RequestBody PaymentAlertRuleRequest request) {
        return ApiResponse.success(service.update(id, request), "payment_alert.updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Delete a payment alert rule")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null, "payment_alert.deleted");
    }
}
