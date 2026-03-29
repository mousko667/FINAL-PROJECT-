package com.oct.invoicesystem.domain.workflow.guard;

import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RejectionReasonGuard implements Guard<InvoiceStatus, InvoiceEvent> {

    @Override
    public boolean evaluate(StateContext<InvoiceStatus, InvoiceEvent> context) {
        Object reasonObj = context.getExtendedState().getVariables().get("rejectionReason");
        String rejectionReason = null;
        
        if (reasonObj instanceof String) {
            rejectionReason = (String) reasonObj;
        }

        if (rejectionReason == null || rejectionReason.trim().length() < 10) {
            throw new ValidationException("Rejection reason is mandatory and must contain at least 10 characters");
        }

        return true;
    }
}
