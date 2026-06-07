package com.oct.invoicesystem.domain.workflow.dto;

import java.time.Instant;
import java.util.UUID;

public record InvoiceHistoryDTO(
        UUID id,
        UUID invoiceId,
        String referenceNumber,
        String fromStatus,
        String toStatus,
        UUID changedBy,
        String changedByUsername,
        String changeReason,
        Instant changedAt
) {
}
