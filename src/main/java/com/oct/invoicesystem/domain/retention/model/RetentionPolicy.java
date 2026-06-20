package com.oct.invoicesystem.domain.retention.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Singleton document-retention policy (B2, M9 #7 / M14 #6). A single row drives the
 * {@link com.oct.invoicesystem.domain.invoice.scheduler.DocumentRetentionJob}: documents older than
 * {@code retentionYears} are flagged (non-destructively) when {@code active} is true.
 *
 * <p>Workflow/compliance setting — contains no financial data (no amounts, no suppliers), so it is
 * editable by ROLE_ADMIN without conflicting with separation-of-duties. {@code lastSweepAt} and
 * {@code lastFlaggedCount} are written by the job for operational visibility.
 */
@Entity
@Table(name = "retention_policy")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetentionPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "retention_years", nullable = false)
    private int retentionYears;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_sweep_at")
    private Instant lastSweepAt;

    @Column(name = "last_flagged_count")
    private Integer lastFlaggedCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
