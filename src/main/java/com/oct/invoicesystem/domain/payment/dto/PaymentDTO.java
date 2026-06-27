package com.oct.invoicesystem.domain.payment.dto;

import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentDTO(
        UUID id,
        UUID invoiceId,
        BigDecimal amountPaid,
        PaymentMethod paymentMethod,
        Instant paymentDate,
        String reference,
        UUID recordedBy,
        Instant createdAt,
        PaymentStatus status,
        Instant processedDate
) {}
