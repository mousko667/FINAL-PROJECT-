package com.oct.invoicesystem.domain.webhook.mapper;

import com.oct.invoicesystem.domain.webhook.dto.WebhookResponse;
import com.oct.invoicesystem.domain.webhook.model.Webhook;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class WebhookMapper {

    public WebhookResponse toResponseWithoutSecret(Webhook webhook) {
        if (webhook == null) {
            return null;
        }
        return WebhookResponse.builder()
                .id(webhook.getId())
                .name(webhook.getName())
                .url(webhook.getUrl())
                .secret(null)
                .events(parseEvents(webhook.getEvents()))
                .isActive(webhook.getIsActive())
                .createdAt(webhook.getCreatedAt())
                .updatedAt(webhook.getUpdatedAt())
                .build();
    }

    public WebhookResponse toResponse(Webhook webhook) {
        if (webhook == null) {
            return null;
        }
        return WebhookResponse.builder()
                .id(webhook.getId())
                .name(webhook.getName())
                .url(webhook.getUrl())
                .secret(null)
                .events(parseEvents(webhook.getEvents()))
                .isActive(webhook.getIsActive())
                .createdAt(webhook.getCreatedAt())
                .updatedAt(webhook.getUpdatedAt())
                .build();
    }

    private List<String> parseEvents(String events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(events.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
