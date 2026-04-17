package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new PurchaseOrder.
 */
public record PurchaseOrderCreateRequest(
        @NotBlank(message = "validation.po_number_required")
        String poNumber,
        
        @NotNull(message = "validation.supplier_id_required")
        UUID supplierId,
        
        @NotNull(message = "validation.order_date_required")
        LocalDate orderDate,
        
        @NotNull(message = "validation.expected_delivery_date_required")
        LocalDate expectedDeliveryDate,
        
        @NotNull(message = "validation.currency_required")
        @NotBlank(message = "validation.currency_required")
        String currency,
        
        @NotNull(message = "validation.items_required")
        @Valid
        List<PurchaseOrderItemCreateRequest> items
) {}
