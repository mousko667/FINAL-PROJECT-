package com.oct.invoicesystem.domain.report.controller;

import com.oct.invoicesystem.domain.report.dto.BottleneckDTO;
import com.oct.invoicesystem.domain.report.dto.BucketedAgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.report.dto.PaymentCycleReportDTO;
import com.oct.invoicesystem.domain.report.dto.ReportPreviewDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;
import com.oct.invoicesystem.domain.report.service.ReportBuilderService;
import com.oct.invoicesystem.domain.report.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Authorization contract for reporting endpoints (see docs/API.md, corrected by the
 * separation-of-duties rule): financial reports are accessible to <b>DAF</b> and
 * <b>ASSISTANT_COMPTABLE</b> only. ROLE_ADMIN must NOT access financial data (admins manage
 * the system, not company finances). There is no ROLE_AUDITEUR in this system
 * (removed by V31__fix_finance_approver_and_remove_auditeur). Compliance/audit PDF exports
 * follow the same DAF + ASSISTANT_COMPTABLE contract enforced by the controller.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private ReportBuilderService reportBuilderService;

    // ─── KPIs ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void getKpis_WithDaf_ReturnsSuccess() throws Exception {
        Map<String, Long> overdueByBucket = Map.of("0_30", 1L, "31_60", 0L, "61_90", 0L, "90_plus", 1L);
        DashboardKpiDTO kpis = new DashboardKpiDTO(10, Collections.emptyMap(), 2.0, 0.1, 2, overdueByBucket, 1.5, 2.0, 0.8, 0.95, Collections.emptyMap());
        when(reportService.getDashboardKpis()).thenReturn(kpis);

        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalInvoices").value(10));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getKpis_WithAssistantComptable_ReturnsSuccess() throws Exception {
        Map<String, Long> overdueByBucket = Map.of("0_30", 1L, "31_60", 0L, "61_90", 0L, "90_plus", 1L);
        DashboardKpiDTO kpis = new DashboardKpiDTO(10, Collections.emptyMap(), 2.0, 0.1, 2, overdueByBucket, 1.5, 2.0, 0.8, 0.95, Collections.emptyMap());
        when(reportService.getDashboardKpis()).thenReturn(kpis);

        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getKpis_WithAdmin_ReturnsForbidden() throws Exception {
        // ADMIN must not access financial reporting data (separation of duties).
        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getKpis_WithUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getKpis_IncludesExtendedFields() throws Exception {
        Map<String, Long> overdueByBucket = Map.of("0_30", 1L, "31_60", 2L, "61_90", 1L, "90_plus", 0L);
        DashboardKpiDTO kpis = new DashboardKpiDTO(10, Collections.emptyMap(), 2.0, 0.1, 2, overdueByBucket, 1.5, 2.0, 0.8, 0.95, Collections.emptyMap());
        when(reportService.getDashboardKpis()).thenReturn(kpis);

        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overdueByBucket.0_30").value(1))
                .andExpect(jsonPath("$.data.overdueByBucket.31_60").value(2))
                .andExpect(jsonPath("$.data.overdueByBucket.61_90").value(1))
                .andExpect(jsonPath("$.data.overdueByBucket.90_plus").value(0))
                .andExpect(jsonPath("$.data.averageN1ApprovalDays").value(1.5))
                .andExpect(jsonPath("$.data.averageN2ApprovalDays").value(2.0))
                .andExpect(jsonPath("$.data.averageDafApprovalDays").value(0.8))
                .andExpect(jsonPath("$.data.webhookDeliverySuccessRate").value(0.95));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getBucketedAging_WithDaf_ReturnsSuccess() throws Exception {
        BucketedAgingReportDTO report = BucketedAgingReportDTO.builder()
                .buckets(Collections.emptyMap())
                .totalOverdueAmount(java.math.BigDecimal.ZERO)
                .totalOverdueInvoiceCount(0L)
                .supplierRollup(Collections.emptyList())
                .build();
        when(reportService.bucketedAging()).thenReturn(report);

        mockMvc.perform(get("/api/v1/reports/aging/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalOverdueInvoiceCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBucketedAging_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/aging/buckets"))
                .andExpect(status().isForbidden());
    }

    // ─── Exports ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void exportExcel_WithDaf_ReturnsFile() throws Exception {
        when(reportService.exportInvoicesToExcel(any(), any(), any(), any(), any()))
                .thenReturn(new ByteArrayInputStream("fake excel".getBytes()));

        mockMvc.perform(get("/api/v1/reports/export/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoices_report.xlsx"))
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void exportAuditPdf_WithDaf_ReturnsFile() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.generateInvoiceAuditPdf(eq(id), any()))
                .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));

        mockMvc.perform(get("/api/v1/reports/export/pdf/audit/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice_audit_" + id + ".pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportAuditPdf_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf/audit/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DAF")
    void exportCompliancePdf_WithDaf_ReturnsFile() throws Exception {
        when(reportService.generateCompliancePdf(any(), any(), any()))
                .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));

        mockMvc.perform(get("/api/v1/reports/export/pdf/compliance")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=compliance_report.pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportCompliancePdf_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/export/pdf/compliance")
                        .param("startDate", LocalDate.now().toString())
                        .param("endDate", LocalDate.now().toString()))
                .andExpect(status().isForbidden());
    }

    // ─── Bottlenecks ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void getApprovalBottlenecks_WithDaf_ReturnsSuccess() throws Exception {
        BottleneckDTO bottleneck = new BottleneckDTO("FIN", 1, "N1_VALIDATION", 4.5, 10L, true);
        when(reportService.getApprovalBottlenecks()).thenReturn(List.of(bottleneck));

        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].departmentCode").value("FIN"))
                .andExpect(jsonPath("$.data[0].stepOrder").value(1))
                .andExpect(jsonPath("$.data[0].bottleneck").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getApprovalBottlenecks_WithAssistantComptable_ReturnsSuccess() throws Exception {
        BottleneckDTO bottleneck = new BottleneckDTO("FIN", 1, "N1_VALIDATION", 4.5, 10L, true);
        when(reportService.getApprovalBottlenecks()).thenReturn(List.of(bottleneck));

        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getApprovalBottlenecks_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isForbidden());
    }

    // ─── Supplier performance ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void getSupplierPerformance_WithDaf_ReturnsSuccess() throws Exception {
        UUID supplierId = UUID.randomUUID();
        SupplierPerformanceDTO performance = new SupplierPerformanceDTO(
                supplierId.toString(), "Test Supplier", 0.85, 0.05, 15.0, 20L, 17L, 2L);
        when(reportService.getSupplierPerformance(supplierId)).thenReturn(performance);

        mockMvc.perform(get("/api/v1/reports/supplier/" + supplierId + "/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.supplierId").value(supplierId.toString()))
                .andExpect(jsonPath("$.data.invoiceAccuracyRate").value(0.85))
                .andExpect(jsonPath("$.data.rejectionRate").value(0.05))
                .andExpect(jsonPath("$.data.averagePaymentDays").value(15.0));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getSupplierPerformance_WithAssistantComptable_ReturnsSuccess() throws Exception {
        UUID supplierId = UUID.randomUUID();
        SupplierPerformanceDTO performance = new SupplierPerformanceDTO(
                supplierId.toString(), "Test Supplier", 0.85, 0.05, 15.0, 20L, 17L, 2L);
        when(reportService.getSupplierPerformance(supplierId)).thenReturn(performance);

        mockMvc.perform(get("/api/v1/reports/supplier/" + supplierId + "/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplierPerformance_WithAdmin_ReturnsForbidden() throws Exception {
        UUID supplierId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/reports/supplier/" + supplierId + "/performance"))
                .andExpect(status().isForbidden());
    }

    // ─── Report preview (M11 #10) ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void previewDefinition_WithDaf_ReturnsPreview() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportBuilderService.preview(eq(id), anyInt())).thenReturn(
                new ReportPreviewDTO(List.of("Reference"), List.of(List.of("FAC-1")), 1, "INVOICES", "CSV"));

        mockMvc.perform(get("/api/v1/reports/definitions/" + id + "/preview").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.columns[0]").value("Reference"))
                .andExpect(jsonPath("$.data.rows[0][0]").value("FAC-1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void previewDefinition_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/definitions/" + UUID.randomUUID() + "/preview"))
                .andExpect(status().isForbidden());
    }

    // ─── Volume/value trend (M11 #7) ───────────────────────────────────────

    private VolumeTrendDTO sampleTrend() {
        return new VolumeTrendDTO(
                LocalDate.now().minusMonths(11).withDayOfMonth(1),
                LocalDate.now(),
                List.of(new VolumeTrendDTO.MonthlyTrendPoint("2026-01", 2026, 1, 3L, new java.math.BigDecimal("1500.00"))));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getVolumeTrend_WithDaf_ReturnsSuccess() throws Exception {
        when(reportService.getVolumeTrend(anyInt())).thenReturn(sampleTrend());

        mockMvc.perform(get("/api/v1/reports/volume-trend").param("months", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.points[0].monthLabel").value("2026-01"))
                .andExpect(jsonPath("$.data.points[0].invoiceCount").value(3));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getVolumeTrend_WithAssistantComptable_ReturnsSuccess() throws Exception {
        when(reportService.getVolumeTrend(anyInt())).thenReturn(sampleTrend());

        mockMvc.perform(get("/api/v1/reports/volume-trend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getVolumeTrend_WithAdmin_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/volume-trend"))
                .andExpect(status().isForbidden());
    }

    // ─── Payment cycle report (M11 #5) ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "DAF")
    void paymentCycle_asDaf_returns200() throws Exception {
        when(reportService.getPaymentCycleReport(any(), any()))
                .thenReturn(new PaymentCycleReportDTO(0, null, null, null, null));
        mockMvc.perform(get("/api/v1/reports/payment-cycle")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void paymentCycle_asAssistantComptable_returns200() throws Exception {
        when(reportService.getPaymentCycleReport(any(), any()))
                .thenReturn(new PaymentCycleReportDTO(0, null, null, null, null));
        mockMvc.perform(get("/api/v1/reports/payment-cycle")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void paymentCycle_asAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/payment-cycle")
                        .param("from", "2026-01-01T00:00:00Z").param("to", "2026-12-31T00:00:00Z"))
                .andExpect(status().isForbidden());
    }

    // ─── N8 : /reports/summary était un alias mort de /kpis, retiré (PROB-130) ───

    @Test
    @WithMockUser(roles = "DAF")
    void summary_isRemoved_returns404() throws Exception {
        // /reports/summary était un doublon strict de /kpis (même DTO, même service), jamais
        // appelé par le front. Retiré : l'endpoint n'existe plus → 404. /kpis reste couvert
        // par les tests getKpis_* et n'est pas régressé.
        mockMvc.perform(get("/api/v1/reports/summary"))
                .andExpect(status().isNotFound());
    }
}
