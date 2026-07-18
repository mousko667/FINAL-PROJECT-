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

        // A rejection reason is always a predefined code (optionally followed by a free-text
        // detail), composed as "[CODE] detail" upstream in ApprovalController. A predefined code
        // is a structured reason on its own, so the guard only enforces that the reason is present.
        // The minimum-length rule for the free-text detail (only meaningful for the AUTRE code) is
        // owned by ApprovalController; applying a length threshold to the composed "[CODE]" string
        // here wrongly rejected short but valid codes such as "[DOUBLON]" (9 chars). See PROB-117.
        if (rejectionReason == null || rejectionReason.trim().isEmpty()) {
            throw new ValidationException("Rejection reason is mandatory");
        }

        return true;
    }
}
