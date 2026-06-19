package com.oct.invoicesystem.domain.checklist.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin view of a checklist template with its ordered items (B1). */
public record ChecklistTemplateDTO(
        UUID id,
        String name,
        UUID departmentId,
        boolean active,
        List<ItemDTO> items,
        Instant createdAt,
        Instant updatedAt
) {
    public record ItemDTO(
            UUID id,
            String label,
            boolean required,
            int displayOrder
    ) {}
}
