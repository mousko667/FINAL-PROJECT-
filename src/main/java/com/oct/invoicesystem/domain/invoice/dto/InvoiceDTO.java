package com.oct.invoicesystem.domain.invoice.dto;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceDTO(
        UUID id,
        String referenceNumber,
        UUID departmentId,
        UUID submittedBy,
        String supplierName,
        String supplierEmail,
        String supplierTaxId,
        BigDecimal amount,
        String currency,
        LocalDate issueDate,
        LocalDate dueDate,
        String description,
        InvoiceStatus status,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
