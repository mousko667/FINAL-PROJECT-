package com.oct.invoicesystem.domain.compliance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** M14 request/response records grouped for the compliance domain. */
public final class ComplianceDTOs {
    private ComplianceDTOs() {}

    public record IncidentResponse(UUID id, String title, String description, String severity,
                                   String status, Instant reportedAt, Instant resolvedAt) {}
    public record IncidentRequest(@NotBlank String title, String description, String severity) {}

    public record ChecklistItemResponse(UUID id, String framework, String label, boolean completed,
                                        String notes, Instant updatedAt) {}
    public record ChecklistItemRequest(@NotBlank String framework, @NotBlank String label) {}

    public record CalendarResponse(UUID id, String title, LocalDate dueDate, String description, boolean completed) {}
    public record CalendarRequest(@NotBlank String title, @NotNull LocalDate dueDate, String description) {}

    public record BackupStatusResponse(Instant lastBackupAt, String status, String detail) {}

    public record BackupAuditLogResponse(UUID id, String operation, String filename, String status, String errorMessage, String triggeredBy, Instant createdAt) {}

    public record PrivacyAcceptanceResponse(boolean accepted, String policyVersion) {}
}
