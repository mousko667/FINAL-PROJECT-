package com.oct.invoicesystem.domain.report.dto;

import java.util.Map;

public record DashboardKpiDTO(
    long totalInvoices,
    Map<String, Long> countByStatus,
    double averageProcessingTimeDays,
    double rejectionRate,
    long overdueCount,
    Map<String, Long> overdueByBucket,
    double averageN1ApprovalDays,
    double averageN2ApprovalDays,
    double averageDafApprovalDays,
    double webhookDeliverySuccessRate,
    Map<String, Double> volumeBySupplier
) {}
