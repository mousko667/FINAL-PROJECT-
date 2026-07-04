package com.oct.invoicesystem.domain.department.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;
import java.util.UUID;

// "active" matches the JavaBeans property name MapStruct derives from the entity getter isActive().
// @JsonProperty("isActive") ensures the JSON output field is still named "isActive" for frontend compatibility.
//
// NOTE (MAJEUR-9, PROB-100): the department `budget` is deliberately NOT exposed here. GET /departments
// and /{id} are only isAuthenticated() (SUPPLIER and ADMIN included), so a budget on this DTO would leak a
// financial figure to non-financial roles (SoD). Budget is written via DepartmentUpdateRequest (admin) and
// read only through the DAF/ASSISTANT_COMPTABLE-gated budget-vs-actual report, which reads it off the entity
// directly — never through this DTO. Do not re-add a budget field to this public DTO.
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
