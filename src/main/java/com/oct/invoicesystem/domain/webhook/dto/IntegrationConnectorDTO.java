package com.oct.invoicesystem.domain.webhook.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class IntegrationConnectorDTO {

    public record Response(
            UUID id, String name, String type, String endpoint, boolean enabled,
            String lastStatus, Instant lastCheckedAt, String lastMessage,
            Integer syncIntervalMinutes, Instant lastSyncAt, String lastSyncStatus, String lastSyncMessage,
            Instant createdAt) {}

    public record Request(
            @NotBlank String name,
            @NotBlank String type,    // ERP | ACCOUNTING | BANKING | DMS | MOCK
            String endpoint,
            String config) {}

    /** Sync schedule configuration (B6). A null interval disables scheduled sync. */
    public record ScheduleRequest(Integer syncIntervalMinutes) {}
}
