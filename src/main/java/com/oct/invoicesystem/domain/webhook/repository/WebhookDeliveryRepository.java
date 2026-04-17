package com.oct.invoicesystem.domain.webhook.repository;

import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WebhookDeliveryRepository — APPEND-ONLY audit log.
 * Queries only; no updates or deletes.
 */
@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    /**
     * Find all deliveries for a webhook
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.webhook.id = :webhookId ORDER BY wd.createdAt DESC")
    Page<WebhookDelivery> findByWebhookId(@Param("webhookId") UUID webhookId, Pageable pageable);

    /**
     * Find the most recent delivery for a webhook
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.webhook.id = :webhookId ORDER BY wd.createdAt DESC LIMIT 1")
    Optional<WebhookDelivery> findMostRecentByWebhookId(@Param("webhookId") UUID webhookId);

    /**
     * Find failed deliveries (for monitoring/alerting)
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.webhook.id = :webhookId AND wd.success = false ORDER BY wd.createdAt DESC")
    List<WebhookDelivery> findFailedByWebhookId(@Param("webhookId") UUID webhookId);

    /**
     * Find deliveries by event type
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.eventType = :eventType ORDER BY wd.createdAt DESC")
    Page<WebhookDelivery> findByEventType(@Param("eventType") String eventType, Pageable pageable);

    /**
     * Find deliveries with max attempt count = 3 (max retries exceeded)
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.webhook.id = :webhookId AND wd.attemptCount = 3 AND wd.success = false")
    List<WebhookDelivery> findFailedMaxRetriesByWebhookId(@Param("webhookId") UUID webhookId);

    /**
     * Find deliveries for a specific invoice
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.invoice.id = :invoiceId ORDER BY wd.createdAt DESC")
    Page<WebhookDelivery> findByInvoiceId(@Param("invoiceId") UUID invoiceId, Pageable pageable);

    /**
     * Find recent failed deliveries (for retry scheduling)
     */
    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.success = false AND wd.attemptCount < 3 AND wd.lastAttemptedAt > :since ORDER BY wd.lastAttemptedAt ASC")
    List<WebhookDelivery> findFailedRetriesAfter(@Param("since") Instant since);
}
