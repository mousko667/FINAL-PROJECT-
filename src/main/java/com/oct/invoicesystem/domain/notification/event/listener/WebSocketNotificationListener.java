package com.oct.invoicesystem.domain.notification.event.listener;

import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WebSocketNotificationListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;

    @Async
    @EventListener
    public void onInvoiceSubmitted(InvoiceSubmittedEvent event) {
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            var dept = invoice.getDepartment();
            List<User> targets = userRepository.findActiveUsersByRoleName(dept.getN1Role());
            String payload = buildPayload("SUBMISSION", "Facture à valider / Invoice pending validation", invoice.getReferenceNumber());
            targets.forEach(user -> sendToUser(user, payload));
        });
    }

    @Async
    @EventListener
    public void onInvoiceValidated(InvoiceValidatedEvent event) {
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            var dept = invoice.getDepartment();
            List<User> targets;
            if ("N1".equals(event.getValidationLevel()) && dept.isRequiresN2()) {
                targets = userRepository.findActiveUsersByRoleName(dept.getN2Role());
            } else {
                targets = userRepository.findActiveUsersByRoleName("ROLE_DAF");
            }
            String payload = buildPayload("VALIDATION", "Facture en attente de validation", invoice.getReferenceNumber());
            targets.forEach(user -> sendToUser(user, payload));
        });
    }

    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            User submitter = invoice.getSubmittedBy();
            if (submitter != null) {
                String payload = buildPayload("REJECTION", "Facture rejetée / Invoice rejected", invoice.getReferenceNumber());
                sendToUser(submitter, payload);
            }
        });
    }

    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        invoiceRepository.findById(event.getInvoiceId()).ifPresent(invoice -> {
            User submitter = invoice.getSubmittedBy();
            if (submitter != null) {
                String payload = buildPayload("APPROVAL", "Bon à Payer accordé / BAP issued", invoice.getReferenceNumber());
                sendToUser(submitter, payload);
            }
        });
    }

    /**
     * Send a WebSocket message to a specific user's notification topic.
     * Topic: /user/{userId}/notifications
     *
     * @param user    target user
     * @param payload message payload as JSON string
     */
    private void sendToUser(User user, String payload) {
        try {
            messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/notifications",
                    payload
            );
            log.debug("WebSocket notification sent to user {}", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to send WebSocket notification to user {}: {}", user.getId(), e.getMessage());
            // Intentionally not re-throwing — WebSocket failure must never roll back a transaction
        }
    }

    private String buildPayload(String type, String title, String reference) {
        return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"reference\":\"" + reference + "\"}";
    }
}
