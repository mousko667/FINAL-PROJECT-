package com.oct.invoicesystem.domain.invoice.dto;

import com.oct.invoicesystem.domain.invoice.model.DataSensitivity;
import jakarta.validation.constraints.NotNull;

/** Request to reclassify an invoice's data sensitivity (P11-15). */
public record UpdateSensitivityRequest(
        @NotNull DataSensitivity dataSensitivity
) {}
