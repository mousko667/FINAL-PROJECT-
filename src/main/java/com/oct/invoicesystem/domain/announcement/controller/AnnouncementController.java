package com.oct.invoicesystem.domain.announcement.controller;

import com.oct.invoicesystem.domain.announcement.dto.AnnouncementDTO;
import com.oct.invoicesystem.domain.announcement.dto.AnnouncementRequest;
import com.oct.invoicesystem.domain.announcement.service.AnnouncementService;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** System announcements (M2). Read = any authenticated user; write = ADMIN. */
@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@Tag(name = "Announcements", description = "System announcements shown on dashboards")
@SecurityRequirement(name = "bearerAuth")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List active announcements (all authenticated users)")
    public ResponseEntity<ApiResponse<List<AnnouncementDTO>>> getActive() {
        return ResponseEntity.ok(ApiResponse.success(announcementService.getActive()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all announcements (Admin)")
    public ResponseEntity<ApiResponse<List<AnnouncementDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(announcementService.getAll()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an announcement (Admin)")
    public ResponseEntity<ApiResponse<AnnouncementDTO>> create(
            @Valid @RequestBody AnnouncementRequest request, Authentication authentication) {
        UUID creatorId = securityHelper.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(announcementService.create(request, creatorId), "Announcement created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an announcement (Admin)")
    public ResponseEntity<ApiResponse<AnnouncementDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody AnnouncementRequest request) {
        return ResponseEntity.ok(ApiResponse.success(announcementService.update(id, request), "Announcement updated"));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate/deactivate an announcement (Admin)")
    public ResponseEntity<ApiResponse<Void>> setActive(
            @PathVariable UUID id, @RequestParam boolean active) {
        announcementService.setActive(id, active);
        return ResponseEntity.ok(ApiResponse.success(null, "Announcement updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an announcement (Admin)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        announcementService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Announcement deleted"));
    }
}
