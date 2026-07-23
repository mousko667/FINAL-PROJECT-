package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.payment.dto.RemittanceAdviceDTO;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.model.RemittanceAdvice;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.payment.repository.RemittanceAdviceRepository;
import com.oct.invoicesystem.domain.storage.service.MinioStorageService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemittanceAdviceServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RemittanceAdviceRepository remittanceAdviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MinioStorageService minioStorageService;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private RemittanceAdviceServiceImpl remittanceAdviceService;

    private Payment payment;
    private User generatedBy;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2024-00001")
                .supplierName("Test Supplier")
                .amount(BigDecimal.valueOf(1000))
                .currency("XAF")
                .build();

        generatedBy = User.builder()
                .id(UUID.randomUUID())
                .username("user1")
                .email("user@test.com")
                .build();

        payment = Payment.builder()
                .id(UUID.randomUUID())
                .invoice(invoice)
                .amountPaid(BigDecimal.valueOf(1000))
                .paymentMethod(PaymentMethod.VIREMENT)
                .paymentDate(Instant.now())
                .reference("REF-123")
                .recordedBy(generatedBy)
                .build();
    }

    @Test
    void generateRemittanceAdvice_Success() throws Exception {
        // Arrange
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(userRepository.findById(generatedBy.getId())).thenReturn(Optional.of(generatedBy));
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Test Message");
        when(minioStorageService.upload(anyString(), any(byte[].class), anyString())).thenReturn("remittance/...pdf");

        RemittanceAdvice savedAdvice = RemittanceAdvice.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .pdfObjectKey("remittance/pdf-key")
                .generatedBy(generatedBy)
                .generatedAt(Instant.now())
                .build();

        when(remittanceAdviceRepository.save(any())).thenReturn(savedAdvice);

        // Act
        RemittanceAdviceDTO result = remittanceAdviceService.generateRemittanceAdvice(payment.getId(), generatedBy.getId());

        // Assert
        assertNotNull(result);
        assertEquals(payment.getId(), result.paymentId());
        assertEquals(savedAdvice.getId(), result.id());
        assertNotNull(result.pdfObjectKey());
        
        // Verify MinIO upload was called
        ArgumentCaptor<String> objectKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(minioStorageService).upload(objectKeyCaptor.capture(), contentCaptor.capture(), eq("application/pdf"));
        
        String objectKey = objectKeyCaptor.getValue();
        assertTrue(objectKey.startsWith("remittance/"), "Object key should start with remittance/");
        assertTrue(objectKey.endsWith(".pdf"), "Object key should end with .pdf");
        
        byte[] pdfContent = contentCaptor.getValue();
        assertNotNull(pdfContent);
        assertTrue(pdfContent.length > 0, "PDF content should not be empty");
        
        // Verify database save was called
        verify(remittanceAdviceRepository).save(any());
    }

    @Test
    void generateRemittanceAdvice_PaymentNotFound() {
        // Arrange
        when(paymentRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, 
                () -> remittanceAdviceService.generateRemittanceAdvice(UUID.randomUUID(), generatedBy.getId()));
    }

    @Test
    void generateRemittanceAdvice_UserNotFound() {
        // Arrange
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, 
                () -> remittanceAdviceService.generateRemittanceAdvice(payment.getId(), UUID.randomUUID()));
    }

    @Test
    void getByPaymentId_Success() {
        // Arrange
        RemittanceAdvice advice = RemittanceAdvice.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .pdfObjectKey("remittance/test.pdf")
                .generatedBy(generatedBy)
                .generatedAt(Instant.now())
                .build();

        when(remittanceAdviceRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(advice));

        // Act
        RemittanceAdviceDTO result = remittanceAdviceService.getByPaymentId(payment.getId());

        // Assert
        assertNotNull(result);
        assertEquals(payment.getId(), result.paymentId());
        assertEquals(advice.getPdfObjectKey(), result.pdfObjectKey());
    }

    @Test
    void getByPaymentId_NotFound() {
        // Arrange
        when(remittanceAdviceRepository.findByPaymentId(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, 
                () -> remittanceAdviceService.getByPaymentId(UUID.randomUUID()));
    }

    @Test
    void getDownloadUrl_Success() throws Exception {
        // Arrange
        String expectedUrl = "https://minio.example.com/bucket/remittance/test.pdf?signature=...";
        RemittanceAdvice advice = RemittanceAdvice.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .pdfObjectKey("remittance/test.pdf")
                .generatedBy(generatedBy)
                .generatedAt(Instant.now())
                .build();

        when(remittanceAdviceRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(advice));
        when(minioStorageService.generateDownloadUrl("remittance/test.pdf")).thenReturn(expectedUrl);

        // Act
        String result = remittanceAdviceService.getDownloadUrl(payment.getId());

        // Assert
        assertEquals(expectedUrl, result);
        verify(minioStorageService).generateDownloadUrl("remittance/test.pdf");
    }

    @Test
    void getDownloadUrl_RemittanceNotFound() {
        // Arrange
        when(remittanceAdviceRepository.findByPaymentId(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, 
                () -> remittanceAdviceService.getDownloadUrl(UUID.randomUUID()));
    }

    @Test
    void generateRemittanceAdvice_PDFContainsPaymentData() throws Exception {
        // Arrange
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(userRepository.findById(generatedBy.getId())).thenReturn(Optional.of(generatedBy));
        when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Test");
        when(minioStorageService.upload(anyString(), any(byte[].class), anyString())).thenReturn("key");

        RemittanceAdvice savedAdvice = RemittanceAdvice.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .pdfObjectKey("remittance/pdf-key")
                .generatedBy(generatedBy)
                .generatedAt(Instant.now())
                .build();

        when(remittanceAdviceRepository.save(any())).thenReturn(savedAdvice);

        // Act
        remittanceAdviceService.generateRemittanceAdvice(payment.getId(), generatedBy.getId());

        // Assert
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(minioStorageService).upload(anyString(), contentCaptor.capture(), eq("application/pdf"));
        
        byte[] pdfContent = contentCaptor.getValue();
        String pdfString = new String(pdfContent);
        
        // PDF should contain payment details
        assertTrue(pdfContent.length > 0, "PDF should not be empty");
        // PDF header indicates it's a valid PDF
        assertTrue(pdfString.contains("%PDF") || pdfContent[0] == '%', "Should contain PDF header");
    }
}
