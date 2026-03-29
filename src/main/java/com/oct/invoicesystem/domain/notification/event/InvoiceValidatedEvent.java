package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class InvoiceValidatedEvent extends ApplicationEvent {
    private final UUID invoiceId;
    private final String validationLevel; // "N1" or "N2"

    public InvoiceValidatedEvent(Object source, UUID invoiceId, String validationLevel) {
        super(source);
        this.invoiceId = invoiceId;
        this.validationLevel = validationLevel;
    }
}
