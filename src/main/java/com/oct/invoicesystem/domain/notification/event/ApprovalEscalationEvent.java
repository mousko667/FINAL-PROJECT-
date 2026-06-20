package com.oct.invoicesystem.domain.notification.event;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/** Published when an overdue approval step is escalated (B1). Carries the resolved recipient. */
public class ApprovalEscalationEvent extends ApplicationEvent {
    private final UUID invoiceId;
    private final UUID recipientUserId;

    public ApprovalEscalationEvent(Object source, UUID invoiceId, UUID recipientUserId) {
        super(source);
        this.invoiceId = invoiceId;
        this.recipientUserId = recipientUserId;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getRecipientUserId() { return recipientUserId; }
}
