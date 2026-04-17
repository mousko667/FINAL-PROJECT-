package com.oct.invoicesystem.domain.purchasing.mapper;

import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderDTO;
import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderItemDTO;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrder;
import com.oct.invoicesystem.domain.purchasing.model.PurchaseOrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for PurchaseOrder and related entities.
 */
@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    @Mapping(target = "status", expression = "java(po.getStatus().name())")
    @Mapping(target = "createdBy", expression = "java(po.getCreatedBy().getId())")
    @Mapping(target = "supplierId", expression = "java(po.getSupplier().getId())")
    PurchaseOrderDTO toPurchaseOrderDTO(PurchaseOrder po);

    PurchaseOrderItemDTO toPurchaseOrderItemDTO(PurchaseOrderItem item);
}
