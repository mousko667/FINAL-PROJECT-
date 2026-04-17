package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request DTO for updating MatchingConfig.
 */
public record MatchingConfigUpdateRequest(
        @NotNull(message = "validation.tolerance_percentage_required")
        @DecimalMin(value = "0", message = "validation.tolerance_percentage_min")
        @DecimalMax(value = "100", message = "validation.tolerance_percentage_max")
        BigDecimal tolerancePercentage,
        
        @NotNull(message = "validation.tolerance_amount_required")
        @DecimalMin(value = "0", message = "validation.tolerance_amount_min")
        BigDecimal toleranceAmount,
        
        @NotNull(message = "validation.require_grn_required")
        Boolean requireGrn
) {}
