package com.oct.invoicesystem.domain.webhook.controller;

import com.oct.invoicesystem.domain.webhook.dto.WebhookStatusResponse;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookRepository;
import com.oct.invoicesystem.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/integrations/status")
@RequiredArgsConstructor
@Slf4j
public class IntegrationStatusController {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;

    /**
     * Get integration health status - lists all active webhooks with last delivery result
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WebhookStatusResponse>>> getIntegrationStatus() {
        log.info("Admin requesting integration status");

        List<Webhook> webhooks = webhookRepository.findByIsActiveTrue();
        
        List<WebhookStatusResponse> responses = webhooks.stream()
                .map(this::mapWebhookToStatus)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    private WebhookStatusResponse mapWebhookToStatus(Webhook webhook) {
        WebhookStatusResponse.WebhookStatusResponseBuilder builder = WebhookStatusResponse.builder()
                .id(webhook.getId())
                .name(webhook.getName())
                .url(webhook.getUrl())
                .events(Arrays.asList(webhook.getEvents().split(",")))
                .isActive(webhook.getIsActive())
                .createdAt(webhook.getCreatedAt());

        // Get latest delivery
        deliveryRepository.findLatestDeliveryByWebhook(webhook)
                .ifPresent(latest -> {
                    builder.lastResponseStatus(latest.getResponseStatus());
                    builder.lastDeliverySuccess(latest.getSuccess());
                    builder.lastDeliveredAt(latest.getLastAttemptedAt());
                });

        return builder.build();
    }
}
