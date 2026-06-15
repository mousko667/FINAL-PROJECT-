package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.dto.UserImportResultDTO;
import com.oct.invoicesystem.domain.user.dto.UserUpdateRequest;
import com.oct.invoicesystem.domain.user.service.UserCsvService;
import com.oct.invoicesystem.domain.user.service.UserService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "User Management", description = "Endpoints for managing users and roles (Admin only)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final UserCsvService userCsvService;

    @GetMapping
    @Operation(summary = "List all users", description = "Retrieves a paginated list of users")
    public ResponseEntity<ApiResponse<PagedResponse<UserDTO>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUsers(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a single user by their UUID")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserById(id)));
    }

    @PostMapping
    @Operation(summary = "Create user", description = "Creates a new user")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(request), "User created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates an existing user's basic information")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request), "User updated successfully"));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate/Deactivate user", description = "Toggles the active status of a user")
    public ResponseEntity<ApiResponse<Void>> activateUser(
            @PathVariable UUID id,
            @RequestParam boolean active) {
        userService.activateUser(id, active);
        String message = active ? "User activated successfully" : "User deactivated successfully";
        return ResponseEntity.ok(ApiResponse.success(null, message));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Assign roles to user", description = "Replaces the current roles with the newly specified ones")
    public ResponseEntity<ApiResponse<Void>> assignRoles(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRoleRequest request) {
        userService.assignRoles(id, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Roles assigned successfully"));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Unlock user account", description = "Resets failed login attempts and removes account lock for a user")
    public ResponseEntity<ApiResponse<Void>> unlockUser(@PathVariable UUID id) {
        userService.unlockUser(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User account unlocked successfully"));
    }

    @PostMapping("/{id}/mfa/reset")
    @Operation(summary = "Reset/disable a user's MFA",
            description = "Admin action: clears the user's MFA secret + verified/enabled flags so they can re-enrol "
                    + "(or stay without MFA if the policy doesn't force it). Use when a user loses their authenticator.")
    public ResponseEntity<ApiResponse<Void>> resetMfa(@PathVariable UUID id) {
        userService.resetMfa(id);
        return ResponseEntity.ok(ApiResponse.success(null, "User MFA has been reset"));
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export users to CSV", description = "Downloads every user as a CSV file (no passwords)")
    public ResponseEntity<Resource> exportUsersCsv() {
        ByteArrayInputStream stream = userCsvService.exportUsersToCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_export.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(new InputStreamResource(stream));
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import users from CSV",
               description = "Create-only bulk import; returns a per-row result report")
    public ResponseEntity<ApiResponse<UserImportResultDTO>> importUsersCsv(
            @RequestParam("file") MultipartFile file) {
        UserImportResultDTO result = userCsvService.importUsersFromCsv(file);
        return ResponseEntity.ok(ApiResponse.success(result, "import.completed"));
    }
}
