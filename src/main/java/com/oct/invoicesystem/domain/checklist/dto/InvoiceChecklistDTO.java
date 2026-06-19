package com.oct.invoicesystem.domain.checklist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The checklist shown on the invoice validation screen (B1): the applicable template plus, for each
 * item, whether it was already checked and any note. {@code templateId} is null when no template
 * applies to the invoice's department.
 */
public record InvoiceChecklistDTO(
        UUID templateId,
        String templateName,
        boolean answered,
        UUID respondedBy,
        Instant respondedAt,
        List<ItemView> items
) {
    public record ItemView(
            UUID templateItemId,
            String label,
            boolean required,
            int displayOrder,
            boolean checked,
            String note
    ) {}
}
