package com.oct.invoicesystem.domain.department.mapper;

import com.oct.invoicesystem.domain.department.dto.DepartmentCreateRequest;
import com.oct.invoicesystem.domain.department.dto.DepartmentDTO;
import com.oct.invoicesystem.domain.department.model.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

    // No explicit mapping needed: both entity and DTO expose property "active"
    // (entity via isActive() getter, DTO via constructor param "active").
    // @JsonProperty("isActive") on DTO ensures JSON output is named "isActive".
    DepartmentDTO toDto(Department department);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Department toEntity(DepartmentCreateRequest request);

    default ZonedDateTime map(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneOffset.UTC);
    }
}
