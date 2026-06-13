package com.oct.invoicesystem.domain.workflow.dto;

import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Typed response for an approval step (P11-23 / audit P1-07).
 * Replaces the previous {@code Map<String, Object>} return of
 * {@code ApprovalService.getApprovalSteps}. Field names/order match the prior map keys
 * exactly, so the JSON shape is unchanged for existing frontend consumers.
 */
public record ApprovalStepResponse(
        UUID id,
        Integer stepOrder,
        String stepName,
        String stepNameFr,
        String departmentCode,
        ApprovalStepStatus status,
        String approverUsername,
        String approverName,
        String comments,
        String rejectionReason,
        Instant deadline,
        Instant actionAt
) {
}
