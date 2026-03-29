package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class BonAPayerEvent extends ApplicationEvent {
    private final UUID invoiceId;

    public BonAPayerEvent(Object source, UUID invoiceId) {
        super(source);
        this.invoiceId = invoiceId;
    }
}
