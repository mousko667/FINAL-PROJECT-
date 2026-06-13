package com.oct.invoicesystem.domain.workflow.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DelegationDTO(
        UUID id,
        String delegatorUsername,
        String delegateeUsername,
        String departmentCode,
        LocalDate fromDate,
        LocalDate toDate,
        String reason,
        Instant createdAt
) {
}
