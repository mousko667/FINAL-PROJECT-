package com.oct.invoicesystem.domain.invoice.dto;

import java.time.Instant;
import java.util.UUID;

public record ArchiveFolderDTO(
        UUID id,
        String name,
        String description,
        UUID parentId,
        String parentName,
        long invoiceCount,
        Instant createdAt
) {
}
