package com.oct.invoicesystem.domain.invoice.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceStateChangeListener;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.notification.event.BonAPayerEvent;
import com.oct.invoicesystem.domain.notification.event.InvoiceRejectedEvent;
import com.oct.invoicesystem.domain.notification.event.InvoiceSubmittedEvent;
import com.oct.invoicesystem.domain.notification.event.InvoiceValidatedEvent;
import com.oct.invoicesystem.domain.purchasing.model.MatchingStatus;
import com.oct.invoicesystem.domain.purchasing.model.ThreeWayMatchingResult;
import com.oct.invoicesystem.domain.purchasing.repository.GoodsReceiptNoteRepository;
import com.oct.invoicesystem.domain.purchasing.repository.PurchaseOrderRepository;
import com.oct.invoicesystem.domain.purchasing.repository.ThreeWayMatchingResultRepository;
import com.oct.invoicesystem.domain.purchasing.service.ThreeWayMatchingService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
@Slf4j
public class InvoiceStateMachineServiceImpl implements InvoiceStateMachineService {

    private final InvoiceRepository invoiceRepository;
    private final StateMachineFactory<InvoiceStatus, InvoiceEvent> stateMachineFactory;
    private final InvoiceStateChangeListener invoiceStateChangeListener;
    private final ApplicationEventPublisher eventPublisher;
    private final ThreeWayMatchingService threeWayMatchingService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    private final ThreeWayMatchingResultRepository matchingResultRepository;

    public InvoiceStateMachineServiceImpl(
            InvoiceRepository invoiceRepository,
            StateMachineFactory<InvoiceStatus, InvoiceEvent> stateMachineFactory,
            InvoiceStateChangeListener invoiceStateChangeListener,
            ApplicationEventPublisher eventPublisher,
            ThreeWayMatchingService threeWayMatchingService,
            PurchaseOrderRepository purchaseOrderRepository,
            GoodsReceiptNoteRepository goodsReceiptNoteRepository,
            ThreeWayMatchingResultRepository matchingResultRepository) {
        this.invoiceRepository = invoiceRepository;
        this.stateMachineFactory = stateMachineFactory;
        this.invoiceStateChangeListener = invoiceStateChangeListener;
        this.eventPublisher = eventPublisher;
        this.threeWayMatchingService = threeWayMatchingService;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.goodsReceiptNoteRepository = goodsReceiptNoteRepository;
        this.matchingResultRepository = matchingResultRepository;
    }

    @Override
    @Transactional
    public void sendEvent(UUID invoiceId, InvoiceEvent event, Map<String, Object> variables) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id " + invoiceId));

        if (event.equals(InvoiceEvent.ARCHIVE)) {
            boolean automaticArchive = variables != null
                    && Boolean.TRUE.equals(variables.get(WorkflowExtendedStateKeys.AUTO_ARCHIVE));
            if (!automaticArchive) {
                throw new WorkflowException("Archive transition is automatic and can only be triggered by payment processing");
            }
        }

        if (event.equals(InvoiceEvent.SUBMIT)) {
            // Duplicate detection — block if same supplier already has a non-rejected invoice
            // with the same description within the last 365 days
            performDuplicateCheck(invoice);

            // Three-way matching — block if PO is linked and matching result is MISMATCH
            if (invoice.getPurchaseOrderId() != null) {
                performMatchingCheck(invoice);
            }
        }

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

        // Publish domain notification events after successful state transition
        publishNotificationEvent(event, invoiceId, variables);
    }

    /**
     * Duplicate detection: blocks submission if the same supplier already has a non-rejected,
     * non-archived invoice with the same description within the last 365 days.
     */
    private void performDuplicateCheck(Invoice invoice) {
        if (invoice.getSupplier() == null || invoice.getDescription() == null || invoice.getDescription().isBlank()) {
            return; // Cannot detect duplicates without supplier + description
        }
        try {
            java.time.Instant since = java.time.Instant.now().minus(365, java.time.temporal.ChronoUnit.DAYS);
            long count = invoiceRepository.countDuplicatesBySupplierAndDescription(
                    invoice.getSupplier().getId(), invoice.getDescription(), since);
            // Exclude the current invoice itself (it was just created so count should be 1 if it's a dup)
            if (count > 1) {
                throw new WorkflowException(
                        "Duplicate invoice detected: an invoice with the same supplier and description already exists in the system. "
                        + "Please verify this is not a duplicate before resubmitting.");
            }
        } catch (WorkflowException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Duplicate check failed for invoice {}: {}", invoice.getId(), e.getMessage());
        }
    }

    /**
     * Perform three-way matching check before invoice submission.
     * If invoice has a purchaseOrderId and matching result is MISMATCH,
     * block the transition unless an override exists.
     *
     * @param invoice the invoice to check
     * @throws WorkflowException if matching is MISMATCH and no override exists
     */
    private void performMatchingCheck(Invoice invoice) {
        try {
            // Check if a matching result already exists (override scenario)
            var existingResult = matchingResultRepository.findByInvoiceId(invoice.getId());
            if (existingResult.isPresent() && existingResult.get().getStatus().equals(MatchingStatus.OVERRIDDEN)) {
                log.info("Matching override found for invoice {}, proceeding with submission", invoice.getId());
                return;
            }

            // Fetch PO
            var po = purchaseOrderRepository.findByIdActive(invoice.getPurchaseOrderId())
                .orElseThrow(() -> new WorkflowException("Referenced purchase order not found or has been deleted"));

            // Fetch GRN if matching configuration requires it
            var grn = goodsReceiptNoteRepository.findByPurchaseOrderId(po.getId()).stream().findFirst().orElse(null);

            // Perform matching
            ThreeWayMatchingResult result = threeWayMatchingService.match(invoice, po, grn);

            // Update invoice matching_status
            invoice.setMatchingStatus(result.getStatus().name());
            invoiceRepository.save(invoice);

            // Throw exception if MISMATCH
            if (result.getStatus().equals(MatchingStatus.MISMATCH)) {
                throw new WorkflowException(
                    String.format("Invoice matching failed with MISMATCH status. Discrepancies: %s. An override is required to proceed.",
                        result.getDiscrepancyNotes())
                );
            }

            log.info("Matching check passed for invoice {} with status {}", invoice.getId(), result.getStatus());
        } catch (WorkflowException e) {
            throw e;
        } catch (Exception e) {
            // Critical path: matching MUST be evaluated. Do not proceed to SOUMIS if it
            // cannot be — surface the failure instead of silently degrading (MAJEUR-3).
            log.error("Matching check could not be evaluated for invoice {}: {}", invoice.getId(), e.getMessage(), e);
            throw new WorkflowException("error.matching.evaluation_failed");
        }
    }

    /**
     * Publish the appropriate notification domain event after a successful workflow transition.
     */
    private void publishNotificationEvent(InvoiceEvent event, UUID invoiceId, Map<String, Object> variables) {
        try {
            switch (event) {
                case SUBMIT, RESUBMIT ->
                        eventPublisher.publishEvent(new InvoiceSubmittedEvent(this, invoiceId));
                case VALIDATE_N1 ->
                        eventPublisher.publishEvent(new InvoiceValidatedEvent(this, invoiceId, "N1"));
                case VALIDATE_N2 ->
                        eventPublisher.publishEvent(new InvoiceValidatedEvent(this, invoiceId, "N2"));
                case BON_A_PAYER ->
                        eventPublisher.publishEvent(new BonAPayerEvent(this, invoiceId));
                case REJECT -> {
                    String reason = variables != null
                            ? (String) variables.getOrDefault("rejectionReason", "")
                            : "";
                    eventPublisher.publishEvent(new InvoiceRejectedEvent(this, invoiceId, reason));
                }
                default -> { /* ASSIGN_REVIEWER, RECORD_PAYMENT, ARCHIVE — no notification needed here */ }
            }
        } catch (Exception e) {
            log.error("Failed to publish notification event for invoice {}: {}", invoiceId, e.getMessage());
            // Intentionally not re-throwing — notification failure must never roll back the workflow transaction
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
