package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SupplierPaymentHistoryDTO(
    UUID paymentId,
    String invoiceReference,
    BigDecimal amountPaid,
    String paymentMethod,
    Instant paymentDate,
    String paymentReference
) {}
