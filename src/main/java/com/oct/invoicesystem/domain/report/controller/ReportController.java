package com.oct.invoicesystem.domain.report.controller;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.report.dto.AgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.BottleneckDTO;
import com.oct.invoicesystem.domain.report.dto.BudgetVsActualDTO;
import com.oct.invoicesystem.domain.report.dto.CashFlowProjectionDTO;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPaymentHistoryDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.report.service.ReportService;
import com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO;
import com.oct.invoicesystem.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Report Management", description = "Endpoints for KPI dashboard and document exports")
@SecurityRequirement(name = "bearerAuth")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/kpis")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Dashboard KPIs", description = "Retrieves real-time KPIs for the dashboard")
    public ApiResponse<DashboardKpiDTO> getKpis() {
        return ApiResponse.success(reportService.getDashboardKpis());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get report summary", description = "Alias for /kpis — invoice counts and metrics")
    public ApiResponse<DashboardKpiDTO> getSummary() {
        return ApiResponse.success(reportService.getDashboardKpis());
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get recent invoice activity", description = "Returns recent invoice status changes for dashboard activity feeds")
    public ApiResponse<List<InvoiceHistoryDTO>> getRecentActivity(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(reportService.getRecentActivity(limit));
    }

    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Export Invoices to Excel", description = "Generates and downloads an Excel file of filtered invoices")
    public ResponseEntity<Resource> exportExcel(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String reference) {

        ByteArrayInputStream stream = reportService.exportInvoicesToExcel(status, departmentId, fromDate, toDate, reference);
        InputStreamResource file = new InputStreamResource(stream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices_report.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @GetMapping("/export/pdf/audit/{id}")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Export Invoice Audit to PDF", description = "Generates and downloads a detailed audit trail for a specific invoice")
    public ResponseEntity<Resource> exportAuditPdf(@PathVariable UUID id) {
        ByteArrayInputStream stream = reportService.generateInvoiceAuditPdf(id);
        InputStreamResource file = new InputStreamResource(stream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice_audit_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file);
    }

    @GetMapping("/export/pdf/compliance")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Export Compliance Report to PDF", description = "Generates and downloads a compliance summary for a date range")
    public ResponseEntity<Resource> exportCompliancePdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        ByteArrayInputStream stream = reportService.generateCompliancePdf(startDate, endDate);
        InputStreamResource file = new InputStreamResource(stream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=compliance_report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file);
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Aging Analysis Report", description = "Analyzes overdue invoices grouped by days overdue")
    public ApiResponse<AgingReportDTO> getAgingAnalysis() {
        return ApiResponse.success(reportService.getAgingAnalysis());
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Cash Flow Projection", description = "Projects cash flow for invoices due within N days, grouped by week")
    public ApiResponse<CashFlowProjectionDTO> getCashFlowProjection(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(reportService.getCashFlowProjection(days));
    }

    @GetMapping("/supplier/{supplierId}/payments")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Supplier Payment History", description = "Retrieves all payments made to a specific supplier")
    public ApiResponse<List<SupplierPaymentHistoryDTO>> getSupplierPaymentHistory(
            @PathVariable UUID supplierId) {
        return ApiResponse.success(reportService.getSupplierPaymentHistory(supplierId));
    }

    @GetMapping("/bottlenecks")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Approval Bottlenecks", description = "Detects approval steps exceeding 3-day SLA")
    public ApiResponse<List<BottleneckDTO>> getApprovalBottlenecks() {
        return ApiResponse.success(reportService.getApprovalBottlenecks());
    }

    @GetMapping("/supplier/{supplierId}/performance")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Supplier Performance Metrics", description = "Returns accuracy rate, rejection rate, and average payment time for a supplier")
    public ApiResponse<SupplierPerformanceDTO> getSupplierPerformance(@PathVariable UUID supplierId) {
        return ApiResponse.success(reportService.getSupplierPerformance(supplierId));
    }

    @GetMapping("/budget-vs-actual")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Budget vs Actual Report",
            description = "Per-department budget compared to committed invoice spend (P11-52 / REQ-21)")
    public ApiResponse<BudgetVsActualDTO> getBudgetVsActual() {
        return ApiResponse.success(reportService.getBudgetVsActual());
    }

    @GetMapping("/budget-alerts")
    @PreAuthorize("hasAnyRole('DAF', 'ASSISTANT_COMPTABLE')")
    @Operation(summary = "Get Budget Alerts",
            description = "Departments at/above the given budget-utilisation threshold (default 80%) — M2 dashboard widget")
    public ApiResponse<java.util.List<BudgetVsActualDTO.DepartmentBudgetLine>> getBudgetAlerts(
            @RequestParam(defaultValue = "80") double threshold) {
        return ApiResponse.success(reportService.getBudgetAlerts(threshold));
    }
}
