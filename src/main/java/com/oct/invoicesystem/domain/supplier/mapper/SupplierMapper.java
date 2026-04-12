package com.oct.invoicesystem.domain.supplier.mapper;

import com.oct.invoicesystem.domain.supplier.dto.SupplierCreateRequest;
import com.oct.invoicesystem.domain.supplier.dto.SupplierResponse;
import com.oct.invoicesystem.domain.supplier.dto.SupplierUpdateRequest;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SupplierMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "onboardedBy", ignore = true)
    @Mapping(target = "onboardedAt", ignore = true)
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Supplier toEntity(SupplierCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "taxId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "onboardedBy", ignore = true)
    @Mapping(target = "onboardedAt", ignore = true)
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    void updateEntity(@MappingTarget Supplier supplier, SupplierUpdateRequest request);

    @Mapping(target = "onboardedBy", source = "onboardedBy.id")
    SupplierResponse toResponse(Supplier supplier);
}
