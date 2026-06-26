package com.oct.invoicesystem.domain.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class SupplierAgingRollupDTO {
    private UUID supplierId;
    private String supplierName;
    private Long invoiceCount;
    private BigDecimal totalOverdueAmount;
    /**
     * Overdue amount per bucket key: 0_30, 31_60, 61_90, 90_plus.
     */
    private Map<String, BigDecimal> amountByBucket;
}
