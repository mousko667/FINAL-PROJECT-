package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.user.dto.AssignRoleRequest;
import com.oct.invoicesystem.domain.user.dto.UserCreateRequest;
import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.dto.UserImportResultDTO;
import com.oct.invoicesystem.domain.user.dto.UserUpdateRequest;
import com.oct.invoicesystem.domain.user.service.UserCsvService;
import com.oct.invoicesystem.domain.user.service.UserService;
import com.oct.invoicesystem.shared.export.TabularExportService;
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
import java.util.List;
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
    private final com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static ResponseEntity<byte[]> fileResponse(byte[] body,
            String baseName, com.oct.invoicesystem.shared.export.TabularExportService.Format fmt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=" + baseName + "." + fmt.extension)
                .contentType(MediaType.parseMediaType(fmt.mediaType))
                .body(body);
    }

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

    @GetMapping("/export")
    @Operation(summary = "Export users (csv|excel|pdf)",
            description = "Unified export of all users in the requested format (no passwords)")
    public ResponseEntity<byte[]> exportUsers(@RequestParam(defaultValue = "csv") String format) {
        TabularExportService.Format fmt = TabularExportService.Format.from(format);
        List<UserDTO> users = userService.getUsers(0, 10000, "createdAt,desc").getContent();
        List<String> headers = List.of("Username", "Email", "First name", "Last name", "Roles", "Active");
        List<List<String>> rows = users.stream().map(u -> List.of(
                nz(u.username()), nz(u.email()), nz(u.firstName()), nz(u.lastName()),
                u.roles() == null ? "" : String.join("|", u.roles()),
                Boolean.toString(u.active()))).toList();
        byte[] body = tabularExportService.export(fmt, "Users", headers, rows);
        return fileResponse(body, "users_export", fmt);
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
