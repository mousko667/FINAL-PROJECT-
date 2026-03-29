package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Service to interact with the Invoice BAP state machine.
 */
public interface InvoiceStateMachineService {

    /**
     * Sends an event to the state machine for a specific invoice.
     * 
     * @param invoiceId the ID of the invoice
     * @param event the event to trigger
     * @param variables additional variables to put in the extended state (optional)
     * @throws com.oct.invoicesystem.shared.exception.WorkflowException if transition is denied
     * @throws com.oct.invoicesystem.shared.exception.ResourceNotFoundException if invoice is not found
     */
    void sendEvent(UUID invoiceId, InvoiceEvent event, Map<String, Object> variables);
}
