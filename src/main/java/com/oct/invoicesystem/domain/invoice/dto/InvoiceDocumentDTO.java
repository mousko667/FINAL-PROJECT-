package com.oct.invoicesystem.domain.invoice.dto;

import java.time.Instant;
import java.util.UUID;

public record InvoiceDocumentDTO(
        UUID id,
        UUID invoiceId,
        String originalFilename,
        String fileType,
        Long fileSizeBytes,
        String checksumSha256,
        UUID uploadedBy,
        Instant uploadedAt
) {
}
