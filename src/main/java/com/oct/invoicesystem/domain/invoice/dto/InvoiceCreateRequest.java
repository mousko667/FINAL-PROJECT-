package com.oct.invoicesystem.domain.invoice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceCreateRequest(
        @NotNull UUID departmentId,
        UUID supplierId,
        @Size(max = 255) String supplierName,
        @Email @Size(max = 255) String supplierEmail,
        String supplierTaxId,
        String supplierBankDetails,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        String description
) {
}
