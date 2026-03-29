package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceStateChangeListener;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceStateMachineServiceImpl implements InvoiceStateMachineService {

    private final InvoiceRepository invoiceRepository;
    private final StateMachineFactory<InvoiceStatus, InvoiceEvent> stateMachineFactory;
    private final InvoiceStateChangeListener invoiceStateChangeListener;

    @Override
    @Transactional
    public void sendEvent(UUID invoiceId, InvoiceEvent event, Map<String, Object> variables) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id " + invoiceId));

        StateMachine<InvoiceStatus, InvoiceEvent> sm = build(invoice);

        if (variables != null) {
            sm.getExtendedState().getVariables().putAll(variables);
        }

        sm.getExtendedState().getVariables().put(WorkflowExtendedStateKeys.DEPARTMENT, invoice.getDepartment());

        if (!sm.getExtendedState().getVariables().containsKey(WorkflowExtendedStateKeys.USER_ID)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User user) {
                sm.getExtendedState().getVariables().put(WorkflowExtendedStateKeys.USER_ID, user.getId());
            }
        }

        Message<InvoiceEvent> message = MessageBuilder.withPayload(event)
                .setHeader("invoice_id", invoice.getId())
                .build();
        InvoiceStatus originalStatus = invoice.getStatus();
        boolean accepted = sm.sendEvent(message);
        
        Exception error = (Exception) sm.getExtendedState().getVariables().get("STATE_MACHINE_ERROR");
        if (error != null) {
            if (error instanceof RuntimeException) {
                throw (RuntimeException) error;
            }
            throw new WorkflowException(error.getMessage(), error);
        }

        boolean stateChanged = sm.getState() != null && sm.getState().getId() != originalStatus;
        if (!accepted || !stateChanged) {
            throw new WorkflowException("Transition denied for event " + event + " from state " + originalStatus);
        }
    }

    private StateMachine<InvoiceStatus, InvoiceEvent> build(Invoice invoice) {
        StateMachine<InvoiceStatus, InvoiceEvent> sm = stateMachineFactory.getStateMachine(invoice.getId().toString());
        sm.stop();

        sm.getStateMachineAccessor().doWithAllRegions(sma -> {
            sma.addStateMachineInterceptor(invoiceStateChangeListener);
            sma.resetStateMachine(new DefaultStateMachineContext<>(invoice.getStatus(), null, null, null));
        });

        sm.start();
        return sm;
    }
}
