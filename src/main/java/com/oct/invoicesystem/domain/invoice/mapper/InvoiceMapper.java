package com.oct.invoicesystem.domain.invoice.mapper;

import com.oct.invoicesystem.domain.invoice.dto.InvoiceDTO;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceMapper {

    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "submittedBy", source = "submittedBy.id")
    @Mapping(target = "supplierId", source = "supplier.id")
    InvoiceDTO toDto(Invoice entity);

    @Mapping(target = "department", ignore = true)
    @Mapping(target = "submittedBy", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "documents", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Invoice toEntity(InvoiceDTO dto);
}
