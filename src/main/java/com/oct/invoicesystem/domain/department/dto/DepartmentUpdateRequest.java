package com.oct.invoicesystem.domain.department.dto;

public record DepartmentUpdateRequest(
        String nameFr,
        String nameEn,
        Boolean requiresN2,
        String n1Role,
        String n2Role
) {}
