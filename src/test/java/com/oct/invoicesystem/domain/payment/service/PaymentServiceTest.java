package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.PaymentMethod;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InvoiceStateMachineService invoiceStateMachineService;
    @Mock
    private RemittanceAdviceService remittanceAdviceService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private com.oct.invoicesystem.shared.export.TabularExportService tabularExportService;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<PaymentService> selfProvider;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Invoice invoice;
    private User assistantAdmin;
    private PaymentRequest request;

    @BeforeEach
    void setUp() {
        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setStatus(InvoiceStatus.BON_A_PAYER);

        assistantAdmin = new User();
        assistantAdmin.setId(UUID.randomUUID());
        assistantAdmin.setEmail("assistant@mail.com");

        request = new PaymentRequest(
                new BigDecimal("1000.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REF-123"
        );
    }

    @Test
    void recordPayment_Success() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(false);
        when(userRepository.findById(assistantAdmin.getId())).thenReturn(Optional.of(assistantAdmin));
        
        Payment savedPayment = new Payment();
        savedPayment.setId(UUID.randomUUID());
        savedPayment.setInvoice(invoice);
        savedPayment.setAmountPaid(request.amountPaid());
        savedPayment.setPaymentMethod(request.paymentMethod());
        savedPayment.setReference(request.reference());
        savedPayment.setRecordedBy(assistantAdmin);

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        PaymentDTO dto = paymentService.recordPayment(invoice.getId(), request, assistantAdmin.getId());

        assertNotNull(dto);
        assertEquals(request.amountPaid(), dto.amountPaid());
        
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.RECORD_PAYMENT), anyMap());
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.ARCHIVE), anyMap());
    }

    @Test
    void recordPayment_FailsIfInvoiceNotBonAPayer() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        WorkflowException ex = assertThrows(WorkflowException.class, 
                () -> paymentService.recordPayment(invoice.getId(), request, assistantAdmin.getId()));
        
        assertTrue(ex.getMessage().contains("BON_A_PAYER"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void recordPayment_FailsIfAlreadyPaid() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(true);

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> paymentService.recordPayment(invoice.getId(), request, assistantAdmin.getId()));

        assertTrue(ex.getMessage().contains("Payment already recorded"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void exportPayments_buildsRowsFromEntities() {
        Invoice inv = new Invoice();
        inv.setReferenceNumber("INV-1");
        inv.setSupplierName("ACME");
        inv.setCurrency("XAF");

        User recorder = new User();
        recorder.setUsername("assistant");

        Payment p = new Payment();
        p.setInvoice(inv);
        p.setAmountPaid(new BigDecimal("1500.00"));
        p.setPaymentMethod(PaymentMethod.VIREMENT);
        p.setReference("PAY-1");
        p.setPaymentDate(Instant.parse("2026-06-01T00:00:00Z"));
        p.setRecordedBy(recorder);

        when(paymentRepository.findAll()).thenReturn(java.util.List.of(p));
        when(tabularExportService.export(
                eq(com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV),
                eq("Payments"),
                anyList(), anyList()))
            .thenReturn("CSV".getBytes());

        byte[] out = paymentService.exportPayments(
                null, com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV);

        assertArrayEquals("CSV".getBytes(), out);

        ArgumentCaptor<java.util.List<java.util.List<String>>> rowsCap = ArgumentCaptor.forClass(java.util.List.class);
        verify(tabularExportService).export(
                eq(com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV),
                eq("Payments"), anyList(), rowsCap.capture());
        java.util.List<String> row = rowsCap.getValue().get(0);
        assertEquals("INV-1", row.get(0));
        assertEquals("ACME", row.get(1));
        assertEquals("VIREMENT", row.get(2));
        assertEquals("PAY-1", row.get(3));
        assertEquals("1500.00", row.get(4));
        assertEquals("XAF", row.get(5));
        assertEquals("assistant", row.get(7));
    }
}
