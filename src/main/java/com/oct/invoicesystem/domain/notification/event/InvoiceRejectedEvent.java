package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class InvoiceRejectedEvent extends ApplicationEvent {
    private final UUID invoiceId;
    private final String reason;

    public InvoiceRejectedEvent(Object source, UUID invoiceId, String reason) {
        super(source);
        this.invoiceId = invoiceId;
        this.reason = reason;
    }
}
