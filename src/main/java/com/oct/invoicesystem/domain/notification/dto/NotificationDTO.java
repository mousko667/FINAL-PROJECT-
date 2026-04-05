package com.oct.invoicesystem.domain.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDTO(
        UUID id,
        UUID invoiceId,
        String titleFr,
        String titleEn,
        String messageFr,
        String messageEn,
        String type,
        boolean isRead,
        Instant readAt,
        Instant createdAt
) {}
