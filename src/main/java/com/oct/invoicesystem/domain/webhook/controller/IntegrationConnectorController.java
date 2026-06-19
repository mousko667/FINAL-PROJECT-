package com.oct.invoicesystem.domain.webhook.controller;

import com.oct.invoicesystem.domain.webhook.dto.IntegrationConnectorDTO;
import com.oct.invoicesystem.domain.webhook.service.IntegrationConnectorService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
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

/** Configurable integration connectors (M12). ADMIN only. */
@RestController
@RequestMapping("/api/v1/integrations/connectors")
@RequiredArgsConstructor
@Tag(name = "Integration Connectors", description = "Configurable ERP/accounting/banking/DMS connectors")
public class IntegrationConnectorController {

    private final IntegrationConnectorService service;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List connectors")
    public ResponseEntity<ApiResponse<List<IntegrationConnectorDTO.Response>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a connector")
    public ResponseEntity<ApiResponse<IntegrationConnectorDTO.Response>> create(
            @Valid @RequestBody IntegrationConnectorDTO.Request req, Authentication authentication) {
        UUID actor = securityHelper.currentUserId(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(req, actor), "Connector created"));
    }

    @PostMapping("/{id}/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Test a connector's connection")
    public ResponseEntity<ApiResponse<IntegrationConnectorDTO.Response>> test(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.testConnection(id), "Connection tested"));
    }

    @PutMapping("/{id}/sync-schedule")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Configure a connector's sync schedule (interval in minutes; null disables)")
    public ResponseEntity<ApiResponse<IntegrationConnectorDTO.Response>> updateSchedule(
            @PathVariable UUID id, @RequestBody IntegrationConnectorDTO.ScheduleRequest req) {
        return ResponseEntity.ok(ApiResponse.success(
                service.updateSchedule(id, req.syncIntervalMinutes()), "Sync schedule updated"));
    }

    @PostMapping("/{id}/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger a connector synchronisation now")
    public ResponseEntity<ApiResponse<IntegrationConnectorDTO.Response>> syncNow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.syncNow(id), "Synchronisation triggered"));
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable/disable a connector")
    public ResponseEntity<ApiResponse<Void>> setEnabled(@PathVariable UUID id, @RequestParam boolean enabled) {
        service.setEnabled(id, enabled);
        return ResponseEntity.ok(ApiResponse.success(null, "Connector updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a connector")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Connector deleted"));
    }
}
