package com.oct.invoicesystem.domain.workflow.guard;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.guard.Guard;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRequiredGuard implements Guard<InvoiceStatus, InvoiceEvent> {

    private final InvoiceRepository invoiceRepository;

    @Override
    public boolean evaluate(StateContext<InvoiceStatus, InvoiceEvent> context) {
        UUID invoiceId = context.getMessageHeaders().get("invoice_id", UUID.class);
        if (invoiceId == null) {
            log.error("Missing invoice_id in headers");
            return false;
        }

        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return false;
        }

        // Must have at least one document attached
        if (invoice.getDocuments() == null || invoice.getDocuments().isEmpty()) {
            throw new ValidationException("Invoice cannot be submitted without at least one document");
        }

        return true;
    }
}
