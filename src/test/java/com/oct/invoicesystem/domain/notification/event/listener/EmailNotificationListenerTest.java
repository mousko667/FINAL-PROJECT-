package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

    @Mock EmailService emailService;
    @Mock InvoiceRepository invoiceRepository;
    @Mock UserRepository userRepository;

    @InjectMocks
    EmailNotificationListener listener;

    private Invoice invoice;
    private User n1User;
    private User submitter;

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setCode("DRH");
        dept.setN1Role("ROLE_VALIDATEUR_N1_DRH");
        dept.setRequiresN2(false);

        submitter = User.builder()
                .id(UUID.randomUUID())
                .username("assistant")
                .email("assistant@oct.ga")
                .password("x")
                .firstName("A")
                .lastName("B")
                .build();

        n1User = User.builder()
                .id(UUID.randomUUID())
                .username("n1")
                .email("n1@oct.ga")
                .password("x")
                .firstName("N")
                .lastName("One")
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00001")
                .supplierName("Acme")
                .amount(BigDecimal.valueOf(100_000))
                .currency("XAF")
                .department(dept)
                .submittedBy(submitter)
                .build();
    }

    @Test
    void onInvoiceSubmitted_sendsEmailToN1Approvers() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_VALIDATEUR_N1_DRH")).thenReturn(List.of(n1User));

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(
                eq(List.of("n1@oct.ga")),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());
        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));
        verifyNoInteractions(emailService);
    }

    @Test
    void onInvoiceRejected_sendsEmailToSubmitter() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Missing docs"));

        verify(emailService).sendEmailToUsers(
                eq(List.of("assistant@oct.ga")),
                anyString(),
                eq("invoice-rejected"),
                anyMap()
        );
    }

    @Test
    void onBonAPayer_sendsEmailToSubmitter() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(
                eq(List.of("assistant@oct.ga")),
                anyString(),
                eq("invoice-approved"),
                anyMap()
        );
    }

    @Test
    void onInvoiceValidated_N1_singleLevel_sendsEmailToDAF() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        User daf = User.builder().id(UUID.randomUUID()).email("daf@oct.ga").username("daf").password("x").firstName("D").lastName("AF").build();
        when(userRepository.findActiveUsersByRoleName("ROLE_DAF")).thenReturn(List.of(daf));

        listener.onInvoiceValidated(new InvoiceValidatedEvent(this, invoice.getId(), "N1"));

        verify(emailService).sendEmailToUsers(eq(List.of("daf@oct.ga")), anyString(), anyString(), anyMap());
    }
}
