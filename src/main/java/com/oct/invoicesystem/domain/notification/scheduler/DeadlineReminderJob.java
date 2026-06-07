package com.oct.invoicesystem.domain.notification.scheduler;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.ApprovalDeadlineEvent;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.user.model.User;
import com.oct.invoicesystem.domain.user.repository.UserRepository;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStep;
import com.oct.invoicesystem.domain.workflow.model.ApprovalStepStatus;
import com.oct.invoicesystem.domain.workflow.repository.ApprovalStepRepository;
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

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * Runs daily at 07:00. Sends deadline reminders (24h warning) AND SLA escalations (past deadline).
     * Reminder  → assigned approver
     * Escalation → DAF + ADMIN users when deadline already passed
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
            List<User> dafUsers   = userRepository.findActiveUsersByRoleName("ROLE_DAF");
            List<User> adminUsers = userRepository.findActiveUsersByRoleName("ROLE_ADMIN");

            for (ApprovalStep step : overdue) {
                try {
                    var invoice = step.getInvoice();
                    long hoursOverdue = ChronoUnit.HOURS.between(step.getDeadline(), now);

                    Map<String, Object> vars = buildStepVars(invoice, step);
                    vars.put("hoursOverdue", hoursOverdue);
                    vars.put("approverName", step.getApprover() != null
                            ? step.getApprover().getFirstName() + " " + step.getApprover().getLastName()
                            : "Non assigné");

                    // Notify the approver themselves (overdue warning)
                    if (step.getApprover() != null) {
                        emailService.sendEmail(
                                step.getApprover().getEmail(),
                                "🚨 Délai dépassé — Action requise / SLA Breached",
                                "sla-escalation-approver",
                                vars
                        );
                    }

                    // Escalate to DAF + Admin
                    for (User manager : concat(dafUsers, adminUsers)) {
                        emailService.sendEmail(
                                manager.getEmail(),
                                "🚨 Escalade SLA — Facture bloquée / SLA Escalation",
                                "sla-escalation-manager",
                                vars
                        );
                    }

                    log.info("SLA escalation sent for invoice {} (step {}, {}h overdue)",
                            invoice.getReferenceNumber(), step.getStepOrder(), hoursOverdue);
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

    private <T> List<T> concat(List<T> a, List<T> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream())
                .distinct().toList();
    }

    /**
     * Runs daily at 06:00 (server time). Checks for invoices due within 7 days
     * and sends payment due date alert emails to ASSISTANT_COMPTABLE users.
     * P9-55: Payment due date alerts
     */
    @Scheduled(cron = "0 0 6 * * *")
    public void sendPaymentDueAlerts() {
        log.info("Running payment due alert job...");
        LocalDate today = LocalDate.now();
        LocalDate in7days = today.plusDays(7);

        // Find invoices in BON_A_PAYER status due within 7 days
        List<Invoice> upcomingPayments = invoiceRepository.findAll().stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.BON_A_PAYER &&
                           inv.getDueDate() != null &&
                           !inv.getDueDate().isBefore(today) &&
                           !inv.getDueDate().isAfter(in7days))
                .toList();

        log.info("Found {} invoices due within 7 days", upcomingPayments.size());

        // Get all ASSISTANT_COMPTABLE users
        List<User> comptables = userRepository.findActiveUsersByRoleName("ROLE_ASSISTANT_COMPTABLE");

        log.info("Found {} ASSISTANT_COMPTABLE users to notify", comptables.size());

        for (Invoice invoice : upcomingPayments) {
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
