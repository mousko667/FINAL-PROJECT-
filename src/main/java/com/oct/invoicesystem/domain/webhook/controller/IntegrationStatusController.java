package com.oct.invoicesystem.domain.webhook.controller;

import com.oct.invoicesystem.domain.webhook.dto.WebhookStatusResponse;
import com.oct.invoicesystem.domain.webhook.service.WebhookService;
import com.oct.invoicesystem.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations/status")
@RequiredArgsConstructor
@Slf4j
public class IntegrationStatusController {

    private final WebhookService webhookService;

    /**
     * Get integration health status - lists all active webhooks with last delivery result
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<WebhookStatusResponse>>> getIntegrationStatus() {
        log.info("Admin requesting integration status");

        return ResponseEntity.ok(ApiResponse.success(webhookService.getIntegrationStatus()));
    }
}
