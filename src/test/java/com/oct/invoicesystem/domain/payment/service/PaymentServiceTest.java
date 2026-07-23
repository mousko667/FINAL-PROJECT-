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
import com.oct.invoicesystem.domain.payment.model.PaymentStatus;
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
        // AUDIT-029 : le paiement doit desormais correspondre exactement au montant facture.
        invoice.setAmount(new BigDecimal("1000.00"));

        assistantAdmin = new User();
        assistantAdmin.setId(UUID.randomUUID());
        assistantAdmin.setEmail("assistant@mail.com");

        request = new PaymentRequest(
                new BigDecimal("1000.00"),
                PaymentMethod.VIREMENT,
                Instant.now(),
                "REF-123",
                null
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
        // AUDIT-030 : le paiement laisse la facture en PAYE ; il n'archive plus.
        verify(invoiceStateMachineService, never()).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.ARCHIVE), anyMap());
    }

    @Test
    void recordPayment_FailsIfInvoiceNotBonAPayer() {
        invoice.setStatus(InvoiceStatus.SOUMIS);
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        WorkflowException ex = assertThrows(WorkflowException.class, 
                () -> paymentService.recordPayment(invoice.getId(), request, assistantAdmin.getId()));
        
        // N17: the service throws the i18n key; resolution happens in GlobalExceptionHandler.
        assertTrue(ex.getMessage().contains("error.payment.only_bon_a_payer"));
        verify(paymentRepository, never()).save(any());
    }

    /**
     * AUDIT-029 (D2) : le paiement integral est obligatoire. Un reglement partiel doit etre refuse,
     * sans quoi la garde existsByInvoiceId fermerait definitivement la facture sur un solde partiel
     * — c'est exactement le defaut constate en P3 (1 XAF soldant une facture de 600 000 XAF).
     */
    @Test
    void recordPayment_FailsIfAmountDoesNotMatchInvoiceAmount() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(false);

        // Facture de 1000.00 reglee par 1 XAF : le scenario exact d'AUDIT-029.
        PaymentRequest partial = new PaymentRequest(
                new BigDecimal("1"), PaymentMethod.VIREMENT, Instant.now(), "REF-PARTIAL", null);

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> paymentService.recordPayment(invoice.getId(), partial, assistantAdmin.getId()));

        assertTrue(ex.getMessage().contains("error.payment.amount_must_match_invoice"));
        verify(paymentRepository, never()).save(any());
        verify(invoiceStateMachineService, never()).sendEvent(any(), any(), anyMap());
    }

    /** AUDIT-029 : un sur-paiement est refuse au meme titre qu'un reglement partiel. */
    @Test
    void recordPayment_FailsIfAmountExceedsInvoiceAmount() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(false);

        PaymentRequest excessive = new PaymentRequest(
                new BigDecimal("1500.00"), PaymentMethod.VIREMENT, Instant.now(), "REF-OVER", null);

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> paymentService.recordPayment(invoice.getId(), excessive, assistantAdmin.getId()));

        assertTrue(ex.getMessage().contains("error.payment.amount_must_match_invoice"));
        verify(paymentRepository, never()).save(any());
    }

    /**
     * AUDIT-029 : la comparaison porte sur la VALEUR, pas sur l'echelle decimale. 1000 et 1000.00
     * sont le meme montant — compareTo, jamais equals (BigDecimal.equals distingue les echelles).
     */
    @Test
    void recordPayment_AcceptsSameAmountWithDifferentScale() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(false);
        when(userRepository.findById(assistantAdmin.getId())).thenReturn(Optional.of(assistantAdmin));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(a -> {
            Payment p = a.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        // Facture a 1000.00, reglement saisi "1000" : meme valeur, echelle differente.
        PaymentRequest sameValue = new PaymentRequest(
                new BigDecimal("1000"), PaymentMethod.VIREMENT, Instant.now(), "REF-SCALE", null);

        PaymentDTO dto = paymentService.recordPayment(invoice.getId(), sameValue, assistantAdmin.getId());

        assertNotNull(dto);
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.RECORD_PAYMENT), anyMap());
    }

    @Test
    void recordPayment_FailsIfAlreadyPaid() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(true);

        WorkflowException ex = assertThrows(WorkflowException.class,
                () -> paymentService.recordPayment(invoice.getId(), request, assistantAdmin.getId()));

        // N17: the service now throws the i18n key; GlobalExceptionHandler.resolve() localizes it.
        assertTrue(ex.getMessage().contains("error.payment.already_recorded"));
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

        when(paymentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(java.util.List.of(p));
        when(tabularExportService.export(
                eq(com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV),
                eq("Payments"),
                anyList(), anyList(), any(), any()))
            .thenReturn("CSV".getBytes());

        byte[] out = paymentService.exportPayments(
                null, null, null, com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV, "Payments", null, null);

        assertArrayEquals("CSV".getBytes(), out);

        ArgumentCaptor<java.util.List<java.util.List<String>>> rowsCap = ArgumentCaptor.forClass(java.util.List.class);
        verify(tabularExportService).export(
                eq(com.oct.invoicesystem.shared.export.TabularExportService.Format.CSV),
                eq("Payments"), anyList(), rowsCap.capture(), any(), any());
        java.util.List<String> row = rowsCap.getValue().get(0);
        assertEquals("INV-1", row.get(0));
        assertEquals("ACME", row.get(1));
        assertEquals("VIREMENT", row.get(2));
        assertEquals("PAY-1", row.get(3));
        assertEquals("1500.00", row.get(4));
        assertEquals("XAF", row.get(5));
        assertEquals("2026-06-01T00:00:00Z", row.get(6));
        assertEquals("assistant", row.get(7));
    }

    @Test
    void recordPayment_processedByDefault_finalizes() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(false);
        when(userRepository.findById(assistantAdmin.getId())).thenReturn(Optional.of(assistantAdmin));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(a -> {
            Payment p = a.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1000.00"), PaymentMethod.VIREMENT, Instant.now(), "PAY-1", null);

        PaymentDTO dto = paymentService.recordPayment(invoice.getId(), req, assistantAdmin.getId());

        assertEquals(PaymentStatus.PROCESSED, dto.status());
        assertNotNull(dto.processedDate());
        verify(remittanceAdviceService).generateRemittanceAdvice(any(), eq(assistantAdmin.getId()));
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.RECORD_PAYMENT), anyMap());
        // AUDIT-030 : le paiement laisse la facture en PAYE ; il n'archive plus.
        verify(invoiceStateMachineService, never()).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.ARCHIVE), anyMap());
    }

    @Test
    void recordPayment_scheduled_doesNotFinalize() {
        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));
        when(paymentRepository.existsByInvoiceId(invoice.getId())).thenReturn(false);
        when(userRepository.findById(assistantAdmin.getId())).thenReturn(Optional.of(assistantAdmin));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(a -> {
            Payment p = a.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentRequest req = new PaymentRequest(
                new BigDecimal("1000.00"), PaymentMethod.VIREMENT, Instant.now(), "PAY-1", true);

        PaymentDTO dto = paymentService.recordPayment(invoice.getId(), req, assistantAdmin.getId());

        assertEquals(PaymentStatus.SCHEDULED, dto.status());
        assertNull(dto.processedDate());
        verifyNoInteractions(remittanceAdviceService);
        verify(invoiceStateMachineService, never()).sendEvent(any(), any(), anyMap());
    }

    @Test
    void processPayment_happy_finalizes() {
        UUID paymentId = UUID.randomUUID();
        Payment scheduled = Payment.builder()
                .id(paymentId)
                .invoice(invoice)
                .status(PaymentStatus.SCHEDULED)
                .recordedBy(assistantAdmin)
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(scheduled));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(a -> a.getArgument(0));

        PaymentDTO dto = paymentService.processPayment(paymentId, assistantAdmin.getId());

        assertEquals(PaymentStatus.PROCESSED, dto.status());
        assertNotNull(dto.processedDate());
        verify(remittanceAdviceService).generateRemittanceAdvice(eq(paymentId), eq(assistantAdmin.getId()));
        verify(invoiceStateMachineService).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.RECORD_PAYMENT), anyMap());
        // AUDIT-030 : le traitement d'un paiement planifie n'archive plus non plus.
        verify(invoiceStateMachineService, never()).sendEvent(eq(invoice.getId()), eq(InvoiceEvent.ARCHIVE), anyMap());
    }

    @Test
    void processPayment_alreadyProcessed_throws() {
        UUID paymentId = UUID.randomUUID();
        Payment processed = Payment.builder()
                .id(paymentId)
                .invoice(invoice)
                .status(PaymentStatus.PROCESSED)
                .build();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(processed));

        assertThrows(WorkflowException.class,
                () -> paymentService.processPayment(paymentId, assistantAdmin.getId()));
    }

    @Test
    void processPayment_notFound_throws() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.processPayment(paymentId, assistantAdmin.getId()));
    }
}
