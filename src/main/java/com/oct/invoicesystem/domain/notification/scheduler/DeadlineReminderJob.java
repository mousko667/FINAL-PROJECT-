package com.oct.invoicesystem.domain.notification.scheduler;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.invoice.model.InvoiceStatus;
import com.oct.invoicesystem.domain.invoice.repository.InvoiceRepository;
import com.oct.invoicesystem.domain.notification.event.ApprovalDeadlineEvent;
import com.oct.invoicesystem.domain.notification.service.EmailService;
import com.oct.invoicesystem.domain.user.model.Role;
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
     * Runs daily at 07:00 (server time). Checks for all PENDING approval steps whose
     * deadline will expire in the next 24 hours and sends a reminder email to the assigned approver.
     * Also publishes an ApprovalDeadlineEvent for WebSocket notification.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void sendDeadlineReminders() {
        log.info("Running deadline reminder job...");
        Instant in24h = Instant.now().plus(24, ChronoUnit.HOURS);

        List<ApprovalStep> approaching = approvalStepRepository
                .findByStatusAndDeadlineBefore(ApprovalStepStatus.PENDING, in24h);

        log.info("Found {} approval steps with approaching deadlines", approaching.size());

        for (ApprovalStep step : approaching) {
            try {
                if (step.getApprover() == null) {
                    log.warn("Approval step {} has no assigned approver — skipping", step.getId());
                    continue;
                }

                User approver = step.getApprover();
                var invoice = step.getInvoice();

                Map<String, Object> vars = new HashMap<>();
                vars.put("reference", invoice.getReferenceNumber());
                vars.put("supplierName", invoice.getSupplierName());
                vars.put("amount", invoice.getAmount() + " " + invoice.getCurrency());
                vars.put("deadline", step.getDeadline() != null ? step.getDeadline().toString() : "—");
                vars.put("frontendUrl", frontendUrl);

                emailService.sendEmail(
                        approver.getEmail(),
                        "⏰ Rappel de délai / Approval Deadline Reminder",
                        "deadline-reminder",
                        vars
                );

                // Also publish for WebSocket
                eventPublisher.publishEvent(new ApprovalDeadlineEvent(this, invoice.getId(), step.getId()));

                log.info("Deadline reminder sent to {} for invoice {}", approver.getEmail(), invoice.getReferenceNumber());

            } catch (Exception e) {
                log.error("Failed to send deadline reminder for step {}: {}", step.getId(), e.getMessage());
            }
        }

        log.info("Deadline reminder job completed.");
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
        List<User> comptables = userRepository.findAll().stream()
                .filter(u -> u.getUserRoles() != null &&
                           u.getUserRoles().stream()
                              .anyMatch(ur -> "ASSISTANT_COMPTABLE".equals(ur.getRole().getName())))
                .toList();

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
