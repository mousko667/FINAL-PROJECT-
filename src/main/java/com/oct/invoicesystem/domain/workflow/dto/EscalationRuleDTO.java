package com.oct.invoicesystem.domain.workflow.dto;

import java.time.Instant;
import java.util.UUID;

/** View of an escalation rule (B1). */
public record EscalationRuleDTO(
        UUID id,
        int hoursAfterDeadline,
        String label,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
