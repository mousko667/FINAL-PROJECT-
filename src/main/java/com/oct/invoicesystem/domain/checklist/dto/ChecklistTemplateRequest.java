package com.oct.invoicesystem.domain.checklist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Admin create/update payload for a checklist template (B1). */
public record ChecklistTemplateRequest(
        @NotBlank @Size(max = 255) String name,
        UUID departmentId,
        boolean active,
        @NotNull @Valid List<ItemRequest> items
) {
    public record ItemRequest(
            @NotBlank @Size(max = 500) String label,
            boolean required,
            int displayOrder
    ) {}
}
