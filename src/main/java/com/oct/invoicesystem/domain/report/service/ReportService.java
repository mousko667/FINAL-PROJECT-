package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.report.dto.AgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.BottleneckDTO;
import com.oct.invoicesystem.domain.report.dto.BudgetVsActualDTO;
import com.oct.invoicesystem.domain.report.dto.CashFlowProjectionDTO;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPaymentHistoryDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;
import com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;

public interface ReportService {

    DashboardKpiDTO getDashboardKpis();

    ByteArrayInputStream exportInvoicesToExcel(
            InvoiceStatus status,
            UUID departmentId,
            LocalDate fromDate,
            LocalDate toDate,
            String reference
    );

    ByteArrayInputStream generateInvoiceAuditPdf(UUID invoiceId);

    ByteArrayInputStream generateCompliancePdf(LocalDate startDate, LocalDate endDate);

    /**
     * Generate aging analysis for overdue invoices.
     * Buckets invoices by days overdue: 0-30, 31-60, 61-90, 90+
     * Only includes invoices NOT in status: PAYE, ARCHIVE, REJETE
     *
     * @return AgingReportDTO with buckets and totals
     */
    AgingReportDTO getAgingAnalysis();

    /**
     * Get cash flow projection for invoices due within N days.
     * Groups by week and sums pending invoice amounts.
     *
     * @param days Number of days to project (default 30)
     * @return CashFlowProjectionDTO with weekly breakdown
     */
    CashFlowProjectionDTO getCashFlowProjection(int days);

    /**
     * Get payment history for a specific supplier.
     * Returns all payments received for invoices from this supplier.
     *
     * @param supplierId Supplier ID
     * @return List of SupplierPaymentHistoryDTO
     */
    List<SupplierPaymentHistoryDTO> getSupplierPaymentHistory(UUID supplierId);

    /**
     * Detect approval bottlenecks: approval steps exceeding 3-business-day SLA.
     * Analyzes average duration per (department, step_order) for APPROVED and REJECTED steps.
     *
     * @return List of BottleneckDTO, flagged where average > 3 business days
     */
    List<BottleneckDTO> getApprovalBottlenecks();

    /**
     * Get supplier performance metrics: accuracy rate, rejection rate, average payment time.
     *
     * @param supplierId Supplier ID
     * @return SupplierPerformanceDTO with calculated metrics
     */
    SupplierPerformanceDTO getSupplierPerformance(UUID supplierId);

    List<InvoiceHistoryDTO> getRecentActivity(int limit);

    /**
     * Budget-vs-actual comparison per department (P11-52 / REQ-21).
     * Compares each department's configured budget against committed invoice spend
     * (sum of invoice amounts excluding BROUILLON and REJETE).
     *
     * @return BudgetVsActualDTO with one line per department plus totals
     */
    BudgetVsActualDTO getBudgetVsActual();

    /**
     * Budget alerts (M2): departments whose committed spend is at or above {@code thresholdPercent}
     * of their budget (default 80%). Used by the manager dashboard "budget alerts" widget.
     *
     * @param thresholdPercent utilisation % at/above which a department is flagged
     * @return only the flagged department lines, highest utilisation first
     */
    java.util.List<BudgetVsActualDTO.DepartmentBudgetLine> getBudgetAlerts(double thresholdPercent);

    VolumeTrendDTO getVolumeTrend(int months);
}
