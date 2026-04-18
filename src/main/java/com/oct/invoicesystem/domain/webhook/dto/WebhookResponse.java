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
public class WebhookResponse {
    private UUID id;
    private String name;
    private String url;

    // Only included in POST response (registration)
    private String secret;

    private List<String> events;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
