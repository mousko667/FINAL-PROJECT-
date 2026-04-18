package com.oct.invoicesystem.domain.report.controller;

import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
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
}
