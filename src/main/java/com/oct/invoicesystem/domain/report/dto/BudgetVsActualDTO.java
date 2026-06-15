package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Budget-vs-actual comparison report (P11-52 / REQ-21, partial scope). For each department it
 * compares the configured annual budget against the actual committed invoice spend (sum of
 * non-rejected, non-draft invoice amounts), with the variance and utilisation percentage.
 */
public record BudgetVsActualDTO(
        List<DepartmentBudgetLine> lines,
        BigDecimal totalBudget,
        BigDecimal totalActual
) {
    public record DepartmentBudgetLine(
            String departmentCode,
            String nameFr,
            String nameEn,
            BigDecimal budget,        // null when no budget defined
            BigDecimal actual,        // committed spend
            BigDecimal variance,      // budget - actual (null when no budget)
            BigDecimal utilizationPercent // actual/budget * 100 (null when no budget)
    ) {}
}
