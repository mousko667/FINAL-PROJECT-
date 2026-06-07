package com.oct.invoicesystem.domain.purchasing.dto;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchingResultDTO(
        UUID id,
        UUID invoiceId,
        UUID purchaseOrderId,
        UUID goodsReceiptNoteId,
        MatchingStatus status,
        String discrepancyNotes,
        UUID overriddenBy,
        String overrideReason,
        Instant createdAt
) {
}
