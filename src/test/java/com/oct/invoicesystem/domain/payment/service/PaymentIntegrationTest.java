package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.department.repository.DepartmentRepository;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.domain.payment.dto.RemittanceAdviceDTO;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.model.RemittanceAdvice;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.payment.repository.RemittanceAdviceRepository;
import com.oct.invoicesystem.domain.report.dto.AgingReportDTO;
import com.oct.invoicesystem.domain.report.service.ReportService;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RemittanceAdviceService remittanceAdviceService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RemittanceAdviceRepository remittanceAdviceRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private InvoiceStateMachineService invoiceStateMachineService;

    @MockBean
    private MinioStorageService minioStorageService;

    private Invoice invoice;
    private User assistantComptable;
    private User assistantComptable2;
    private Department department;

    @BeforeEach
    void setUp() {
        // Create department
        department = Department.builder()
                .id(UUID.randomUUID())
                .code("DEPT-001")
                .build();
        department = departmentRepository.save(department);

        // Create user
        assistantComptable = User.builder()
                .id(UUID.randomUUID())
                .username("assistant_comptable_1")
                .email("assistant@test.com")
                .password("hashed_password")
                .firstName("Jean")
                .lastName("Dupont")
                .isActive(true)
                .build();
        assistantComptable = userRepository.save(assistantComptable);

        assistantComptable2 = User.builder()
                .id(UUID.randomUUID())
                .username("assistant_comptable_2")
                .email("assistant2@test.com")
                .password("hashed_password")
                .firstName("Marie")
                .lastName("Martin")
                .isActive(true)
                .build();
        assistantComptable2 = userRepository.save(assistantComptable2);

        // Create invoice
        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2024-00001")
                .department(department)
                .submittedBy(assistantComptable)
                .supplierName("Test Supplier Corp")
                .supplierEmail("supplier@test.com")
                .amount(BigDecimal.valueOf(5000))
                .currency("EUR")
                .status(InvoiceStatus.BON_A_PAYER)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .build();
        invoice = invoiceRepository.save(invoice);
    }

    @Test
    @Transactional
    void recordPayment_AutoGeneratesRemittanceAdvice() throws Exception {
        // Arrange
        when(minioStorageService.upload(anyString(), any(byte[].class), anyString()))
                .thenReturn("remittance/path/to/file.pdf");

        PaymentRequest paymentRequest = new PaymentRequest(
                BigDecimal.valueOf(5000),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "VIR-2024-001"
        );

        // Act
        PaymentDTO paymentDTO = paymentService.recordPayment(invoice.getId(), paymentRequest, assistantComptable.getId());

        // Assert - Payment recorded
        assertNotNull(paymentDTO);
        assertEquals(BigDecimal.valueOf(5000), paymentDTO.amountPaid());
        assertEquals("VIR-2024-001", paymentDTO.reference());

        // Assert - Remittance advice auto-generated
        RemittanceAdviceDTO remittance = remittanceAdviceService.getByPaymentId(paymentDTO.id());
        assertNotNull(remittance);
        assertNotNull(remittance.pdfObjectKey());
        assertEquals(paymentDTO.id(), remittance.paymentId());
    }

    @Test
    @Transactional
    void recordPayment_RemittanceDownloadUrlReturned() throws Exception {
        // Arrange
        String expectedUrl = "https://minio.example.com/bucket/remittance/test.pdf?signature=abc123";
        when(minioStorageService.upload(anyString(), any(byte[].class), anyString()))
                .thenReturn("remittance/path/to/file.pdf");
        when(minioStorageService.generateDownloadUrl(anyString()))
                .thenReturn(expectedUrl);

        PaymentRequest paymentRequest = new PaymentRequest(
                BigDecimal.valueOf(5000),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "VIR-2024-001"
        );

        // Act
        PaymentDTO paymentDTO = paymentService.recordPayment(invoice.getId(), paymentRequest, assistantComptable.getId());
        String downloadUrl = remittanceAdviceService.getDownloadUrl(paymentDTO.id());

        // Assert
        assertNotNull(downloadUrl);
        assertEquals(expectedUrl, downloadUrl);
    }

    @Test
    @Transactional
    void agingReport_CorrectlyReflectsOverdueInvoices() {
        // Arrange - Create multiple invoices with different due dates
        Invoice overdueInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-OVERDUE-001")
                .department(department)
                .submittedBy(assistantComptable)
                .supplierName("Overdue Supplier")
                .supplierEmail("overdue@test.com")
                .amount(BigDecimal.valueOf(1000))
                .currency("EUR")
                .status(InvoiceStatus.BON_A_PAYER)
                .issueDate(LocalDate.now().minusDays(60))
                .dueDate(LocalDate.now().minusDays(20)) // 20 days overdue
                .build();
        invoiceRepository.save(overdueInvoice);

        Invoice recentInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-RECENT-001")
                .department(department)
                .submittedBy(assistantComptable)
                .supplierName("Recent Supplier")
                .supplierEmail("recent@test.com")
                .amount(BigDecimal.valueOf(2000))
                .currency("EUR")
                .status(InvoiceStatus.EN_VALIDATION_N1)
                .issueDate(LocalDate.now().minusDays(10))
                .dueDate(LocalDate.now().minusDays(5)) // 5 days overdue
                .build();
        invoiceRepository.save(recentInvoice);

        // Act
        AgingReportDTO agingReport = reportService.getAgingAnalysis();

        // Assert
        assertNotNull(agingReport);
        assertNotNull(agingReport.getBuckets());
        assertTrue(agingReport.getTotalOverdueInvoiceCount() > 0);
        assertTrue(agingReport.getTotalOverdueAmount().compareTo(BigDecimal.ZERO) > 0);

        // Check that buckets contain expected invoices
        assertNotNull(agingReport.getBuckets().get("0_30"));
        assertTrue(agingReport.getBuckets().get("0_30").getInvoiceCount() >= 1);
    }

    @Test
    @Transactional
    void paymentWorkflow_CompleteFlow() throws Exception {
        // Arrange
        when(minioStorageService.upload(anyString(), any(byte[].class), anyString()))
                .thenReturn("remittance/payment-id/timestamp.pdf");
        when(minioStorageService.generateDownloadUrl(anyString()))
                .thenReturn("https://presigned-url.example.com");

        PaymentRequest paymentRequest = new PaymentRequest(
                BigDecimal.valueOf(5000),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "VIR-2024-WORKFLOW"
        );

        // Act 1: Record payment
        PaymentDTO recordedPayment = paymentService.recordPayment(invoice.getId(), paymentRequest, assistantComptable.getId());
        assertNotNull(recordedPayment, "Payment should be recorded");

        // Act 2: Verify remittance was auto-generated
        RemittanceAdviceDTO generatedRemittance = remittanceAdviceService.getByPaymentId(recordedPayment.id());
        assertNotNull(generatedRemittance, "Remittance should be auto-generated");
        assertTrue(generatedRemittance.pdfObjectKey().contains("remittance/"), "PDF object key format should be correct");

        // Act 3: Get download URL
        String downloadUrl = remittanceAdviceService.getDownloadUrl(recordedPayment.id());
        assertNotNull(downloadUrl, "Download URL should be generated");
        assertTrue(downloadUrl.contains("presigned-url"), "Download URL should be pre-signed");

        // Act 4: Verify aging analysis includes the invoice
        AgingReportDTO agingReport = reportService.getAgingAnalysis();
        assertNotNull(agingReport, "Aging report should be generated");

        // Assert - Complete workflow success
        assertEquals(BigDecimal.valueOf(5000), recordedPayment.amountPaid());
        assertEquals(PaymentMethod.VIREMENT.toString(), recordedPayment.paymentMethod().toString());
    }

    @Test
    @Transactional
    void multiplePaymentsCreateMultipleRemittances() throws Exception {
        // Arrange
        when(minioStorageService.upload(anyString(), any(byte[].class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice2 = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2024-00002")
                .department(department)
                .submittedBy(assistantComptable)
                .supplierName("Another Supplier")
                .supplierEmail("another@test.com")
                .amount(BigDecimal.valueOf(3000))
                .currency("EUR")
                .status(InvoiceStatus.BON_A_PAYER)
                .issueDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .build();
        invoice2 = invoiceRepository.save(invoice2);

        PaymentRequest request1 = new PaymentRequest(
                BigDecimal.valueOf(5000),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "VIR-001"
        );

        PaymentRequest request2 = new PaymentRequest(
                BigDecimal.valueOf(3000),
                PaymentMethod.CHEQUE,
                Instant.now(),
                "CHQ-001"
        );

        // Act
        PaymentDTO payment1 = paymentService.recordPayment(invoice.getId(), request1, assistantComptable.getId());
        PaymentDTO payment2 = paymentService.recordPayment(invoice2.getId(), request2, assistantComptable.getId());

        RemittanceAdviceDTO remittance1 = remittanceAdviceService.getByPaymentId(payment1.id());
        RemittanceAdviceDTO remittance2 = remittanceAdviceService.getByPaymentId(payment2.id());

        // Assert
        assertNotNull(remittance1);
        assertNotNull(remittance2);
        assertNotEquals(remittance1.pdfObjectKey(), remittance2.pdfObjectKey());
        assertEquals(2, remittanceAdviceRepository.count(), "Should have 2 remittance advices");
    }
}
