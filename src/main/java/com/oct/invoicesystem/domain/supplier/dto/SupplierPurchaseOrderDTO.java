package com.oct.invoicesystem.domain.supplier.dto;

import com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderItemDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A purchase order as exposed to the supplier portal's PO selector (audit finding AUDIT-001).
 *
 * <p>Deliberately narrower than {@link com.oct.invoicesystem.domain.purchasing.dto.PurchaseOrderDTO}:
 * it drops {@code createdBy} and {@code supplierId} — internal OCT data the supplier has no reason
 * to see — and keeps only what the form needs to prefill invoice lines (number, total, lines).</p>
 *
 * @param id          the PO identifier, submitted back as {@code purchaseOrderId}
 * @param poNumber    the human-readable PO number shown in the selector
 * @param totalAmount the PO total, used to prefill the invoice amount
 * @param items       the PO lines, used to prefill the invoice lines
 */
public record SupplierPurchaseOrderDTO(
        UUID id,
        String poNumber,
        BigDecimal totalAmount,
        List<PurchaseOrderItemDTO> items
) {}
