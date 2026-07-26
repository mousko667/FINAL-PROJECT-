package com.oct.invoicesystem.domain.supplier.dto;

import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderItemDTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A purchase order as shown on the supplier portal's dedicated "My purchase orders" page (B1).
 *
 * <p>Richer than {@link SupplierPurchaseOrderDTO} (which backs the OPEN-only invoicing selector):
 * it carries the {@code status} and {@code createdAt} so the supplier can browse the full history of
 * their orders — OPEN, CLOSED and CANCELLED — sorted and filtered. It still omits internal OCT data
 * ({@code createdBy}, {@code supplierId}).</p>
 */
public record SupplierPurchaseOrderDetailDTO(
        UUID id,
        String poNumber,
        BigDecimal totalAmount,
        String status,
        Instant createdAt,
        List<PurchaseOrderItemDTO> items
) {}
