package com.oct.invoicesystem.domain.purchasing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for PurchaseOrderItem response.
 */
public record PurchaseOrderItemDTO(
        UUID id,
        String itemDescription,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
