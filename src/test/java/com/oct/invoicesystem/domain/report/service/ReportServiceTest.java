package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.report.dto.AgingReportDTO;
import com.oct.invoicesystem.domain.report.dto.CashFlowProjectionDTO;
import com.oct.invoicesystem.domain.report.dto.DashboardKpiDTO;
import com.oct.invoicesystem.domain.workflow.model.InvoiceStatusHistory;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
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
    private PaymentRepository paymentRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        invoiceId = UUID.randomUUID();
    }

    @Test
    void getDashboardKpis_ReturnsCorrectData() {
        // Arrange
        when(invoiceRepository.count()).thenReturn(10L);
        when(invoiceRepository.countInvoicesByStatus()).thenReturn(Collections.singletonList(new Object[]{"SOUMIS", 5L}));
        when(invoiceRepository.countOverdueInvoices(any())).thenReturn(2L);
        
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

        // Assert
        assertEquals(10L, result.totalInvoices());
        assertEquals(2L, result.overdueCount());
        assertEquals(0.2, result.rejectionRate());
        assertEquals(2.0, result.averageProcessingTimeDays());
        assertTrue(result.countByStatus().containsKey("SOUMIS"));
        assertTrue(result.volumeBySupplier().containsKey("Supplier A"));
    }

    @Test
    void exportInvoicesToExcel_ReturnsStream() {
        when(invoiceRepository.findAllWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(messageSource.getMessage(anyString(), any(), any())).thenReturn("Label");

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
}
