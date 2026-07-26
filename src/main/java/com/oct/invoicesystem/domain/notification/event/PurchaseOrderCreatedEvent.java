package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Raised when a purchase order is created, so the supplier it targets is notified (in-app + email)
 * that a new order awaits them on the portal.
 */
@Getter
public class PurchaseOrderCreatedEvent extends ApplicationEvent {
    private final UUID purchaseOrderId;

    public PurchaseOrderCreatedEvent(Object source, UUID purchaseOrderId) {
        super(source);
        this.purchaseOrderId = purchaseOrderId;
    }
}
