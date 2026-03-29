package com.oct.invoicesystem.domain.workflow.guard;

import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import org.springframework.statemachine.StateContext;
import org.springframework.stereotype.Component;

/**
 * Routes {@link InvoiceEvent#VALIDATE_N1} to N2 vs VALIDE using {@link Department#isRequiresN2()} from DB-backed
 * {@link Department} in extended state — never hardcoded.
 */
@Component
public class DepartmentTransitionGuard {

    /**
     * @return true if N1 validation should move the invoice to {@link InvoiceStatus#EN_VALIDATION_N2}
     */
    public boolean requiresN2(StateContext<InvoiceStatus, InvoiceEvent> ctx) {
        Object d = ctx.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.DEPARTMENT);
        return d instanceof Department dept && dept.isRequiresN2();
    }

    /**
     * @return true if N1 validation should move the invoice directly to {@link InvoiceStatus#VALIDE}
     */
    public boolean isSingleLevel(StateContext<InvoiceStatus, InvoiceEvent> ctx) {
        Object d = ctx.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.DEPARTMENT);
        return d instanceof Department dept && !dept.isRequiresN2();
    }
}
