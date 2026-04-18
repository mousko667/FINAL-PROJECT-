package com.oct.invoicesystem.domain.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryResponse {
    private UUID id;
    private String eventType;
    private Integer responseStatus;
    private Integer attemptCount;
    private Boolean success;
    private Instant lastAttemptedAt;
    private Instant createdAt;
}
