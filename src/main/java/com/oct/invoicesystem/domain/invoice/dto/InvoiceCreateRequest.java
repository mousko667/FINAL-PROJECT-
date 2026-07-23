package com.oct.invoicesystem.domain.invoice.dto;

import com.oct.invoicesystem.shared.validation.DueDateNotBeforeIssueDate;
import com.oct.invoicesystem.shared.validation.HasIssueAndDueDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@DueDateNotBeforeIssueDate
public record InvoiceCreateRequest(
        @NotNull UUID departmentId,
        UUID supplierId,
        UUID purchaseOrderId,
        @Size(max = 255) String supplierName,
        @Email @Size(max = 255) String supplierEmail,
        String supplierTaxId,
        String supplierBankDetails,
        @NotNull(message = "validation.invoice.amount_required")
        @Positive(message = "validation.invoice.amount_positive")
        BigDecimal amount,
        // AUDIT-032/033 (D4) : mono-devise XAF stricte (Franc CFA BEAC). La liste blanche ferme la
        // reintroduction de XOF par l'API, que la migration V45 avait precisement servi a eliminer.
        @NotBlank(message = "validation.currency_required")
        @Pattern(regexp = "XAF", message = "validation.invoice.currency_must_be_xaf")
        String currency,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        String description,
        List<LineItem> lineItems
) implements HasIssueAndDueDate {
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
