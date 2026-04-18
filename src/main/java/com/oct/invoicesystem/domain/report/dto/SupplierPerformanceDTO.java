package com.oct.invoicesystem.domain.report.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Supplier performance metrics.
 * Includes accuracy rate, rejection rate, and average payment time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierPerformanceDTO {
    
    /** Supplier ID */
    private String supplierId;
    
    /** Supplier name */
    private String supplierName;
    
    /** Invoice accuracy rate: invoices matched on first submission / total submitted */
    private Double invoiceAccuracyRate;
    
    /** Invoice rejection rate: rejected invoices / total submitted */
    private Double rejectionRate;
    
    /** Average days from SOUMIS to PAYE status */
    private Double averagePaymentDays;
    
    /** Total invoices submitted by supplier */
    private Long totalInvoicesSubmitted;
    
    /** Invoices with matching status MATCHED */
    private Long matchedInvoices;
    
    /** Invoices with matching status MISMATCH */
    private Long mismatchedInvoices;
}
