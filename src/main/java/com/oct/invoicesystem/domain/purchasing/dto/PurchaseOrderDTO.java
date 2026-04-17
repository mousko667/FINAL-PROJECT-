package com.oct.invoicesystem.domain.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for PurchaseOrder response.
 */
public record PurchaseOrderDTO(
        UUID id,
        String poNumber,
        UUID supplierId,
        BigDecimal totalAmount,
        String status,
        UUID createdBy,
        List<PurchaseOrderItemDTO> items,
        Instant createdAt,
        Instant updatedAt
) {}
