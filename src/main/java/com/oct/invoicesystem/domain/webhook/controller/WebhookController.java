package com.oct.invoicesystem.domain.webhook.controller;

import com.oct.invoicesystem.domain.webhook.dto.WebhookCreateRequest;
import com.oct.invoicesystem.domain.webhook.dto.WebhookDeliveryResponse;
import com.oct.invoicesystem.domain.webhook.dto.WebhookResponse;
import com.oct.invoicesystem.domain.webhook.dto.WebhookStatusResponse;
import com.oct.invoicesystem.domain.webhook.mapper.WebhookMapper;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import com.oct.invoicesystem.domain.webhook.model.WebhookDelivery;
import com.oct.invoicesystem.domain.webhook.repository.WebhookDeliveryRepository;
import com.oct.invoicesystem.domain.webhook.repository.WebhookRepository;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.oct.invoicesystem.shared.exception.ResourceNotFoundException;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/integrations/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookMapper webhookMapper;

    /**
     * Register a new webhook (ROLE_ADMIN only)
     * Returns the raw secret ONCE in the response
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<WebhookResponse>> registerWebhook(
            @Valid @RequestBody WebhookCreateRequest request) {
        log.info("Admin registering new webhook: {}", request.getName());

        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        WebhookResponse response = webhookService.registerWebhook(request, currentUser);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "webhook.created"));
    }

    /**
     * List all webhooks (ROLE_ADMIN only)
     * Secret is NOT returned in list responses
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WebhookResponse>>> listWebhooks() {
        log.info("Admin listing webhooks");

        List<Webhook> webhooks = webhookRepository.findByIsActiveTrue();
        List<WebhookResponse> responses = webhooks.stream()
                .map(webhookMapper::toResponseWithoutSecret)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    /**
     * Deactivate a webhook (soft delete) (ROLE_ADMIN only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateWebhook(@PathVariable UUID id) {
        log.info("Admin deactivating webhook: {}", id);

        Webhook webhook = webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));

        webhookService.deactivateWebhook(id);

        return ResponseEntity.ok(ApiResponse.success(null, "webhook.deactivated"));
    }

    /**
     * Get delivery log for a webhook (ROLE_ADMIN only)
     */
    @GetMapping("/{id}/deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResponse<WebhookDeliveryResponse>>> getDeliveryLog(
            @PathVariable UUID id,
            Pageable pageable) {
        log.info("Admin requesting delivery log for webhook: {}", id);

        Webhook webhook = webhookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));

        Page<WebhookDelivery> deliveries = deliveryRepository.findByWebhookOrderByCreatedAtDesc(webhook, pageable);
        
        List<WebhookDeliveryResponse> responses = deliveries.getContent().stream()
                .map(d -> WebhookDeliveryResponse.builder()
                        .id(d.getId())
                        .eventType(d.getEventType())
                        .responseStatus(d.getResponseStatus())
                        .attemptCount(d.getAttemptCount())
                        .success(d.getSuccess())
                        .lastAttemptedAt(d.getLastAttemptedAt())
                        .createdAt(d.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        PagedResponse<WebhookDeliveryResponse> pagedResponse = PagedResponse.<WebhookDeliveryResponse>builder()
                .content(responses)
                .totalElements(deliveries.getTotalElements())
                .totalPages(deliveries.getTotalPages())
                .page(deliveries.getNumber())
                .size(deliveries.getSize())
                .last(deliveries.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.success(pagedResponse));
    }
}
