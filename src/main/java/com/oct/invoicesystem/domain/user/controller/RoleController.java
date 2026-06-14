package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.user.dto.RoleDTO;
import com.oct.invoicesystem.domain.user.service.RoleService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only role catalogue (Admin only). Supports the permission-matrix editor (P11-18) by
 * exposing the role name→UUID mapping required to assign roles via {@code PUT /users/{id}/roles}.
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Role Catalogue", description = "Read-only list of assignable roles (Admin only)")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @Operation(summary = "List all roles", description = "Retrieves every role with its UUID and name")
    public ResponseEntity<ApiResponse<List<RoleDTO>>> getRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleService.getAllRoles()));
    }
}
