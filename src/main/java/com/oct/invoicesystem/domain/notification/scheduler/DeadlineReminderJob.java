package com.oct.invoicesystem.domain.notification.scheduler;

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
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DeadlineReminderJob {

    private final ApprovalStepRepository approvalStepRepository;
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
}
