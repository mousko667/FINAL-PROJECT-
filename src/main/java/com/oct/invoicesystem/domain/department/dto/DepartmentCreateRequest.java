package com.oct.invoicesystem.domain.department.dto;

import jakarta.validation.constraints.NotBlank;

public record DepartmentCreateRequest(
        @NotBlank String code,
        @NotBlank String nameFr,
        @NotBlank String nameEn,
        boolean requiresN2,
        @NotBlank String n1Role,
        String n2Role
) {}
