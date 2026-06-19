package com.oct.invoicesystem.domain.payment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Create/update payload for a payment alert rule (B4). */
public record PaymentAlertRuleRequest(
        @PositiveOrZero @Max(365) int daysBeforeDue,
        @Size(max = 255) String label,
        boolean active
) {}
