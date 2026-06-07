package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record GoodsReceiptItemRequest(
        @NotNull UUID purchaseOrderItemId,
        @NotNull @DecimalMin("0.01") BigDecimal receivedQuantity
) {}
