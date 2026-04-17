package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for updating a PurchaseOrder.
 */
public record PurchaseOrderUpdateRequest(
        @NotNull(message = "validation.order_date_required")
        LocalDate orderDate,
        
        @NotNull(message = "validation.expected_delivery_date_required")
        LocalDate expectedDeliveryDate,
        
        @NotBlank(message = "validation.status_required")
        String status,
        
        @NotNull(message = "validation.items_required")
        @Valid
        List<PurchaseOrderItemCreateRequest> items
) {}
