package com.oct.invoicesystem.domain.report.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.workflow.model.InvoiceStatusHistory;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceStatusHistoryRepository historyRepository;
    private final MessageSource messageSource;

    @Override
    @Transactional(readOnly = true)
    public DashboardKpiDTO getDashboardKpis() {
        log.info("Calculating dashboard KPIs");

        long totalInvoices = invoiceRepository.count();
        
        List<Object[]> statusCountsRaw = invoiceRepository.countInvoicesByStatus();
        Map<String, Long> countByStatus = statusCountsRaw.stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));

        long overdueCount = invoiceRepository.countOverdueInvoices(LocalDate.now());

        // Top 5 Suppliers
        Map<String, Double> topSuppliers = invoiceRepository.findTopSuppliersByAmount(PageRequest.of(0, 5))
                .getContent().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> ((Number) row[1]).doubleValue(),
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));

        // Rejection Rate
        long submittedCount = historyRepository.countUniqueInvoicesByToStatus("SOUMIS");
        long rejectedCount = historyRepository.countUniqueInvoicesByToStatus("REJETE");
        double rejectionRate = submittedCount > 0 ? (double) rejectedCount / submittedCount : 0.0;

        // Average Processing Time (SOUMIS -> BON_A_PAYER)
        double avgProcessingTimeDays = calculateAverageProcessingTimeDays();

        return new DashboardKpiDTO(
                totalInvoices,
                countByStatus,
                avgProcessingTimeDays,
                rejectionRate,
                overdueCount,
                topSuppliers
        );
    }

    private double calculateAverageProcessingTimeDays() {
        List<InvoiceStatusHistory> relevantHistory = historyRepository.findRelevantHistoryForProcessingTime();
        
        Map<UUID, List<InvoiceStatusHistory>> historyByInvoice = relevantHistory.stream()
                .collect(Collectors.groupingBy(h -> h.getInvoice().getId()));

        List<Long> durationsSeconds = new ArrayList<>();

        for (List<InvoiceStatusHistory> histories : historyByInvoice.values()) {
            Optional<InvoiceStatusHistory> submitted = histories.stream()
                    .filter(h -> "SOUMIS".equals(h.getToStatus()))
                    .min(Comparator.comparing(InvoiceStatusHistory::getChangedAt));

            Optional<InvoiceStatusHistory> approved = histories.stream()
                    .filter(h -> "BON_A_PAYER".equals(h.getToStatus()))
                    .min(Comparator.comparing(InvoiceStatusHistory::getChangedAt));

            if (submitted.isPresent() && approved.isPresent()) {
                long seconds = Duration.between(submitted.get().getChangedAt(), approved.get().getChangedAt()).getSeconds();
                if (seconds > 0) {
                    durationsSeconds.add(seconds);
                }
            }
        }

        if (durationsSeconds.isEmpty()) return 0.0;

        double avgSeconds = durationsSeconds.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return avgSeconds / (24 * 3600); // Convert to days
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportInvoicesToExcel(InvoiceStatus status, UUID departmentId, LocalDate fromDate, LocalDate toDate, String reference) {
        log.info("Generating Excel report for invoices");
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                status, departmentId, fromDate, toDate, reference, Pageable.unpaged()).getContent();

        Locale locale = LocaleContextHolder.getLocale();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(messageSource.getMessage("report.excel.title", null, locale));

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Headers
            Row headerRow = sheet.createRow(0);
            String[] headerKeys = {
                    "report.excel.header.reference",
                    "report.excel.header.supplier",
                    "report.excel.header.amount",
                    "report.excel.header.currency",
                    "report.excel.header.status",
                    "report.excel.header.issue_date",
                    "report.excel.header.due_date",
                    "report.excel.header.department"
            };

            for (int i = 0; i < headerKeys.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = headerRow.createCell(i);
                cell.setCellValue(messageSource.getMessage(headerKeys[i], null, locale));
                cell.setCellStyle(headerStyle);
            }

            // Data
            int rowIdx = 1;
            for (Invoice invoice : invoices) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(invoice.getReferenceNumber());
                row.createCell(1).setCellValue(invoice.getSupplierName());
                row.createCell(2).setCellValue(invoice.getAmount().doubleValue());
                row.createCell(3).setCellValue(invoice.getCurrency());
                
                // Translated status
                String statusKey = "invoice.status." + invoice.getStatus().name().toLowerCase();
                row.createCell(4).setCellValue(messageSource.getMessage(statusKey, null, locale));
                
                row.createCell(5).setCellValue(invoice.getIssueDate().toString());
                row.createCell(6).setCellValue(invoice.getDueDate().toString());
                row.createCell(7).setCellValue(invoice.getDepartment() != null ? invoice.getDepartment().getCode() : "");
            }

            for (int i = 0; i < headerKeys.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            log.error("Error generating Excel report", e);
            throw new RuntimeException("Fail to export data to Excel file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream generateInvoiceAuditPdf(UUID invoiceId) {
        log.info("Generating PDF Audit report for invoice: {}", invoiceId);
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        
        List<InvoiceStatusHistory> histories = historyRepository.findByInvoiceIdOrderByChangedAtAsc(invoiceId);
        Locale locale = LocaleContextHolder.getLocale();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Title
            document.add(new Paragraph(messageSource.getMessage("report.pdf.audit.title", null, locale))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold());

            // Generated at
            String generatedAt = formatter.format(java.time.Instant.now());
            document.add(new Paragraph(messageSource.getMessage("report.pdf.generated_at", new Object[]{generatedAt}, locale))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(8));

            document.add(new Paragraph("\n"));

            // Invoice Summary Table
            Table summary = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth();
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.reference", null, locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(invoice.getReferenceNumber())));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.supplier", null, locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(invoice.getSupplierName())));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.amount", null, locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(invoice.getAmount().toString() + " " + invoice.getCurrency())));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.status", null, locale)).setBold()));
            summary.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("invoice.status." + invoice.getStatus().name().toLowerCase(), null, locale))));
            document.add(summary);

            document.add(new Paragraph("\n"));
            document.add(new Paragraph("Audit Trail").setBold().setFontSize(14));

            // Audit Trail Table
            Table auditTable = new Table(UnitValue.createPercentArray(new float[]{20, 20, 20, 40})).useAllAvailableWidth();
            auditTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Date").setBold()));
            auditTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("User").setBold()));
            auditTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Transition").setBold()));
            auditTable.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("Reason/Comment").setBold()));

            for (InvoiceStatusHistory h : histories) {
                auditTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(formatter.format(h.getChangedAt()))));
                auditTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(h.getChangedBy().getUsername())));
                
                String fromStatusLabel = messageSource.getMessage("invoice.status." + h.getFromStatus().toLowerCase(), null, locale);
                String toStatusLabel = messageSource.getMessage("invoice.status." + h.getToStatus().toLowerCase(), null, locale);
                auditTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(fromStatusLabel + " -> " + toStatusLabel)));
                
                auditTable.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(h.getChangeReason() != null ? h.getChangeReason() : "")));
            }
            document.add(auditTable);

            document.close();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            log.error("Error generating PDF audit", e);
            throw new RuntimeException("Fail to generate PDF Audit file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream generateCompliancePdf(LocalDate startDate, LocalDate endDate) {
        log.info("Generating Compliance PDF report from {} to {}", startDate, endDate);
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, startDate, endDate, null, Pageable.unpaged()).getContent();
        
        Locale locale = LocaleContextHolder.getLocale();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            // Title
            document.add(new Paragraph(messageSource.getMessage("report.pdf.compliance.title", null, locale))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontSize(20)
                    .setBold());

            // Period
            String period = messageSource.getMessage("report.pdf.period", new Object[]{startDate.toString(), endDate.toString()}, locale);
            document.add(new Paragraph(period).setTextAlignment(TextAlignment.CENTER).setFontSize(12));

            // Generated at
            String generatedAt = formatter.format(java.time.Instant.now());
            document.add(new Paragraph(messageSource.getMessage("report.pdf.generated_at", new Object[]{generatedAt}, locale))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setFontSize(8));

            document.add(new Paragraph("\n"));

            // Compliance Table
            Table table = new Table(UnitValue.createPercentArray(new float[]{15, 30, 15, 15, 25})).useAllAvailableWidth();
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.reference", null, locale)).setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.supplier", null, locale)).setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.amount", null, locale)).setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.issue_date", null, locale)).setBold()));
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("report.excel.header.status", null, locale)).setBold()));

            for (Invoice i : invoices) {
                table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(i.getReferenceNumber())));
                table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(i.getSupplierName())));
                table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(i.getAmount().toString() + " " + i.getCurrency())));
                table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(i.getIssueDate().toString())));
                table.addCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(messageSource.getMessage("invoice.status." + i.getStatus().name().toLowerCase(), null, locale))));
            }
            document.add(table);

            document.close();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            log.error("Error generating compliance PDF", e);
            throw new RuntimeException("Fail to generate Compliance PDF file: " + e.getMessage());
        }
    }
}
