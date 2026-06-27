package com.oct.invoicesystem.domain.payment.service;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.invoice.service.InvoiceStateMachineService;
import com.oct.invoicesystem.domain.invoice.statemachine.InvoiceEvent;
import com.oct.invoicesystem.domain.invoice.statemachine.WorkflowExtendedStateKeys;
import com.oct.invoicesystem.domain.payment.dto.BatchPaymentRequest;
import com.oct.invoicesystem.domain.payment.dto.BatchPaymentResultDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentDTO;
import com.oct.invoicesystem.domain.payment.dto.PaymentRequest;
import com.oct.invoicesystem.domain.payment.model.Payment;
import com.oct.invoicesystem.domain.payment.model.PaymentStatus;
import com.oct.invoicesystem.domain.payment.repository.PaymentRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.notification.event.InvoicePayedEvent;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;
import com.oct.invoicesystem.shared.exception.WorkflowException;
import com.oct.invoicesystem.shared.export.TabularExportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final InvoiceStateMachineService invoiceStateMachineService;
    private final RemittanceAdviceService remittanceAdviceService;
    private final ApplicationEventPublisher eventPublisher;
    /**
     * Self-reference (the Spring proxy) so the batch loop can invoke {@link #recordPayment} through
     * the transactional proxy — each call then commits in its own transaction, which is what makes
     * the batch best-effort (one failing line does not roll back the others). A direct {@code this.}
     * call would bypass the proxy and run everything in a single transaction.
     */
    private final ObjectProvider<PaymentService> selfProvider;
    private final TabularExportService tabularExportService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              InvoiceRepository invoiceRepository,
                              UserRepository userRepository,
                              InvoiceStateMachineService invoiceStateMachineService,
                              RemittanceAdviceService remittanceAdviceService,
                              ApplicationEventPublisher eventPublisher,
                              ObjectProvider<PaymentService> selfProvider,
                              TabularExportService tabularExportService) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.invoiceStateMachineService = invoiceStateMachineService;
        this.remittanceAdviceService = remittanceAdviceService;
        this.eventPublisher = eventPublisher;
        this.selfProvider = selfProvider;
        this.tabularExportService = tabularExportService;
    }

    @Override
    @Transactional
    public PaymentDTO recordPayment(UUID invoiceId, PaymentRequest request, UUID userId) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found id: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.BON_A_PAYER) {
            throw new WorkflowException("Payment can only be recorded for invoices in BON_A_PAYER status");
        }

        if (paymentRepository.existsByInvoiceId(invoiceId)) {
            throw new WorkflowException("Payment already recorded for this invoice");
        }

        User recordedBy = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean isScheduled = Boolean.TRUE.equals(request.scheduled());

        Payment payment = Payment.builder()
                .invoice(invoice)
                .amountPaid(request.amountPaid())
                .paymentDate(request.paymentDate())
                .paymentMethod(request.paymentMethod())
                .reference(request.reference())
                .recordedBy(recordedBy)
                .status(isScheduled ? PaymentStatus.SCHEDULED : PaymentStatus.PROCESSED)
                .processedDate(isScheduled ? null : Instant.now())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Recorded payment {} for invoice {} (status={})", payment.getId(), invoiceId, payment.getStatus());

        if (!isScheduled) {
            finalizePayment(payment, userId);
        }

        return toDTO(payment);
    }

    /**
     * Effets de bord d'un paiement execute : avis de paiement, evenement metier, transitions
     * BON_A_PAYER -> PAYE puis PAYE -> ARCHIVE. Partage par la creation directe et processPayment.
     */
    private void finalizePayment(Payment payment, UUID userId) {
        UUID invoiceId = payment.getInvoice().getId();

        remittanceAdviceService.generateRemittanceAdvice(payment.getId(), userId);
        log.info("Auto-generated remittance advice for payment {}", payment.getId());

        try {
            eventPublisher.publishEvent(new InvoicePayedEvent(this, invoiceId, payment.getId()));
        } catch (Exception e) {
            log.error("Failed to publish InvoicePayedEvent for invoice {}: {}", invoiceId, e.getMessage());
        }

        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.RECORD_PAYMENT,
                Map.of(WorkflowExtendedStateKeys.USER_ID, userId));

        invoiceStateMachineService.sendEvent(invoiceId, InvoiceEvent.ARCHIVE,
                Map.of(
                        WorkflowExtendedStateKeys.USER_ID, userId,
                        WorkflowExtendedStateKeys.AUTO_ARCHIVE, true
                ));
    }

    @Override
    @Transactional
    public PaymentDTO processPayment(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found id: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SCHEDULED) {
            throw new WorkflowException("payment.already.processed");
        }

        payment.setStatus(PaymentStatus.PROCESSED);
        payment.setProcessedDate(Instant.now());
        payment = paymentRepository.save(payment);
        log.info("Processed scheduled payment {}", paymentId);

        finalizePayment(payment, userId);
        return toDTO(payment);
    }

    /**
     * Pays several invoices at once (B3). Best-effort: each invoice is paid at its full amount via
     * {@link #recordPayment} through the proxy, so it runs in its own transaction and a failing line
     * (e.g. wrong status, already paid) does not roll back the others. Not annotated
     * {@code @Transactional} on purpose — wrapping the loop in one transaction would defeat the
     * per-line semantics.
     */
    @Override
    public BatchPaymentResultDTO recordBatchPayment(BatchPaymentRequest request, UUID userId) {
        PaymentService self = selfProvider.getObject();
        List<BatchPaymentResultDTO.LineResult> results = new ArrayList<>();

        for (UUID invoiceId : request.invoiceIds()) {
            try {
                Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                        .orElseThrow(() -> new ResourceNotFoundException("Invoice not found id: " + invoiceId));
                String reference = "PAY-" + invoice.getReferenceNumber() + "-"
                        + Long.toString(Instant.now().toEpochMilli()).substring(7);
                PaymentRequest perInvoice = new PaymentRequest(
                        invoice.getAmount(), request.paymentMethod(), request.paymentDate(), reference, null);

                PaymentDTO dto = self.recordPayment(invoiceId, perInvoice, userId);
                results.add(BatchPaymentResultDTO.LineResult.ok(invoiceId, dto.id(), dto.reference()));
            } catch (Exception e) {
                log.warn("Batch payment failed for invoice {}: {}", invoiceId, e.getMessage());
                results.add(BatchPaymentResultDTO.LineResult.failure(invoiceId, e.getMessage()));
            }
        }

        long succeeded = results.stream().filter(BatchPaymentResultDTO.LineResult::success).count();
        return new BatchPaymentResultDTO(
                results.size(), (int) succeeded, (int) (results.size() - succeeded), results);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPaymentByInvoiceId(UUID invoiceId) {
        Payment payment = paymentRepository.findByInvoiceId(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for invoice " + invoiceId));
        return toDTO(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentDTO> listPayments(String departmentCode, Pageable pageable) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return paymentRepository.findAll(pageable).map(this::toDTO);
        }
        return paymentRepository.findByInvoiceDepartmentCode(departmentCode, pageable).map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportPayments(String departmentCode, TabularExportService.Format format) {
        List<Payment> payments = (departmentCode == null || departmentCode.isBlank())
                ? paymentRepository.findAll()
                : paymentRepository.findByInvoiceDepartmentCode(departmentCode);

        List<String> headers = List.of(
                "Référence facture", "Fournisseur", "Mode de paiement", "Référence paiement",
                "Montant payé", "Devise", "Date de paiement", "Enregistré par");

        List<List<String>> rows = payments.stream().map(p -> {
            Invoice inv = p.getInvoice();
            return List.of(
                    nz(inv == null ? null : inv.getReferenceNumber()),
                    nz(inv == null ? null : inv.getSupplierName()),
                    p.getPaymentMethod() == null ? "" : p.getPaymentMethod().name(),
                    nz(p.getReference()),
                    p.getAmountPaid() == null ? "" : p.getAmountPaid().toPlainString(),
                    nz(inv == null ? null : inv.getCurrency()),
                    p.getPaymentDate() == null ? "" : p.getPaymentDate().toString(),
                    p.getRecordedBy() == null ? "" : nz(p.getRecordedBy().getUsername()));
        }).toList();

        return tabularExportService.export(format, "Payments", headers, rows);
    }

    private PaymentDTO toDTO(Payment payment) {
        return new PaymentDTO(
                payment.getId(),
                payment.getInvoice().getId(),
                payment.getAmountPaid(),
                payment.getPaymentMethod(),
                payment.getPaymentDate(),
                payment.getReference(),
                payment.getRecordedBy() != null ? payment.getRecordedBy().getId() : null,
                payment.getCreatedAt(),
                payment.getStatus(),
                payment.getProcessedDate()
        );
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
