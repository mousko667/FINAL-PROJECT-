package com.oct.invoicesystem.domain.workflow.model;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "approval_steps",
        uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_id", "step_order"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_name_fr", nullable = false, length = 255)
    private String stepNameFr;

    @Column(name = "step_name_en", nullable = false, length = 255)
    private String stepNameEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private User approver;

    @Column(name = "department_code", nullable = false, length = 20)
    private String departmentCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApprovalStepStatus status = ApprovalStepStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String comments;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    private Instant deadline;

    @Column(name = "action_at")
    private Instant actionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ApprovalStepStatus.PENDING;
        }
    }
}
