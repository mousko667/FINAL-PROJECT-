package com.oct.invoicesystem.domain.webhook.repository;

import com.oct.invoicesystem.domain.webhook.model.Webhook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    /**
     * Find active webhooks for a given user (admin who registered them)
     */
    @Query("SELECT w FROM Webhook w WHERE w.createdBy.id = :userId AND w.isActive = true")
    List<Webhook> findActiveByCreatedByIdWithDeleteCheck(@Param("userId") UUID userId);

    /**
     * Find all webhooks (including inactive) registered by a user
     */
    @Query("SELECT w FROM Webhook w WHERE w.createdBy.id = :userId ORDER BY w.createdAt DESC")
    Page<Webhook> findByCreatedById(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find webhooks that listen to a specific event type
     */
    @Query("SELECT w FROM Webhook w WHERE w.isActive = true AND w.events LIKE CONCAT('%', :eventType, '%')")
    List<Webhook> findActiveByEventType(@Param("eventType") String eventType);

    /**
     * Find all active webhooks
     */
    @Query("SELECT w FROM Webhook w WHERE w.isActive = true")
    List<Webhook> findAllActive();

    /**
     * Find webhook by ID, excluding soft-deleted
     */
    Optional<Webhook> findById(UUID id);
}
