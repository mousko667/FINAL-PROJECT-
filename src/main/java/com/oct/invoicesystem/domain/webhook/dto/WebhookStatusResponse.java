package com.oct.invoicesystem.domain.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebhookStatusResponse {
    private UUID id;
    private String name;
    private String url;
    private List<String> events;
    private Boolean isActive;
    private Instant createdAt;

    // Last delivery info
    private Integer lastResponseStatus;
    private Boolean lastDeliverySuccess;
    private Instant lastDeliveredAt;
}
