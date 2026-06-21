package com.oct.invoicesystem.domain.purchasing.dto;

import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;

import java.time.Instant;
import java.util.UUID;

/** Ligne de la liste de rapprochement (dernier résultat par facture). Aucun total financier exposé. */
public record MatchingSummaryDTO(
        UUID invoiceId,
        String invoiceNumber,
        String supplierName,
        UUID purchaseOrderId,
        String purchaseOrderNumber,
        boolean grnPresent,
        MatchingStatus status,
        int lineCount,
        int discrepancyLineCount,
        Instant matchedAt) {
}
