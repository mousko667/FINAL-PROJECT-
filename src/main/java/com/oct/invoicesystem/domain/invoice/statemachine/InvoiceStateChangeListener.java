package com.oct.invoicesystem.domain.invoice.statemachine;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.InvoiceStatusHistory;
import com.oct.invoicesystem.domain.workflow.repository.InvoiceStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Intercepts state changes to persist the new state to the Invoice entity
 * and write an audit record to InvoiceStatusHistory.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvoiceStateChangeListener extends StateMachineInterceptorAdapter<InvoiceStatus, InvoiceEvent> {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceStatusHistoryRepository invoiceStatusHistoryRepository;
    private final UserRepository userRepository;

    @Override
    public void preStateChange(State<InvoiceStatus, InvoiceEvent> state, Message<InvoiceEvent> message,
                               Transition<InvoiceStatus, InvoiceEvent> transition,
                               StateMachine<InvoiceStatus, InvoiceEvent> stateMachine,
                               StateMachine<InvoiceStatus, InvoiceEvent> rootStateMachine) {

        Optional.ofNullable(message).ifPresent(msg -> {
            UUID invoiceId = msg.getHeaders().getOrDefault("invoice_id", null) instanceof UUID 
                    ? msg.getHeaders().get("invoice_id", UUID.class) : null;
            
            if (invoiceId != null) {
                Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
                if (invoice != null && transition.getSource() != null && transition.getTarget() != null) {
                    InvoiceStatus fromStatus = transition.getSource().getId();
                    InvoiceStatus toStatus = transition.getTarget().getId();

                    if (fromStatus != toStatus) {
                        invoice.setStatus(toStatus);
                        invoiceRepository.save(invoice);

                        UUID userId = (UUID) stateMachine.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.USER_ID);
                        String changeReason = (String) stateMachine.getExtendedState().getVariables().get(WorkflowExtendedStateKeys.CHANGE_REASON);

                        User changedBy = null;
                        if (userId != null) {
                            changedBy = userRepository.findById(userId).orElse(null);
                        }

                        if (changedBy == null) {
                            log.warn("Recording status history for invoice {} without a valid user ID ()", invoiceId);
                        }

                        InvoiceStatusHistory history = InvoiceStatusHistory.builder()
                                .invoice(invoice)
                                .fromStatus(fromStatus.name())
                                .toStatus(toStatus.name())
                                .changedBy(changedBy)
                                .changeReason(changeReason)
                                .build();

                        invoiceStatusHistoryRepository.save(history);
                    }
                }
            }
        });
    }

    @Override
    public Exception stateMachineError(StateMachine<InvoiceStatus, InvoiceEvent> stateMachine, Exception exception) {
        stateMachine.getExtendedState().getVariables().put("STATE_MACHINE_ERROR", exception);
        return super.stateMachineError(stateMachine, exception);
    }
}
