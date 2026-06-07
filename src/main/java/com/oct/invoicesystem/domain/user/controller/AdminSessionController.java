package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.auth.model.ActiveSession;
import com.oct.invoicesystem.domain.auth.repository.ActiveSessionRepository;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/sessions")
@RequiredArgsConstructor
@Tag(name = "Admin — Sessions", description = "Gestion des sessions actives (Admin only)")
public class AdminSessionController {

    private final ActiveSessionRepository sessionRepository;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister toutes les sessions actives")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listActiveSessions() {
        List<Map<String, Object>> sessions = sessionRepository.findAllActive(Instant.now())
                .stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("userId", s.getUser().getId());
                    m.put("username", s.getUser().getUsername());
                    m.put("ipAddress", s.getIpAddress());
                    m.put("createdAt", s.getCreatedAt());
                    m.put("expiresAt", s.getExpiresAt());
                    return m;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @DeleteMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Révoquer toutes les sessions d'un utilisateur")
    public ResponseEntity<ApiResponse<Void>> revokeUserSessions(@PathVariable UUID userId) {
        sessionRepository.revokeAllForUser(userId, Instant.now());
        return ResponseEntity.ok(ApiResponse.success(null, "Sessions révoquées"));
    }
}
