package com.oct.invoicesystem.domain.workflow.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A configurable SLA escalation rule (B1, M4 #11 / M6 #6). The {@link
 * com.oct.invoicesystem.domain.notification.scheduler.DeadlineReminderJob} escalates an overdue
 * approval step once it has been overdue for at least {@code hoursAfterDeadline} hours, for the
 * smallest active threshold. The recipient is derived from the approval chain (next tier in the
 * same department, otherwise the DAF) — it is NOT stored on the rule.
 */
@Entity
@Table(name = "escalation_rules")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EscalationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hours_after_deadline", nullable = false)
    private int hoursAfterDeadline;

    @Column(length = 255)
    private String label;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
