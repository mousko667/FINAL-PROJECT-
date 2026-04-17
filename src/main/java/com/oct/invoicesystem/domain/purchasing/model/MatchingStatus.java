package com.oct.invoicesystem.domain.purchasing.model;

/**
 * Enumeration of Three-Way Matching result statuses.
 * - MATCHED: All line items match within tolerance thresholds
 * - PARTIAL: At least one line within tolerance but not all lines exactly match
 * - MISMATCH: At least one line outside tolerance thresholds
 * - OVERRIDDEN: Mismatch was overridden by DAF/Admin
 */
public enum MatchingStatus {
    MATCHED,
    PARTIAL,
    MISMATCH,
    OVERRIDDEN
}
