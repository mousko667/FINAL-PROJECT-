package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.supplier.model.Supplier;
import com.oct.invoicesystem.domain.supplier.repository.SupplierRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EmailNotificationListener {

    private final EmailService emailService;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    // ── Invoice submitted → notify N1 approvers + supplier (confirmation) ──

    @Async
    @EventListener
    public void onInvoiceSubmitted(InvoiceSubmittedEvent event) {
        log.info("Handling InvoiceSubmittedEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Department dept = invoice.getDepartment();

            // Notify N1 approvers
            List<User> n1Approvers = userRepository.findActiveUsersByRoleName(dept.getN1Role());
            if (!n1Approvers.isEmpty()) {
                Map<String, Object> vars = buildCommonVariables(invoice);
                notifyUsers(n1Approvers, "Nouvelle facture à valider (N1)", "invoice-submitted", vars);
            }

            // Notify supplier: submission confirmed
            notifySupplier(invoice, "Votre facture a été soumise / Invoice submission confirmed", "supplier-invoice-submitted",
                    buildCommonVariables(invoice));
        });
    }

    // ── Invoice validated → notify next approver tier ──

    @Async
    @EventListener
    public void onInvoiceValidated(InvoiceValidatedEvent event) {
        log.info("Handling InvoiceValidatedEvent for invoice {}, level {}", event.getInvoiceId(), event.getValidationLevel());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Department dept = invoice.getDepartment();
            List<User> targets;
            if ("N1".equals(event.getValidationLevel()) && dept.isRequiresN2()) {
                targets = userRepository.findActiveUsersByRoleName(dept.getN2Role());
            } else {
                targets = userRepository.findActiveUsersByRoleName("ROLE_DAF");
            }
            if (!targets.isEmpty()) {
                notifyUsers(targets, "Facture en attente de validation", "invoice-submitted",
                        buildCommonVariables(invoice));
            }
        });
    }

    // ── Invoice rejected → notify submitter (AA) + supplier ──

    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        log.info("Handling InvoiceRejectedEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Map<String, Object> vars = buildCommonVariables(invoice);
            vars.put("reason", event.getReason());

            // Notify the resolved Assistant(s) Comptable (N5)
            List<User> accounting = resolveAccountingRecipients(invoice);
            if (!accounting.isEmpty()) {
                notifyUsers(accounting, "Facture rejetée / Invoice rejected", "invoice-rejected", vars);
            }

            // Notify supplier
            notifySupplier(invoice, "Votre facture a été rejetée / Your invoice was rejected", "supplier-invoice-rejected", vars);
        });
    }

    // ── BON_A_PAYER → notify submitter (AA) ──

    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        log.info("Handling BonAPayerEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Map<String, Object> vars = buildCommonVariables(invoice);

            // Notify the resolved Assistant(s) Comptable (N5)
            List<User> accounting = resolveAccountingRecipients(invoice);
            if (!accounting.isEmpty()) {
                notifyUsers(accounting, "Bon À Payer accordé / BAP issued", "invoice-approved", vars);
            }

            // Notify supplier: invoice approved for payment (N6)
            notifySupplier(invoice, "Votre facture a été approuvée pour paiement / Your invoice was approved for payment",
                    "supplier-invoice-approved", vars);
        });
    }

    // ── Payment recorded → notify supplier with payment confirmation + remittance ──

    @Async
    @EventListener
    public void onInvoicePayed(InvoicePayedEvent event) {
        log.info("Handling InvoicePayedEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Map<String, Object> vars = buildCommonVariables(invoice);
            vars.put("paymentId", event.getPaymentId());

            // Notify supplier: payment processed + remittance advice
            notifySupplier(invoice, "Paiement effectué / Payment processed", "supplier-invoice-paid", vars);
        });
    }

    // ── Approval deadline ──

    @Async
    @EventListener
    public void onApprovalDeadline(ApprovalDeadlineEvent event) {
        log.info("Handling ApprovalDeadlineEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            // Notify the current N1/N2 approver about the approaching deadline
            Department dept = invoice.getDepartment();
            List<User> approvers = new ArrayList<>();
            switch (invoice.getStatus()) {
                case EN_VALIDATION_N1 -> approvers = userRepository.findActiveUsersByRoleName(dept.getN1Role());
                case EN_VALIDATION_N2 -> approvers = userRepository.findActiveUsersByRoleName(dept.getN2Role());
                case VALIDE -> approvers = userRepository.findActiveUsersByRoleName("ROLE_DAF");
                default -> { }
            }
            if (!approvers.isEmpty()) {
                Map<String, Object> vars = buildCommonVariables(invoice);
                notifyUsers(approvers, "URGENT — Délai d'approbation dépassé / Approval SLA breached", "invoice-submitted", vars);
            }
        });
    }

    // ── Helpers ──

    /**
     * Résout le(s) destinataire(s) interne(s) « Assistant Comptable » d'une facture.
     * Si le soumetteur porte {@code ROLE_ASSISTANT_COMPTABLE} (facture interne saisie par un AA),
     * il est seul destinataire — il suit son propre dossier. Sinon (facture portail : le soumetteur
     * est le compte fournisseur), on cible tous les AA actifs.
     */
    private List<User> resolveAccountingRecipients(Invoice invoice) {
        User submitter = invoice.getSubmittedBy();
        if (submitter != null && submitter.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ASSISTANT_COMPTABLE".equals(a.getAuthority()))) {
            return List.of(submitter);
        }
        return userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
    }

    private Map<String, Object> buildCommonVariables(Invoice invoice) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("invoice", invoice);
        vars.put("reference", invoice.getReferenceNumber());
        vars.put("supplierName", invoice.getSupplierName());
        vars.put("amount", invoice.getAmount() + " " + invoice.getCurrency());
        vars.put("frontendUrl", frontendUrl);
        return vars;
    }

    /** Notify the supplier contact email associated with this invoice. */
    private void notifySupplier(Invoice invoice, String subject, String template, Map<String, Object> vars) {
        try {
            String supplierEmail = null;

            // Prefer the linked Supplier entity email
            if (invoice.getSupplier() != null) {
                Supplier supplier = supplierRepository.findByIdAndDeletedAtIsNull(invoice.getSupplier().getId())
                        .orElse(null);
                if (supplier != null) supplierEmail = supplier.getContactEmail();
            }

            // Fall back to inline supplierEmail field on the invoice
            if (supplierEmail == null) supplierEmail = invoice.getSupplierEmail();

            if (supplierEmail != null && !supplierEmail.isBlank()) {
                emailService.sendEmailToUsers(List.of(supplierEmail), subject, template, vars);
            } else {
                log.debug("No supplier email for invoice {} — skipping supplier notification", invoice.getReferenceNumber());
            }
        } catch (Exception e) {
            log.error("Failed to send supplier notification for invoice {}: {}", invoice.getId(), e.getMessage());
        }
    }

    private void notifyUsers(List<User> users, String subject, String template, Map<String, Object> vars) {
        List<String> emails = users.stream().map(User::getEmail).filter(e -> e != null && !e.isBlank()).collect(Collectors.toList());
        if (!emails.isEmpty()) {
            emailService.sendEmailToUsers(emails, subject, template, vars);
        }
    }
}
