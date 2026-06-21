package com.oct.invoicesystem.domain.department.controller;

import com.oct.invoicesystem.domain.department.dto.DepartmentAccessDTO;
import com.oct.invoicesystem.domain.department.service.DepartmentAccessService;
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

@RestController
@RequestMapping("/api/v1/admin/department-access")
@RequiredArgsConstructor
@Tag(name = "Department Access", description = "Aperçu lecture seule des accès par département (Admin)")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentAccessController {

    private final DepartmentAccessService departmentAccessService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aperçu des accès par département",
            description = "Liste, par département, les utilisateurs rattachés, leurs rôles et le niveau de validation (Admin uniquement). Aucune donnée financière.")
    public ResponseEntity<ApiResponse<List<DepartmentAccessDTO>>> getDepartmentAccessOverview() {
        return ResponseEntity.ok(ApiResponse.success(departmentAccessService.getDepartmentAccessOverview()));
    }
}
