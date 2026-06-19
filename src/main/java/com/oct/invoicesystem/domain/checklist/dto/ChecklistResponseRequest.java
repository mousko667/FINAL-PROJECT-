package com.oct.invoicesystem.domain.checklist.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** The validator's answers to a checklist for one invoice (B1). */
public record ChecklistResponseRequest(
        @NotNull UUID templateId,
        @NotNull @Valid List<ItemAnswer> items
) {
    public record ItemAnswer(
            @NotNull UUID templateItemId,
            boolean checked,
            @Size(max = 1000) String note
    ) {}
}
