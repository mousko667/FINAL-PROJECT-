package com.oct.invoicesystem.domain.webhook.model;

import com.oct.invoicesystem.domain.invoice.model.Invoice;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * WebhookDelivery entity — APPEND-ONLY audit log of webhook delivery attempts.
 * Records are never updated or deleted (enforced at DB level via trigger).
 * Each delivery attempt is logged, including retries.
 */
@Entity
@Table(name = "webhook_deliveries", indexes = {
        @Index(name = "idx_webhook_deliveries_webhook_id", columnList = "webhook_id"),
        @Index(name = "idx_webhook_deliveries_event_type", columnList = "event_type"),
        @Index(name = "idx_webhook_deliveries_success", columnList = "success"),
        @Index(name = "idx_webhook_deliveries_created_at", columnList = "created_at DESC"),
        @Index(name = "idx_webhook_deliveries_invoice_id", columnList = "invoice_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    /**
     * Event type that triggered this delivery: INVOICE_SUBMITTED, INVOICE_VALIDATED, INVOICE_REJECTED, INVOICE_PAID
     */
    @Column(nullable = false, length = 50)
    private String eventType;

    /**
     * The invoice that triggered the webhook event (if applicable)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    /**
     * JSON payload sent to the webhook endpoint.
     * Structure: { "event": "...", "timestamp": "...", "invoiceId": "...", ... }
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Request headers sent (e.g., "X-OCT-Signature: sha256=...")
     */
    @Column(columnDefinition = "TEXT")
    private String requestHeaders;

    /**
     * HTTP status code from the webhook endpoint (e.g., 200, 500)
     */
    @Column
    private Integer responseStatus;

    /**
     * Response body from the webhook endpoint (for debugging)
     */
    @Column(columnDefinition = "TEXT")
    private String responseBody;

    /**
     * Number of delivery attempts made (1 for success, up to 3 for retry scenarios)
     */
    @Column(nullable = false)
    private Integer attemptCount;

    /**
     * Timestamp of the last delivery attempt
     */
    @Column
    private Instant lastAttemptedAt;

    /**
     * Whether the delivery was successful (HTTP 200-299)
     */
    @Column(nullable = false)
    private Boolean success;

    /**
     * When the record was created (immutable)
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (success == null) {
            success = false;
        }
    }
}
