package com.oct.invoicesystem.domain.department.dto;

import java.math.BigDecimal;

public record DepartmentUpdateRequest(
        String nameFr,
        String nameEn,
        Boolean requiresN2,
        String n1Role,
        String n2Role,
        BigDecimal budget
) {}
