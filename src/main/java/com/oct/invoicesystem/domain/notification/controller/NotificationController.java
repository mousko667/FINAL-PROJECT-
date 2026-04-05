package com.oct.invoicesystem.domain.notification.controller;

import com.oct.invoicesystem.domain.notification.dto.NotificationDTO;
import com.oct.invoicesystem.domain.notification.service.NotificationService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification management")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * List notifications for the authenticated user (paginated).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List my notifications")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDTO>>> listMyNotifications(
            @AuthenticationPrincipal User currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {

        Page<NotificationDTO> page = notificationService.getMyNotifications(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.of(page)));
    }

    /**
     * Count unread notifications for the authenticated user.
     */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Count unread notifications")
    public ResponseEntity<ApiResponse<Long>> countUnread(@AuthenticationPrincipal User currentUser) {
        long count = notificationService.countUnread(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(count, "notification.unread.count"));
    }

    /**
     * Mark a specific notification as read.
     */
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {

        notificationService.markAsRead(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "notification.marked.read"));
    }

    /**
     * Mark all notifications as read for the authenticated user.
     */
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal User currentUser) {
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(null, "notification.marked.all.read"));
    }
}
