package com.oct.invoicesystem.domain.invoice.dto;

import com.oct.invoicesystem.domain.invoice.model.DataSensitivity;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceDTO(
        UUID id,
        String referenceNumber,
        UUID departmentId,
        String departmentCode,
        String departmentNameFr,
        String departmentNameEn,
        UUID submittedBy,
        UUID supplierId,
        UUID purchaseOrderId,
        String supplierName,
        String supplierEmail,
        String supplierTaxId,
        BigDecimal amount,
        String currency,
        LocalDate issueDate,
        LocalDate dueDate,
        String description,
        InvoiceStatus status,
        DataSensitivity dataSensitivity,
        String matchingStatus,
        Integer version,
        Instant createdAt,
        Instant updatedAt,
        UUID folderId
) {
}
