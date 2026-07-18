package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.user.model.Role;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.model.UserRole;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationListenerTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock InvoiceRepository invoiceRepository;
    @Mock UserRepository userRepository;

    @InjectMocks WebSocketNotificationListener listener;

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
    void onInvoiceSubmitted_sendsWebSocketToN1Approvers() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_VALIDATEUR_N1_DRH")).thenReturn(List.of(n1User));

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, invoice.getId()));

        verify(messagingTemplate).convertAndSendToUser(
                eq(n1User.getId().toString()), eq("/notifications"), anyString());
    }

    // ── N5 : rejet, facture INTERNE (submitter = AA) → AA seul + fournisseur ──
    @Test
    void onInvoiceRejected_internalInvoice_pushesToSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "reason"));

        verify(messagingTemplate).convertAndSendToUser(eq(aaSubmitter.getId().toString()), eq("/notifications"), anyString());
        verify(messagingTemplate).convertAndSendToUser(eq(supplierUser.getId().toString()), eq("/notifications"), anyString());
        verify(userRepository, never()).findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
    }

    // ── N5 : rejet, facture PORTAIL (submitter = fournisseur) → AA actifs + fournisseur ──
    @Test
    void onInvoiceRejected_portalInvoice_pushesToActiveAAsAndSupplier() {
        invoice.setSubmittedBy(supplierUser); // fournisseur, non-AA
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "reason"));

        verify(messagingTemplate).convertAndSendToUser(eq(aaSubmitter.getId().toString()), eq("/notifications"), anyString());
        verify(messagingTemplate).convertAndSendToUser(eq(supplierUser.getId().toString()), eq("/notifications"), anyString());
    }

    // ── N6 : BAP, facture INTERNE → AA seul + fournisseur ──
    @Test
    void onBonAPayer_internalInvoice_pushesToSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(messagingTemplate).convertAndSendToUser(eq(aaSubmitter.getId().toString()), eq("/notifications"), anyString());
        verify(messagingTemplate).convertAndSendToUser(eq(supplierUser.getId().toString()), eq("/notifications"), anyString());
    }

    // ── N5+N6 : BAP, facture PORTAIL → AA actifs + fournisseur ──
    @Test
    void onBonAPayer_portalInvoice_pushesToActiveAAsAndSupplier() {
        invoice.setSubmittedBy(supplierUser);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));
        when(userRepository.findActiveUsersBySupplierId(supplier.getId())).thenReturn(List.of(supplierUser));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(messagingTemplate).convertAndSendToUser(eq(aaSubmitter.getId().toString()), eq("/notifications"), anyString());
        verify(messagingTemplate).convertAndSendToUser(eq(supplierUser.getId().toString()), eq("/notifications"), anyString());
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));

        verifyNoInteractions(messagingTemplate);
    }
}
