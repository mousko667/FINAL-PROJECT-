package com.oct.invoicesystem.domain.invoice.controller;

import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderCreateRequest;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderDTO;
import com.oct.invoicesystem.domain.invoice.dto.ArchiveFolderUpdateRequest;
import com.oct.invoicesystem.domain.invoice.service.ArchiveFolderService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/archive")
@RequiredArgsConstructor
@Tag(name = "Archive Folders", description = "Endpoints for managing archive folders")
@SecurityRequirement(name = "bearerAuth")
public class ArchiveFolderController {

    private final ArchiveFolderService archiveFolderService;
    private final SecurityHelper securityHelper;

    @GetMapping("/folders")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get folder tree", description = "Retrieves all archive folders as a flat tree list")
    public ResponseEntity<ApiResponse<List<ArchiveFolderDTO>>> getFolderTree() {
        return ResponseEntity.ok(ApiResponse.success(archiveFolderService.getFolderTree()));
    }

    @PostMapping("/folders")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create folder", description = "Creates a new archive folder")
    public ResponseEntity<ApiResponse<ArchiveFolderDTO>> createFolder(
            @Valid @RequestBody ArchiveFolderCreateRequest request,
            Authentication authentication) {
        User user = securityHelper.currentUser(authentication);
        ArchiveFolderDTO created = archiveFolderService.createFolder(request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "archive.folder.created"));
    }

    @PutMapping("/folders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update folder", description = "Renames or moves an archive folder")
    public ResponseEntity<ApiResponse<ArchiveFolderDTO>> updateFolder(
            @PathVariable UUID id,
            @Valid @RequestBody ArchiveFolderUpdateRequest request,
            Authentication authentication) {
        User user = securityHelper.currentUser(authentication);
        ArchiveFolderDTO updated = archiveFolderService.updateFolder(id, request, user);
        return ResponseEntity.ok(ApiResponse.success(updated, "archive.folder.updated"));
    }

    @DeleteMapping("/folders/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete folder", description = "Deletes an archive folder, its invoices become unclassified")
    public ResponseEntity<Void> deleteFolder(@PathVariable UUID id) {
        archiveFolderService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/invoices/{invoiceId}/folder")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Assign invoice to folder", description = "Assigns an archived invoice to a folder")
    public ResponseEntity<ApiResponse<Void>> assignInvoiceToFolder(
            @PathVariable UUID invoiceId,
            @RequestParam(required = false) UUID folderId,
            Authentication authentication) {
        User user = securityHelper.currentUser(authentication);
        archiveFolderService.assignInvoiceToFolder(invoiceId, folderId, user);
        return ResponseEntity.ok(ApiResponse.success(null, "archive.invoice.assigned"));
    }
}
