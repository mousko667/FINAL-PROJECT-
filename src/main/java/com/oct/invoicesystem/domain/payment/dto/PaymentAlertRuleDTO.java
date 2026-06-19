package com.oct.invoicesystem.domain.payment.dto;

import java.time.Instant;
import java.util.UUID;

/** View of a payment alert rule (B4). */
public record PaymentAlertRuleDTO(
        UUID id,
        int daysBeforeDue,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
