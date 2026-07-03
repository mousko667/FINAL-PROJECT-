package com.oct.invoicesystem.domain.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ArchiveFolderCreateRequest(
        @NotBlank(message = "{validation.required}") String name,
        String description,
        UUID parentId
) {
}
