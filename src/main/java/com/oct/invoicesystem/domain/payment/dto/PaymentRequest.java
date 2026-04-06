package com.oct.invoicesystem.domain.payment.dto;

import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        @NotNull(message = "Amount paid is required")
        @Positive(message = "Amount paid must be positive")
        BigDecimal amountPaid,

        @NotNull(message = "Payment method is required")
        PaymentMethod paymentMethod,

        @NotNull(message = "Payment date is required")
        Instant paymentDate,

        String reference
) {}
