package com.oct.invoicesystem.domain.workflow.guard;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceValidationService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Guards the {@code REJETE → SOUMIS} (RESUBMIT) transition: a rejected invoice may only be
 * resubmitted once it has actually been corrected since the rejection (N1-A, PROB-118).
 *
 * <p>The correction is proven by the optimistic-lock {@code version} having grown past the value
 * captured at rejection ({@link Invoice#getVersionAtRejection()}). When no rejection version was
 * captured (legacy invoices rejected before this rule existed), the guard stays permissive so it
 * never blocks pre-existing rejected invoices.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResubmissionVersionGuard implements Guard<InvoiceStatus, InvoiceEvent> {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceValidationService invoiceValidationService;

    @Override
    public boolean evaluate(StateContext<InvoiceStatus, InvoiceEvent> context) {
        UUID invoiceId = context.getMessageHeaders().get("invoice_id", UUID.class);
        if (invoiceId == null) {
            log.error("Missing invoice_id in headers for RESUBMIT");
            return false;
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return false;
        }

        // Legacy rejected invoices carry no captured version — stay permissive rather than block
        // a correction path that predates this rule.
        if (invoice.getVersionAtRejection() == null) {
            return true;
        }

        // Throws WorkflowException when version has not grown since the rejection (no correction).
        invoiceValidationService.validateResubmissionVersion(
                invoice.getVersion(), invoice.getVersionAtRejection());
        return true;
    }
}
