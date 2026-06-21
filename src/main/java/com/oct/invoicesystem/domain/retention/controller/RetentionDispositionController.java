package com.oct.invoicesystem.domain.retention.controller;

import com.oct.invoicesystem.domain.retention.dto.RetentionDispositionRequest;
import com.oct.invoicesystem.domain.retention.dto.RetentionPendingDocumentDTO;
import com.oct.invoicesystem.domain.retention.service.RetentionDispositionService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * ADMIN disposition of documents past their retention horizon (M10 #10 refinement).
 * Technical/compliance setting with no financial data — ADMIN only (SoD, PROB-065).
 */
@RestController
@RequestMapping("/api/v1/retention")
@RequiredArgsConstructor
@Tag(name = "Retention Disposition", description = "Disposition of documents past retention horizon")
public class RetentionDispositionController {

    private final RetentionDispositionService service;

    @GetMapping("/pending-documents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List documents past retention horizon still awaiting disposition")
    public ApiResponse<List<RetentionPendingDocumentDTO>> pending() {
        return ApiResponse.success(service.listPendingExpired());
    }

    @PutMapping("/documents/{id}/disposition")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Set a document's retention disposition (RETAINED or PURGED)")
    public ApiResponse<RetentionPendingDocumentDTO> setDisposition(
            @PathVariable UUID id,
            @Valid @RequestBody RetentionDispositionRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(
                service.setDisposition(id, request.disposition(), currentUser),
                "retention.disposition.updated");
    }
}
