package com.oct.invoicesystem.domain.webhook.model;

import com.oct.invoicesystem.domain.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SoftDelete;

import java.time.Instant;
import java.util.UUID;

/**
 * Webhook entity — represents an external endpoint to receive invoice state change events.
 * Webhooks are soft-deleted, never hard-deleted.
 * Secret is stored as SHA-256 hash; raw secret never persisted.
 */
@Entity
@Table(name = "webhooks", indexes = {
        @Index(name = "idx_webhooks_created_by", columnList = "created_by"),
        @Index(name = "idx_webhooks_is_active", columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SoftDelete(columnName = "deleted_at")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 2048)
    private String url;

    /**
     * SHA-256 hash of the original secret.
     * The raw secret is NEVER stored in the database.
     */
    @Column(nullable = false, length = 64)
    private String secretHash;

    /**
     * Comma-separated list of event types: INVOICE_SUBMITTED,INVOICE_VALIDATED,INVOICE_REJECTED,INVOICE_PAID
     */
    @Column(nullable = false, length = 500)
    private String events;

    @Column(nullable = false)
    private boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
