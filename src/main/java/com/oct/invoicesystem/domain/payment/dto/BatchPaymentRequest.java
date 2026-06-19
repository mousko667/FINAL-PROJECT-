package com.oct.invoicesystem.domain.payment.dto;

import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Batch payment request (B3): pay several BON_A_PAYER invoices at once with a common method and
 * date. Each invoice is paid at its full amount; the reference is generated per invoice.
 */
public record BatchPaymentRequest(
        @NotEmpty List<UUID> invoiceIds,
        @NotNull PaymentMethod paymentMethod,
        @NotNull Instant paymentDate
) {}
