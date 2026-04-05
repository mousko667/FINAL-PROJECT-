package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EmailNotificationListener {

    private final EmailService emailService;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Async
    @EventListener
    public void onInvoiceSubmitted(InvoiceSubmittedEvent event) {
        log.info("Handling InvoiceSubmittedEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            Department dept = invoice.getDepartment();
            List<User> n1Approvers = userRepository.findActiveUsersByRoleName(dept.getN1Role());
            
            if (!n1Approvers.isEmpty()) {
                Map<String, Object> vars = buildCommonVariables(invoice);
                notifyUsers(n1Approvers, "Nouvelle facture à valider (N1) / New invoice for N1 validation", "invoice-submitted", vars);
            }
        });
    }

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
                Map<String, Object> vars = buildCommonVariables(invoice);
                notifyUsers(targets, "Facture en attente de validation / Invoice pending validation", "invoice-submitted", vars);
            }
        });
    }

    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        log.info("Handling InvoiceRejectedEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            User submitter = invoice.getSubmittedBy();
            if (submitter != null) {
                Map<String, Object> vars = buildCommonVariables(invoice);
                vars.put("reason", event.getReason());
                notifyUsers(List.of(submitter), "Facture rejetée / Invoice rejected", "invoice-rejected", vars);
            }
        });
    }

    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        log.info("Handling BonAPayerEvent for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            User submitter = invoice.getSubmittedBy();
            if (submitter != null) {
                Map<String, Object> vars = buildCommonVariables(invoice);
                notifyUsers(List.of(submitter), "Bon À Payer accordé / BAP issued", "invoice-approved", vars);
            }
        });
    }

    @Async
    @EventListener
    public void onApprovalDeadline(ApprovalDeadlineEvent event) {
        log.info("Handling ApprovalDeadlineEvent for invoice {}", event.getInvoiceId());
        // Detailed logic will follow in P4-11, but base structure is here
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            // Need approver for the step, fetch step, etc.
        });
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

    private void notifyUsers(List<User> users, String subject, String template, Map<String, Object> vars) {
        List<String> emails = users.stream().map(User::getEmail).collect(Collectors.toList());
        emailService.sendEmailToUsers(emails, subject, template, vars);
    }
}
