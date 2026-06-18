package com.oct.invoicesystem.domain.supplier.dto;

import com.oct.invoicesystem.domain.supplier.model.SupplierCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Email @Size(max = 255) String contactEmail,
    @Size(max = 50) String contactPhone,
    String bankDetails,
    String address,
    SupplierCategory category
) {
    /** Backward-compatible constructor for callers that predate the B5 category field. */
    public SupplierUpdateRequest(
            String companyName,
            String contactEmail,
            String contactPhone,
            String bankDetails,
            String address
    ) {
        this(companyName, contactEmail, contactPhone, bankDetails, address, null);
    }
}
