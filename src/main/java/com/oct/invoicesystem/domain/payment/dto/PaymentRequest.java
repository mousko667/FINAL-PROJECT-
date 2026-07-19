package com.oct.invoicesystem.domain.payment.dto;

import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        @NotNull(message = "{validation.payment.amount_required}")
        @Positive(message = "{validation.payment.amount_positive}")
        BigDecimal amountPaid,

        @NotNull(message = "{validation.payment.method_required}")
        PaymentMethod paymentMethod,

        @NotNull(message = "{validation.payment.date_required}")
        Instant paymentDate,

        String reference,

        /** true = paiement planifie (SCHEDULED) ; absent/false = execute immediatement (PROCESSED). */
        Boolean scheduled
) {}
