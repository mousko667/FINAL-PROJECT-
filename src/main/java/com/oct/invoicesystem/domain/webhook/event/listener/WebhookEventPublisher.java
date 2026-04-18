package com.oct.invoicesystem.domain.webhook.event.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.*;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.repository.WebhookRepository;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookEventPublisher {
    private final WebhookRepository webhookRepository;
    private final InvoiceRepository invoiceRepository;
    private final WebhookService webhookService;

    @Async
    @EventListener
    public void onInvoiceSubmitted(InvoiceSubmittedEvent event) {
        publishWebhookEvent("INVOICE_SUBMITTED", event.getInvoiceId());
    }

    @Async
    @EventListener
    public void onInvoiceValidated(InvoiceValidatedEvent event) {
        publishWebhookEvent("INVOICE_VALIDATED", event.getInvoiceId());
    }

    @Async
    @EventListener
    public void onInvoiceRejected(InvoiceRejectedEvent event) {
        publishWebhookEvent("INVOICE_REJECTED", event.getInvoiceId());
    }

    @Async
    @EventListener
    public void onBonAPayer(BonAPayerEvent event) {
        publishWebhookEvent("INVOICE_PAID", event.getInvoiceId());
    }

    private void publishWebhookEvent(String eventType, java.util.UUID invoiceId) {
        try {
            List<Webhook> webhooks = webhookRepository.findByIsActiveTrue();
            
            invoiceRepository.findById(invoiceId).ifPresent(invoice -> {
                Map<String, Object> payload = buildPayload(eventType, invoice);
                
                for (Webhook webhook : webhooks) {
                    if (webhook.getEvents().contains(eventType)) {
                        // Deliver webhook asynchronously with signature using stored secret hash
                        webhookService.deliverWebhook(webhook, eventType, payload);
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to publish webhook events for invoice {} on event {}: {}", 
                    invoiceId, eventType, e.getMessage(), e);
        }
    }

    private Map<String, Object> buildPayload(String eventType, com.oct.invoicesystem.domain.invoice.model.Invoice invoice) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", eventType);
        payload.put("timestamp", Instant.now().toString());
        payload.put("invoiceId", invoice.getId());
        payload.put("referenceNumber", invoice.getReferenceNumber());
        payload.put("supplierId", invoice.getSupplier() != null ? invoice.getSupplier().getId() : null);
        payload.put("amount", invoice.getAmount());
        payload.put("currency", invoice.getCurrency());
        payload.put("status", invoice.getStatus().name());
        return payload;
    }
}
