package com.oct.invoicesystem.domain.department.controller;

import com.oct.invoicesystem.domain.department.dto.DepartmentCreateRequest;
import com.oct.invoicesystem.domain.department.dto.DepartmentDTO;
import com.oct.invoicesystem.domain.department.dto.DepartmentUpdateRequest;
import com.oct.invoicesystem.domain.department.service.DepartmentService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
@Tag(name = "Department Management", description = "Endpoints for managing departments")
@SecurityRequirement(name = "bearerAuth")
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "List all departments", description = "Retrieves a paginated list of departments (All authenticated users)")
    public ResponseEntity<ApiResponse<PagedResponse<DepartmentDTO>>> getDepartments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "code,asc") String sort) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getDepartments(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get department by ID", description = "Retrieves a single department (All authenticated users)")
    public ResponseEntity<ApiResponse<DepartmentDTO>> getDepartmentById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.getDepartmentById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create department", description = "Creates a new department (Admin only)")
    public ResponseEntity<ApiResponse<DepartmentDTO>> createDepartment(@Valid @RequestBody DepartmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(departmentService.createDepartment(request), "Department created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update department", description = "Updates an existing department (Admin only)")
    public ResponseEntity<ApiResponse<DepartmentDTO>> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody DepartmentUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(departmentService.updateDepartment(id, request), "Department updated successfully"));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activate/Deactivate department", description = "Toggles the active status of a department (Admin only)")
    public ResponseEntity<ApiResponse<Void>> activateDepartment(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        departmentService.activateDepartment(id, active);
        String message = active ? "Department activated successfully" : "Department deactivated successfully";
        return ResponseEntity.ok(ApiResponse.success(null, message));
    }
}
