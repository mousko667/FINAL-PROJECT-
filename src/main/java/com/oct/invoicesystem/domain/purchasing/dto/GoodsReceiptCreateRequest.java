package com.oct.invoicesystem.domain.purchasing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptCreateRequest(
        @NotBlank @Size(max = 50) String grnNumber,
        @NotNull UUID purchaseOrderId,
        @NotNull LocalDate receiptDate,
        @Valid List<GoodsReceiptItemRequest> items
) {}
