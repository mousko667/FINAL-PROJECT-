package com.oct.invoicesystem.domain.purchasing.controller;

import com.oct.invoicesystem.domain.purchasing.dto.MatchingConfigDTO;
import com.oct.invoicesystem.domain.purchasing.dto.MatchingConfigUpdateRequest;
import com.oct.invoicesystem.domain.purchasing.mapper.MatchingConfigMapper;
import com.oct.invoicesystem.domain.purchasing.model.MatchingConfig;
import com.oct.invoicesystem.domain.purchasing.service.MatchingConfigService;
import com.oct.invoicesystem.domain.user.model.User;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matching-config")
@RequiredArgsConstructor
@Tag(name = "Matching Configuration", description = "Endpoints for managing three-way matching configuration")
@SecurityRequirement(name = "bearerAuth")
public class MatchingConfigController {

    private final MatchingConfigService matchingConfigService;
    private final MatchingConfigMapper matchingConfigMapper;
    private final SecurityHelper securityHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get active matching configuration", description = "Retrieves the current active matching configuration")
    public ResponseEntity<ApiResponse<MatchingConfigDTO>> getActiveConfig() {
        MatchingConfig config = matchingConfigService.getActiveConfig();
        MatchingConfigDTO dto = matchingConfigMapper.toDTO(config);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping
    @PreAuthorize("hasRole('DAF')")
    @Operation(summary = "Update matching configuration",
            description = "Updates the matching tolerance and GRN requirements (DAF only — AUDIT-008/D5: "
                    + "tolerance thresholds decide whether a billing discrepancy passes or blocks, so they are "
                    + "a financial control and ADMIN, which has no financial access, must not set them)")
    public ResponseEntity<ApiResponse<MatchingConfigDTO>> updateConfig(
            @Valid @RequestBody MatchingConfigUpdateRequest request,
            Authentication authentication) {
        User actor = securityHelper.currentUser(authentication);
        MatchingConfig updated = matchingConfigService.updateConfig(
                request.tolerancePercentage(),
                request.toleranceAmount(),
                request.requireGrn(),
                actor
        );
        MatchingConfigDTO dto = matchingConfigMapper.toDTO(updated);
        return ResponseEntity.ok(ApiResponse.success(dto, "matching_config.updated"));
    }
}
