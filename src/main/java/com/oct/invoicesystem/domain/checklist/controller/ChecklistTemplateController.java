package com.oct.invoicesystem.domain.checklist.controller;

import com.oct.invoicesystem.domain.checklist.dto.ChecklistTemplateDTO;
import com.oct.invoicesystem.domain.checklist.dto.ChecklistTemplateRequest;
import com.oct.invoicesystem.domain.checklist.service.ChecklistService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin CRUD for validation checklist templates (B1, M4). */
@RestController
@RequestMapping("/api/v1/checklist-templates")
@RequiredArgsConstructor
@Tag(name = "Checklist Templates", description = "Admin management of validation checklist templates")
public class ChecklistTemplateController {

    private final ChecklistService checklistService;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List checklist templates")
    public ApiResponse<List<ChecklistTemplateDTO>> list() {
        return ApiResponse.success(checklistService.listTemplates());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a checklist template")
    public ApiResponse<ChecklistTemplateDTO> get(@PathVariable UUID id) {
        return ApiResponse.success(checklistService.getTemplate(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a checklist template")
    public ApiResponse<ChecklistTemplateDTO> create(
            @Valid @RequestBody ChecklistTemplateRequest request, Authentication authentication) {
        User actor = securityHelper.currentUser(authentication);
        return ApiResponse.success(checklistService.createTemplate(request, actor), "checklist.template.created");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a checklist template")
    public ApiResponse<ChecklistTemplateDTO> update(
            @PathVariable UUID id, @Valid @RequestBody ChecklistTemplateRequest request) {
        return ApiResponse.success(checklistService.updateTemplate(id, request), "checklist.template.updated");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a checklist template")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        checklistService.deleteTemplate(id);
        return ApiResponse.success(null, "checklist.template.deleted");
    }
}
