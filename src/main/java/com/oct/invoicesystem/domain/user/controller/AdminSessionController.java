package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.auth.service.AdminSessionService;
import com.oct.invoicesystem.domain.user.dto.ActiveSessionDTO;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sessions")
@RequiredArgsConstructor
@Tag(name = "Admin — Sessions", description = "Gestion des sessions actives (Admin only)")
public class AdminSessionController {

    private final AdminSessionService adminSessionService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister toutes les sessions actives")
    public ResponseEntity<ApiResponse<List<ActiveSessionDTO>>> listActiveSessions() {
        return ResponseEntity.ok(ApiResponse.success(adminSessionService.listActiveSessions()));
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Révoquer toutes les sessions d'un utilisateur")
    public ResponseEntity<ApiResponse<Void>> revokeUserSessions(@PathVariable UUID userId) {
        adminSessionService.revokeUserSessions(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Sessions révoquées"));
    }
}
