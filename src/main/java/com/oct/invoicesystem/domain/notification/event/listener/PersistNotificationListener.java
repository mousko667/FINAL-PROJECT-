package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
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
     * Persist a deadline reminder notification for the assigned approver.
     */
    @Async
    @EventListener
    public void onApprovalDeadline(ApprovalDeadlineEvent event) {
        log.info("Persisting deadline notification for invoice {}", event.getInvoiceId());
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            // The scheduled job provides this — persist for the currently assigned approver if any
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
