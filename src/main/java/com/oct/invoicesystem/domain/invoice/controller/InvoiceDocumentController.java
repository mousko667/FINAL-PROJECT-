package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.invoice.dto.BulkUploadResultDTO;
import com.oct.invoicesystem.domain.invoice.dto.InvoiceDocumentDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.service.InvoiceDocumentService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.oct.invoicesystem.shared.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices/{invoiceId}/documents")
@RequiredArgsConstructor
@Tag(name = "Invoice Documents", description = "Endpoints for invoice document management")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceDocumentController {

    private final InvoiceDocumentService invoiceDocumentService;

    @PostMapping
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Upload document", description = "Uploads and validates an invoice document")
    public ResponseEntity<ApiResponse<InvoiceDocumentDTO>> upload(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {
        InvoiceDocument uploaded = invoiceDocumentService.upload(invoiceId, file, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDto(uploaded), "Document uploaded successfully"));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Bulk upload documents",
            description = "Uploads multiple invoice documents in one request; returns a per-file outcome report")
    public ResponseEntity<ApiResponse<BulkUploadResultDTO>> uploadBulk(
            @PathVariable UUID invoiceId,
            @RequestParam("files") List<MultipartFile> files,
            Authentication authentication) {
        BulkUploadResultDTO result = invoiceDocumentService.uploadMultiple(invoiceId, files, authentication.getName());
        // 201 if every file landed; 207-style "partial" still returns 201 with the report (errors inside).
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Bulk upload processed"));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List documents", description = "Lists all documents for an invoice")
    public ResponseEntity<ApiResponse<List<InvoiceDocumentDTO>>> list(@PathVariable UUID invoiceId) {
        List<InvoiceDocumentDTO> documents = invoiceDocumentService.listByInvoice(invoiceId)
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/{docId}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get download URL",
            description = "Re-verifies SHA-256 integrity, records an access-log entry, then returns a pre-signed URL")
    public ResponseEntity<ApiResponse<Map<String, String>>> download(
            @PathVariable UUID invoiceId,
            @PathVariable UUID docId,
            Authentication authentication,
            HttpServletRequest request) throws Exception {
        // P11-50: log who downloaded what, when, and from where (append-only access trail).
        // Resolve the client IP the same way AuditLoggingFilter/RateLimitingFilter do (XFF-aware).
        String url = invoiceDocumentService.generateDownloadUrlAndLog(
                invoiceId, docId, authentication.getName(),
                ClientIpResolver.resolve(request), request.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    private InvoiceDocumentDTO toDto(InvoiceDocument document) {
        return new InvoiceDocumentDTO(
                document.getId(),
                document.getInvoice() != null ? document.getInvoice().getId() : null,
                document.getOriginalFilename(),
                document.getFileType(),
                document.getFileSizeBytes(),
                document.getChecksumSha256(),
                document.getUploadedBy() != null ? document.getUploadedBy().getId() : null,
                document.getUploadedAt(),
                document.getVersion(),
                document.getSupersededByDocumentId()
        );
    }
}
