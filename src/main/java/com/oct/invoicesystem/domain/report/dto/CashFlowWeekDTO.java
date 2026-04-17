package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowWeekDTO(
    LocalDate weekStart,
    LocalDate weekEnd,
    BigDecimal projectedAmount,
    Long invoiceCount
) {}
