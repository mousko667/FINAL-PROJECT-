package com.oct.invoicesystem.domain.report.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
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
}
