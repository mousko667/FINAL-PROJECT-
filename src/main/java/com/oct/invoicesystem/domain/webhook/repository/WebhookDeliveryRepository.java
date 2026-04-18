package com.oct.invoicesystem.domain.webhook.repository;

import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    Page<WebhookDelivery> findByWebhookOrderByCreatedAtDesc(Webhook webhook, Pageable pageable);

    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.webhook = ?1 ORDER BY wd.createdAt DESC LIMIT 1")
    Optional<WebhookDelivery> findLatestDeliveryByWebhook(Webhook webhook);

    List<WebhookDelivery> findByWebhookAndSuccess(Webhook webhook, Boolean success);

    @Query("SELECT wd FROM WebhookDelivery wd WHERE wd.webhook.id = ?1 AND wd.createdAt >= ?2 ORDER BY wd.createdAt DESC")
    Page<WebhookDelivery> findDeliveriesSinceTime(UUID webhookId, Instant since, Pageable pageable);

    long countByCreatedAtAfter(Instant since);

    long countByCreatedAtAfterAndSuccessTrue(Instant since);
}
