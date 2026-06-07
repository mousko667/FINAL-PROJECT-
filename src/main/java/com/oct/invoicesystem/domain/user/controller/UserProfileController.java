package com.oct.invoicesystem.domain.user.controller;

import com.oct.invoicesystem.domain.user.dto.UserDTO;
import com.oct.invoicesystem.domain.user.dto.UserUpdateRequest;
import com.oct.invoicesystem.domain.user.mapper.UserMapper;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.service.UserService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.util.SecurityHelper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "Current authenticated user profile")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserDTO>> getProfile(Authentication authentication) {
        User user = securityHelper.currentUser(authentication);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(user)));
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update current user profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        User user = securityHelper.currentUser(authentication);
        // Email change is intentionally blocked via profile update.
        // Changing email requires admin action to prevent account takeover.
        User saved = userService.updateProfile(user.getId(), request.firstName(), request.lastName(), request.preferredLang());
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(saved), "Profile updated successfully"));
    }
}
