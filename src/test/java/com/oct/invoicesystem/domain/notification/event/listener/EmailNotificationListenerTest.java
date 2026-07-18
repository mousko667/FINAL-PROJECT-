package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationListenerTest {

    @Mock EmailService emailService;
    @Mock InvoiceRepository invoiceRepository;
    @Mock UserRepository userRepository;
    @Mock SupplierRepository supplierRepository;

    @InjectMocks
    EmailNotificationListener listener;

    private Invoice invoice;
    private User n1User;
    private User aaSubmitter;

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
                .id(UUID.randomUUID())
                .username("assistant")
                .email("assistant@oct.ga")
                .password("x").firstName("A").lastName("B")
                .build(), "ROLE_ASSISTANT_COMPTABLE");

        n1User = User.builder()
                .id(UUID.randomUUID())
                .username("n1")
                .email("n1@oct.ga")
                .password("x").firstName("N").lastName("One")
                .build();

        invoice = Invoice.builder()
                .id(UUID.randomUUID())
                .referenceNumber("FAC-2026-00001")
                .supplierName("Acme")
                .supplierEmail("supplier@acme.ga")
                .amount(BigDecimal.valueOf(100_000))
                .currency("XAF")
                .department(dept)
                .submittedBy(aaSubmitter)
                .build();
    }

    @Test
    void onInvoiceSubmitted_sendsEmailToN1Approvers() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_VALIDATEUR_N1_DRH")).thenReturn(List.of(n1User));

        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(eq(List.of("n1@oct.ga")), anyString(), anyString(), anyMap());
    }

    @Test
    void onInvoiceSubmitted_doesNothing_whenInvoiceNotFound() {
        when(invoiceRepository.findById(any())).thenReturn(Optional.empty());
        listener.onInvoiceSubmitted(new InvoiceSubmittedEvent(this, UUID.randomUUID()));
        verifyNoInteractions(emailService);
    }

    // ── N5 : rejet, facture INTERNE (submitter = AA) → cet AA seul + fournisseur ──
    @Test
    void onInvoiceRejected_internalInvoice_sendsToSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Missing docs"));

        // AA interne (le submitter lui-même), pas d'appel à findActiveUsersByRoleName
        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-rejected"), anyMap());
        verify(userRepository, never()).findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
        // fournisseur
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-rejected"), anyMap());
    }

    // ── N5 : rejet, facture PORTAIL (submitter = fournisseur, non-AA) → AA résolus + fournisseur ──
    @Test
    void onInvoiceRejected_portalInvoice_resolvesActiveAAs() {
        User portalSubmitter = User.builder()
                .id(UUID.randomUUID()).username("supplier").email("supplier@acme.ga")
                .password("x").firstName("S").lastName("Up").build(); // aucun rôle AA
        invoice.setSubmittedBy(portalSubmitter);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));

        listener.onInvoiceRejected(new InvoiceRejectedEvent(this, invoice.getId(), "Missing docs"));

        // interne = AA actifs, PAS le fournisseur, via le template interne
        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-rejected"), anyMap());
        // fournisseur reçoit SON template (et pas le template interne)
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-rejected"), anyMap());
    }

    // ── N6 : BAP, facture INTERNE → AA seul (template interne) + fournisseur (nouveau template) ──
    @Test
    void onBonAPayer_internalInvoice_notifiesSubmitterAndSupplier() {
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-approved"), anyMap());
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-approved"), anyMap());
    }

    // ── N5+N6 : BAP, facture PORTAIL → AA résolus + fournisseur ──
    @Test
    void onBonAPayer_portalInvoice_resolvesAAsAndNotifiesSupplier() {
        User portalSubmitter = User.builder()
                .id(UUID.randomUUID()).username("supplier").email("supplier@acme.ga")
                .password("x").firstName("S").lastName("Up").build();
        invoice.setSubmittedBy(portalSubmitter);
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE")).thenReturn(List.of(aaSubmitter));

        listener.onBonAPayer(new BonAPayerEvent(this, invoice.getId()));

        verify(emailService).sendEmailToUsers(eq(List.of("assistant@oct.ga")), anyString(), eq("invoice-approved"), anyMap());
        verify(emailService).sendEmailToUsers(eq(List.of("supplier@acme.ga")), anyString(), eq("supplier-invoice-approved"), anyMap());
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
