package com.oct.invoicesystem.domain.payment.controller;

import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.domain.payment.service.PaymentService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Invoice payment tracking")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Record a new payment for an invoice (Triggers Archive)")
    public ResponseEntity<ApiResponse<PaymentDTO>> recordPayment(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal User currentUser) {

        PaymentDTO paymentDTO = paymentService.recordPayment(invoiceId, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(paymentDTO, "payment.recorded.success"));
    }

    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'AUDITEUR', 'ADMIN')")
    @Operation(summary = "Get payment details for an invoice")
    public ResponseEntity<ApiResponse<PaymentDTO>> getPaymentByInvoiceId(@PathVariable UUID invoiceId) {
        PaymentDTO paymentDTO = paymentService.getPaymentByInvoiceId(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(paymentDTO));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'AUDITEUR', 'ADMIN')")
    @Operation(summary = "List all payments with optional department filtering")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDTO>>> listPayments(
            @RequestParam(required = false) String departmentCode,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        Page<PaymentDTO> page = paymentService.listPayments(departmentCode, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(page)));
    }
}
