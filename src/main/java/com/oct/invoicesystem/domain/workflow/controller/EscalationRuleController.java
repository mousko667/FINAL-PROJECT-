package com.oct.invoicesystem.domain.workflow.controller;

import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleDTO;
import com.oct.invoicesystem.domain.workflow.dto.EscalationRuleRequest;
import com.oct.invoicesystem.domain.workflow.service.EscalationRuleService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Configuration of SLA escalation rules (B1, M4 #11 / M6 #6). Workflow setting (non-financial):
 * accessible to ADMIN and DAF. The escalation recipient is derived from the approval chain, never
 * the Admin (separation of duties).
 */
@RestController
@RequestMapping("/api/v1/escalation-rules")
@RequiredArgsConstructor
@Tag(name = "Escalation Rules", description = "Configurable SLA escalation thresholds")
public class EscalationRuleController {

    private final EscalationRuleService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "List escalation rules")
    public ApiResponse<List<EscalationRuleDTO>> list() {
        return ApiResponse.success(service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "Create an escalation rule")
    public ApiResponse<EscalationRuleDTO> create(
            @Valid @RequestBody EscalationRuleRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ApiResponse.success(service.create(request, currentUser), "escalation_rule.created");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "Update an escalation rule")
    public ApiResponse<EscalationRuleDTO> update(
            @PathVariable UUID id, @Valid @RequestBody EscalationRuleRequest request) {
        return ApiResponse.success(service.update(id, request), "escalation_rule.updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DAF')")
    @Operation(summary = "Delete an escalation rule")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ApiResponse.success(null, "escalation_rule.deleted");
    }
}
