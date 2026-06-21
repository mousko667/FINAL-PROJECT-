package com.oct.invoicesystem.domain.compliance.dto;

import com.oct.invoicesystem.domain.retention.dto.RetentionComplianceDTO;

import java.time.Instant;

/**
 * Aggregated, read-only archive-compliance report (M14 #11). ADMIN only, no financial data.
 * Computed in real time; not persisted.
 */
public record ArchiveComplianceReportDTO(
        Instant generatedAt,
        CoverageSection coverage,
        IntegritySection integrity,
        RetentionComplianceDTO retention,
        LifecycleSection lifecycle
) {
    /** Archival coverage of invoices that reached the ARCHIVE status. */
    public record CoverageSection(
            long archivedInvoices,
            long archivedWithDocument,
            long archivedWithoutDocument,
            double coverageRate) {}

    /** Integrity proof: every stored document carries a SHA-256 checksum. */
    public record IntegritySection(
            long totalDocuments,
            long withChecksum,
            long missingChecksum,
            double integrityRate) {}

    /** Document lifecycle: retention dispositions and versioning. */
    public record LifecycleSection(
            long pending,
            long retained,
            long purged,
            long versionedDocuments) {}
}
