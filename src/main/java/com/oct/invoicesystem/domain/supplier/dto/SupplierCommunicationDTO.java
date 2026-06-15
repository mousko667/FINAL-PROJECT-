package com.oct.invoicesystem.domain.supplier.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.UUID;

public class SupplierCommunicationDTO {

    public record Response(
            UUID id, UUID supplierId, String channel, String subject, String body, Instant loggedAt) {}

    public record Request(
            String channel,
            @NotBlank String subject,
            String body) {}
}
