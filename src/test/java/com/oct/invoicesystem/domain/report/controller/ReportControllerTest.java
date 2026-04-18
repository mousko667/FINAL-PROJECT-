package com.oct.invoicesystem.domain.report.controller;

import com.oct.invoicesystem.domain.report.dto.BottleneckDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.report.service.ReportService;
import com.oct.invoicesystem.shared.response.ApiResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getKpis_WithAdmin_ReturnsSuccess() throws Exception {
        Map<String, Long> overdueByBucket = Map.of("0_30", 1L, "31_60", 0L, "61_90", 0L, "90_plus", 1L);
        DashboardKpiDTO kpis = new DashboardKpiDTO(10, Collections.emptyMap(), 2.0, 0.1, 2, overdueByBucket, 1.5, 2.0, 0.8, 0.95, Collections.emptyMap());
        when(reportService.getDashboardKpis()).thenReturn(kpis);

        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalInvoices").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getKpis_WithUser_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/kpis"))
                .andExpect(status().isForbidden());
    }

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
    @WithMockUser(roles = "ADMIN")
    void exportAuditPdf_WithAdmin_ReturnsFile() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportService.generateInvoiceAuditPdf(id))
                .thenReturn(new ByteArrayInputStream("fake pdf".getBytes()));

        mockMvc.perform(get("/api/v1/reports/export/pdf/audit/" + id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=invoice_audit_" + id + ".pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportCompliancePdf_WithAdmin_ReturnsFile() throws Exception {
        when(reportService.generateCompliancePdf(any(), any()))
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
    void getApprovalBottlenecks_WithAdmin_ReturnsSuccess() throws Exception {
        BottleneckDTO bottleneck = new BottleneckDTO("FIN", 1, "N1_VALIDATION", 4.5, 10, true);
        when(reportService.getApprovalBottlenecks()).thenReturn(List.of(bottleneck));

        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].departmentCode").value("FIN"))
                .andExpect(jsonPath("$.data[0].stepOrder").value(1))
                .andExpect(jsonPath("$.data[0].bottleneck").value(true));
    }

    @Test
    @WithMockUser(roles = "DAF")
    void getApprovalBottlenecks_WithDaf_ReturnsSuccess() throws Exception {
        BottleneckDTO bottleneck = new BottleneckDTO("FIN", 1, "N1_VALIDATION", 4.5, 10, true);
        when(reportService.getApprovalBottlenecks()).thenReturn(List.of(bottleneck));

        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "AUDITEUR")
    void getApprovalBottlenecks_WithAuditeur_ReturnsSuccess() throws Exception {
        BottleneckDTO bottleneck = new BottleneckDTO("FIN", 1, "N1_VALIDATION", 4.5, 10, true);
        when(reportService.getApprovalBottlenecks()).thenReturn(List.of(bottleneck));

        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getApprovalBottlenecks_WithAssistantComptable_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/bottlenecks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplierPerformance_WithAdmin_ReturnsSuccess() throws Exception {
        UUID supplierId = UUID.randomUUID();
        SupplierPerformanceDTO performance = new SupplierPerformanceDTO(
                supplierId, "Test Supplier", 0.85, 0.05, 15.0, 20, 17, 2);
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
    @WithMockUser(roles = "DAF")
    void getSupplierPerformance_WithDaf_ReturnsSuccess() throws Exception {
        UUID supplierId = UUID.randomUUID();
        SupplierPerformanceDTO performance = new SupplierPerformanceDTO(
                supplierId, "Test Supplier", 0.85, 0.05, 15.0, 20, 17, 2);
        when(reportService.getSupplierPerformance(supplierId)).thenReturn(performance);

        mockMvc.perform(get("/api/v1/reports/supplier/" + supplierId + "/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "AUDITEUR")
    void getSupplierPerformance_WithAuditeur_ReturnsSuccess() throws Exception {
        UUID supplierId = UUID.randomUUID();
        SupplierPerformanceDTO performance = new SupplierPerformanceDTO(
                supplierId, "Test Supplier", 0.85, 0.05, 15.0, 20, 17, 2);
        when(reportService.getSupplierPerformance(supplierId)).thenReturn(performance);

        mockMvc.perform(get("/api/v1/reports/supplier/" + supplierId + "/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ASSISTANT_COMPTABLE")
    void getSupplierPerformance_WithAssistantComptable_ReturnsForbidden() throws Exception {
        UUID supplierId = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/reports/supplier/" + supplierId + "/performance"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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
}
