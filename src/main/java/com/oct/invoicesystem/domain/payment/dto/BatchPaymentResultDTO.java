package com.oct.invoicesystem.domain.payment.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of a batch payment (B3). Best-effort: each invoice is processed independently, so the
 * batch reports a per-line outcome plus aggregate counts.
 */
public record BatchPaymentResultDTO(
        int total,
        int succeeded,
        int failed,
        List<LineResult> results
) {
    public record LineResult(
            UUID invoiceId,
            boolean success,
            UUID paymentId,
            String reference,
            String error
    ) {
        public static LineResult ok(UUID invoiceId, UUID paymentId, String reference) {
            return new LineResult(invoiceId, true, paymentId, reference, null);
        }

        public static LineResult failure(UUID invoiceId, String error) {
            return new LineResult(invoiceId, false, null, null, error);
        }
    }
}
