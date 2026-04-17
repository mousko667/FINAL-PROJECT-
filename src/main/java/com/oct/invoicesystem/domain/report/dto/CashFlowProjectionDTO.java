package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowProjectionDTO(
    LocalDate fromDate,
    LocalDate toDate,
    BigDecimal totalProjected,
    List<CashFlowWeekDTO> weeklyBreakdown
) {}
