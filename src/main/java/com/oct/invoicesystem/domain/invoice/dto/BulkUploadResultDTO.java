package com.oct.invoicesystem.domain.invoice.dto;

import java.util.List;

/**
 * Outcome of a bulk multi-file invoice-document upload (P11-48 / REQ-05). Each file is processed
 * independently: a valid file is stored and reported in {@link #uploaded}, an invalid one
 * (bad type / oversize / unreadable) is rejected and reported in {@link #errors} with its
 * original filename and reason. Valid files are still stored when others fail (per-file, not
 * all-or-nothing — same philosophy as the CSV import, {@link com.oct.invoicesystem.domain.user.dto.UserImportResultDTO}).
 */
public record BulkUploadResultDTO(
        int totalFiles,
        int uploaded,
        int failed,
        List<InvoiceDocumentDTO> documents,
        List<FileError> errors
) {
    public record FileError(String filename, String message) {}
}
