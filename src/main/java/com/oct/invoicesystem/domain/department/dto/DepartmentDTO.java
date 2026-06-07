package com.oct.invoicesystem.domain.department.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.UUID;

// "active" matches the JavaBeans property name MapStruct derives from the entity getter isActive().
// @JsonProperty("isActive") ensures the JSON output field is still named "isActive" for frontend compatibility.
public record DepartmentDTO(
        UUID id,
        String code,
        String nameFr,
        String nameEn,
        boolean requiresN2,
        String n1Role,
        String n2Role,
        @JsonProperty("isActive") boolean active,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {}
