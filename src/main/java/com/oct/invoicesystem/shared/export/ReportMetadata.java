package com.oct.invoicesystem.shared.export;

import java.time.Instant;

/**
 * Immutable metadata block stamped on generated PDF reports: who generated it (name + role),
 * when, and — when applicable — the covered period. Built in the service layer from the caller's
 * {@code Authentication}; {@code periodLabel} is null for reports that have no date range.
 */
public record ReportMetadata(
        String generatorName,
        String generatorRole,
        Instant generatedAt,
        String periodLabel
) {}
