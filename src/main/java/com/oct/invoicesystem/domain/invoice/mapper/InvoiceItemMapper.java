package com.oct.invoicesystem.domain.invoice.mapper;

import com.oct.invoicesystem.domain.invoice.dto.InvoiceItemDTO;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InvoiceItemMapper {

    InvoiceItemDTO toDto(InvoiceItem entity);

    @Mapping(target = "invoice", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    InvoiceItem toEntity(InvoiceItemDTO dto);
}
