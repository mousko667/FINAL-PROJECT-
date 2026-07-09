package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.payment.model.PaymentStatus;
import com.oct.invoicesystem.domain.report.dto.AgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.BucketedAgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.CashFlowProjectionDTO;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.report.dto.BottleneckDTO;
import com.oct.invoicesystem.domain.report.dto.PaymentCycleReportDTO;
import com.oct.invoicesystem.domain.report.dto.SupplierPerformanceDTO;
import com.oct.invoicesystem.domain.report.dto.VolumeTrendDTO;
import java.time.YearMonth;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.model.InvoiceStatusHistory;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceStatusHistoryRepository historyRepository;

    @Mock
    private ApprovalStepRepository approvalStepRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private MessageSource messageSource;

    @Mock
    private com.oct.invoicesystem.domain.department.repository.DepartmentRepository departmentRepository;

    @Mock
    private com.oct.invoicesystem.domain.invoice.service.InvoiceService invoiceService;

    @Mock
    private com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        invoiceId = UUID.randomUUID();
    }

    @Test
    void getDashboardKpis_ReturnsCorrectData() {
        // Mock approval steps
        when(approvalStepRepository.findAll()).thenReturn(Collections.emptyList());
        
        // Mock webhook deliveries
        when(webhookDeliveryRepository.countByCreatedAtAfter(any())).thenReturn(10L);
        when(webhookDeliveryRepository.countByCreatedAtAfterAndSuccessTrue(any())).thenReturn(8L);

        // Arrange
        when(invoiceRepository.count()).thenReturn(10L);
        when(invoiceRepository.countInvoicesByStatus()).thenReturn(Collections.singletonList(new Object[]{"SOUMIS", 5L}));
        when(invoiceRepository.countOverdueInvoices(any())).thenReturn(2L);
        when(invoiceRepository.findOverdueInvoices(any())).thenReturn(Collections.emptyList());
        
        Page<Object[]> topSuppliersPage = new PageImpl<>(Collections.singletonList(new Object[]{"Supplier A", 1000.0}));
        when(invoiceRepository.findTopSuppliersByAmount(any())).thenReturn(topSuppliersPage);

        when(historyRepository.countUniqueInvoicesByToStatus("SOUMIS")).thenReturn(5L);
        when(historyRepository.countUniqueInvoicesByToStatus("REJETE")).thenReturn(1L);

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        
        Instant now = Instant.now();
        List<InvoiceStatusHistory> histories = List.of(
            InvoiceStatusHistory.builder().invoice(invoice).toStatus("SOUMIS").changedAt(now.minus(2, ChronoUnit.DAYS)).build(),
            InvoiceStatusHistory.builder().invoice(invoice).toStatus("BON_A_PAYER").changedAt(now).build()
        );
        when(historyRepository.findRelevantHistoryForProcessingTime()).thenReturn(histories);

        // Act
        DashboardKpiDTO result = reportService.getDashboardKpis();

        assertEquals(10L, result.totalInvoices());
        assertEquals(2L, result.overdueCount());
        assertEquals(0.2, result.rejectionRate());
        assertEquals(2.0, result.averageProcessingTimeDays());
        assertTrue(result.countByStatus().containsKey("SOUMIS"));
        assertTrue(result.volumeBySupplier().containsKey("Supplier A"));
    }

    @Test
    void exportInvoicesToExcel_ReturnsStream() {
        // Now delegates to the shared invoice-export source of truth + TabularExportService.
        when(invoiceService.invoiceExportHeaders(any(), any())).thenReturn(List.of("Reference", "Supplier"));
        when(invoiceService.buildExportRows(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Label");
        when(tabularExportService.export(any(), any(), any(), any())).thenReturn(new byte[]{1, 2, 3});

        ByteArrayInputStream result = reportService.exportInvoicesToExcel(null, null, null, null, null);
        assertNotNull(result);
    }

    @Test
    void generateInvoiceAuditPdf_ReturnsStream() {
        Invoice invoice = Invoice.builder()
                .id(invoiceId)
                .referenceNumber("FAC-2024-00001")
                .supplierName("Supplier A")
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .status(InvoiceStatus.SOUMIS)
                .build();
        
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));
        when(historyRepository.findByInvoiceIdOrderByChangedAtAsc(invoiceId)).thenReturn(Collections.emptyList());
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Label");

        ByteArrayInputStream result = reportService.generateInvoiceAuditPdf(invoiceId);
        assertNotNull(result);
    }

    @Test
    void generateCompliancePdf_ReturnsStream() {
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Label");

        ByteArrayInputStream result = reportService.generateCompliancePdf(LocalDate.now(), LocalDate.now());
        assertNotNull(result);
    }

    @Test
    void getAgingAnalysis_CorrectlyBucketsOverdueInvoices() {
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        // Arrange
        LocalDate today = LocalDate.now();
        List<Invoice> invoices = new ArrayList<>();

        // Create invoices in different aging buckets
        Invoice inv0to30 = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-0-30")
                .status(InvoiceStatus.BON_A_PAYER)
                .dueDate(today.minusDays(15)) // 15 days overdue
                .amount(BigDecimal.valueOf(100))
                .build();
        invoices.add(inv0to30);

        Invoice inv31to60 = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-31-60")
                .status(InvoiceStatus.EN_VALIDATION_N1)
                .dueDate(today.minusDays(45)) // 45 days overdue
                .amount(BigDecimal.valueOf(200))
                .build();
        invoices.add(inv31to60);

        Invoice inv61to90 = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-61-90")
                .status(InvoiceStatus.SOUMIS)
                .dueDate(today.minusDays(75)) // 75 days overdue
                .amount(BigDecimal.valueOf(300))
                .build();
        invoices.add(inv61to90);

        Invoice inv90plus = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-90-PLUS")
                .status(InvoiceStatus.VALIDE)
                .dueDate(today.minusDays(120)) // 120 days overdue
                .amount(BigDecimal.valueOf(400))
                .build();
        invoices.add(inv90plus);

        // Excluded invoices (should not be counted)
        Invoice invPaye = Invoice.builder()
                .id(UUID.randomUUID())
                .status(InvoiceStatus.PAYE)
                .dueDate(today.minusDays(10))
                .amount(BigDecimal.valueOf(500))
                .build();
        invoices.add(invPaye);

        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(invoices));

        // Act
        AgingReportDTO result = reportService.getAgingAnalysis();

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.getBuckets().get("0_30").getInvoiceCount());
        assertEquals(BigDecimal.valueOf(100), result.getBuckets().get("0_30").getTotalAmount());

        assertEquals(1L, result.getBuckets().get("31_60").getInvoiceCount());
        assertEquals(BigDecimal.valueOf(200), result.getBuckets().get("31_60").getTotalAmount());

        assertEquals(1L, result.getBuckets().get("61_90").getInvoiceCount());
        assertEquals(BigDecimal.valueOf(300), result.getBuckets().get("61_90").getTotalAmount());

        assertEquals(1L, result.getBuckets().get("90_plus").getInvoiceCount());
        assertEquals(BigDecimal.valueOf(400), result.getBuckets().get("90_plus").getTotalAmount());

        // Total should exclude PAYE invoice
        assertEquals(4L, result.getTotalOverdueInvoiceCount());
        assertEquals(BigDecimal.valueOf(1000), result.getTotalOverdueAmount());
    }

    @Test
    void getAgingAnalysis_ExcludesNonOverdueInvoices() {
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        // Arrange
        LocalDate today = LocalDate.now();
        List<Invoice> invoices = new ArrayList<>();

        Invoice futureInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-FUTURE")
                .status(InvoiceStatus.BON_A_PAYER)
                .dueDate(today.plusDays(10)) // Due in future
                .amount(BigDecimal.valueOf(100))
                .build();
        invoices.add(futureInvoice);

        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(invoices));

        // Act
        AgingReportDTO result = reportService.getAgingAnalysis();

        // Assert
        assertEquals(0L, result.getTotalOverdueInvoiceCount());
        assertEquals(BigDecimal.ZERO, result.getTotalOverdueAmount());
    }

    @Test
    void bucketedAging_groupsOverdueInvoicesBySupplier() {
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDate today = LocalDate.now();
        UUID supplierAId = UUID.randomUUID();
        UUID supplierBId = UUID.randomUUID();
        Supplier supplierA = Supplier.builder().id(supplierAId).companyName("Alpha SA").build();
        Supplier supplierB = Supplier.builder().id(supplierBId).companyName("Beta SARL").build();

        List<Invoice> invoices = List.of(
                Invoice.builder()
                        .id(UUID.randomUUID())
                        .status(InvoiceStatus.BON_A_PAYER)
                        .dueDate(today.minusDays(10))
                        .amount(BigDecimal.valueOf(100))
                        .supplier(supplierA)
                        .supplierName("Alpha SA")
                        .build(),
                Invoice.builder()
                        .id(UUID.randomUUID())
                        .status(InvoiceStatus.VALIDE)
                        .dueDate(today.minusDays(50))
                        .amount(BigDecimal.valueOf(200))
                        .supplier(supplierA)
                        .supplierName("Alpha SA")
                        .build(),
                Invoice.builder()
                        .id(UUID.randomUUID())
                        .status(InvoiceStatus.SOUMIS)
                        .dueDate(today.minusDays(95))
                        .amount(BigDecimal.valueOf(300))
                        .supplier(supplierB)
                        .supplierName("Beta SARL")
                        .build()
        );

        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(invoices));

        BucketedAgingReportDTO result = reportService.bucketedAging();

        assertEquals(3L, result.getTotalOverdueInvoiceCount());
        assertEquals(2, result.getSupplierRollup().size());
        assertEquals("Alpha SA", result.getSupplierRollup().get(0).getSupplierName());
        assertEquals(2L, result.getSupplierRollup().get(0).getInvoiceCount());
        assertEquals(BigDecimal.valueOf(300), result.getSupplierRollup().get(0).getTotalOverdueAmount());
        assertEquals(BigDecimal.valueOf(100), result.getSupplierRollup().get(0).getAmountByBucket().get("0_30"));
        assertEquals(BigDecimal.valueOf(200), result.getSupplierRollup().get(0).getAmountByBucket().get("31_60"));
        assertEquals("Beta SARL", result.getSupplierRollup().get(1).getSupplierName());
        assertEquals(BigDecimal.valueOf(300), result.getSupplierRollup().get(1).getAmountByBucket().get("90_plus"));
    }

    @Test
    void bucketedAging_emptyWhenNoOverdueInvoices() {
        when(messageSource.getMessage(anyString(), any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        BucketedAgingReportDTO result = reportService.bucketedAging();

        assertEquals(0L, result.getTotalOverdueInvoiceCount());
        assertTrue(result.getSupplierRollup().isEmpty());
        assertEquals(0L, result.getBuckets().get("0_30").getInvoiceCount());
    }

    @Test
    void getCashFlowProjection_GroupsInvoicesByWeek() {
        // Arrange
        LocalDate today = LocalDate.now();
        List<Invoice> invoices = new ArrayList<>();

        Invoice inv1 = Invoice.builder()
                .id(UUID.randomUUID())
                .status(InvoiceStatus.BON_A_PAYER)
                .dueDate(today.plusDays(3))
                .amount(BigDecimal.valueOf(100))
                .build();
        invoices.add(inv1);

        Invoice inv2 = Invoice.builder()
                .id(UUID.randomUUID())
                .status(InvoiceStatus.EN_VALIDATION_N1)
                .dueDate(today.plusDays(15))
                .amount(BigDecimal.valueOf(200))
                .build();
        invoices.add(inv2);

        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(invoices));

        // Act
        CashFlowProjectionDTO result = reportService.getCashFlowProjection(30);

        // Assert
        assertNotNull(result);
        assertEquals(today, result.fromDate());
        assertEquals(today.plusDays(30), result.toDate());
        assertEquals(BigDecimal.valueOf(300), result.totalProjected());
        assertTrue(result.weeklyBreakdown().size() > 0);
    }

    @Test
    void getSupplierPaymentHistory_ReturnsPaymentsForSupplier() {
        // Arrange
        UUID supplierId = UUID.randomUUID();
        UUID otherSupplierId = UUID.randomUUID();

        com.oct.invoicesystem.domain.supplier.model.Supplier supplier = new com.oct.invoicesystem.domain.supplier.model.Supplier();
        supplier.setId(supplierId);

        com.oct.invoicesystem.domain.supplier.model.Supplier otherSupplier = new com.oct.invoicesystem.domain.supplier.model.Supplier();
        otherSupplier.setId(otherSupplierId);

        Invoice inv1 = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-001")
                .supplier(supplier)
                .build();

        Invoice inv2 = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-002")
                .supplier(otherSupplier)
                .build();

        Payment pay1 = Payment.builder()
                .id(UUID.randomUUID())
                .invoice(inv1)
                .amountPaid(BigDecimal.valueOf(100))
                .paymentMethod(PaymentMethod.VIREMENT)
                .paymentDate(Instant.now())
                .reference("REF-001")
                .build();

        Payment pay2 = Payment.builder()
                .id(UUID.randomUUID())
                .invoice(inv2)
                .amountPaid(BigDecimal.valueOf(200))
                .paymentMethod(PaymentMethod.CHEQUE)
                .paymentDate(Instant.now())
                .reference("REF-002")
                .build();

        when(paymentRepository.findAll()).thenReturn(List.of(pay1, pay2));

        // Act
        var result = reportService.getSupplierPaymentHistory(supplierId);

        // Assert
        assertEquals(1, result.size());
        assertEquals("FAC-001", result.get(0).invoiceReference());
        assertEquals(BigDecimal.valueOf(100), result.get(0).amountPaid());
    }

    @Test
    void getApprovalBottlenecks_ReturnsBottlenecksWhereAverageExceedsSLA() {
        // Arrange
        Instant now = Instant.now();
        ApprovalStep bottleneckStep = ApprovalStep.builder()
                .departmentCode("FIN")
                .stepOrder(1)
                .status(ApprovalStepStatus.APPROVED)
                .createdAt(now.minus(5, ChronoUnit.DAYS))
                .actionAt(now)
                .build();
        
        ApprovalStep normalStep = ApprovalStep.builder()
                .departmentCode("HR")
                .stepOrder(2)
                .status(ApprovalStepStatus.APPROVED)
                .createdAt(now.minus(2, ChronoUnit.DAYS))
                .actionAt(now)
                .build();
        
        when(approvalStepRepository.findAll()).thenReturn(List.of(bottleneckStep, normalStep));

        // Act
        List<BottleneckDTO> result = reportService.getApprovalBottlenecks();

        assertEquals(2, result.size());
        
        // Since they are sorted by bottleneck (true first)
        BottleneckDTO bottleneck = result.get(0);
        assertEquals("FIN", bottleneck.getDepartmentCode());
        assertEquals(1, bottleneck.getStepOrder());
        assertTrue(bottleneck.getAverageDays() > 3.0);
        assertTrue(bottleneck.getBottleneck());

        BottleneckDTO normal = result.get(1);
        assertEquals("HR", normal.getDepartmentCode());
        assertEquals(2, normal.getStepOrder());
        assertTrue(normal.getAverageDays() <= 3.0);
        assertFalse(normal.getBottleneck());
    }

    @Test
    void getSupplierPerformance_CalculatesCorrectMetrics() {
        // Arrange
        UUID supplierId = UUID.randomUUID();
        
        com.oct.invoicesystem.domain.supplier.model.Supplier supplier = new com.oct.invoicesystem.domain.supplier.model.Supplier();
        supplier.setId(supplierId);

        Invoice matchedInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .matchingStatus("MATCHED")
                .build();
        Invoice pendingInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .matchingStatus(null)
                .build();
        Invoice mismatchInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .supplier(supplier)
                .matchingStatus("MISMATCH")
                .build();
        
        when(invoiceRepository.findAll()).thenReturn(List.of(matchedInvoice, pendingInvoice, mismatchInvoice));
        
        // Mock payment history for average payment time
        Instant now = Instant.now();
        List<InvoiceStatusHistory> paymentHistories = List.of(
            InvoiceStatusHistory.builder()
                .invoice(matchedInvoice)
                .toStatus("SOUMIS")
                .changedAt(now.minus(10, ChronoUnit.DAYS))
                .build(),
            InvoiceStatusHistory.builder()
                .invoice(matchedInvoice)
                .toStatus("PAYE")
                .changedAt(now)
                .build()
        );
        when(historyRepository.findAll()).thenReturn(paymentHistories);

        // Act
        SupplierPerformanceDTO result = reportService.getSupplierPerformance(supplierId);

        // Assert
        assertEquals(supplierId.toString(), result.getSupplierId());
        assertEquals(0.67, result.getInvoiceAccuracyRate(), 0.01); // 2/3
        assertEquals(10.0, result.getAveragePaymentDays(), 0.01); // 10 days
        assertEquals(3, result.getTotalInvoicesSubmitted());
        assertEquals(1, result.getMatchedInvoices());
        assertEquals(1, result.getMismatchedInvoices());
    }

    @Test
    void getDashboardKpis_IncludesExtendedFields() {
        // Mock approval steps
        when(approvalStepRepository.findAll()).thenReturn(Collections.emptyList());
        
        // Mock webhook deliveries
        when(webhookDeliveryRepository.countByCreatedAtAfter(any())).thenReturn(10L);
        when(webhookDeliveryRepository.countByCreatedAtAfterAndSuccessTrue(any())).thenReturn(8L);

        // Arrange - similar to existing test but check new fields
        when(invoiceRepository.count()).thenReturn(10L);
        when(invoiceRepository.countInvoicesByStatus()).thenReturn(Collections.singletonList(new Object[]{"SOUMIS", 5L}));
        when(invoiceRepository.countOverdueInvoices(any())).thenReturn(2L);
        
        // Mock overdue buckets
        LocalDate today = LocalDate.now();
        Invoice inv0to30 = Invoice.builder().dueDate(today.minusDays(15)).build();
        Invoice inv31to601 = Invoice.builder().dueDate(today.minusDays(45)).build();
        Invoice inv31to602 = Invoice.builder().dueDate(today.minusDays(45)).build();
        Invoice inv61to90 = Invoice.builder().dueDate(today.minusDays(75)).build();
        
        when(invoiceRepository.findOverdueInvoices(today)).thenReturn(List.of(inv0to30, inv31to601, inv31to602, inv61to90));
        
        Page<Object[]> topSuppliersPage = new PageImpl<>(Collections.singletonList(new Object[]{"Supplier A", 1000.0}));
        when(invoiceRepository.findTopSuppliersByAmount(any())).thenReturn(topSuppliersPage);

        when(historyRepository.countUniqueInvoicesByToStatus("SOUMIS")).thenReturn(5L);
        when(historyRepository.countUniqueInvoicesByToStatus("REJETE")).thenReturn(1L);

        Invoice invoice = new Invoice();
        invoice.setId(invoiceId);
        
        Instant now = Instant.now();
        List<InvoiceStatusHistory> histories = List.of(
            InvoiceStatusHistory.builder().invoice(invoice).toStatus("SOUMIS").changedAt(now.minus(2, ChronoUnit.DAYS)).build(),
            InvoiceStatusHistory.builder().invoice(invoice).toStatus("BON_A_PAYER").changedAt(now).build()
        );
        when(historyRepository.findRelevantHistoryForProcessingTime()).thenReturn(histories);

        // Act
        DashboardKpiDTO result = reportService.getDashboardKpis();

        // Assert existing fields still work
        assertEquals(10L, result.totalInvoices());
        assertEquals(2L, result.overdueCount());
        
        // Assert new extended fields
        assertNotNull(result.overdueByBucket());
        assertEquals(4, result.overdueByBucket().size()); // All buckets present
        assertEquals(1L, result.overdueByBucket().get("0_30"));
        assertEquals(2L, result.overdueByBucket().get("31_60"));
        assertEquals(1L, result.overdueByBucket().get("61_90"));
        assertEquals(0L, result.overdueByBucket().get("90_plus"));
        
        // Approval averages (mocked to return 0.0)
        assertEquals(0.0, result.averageN1ApprovalDays());
        assertEquals(0.0, result.averageN2ApprovalDays());
        assertEquals(0.0, result.averageDafApprovalDays());
        
        // Webhook success rate (mocked 8/10 = 0.8)
        assertEquals(0.8, result.webhookDeliverySuccessRate());
    }

    @Test
    void getBudgetVsActual_computesActualVarianceAndUtilization() {
        com.oct.invoicesystem.domain.department.model.Department it =
                com.oct.invoicesystem.domain.department.model.Department.builder()
                        .id(UUID.randomUUID()).code("IT").nameFr("Informatique").nameEn("IT")
                        .n1Role("ROLE_X").budget(new BigDecimal("1000.00")).build();
        com.oct.invoicesystem.domain.department.model.Department hr =
                com.oct.invoicesystem.domain.department.model.Department.builder()
                        .id(UUID.randomUUID()).code("HR").nameFr("RH").nameEn("HR")
                        .n1Role("ROLE_Y").budget(null).build(); // no budget defined

        when(departmentRepository.findAll()).thenReturn(List.of(it, hr));

        // Two committed IT invoices (300 + 250) and one HR invoice (100); one REJETE IT must be excluded.
        Invoice it1 = Invoice.builder().department(it).amount(new BigDecimal("300.00")).status(InvoiceStatus.VALIDE).build();
        Invoice it2 = Invoice.builder().department(it).amount(new BigDecimal("250.00")).status(InvoiceStatus.BON_A_PAYER).build();
        Invoice itRejected = Invoice.builder().department(it).amount(new BigDecimal("999.00")).status(InvoiceStatus.REJETE).build();
        Invoice hr1 = Invoice.builder().department(hr).amount(new BigDecimal("100.00")).status(InvoiceStatus.PAYE).build();
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(it1, it2, itRejected, hr1)));

        var result = reportService.getBudgetVsActual();

        var itLine = result.lines().stream().filter(l -> l.departmentCode().equals("IT")).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("550.00").compareTo(itLine.actual()));   // 300 + 250, rejected excluded
        assertEquals(0, new BigDecimal("450.00").compareTo(itLine.variance())); // 1000 - 550
        assertEquals(0, new BigDecimal("55.00").compareTo(itLine.utilizationPercent())); // 550/1000 * 100

        var hrLine = result.lines().stream().filter(l -> l.departmentCode().equals("HR")).findFirst().orElseThrow();
        assertNull(hrLine.budget());
        assertNull(hrLine.variance());
        assertNull(hrLine.utilizationPercent());
        assertEquals(0, new BigDecimal("100.00").compareTo(hrLine.actual()));

        assertEquals(0, new BigDecimal("1000.00").compareTo(result.totalBudget()));
        assertEquals(0, new BigDecimal("650.00").compareTo(result.totalActual())); // 550 + 100
    }

    private Invoice invoiceOn(LocalDate issueDate, String amount) {
        return Invoice.builder()
                .id(UUID.randomUUID())
                .issueDate(issueDate)
                .amount(new BigDecimal(amount))
                .status(InvoiceStatus.SOUMIS)
                .build();
    }

    @Test
    void getVolumeTrend_AggregatesCountAndAmountPerMonth() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(10);
        LocalDate lastMonth = thisMonth.minusMonths(1);
        List<Invoice> invoices = List.of(
                invoiceOn(thisMonth, "100.00"),
                invoiceOn(thisMonth, "50.00"),
                invoiceOn(lastMonth, "200.00")
        );
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(invoices));

        VolumeTrendDTO result = reportService.getVolumeTrend(12);

        assertEquals(12, result.points().size()); // série continue de 12 mois
        // points triés chronologiquement : le dernier est le mois courant
        VolumeTrendDTO.MonthlyTrendPoint current = result.points().get(11);
        assertEquals(YearMonth.from(thisMonth).toString(), current.monthLabel()); // "YYYY-MM"
        assertEquals(2L, current.invoiceCount());
        assertEquals(new BigDecimal("150.00"), current.totalAmount());
        VolumeTrendDTO.MonthlyTrendPoint previous = result.points().get(10);
        assertEquals(1L, previous.invoiceCount());
        assertEquals(new BigDecimal("200.00"), previous.totalAmount());
    }

    @Test
    void getVolumeTrend_FillsEmptyMonthsWithZero() {
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        VolumeTrendDTO result = reportService.getVolumeTrend(6);

        assertEquals(6, result.points().size());
        assertTrue(result.points().stream().allMatch(p -> p.invoiceCount() == 0L));
        assertTrue(result.points().stream().allMatch(p -> p.totalAmount().compareTo(BigDecimal.ZERO) == 0));
    }

    @Test
    void getVolumeTrend_RejectsMonthsBelowOne() {
        assertThrows(
                com.oct.invoicesystem.shared.exception.ValidationException.class,
                () -> reportService.getVolumeTrend(0));
    }

    @Test
    void getVolumeTrend_RejectsMonthsAboveSixty() {
        assertThrows(
                com.oct.invoicesystem.shared.exception.ValidationException.class,
                () -> reportService.getVolumeTrend(61));
    }

    // ─── Payment cycle report (M11 #5) ─────────────────────────────────────

    private InvoiceStatusHistory history(UUID invoiceId, String toStatus, String changedAtIso) {
        Invoice inv = new Invoice();
        inv.setId(invoiceId);
        return InvoiceStatusHistory.builder()
                .invoice(inv)
                .toStatus(toStatus)
                .changedAt(Instant.parse(changedAtIso))
                .build();
    }

    @Test
    void paymentCycle_happy_computesAverages() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T00:00:00Z");
        UUID invId = UUID.randomUUID();

        Invoice invoice = new Invoice();
        invoice.setId(invId);

        Payment p = Payment.builder()
                .invoice(invoice)
                .status(PaymentStatus.PROCESSED)
                .paymentDate(Instant.parse("2026-06-10T00:00:00Z"))
                .processedDate(Instant.parse("2026-06-12T00:00:00Z"))
                .build();

        when(paymentRepository.findProcessedBetween(from, to)).thenReturn(List.of(p));
        when(historyRepository.findRelevantHistoryForProcessingTime()).thenReturn(List.of(
                history(invId, "SOUMIS", "2026-06-01T00:00:00Z"),
                history(invId, "BON_A_PAYER", "2026-06-08T00:00:00Z")));

        PaymentCycleReportDTO dto = reportService.getPaymentCycleReport(from, to);

        assertEquals(1L, dto.invoicesPaidCount());
        assertEquals(7.0, dto.avgSubmissionToBapDays());   // 01 -> 08
        assertEquals(4.0, dto.avgBapToPaymentDays());      // 08 -> 12 (processedDate)
        assertEquals(2.0, dto.avgScheduledToProcessedDays()); // 10 -> 12
        assertEquals(11.0, dto.avgTotalCycleDays());       // 01 -> 12
    }

    @Test
    void paymentCycle_emptyPeriod_returnsZeroAndNulls() {
        when(paymentRepository.findProcessedBetween(any(), any())).thenReturn(List.of());

        PaymentCycleReportDTO dto = reportService.getPaymentCycleReport(Instant.now(), Instant.now());

        assertEquals(0L, dto.invoicesPaidCount());
        assertNull(dto.avgSubmissionToBapDays());
        assertNull(dto.avgBapToPaymentDays());
        assertNull(dto.avgScheduledToProcessedDays());
        assertNull(dto.avgTotalCycleDays());
    }

    @Test
    void paymentCycle_scheduledPayment_feedsScheduledToProcessed() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-12-31T00:00:00Z");

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());

        Payment p = Payment.builder()
                .invoice(invoice)
                .status(PaymentStatus.PROCESSED)
                .paymentDate(Instant.parse("2026-06-10T00:00:00Z"))   // prevu
                .processedDate(Instant.parse("2026-06-13T00:00:00Z")) // reel (+3j)
                .build();

        when(paymentRepository.findProcessedBetween(from, to)).thenReturn(List.of(p));
        when(historyRepository.findRelevantHistoryForProcessingTime()).thenReturn(List.of());

        PaymentCycleReportDTO dto = reportService.getPaymentCycleReport(from, to);

        assertEquals(3.0, dto.avgScheduledToProcessedDays());
    }
}
