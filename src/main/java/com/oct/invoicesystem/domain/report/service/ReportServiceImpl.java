package com.oct.invoicesystem.domain.report.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.report.dto.BudgetVsActualDTO;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.report.dto.AgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.BucketedAgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.BottleneckDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierAgingRollupDTO;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.report.dto.CashFlowProjectionDTO;
import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;
import com.oct.invoicesystem.shared.exception.ValidationException;
import com.oct.invoicesystem.domain.report.dto.CashFlowWeekDTO;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPaymentHistoryDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.workflow.dto.InvoiceHistoryDTO;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.model.InvoiceStatusHistory;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceStatusHistoryRepository historyRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final MessageSource messageSource;
    private final DepartmentRepository departmentRepository;

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
        Map<String, Long> overdueByBucket = calculateOverdueBuckets();

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

        // Approval step averages
        double averageN1ApprovalDays = calculateAverageApprovalDays(1);
        double averageN2ApprovalDays = calculateAverageApprovalDays(2);
        double averageDafApprovalDays = calculateAverageApprovalDays(3);

        // Webhook delivery success rate over last 7 days
        double webhookDeliverySuccessRate = calculateWebhookDeliverySuccessRate();

        return new DashboardKpiDTO(
                totalInvoices,
                countByStatus,
                avgProcessingTimeDays,
                rejectionRate,
                overdueCount,
                overdueByBucket,
                averageN1ApprovalDays,
                averageN2ApprovalDays,
                averageDafApprovalDays,
                webhookDeliverySuccessRate,
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

    private Map<String, Long> calculateOverdueBuckets() {
        List<Invoice> overdueInvoices = invoiceRepository.findOverdueInvoices(LocalDate.now());
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0_30", 0L);
        buckets.put("31_60", 0L);
        buckets.put("61_90", 0L);
        buckets.put("90_plus", 0L);

        for (Invoice invoice : overdueInvoices) {
            if (invoice.getDueDate() == null) {
                continue;
            }
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(invoice.getDueDate(), LocalDate.now());
            if (daysOverdue <= 30) {
                buckets.compute("0_30", (k, v) -> v + 1);
            } else if (daysOverdue <= 60) {
                buckets.compute("31_60", (k, v) -> v + 1);
            } else if (daysOverdue <= 90) {
                buckets.compute("61_90", (k, v) -> v + 1);
            } else {
                buckets.compute("90_plus", (k, v) -> v + 1);
            }
        }

        return buckets;
    }

    private double calculateAverageApprovalDays(int stepOrder) {
        List<ApprovalStep> steps = approvalStepRepository.findAll().stream()
                .filter(step -> step.getStepOrder() != null && step.getStepOrder() == stepOrder)
                .filter(step -> step.getStatus() == ApprovalStepStatus.APPROVED || step.getStatus() == ApprovalStepStatus.REJECTED)
                .filter(step -> step.getCreatedAt() != null && step.getActionAt() != null)
                .toList();

        if (steps.isEmpty()) {
            return 0.0;
        }

        double totalDays = steps.stream()
                .mapToDouble(step -> Duration.between(step.getCreatedAt(), step.getActionAt()).toSeconds() / (24.0 * 3600.0))
                .sum();

        return totalDays / steps.size();
    }

    private double calculateWebhookDeliverySuccessRate() {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        long totalDeliveries = webhookDeliveryRepository.countByCreatedAtAfter(since);
        long successfulDeliveries = webhookDeliveryRepository.countByCreatedAtAfterAndSuccessTrue(since);
        return totalDeliveries == 0 ? 1.0 : (double) successfulDeliveries / totalDeliveries;
    }

    @Override
    @Transactional(readOnly = true)
    public ByteArrayInputStream exportInvoicesToExcel(InvoiceStatus status, UUID departmentId, LocalDate fromDate, LocalDate toDate, String reference) {
        log.info("Generating Excel report for invoices");
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                status, departmentId, fromDate, toDate, reference, null, Pageable.unpaged()).getContent();

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
                null, null, startDate, endDate, null, null, Pageable.unpaged()).getContent();
        
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

    @Override
    @Transactional(readOnly = true)
    public AgingReportDTO getAgingAnalysis() {
        BucketedAgingReportDTO bucketed = bucketedAging();
        return AgingReportDTO.builder()
                .buckets(bucketed.getBuckets())
                .totalOverdueAmount(bucketed.getTotalOverdueAmount())
                .totalOverdueInvoiceCount(bucketed.getTotalOverdueInvoiceCount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BucketedAgingReportDTO bucketedAging() {
        log.info("Calculating bucketed aging analysis with supplier rollup");

        LocalDate today = LocalDate.now();
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, null, null, null, null, Pageable.unpaged()).getContent();

        List<Invoice> overdueInvoices = invoices.stream()
                .filter(inv -> inv.getStatus() != InvoiceStatus.PAYE
                        && inv.getStatus() != InvoiceStatus.ARCHIVE
                        && inv.getStatus() != InvoiceStatus.REJETE)
                .filter(inv -> inv.getDueDate() != null && inv.getDueDate().isBefore(today))
                .toList();

        Map<String, Long> bucketCounts = initBucketCounts();
        Map<String, BigDecimal> bucketAmounts = initBucketAmounts();
        Map<String, SupplierRollupAccumulator> supplierAccumulators = new LinkedHashMap<>();

        for (Invoice inv : overdueInvoices) {
            long daysOverdue = Duration.between(
                    inv.getDueDate().atStartOfDay(),
                    today.atStartOfDay()
            ).toDays();
            String bucketKey = resolveAgingBucketKey(daysOverdue);

            bucketCounts.merge(bucketKey, 1L, Long::sum);
            bucketAmounts.merge(bucketKey, inv.getAmount(), BigDecimal::add);

            String supplierKey = resolveSupplierKey(inv);
            SupplierRollupAccumulator rollup = supplierAccumulators.computeIfAbsent(
                    supplierKey, k -> new SupplierRollupAccumulator(inv));
            rollup.invoiceCount++;
            rollup.totalAmount = rollup.totalAmount.add(inv.getAmount());
            rollup.amountByBucket.merge(bucketKey, inv.getAmount(), BigDecimal::add);
        }

        Map<String, AgingReportDTO.AgingBucketDTO> buckets = buildAgingBuckets(bucketCounts, bucketAmounts);
        BigDecimal totalAmount = bucketAmounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCount = bucketCounts.values().stream().mapToLong(Long::longValue).sum();

        List<SupplierAgingRollupDTO> supplierRollup = supplierAccumulators.values().stream()
                .sorted(Comparator.comparing((SupplierRollupAccumulator acc) -> acc.totalAmount).reversed())
                .map(SupplierRollupAccumulator::toDto)
                .toList();

        return BucketedAgingReportDTO.builder()
                .buckets(buckets)
                .totalOverdueAmount(totalAmount)
                .totalOverdueInvoiceCount(totalCount)
                .supplierRollup(supplierRollup)
                .build();
    }

    private static Map<String, Long> initBucketCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("0_30", 0L);
        counts.put("31_60", 0L);
        counts.put("61_90", 0L);
        counts.put("90_plus", 0L);
        return counts;
    }

    private static Map<String, BigDecimal> initBucketAmounts() {
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        amounts.put("0_30", BigDecimal.ZERO);
        amounts.put("31_60", BigDecimal.ZERO);
        amounts.put("61_90", BigDecimal.ZERO);
        amounts.put("90_plus", BigDecimal.ZERO);
        return amounts;
    }

    private String resolveAgingBucketKey(long daysOverdue) {
        if (daysOverdue <= 30) {
            return "0_30";
        }
        if (daysOverdue <= 60) {
            return "31_60";
        }
        if (daysOverdue <= 90) {
            return "61_90";
        }
        return "90_plus";
    }

    private Map<String, AgingReportDTO.AgingBucketDTO> buildAgingBuckets(
            Map<String, Long> bucketCounts, Map<String, BigDecimal> bucketAmounts) {
        Map<String, AgingReportDTO.AgingBucketDTO> buckets = new LinkedHashMap<>();
        for (String key : List.of("0_30", "31_60", "61_90", "90_plus")) {
            buckets.put(key, AgingReportDTO.AgingBucketDTO.builder()
                    .bucketKey(key)
                    .displayName(messageSource.getMessage(
                            "report.aging.bucket." + key, null, LocaleContextHolder.getLocale()))
                    .invoiceCount(bucketCounts.get(key))
                    .totalAmount(bucketAmounts.get(key))
                    .build());
        }
        return buckets;
    }

    private static String resolveSupplierKey(Invoice inv) {
        Supplier supplier = inv.getSupplier();
        if (supplier != null && supplier.getId() != null) {
            return supplier.getId().toString();
        }
        return "name:" + inv.getSupplierName();
    }

    private static final class SupplierRollupAccumulator {
        private final UUID supplierId;
        private final String supplierName;
        private long invoiceCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private final Map<String, BigDecimal> amountByBucket = initBucketAmounts();

        private SupplierRollupAccumulator(Invoice inv) {
            Supplier supplier = inv.getSupplier();
            this.supplierId = supplier != null ? supplier.getId() : null;
            this.supplierName = inv.getSupplierName();
        }

        private SupplierAgingRollupDTO toDto() {
            return SupplierAgingRollupDTO.builder()
                    .supplierId(supplierId)
                    .supplierName(supplierName)
                    .invoiceCount(invoiceCount)
                    .totalOverdueAmount(totalAmount)
                    .amountByBucket(Map.copyOf(amountByBucket))
                    .build();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowProjectionDTO getCashFlowProjection(int days) {
        log.info("Calculating cash flow projection for {} days", days);

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);

        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, today, endDate, null, null, Pageable.unpaged()).getContent();

        // Filter: only include invoices not yet paid and not in excluded statuses
        List<Invoice> filteredInvoices = invoices.stream()
                .filter(inv -> inv.getStatus() != InvoiceStatus.PAYE 
                        && inv.getStatus() != InvoiceStatus.ARCHIVE 
                        && inv.getStatus() != InvoiceStatus.REJETE)
                .toList();

        // Group by week
        WeekFields weekFields = WeekFields.ISO;
        Map<Integer, List<Invoice>> byWeek = filteredInvoices.stream()
                .collect(Collectors.groupingBy(inv -> inv.getDueDate().get(weekFields.weekOfYear())));

        List<CashFlowWeekDTO> weeklyBreakdown = new ArrayList<>();
        BigDecimal totalProjected = BigDecimal.ZERO;

        for (Integer weekNum : new TreeSet<>(byWeek.keySet())) {
            List<Invoice> weekInvoices = byWeek.get(weekNum);
            BigDecimal weekAmount = weekInvoices.stream()
                    .map(Invoice::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            LocalDate weekStart = weekInvoices.get(0).getDueDate()
                    .with(weekFields.dayOfWeek(), 1); // Monday
            LocalDate weekEnd = weekStart.plusDays(6); // Sunday

            weeklyBreakdown.add(new CashFlowWeekDTO(
                    weekStart,
                    weekEnd,
                    weekAmount,
                    (long) weekInvoices.size()
            ));

            totalProjected = totalProjected.add(weekAmount);
        }

        return new CashFlowProjectionDTO(
                today,
                endDate,
                totalProjected,
                weeklyBreakdown
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierPaymentHistoryDTO> getSupplierPaymentHistory(UUID supplierId) {
        log.info("Getting payment history for supplier {}", supplierId);

        List<Payment> payments = paymentRepository.findAll().stream()
                .filter(p -> p.getInvoice().getSupplier() != null && 
                           p.getInvoice().getSupplier().getId().equals(supplierId))
                .sorted(Comparator.comparing(Payment::getPaymentDate).reversed())
                .toList();

        return payments.stream()
                .map(p -> new SupplierPaymentHistoryDTO(
                        p.getId(),
                        p.getInvoice().getReferenceNumber(),
                        p.getAmountPaid(),
                        p.getPaymentMethod().toString(),
                        p.getPaymentDate(),
                        p.getReference()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BottleneckDTO> getApprovalBottlenecks() {
        log.info("Calculating approval bottlenecks (steps exceeding 3-day SLA)");

        // Get all APPROVED and REJECTED approval steps
        List<ApprovalStep> steps = approvalStepRepository.findAll().stream()
                .filter(s -> s.getStatus() == ApprovalStepStatus.APPROVED || s.getStatus() == ApprovalStepStatus.REJECTED)
                .filter(s -> s.getActionAt() != null && s.getCreatedAt() != null)
                .toList();

        // Group by (departmentCode, stepOrder)
        Map<String, List<ApprovalStep>> groupedSteps = new HashMap<>();
        for (ApprovalStep step : steps) {
            String key = step.getDepartmentCode() + "_" + step.getStepOrder();
            groupedSteps.computeIfAbsent(key, k -> new ArrayList<>()).add(step);
        }

        List<BottleneckDTO> bottlenecks = new ArrayList<>();
        final double SLA_DAYS = 3.0;

        for (Map.Entry<String, List<ApprovalStep>> entry : groupedSteps.entrySet()) {
            String[] parts = entry.getKey().split("_");
            String departmentCode = parts[0];
            Integer stepOrder = Integer.parseInt(parts[1]);

            List<ApprovalStep> groupSteps = entry.getValue();
            long count = groupSteps.size();

            // Calculate average duration
            double totalDays = 0.0;
            for (ApprovalStep step : groupSteps) {
                long seconds = java.time.Duration.between(step.getCreatedAt(), step.getActionAt()).getSeconds();
                double days = seconds / (24.0 * 3600.0);
                totalDays += days;
            }
            double averageDays = totalDays / count;
            boolean isBottleneck = averageDays > SLA_DAYS;

            // Map step order to display name
            String stepName = mapStepOrderToName(stepOrder);

            bottlenecks.add(BottleneckDTO.builder()
                    .departmentCode(departmentCode)
                    .stepOrder(stepOrder)
                    .stepName(stepName)
                    .averageDays(Math.round(averageDays * 100.0) / 100.0) // Round to 2 decimals
                    .stepCount(count)
                    .bottleneck(isBottleneck)
                    .build());
        }

        return bottlenecks.stream()
                .sorted(Comparator.comparing(BottleneckDTO::getBottleneck).reversed()
                        .thenComparing(BottleneckDTO::getDepartmentCode))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierPerformanceDTO getSupplierPerformance(UUID supplierId) {
        log.info("Calculating performance metrics for supplier {}", supplierId);

        // Get all invoices from this supplier that have been submitted (status SOUMIS or later)
        List<Invoice> allInvoices = invoiceRepository.findAll();
        List<Invoice> supplierInvoices = allInvoices.stream()
                .filter(inv -> inv.getSupplier() != null && inv.getSupplier().getId().equals(supplierId))
                .toList();

        if (supplierInvoices.isEmpty()) {
            throw new ResourceNotFoundException("Supplier not found or has no invoices: " + supplierId);
        }

        String supplierName = supplierInvoices.get(0).getSupplierName();

        // Count invoices by matching status
        long matchedCount = supplierInvoices.stream()
                .filter(inv -> "MATCHED".equals(inv.getMatchingStatus()))
                .count();
        long mismatchedCount = supplierInvoices.stream()
                .filter(inv -> "MISMATCH".equals(inv.getMatchingStatus()))
                .count();

        // Invoices with no matching_status (null) are also considered matched for accuracy rate
        long totalCount = supplierInvoices.size();
        long accuracyCount = matchedCount + supplierInvoices.stream()
                .filter(inv -> inv.getMatchingStatus() == null)
                .count();

        double accuracyRate = totalCount > 0 ? (double) accuracyCount / totalCount : 1.0;

        // Count rejections
        long rejectedCount = supplierInvoices.stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.REJETE)
                .count();
        double rejectionRate = totalCount > 0 ? (double) rejectedCount / totalCount : 0.0;

        // Calculate average payment time (SOUMIS -> PAYE)
        double averagePaymentDays = 0.0;
        List<Long> paymentDurations = new ArrayList<>();
        for (Invoice inv : supplierInvoices) {
            List<InvoiceStatusHistory> history = historyRepository.findAll().stream()
                    .filter(h -> h.getInvoice().getId().equals(inv.getId()))
                    .sorted(Comparator.comparing(InvoiceStatusHistory::getChangedAt))
                    .toList();

            InvoiceStatusHistory submitted = history.stream()
                    .filter(h -> "SOUMIS".equals(h.getToStatus()))
                    .findFirst()
                    .orElse(null);

            InvoiceStatusHistory paid = history.stream()
                    .filter(h -> "PAYE".equals(h.getToStatus()))
                    .findFirst()
                    .orElse(null);

            if (submitted != null && paid != null) {
                long seconds = java.time.Duration.between(submitted.getChangedAt(), paid.getChangedAt()).getSeconds();
                if (seconds > 0) {
                    paymentDurations.add(seconds);
                }
            }
        }

        if (!paymentDurations.isEmpty()) {
            double avgSeconds = paymentDurations.stream().mapToLong(Long::longValue).average().orElse(0.0);
            averagePaymentDays = avgSeconds / (24.0 * 3600.0);
        }

        return SupplierPerformanceDTO.builder()
                .supplierId(supplierId.toString())
                .supplierName(supplierName)
                .invoiceAccuracyRate(Math.round(accuracyRate * 10000.0) / 10000.0) // Round to 2 decimals (for percentage)
                .rejectionRate(Math.round(rejectionRate * 10000.0) / 10000.0)
                .averagePaymentDays(Math.round(averagePaymentDays * 100.0) / 100.0)
                .totalInvoicesSubmitted((long) totalCount)
                .matchedInvoices(matchedCount)
                .mismatchedInvoices(mismatchedCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceHistoryDTO> getRecentActivity(int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return historyRepository.findAllByOrderByChangedAtDesc(PageRequest.of(0, boundedLimit)).stream()
                .map(h -> new InvoiceHistoryDTO(h.getId(), null, null,
                        h.getFromStatus(), h.getToStatus(), null, null,
                        h.getChangeReason(), h.getChangedAt()))
                .toList();
    }

    private String mapStepOrderToName(Integer stepOrder) {
        return switch (stepOrder) {
            case 1 -> "N1 Reviewer";
            case 2 -> "N2 Reviewer";
            case 3 -> "DAF Reviewer";
            default -> "Approval Step " + stepOrder;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public BudgetVsActualDTO getBudgetVsActual() {
        log.info("Calculating budget-vs-actual report");

        // Committed spend = all invoices except drafts and rejected ones.
        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, null, null, null, null, Pageable.unpaged()).getContent();

        Map<UUID, BigDecimal> actualByDept = new HashMap<>();
        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.BROUILLON || inv.getStatus() == InvoiceStatus.REJETE) {
                continue;
            }
            if (inv.getDepartment() == null || inv.getAmount() == null) {
                continue;
            }
            actualByDept.merge(inv.getDepartment().getId(), inv.getAmount(), BigDecimal::add);
        }

        List<BudgetVsActualDTO.DepartmentBudgetLine> lines = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        List<Department> departments = departmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Department::getCode))
                .toList();

        for (Department dept : departments) {
            BigDecimal actual = actualByDept.getOrDefault(dept.getId(), BigDecimal.ZERO);
            BigDecimal budget = dept.getBudget();

            BigDecimal variance = null;
            BigDecimal utilization = null;
            if (budget != null) {
                variance = budget.subtract(actual);
                totalBudget = totalBudget.add(budget);
                if (budget.compareTo(BigDecimal.ZERO) > 0) {
                    utilization = actual.multiply(BigDecimal.valueOf(100))
                            .divide(budget, 2, RoundingMode.HALF_UP);
                } else {
                    utilization = BigDecimal.ZERO;
                }
            }
            totalActual = totalActual.add(actual);

            lines.add(new BudgetVsActualDTO.DepartmentBudgetLine(
                    dept.getCode(), dept.getNameFr(), dept.getNameEn(),
                    budget, actual, variance, utilization));
        }

        return new BudgetVsActualDTO(lines, totalBudget, totalActual);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetVsActualDTO.DepartmentBudgetLine> getBudgetAlerts(double thresholdPercent) {
        BigDecimal threshold = BigDecimal.valueOf(thresholdPercent);
        return getBudgetVsActual().lines().stream()
                .filter(l -> l.utilizationPercent() != null
                        && l.utilizationPercent().compareTo(threshold) >= 0)
                .sorted(Comparator.comparing(BudgetVsActualDTO.DepartmentBudgetLine::utilizationPercent).reversed())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public VolumeTrendDTO getVolumeTrend(int months) {
        if (months < 1 || months > 60) {
            throw new ValidationException("reports.trends.invalid_months");
        }
        log.info("Calculating volume/value trend over {} months", months);

        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        LocalDate fromDate = firstMonth.atDay(1);
        LocalDate toDate = LocalDate.now();

        List<Invoice> invoices = invoiceRepository.findAllWithFilters(
                null, null, fromDate, toDate, null, null, Pageable.unpaged()).getContent();

        // Buckets par mois calendaire (issueDate)
        Map<YearMonth, List<Invoice>> byMonth = invoices.stream()
                .collect(Collectors.groupingBy(inv -> YearMonth.from(inv.getIssueDate())));

        // Série continue : un point par mois de la fenêtre, vide = 0
        List<VolumeTrendDTO.MonthlyTrendPoint> points = new java.util.ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = firstMonth.plusMonths(i);
            List<Invoice> monthInvoices = byMonth.getOrDefault(ym, java.util.Collections.emptyList());
            BigDecimal total = monthInvoices.stream()
                    .map(Invoice::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new VolumeTrendDTO.MonthlyTrendPoint(
                    ym.toString(),           // "YYYY-MM"
                    ym.getYear(),
                    ym.getMonthValue(),
                    monthInvoices.size(),
                    total));
        }

        return new VolumeTrendDTO(fromDate, toDate, points);
    }
}
