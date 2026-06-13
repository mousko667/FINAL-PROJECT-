package com.oct.invoicesystem.domain.webhook.controller;

import com.oct.invoicesystem.domain.webhook.dto.WebhookCreateRequest;
import com.oct.invoicesystem.domain.webhook.dto.WebhookDeliveryResponse;
import com.oct.invoicesystem.domain.webhook.dto.WebhookResponse;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import com.oct.invoicesystem.shared.response.PagedResponse;
import com.oct.invoicesystem.domain.user.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

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

        return ResponseEntity.ok(ApiResponse.success(webhookService.listActiveWebhooks()));
    }

    /**
     * Deactivate a webhook (soft delete) (ROLE_ADMIN only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateWebhook(@PathVariable UUID id) {
        log.info("Admin deactivating webhook: {}", id);

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

        return ResponseEntity.ok(ApiResponse.success(webhookService.getDeliveryLog(id, pageable)));
    }
}
