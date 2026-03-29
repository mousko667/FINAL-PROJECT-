package com.oct.invoicesystem.domain.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ApprovalDeadlineEvent extends ApplicationEvent {
    private final UUID invoiceId;
    private final UUID approvalStepId;

    public ApprovalDeadlineEvent(Object source, UUID invoiceId, UUID approvalStepId) {
        super(source);
        this.invoiceId = invoiceId;
        this.approvalStepId = approvalStepId;
    }
}
