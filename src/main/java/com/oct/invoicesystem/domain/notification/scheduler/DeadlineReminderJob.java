package com.oct.invoicesystem.domain.notification.scheduler;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.ApprovalDeadlineEvent;
import com.oct.invoicesystem.domain.notification.event.ApprovalEscalationEvent;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.payment.model.PaymentAlertRule;
import com.oct.invoicesystem.domain.payment.repository.PaymentAlertRuleRepository;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.model.EscalationRule;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
import com.oct.invoicesystem.domain.workflow.repository.EscalationRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadlineReminderJob {

    private final ApprovalStepRepository approvalStepRepository;
    private final InvoiceRepository invoiceRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentAlertRuleRepository paymentAlertRuleRepository;
    private final EscalationRuleRepository escalationRuleRepository;

    /** Fallback threshold (days) used when no payment alert rule is configured (B4). */
    private static final int DEFAULT_DUE_ALERT_DAYS = 7;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Runs daily at 07:00. Sends deadline reminders (24h warning) AND SLA escalations (past deadline).
     * Reminder  → assigned approver
     * Escalation → next approval tier in the same department (contextual), else DAF only,
     *              once overdue past the smallest active EscalationRule threshold (B1).
     *              Sends email + persists an in-app notification. Admin is never a recipient.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendDeadlineReminders() {
        log.info("Running deadline reminder + SLA escalation job...");
        Instant now  = Instant.now();
        Instant in24h = now.plus(24, ChronoUnit.HOURS);

        // All PENDING steps with a deadline set
        List<ApprovalStep> allPending = approvalStepRepository
                .findByStatusAndDeadlineBefore(ApprovalStepStatus.PENDING, in24h);

        List<ApprovalStep> approaching = allPending.stream()
                .filter(s -> s.getDeadline() != null && s.getDeadline().isAfter(now))
                .toList();

        List<ApprovalStep> overdue = allPending.stream()
                .filter(s -> s.getDeadline() != null && !s.getDeadline().isAfter(now))
                .toList();

        log.info("Deadline approaching: {} steps, overdue: {} steps", approaching.size(), overdue.size());

        // ── Reminders ──────────────────────────────────────────────────────
        for (ApprovalStep step : approaching) {
            try {
                if (step.getApprover() == null) continue;
                User approver = step.getApprover();
                var invoice   = step.getInvoice();

                Map<String, Object> vars = buildStepVars(invoice, step);
                emailService.sendEmail(
                        approver.getEmail(),
                        "⏰ Rappel de délai / Approval Deadline Reminder",
                        "deadline-reminder",
                        vars
                );
                eventPublisher.publishEvent(new ApprovalDeadlineEvent(this, invoice.getId(), step.getId()));
                log.info("Deadline reminder → {} for {}", approver.getEmail(), invoice.getReferenceNumber());
            } catch (Exception e) {
                log.error("Reminder failed for step {}: {}", step.getId(), e.getMessage());
            }
        }

        // ── SLA Escalations ────────────────────────────────────────────────
        if (!overdue.isEmpty()) {
            // Effective escalation threshold = smallest active rule (0 = immediate, historical default)
            int escalationThresholdHours = escalationRuleRepository.findByActiveTrue().stream()
                    .mapToInt(EscalationRule::getHoursAfterDeadline)
                    .min()
                    .orElse(0);

            for (ApprovalStep step : overdue) {
                try {
                    var invoice = step.getInvoice();
                    long hoursOverdue = ChronoUnit.HOURS.between(step.getDeadline(), now);

                    Map<String, Object> vars = buildStepVars(invoice, step);
                    vars.put("hoursOverdue", hoursOverdue);
                    vars.put("approverName", step.getApprover() != null
                            ? step.getApprover().getFirstName() + " " + step.getApprover().getLastName()
                            : "Non assigné");

                    // Notify the approver themselves (overdue warning) — unconditional for every overdue step
                    if (step.getApprover() != null) {
                        emailService.sendEmail(
                                step.getApprover().getEmail(),
                                "🚨 Délai dépassé — Action requise / SLA Breached",
                                "sla-escalation-approver",
                                vars
                        );
                    }

                    // Managerial escalation — gated by escalation threshold
                    if (hoursOverdue >= escalationThresholdHours) {
                        // Contextual escalation recipient (B1):
                        // N1 overdue in a 2-tier dept → the N2 approver; otherwise → DAF only.
                        User recipient = null;
                        if (step.getStepOrder() != null && step.getStepOrder() == 1) {
                            recipient = approvalStepRepository
                                    .findByInvoiceIdAndStepOrder(invoice.getId(), 2)
                                    .map(ApprovalStep::getApprover)
                                    .orElse(null);
                        }

                        List<User> recipients = (recipient != null)
                                ? List.of(recipient)
                                : userRepository.findActiveUsersByRoleName("ROLE_DAF");

                        for (User mgr : recipients) {
                            emailService.sendEmail(
                                    mgr.getEmail(),
                                    "🚨 Escalade SLA — Facture bloquée / SLA Escalation",
                                    "sla-escalation-manager",
                                    vars
                            );
                            // In-app notification (B1)
                            eventPublisher.publishEvent(
                                    new ApprovalEscalationEvent(
                                            this, invoice.getId(), mgr.getId()));
                        }

                        log.info("SLA escalation sent for invoice {} (step {}, {}h overdue)",
                                invoice.getReferenceNumber(), step.getStepOrder(), hoursOverdue);
                    }
                } catch (Exception e) {
                    log.error("SLA escalation failed for step {}: {}", step.getId(), e.getMessage());
                }
            }
        }

        log.info("Deadline reminder + SLA escalation job completed.");
    }

    private Map<String, Object> buildStepVars(Invoice invoice, ApprovalStep step) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("reference",    invoice.getReferenceNumber());
        vars.put("supplierName", invoice.getSupplierName());
        vars.put("amount",       invoice.getAmount() + " " + invoice.getCurrency());
        vars.put("deadline",     step.getDeadline() != null ? step.getDeadline().toString() : "—");
        vars.put("stepName",     step.getStepNameFr() != null ? step.getStepNameFr() : step.getStepNameEn());
        vars.put("department",   step.getDepartmentCode());
        vars.put("frontendUrl",  frontendUrl);
        vars.put("invoiceId",    invoice.getId().toString());
        return vars;
    }

    /**
     * Runs daily at 06:00 (server time). Sends payment due-date alerts to ASSISTANT_COMPTABLE users
     * for every BON_A_PAYER invoice whose due date matches an active {@link PaymentAlertRule}
     * threshold (J-N). When no rule is configured, falls back to the historical single 7-day
     * threshold so behaviour is never lost. (P9-55, made configurable by B4.)
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void sendPaymentDueAlerts() {
        log.info("Running payment due alert job...");
        LocalDate today = LocalDate.now();

        // Resolve the active J-N thresholds (configurable). Empty config → historical 7-day default.
        java.util.Set<Integer> thresholds = paymentAlertRuleRepository.findByActiveTrue().stream()
                .map(PaymentAlertRule::getDaysBeforeDue)
                .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        if (thresholds.isEmpty()) {
            thresholds = java.util.Set.of(DEFAULT_DUE_ALERT_DAYS);
        }
        final java.util.Set<Integer> activeThresholds = thresholds;
        log.info("Payment alert thresholds (days before due): {}", activeThresholds);

        // An invoice is alerted when the number of days until its due date equals an active threshold.
        List<Invoice> dueToAlert = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.BON_A_PAYER && inv.getDueDate() != null)
                .filter(inv -> !inv.getDueDate().isBefore(today))
                .filter(inv -> activeThresholds.contains(
                        (int) ChronoUnit.DAYS.between(today, inv.getDueDate())))
                .toList();

        log.info("Found {} invoices matching an active payment alert threshold", dueToAlert.size());

        List<User> comptables = userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");
        log.info("Found {} ASSISTANT_COMPTABLE users to notify", comptables.size());

        for (Invoice invoice : dueToAlert) {
            for (User comptable : comptables) {
                try {
                    Map<String, Object> vars = new HashMap<>();
                    vars.put("reference", invoice.getReferenceNumber());
                    vars.put("supplierName", invoice.getSupplierName());
                    vars.put("amount", invoice.getAmount() + " " + invoice.getCurrency());
                    vars.put("dueDate", invoice.getDueDate().toString());
                    vars.put("daysUntilDue", ChronoUnit.DAYS.between(today, invoice.getDueDate()));
                    vars.put("frontendUrl", frontendUrl);

                    emailService.sendEmail(
                            comptable.getEmail(),
                            "💰 Alerte Échéance de Paiement / Payment Due Alert",
                            "payment-due-alert",
                            vars
                    );

                    log.info("Payment due alert sent to {} for invoice {}", comptable.getEmail(), invoice.getReferenceNumber());

                } catch (Exception e) {
                    log.error("Failed to send payment due alert to {} for invoice {}: {}",
                            comptable.getEmail(), invoice.getReferenceNumber(), e.getMessage());
                }
            }
        }

        log.info("Payment due alert job completed.");
    }
}
