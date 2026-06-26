package com.oct.invoicesystem.domain.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class BucketedAgingReportDTO {
    private Map<String, AgingReportDTO.AgingBucketDTO> buckets;
    private BigDecimal totalOverdueAmount;
    private Long totalOverdueInvoiceCount;
    private List<SupplierAgingRollupDTO> supplierRollup;
}
