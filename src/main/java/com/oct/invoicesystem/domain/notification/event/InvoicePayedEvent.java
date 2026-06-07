package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class InvoicePayedEvent extends ApplicationEvent {
    private final UUID invoiceId;
    private final UUID paymentId;

    public InvoicePayedEvent(Object source, UUID invoiceId, UUID paymentId) {
        super(source);
        this.invoiceId = invoiceId;
        this.paymentId = paymentId;
    }
}
