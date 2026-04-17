package com.oct.invoicesystem.domain.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class AgingReportDTO {
    /**
     * Aging buckets keyed by age range:
     * "0_30" (0-30 days overdue)
     * "31_60" (31-60 days)
     * "61_90" (61-90 days)
     * "90_plus" (90+ days)
     */
    private Map<String, AgingBucketDTO> buckets;
    
    /**
     * Total overdue amount across all buckets
     */
    private BigDecimal totalOverdueAmount;
    
    /**
     * Count of invoices in overdue status
     */
    private Long totalOverdueInvoiceCount;

    @Getter
    @Setter
    @AllArgsConstructor
    @Builder
    public static class AgingBucketDTO {
        private String bucketKey;
        private String displayName;
        private Long invoiceCount;
        private BigDecimal totalAmount;
    }
}
