package com.oct.invoicesystem.domain.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierCreateRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Size(max = 100) String taxId,
    @NotBlank @Email @Size(max = 255) String contactEmail,
    @Size(max = 50) String contactPhone,
    @NotBlank String bankDetails,
    String address
) {}
