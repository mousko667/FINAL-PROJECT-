package com.oct.invoicesystem.domain.workflow.dto;

/**
 * Dashboard stats scoped to a single validator/approver (REQ-04 / P11-42).
 *
 * @param approvedTotal       total approval steps this user has APPROVED (all time)
 * @param processedThisMonth  steps this user has acted on (APPROVED or REJECTED) since the
 *                            first day of the current month
 */
public record ValidatorStatsResponse(
        long approvedTotal,
        long processedThisMonth
) {
}
