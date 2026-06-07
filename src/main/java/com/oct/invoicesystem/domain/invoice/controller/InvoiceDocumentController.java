package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.invoice.dto.InvoiceDocumentDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceDocument;
import com.oct.invoicesystem.domain.invoice.service.InvoiceDocumentService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('ASSISTANT_COMPTABLE')")
    @Operation(summary = "Upload document", description = "Uploads and validates an invoice document")
    public ResponseEntity<ApiResponse<InvoiceDocumentDTO>> upload(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws Exception {
        UUID actorId = getActorId(authentication);
        InvoiceDocument uploaded = invoiceDocumentService.upload(invoiceId, file, actorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toDto(uploaded), "Document uploaded successfully"));
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
    @Operation(summary = "Get download URL", description = "Generates a pre-signed URL for document download")
    public ResponseEntity<ApiResponse<Map<String, String>>> download(
            @PathVariable UUID invoiceId,
            @PathVariable UUID docId) throws Exception {
        String url = invoiceDocumentService.generateDownloadUrl(invoiceId, docId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", url)));
    }

    private UUID getActorId(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
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
                document.getUploadedAt()
        );
    }
}
