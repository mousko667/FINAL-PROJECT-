package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.model.Notification;
import com.oct.invoicesystem.domain.notification.model.NotificationType;
import com.oct.invoicesystem.domain.notification.repository.NotificationRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistNotificationListenerTest {

    @Mock NotificationRepository notificationRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PersistNotificationListener listener;

    private Invoice invoice;
    private User submitter;
    private User n1User;

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setCode("DRH");
        dept.setN1Role("ROLE_VALIDATEUR_N1_DRH");
        dept.setRequiresN2(false);

        submitter = User.builder()
                .id(UUID.randomUUID()).username("assistant").email("a@oct.ga")
                .password("x").firstName("A").lastName("B").build();

        n1User = User.builder()
                .id(UUID.randomUUID()).username("n1").email("n1@oct.ga")
                .password("x").firstName("N").lastName("One").build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID()).referenceNumber("FAC-2026-00001")
                .supplierName("Acme").amount(BigDecimal.valueOf(50_000)).currency("XAF")
                .department(dept).submittedBy(submitter).build();
    }

    @Test
    void onInvoiceSubmitted_persistsNotificationForN1Approvers() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_VALIDATEUR_N1_DRH")).thenReturn(List.of(n1User));

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.SUBMISSION);
        assertThat(captor.getValue().getUser()).isEqualTo(n1User);
    }

    @Test
    void onInvoiceRejected_persistsNotificationForSubmitter() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Wrong amount"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.REJECTION);
        assertThat(captor.getValue().getUser()).isEqualTo(submitter);
        assertThat(captor.getValue().getMessageFr()).contains("Wrong amount");
    }

    @Test
    void onBonAPayer_persistsNotificationForSubmitter() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.APPROVAL);
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));

        verifyNoInteractions(notificationRepository);
    }
}
