package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.dto.InvoiceCreateRequest;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceItem;

import java.util.List;

/**
 * Attaches the optional invoice lines of an {@link InvoiceCreateRequest} to a freshly built
 * {@link Invoice}, so the three-way matching engine has line-level data to compare against the
 * purchase order.
 *
 * <p>Audit finding AUDIT-031: this logic used to be a private method of {@code InvoiceController},
 * so the supplier portal — which reuses the very same {@code InvoiceCreateRequest} DTO — silently
 * dropped {@code lineItems}. The portal therefore accepted the field and threw it away, and any
 * portal invoice referencing a PO was rejected at submission by
 * {@code ThreeWayMatchingService} ("Invoice or PO has no line items"). Extracting it here makes
 * both intake paths share one implementation, so they cannot drift apart again.</p>
 */
public final class InvoiceLineItemFactory {

    private InvoiceLineItemFactory() {
    }

    /**
     * Attaches {@code lines} to {@code invoice}, numbering them from 1 and setting the
     * bidirectional link so the cascade on {@code Invoice.items} persists them with the invoice.
     * Null or empty input leaves the invoice untouched; null entries are skipped.
     *
     * @param invoice the invoice being created, never {@code null}
     * @param lines   the requested lines, may be {@code null} or empty
     */
    public static void attachLineItems(Invoice invoice, List<InvoiceCreateRequest.LineItem> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int lineNumber = 1;
        for (InvoiceCreateRequest.LineItem line : lines) {
            if (line == null) {
                continue;
            }
            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .lineNumber(lineNumber++)
                    .description(line.description())
                    .quantity(line.quantity())
                    .unitPrice(line.unitPrice())
                    .totalPrice(line.totalPrice())
                    .build();
            invoice.getItems().add(item);
        }
    }
}
