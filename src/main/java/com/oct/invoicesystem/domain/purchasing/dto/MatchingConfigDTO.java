package com.oct.invoicesystem.domain.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for MatchingConfig response.
 */
public record MatchingConfigDTO(
        UUID id,
        BigDecimal tolerancePercentage,
        BigDecimal toleranceAmount,
        Boolean requireGrn,
        Boolean isActive,
        UUID updatedBy,
        Instant updatedAt
) {}
