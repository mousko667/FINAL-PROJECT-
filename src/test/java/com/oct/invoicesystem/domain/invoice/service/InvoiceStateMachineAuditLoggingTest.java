package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.audit.service.AuditService;
import com.oct.invoicesystem.domain.department.model.Department;
import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceStateChangeListener;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.purchasing.service.ThreeWayMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.access.StateMachineAccessor;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.DefaultExtendedState;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceStateMachineAuditLoggingTest {

    @Mock InvoiceRepository invoiceRepository;
    @Mock StateMachineFactory<InvoiceStatus, InvoiceEvent> factory;
    @Mock InvoiceStateChangeListener listener;
    @Mock ApplicationEventPublisher publisher;
    @Mock ThreeWayMatchingService matchingService;
    @Mock PurchaseOrderRepository poRepo;
    @Mock GoodsReceiptNoteRepository grnRepo;
    @Mock ThreeWayMatchingResultRepository matchingResultRepo;
    @Mock AuditService auditService;

    @Mock StateMachine<InvoiceStatus, InvoiceEvent> sm;
    @Mock State<InvoiceStatus, InvoiceEvent> state;
    @Mock StateMachineAccessor<InvoiceStatus, InvoiceEvent> stateMachineAccessor;

    private InvoiceStateMachineServiceImpl service;
    private Invoice invoice;
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InvoiceStateMachineServiceImpl(
                invoiceRepository, factory, listener, publisher, matchingService,
                poRepo, grnRepo, matchingResultRepo, auditService);

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setReferenceNumber("FAC-2026-0042");
        invoice.setStatus(InvoiceStatus.BON_A_PAYER); // pre-payment state so RECORD_PAYMENT is plausible
        invoice.setAmount(new BigDecimal("850000"));
        invoice.setCurrency("XAF");
        invoice.setSupplierName("SOGARA");
        invoice.setDepartment(new Department()); // avoids null-value put into the extended state map

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoice.getId())).thenReturn(Optional.of(invoice));

        // Make the state machine "accept" and change state.
        DefaultExtendedState ext = new DefaultExtendedState();
        ext.getVariables().put(
                com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys.USER_ID, actorId);
        when(factory.getStateMachine(anyString())).thenReturn(sm);
        when(sm.getExtendedState()).thenReturn(ext);
        when(sm.getStateMachineAccessor()).thenReturn(stateMachineAccessor);
        doNothing().when(stateMachineAccessor).doWithAllRegions(any());
        when(sm.sendEvent(any(org.springframework.messaging.Message.class))).thenReturn(true);
        when(sm.getState()).thenReturn(state);
        when(state.getId()).thenReturn(InvoiceStatus.PAYE); // different from BON_A_PAYER -> stateChanged=true
    }

    @Test
    void sendEvent_recordPayment_logsPaymentBusinessAudit() {
        service.sendEvent(invoice.getId(), InvoiceEvent.RECORD_PAYMENT, null);

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> entityId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> newValue = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<UUID> uid = ArgumentCaptor.forClass(UUID.class);
        verify(auditService).logAction(uid.capture(), eq("INVOICE"), entityId.capture(),
                action.capture(), any(), newValue.capture(), any(), any());

        assertEquals("PAYMENT", action.getValue());
        assertEquals("FAC-2026-0042", entityId.getValue());
        assertEquals(actorId, uid.getValue());
        String details = newValue.getValue().toString();
        assertTrue(details.contains("850000"), details);
        assertTrue(details.contains("XAF"), details);
        assertTrue(details.contains("SOGARA"), details);
    }
}
