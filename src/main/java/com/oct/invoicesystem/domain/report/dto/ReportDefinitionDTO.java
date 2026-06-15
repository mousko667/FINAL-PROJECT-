package com.oct.invoicesystem.domain.report.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class ReportDefinitionDTO {

    public record Response(
            UUID id, String name, String dataset, String format, String frequency,
            String recipients, boolean active, Instant createdAt, Instant lastRunAt) {}

    public record Request(
            @NotBlank String name,
            @NotBlank String dataset,   // INVOICES | SUPPLIERS | AUDIT | BUDGET
            String format,              // CSV | EXCEL | PDF (default CSV)
            String frequency,           // MANUAL | DAILY | WEEKLY | MONTHLY (default MANUAL)
            String recipients) {}
}
