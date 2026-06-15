package com.oct.invoicesystem.domain.supplier.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class SupplierContractDTO {

    public record Response(
            UUID id, UUID supplierId, String reference, String title,
            LocalDate startDate, LocalDate endDate, String status, String notes, Instant createdAt) {}

    public record Request(
            @NotBlank String reference,
            @NotBlank String title,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            String notes) {}
}
