package com.oct.invoicesystem.domain.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Email @Size(max = 255) String contactEmail,
    @Size(max = 50) String contactPhone,
    String bankDetails,
    String address
) {}
