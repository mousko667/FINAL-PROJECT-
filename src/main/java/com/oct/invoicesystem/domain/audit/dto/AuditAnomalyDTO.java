package com.oct.invoicesystem.domain.audit.dto;

import java.util.UUID;

/**
 * A flagged audit anomaly (M10, statistical — no ML). Surfaces a user whose recent activity
 * deviates from the population baseline, or who has an excessive number of access-denied events.
 */
public record AuditAnomalyDTO(
        UUID userId,
        String username,
        String type,        // HIGH_VOLUME | EXCESSIVE_ACCESS_DENIED
        long observed,      // the user's count in the window
        double baseline,    // mean across users (for HIGH_VOLUME) or the threshold
        String detail       // human-readable explanation
) {}
