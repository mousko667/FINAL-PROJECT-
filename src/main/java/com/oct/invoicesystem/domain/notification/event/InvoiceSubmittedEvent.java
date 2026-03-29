package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class InvoiceSubmittedEvent extends ApplicationEvent {
    private final UUID invoiceId;

    public InvoiceSubmittedEvent(Object source, UUID invoiceId) {
        super(source);
        this.invoiceId = invoiceId;
    }
}
