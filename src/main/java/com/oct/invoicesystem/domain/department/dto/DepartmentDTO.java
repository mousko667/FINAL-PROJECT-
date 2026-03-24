package com.oct.invoicesystem.domain.department.dto;

import java.time.ZonedDateTime;
import java.util.UUID;

public record DepartmentDTO(
        UUID id,
        String code,
        String nameFr,
        String nameEn,
        boolean requiresN2,
        String n1Role,
        String n2Role,
        boolean isActive,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {}
