package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
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
}
