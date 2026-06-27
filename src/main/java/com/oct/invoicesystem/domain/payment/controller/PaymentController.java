package com.oct.invoicesystem.domain.payment.controller;

import com.oct.invoicesystem.domain.payment.dto.BatchPaymentRequest;
import com.oct.invoicesystem.domain.payment.dto.BatchPaymentResultDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.domain.payment.dto.RemittanceAdviceDTO;
import com.oct.invoicesystem.domain.payment.service.PaymentService;
import com.oct.invoicesystem.domain.payment.service.RemittanceAdviceService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.export.TabularExportService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final RemittanceAdviceService remittanceAdviceService;

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

    @PostMapping("/batch")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Record payments for several invoices at once (best-effort, per-line result)")
    public ResponseEntity<ApiResponse<BatchPaymentResultDTO>> recordBatchPayment(
            @Valid @RequestBody BatchPaymentRequest request,
            @AuthenticationPrincipal User currentUser) {
        BatchPaymentResultDTO result = paymentService.recordBatchPayment(request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(result, "payment.batch.processed"));
    }

    @PostMapping("/{paymentId}/process")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Mark a scheduled payment as processed (triggers remittance + archive)")
    public ResponseEntity<ApiResponse<PaymentDTO>> processPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal User currentUser) {
        PaymentDTO dto = paymentService.processPayment(paymentId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(dto, "payment.processed.success"));
    }

    @GetMapping("/invoice/{invoiceId}")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")
    @Operation(summary = "Get payment details for an invoice")
    public ResponseEntity<ApiResponse<PaymentDTO>> getPaymentByInvoiceId(@PathVariable UUID invoiceId) {
        PaymentDTO paymentDTO = paymentService.getPaymentByInvoiceId(invoiceId);
        return ResponseEntity.ok(ApiResponse.success(paymentDTO));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")
    @Operation(summary = "List all payments with optional department filtering")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDTO>>> listPayments(
            @RequestParam(required = false) String departmentCode,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        Page<PaymentDTO> page = paymentService.listPayments(departmentCode, pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(page)));
    }

    @GetMapping("/{paymentId}/remittance")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")
    @Operation(summary = "Get pre-signed URL for remittance advice PDF download")
    public ResponseEntity<ApiResponse<String>> getRemittanceDownloadUrl(@PathVariable UUID paymentId) {
        String downloadUrl = remittanceAdviceService.getDownloadUrl(paymentId);
        return ResponseEntity.ok(ApiResponse.success(downloadUrl, "remittance.download.url.generated"));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ASSISTANT_COMPTABLE', 'DAF', 'ADMIN')")
    @Operation(summary = "Export payments (csv|excel|pdf)",
            description = "Unified export of the payment history in the requested format")
    public ResponseEntity<byte[]> exportPayments(
            @RequestParam(required = false) String departmentCode,
            @RequestParam(defaultValue = "csv") String format) {
        TabularExportService.Format fmt = TabularExportService.Format.from(format);
        byte[] body = paymentService.exportPayments(departmentCode, fmt);
        String mediaType = fmt == TabularExportService.Format.CSV
                ? fmt.mediaType + "; charset=UTF-8"
                : fmt.mediaType;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payments." + fmt.extension)
                .contentType(MediaType.parseMediaType(mediaType))
                .body(body);
    }
}
