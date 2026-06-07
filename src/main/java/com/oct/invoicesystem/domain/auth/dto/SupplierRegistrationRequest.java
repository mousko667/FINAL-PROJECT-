package com.oct.invoicesystem.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRegistrationRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Size(max = 100) String taxId,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(max = 255) String password,
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    @Size(max = 50) String contactPhone,
    String bankDetails,
    String address
) {}
