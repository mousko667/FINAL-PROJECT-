package com.oct.invoicesystem.domain.supplier.dto;

import com.oct.invoicesystem.domain.supplier.model.SupplierCategory;
import com.oct.invoicesystem.domain.supplier.model.SupplierStatus;

import java.time.Instant;
import java.util.UUID;

public record SupplierResponse(
    UUID id,
    String companyName,
    String taxId,
    String contactEmail,
    String contactPhone,
    String address,
    SupplierStatus status,
    SupplierCategory category,
    UUID onboardedBy,
    Instant onboardedAt,
    Instant createdAt,
    Instant updatedAt
) {}
