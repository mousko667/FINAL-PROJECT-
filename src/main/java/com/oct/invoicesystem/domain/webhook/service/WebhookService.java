package com.oct.invoicesystem.domain.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oct.invoicesystem.domain.webhook.dto.WebhookCreateRequest;
import com.oct.invoicesystem.domain.webhook.dto.WebhookResponse;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookRepository;
import com.oct.invoicesystem.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${webhook.delivery.timeout.seconds:5}")
    private int deliveryTimeoutSeconds;

    private static final int[] RETRY_DELAYS_MS = {5000, 25000, 125000};
    private static final int MAX_RETRIES = 3;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Register a new webhook with the given URL and events.
     * Generates a raw secret (returned once), stores SHA-256 hash in DB.
     */
    @Transactional
    public WebhookResponse registerWebhook(WebhookCreateRequest request, User createdBy) {
        // Generate raw secret
        String rawSecret = generateSecret();
        String secretHash = hashSecret(rawSecret);

        // Save webhook with hashed secret
        Webhook webhook = Webhook.builder()
                .name(request.getName())
                .url(request.getUrl())
                .secretHash(secretHash)
                .events(String.join(",", request.getEvents()))
                .isActive(true)
                .createdBy(createdBy)
                .build();

        Webhook saved = webhookRepository.save(webhook);

        // Return DTO with raw secret (shown only once)
        return WebhookResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .url(saved.getUrl())
                .secret(rawSecret)
                .events(request.getEvents())
                .isActive(saved.getIsActive())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
    }

    /**
     * Deactivate a webhook (soft delete). Append-only: never hard delete.
     */
    @Transactional
    public void deactivateWebhook(UUID webhookId) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + webhookId));
        webhook.setIsActive(false);
        webhookRepository.save(webhook);
    }

    /**
     * Get all active webhooks
     */
    public List<Webhook> getActiveWebhooks() {
        return webhookRepository.findByIsActiveTrue();
    }

    /**
     * Get a webhook by ID (if active)
     */
    public Webhook getActiveWebhook(UUID webhookId) {
        return webhookRepository.findActiveWebhookById(webhookId);
    }

    /**
     * Deliver a webhook event with retries.
     * Runs asynchronously; failures do NOT block the calling transaction.
     */
    @Async
    public void deliverWebhook(Webhook webhook, String eventType, Map<String, Object> payload, String rawSecret) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            deliverWithRetry(webhook, eventType, payloadJson, rawSecret, 0);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for webhook {}: {}", webhook.getId(), e.getMessage(), e);
            recordDeliveryFailure(webhook, eventType, "{}", null, 0, null);
        }
    }

    /**
     * Retry logic with exponential backoff.
     */
    private void deliverWithRetry(Webhook webhook, String eventType, String payloadJson, String rawSecret, int attemptNumber) {
        if (attemptNumber >= MAX_RETRIES) {
            log.warn("Webhook {} failed after {} retries for event {}", webhook.getId(), MAX_RETRIES, eventType);
            recordDeliveryFailure(webhook, eventType, payloadJson, null, MAX_RETRIES, null);
            return;
        }

        // Delay before retry (except first attempt)
        if (attemptNumber > 0) {
            int delayMs = RETRY_DELAYS_MS[attemptNumber - 1];
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Webhook retry sleep interrupted for webhook {}", webhook.getId());
                recordDeliveryFailure(webhook, eventType, payloadJson, null, attemptNumber + 1, null);
                return;
            }
        }

        try {
            // Build signed request
            String signature = buildSignature(payloadJson, rawSecret);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-OCT-Signature", signature);

            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);

            // Send with timeout
            var response = restTemplate.exchange(webhook.getUrl(), HttpMethod.POST, request, String.class);
            int statusCode = response.getStatusCode().value();

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Webhook {} delivered successfully to {} for event {}", webhook.getId(), webhook.getUrl(), eventType);
                recordDeliverySuccess(webhook, eventType, payloadJson, statusCode, attemptNumber + 1);
            } else {
                log.warn("Webhook {} returned non-2xx status {} from {}", webhook.getId(), statusCode, webhook.getUrl());
                deliverWithRetry(webhook, eventType, payloadJson, rawSecret, attemptNumber + 1);
            }
        } catch (RestClientException e) {
            log.warn("Webhook {} delivery failed (attempt {}) to {}: {}", webhook.getId(), attemptNumber + 1, webhook.getUrl(), e.getMessage());
            deliverWithRetry(webhook, eventType, payloadJson, rawSecret, attemptNumber + 1);
        }
    }

    /**
     * Build HMAC-SHA256 signature for payload
     */
    private String buildSignature(String payload, String rawSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec spec = new SecretKeySpec(rawSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(spec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("Failed to build HMAC signature: {}", e.getMessage(), e);
            throw new RuntimeException("Signature generation failed", e);
        }
    }

    /**
     * Record successful delivery
     */
    private void recordDeliverySuccess(Webhook webhook, String eventType, String payload, int responseStatus, int attemptCount) {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .webhook(webhook)
                .eventType(eventType)
                .payload(payload)
                .responseStatus(responseStatus)
                .attemptCount(attemptCount)
                .lastAttemptedAt(Instant.now())
                .success(true)
                .build();
        deliveryRepository.save(delivery);
    }

    /**
     * Record failed delivery
     */
    private void recordDeliveryFailure(Webhook webhook, String eventType, String payload, Integer responseStatus, int attemptCount, Instant lastAttempted) {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .webhook(webhook)
                .eventType(eventType)
                .payload(payload)
                .responseStatus(responseStatus)
                .attemptCount(attemptCount)
                .lastAttemptedAt(lastAttempted != null ? lastAttempted : Instant.now())
                .success(false)
                .build();
        deliveryRepository.save(delivery);
    }

    /**
     * Generate a cryptographically secure random secret
     */
    private String generateSecret() {
        UUID uuid = UUID.randomUUID();
        return Base64.getEncoder().encodeToString(uuid.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Hash a secret using SHA-256
     */
    private String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to hash secret: {}", e.getMessage(), e);
            throw new RuntimeException("Secret hashing failed", e);
        }
    }
}
