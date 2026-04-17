package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request DTO for creating a PurchaseOrderItem.
 */
public record PurchaseOrderItemCreateRequest(
        @NotBlank(message = "validation.item_description_required")
        String itemDescription,
        
        @NotNull(message = "validation.quantity_required")
        @DecimalMin(value = "0.01", message = "validation.quantity_positive")
        BigDecimal quantity,
        
        @NotNull(message = "validation.unit_price_required")
        @DecimalMin(value = "0.01", message = "validation.unit_price_positive")
        BigDecimal unitPrice
) {}
