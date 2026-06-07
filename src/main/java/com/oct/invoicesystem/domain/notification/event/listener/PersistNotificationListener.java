package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.notification.model.Notification;
import com.oct.invoicesystem.domain.notification.model.NotificationType;
import com.oct.invoicesystem.domain.notification.repository.NotificationRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PersistNotificationListener {

    private final NotificationRepository notificationRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    /**
     * Persist a notification record for every N1 approver when an invoice is submitted.
     */
    @Async
    @EventListener
    public void onInvoiceSubmitted(InvoiceSubmittedEvent event) {
        log.info("Persisting notifications for InvoiceSubmittedEvent {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            var dept = invoice.getDepartment();
            List<User> targets = userRepository.findActiveUsersByRoleName(dept.getN1Role());
            targets.forEach(user -> save(user, invoice,
                    "Nouvelle facture à valider",
                    "New invoice pending N1 validation",
                    "Facture " + invoice.getReferenceNumber() + " du fournisseur " + invoice.getSupplierName() + " est en attente de votre validation N1.",
                    "Invoice " + invoice.getReferenceNumber() + " from supplier " + invoice.getSupplierName() + " is awaiting your N1 validation.",
                    NotificationType.SUBMISSION));
        });
    }

    /**
     * Persist a notification record for the next approver tier after N1 or N2 validation.
     */
    @Async
    @EventListener
    public void onInvoiceValidated(InvoiceValidatedEvent event) {
        log.info("Persisting notifications for InvoiceValidatedEvent {}, level {}", event.getInvoiceId(), event.getValidationLevel());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            var dept = invoice.getDepartment();
            List<User> targets;
            if ("N1".equals(event.getValidationLevel()) && dept.isRequiresN2()) {
                targets = userRepository.findActiveUsersByRoleName(dept.getN2Role());
            } else {
                targets = userRepository.findActiveUsersByRoleName("ROLE_DAF");
            }
            targets.forEach(user -> save(user, invoice,
                    "Facture en attente de votre validation",
                    "Invoice pending your validation",
                    "Facture " + invoice.getReferenceNumber() + " attend votre validation.",
                    "Invoice " + invoice.getReferenceNumber() + " is awaiting your validation.",
                    NotificationType.VALIDATION));
        });
    }

    /**
     * Persist a rejection notification for the invoice submitter.
     */
    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        log.info("Persisting notification for InvoiceRejectedEvent {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            User submitter = invoice.getSubmittedBy();
            if (submitter != null) {
                save(submitter, invoice,
                        "Facture rejetée",
                        "Invoice rejected",
                        "Votre facture " + invoice.getReferenceNumber() + " a été rejetée. Motif : " + event.getReason(),
                        "Your invoice " + invoice.getReferenceNumber() + " was rejected. Reason: " + event.getReason(),
                        NotificationType.REJECTION);
            }
        });
    }

    /**
     * Persist a BON_A_PAYER notification for the invoice submitter.
     */
    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        log.info("Persisting notification for BonAPayerEvent {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            User submitter = invoice.getSubmittedBy();
            if (submitter != null) {
                save(submitter, invoice,
                        "Bon à Payer accordé",
                        "BAP issued",
                        "Votre facture " + invoice.getReferenceNumber() + " a reçu le Bon à Payer.",
                        "Your invoice " + invoice.getReferenceNumber() + " has received the BAP approval.",
                        NotificationType.APPROVAL);
            }
        });
    }

    /**
     * Persist an in-app notification for the supplier when payment is recorded.
     */
    @Async
    @EventListener
    public void onInvoicePayed(InvoicePayedEvent event) {
        log.info("Persisting payment notification for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            // Notify the supplier user(s) linked to this invoice
            if (invoice.getSupplier() != null) {
                userRepository.findActiveUsersBySupplierId(invoice.getSupplier().getId()).forEach(user ->
                    save(user, invoice,
                        "Paiement effectué",
                        "Payment processed",
                        "Votre facture " + invoice.getReferenceNumber() + " a été payée. L'avis de remise vous a été envoyé par email.",
                        "Your invoice " + invoice.getReferenceNumber() + " has been paid. A remittance advice has been sent to your email.",
                        NotificationType.PAYMENT)
                );
            }
        });
    }

    /**
     * Persist an SLA breach notification for the current approver when the deadline is exceeded.
     */
    @Async
    @EventListener
    public void onApprovalDeadline(ApprovalDeadlineEvent event) {
        log.info("Persisting SLA deadline notification for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            var dept = invoice.getDepartment();
            if (dept == null) return;

            List<User> approvers;
            String roleToNotify;
            switch (invoice.getStatus()) {
                case EN_VALIDATION_N1 -> roleToNotify = dept.getN1Role();
                case EN_VALIDATION_N2 -> roleToNotify = dept.getN2Role();
                case VALIDE -> roleToNotify = "ROLE_DAF";
                default -> { return; }
            }
            approvers = userRepository.findActiveUsersByRoleName(roleToNotify);

            approvers.forEach(user -> save(user, invoice,
                    "URGENT — Délai d'approbation SLA dépassé",
                    "URGENT — Approval SLA deadline breached",
                    "La facture " + invoice.getReferenceNumber() + " attend votre validation depuis plus de 3 jours ouvrables. Action immédiate requise.",
                    "Invoice " + invoice.getReferenceNumber() + " has been awaiting your approval for more than 3 business days. Immediate action required.",
                    NotificationType.VALIDATION));
        });
    }

    private void save(User user, Invoice invoice,
                      String titleFr, String titleEn,
                      String messageFr, String messageEn,
                      NotificationType type) {
        try {
            Notification notification = Notification.builder()
                    .user(user)
                    .invoice(invoice)
                    .titleFr(titleFr)
                    .titleEn(titleEn)
                    .messageFr(messageFr)
                    .messageEn(messageEn)
                    .type(type)
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
            log.debug("Notification persisted for user {} on invoice {}", user.getUsername(), invoice.getReferenceNumber());
        } catch (Exception e) {
            log.error("Failed to persist notification for user {}: {}", user.getId(), e.getMessage());
        }
    }
}
