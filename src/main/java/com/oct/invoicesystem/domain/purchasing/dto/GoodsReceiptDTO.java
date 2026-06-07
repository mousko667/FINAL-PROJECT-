package com.oct.invoicesystem.domain.purchasing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptDTO(
        UUID id,
        String grnNumber,
        UUID purchaseOrderId,
        String purchaseOrderNumber,
        String receivedByUsername,
        LocalDate receiptDate,
        List<GoodsReceiptItemDTO> items,
        Instant createdAt
) {
    public record GoodsReceiptItemDTO(
            UUID id,
            UUID purchaseOrderItemId,
            String itemDescription,
            BigDecimal receivedQuantity
    ) {}
}
