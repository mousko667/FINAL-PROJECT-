package com.oct.invoicesystem.domain.webhook.repository;

import com.oct.invoicesystem.domain.webhook.model.Webhook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, UUID> {
    List<Webhook> findByIsActiveTrue();

    @Query("SELECT w FROM Webhook w WHERE w.isActive = true ORDER BY w.createdAt DESC")
    Page<Webhook> findActiveWebhooks(Pageable pageable);

    @Query("SELECT w FROM Webhook w WHERE w.isActive = true AND w.id = ?1")
    Webhook findActiveWebhookById(UUID id);
}
