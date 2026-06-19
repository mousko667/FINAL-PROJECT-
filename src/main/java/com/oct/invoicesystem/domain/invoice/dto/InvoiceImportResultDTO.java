package com.oct.invoicesystem.domain.invoice.dto;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of a multi-invoice import (B8, M3). Best-effort: each parsed invoice is created in its own
 * transaction, so valid entries are persisted even when others fail. Lines are 1-based; for CSV the
 * header is line 1, for XML the line number is the invoice's position in the document.
 */
public record InvoiceImportResultDTO(
        int total,
        int created,
        int failed,
        List<LineResult> results
) {
    public record LineResult(int line, boolean success, UUID invoiceId, String reference, String error) {
        public static LineResult ok(int line, UUID invoiceId, String reference) {
            return new LineResult(line, true, invoiceId, reference, null);
        }
        public static LineResult failure(int line, String error) {
            return new LineResult(line, false, null, null, error);
        }
    }
}
