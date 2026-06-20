package com.oct.invoicesystem.domain.workflow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Create/update payload for an escalation rule (B1). */
public record EscalationRuleRequest(
        @PositiveOrZero @Max(720) int hoursAfterDeadline,
        @Size(max = 255) String label,
        boolean active
) {}
