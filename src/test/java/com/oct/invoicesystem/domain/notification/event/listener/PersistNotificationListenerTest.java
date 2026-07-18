package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.model.Notification;
import com.oct.invoicesystem.domain.notification.model.NotificationType;
import com.oct.invoicesystem.domain.notification.repository.NotificationRepository;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
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
import java.util.Set;
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
    private User aaSubmitter;
    private User n1User;
    private Supplier supplier;
    private User supplierUser;

    private static User withRole(User u, String roleName) {
        u.setUserRoles(Set.of(UserRole.builder()
                .role(Role.builder().name(roleName).build())
                .user(u)
                .build()));
        return u;
    }

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setCode("DRH");
        dept.setN1Role("ROLE_VALIDATEUR_N1_DRH");
        dept.setRequiresN2(false);

        aaSubmitter = withRole(User.builder()
                .id(UUID.randomUUID()).username("assistant").email("a@oct.ga")
                .password("x").firstName("A").lastName("B").build(), "ROLE_ASSISTANT_COMPTABLE");

        n1User = User.builder()
                .id(UUID.randomUUID()).username("n1").email("n1@oct.ga")
                .password("x").firstName("N").lastName("One").build();

        supplier = Supplier.builder().id(UUID.randomUUID()).build();
        supplierUser = User.builder()
                .id(UUID.randomUUID()).username("supplier").email("supplier@acme.ga")
                .password("x").firstName("S").lastName("Up").supplier(supplier).build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID()).referenceNumber("FAC-2026-00001")
                .supplierName("Acme").amount(BigDecimal.valueOf(50_000)).currency("XAF")
                .supplier(supplier)
                .department(dept).submittedBy(aaSubmitter).build();
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

    // ── N5 : rejet, facture INTERNE (submitter = AA) → AA seul + fournisseur in-app ──
    @Test
    void onInvoiceRejected_internalInvoice_persistsForSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Wrong amount"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.REJECTION);
        assertThat(saved.stream().anyMatch(n -> n.getMessageFr().contains("Wrong amount"))).isTrue();
        verify(userRepository, never()).findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
    }

    // ── N5 : rejet, facture PORTAIL (submitter = fournisseur) → AA actifs + fournisseur in-app ──
    @Test
    void onInvoiceRejected_portalInvoice_persistsForActiveAAsAndSupplier() {
        invoice.setSubmittedBy(supplierUser); // fournisseur, non-AA
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Wrong amount"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
    }

    // ── N6 : BAP, facture INTERNE → AA seul + fournisseur in-app ──
    @Test
    void onBonAPayer_internalInvoice_persistsForSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertThat(saved).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
        assertThat(saved).allMatch(n -> n.getType() == NotificationType.APPROVAL);
    }

    // ── N5+N6 : BAP, facture PORTAIL → AA actifs + fournisseur in-app ──
    @Test
    void onBonAPayer_portalInvoice_persistsForActiveAAsAndSupplier() {
        invoice.setSubmittedBy(supplierUser);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(Notification::getUser).contains(aaSubmitter, supplierUser);
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());
        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));
        verifyNoInteractions(notificationRepository);
    }
}
