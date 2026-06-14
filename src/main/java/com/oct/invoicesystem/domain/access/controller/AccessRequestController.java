package com.oct.invoicesystem.domain.access.controller;

import com.oct.invoicesystem.domain.access.dto.AccessRequestCreateRequest;
import com.oct.invoicesystem.domain.access.dto.AccessRequestDTO;
import com.oct.invoicesystem.domain.access.dto.AccessRequestReviewRequest;
import com.oct.invoicesystem.domain.access.model.AccessRequestStatus;
import com.oct.invoicesystem.domain.access.service.AccessRequestService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Self-service access-request workflow (P11-17 / REQ-23 item 3).
 * Staff create / view their own requests; ADMIN reviews the queue and approves or rejects.
 */
@RestController
@RequestMapping("/api/v1/access-requests")
@RequiredArgsConstructor
@Tag(name = "Access Requests", description = "Self-service role access-request workflow")
@SecurityRequirement(name = "bearerAuth")
public class AccessRequestController {

    private final AccessRequestService accessRequestService;
    private final SecurityHelper securityHelper;

    @PostMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    @Operation(summary = "Request access to a role",
            description = "Any authenticated staff user (not suppliers) can request one additional role")
    public ResponseEntity<ApiResponse<AccessRequestDTO>> create(
            @Valid @RequestBody AccessRequestCreateRequest request,
            Authentication authentication) {
        UUID requesterId = securityHelper.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accessRequestService.create(requesterId, request),
                        "Access request submitted"));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPPLIER')")
    @Operation(summary = "List my access requests",
            description = "Returns the authenticated user's own access requests")
    public ResponseEntity<ApiResponse<PagedResponse<AccessRequestDTO>>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID requesterId = securityHelper.currentUserId(authentication);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(accessRequestService.listMine(requesterId, pageable)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List access requests (admin queue)",
            description = "Returns access requests, optionally filtered by status (Admin only)")
    public ResponseEntity<ApiResponse<PagedResponse<AccessRequestDTO>>> list(
            @RequestParam(required = false) AccessRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(accessRequestService.list(status, pageable)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Review an access request",
            description = "Approve (grants the requested role) or reject a pending request (Admin only)")
    public ResponseEntity<ApiResponse<AccessRequestDTO>> review(
            @PathVariable UUID id,
            @Valid @RequestBody AccessRequestReviewRequest request,
            Authentication authentication) {
        UUID reviewerId = securityHelper.currentUserId(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                accessRequestService.review(id, reviewerId, request), "Access request reviewed"));
    }
}
