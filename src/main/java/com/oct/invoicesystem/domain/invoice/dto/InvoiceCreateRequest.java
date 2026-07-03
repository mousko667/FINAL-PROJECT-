package com.oct.invoicesystem.domain.invoice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceCreateRequest(
        @NotNull UUID departmentId,
        UUID supplierId,
        UUID purchaseOrderId,
        @Size(max = 255) String supplierName,
        @Email @Size(max = 255) String supplierEmail,
        String supplierTaxId,
        String supplierBankDetails,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        String description,
        List<LineItem> lineItems
) {
    /** Invoice line for three-way matching (optional; compared against the PO when present). */
    public record LineItem(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice
    ) {}
    public InvoiceCreateRequest(
            UUID departmentId,
            UUID supplierId,
            String supplierName,
            String supplierEmail,
            String supplierTaxId,
            String supplierBankDetails,
            BigDecimal amount,
            String currency,
            LocalDate issueDate,
            LocalDate dueDate,
            String description
    ) {
        this(departmentId, supplierId, null, supplierName, supplierEmail, supplierTaxId,
                supplierBankDetails, amount, currency, issueDate, dueDate, description, null);
    }
}
